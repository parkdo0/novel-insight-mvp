package com.sparta.novel_insight.service

// Jackson: JSON 직렬화/역직렬화 라이브러리
// ObjectMapper: Java 객체 ↔ JSON 문자열 변환을 담당하는 핵심 클래스
import com.fasterxml.jackson.databind.ObjectMapper

// SLF4J (Simple Logging Facade for Java): 자바 로깅 추상화 API
// LoggerFactory: 로거 인스턴스를 생성하는 팩토리 클래스
import org.slf4j.LoggerFactory

// MDC (Mapped Diagnostic Context): 로그에 컨텍스트 정보를 추가하는 도구
// 스레드별로 독립적인 정보를 저장하고 로그에 포함시킬 수 있음
// 예: 요청마다 고유한 traceId를 부여하여 로그 추적 가능
import org.slf4j.MDC

// Spring Kafka 관련 임포트
// KafkaTemplate: Kafka로 메시지를 전송하는 Spring의 고수준 추상화 클래스
import org.springframework.kafka.core.KafkaTemplate

// SendResult: Kafka 메시지 전송 결과를 담는 클래스
// RecordMetadata (offset, partition 등 정보 포함)
import org.springframework.kafka.support.SendResult

// @Service: Spring이 관리하는 서비스 빈으로 등록
import org.springframework.stereotype.Service

// CompletableFuture: 자바의 비동기 처리를 위한 Future 구현체
// 비동기 작업의 완료, 예외 처리 등을 함수형 스타일로 처리 가능
import java.util.concurrent.CompletableFuture

/**
 * Kafka Producer 서비스
 *
 * 리뷰 작성 시 로그 데이터를 Kafka 토픽으로 비동기 전송하는 서비스입니다.
 * Python 감성 분석 서비스가 이 메시지를 구독(consume)하여 처리합니다.
 *
 * 데이터 흐름:
 * 1. ReviewController에서 리뷰 저장 후 이 서비스 호출
 * 2. 리뷰 정보를 JSON으로 직렬화
 * 3. Kafka 토픽(novel-reviews)으로 전송 (비동기)
 * 4. Python Kafka Consumer가 메시지 수신
 * 5. Python이 감성 분석 후 MySQL에 저장
 *
 * 비동기 처리 이유:
 * - Kafka 전송 대기 시간만큼 API 응답이 지연되지 않음
 * - 감성 분석은 시간이 걸리는 작업이므로 사용자 응답과 분리
 * - Kafka 장애 시에도 리뷰 저장은 성공 (서비스 안정성)
 *
 * Kafka 사용 이유:
 * - 대용량 메시지 처리 가능 (초당 수백만 건)
 * - 메시지 영속성 보장 (디스크에 저장)
 * - Consumer 장애 시 재처리 가능 (메시지 유실 방지)
 * - 수평 확장 용이 (파티션 추가로 처리량 증가)
 */
