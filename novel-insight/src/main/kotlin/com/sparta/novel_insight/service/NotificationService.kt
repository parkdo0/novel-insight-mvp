package com.sparta.novel_insight.service

import org.springframework.stereotype.Service

/**
 * 알림 전송 서비스
 *
 * 실제 프로덕션 환경에서는 이메일, SMS, Push 알림을 전송하는 서비스입니다.
 * 현재는 동기식 처리로 구현되어 있어 성능 문제가 있는 예제입니다.
 *
 * 문제점 (안티패턴):
 * - Thread.sleep(3000)으로 3초 동안 블로킹
 * - API 응답 시간이 3초 이상 지연됨
 * - 사용자 경험 저하
 * - 서버 리소스 낭비 (스레드 블로킹)
 *
 * 개선 방안:
 * 1. @Async 어노테이션 사용 (Spring 비동기 처리)
 * 2. RabbitMQ/Kafka로 메시지 큐 사용 (현재 프로젝트에서 사용 중)
 * 3. 별도의 알림 서비스 분리 (MSA)
 * 4. CompletableFuture 사용 (비동기 프로그래밍)
 *
 * IoC/DI 적용:
 * - @Service 어노테이션으로 Spring 컨테이너가 빈 관리
 * - 다른 서비스에서 의존성 주입으로 사용 가능
 * - 테스트 시 Mock 객체로 대체 가능
 */
@Service
class NotificationService {

    /**
     * 작가에게 알림 전송 (동기식 - 안티패턴 예제)
     *
     * 실무에서 이렇게 구현하면 안 되는 코드입니다.
     * 교육 목적으로 동기식 처리의 문제점을 보여주기 위한 예제입니다.
     *
     * 문제점 분석:
     * 1. Thread.sleep(3000): 현재 스레드를 3초 동안 블로킹
     *    - 톰캣 스레드 풀의 스레드를 3초 동안 점유
     *    - 동시 처리 가능한 요청 수 감소
     *    - 스레드 풀 고갈 위험 (스레드가 모두 블로킹되면 새 요청 처리 불가)
     *
     * 2. println 사용: 프로덕션 코드에서는 안티패턴
     *    - 로그 레벨 제어 불가
     *    - 로그 파일 저장 불가
     *    - 성능 저하 (System.out은 동기화됨)
     *    - SLF4J Logger 사용 권장
     *
     * 3. 예외 처리 불충분:
     *    - e.printStackTrace(): 단순 콘솔 출력
     *    - 로그 관리 시스템에 기록되지 않음
     *    - 에러 추적 및 모니터링 불가
     *
     * 개선된 코드 예시:
     * ```kotlin
     * @Service
     * class NotificationService {
     *     private val log = LoggerFactory.getLogger(javaClass)
     *
     *     @Async("notificationExecutor")  // 비동기 실행
     *     fun sendToAuthor() {
     *         try {
     *             log.info("알림 발송 시작...")
     *             // 실제 이메일/SMS 발송 로직
     *             emailService.send(...)
     *             log.info("알림 발송 완료!")
     *         } catch (e: Exception) {
     *             log.error("알림 발송 실패", e)
     *             // 재시도 로직 또는 DLQ로 이동
     *         }
     *     }
     * }
     * ```
     *
     * @Async 어노테이션 사용 시:
     * - 별도의 스레드 풀에서 실행
     * - 호출한 메서드는 즉시 리턴 (비블로킹)
     * - TaskExecutor 설정 필요
     * - 반환 타입은 void 또는 Future/CompletableFuture
     *
     * 스레드 풀 설정 예시 (Config 클래스):
     * ```kotlin
     * @Configuration
     * @EnableAsync  // @Async 활성화
     * class AsyncConfig {
     *     @Bean("notificationExecutor")
     *     fun notificationExecutor(): Executor {
     *         return ThreadPoolTaskExecutor().apply {
     *             corePoolSize = 5          // 기본 스레드 개수
     *             maxPoolSize = 10          // 최대 스레드 개수
     *             queueCapacity = 100       // 큐 크기
     *             setThreadNamePrefix("notification-")
     *             initialize()
     *         }
     *     }
     * }
     * ```
     */
    fun sendToAuthor() {
        // 일부러 3초 동안 멈추게 함 (메일 서버 통신 지연 시뮬레이션)
        // 실무에서는 절대 사용하면 안 되는 코드!
        println("알림 발송 시작... (동기 방식)")

        try {
            // Thread.sleep(): 현재 스레드를 지정된 시간만큼 일시 정지
            // InterruptedException: 스레드가 대기 중일 때 interrupt() 호출 시 발생
            // 스레드 풀 환경에서는 InterruptedException 처리 필수
            Thread.sleep(3000) // 3초 대기 (3000 밀리초)

        } catch (e: InterruptedException) {
            // InterruptedException 처리
            // 스레드 인터럽트 상태 복원 필수!
            Thread.currentThread().interrupt()

            // printStackTrace(): 스택 트레이스를 콘솔에 출력
            // 프로덕션에서는 사용 금지!
            // 대신 Logger 사용: log.error("알림 발송 중단", e)
            e.printStackTrace()
        }

        println("알림 발송 완료!")
    }
}

