package com.sparta.novel_insight.controller

import com.sparta.novel_insight.client.PythonClient
import com.sparta.novel_insight.dto.AnalysisRequest
import com.sparta.novel_insight.dto.AnalysisResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * ========================================
 * AnalysisController - 감정 분석 API 컨트롤러
 * ========================================
 *
 * [핵심 개념]
 * 1. MSA(Microservices Architecture)에서 서비스 간 통신을 담당하는 컨트롤러
 * 2. Feign Client를 사용하여 Python 감정 분석 서비스와 동기적으로 통신
 * 3. Spring Cloud OpenFeign을 활용한 선언적(Declarative) HTTP 클라이언트 패턴
 *
 * [실무 적용 - MSA 서비스 간 통신]
 * - Spring Boot(Java/Kotlin) ↔ FastAPI(Python) 간 REST API 통신
 * - 각 서비스는 독립적으로 배포/확장 가능 (Polyglot Architecture)
 * - 언어가 달라도 HTTP/JSON 표준 프로토콜로 통신 가능
 *
 * [Feign Client vs 다른 통신 방법]
 * ┌────────────────────────────────────────────────────────────────┐
 * │ 방법              │ 장점                  │ 단점                │
 * ├────────────────────────────────────────────────────────────────┤
 * │ RestTemplate      │ 간단함                │ Deprecated (Spring 5+) │
 * │ WebClient         │ 비동기, Reactive      │ 학습 곡선 높음        │
 * │ Feign Client      │ 선언적, 간결함         │ 동기 방식 (블로킹)    │
 * │ gRPC              │ 성능 우수, Binary      │ HTTP/2 필요, 복잡함   │
 * └────────────────────────────────────────────────────────────────┘
 *
 * [IoC/DI 적용 분석]
 * - @RestController: Spring이 이 클래스를 빈으로 관리 (IoC)
 * - 생성자 주입으로 PythonClient 의존성 자동 주입 (DI)
 * - Spring이 런타임에 Feign 인터페이스의 프록시 구현체를 생성하여 주입
 *
 * [5~6년차 개발자가 반드시 알아야 할 필수 개념]
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 1. 동기 vs 비동기 통신
 *    - 현재: 동기 방식 → 응답 올 때까지 스레드 블로킹 (최대 5초)
 *    - 문제점: 많은 요청 시 스레드 풀 고갈 가능
 *    - 개선안: WebClient + 비동기 처리 또는 메시징(Kafka/RabbitMQ) 사용
 *
 * 2. 타임아웃 설정의 중요성
 *    - Feign 기본 타임아웃: connectTimeout(10초), readTimeout(60초)
 *    - 실무: 반드시 명시적 설정 필요 (보통 3~5초)
 *    - 이유: 서비스 장애 시 연쇄 장애(Cascading Failure) 방지
 *
 * 3. Circuit Breaker 패턴 (미적용 - 추후 개선 필요)
 *    - 현재: Python 서비스 다운 시 계속 요청 → 리소스 낭비
 *    - 필요: Resilience4j Circuit Breaker 적용
 *    - 동작: 일정 실패율 초과 시 자동으로 요청 차단 (Open 상태)
 *
 * 4. 로드 밸런싱 (미적용 - 단일 인스턴스)
 *    - 실무: Python 서비스를 여러 인스턴스로 운영
 *    - Spring Cloud LoadBalancer 또는 Ribbon 사용
 *    - Kubernetes 환경: Service Discovery로 자동 로드 밸런싱
 *
 * 5. 서비스 디스커버리
 *    - 현재: 하드코딩된 URL (application.yml에 python-service:8000)
 *    - 실무: Eureka, Consul, Kubernetes DNS 등으로 동적 발견
 *    - 이점: 서비스 IP 변경 시에도 자동 대응
 *
 * [Feign Client 내부 동작 원리]
 * 1. Spring Boot 시작 시 @FeignClient 스캔
 * 2. PythonClient 인터페이스를 발견하면 JDK Dynamic Proxy 생성
 * 3. 런타임에 메소드 호출 시:
 *    a. Method Interceptor가 가로챔 (AOP)
 *    b. 메소드 시그니처 분석 → HTTP 요청 생성
 *       - @PostMapping → POST 메소드
 *       - @RequestBody → JSON 직렬화
 *       - URL: http://python-service:8000/analyze
 *    c. HTTP 클라이언트로 실제 요청 전송
 *    d. 응답 JSON → AnalysisResponse 객체로 역직렬화
 *    e. 호출자에게 반환
 *
 * [REST API 설계 원칙]
 * - POST /api/analyze: 리소스 생성/처리 작업에 적합
 * - @RequestBody: HTTP Body에 JSON 페이로드 전달
 * - ResponseEntity: HTTP 상태 코드와 응답 본문을 함께 제어
 *
 * [에러 핸들링 전략 (현재 미흡 - 개선 필요)]
 * 현재 문제점:
 * - Python 서비스 타임아웃 시 500 에러만 반환
 * - 사용자에게 명확한 에러 메시지 없음
 *
 * 개선 방안:
 * @ControllerAdvice로 전역 예외 처리
 * - FeignException.TimeoutException → 504 Gateway Timeout
 * - FeignException.ServiceUnavailable → 503 Service Unavailable
 * - 상세한 에러 응답 DTO 반환 (errorCode, message, timestamp)
 *
 * [성능 최적화 고려사항]
 * 1. 커넥션 풀 설정
 *    - Feign 내부적으로 Apache HttpClient 또는 OkHttp 사용
 *    - maxConnections, maxConnectionsPerRoute 튜닝 필요
 *
 * 2. 캐싱 전략
 *    - 동일 텍스트 반복 분석 시 Redis 캐싱 고려
 *    - @Cacheable 어노테이션 활용
 *
 * 3. 비동기 처리
 *    - 응답이 즉시 필요 없다면 Kafka로 메시지 발행
 *    - 사용자에게는 202 Accepted 즉시 반환
 *
 * [실무 체크리스트]
 * ✓ 타임아웃 설정 확인
 * ✓ Circuit Breaker 적용 검토
 * ✓ 로깅 및 모니터링 (응답 시간, 에러율)
 * ✓ 재시도 정책 (Retry with Exponential Backoff)
 * ✓ 보안 (API 키, OAuth2 토큰 전달)
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 */
@RestController  // IoC: Spring 컨테이너가 이 클래스를 빈으로 관리 (컴포넌트 스캔)
class AnalysisController(
    /**
     * [DI - 생성자 주입 방식]
     * - Spring이 PythonClient 인터페이스의 프록시 구현체를 자동 주입
     * - 주입 시점: 컨트롤러 빈 생성 시 (애플리케이션 시작 시)
     *
     * [왜 생성자 주입을 사용하는가?]
     * 1. 불변성(Immutability): val로 선언 가능 → Thread-safe
     * 2. 순환 참조 방지: 컴파일 타임에 순환 의존성 감지
     * 3. 테스트 용이성: Mock 객체 주입이 쉬움
     * 4. 명시적 의존성: 클래스가 무엇을 필요로 하는지 명확
     *
     * [필드 주입 vs 생성자 주입]
     * ❌ 필드 주입 (@Autowired):
     *    - 테스트 시 리플렉션 필요
     *    - 불변성 보장 불가 (var만 가능)
     * ✅ 생성자 주입 (권장):
     *    - Spring 4.3+부터 생성자가 하나면 @Autowired 생략 가능
     *    - Kotlin에서 더욱 간결하게 작성 가능
     */
    private val pythonClient: PythonClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [POST /api/analyze - 텍스트 감정 분석 API]
     *
     * 클라이언트 요청 → Spring Boot → Feign Client → Python FastAPI → 감정 분석 결과 반환
     *
     * [요청 예시]
     * POST /api/analyze
     * Content-Type: application/json
     * {
     *   "text": "이 소설 정말 재미있어요!"
     * }
     *
     * [응답 예시]
     * HTTP 200 OK
     * {
     *   "result": "긍정",
     *   "score": 0.92
     * }
     *
     * [실행 흐름 - 동기 통신 방식]
     * 1. 클라이언트(프론트엔드)가 POST 요청
     * 2. Spring DispatcherServlet이 요청 수신
     * 3. @RequestBody로 JSON → AnalysisRequest 객체 자동 변환 (Jackson)
     * 4. pythonClient.analyzeText() 호출
     *    ↓
     * 5. Feign Proxy가 HTTP POST http://python-service:8000/analyze 요청 생성
     * 6. Python FastAPI 서비스가 감정 분석 수행 (KoNLPy 사용)
     * 7. Python이 JSON 응답 반환
     *    ↓
     * 8. Feign이 JSON → AnalysisResponse 객체로 변환
     * 9. ResponseEntity.ok()로 200 OK + 응답 본문 반환
     *
     * [주의사항]
     * - 동기 방식이므로 Python 응답이 올 때까지 현재 스레드 대기 (블로킹)
     * - Python 서비스가 느리면 이 API도 느려짐
     * - 타임아웃 발생 시 FeignException 발생 → 500 에러
     *
     * [개선 포인트]
     * - 비동기 처리: @Async + CompletableFuture 또는 WebClient 사용
     * - 에러 핸들링: try-catch로 FeignException 처리
     * - 검증: @Valid로 요청 본문 유효성 검증
     */
    @PostMapping("/api/analyze")
    fun analyze(@RequestBody request: AnalysisRequest): ResponseEntity<AnalysisResponse> {
        log.info("Java: 파이썬에게 분석 요청 보냄... (${request.text})")

        /**
         * [중요] 마치 로컬 메소드 호출처럼 보이지만, 실제로는:
         * 1. HTTP 요청 생성 (POST http://python-service:8000/analyze)
         * 2. JSON 직렬화 (request → {"text": "..."})
         * 3. 네트워크 전송
         * 4. Python 서비스 처리 대기 (여기서 스레드 블로킹!)
         * 5. 응답 수신 및 역직렬화 (JSON → AnalysisResponse)
         *
         * 이 모든 과정이 Feign Proxy가 자동으로 처리함
         */
        val response = pythonClient.analyzeText(request)

        log.info("Java: 응답 받음! -> ${response.result}")
        return ResponseEntity.ok(response)
    }
}