@Service
class KafkaProducerService(
    /**
     * KafkaTemplate 의존성 주입
     *
     * Spring Kafka가 제공하는 고수준 Kafka 클라이언트입니다.
     * application.yaml의 Kafka 설정을 기반으로 자동 구성됩니다.
     *
     * 제네릭 타입 <String, String>:
     * - 첫 번째 String: 메시지 키(Key) 타입
     *   - 동일한 키를 가진 메시지는 같은 파티션으로 전송
     *   - 여기서는 novelId를 키로 사용 (소설별로 파티션 분산)
     * - 두 번째 String: 메시지 값(Value) 타입
     *   - 실제 전송할 데이터 (JSON 문자열)
     *
     * KafkaTemplate의 주요 메서드:
     * - send(topic, key, value): 메시지 전송
     * - sendDefault(key, value): 기본 토픽으로 전송
     * - flush(): 버퍼에 있는 모든 메시지 즉시 전송
     */
    private val kafkaTemplate: KafkaTemplate<String, String>,

    /**
     * ObjectMapper 의존성 주입
     *
     * Jackson 라이브러리의 핵심 클래스로, Java 객체를 JSON으로 변환합니다.
     * Spring Boot가 자동으로 빈으로 등록하여 제공합니다.
     *
     * 주요 메서드:
     * - writeValueAsString(obj): 객체 → JSON 문자열
     * - readValue(json, Class): JSON 문자열 → 객체
     *
     * 설정 (application.yaml에서 커스터마이징 가능):
     * - 날짜 형식
     * - null 필드 포함 여부
     * - 필드 이름 변환 규칙 (camelCase ↔ snake_case)
     */
    private val objectMapper: ObjectMapper
) {
    /**
     * 로거 인스턴스
     *
     * SLF4J를 사용하여 로그를 출력합니다.
     * javaClass: Kotlin의 reflection으로 현재 클래스 정보 가져오기
     * 로그 출력 시 클래스 이름이 함께 표시됨
     *
     * 로그 레벨:
     * - ERROR: 에러 발생 시
     * - WARN: 경고 (잠재적 문제)
     * - INFO: 일반 정보 (주요 이벤트)
     * - DEBUG: 디버깅 정보
     * - TRACE: 상세 추적 정보
     */
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * companion object
     *
     * Kotlin의 static 멤버를 정의하는 영역입니다.
     * 클래스 인스턴스와 무관하게 접근 가능한 상수나 메서드를 선언합니다.
     */
    companion object {
        /**
         * Kafka 토픽 이름
         *
         * const val: 컴파일 타임 상수
         * - 변경 불가능한 값
         * - 컴파일 시점에 인라인 처리되어 성능 향상
         *
         * "novel-reviews": 소설 리뷰 로그를 위한 토픽
         * - Python Consumer가 이 토픽을 구독
         * - 토픽은 Kafka 서버에 미리 생성되어 있어야 함
         *   (또는 auto.create.topics.enable=true 설정)
         */
        const val REVIEW_TOPIC = "novel-reviews"
    }

    /**
     * 리뷰 로그를 Kafka로 비동기 전송
     *
     * 리뷰 작성 후 Python 감성 분석 서비스에게 분석 요청을 보내기 위해 호출됩니다.
     * 메시지 전송은 비동기로 처리되어 API 응답 시간에 영향을 주지 않습니다.
     *
     * 처리 흐름:
     * 1. MDC에서 traceId 추출 (로그 추적용)
     * 2. 로그 데이터 맵 생성 (Map<String, Any>)
     * 3. Jackson으로 JSON 문자열 변환
     * 4. Kafka로 비동기 전송
     * 5. CompletableFuture로 전송 결과 처리
     *    - 성공: INFO 로그 출력
     *    - 실패: ERROR 로그 출력
     *
     * @param novelId 소설 ID (Kafka 메시지 키로 사용)
     * @param authorId 작가 ID
     * @param reviewContent 리뷰 내용
     */
    fun sendReviewLog(novelId: Long, authorId: Long, reviewContent: String) {
        // MDC (Mapped Diagnostic Context)에서 traceId 가져오기
        // traceId: 요청마다 부여되는 고유 ID (로그 추적에 사용)
        // MDC.get("traceId"): 현재 스레드의 MDC에서 traceId 조회
        // ?: "NO-TRACE": traceId가 없으면 기본값 사용
        //
        // MDC는 ThreadLocal을 사용하여 스레드별로 독립적인 데이터 저장
        // 동일한 요청의 모든 로그에 같은 traceId가 포함되어 추적 가능
        val traceId = MDC.get("traceId") ?: "NO-TRACE"

        // Kafka로 전송할 로그 데이터 구성
        // Map 형태로 데이터 구조화 (Key-Value 쌍)
        val logData = mapOf(
            // 요청 추적 ID (로그 상관관계 분석에 사용)
            "traceId" to traceId,

            // 타임스탬프 (밀리초 단위의 Unix Epoch Time)
            // System.currentTimeMillis(): 1970-01-01 00:00:00 UTC 이후의 밀리초
            // 예: 1733202000000 = 2025-12-03 10:00:00
            "timestamp" to System.currentTimeMillis(),

            // 소설 ID
            "novelId" to novelId,

            // 작가 ID
            "authorId" to authorId,

            // 리뷰 내용 (Python이 감성 분석할 텍스트)
            "content" to reviewContent
        )

        // try-catch: 예외 처리 블록
        // JSON 변환 또는 Kafka 전송 중 예외 발생 가능
        try {
            // Map 객체를 JSON 문자열로 변환
            // objectMapper.writeValueAsString(): 직렬화 (Serialization)
            //
            // 변환 결과 예시:
            // {
            //   "traceId": "abc123",
            //   "timestamp": 1733202000000,
            //   "novelId": 1,
            //   "authorId": 1,
            //   "content": "이 소설 정말 재미있어요!"
            // }
            val jsonLog = objectMapper.writeValueAsString(logData)

            // Kafka로 메시지 전송 (비동기)
            // kafkaTemplate.send() 메서드는 CompletableFuture를 반환
            //
            // 파라미터:
            // 1. REVIEW_TOPIC: 토픽 이름 ("novel-reviews")
            // 2. novelId.toString(): 메시지 키 (String 타입으로 변환)
            //    - 같은 novelId를 가진 메시지는 동일한 파티션으로 전송
            //    - 순서 보장 (같은 파티션 내에서는 전송 순서대로 처리)
            // 3. jsonLog: 메시지 값 (JSON 문자열)
            //
            // CompletableFuture<SendResult<String, String>>:
            // - 비동기 작업의 결과를 나타내는 Future 객체
            // - 전송 완료 시 SendResult 객체 반환
            // - 전송 실패 시 예외 발생
            val future: CompletableFuture<SendResult<String, String>> =
                kafkaTemplate.send(REVIEW_TOPIC, novelId.toString(), jsonLog)

            // CompletableFuture의 완료 또는 예외 처리
            // whenComplete: 비동기 작업 완료 후 실행할 콜백 함수 등록
            //
            // 파라미터:
            // - result: 성공 시 SendResult 객체 (전송 결과 메타데이터 포함)
            // - ex: 실패 시 Throwable 객체 (예외 정보)
            //
            // 둘 중 하나만 null이 아님:
            // - 성공: result != null, ex == null
            // - 실패: result == null, ex != null
            future.whenComplete { result, ex ->
                if (ex == null) {
                    // 전송 성공 시 INFO 로그 출력
                    // result.recordMetadata: Kafka 서버가 반환한 메타데이터
                    //   - offset(): 메시지가 저장된 위치 (오프셋 번호)
                    //   - partition(): 메시지가 저장된 파티션 번호
                    //   - timestamp(): 메시지가 저장된 시각
                    //
                    // offset 정보는 디버깅 및 모니터링에 유용:
                    // - 메시지 전송 순서 확인
                    // - Consumer가 어디까지 읽었는지 추적
                    // - 메시지 유실 여부 확인
                    log.info("Kafka 전송 성공: novelId=$novelId, offset=${result.recordMetadata.offset()}")
                } else {
                    // 전송 실패 시 ERROR 로그 출력
                    // ex: 예외 객체 (스택 트레이스 포함)
                    //
                    // 전송 실패 원인:
                    // - Kafka 서버 다운
                    // - 네트워크 오류
                    // - 토픽이 존재하지 않음
                    // - 직렬화 오류
                    // - 권한 부족
                    log.error("Kafka 전송 실패: novelId=$novelId", ex)
                }
            }

            // 함수 종료 (비동기 전송은 백그라운드에서 계속 진행)
            // API 응답은 즉시 반환되고, Kafka 전송은 별도 스레드에서 처리됨

        } catch (e: Exception) {
            // JSON 변환 또는 Kafka 전송 중 동기적 예외 발생 시
            // (예: ObjectMapper 설정 오류, KafkaTemplate 초기화 실패)
            //
            // whenComplete의 예외 처리와 차이점:
            // - try-catch: 동기 코드에서 발생한 예외
            // - whenComplete: 비동기 작업에서 발생한 예외
            log.error("Kafka 전송 중 예외 발생: novelId=$novelId", e)
        }

        // 참고: 이 메서드는 void(Unit)를 반환
        // 전송 성공 여부와 무관하게 즉시 리턴
        // 실패해도 예외를 throw하지 않음 (로그만 출력)
        // 이유: Kafka 전송 실패가 리뷰 저장 실패로 이어지면 안 됨
    }
}

