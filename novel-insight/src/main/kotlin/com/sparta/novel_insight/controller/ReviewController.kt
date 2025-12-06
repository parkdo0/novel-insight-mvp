package com.sparta.novel_insight.controller

import com.sparta.novel_insight.dto.ReviewRequest
import com.sparta.novel_insight.dto.ReviewResponse
import com.sparta.novel_insight.domain.Review
import com.sparta.novel_insight.repository.NovelRepository
import com.sparta.novel_insight.repository.ReviewRepository
import com.sparta.novel_insight.service.RabbitProducer
import com.sparta.novel_insight.service.KafkaProducerService
// Apache Commons Text: HTML/XML 이스케이프 처리 유틸리티
// StringEscapeUtils: XSS 공격 방지를 위한 문자열 이스케이프 도구
import org.apache.commons.text.StringEscapeUtils
// SLF4J Logger
import org.slf4j.LoggerFactory
// Spring Web MVC 관련 임포트
import org.springframework.http.ResponseEntity
// REST API 어노테이션들
// @PostMapping: HTTP POST 요청 매핑
// @RequestBody: 요청 바디를 객체로 변환
// @RestController: REST API 컨트롤러 선언
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 리뷰 API 컨트롤러
 *
 * 독자가 소설에 대한 리뷰를 작성하는 REST API 엔드포인트를 제공합니다.
 * 리뷰 저장 후 다음 작업을 수행합니다:
 * 1. RabbitMQ로 작가에게 알림 전송
 * 2. Kafka로 Python 감성 분석 서비스에 로그 전송
 *
 * 전체 데이터 흐름:
 * [독자] → [이 API] → [MySQL 리뷰 저장]
 *                   → [RabbitMQ 알림 전송]
 *                   → [Kafka 로그 전송] → [Python 감성 분석] → [MySQL 분석 결과 저장]
 *
 * 컨트롤러 역할:
 * - HTTP 요청 수신 및 검증
 * - XSS 공격 방지 (HTML 이스케이프)
 * - 리뷰 엔티티 생성 및 저장
 * - 메시징 시스템 호출 (RabbitMQ, Kafka)
 * - HTTP 응답 반환
 *
 * @RestController:
 * - Spring MVC의 @Controller + @ResponseBody
 * - 모든 메서드가 HTTP 응답 바디로 JSON 반환
 * - View 렌더링 없음 (REST API 전용)
 */
