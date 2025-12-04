package com.sparta.novel_insight.service

// SLF4J 로깅
import org.slf4j.LoggerFactory
import org.slf4j.MDC
// Spring AMQP (RabbitMQ 추상화 라이브러리)
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import java.util.*

/**
 * RabbitMQ 메시지 발행자 (Producer) 서비스
 *
 * RabbitMQ는 AMQP (Advanced Message Queuing Protocol) 프로토콜을 구현한 메시지 브로커입니다.
 * Kafka와 함께 MSA 환경에서 널리 사용되는 메시징 시스템입니다.
 *
 * RabbitMQ vs Kafka 비교:
 * - RabbitMQ: 실시간 알림, 작은 메시지, 라우팅 유연성
 * - Kafka: 대용량 로그, 이벤트 스트리밍, 영속성 보장
 *
 * 이 프로젝트에서 RabbitMQ 사용 목적:
 * - 작가에게 실시간 알림 전송
 * - 짧은 메시지 빠른 전달
 * - Push 알림 시스템과 연동
 *
 * IoC (Inversion of Control) 적용:
 * - @Service 어노테이션으로 Spring 컨테이너가 빈(Bean) 관리
 * - 객체 생성 및 생명주기를 Spring이 제어
 * - 개발자는 비즈니스 로직에만 집중
 *
 * DI (Dependency Injection) 적용:
 * - 생성자 주입 방식으로 RabbitTemplate 주입
 * - Spring이 자동으로 의존성 해결 및 주입
 * - 테스트 시 Mock 객체로 쉽게 대체 가능
 */
@Service
class RabbitProducer(
    /**
     * RabbitTemplate 의존성 주입
     *
     * Spring AMQP가 제공하는 RabbitMQ 메시지 전송을 위한 템플릿 클래스입니다.
     * application.yaml의 RabbitMQ 설정을 기반으로 자동 구성됩니다.
     *
     * 주요 메서드:
     * - convertAndSend(): 객체를 메시지로 변환하여 전송
     * - send(): 직접 Message 객체 전송
     * - sendAndReceive(): 동기식 RPC 호출
     *
     * 생성자 주입의 장점:
     * 1. 불변성 보장 (val 사용)
     * 2. 순환 참조 컴파일 시점 감지
     * 3. 테스트 용이성
     * 4. 명시적 의존성 표현
     */
    private val rabbitTemplate: RabbitTemplate
) {
    /**
     * 로거 인스턴스
     *
     * SLF4J (Simple Logging Facade for Java):
     * - 로깅 추상화 인터페이스
     * - 실제 구현체는 Logback, Log4j2 등
     * - 구현체 변경 시 코드 수정 불필요
     *
     * companion object vs 필드 로거:
     * - companion object: static 멤버 (메모리 효율적)
     * - 인스턴스 필드: 각 객체마다 생성 (비효율적)
     * - 일반적으로 companion object 사용 권장
     */
    private val log = LoggerFactory.getLogger(RabbitProducer::class.java)

    /**
     * RabbitMQ로 알림 메시지 전송
     *
     * 리뷰 작성 시 작가에게 실시간 알림을 보내기 위해 사용됩니다.
     * 메시지는 RabbitMQ 큐에 저장되고, Consumer가 읽어서 처리합니다.
     *
     * 메시지 흐름:
     * 1. Producer (이 메서드) → Exchange → Queue
     * 2. Consumer (RabbitConsumer) ← Queue
     *
     * Exchange와 Routing Key:
     * - Exchange: 메시지를 받아서 어떤 Queue로 보낼지 결정하는 라우터
     * - Routing Key: Exchange가 Queue를 선택하는 기준
     * - Exchange 타입:
     *   - Direct: Routing Key 정확히 일치
     *   - Topic: 패턴 매칭 (*.notification, novel.#)
     *   - Fanout: 모든 Queue에 브로드캐스트
     *   - Headers: 헤더 기반 라우팅
     *
     * MDC (Mapped Diagnostic Context):
     * - 스레드별로 독립적인 컨텍스트 정보 저장
     * - 로그에 traceId를 포함하여 요청 추적
     * - 분산 시스템에서 트랜잭션 추적에 필수
     * - ThreadLocal 기반 구현
     *
     * @param message 전송할 알림 메시지 (예: "새 리뷰: 정말 재미있어요...")
     */
    fun sendNotification(message: String) {
        // MDC에서 traceId 추출 또는 새로 생성
        // traceId: 요청의 전체 생명주기를 추적하는 고유 ID
        // UUID.randomUUID().substring(0, 8): 짧은 ID 생성 (가독성)
        val traceId = MDC.get("traceId") ?: UUID.randomUUID().toString().substring(0, 8)

        // 메시지 전송 시작 로그
        // {} 플레이스홀더: 로그 성능 최적화 (String concatenation 회피)
        log.info("RabbitMQ로 메시지 전송 시도: {}", message)

        /**
         * convertAndSend 메서드 파라미터 설명:
         *
         * 1. Exchange 이름: "" (빈 문자열 = Default Exchange)
         *    - Default Exchange는 RabbitMQ가 자동 제공
         *    - Routing Key와 Queue 이름이 동일하면 직접 전달
         *    - 별도 Exchange 설정 불필요
         *
         * 2. Routing Key: "novel.notification"
         *    - 메시지를 라우팅할 기준
         *    - Default Exchange에서는 Queue 이름과 동일하게 사용
         *    - RabbitConfig에서 novel.notification 큐 생성
         *
         * 3. Message: message (실제 전송할 데이터)
         *    - Spring AMQP가 자동으로 직렬화 (Serialization)
         *    - 기본 직렬화: Java Serialization 또는 JSON
         *
         * 4. MessagePostProcessor (람다): 메시지 후처리
         *    - 메시지 전송 직전에 헤더 추가 등 수정 가능
         *    - traceId를 헤더에 추가하여 Consumer에서 사용
         */
        // 람다 표현식: { m -> ... }
        // m: Message 객체 (org.springframework.amqp.core.Message)
        // 람다는 마지막 수정된 Message 객체를 반환해야 함
        rabbitTemplate.convertAndSend("", "novel.notification", message) { m ->
            // 메시지 헤더에 traceId 추가
            // headers: Map<String, Object> 형태
            // Consumer에서 이 traceId를 추출하여 MDC에 설정
            m.messageProperties.headers["traceId"] = traceId

            // 수정된 Message 객체 반환 (필수!)
            // Kotlin에서 람다의 마지막 표현식이 자동으로 반환됨
            m
        }

        // 전송 완료 로그
        // RabbitMQ는 기본적으로 비동기 전송
        // 실제로는 메시지가 큐에 저장되었는지 확인하지 않음
        // 신뢰성이 중요하면 Publisher Confirms 사용 권장
        log.info("전송 완료! (비동기 전송)")
    }
}

