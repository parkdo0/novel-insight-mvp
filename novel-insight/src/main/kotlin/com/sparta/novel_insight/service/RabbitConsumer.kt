package com.sparta.novel_insight.service

import org.slf4j.LoggerFactory
import org.slf4j.MDC
// Spring AMQP 메시지 리스너 어노테이션
import org.springframework.amqp.rabbit.annotation.RabbitListener
// Spring Messaging - 메시지 헤더 추출용
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Service

/**
 * RabbitMQ 메시지 소비자 (Consumer) 서비스
 *
 * Producer가 전송한 메시지를 수신하여 처리하는 리스너입니다.
 * @RabbitListener 어노테이션으로 특정 Queue를 구독합니다.
 *
 * 메시지 처리 흐름:
 * 1. RabbitMQ Queue에 메시지 도착
 * 2. Spring AMQP가 자동으로 이 메서드 호출 (AOP)
 * 3. 메시지 처리 로직 실행
 * 4. 성공 시 자동 ACK (Acknowledgment)
 * 5. 실패 시 재시도 또는 DLQ로 이동
 *
 * AOP (Aspect-Oriented Programming) 적용:
 * - @RabbitListener는 AOP 기반 프록시 패턴
 * - Spring이 런타임에 프록시 객체 생성
 * - 실제 메서드 호출 전후로 메시지 수신/ACK 처리
 * - 개발자는 비즈니스 로직만 구현
 *
 * 트랜잭션 추적 (Distributed Tracing):
 * - Producer에서 생성한 traceId를 메시지 헤더로 전달받음
 * - MDC에 설정하여 모든 로그에 traceId 포함
 * - 분산 시스템에서 요청 전체 흐름 추적 가능
 * - Zipkin, Jaeger 등 APM 도구와 연동 가능
 */
@Service
class RabbitConsumer {

    /**
     * 로거 인스턴스
     *
     * 실무에서 companion object로 선언하는 것이 일반적:
     * ```
     * companion object {
     *     private val log = LoggerFactory.getLogger(RabbitConsumer::class.java)
     * }
     * ```
     *
     * 이유:
     * - static 멤버로 클래스 로딩 시 한 번만 생성
     * - 메모리 효율적 (모든 인스턴스가 공유)
     * - 성능상 이점 (매번 생성하지 않음)
     */
    private val log = LoggerFactory.getLogger(RabbitConsumer::class.java)

    /**
     * RabbitMQ 메시지 리스너 메서드
     *
     * @RabbitListener 어노테이션:
     * - Spring AMQP가 제공하는 메시지 리스너 등록 어노테이션
     * - queues 속성: 구독할 Queue 이름 배열
     * - 여러 Queue를 동시에 구독 가능
     * - 메시지 도착 시 자동으로 이 메서드 호출
     *
     * AOP 프록시 동작 방식:
     * 1. Spring이 RabbitConsumer의 프록시 객체 생성
     * 2. 프록시가 RabbitMQ와 연결 유지
     * 3. 메시지 도착 시 프록시가 감지
     * 4. 프록시가 실제 메서드 호출 (before advice)
     * 5. 메서드 실행 완료 후 ACK 전송 (after advice)
     * 6. 예외 발생 시 NACK 또는 재시도 (exception advice)
     *
     * @Header 어노테이션:
     * - 메시지 헤더에서 특정 값 추출
     * - @Header("traceId"): traceId 헤더 값 추출
     * - String?: Nullable 타입 (헤더가 없을 수 있음)
     * - Producer에서 설정한 traceId를 받아옴
     *
     * MDC (Mapped Diagnostic Context) 사용:
     * - ThreadLocal 기반 컨텍스트 저장소
     * - 현재 스레드의 로그에 자동으로 traceId 포함
     * - 분산 트랜잭션 추적에 필수
     * - 반드시 finally 블록에서 clear() 호출 필요
     *
     * @param message Queue에서 수신한 메시지 본문
     * @param traceId 메시지 헤더의 traceId (Nullable)
     */
    @RabbitListener(queues = ["novel.notification"])
    fun receiveMessage(message: String, @Header("traceId") traceId: String?) {

        // MDC에 traceId 설정
        // traceId ?: "NO_ID": null이면 "NO_ID" 사용 (Elvis operator)
        //
        // MDC.put() 이유:
        // - 이후 모든 로그에 자동으로 traceId 포함
        // - Logback pattern: %X{traceId}로 출력 가능
        // - Producer → Consumer로 traceId가 이어짐
        // - 전체 요청 흐름을 하나의 ID로 추적
        MDC.put("traceId", traceId ?: "NO_ID")

        // try-finally 패턴:
        // - finally는 예외 발생 여부와 무관하게 항상 실행
        // - MDC 정리는 반드시 수행되어야 함 (메모리 누수 방지)
        try {
            // 메시지 수신 로그
            // {} 플레이스홀더: SLF4J의 파라미터 치환
            // traceId가 MDC에 있으면 로그에 자동 포함됨
            log.info("[Consumer] 메시지 수신! (TraceId 이어받음): {}", message)

            // 작업 시뮬레이션: 3초 대기
            // 실무에서는:
            // - DB 저장
            // - 외부 API 호출 (이메일, SMS, Push 알림)
            // - 캐시 업데이트
            // - 통계 집계
            Thread.sleep(3000)

            // 작업 완료 로그
            log.info("[Consumer] 알림 발송 완료!")

            // 메서드 정상 종료 시 Spring AMQP가 자동으로 ACK 전송
            // ACK (Acknowledgment):
            // - RabbitMQ에게 메시지 처리 완료를 알림
            // - Queue에서 메시지 제거
            // - 다음 메시지 처리 가능

        } finally {
            // MDC 정리 (필수!)
            //
            // MDC.clear() 호출 이유:
            // 1. ThreadLocal 메모리 누수 방지
            //    - ThreadLocal은 스레드가 종료되기 전까지 메모리 유지
            //    - 스레드 풀 환경에서 스레드 재사용 시 이전 데이터 남음
            //
            // 2. 스레드 오염 방지
            //    - 다음 메시지 처리 시 이전 traceId가 남아있으면 혼란
            //    - 잘못된 traceId로 로그가 기록됨
            //
            // 3. 보안
            //    - 민감한 정보가 MDC에 있을 경우 다른 요청에 노출
            //
            // 실무 팁:
            // - Filter나 Interceptor에서 MDC 설정/정리 통일
            // - AOP로 공통 처리 가능
            // - 절대 생략하면 안 되는 코드!
            MDC.clear()
        }

        // 예외 처리 전략:
        // 1. 예외 발생 시 Spring AMQP가 자동으로 NACK 전송
        // 2. RabbitMQ는 메시지를 다시 Queue에 넣거나 DLQ로 이동
        // 3. 재시도 횟수는 RabbitConfig에서 설정 가능
        // 4. 무한 재시도 방지를 위한 Max Retry 설정 필수
    }
}