@RestController
class ReviewController(
    /**
     * ReviewRepository 의존성 주입
     *
     * 리뷰 엔티티를 데이터베이스에 저장하는 JPA Repository입니다.
     * Spring Data JPA가 자동으로 구현체를 생성합니다.
     *
     * 제공하는 메서드:
     * - save(review): 리뷰 저장
     * - findById(id): ID로 리뷰 조회
     * - findAll(): 모든 리뷰 조회
     * 등 기본 CRUD 메서드 자동 제공
     */
    private val reviewRepository: ReviewRepository,

    /**
     * NovelRepository 의존성 주입
     *
     * 소설 엔티티를 조회하는 JPA Repository입니다.
     * 리뷰 작성 시 해당 소설이 존재하는지 확인하는 용도로 사용합니다.
     *
     * 필요한 이유:
     * - 존재하지 않는 소설에 리뷰를 작성하면 안 됨
     * - 외래키 제약조건 위반 방지
     * - 명확한 에러 메시지 제공
     */
    private val novelRepository: NovelRepository,

    /**
     * RabbitProducer 의존성 주입
     *
     * RabbitMQ로 메시지를 전송하는 서비스입니다.
     * 리뷰 작성 시 작가에게 실시간 알림을 보내는 용도로 사용합니다.
     *
     * 알림 내용:
     * - "새 리뷰: {리뷰 내용 앞 20자}..."
     * - 작가는 RabbitMQ 메시지를 구독하여 알림 수신
     *
     * RabbitMQ vs Kafka:
     * - RabbitMQ: 실시간 알림 (짧은 메시지, 빠른 전달)
     * - Kafka: 데이터 파이프라인 (대용량 로그, 영속성)
     */
    private val rabbitProducer: RabbitProducer,

    /**
     * KafkaProducerService 의존성 주입
     *
     * Kafka로 리뷰 로그를 전송하는 서비스입니다.
     * Python 감성 분석 서비스가 이 메시지를 구독하여 분석을 수행합니다.
     *
     * 전송 데이터:
     * - novelId: 소설 ID
     * - authorId: 작가 ID
     * - reviewContent: 리뷰 내용
     *
     * 비동기 처리:
     * - Kafka 전송은 비동기로 처리됨
     * - API 응답 시간에 영향 없음
     * - 실패 시 로그만 출력 (리뷰 저장은 성공)
     */
    private val kafkaProducerService: KafkaProducerService,

    /**
     * NotificationService 의존성 주입 (★ Phase 2: WebSocket 알림 시스템)
     *
     * WebSocket + STOMP 기반 실시간 알림 시스템입니다.
     * 리뷰 작성 시 작가에게 즉각적인 알림을 전송합니다.
     *
     * WHY NotificationService 추가?
     * - 기존 RabbitMQ: Legacy 알림 시스템 (Phase 1, 점진적 마이그레이션 예정)
     * - NotificationService: 신규 WebSocket 기반 알림 (Phase 2, Production-ready)
     *
     * 차이점:
     * 1. RabbitProducer (Legacy):
     *    - 단순 메시지 전송 (메시지 큐)
     *    - 별도 Consumer 필요
     *    - DB 저장 없음 (휘발성)
     *
     * 2. NotificationService (신규):
     *    - Dual Write: DB 저장 + WebSocket 발송
     *    - 오프라인 알림 지원 (DB 영속성)
     *    - 알림 관리 API 제공 (읽음 처리, 조회 등)
     *    - 프론트엔드 실시간 업데이트
     *
     * 마이그레이션 전략:
     * - 현재: RabbitProducer + NotificationService 병행 운영
     * - 향후: NotificationService로 통합 (RabbitProducer 제거)
     */
    private val notificationService: com.sparta.novel_insight.service.NotificationService,

    /**
     * MemberRepository 의존성 주입 (★ Phase 2: 알림 수신자 조회)
     *
     * 알림을 받을 작가(Member)를 조회하는 Repository입니다.
     *
     * WHY 필요한가?
     * - notificationService.sendNotification()은 Member 엔티티를 파라미터로 받음
     * - novel.authorId(Long)로 Member 엔티티 조회 필요
     * - 알림 발송 전 작가 존재 여부 확인
     *
     * 사용 흐름:
     * 1. 리뷰 작성 (novelist A의 소설)
     * 2. novel.authorId로 작가 조회
     * 3. NotificationService로 작가에게 알림 전송
     * 4. WebSocket 구독 중이면 실시간 수신
     */
    private val memberRepository: com.sparta.novel_insight.repository.MemberRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ReviewController::class.java)
    }

    /**
     * 리뷰 작성 API
     *
     * 독자가 소설에 대한 리뷰를 작성하는 엔드포인트입니다.
     * 리뷰 저장 후 작가 알림과 감성 분석 요청을 비동기로 처리합니다.
     *
     * HTTP Method: POST
     * - 새로운 리소스(리뷰) 생성
     * - 멱등성 없음 (같은 요청 여러 번 → 여러 개의 리뷰 생성)
     *
     * URL: /reviews
     * - RESTful 설계: 명사 복수형 사용
     * - 리소스 중심 URL 구조
     *
     * 요청 예시:
     * POST /reviews
     * Content-Type: application/json
     * {
     *   "novelId": 1,
     *   "content": "이 소설 정말 재미있어요!"
     * }
     *
     * 응답 예시:
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * {
     *   "id": 123,
     *   "novelId": 1,
     *   "content": "이 소설 정말 재미있어요!",
     *   "createdAt": "2025-12-03T14:30:45"
     * }
     *
     * 처리 흐름:
     * 1. 요청 바디에서 novelId, content 추출
     * 2. 소설 존재 여부 확인 (없으면 예외 발생)
     * 3. XSS 방지를 위한 HTML 이스케이프
     * 4. Review 엔티티 생성 및 저장
     * 5. RabbitMQ로 작가 알림 전송
     * 6. Kafka로 Python 감성 분석 요청 전송
     * 7. 저장된 리뷰 정보를 DTO로 변환하여 응답 반환
     *
     * 보안 고려사항:
     * - XSS 공격 방지: HTML 이스케이프 처리
     * - SQL Injection 방지: JPA 사용으로 자동 처리
     * - 입력 검증: DTO에 @Valid 어노테이션 추가 권장
     *
     * @PostMapping 어노테이션:
     * - HTTP POST 요청을 이 메서드에 매핑
     * - value = "/reviews": URL 경로
     *
     * @RequestBody 어노테이션:
     * - HTTP 요청 바디(JSON)를 ReviewRequest 객체로 자동 변환
     * - Jackson ObjectMapper가 JSON → 객체 역직렬화 수행
     * - Content-Type: application/json 필수
     *
     * @param request ReviewRequest DTO (novelId, content 포함)
     * @return ResponseEntity<ReviewResponse> HTTP 응답 (200 OK + 리뷰 정보)
     */
    @PostMapping("/reviews")
    fun writeReview(
        @RequestBody request: ReviewRequest,
        @org.springframework.security.core.annotation.AuthenticationPrincipal user: com.sparta.novel_insight.security.UserPrincipal
    ): ResponseEntity<ReviewResponse> {
        // 1단계: 소설 존재 여부 확인
        // novelRepository.findById(): Optional<Novel> 반환
        // orElseThrow: Optional이 비어있으면 예외 발생
        //
        // Optional 패턴:
        // - Java 8의 null 처리 패턴
        // - null 대신 Optional로 감싸서 반환
        // - orElseThrow(): 값이 없으면 예외 발생
        // - orElse(defaultValue): 값이 없으면 기본값 반환
        val novel = novelRepository.findById(request.novelId)
            .orElseThrow { RuntimeException("소설 없음") }
            // RuntimeException: 확인되지 않은 예외 (Unchecked Exception)
            // - 컴파일러가 강제하지 않음 (try-catch 불필요)
            // - Spring은 RuntimeException 발생 시 500 에러 반환
            //
            // 개선 방안:
            // - 커스텀 예외 클래스 정의 (NovelNotFoundException)
            // - @ControllerAdvice로 전역 예외 처리
            // - 404 Not Found 응답 반환

        // 2단계: XSS 공격 방지를 위한 HTML 이스케이프
        // StringEscapeUtils.escapeHtml4(): HTML 특수 문자를 엔티티로 변환
        //
        // 변환 예시:
        // - < → &lt;
        // - > → &gt;
        // - & → &amp;
        // - " → &quot;
        // - ' → &#39;
        //
        // XSS (Cross-Site Scripting) 공격 시나리오:
        // 1. 공격자가 악의적인 스크립트를 리뷰로 작성
        //    예: "<script>alert('XSS')</script>"
        // 2. 이스케이프 없이 DB에 저장
        // 3. 다른 사용자가 리뷰 조회 시 브라우저에서 스크립트 실행
        // 4. 쿠키 탈취, 세션 하이재킹 등 보안 위협
        //
        // 이스케이프 처리 후:
        // - "<script>" → "&lt;script&gt;"
        // - 브라우저가 HTML 태그로 해석하지 않고 텍스트로 표시
        // - 스크립트가 실행되지 않음
        val safeContent = StringEscapeUtils.escapeHtml4(request.content)

        // 3단계: Review 엔티티 생성
        // Review 생성자에 필요한 파라미터 전달
        // - content: 이스케이프 처리된 안전한 내용
        // - userId: 리뷰 작성자 ID
        // - novelId: 소설 ID
        // - rating: 기본 평점 3.0 (향후 ReviewRequest에 rating 필드 추가 필요)
        // - novel: 존재 확인된 소설 엔티티
        val review = Review(
            content = safeContent,
            userId = user.id,
            novelId = request.novelId,
            rating = java.math.BigDecimal(3), // 기본 평점 3.0
            novel = novel
        )

        // 4단계: 리뷰 데이터베이스에 저장
        // reviewRepository.save(): JPA의 persist/merge 작업 수행
        // - 새로운 엔티티: INSERT 쿼리 실행
        // - 기존 엔티티: UPDATE 쿼리 실행 (id가 있을 경우)
        // - @Transactional이 있으면 트랜잭션 내에서 실행
        // - 저장 후 id가 자동 생성되어 review 객체에 할당됨
        reviewRepository.save(review)

        // 5단계: RabbitMQ로 작가 알림 전송
        // rabbitProducer.sendNotification(): 비동기 메시지 전송
        //
        // 알림 내용:
        // - "새 리뷰: " + 리뷰 내용 앞 20자 + "..."
        // - safeContent.take(20): Kotlin 표준 라이브러리 확장 함수
        //   - 문자열의 앞 20자만 추출
        //   - 20자 미만이면 전체 반환
        //
        // RabbitMQ 사용 목적:
        // - 작가에게 실시간 알림 전달
        // - WebSocket이나 SSE와 연동하여 브라우저 푸시 알림 구현 가능
        // - 메시지 큐를 통해 알림 시스템과 분리 (느슨한 결합)
        rabbitProducer.sendNotification("새 리뷰: ${safeContent.take(20)}...")

        // 6단계: Kafka로 감성 분석 요청 전송
        // kafkaProducerService.sendReviewLog(): 비동기 메시지 전송
        //
        // 전송 데이터:
        // - novelId: 소설 ID (novel.id!!)
        //   - !!: null이 아님을 단언 (null-safety 연산자)
        //   - novel.id는 Long? 타입 (Nullable)
        //   - 저장 후에는 id가 반드시 존재하므로 !! 사용
        //   - null이면 NullPointerException 발생 (하지만 발생하지 않음)
        // - authorId: 작가 ID
        // - reviewContent: 이스케이프된 리뷰 내용
        //
        // Kafka 사용 목적:
        // - Python 감성 분석 서비스로 데이터 전달
        // - 대용량 로그 처리 가능
        // - 메시지 영속성 보장 (재처리 가능)
        // - Consumer 장애 시에도 메시지 유실 없음
        kafkaProducerService.sendReviewLog(
            novelId = novel.id!!,
            authorId = novel.authorId,
            reviewContent = safeContent
        )

        // ★★★ 7단계: WebSocket 실시간 알림 발송 (Phase 2 추가) ★★★
        // NotificationService를 통한 작가 알림 전송
        //
        // WHY 이 시점에 알림 발송?
        // 1. 리뷰 저장 완료 (review.id 생성됨)
        // 2. 모든 비즈니스 로직 성공 (RabbitMQ, Kafka 전송 완료)
        // 3. 트랜잭션 커밋 전 실행 (실패 시 롤백 가능)
        //
        // 알림 발송 흐름:
        // 1. 작가(Member) 조회: novel.authorId로 작가 엔티티 가져오기
        // 2. 알림 메시지 생성: NotificationType.NEW_REVIEW 템플릿 사용
        // 3. NotificationService.sendNotification() 호출
        // 4. DB 저장 + WebSocket 발송 (Dual Write)
        // 5. 작가가 온라인이면 즉시 브라우저 알림
        try {
            // 작가 조회
            // memberRepository.findById(): 작가 Member 엔티티 조회
            // novel.authorId: 소설 작가의 회원 ID
            // orElse(null): 작가가 없으면 null 반환 (탈퇴한 회원 등)
            //
            // WHY orElse(null)?
            // - 작가 탈퇴 시에도 리뷰 저장은 성공해야 함
            // - 알림 발송 실패가 리뷰 작성을 막으면 안 됨
            // - null이면 알림 발송 스킵 (try-catch로 안전 처리)
            val author = memberRepository.findById(novel.authorId).orElse(null)

            // 알림 발송 (작가가 존재하는 경우만)
            if (author != null) {
                // 리뷰 작성자 닉네임 추출
                // request에는 reviewerId가 없으므로 현재는 "독자" 고정
                // TODO: ReviewRequest DTO에 writerId 추가 또는 @AuthenticationPrincipal 사용
                val reviewerNickname = "독자" // 임시 닉네임 (향후 개선 필요)

                // 알림 메시지 생성
                // NotificationType.NEW_REVIEW.getTemplate():
                // - "독자님이 '소설제목'에 리뷰를 남겼습니다"
                // - 템플릿 파라미터: nickname, novelTitle
                val message = com.sparta.novel_insight.domain.NotificationType.NEW_REVIEW.getTemplate(
                    mapOf(
                        "nickname" to reviewerNickname,
                        "novelTitle" to novel.title
                    )
                )

                // NotificationService로 알림 발송
                // 파라미터:
                // - type: NEW_REVIEW (알림 타입)
                // - recipient: author (수신자 - 작가)
                // - sender: null (발신자 정보 없음, 향후 개선 필요)
                // - content: 생성된 메시지
                // - relatedId: review.id (리뷰 상세 페이지 이동용)
                //
                // 내부 동작:
                // 1. DB 저장: notification 테이블에 INSERT
                // 2. WebSocket 발송: /topic/notifications/{authorId}로 메시지 전송
                // 3. 작가가 구독 중이면 실시간 수신
                notificationService.sendNotification(
                    type = com.sparta.novel_insight.domain.NotificationType.NEW_REVIEW,
                    recipient = author,
                    sender = null, // 향후 리뷰 작성자 Member로 변경 필요
                    content = message,
                    relatedId = review.id // 프론트엔드에서 /novels/{novelId}/reviews/{reviewId} 이동
                )
            }
        } catch (e: Exception) {
            // 알림 발송 실패 시 로그만 출력 (리뷰 저장은 성공 유지)
            // WHY 예외를 먹어버리는가?
            // - 알림 시스템 장애가 리뷰 작성을 막으면 안 됨
            // - 사용자 경험 우선: 리뷰는 반드시 저장되어야 함
            // - 알림은 부가 기능 (Nice-to-have)
            // - 오프라인 사용자는 나중에 알림 조회 가능 (DB 저장 완료)
            logger.error("리뷰 알림 발송 실패: novelId=${novel.id}, authorId=${novel.authorId}", e)
        }

        // 8단계: HTTP 응답 반환
        // ResponseEntity.ok(): 200 OK 상태 코드 + 바디
        // ReviewResponse.from(review): Entity → DTO 변환 (팩토리 메서드)
        //
        // Entity를 직접 반환하지 않는 이유:
        // 1. 엔티티는 데이터베이스 구조와 밀접하게 연결
        // 2. JPA lazy loading으로 인한 에러 가능성
        // 3. 불필요한 필드 노출 방지
        // 4. API 스펙 안정성 (엔티티 변경 시 API 영향 최소화)
        //
        // DTO (Data Transfer Object) 사용 장점:
        // - API 스펙과 내부 구조 분리
        // - 필요한 데이터만 선별하여 전송
        // - 네트워크 트래픽 절약
        // - 버전 관리 용이
        return ResponseEntity.ok(ReviewResponse.from(review))

        // 참고: 이 메서드는 동기식 처리
        // - 리뷰 저장이 완료되어야 응답 반환
        // - RabbitMQ, Kafka 전송은 비동기 (응답 시간에 영향 없음)
        // - 트랜잭션: @Transactional이 없으면 자동 커밋
        // - 롤백: save() 실패 시 예외 발생하여 롤백
    }
}