/*
 * Kafka Producer 설계 원칙:
 *
 * 1. 비동기 처리 (Asynchronous Processing)
 *    - send() 메서드는 즉시 리턴 (블로킹하지 않음)
 *    - CompletableFuture로 결과 처리
 *    - API 응답 시간에 영향 없음
 *
 * 2. Fire-and-Forget vs Synchronous
 *    - 현재 구현: Fire-and-Forget (결과 대기 안 함)
 *    - 대안: future.get() 호출로 동기식 처리 가능
 *    - 트레이드오프: 성능 vs 신뢰성
 *
 * 3. 에러 처리 (Error Handling)
 *    - 전송 실패 시 로그만 출력 (예외 throw 안 함)
 *    - 재시도는 KafkaTemplate 설정으로 처리 가능
 *    - DLQ (Dead Letter Queue) 활용 권장
 *
 * 4. 메시지 키 전략 (Message Key Strategy)
 *    - novelId를 키로 사용 → 같은 소설의 리뷰는 같은 파티션
 *    - 순서 보장: 같은 파티션 내에서는 FIFO
 *    - 파티셔닝: 키의 해시값으로 파티션 결정
 *
 * 5. JSON 직렬화 (JSON Serialization)
 *    - Map → JSON 문자열 변환
 *    - Python Consumer와 호환성 보장
 *    - 스키마 변경 시 하위 호환성 고려 필요
 *
 * 6. 모니터링 (Monitoring)
 *    - traceId로 요청 추적
 *    - offset으로 메시지 위치 추적
 *    - 실패율, 지연 시간 모니터링 필수
 *
 * 7. 성능 최적화 (Performance Optimization)
 *    - 배치 전송: linger.ms 설정으로 묶어서 전송
 *    - 압축: compression.type 설정 (gzip, snappy 등)
 *    - 버퍼 크기: buffer.memory 튜닝
 *
 * 8. 신뢰성 보장 (Reliability)
 *    - acks=all: 모든 replica 확인 후 응답
 *    - retries: 재시도 횟수 설정
 *    - enable.idempotence: 중복 전송 방지
 */