/*
 * RabbitMQ 설계 패턴 및 고려사항:
 *
 * 1. Producer-Consumer 패턴
 *    - Producer: 메시지 생성 및 전송
 *    - Consumer: 메시지 수신 및 처리
 *    - Queue: Producer와 Consumer 사이의 버퍼
 *    - 느슨한 결합 (Loose Coupling)
 *
 * 2. 메시지 영속성 (Message Durability)
 *    - Queue와 Message를 디스크에 저장
 *    - RabbitMQ 재시작 후에도 메시지 유실 방지
 *    - RabbitConfig에서 durable = true 설정
 *
 * 3. Publisher Confirms (전송 확인)
 *    - 메시지가 Queue에 안전하게 저장되었는지 확인
 *    - 네트워크 오류 시 재전송 가능
 *    - 신뢰성 향상하지만 성능 저하
 *
 * 4. Dead Letter Queue (DLQ)
 *    - 처리 실패한 메시지를 저장하는 특별한 큐
 *    - Consumer 에러 시 메시지 유실 방지
 *    - 재처리 또는 수동 확인 가능
 *
 * 5. Prefetch Count
 *    - Consumer가 한 번에 가져갈 메시지 개수
 *    - 1로 설정: 순차 처리 (공평한 분배)
 *    - 높게 설정: 처리 속도 향상 (불균형 가능)
 *
 * 6. 메시지 TTL (Time To Live)
 *    - 메시지 유효 기간 설정
 *    - 오래된 알림은 의미 없으므로 자동 삭제
 *    - Queue 또는 Message 단위 설정 가능
 *
 * 7. 트랜잭션 vs Confirms
 *    - 트랜잭션: 완벽한 신뢰성, 성능 저하 큼
 *    - Confirms: 높은 신뢰성, 성능 영향 적음
 *    - 대부분 Confirms 사용 권장
 *
 * 8. Connection Pool
 *    - RabbitTemplate은 Connection Pool 사용
 *    - 매번 새로운 연결 생성하지 않음
 *    - 성능 및 리소스 효율성
 *
 * 9. 에러 처리 전략
 *    - Retry: 자동 재시도 (일시적 오류)
 *    - DLQ: Dead Letter Queue로 이동
 *    - Logging: 에러 로그 기록 및 모니터링
 *    - Alert: 심각한 에러 시 알림 발송
 *
 * 10. 모니터링
 *     - RabbitMQ Management UI (http://localhost:15672)
 *     - Metrics: Queue 길이, 처리 속도, 에러율
 *     - Alerting: 임계값 초과 시 알림
 */