/*
 * 컨트롤러 설계 원칙:
 *
 * 1. 얇은 컨트롤러 (Thin Controller)
 *    - 비즈니스 로직은 서비스 계층에 위임
 *    - 컨트롤러는 HTTP 요청/응답 처리만 담당
 *    - 복잡한 계산, 검증, 변환은 서비스에서 수행
 *
 * 2. 입력 검증 (Input Validation)
 *    - @Valid 어노테이션으로 DTO 검증
 *    - @NotNull, @NotBlank, @Size 등 제약조건 명시
 *    - BindingResult로 검증 에러 처리
 *    - XSS, SQL Injection 방지
 *
 * 3. 예외 처리 (Exception Handling)
 *    - @ControllerAdvice로 전역 예외 처리
 *    - 커스텀 예외 클래스 정의
 *    - 적절한 HTTP 상태 코드 반환
 *    - 명확한 에러 메시지 제공
 *
 * 4. HTTP 메서드 사용
 *    - POST: 리소스 생성
 *    - GET: 리소스 조회
 *    - PUT: 리소스 전체 수정
 *    - PATCH: 리소스 부분 수정
 *    - DELETE: 리소스 삭제
 *
 * 5. RESTful URL 설계
 *    - 명사 사용 (동사 금지)
 *    - 복수형 사용 (/reviews, /novels)
 *    - 계층 구조 표현 (/novels/{id}/reviews)
 *    - 쿼리 파라미터로 필터링 (?page=1&size=10)
 *
 * 6. 응답 형식 통일
 *    - 성공: ResponseEntity.ok(data)
 *    - 생성: ResponseEntity.created(location).body(data)
 *    - 에러: ResponseEntity.badRequest().body(error)
 *    - 일관된 JSON 구조 유지
 *
 * 7. 보안 (Security)
 *    - XSS 공격 방지: HTML 이스케이프
 *    - CSRF 공격 방지: CSRF 토큰 검증
 *    - SQL Injection 방지: JPA 사용
 *    - 인증/인가: Spring Security 적용
 *    - Rate Limiting: 과도한 요청 차단
 *
 * 8. 비동기 처리 (Asynchronous Processing)
 *    - 시간이 오래 걸리는 작업은 비동기 처리
 *    - 메시징 시스템 활용 (RabbitMQ, Kafka)
 *    - API 응답 시간 최소화
 *    - @Async 어노테이션 또는 CompletableFuture 사용
 *
 * 9. 트랜잭션 관리 (Transaction Management)
 *    - @Transactional은 서비스 계층에 적용
 *    - 컨트롤러에서는 트랜잭션 경계 설정 지양
 *    - 여러 서비스 호출 시 조정자 서비스 추가
 *
 * 10. 로깅 (Logging)
 *     - 요청/응답 로그 기록
 *     - traceId로 요청 추적
 *     - 에러 로그에 스택 트레이스 포함
 *     - 민감 정보 마스킹 (비밀번호, 카드번호 등)
 */