/*
 * RabbitMQ Consumer 설계 패턴 및 고려사항:
 *
 * 1. 메시지 처리 모드
 *    - Auto ACK: 메시지 수신 즉시 ACK (빠르지만 유실 가능)
 *    - Manual ACK: 처리 완료 후 명시적 ACK (안전하지만 복잡)
 *    - 현재: Auto ACK (기본값)
 *    - 실무에서는 Manual ACK 권장 (신뢰성)
 *
 * 2. Prefetch Count 설정
 *    - Consumer가 동시에 처리할 메시지 개수
 *    - 1: 공평한 분배 (느린 Worker도 많은 작업 받지 않음)
 *    - N: 빠른 처리 (하지만 불균형 가능)
 *    - application.yaml: spring.rabbitmq.listener.simple.prefetch
 *
 * 3. 동시성 (Concurrency)
 *    - 여러 스레드로 동시에 메시지 처리
 *    - @RabbitListener(concurrency = "5-10")
 *    - 최소 5개, 최대 10개 Consumer 스레드
 *    - 처리량 향상하지만 순서 보장 X
 *
 * 4. 에러 처리 및 재시도
 *    - 일시적 오류: 자동 재시도 (네트워크 오류 등)
 *    - 영구적 오류: DLQ로 이동 (잘못된 데이터)
 *    - Retry Template 설정:
 *      - 재시도 횟수
 *      - 재시도 간격 (Exponential Backoff)
 *      - 최대 재시도 시간
 *
 * 5. Dead Letter Queue (DLQ)
 *    - 처리 실패한 메시지를 저장하는 특별한 Queue
 *    - x-dead-letter-exchange 헤더로 설정
 *    - 재시도 횟수 초과 시 자동으로 DLQ로 이동
 *    - 수동 확인 및 재처리 가능
 *
 * 6. Idempotency (멱등성)
 *    - 같은 메시지를 여러 번 처리해도 결과가 동일
 *    - 네트워크 오류로 메시지 중복 수신 가능
 *    - 해결 방법:
 *      - 고유 ID 기반 중복 체크 (DB, Redis)
 *      - Transaction ID 활용
 *      - At-least-once vs Exactly-once 전략
 *
 * 7. 트랜잭션 처리
 *    - @Transactional과 함께 사용 가능
 *    - DB 트랜잭션과 메시지 ACK 동기화
 *    - 주의: 성능 저하 및 복잡도 증가
 *    - 대안: Saga 패턴, Event Sourcing
 *
 * 8. 모니터링 및 로깅
 *    - 처리 시간 측정 (Micrometer Metrics)
 *    - 에러율 추적
 *    - Queue 길이 모니터링
 *    - 느린 Consumer 감지 (Slow Consumer Detection)
 *    - Alerting 설정 (임계값 초과 시 알림)
 *
 * 9. ThreadLocal 주의사항
 *    - MDC는 ThreadLocal 기반
 *    - 스레드 풀 환경에서 clear() 필수
 *    - 비동기 처리 시 MDC 전파 고려
 *    - CompletableFuture, @Async 사용 시 주의
 *
 * 10. 성능 최적화
 *     - Batch Processing: 여러 메시지를 묶어서 처리
 *     - Connection Pool: 연결 재사용
 *     - Message Compression: 메시지 압축
 *     - 불필요한 직렬화/역직렬화 최소화
 *
 * 11. 보안
 *     - 메시지 암호화 (TLS)
 *     - 인증/인가 (AMQP 사용자 권한)
 *     - 민감 정보 마스킹 (로그)
 *     - Rate Limiting (과도한 메시지 방어)
 */