/*
 * 비동기 처리 패턴 및 고려사항:
 *
 * 1. @Async 어노테이션
 *    - Spring이 제공하는 메서드 레벨 비동기 실행
 *    - @EnableAsync로 활성화 필요
 *    - 프록시 기반 AOP 구현
 *    - self-invocation 주의 (같은 클래스 내 호출 시 비동기 동작 안 함)
 *
 * 2. CompletableFuture
 *    - Java 8의 비동기 프로그래밍 API
 *    - 콜백 체이닝 가능
 *    - thenApply, thenCompose, thenCombine 등
 *    - 예외 처리: exceptionally, handle
 *
 * 3. Reactive Programming
 *    - Spring WebFlux (Reactor)
 *    - Mono, Flux 사용
 *    - 논블로킹 I/O
 *    - 높은 처리량, 적은 리소스
 *
 * 4. 메시지 큐 (RabbitMQ, Kafka)
 *    - 현재 프로젝트에서 사용 중
 *    - 느슨한 결합
 *    - 확장성 우수
 *    - 메시지 영속성
 *
 * 5. 스레드 풀 설정
 *    - corePoolSize: 기본 스레드 개수
 *    - maxPoolSize: 최대 스레드 개수
 *    - queueCapacity: 큐 크기
 *    - keepAliveTime: 유휴 스레드 제거 시간
 *
 * 6. 예외 처리
 *    - @Async 메서드의 예외는 호출자에게 전파 안 됨
 *    - AsyncUncaughtExceptionHandler 설정 필요
 *    - 로깅 및 모니터링 필수
 *
 * 7. 트랜잭션 주의사항
 *    - @Async + @Transactional 함께 사용 시 주의
 *    - 별도 스레드에서 실행되므로 트랜잭션 전파 안 됨
 *    - @Async 메서드 내부에 @Transactional 적용
 *
 * 8. 성능 모니터링
 *    - 스레드 풀 사용률 모니터링
 *    - 큐 길이 모니터링
 *    - 처리 시간 측정
 *    - Rejected Execution 발생 시 알림
 *
 * 9. 백프레셔 (Backpressure)
 *    - 작업 속도가 처리 속도를 초과할 때 대응
 *    - 큐가 가득 차면 어떻게 할 것인가?
 *    - 정책: 거부(reject), 대기(wait), 폐기(discard)
 *
 * 10. 디버깅 및 테스트
 *     - 비동기 코드는 디버깅이 어려움
 *     - 로깅 전략 중요
 *     - 통합 테스트 필수
 *     - @Async 테스트 시 await() 또는 Thread.sleep() 필요
 */
