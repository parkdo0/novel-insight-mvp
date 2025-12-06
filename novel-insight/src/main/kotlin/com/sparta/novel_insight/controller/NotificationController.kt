package com.sparta.novel_insight.controller

import com.sparta.novel_insight.exception.MemberNotFoundException
import com.sparta.novel_insight.exception.NotificationNotFoundException
import com.sparta.novel_insight.exception.UnauthorizedException

import com.sparta.novel_insight.domain.Notification
import com.sparta.novel_insight.repository.MemberRepository
import com.sparta.novel_insight.repository.NotificationRepository
import com.sparta.novel_insight.security.UserPrincipal
import com.sparta.novel_insight.service.NotificationService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

/**
 * 실시간 알림 API 컨트롤러
 *
 * WebSocket + STOMP 기반 실시간 알림 시스템의 REST API 엔드포인트를 제공합니다.
 * 사용자는 이 API를 통해 알림 조회, 읽음 처리, 개수 확인 등을 수행할 수 있습니다.
 *
 * ## 알림 시스템 아키텍처
 *
 * ```
 * [이벤트 발생]
 *     ↓
 * [NotificationService.sendNotification()]
 *     ↓
 *     ├─→ [DB 저장] → [MySQL notification 테이블]
 *     └─→ [WebSocket 발송] → [STOMP Broker] → [구독 중인 클라이언트]
 *
 * [클라이언트]
 *     ├─→ [WebSocket 구독] → /topic/notifications/{userId} → 실시간 알림 수신
 *     └─→ [REST API 호출] → 이 컨트롤러 → 알림 히스토리 조회/관리
 * ```
 *
 * ## Dual Write 전략 (DB + WebSocket)
 *
 * WHY 두 군데에 저장/전송하는가?
 *
 * 1. **DB 저장** (Persistence)
 *    - 오프라인 사용자: 로그인 시 미확인 알림 조회
 *    - 알림 히스토리: 과거 알림 검색 및 관리
 *    - 감사 로그: 알림 발송 기록 추적
 *    - 재전송: WebSocket 실패 시 재시도 가능
 *
 * 2. **WebSocket 발송** (Real-time)
 *    - 온라인 사용자: 즉시 브라우저 알림
 *    - UX 향상: 새로고침 없이 실시간 업데이트
 *    - Push 알림: 브라우저 Notification API 연동 가능
 *
 * 3. **Trade-off**
 *    - 단점: Write 작업 2회 (성능 오버헤드)
 *    - 장점: 사용자 경험 대폭 향상 (오프라인/온라인 모두 대응)
 *    - 결론: UX가 성능보다 중요 → Dual Write 선택
 *
 * ## API vs WebSocket 역할 분담
 *
 * | 기능                 | 담당 기술         | 이유                                |
 * |---------------------|------------------|-------------------------------------|
 * | 실시간 알림 푸시      | WebSocket        | 서버 → 클라이언트 단방향 push 필요    |
 * | 알림 목록 조회        | REST API         | 페이지네이션, 필터링 등 복잡한 쿼리   |
 * | 읽음 처리            | REST API         | RESTful 설계 (PUT 메서드 활용)       |
 * | 알림 개수 조회        | REST API         | 간단한 요청/응답 (WebSocket 불필요)  |
 *
 * ## 프론트엔드 통합 예시
 *
 * ```javascript
 * // 1. WebSocket 연결 및 실시간 알림 구독
 * const stompClient = Stomp.over(new SockJS('/ws'));
 * stompClient.connect({}, (frame) => {
 *   stompClient.subscribe(`/topic/notifications/${userId}`, (message) => {
 *     const notification = JSON.parse(message.body);
 *     showBrowserNotification(notification);  // 브라우저 알림
 *     updateBadge();                          // 뱃지 업데이트
 *   });
 * });
 *
 * // 2. REST API로 알림 히스토리 조회
 * async function loadNotifications() {
 *   const response = await fetch('/api/notifications?page=0');
 *   const data = await response.json();
 *   renderNotificationList(data.content);
 * }
 *
 * // 3. 알림 읽음 처리
 * async function markAsRead(notificationId) {
 *   await fetch(`/api/notifications/${notificationId}/read`, { method: 'PUT' });
 *   updateBadge();
 * }
 * ```
 *
 * ## 보안 고려사항
 *
 * 1. **인증 (Authentication)**
 *    - @AuthenticationPrincipal로 현재 로그인 사용자 확인
 *    - 각 API는 자신의 알림만 조회/수정 가능
 *    - 타인의 알림 접근 시도 시 404 또는 403 반환
 *
 * 2. **WebSocket 보안**
 *    - WebSocketConfig에서 CORS 설정 (허용된 Origin만 접근)
 *    - 향후 개선: JWT 토큰 기반 WebSocket handshake 인증
 *    - StompHeaderAccessor로 세션별 권한 검증 가능
 *
 * 3. **Rate Limiting**
 *    - 알림 조회 API 남용 방지 필요
 *    - Spring Cloud Gateway 또는 Bucket4j 활용 권장
 *
 * ## 성능 최적화
 *
 * 1. **인덱스 활용**
 *    - Notification 엔티티의 composite index 활용
 *    - `(recipient_id, is_read, created_at)` 인덱스로 빠른 조회
 *
 * 2. **페이지네이션**
 *    - 대량의 알림 조회 시 메모리 부담 방지
 *    - Spring Data JPA Pageable 활용
 *
 * 3. **Lazy Loading**
 *    - Member 관계는 FetchType.LAZY로 설정
 *    - 필요한 경우만 JOIN FETCH로 명시적 로딩
 *
 * 4. **캐싱 (선택적)**
 *    - 알림 개수는 캐싱 고려 가능 (Redis)
 *    - TTL 짧게 설정 (5-10초)
 *    - 알림 발송 시 캐시 무효화 필수
 *
 * @RestController
 * - @Controller + @ResponseBody
 * - 모든 메서드가 JSON 응답 반환
 * - View 렌더링 없음 (REST API 전용)
 *
 * @RequestMapping("/api/notifications")
 * - 모든 엔드포인트에 /api/notifications 접두사 적용
 * - RESTful URL 설계: 리소스 중심 명사 복수형
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    /**
     * NotificationService 의존성 주입
     *
     * 알림 비즈니스 로직을 처리하는 서비스 계층입니다.
     * DB 저장 + WebSocket 발송의 Dual Write 전략을 구현합니다.
     *
     * 제공 메서드:
     * - sendNotification(): 알림 생성 및 발송
     * - getUnreadNotifications(): 미확인 알림 조회 (최신 20개)
     * - getUnreadCount(): 미확인 알림 개수
     * - markAsRead(): 단일 알림 읽음 처리
     * - markAllAsRead(): 전체 알림 읽음 처리
     * - getAllNotifications(): 전체 알림 조회 (페이지네이션)
     */
    private val notificationService: NotificationService,

    /**
     * MemberRepository 의존성 주입
     *
     * 사용자 정보를 조회하는 JPA Repository입니다.
     * @AuthenticationPrincipal로 받은 userId로 Member 엔티티를 조회합니다.
     *
     * WHY 필요한가?
     * - NotificationService의 메서드는 Member 엔티티를 파라미터로 받음
     * - UserPrincipal은 인증 정보만 담고 있음 (id, email)
     * - Member 엔티티로 변환하여 서비스 계층에 전달 필요
     *
     * 대안:
     * - NotificationService를 userId(Long) 기반으로 리팩토링 가능
     * - 현재는 Member 기반 API 일관성 유지를 위해 Repository 사용
     */
    private val memberRepository: MemberRepository,

    /**
     * NotificationRepository 의존성 주입
     *
     * 알림 권한 검증을 위해 사용합니다.
     * 사용자가 자신의 알림만 수정/삭제할 수 있도록 제한합니다.
     */
    private val notificationRepository: NotificationRepository
) {

    /**
     * 미확인 알림 조회 API
     *
     * 현재 로그인한 사용자의 읽지 않은 알림 목록을 반환합니다.
     * 최신 순으로 정렬되며, 최대 20개까지 조회됩니다.
     *
     * ## HTTP Method: GET
     * - 리소스 조회 (읽기 전용)
     * - 멱등성 O (여러 번 호출해도 결과 동일)
     * - 캐싱 가능 (Cache-Control 헤더 추가 권장)
     *
     * ## URL: /api/notifications/unread
     * - unread: 필터링 조건을 URL에 명시
     * - RESTful 설계: 명확한 의도 전달
     *
     * ## 요청 예시
     * ```http
     * GET /api/notifications/unread HTTP/1.1
     * Host: api.example.com
     * Authorization: Bearer {JWT_TOKEN}
     * ```
     *
     * ## 응답 예시
     * ```json
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     *
     * [
     *   {
     *     "id": 123,
     *     "type": "NEW_REVIEW",
     *     "content": "홍길동님이 '해리포터'에 리뷰를 남겼습니다",
     *     "relatedId": 456,
     *     "isRead": false,
     *     "createdAt": "2025-12-05T14:30:45",
     *     "sender": {
     *       "id": 789,
     *       "nickname": "홍길동"
     *     }
     *   }
     * ]
     * ```
     *
     * ## WHY 최대 20개로 제한?
     * - UX 관점: 알림 패널은 드롭다운 형태 (전체 보기는 별도 페이지)
     * - 성능: 대량 알림 조회 시 응답 시간 증가 방지
     * - 모바일: 화면 크기 제약으로 많은 알림 표시 어려움
     * - 추가 조회: 전체 알림은 GET /api/notifications?page=N 사용
     *
     * ## 에러 시나리오
     * - 401 Unauthorized: 로그인하지 않은 사용자
     * - 404 Not Found: 사용자 정보 없음 (탈퇴한 회원 등)
     *
     * @AuthenticationPrincipal 어노테이션:
     * - Spring Security가 현재 인증된 사용자 정보를 주입
     * - UserPrincipal 객체로 변환 (CustomUserDetailsService에서 생성)
     * - 로그인하지 않은 경우 null 또는 AnonymousUser
     *
     * @param user 현재 로그인한 사용자 정보 (UserPrincipal)
     * @return ResponseEntity<List<NotificationResponse>> 알림 목록 (200 OK)
     */
    @GetMapping("/unread")
    fun getUnreadNotifications(
        @AuthenticationPrincipal user: UserPrincipal
    ): ResponseEntity<List<NotificationResponse>> {
        // 1단계: 사용자 엔티티 조회
        // memberRepository.findById(): Optional<Member> 반환
        // user.id: UserPrincipal에서 추출한 사용자 ID
        // orElseThrow: 사용자가 없으면 예외 발생 (탈퇴한 회원 등)
        //
        // WHY Optional 사용?
        // - JPA는 findById() 결과가 없을 수 있음 (nullable)
        // - Optional로 감싸서 안전하게 처리
        // - orElseThrow()로 명시적 예외 처리
        val member = memberRepository.findById(user.id)
            .orElseThrow { MemberNotFoundException(user.id) }

        // 2단계: 미확인 알림 조회
        // notificationService.getUnreadNotifications():
        // - DB에서 recipient = member, isRead = false 조회
        // - 최신순 정렬 (createdAt DESC)
        // - 최대 20개 제한 (findTop20By...)
        val notifications = notificationService.getUnreadNotifications(member)

        // 3단계: Entity → DTO 변환
        // map { NotificationResponse.from(it) }:
        // - Kotlin Collection 확장 함수
        // - 각 Notification 엔티티를 NotificationResponse DTO로 변환
        // - it: 람다의 암묵적 파라미터 (현재 Notification 객체)
        //
        // WHY DTO 변환?
        // - Entity를 직접 반환하면 JPA lazy loading 에러 가능
        // - 불필요한 필드 노출 방지 (보안)
        // - API 스펙 안정성 (Entity 변경 시 API 영향 최소화)
        // - JSON 직렬화 최적화
        val response = notifications.map { NotificationResponse.from(it) }

        // 4단계: HTTP 응답 반환
        // ResponseEntity.ok():
        // - 200 OK 상태 코드
        // - Content-Type: application/json (Spring이 자동 설정)
        // - Jackson ObjectMapper가 DTO → JSON 직렬화
        return ResponseEntity.ok(response)
    }

    /**
     * 미확인 알림 개수 조회 API
     *
     * 현재 로그인한 사용자의 읽지 않은 알림 개수를 반환합니다.
     * 주로 헤더 뱃지에 빨간 점 또는 숫자로 표시하는 용도로 사용됩니다.
     *
     * ## HTTP Method: GET
     * - 단순 개수 조회 (읽기 전용)
     * - 멱등성 O
     * - 매우 가벼운 쿼리 (COUNT 쿼리)
     *
     * ## URL: /api/notifications/unread-count
     * - unread-count: 개수 조회 의도 명확히 표현
     * - count만 사용하지 않는 이유: 전체 알림 개수와 구분
     *
     * ## 요청 예시
     * ```http
     * GET /api/notifications/unread-count HTTP/1.1
     * Host: api.example.com
     * Authorization: Bearer {JWT_TOKEN}
     * ```
     *
     * ## 응답 예시
     * ```json
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     *
     * {
     *   "count": 5
     * }
     * ```
     *
     * ## 프론트엔드 통합
     * ```javascript
     * async function updateBadge() {
     *   const response = await fetch('/api/notifications/unread-count');
     *   const data = await response.json();
     *
     *   const badge = document.getElementById('notification-badge');
     *   if (data.count > 0) {
     *     badge.textContent = data.count;
     *     badge.style.display = 'block';  // 뱃지 표시
     *   } else {
     *     badge.style.display = 'none';   // 뱃지 숨김
     *   }
     * }
     *
     * // WebSocket으로 새 알림 수신 시 뱃지 업데이트
     * stompClient.subscribe(`/topic/notifications/${userId}`, (message) => {
     *   updateBadge();  // 실시간 개수 갱신
     * });
     * ```
     *
     * ## 성능 최적화
     * 1. **인덱스 활용**
     *    - Notification 테이블의 (recipient_id, is_read) 인덱스
     *    - COUNT 쿼리가 인덱스 스캔으로 빠르게 실행
     *
     * 2. **캐싱 (선택적)**
     *    - Redis 캐시 적용 가능 (TTL 5-10초)
     *    - 캐시 키: "notification:unread:count:${userId}"
     *    - 알림 발송 시 캐시 무효화 필수
     *    - Trade-off: 실시간성 vs 성능
     *
     * 3. **Polling vs WebSocket**
     *    - 현재: Polling 방식 (주기적으로 API 호출)
     *    - 개선: WebSocket으로 실시간 개수 push
     *    - 방법: 알림 발송 시 개수도 함께 전송
     *
     * @param user 현재 로그인한 사용자 정보
     * @return ResponseEntity<Map<String, Long>> 개수 정보 (200 OK)
     */
    @GetMapping("/unread-count")
    fun getUnreadCount(
        @AuthenticationPrincipal user: UserPrincipal
    ): ResponseEntity<Map<String, Long>> {
        // 1단계: 사용자 엔티티 조회
        val member = memberRepository.findById(user.id)
            .orElseThrow { RuntimeException("사용자를 찾을 수 없습니다") }

        // 2단계: 미확인 알림 개수 조회
        // notificationService.getUnreadCount():
        // - SQL: SELECT COUNT(*) FROM notification WHERE recipient_id = ? AND is_read = false
        // - 인덱스 활용으로 빠른 조회
        // - Long 타입 반환
        val count = notificationService.getUnreadCount(member)

        // 3단계: JSON 응답 생성
        // mapOf("count" to count):
        // - Kotlin의 Map 생성 함수
        // - {"count": 5} 형태의 JSON으로 직렬화됨
        //
        // WHY Map 사용?
        // - 간단한 구조는 별도 DTO 불필요
        // - 향후 필드 추가 가능 (예: hasUnread, lastNotificationTime)
        // - 프론트엔드에서 data.count로 직관적 접근
        return ResponseEntity.ok(mapOf("count" to count))
    }

    /**
     * 단일 알림 읽음 처리 API
     *
     * 특정 알림을 읽음(isRead = true)으로 표시합니다.
     * 사용자가 알림을 클릭하여 상세 페이지로 이동할 때 호출됩니다.
     *
     * ## HTTP Method: PUT
     * - 리소스 상태 변경 (읽음 처리)
     * - 멱등성 O (여러 번 호출해도 결과 동일)
     * - PUT vs PATCH:
     *   - PUT: 리소스 전체 교체
     *   - PATCH: 리소스 일부 수정
     *   - 여기서는 isRead 필드만 변경하므로 PATCH가 더 적합하나,
     *     간단한 상태 변경이므로 PUT 사용 (RESTful 논쟁 있음)
     *
     * ## URL: /api/notifications/{id}/read
     * - {id}: 알림 ID (Path Variable)
     * - /read: 읽음 처리 액션 명시
     * - 대안: PUT /api/notifications/{id} + body: {"isRead": true}
     *   → 현재 방식이 더 명확하고 간단
     *
     * ## 요청 예시
     * ```http
     * PUT /api/notifications/123/read HTTP/1.1
     * Host: api.example.com
     * Authorization: Bearer {JWT_TOKEN}
     * ```
     *
     * ## 응답 예시
     * ```json
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     *
     * {
     *   "message": "알림이 읽음 처리되었습니다"
     * }
     * ```
     *
     * ## 프론트엔드 통합
     * ```javascript
     * async function handleNotificationClick(notificationId, relatedUrl) {
     *   // 1. 읽음 처리
     *   await fetch(`/api/notifications/${notificationId}/read`, {
     *     method: 'PUT',
     *     headers: { 'Authorization': `Bearer ${token}` }
     *   });
     *
     *   // 2. 뱃지 업데이트
     *   updateBadge();
     *
     *   // 3. 관련 페이지로 이동
     *   window.location.href = relatedUrl;  // 예: /novels/1/episodes/5
     * }
     * ```
     *
     * ## 보안 고려사항
     * 1. **권한 검증**
     *    - 현재: NotificationService에서 검증 필요
     *    - 타인의 알림 읽음 처리 시도 시 예외 발생
     *    - 403 Forbidden 반환 권장
     *
     * 2. **존재하지 않는 알림**
     *    - 404 Not Found 반환
     *    - 명확한 에러 메시지 제공
     *
     * ## 에러 시나리오
     * - 401 Unauthorized: 로그인하지 않은 사용자
     * - 404 Not Found: 알림이 존재하지 않음
     * - 403 Forbidden: 타인의 알림 접근 시도 (향후 구현 필요)
     *
     * @PathVariable 어노테이션:
     * - URL 경로의 {id} 부분을 Long 타입 파라미터로 바인딩
     * - 예: /api/notifications/123/read → id = 123
     * - 타입 변환 실패 시 400 Bad Request
     *
     * @param id 알림 ID (Path Variable)
     * @return ResponseEntity<Map<String, String>> 성공 메시지 (200 OK)
     */
    @PutMapping("/{id}/read")
    fun markAsRead(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserPrincipal
    ): ResponseEntity<Map<String, String>> {
        // 1단계: 권한 검증
        // - 현재 로그인 사용자가 알림의 recipient인지 확인
        // - 타인의 알림 수정 시도 시 403 Forbidden 반환
        val notification = notificationRepository.findById(id)
            .orElseThrow { NotificationNotFoundException(id) }

        if (notification.recipient.id != user.id) {
            throw UnauthorizedException("이 알림에 접근할 권한이 없습니다.")
        }

        // 2단계: 알림 읽음 처리
        // notificationService.markAsRead():
        // - DB에서 알림 조회 (findById)
        // - isRead = true로 변경
        // - save() 또는 @Transactional로 자동 커밋
        notificationService.markAsRead(id)

        // 2단계: 성공 메시지 반환
        // mapOf("message" to ...):
        // - 간단한 응답은 Map으로 처리 (DTO 불필요)
        // - 프론트엔드에서 성공 확인 용도
        // - 향후 추가 정보 필요 시 DTO로 변경 가능
        return ResponseEntity.ok(mapOf("message" to "알림이 읽음 처리되었습니다"))
    }

    /**
     * 전체 알림 읽음 처리 API
     *
     * 현재 로그인한 사용자의 모든 미확인 알림을 읽음으로 표시합니다.
     * "모두 읽음" 버튼 클릭 시 호출됩니다.
     *
     * ## HTTP Method: PUT
     * - 여러 리소스의 상태를 일괄 변경
     * - 멱등성 O (여러 번 호출해도 결과 동일)
     *
     * ## URL: /api/notifications/read-all
     * - read-all: 전체 읽음 처리 의도 명시
     * - 대안: PUT /api/notifications/bulk-read
     *   → read-all이 더 직관적
     *
     * ## 요청 예시
     * ```http
     * PUT /api/notifications/read-all HTTP/1.1
     * Host: api.example.com
     * Authorization: Bearer {JWT_TOKEN}
     * ```
     *
     * ## 응답 예시
     * ```json
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     *
     * {
     *   "message": "5개의 알림이 읽음 처리되었습니다"
     * }
     * ```
     *
     * ## 프론트엔드 통합
     * ```javascript
     * async function markAllAsRead() {
     *   const response = await fetch('/api/notifications/read-all', {
     *     method: 'PUT',
     *     headers: { 'Authorization': `Bearer ${token}` }
     *   });
     *
     *   const data = await response.json();
     *   alert(data.message);  // "5개의 알림이 읽음 처리되었습니다"
     *
     *   // 뱃지 업데이트 (0으로 변경)
     *   updateBadge();
     *
     *   // 알림 목록 새로고침
     *   loadNotifications();
     * }
     * ```
     *
     * ## 성능 최적화
     * 1. **Bulk Update 쿼리**
     *    - JPA의 @Modifying 쿼리 사용
     *    - SQL: UPDATE notification SET is_read = true WHERE recipient_id = ? AND is_read = false
     *    - 단일 쿼리로 여러 행 업데이트 (N+1 문제 없음)
     *
     * 2. **트랜잭션**
     *    - @Transactional로 원자성 보장
     *    - 모든 알림이 한 번에 업데이트되거나 롤백
     *
     * 3. **영향 받은 행 수**
     *    - NotificationService가 업데이트된 알림 개수 반환
     *    - 프론트엔드에 구체적인 정보 제공
     *
     * ## WHY 개수를 응답에 포함?
     * - 사용자 피드백: "5개의 알림이 읽음 처리되었습니다"
     * - 디버깅: 예상과 다른 개수면 문제 파악 가능
     * - 통계: 사용자 행동 분석 데이터
     *
     * @param user 현재 로그인한 사용자 정보
     * @return ResponseEntity<Map<String, String>> 성공 메시지 (200 OK)
     */
    @PutMapping("/read-all")
    fun markAllAsRead(
        @AuthenticationPrincipal user: UserPrincipal
    ): ResponseEntity<Map<String, String>> {
        // 1단계: 사용자 엔티티 조회
        val member = memberRepository.findById(user.id)
            .orElseThrow { RuntimeException("사용자를 찾을 수 없습니다") }

        // 2단계: 전체 알림 읽음 처리
        // notificationService.markAllAsRead():
        // - Bulk UPDATE 쿼리 실행
        // - 업데이트된 행 수 반환
        // - @Transactional로 원자성 보장
        val updatedCount = notificationService.markAllAsRead(member)

        // 3단계: 성공 메시지 생성
        // 업데이트된 알림 개수를 포함하여 명확한 피드백 제공
        val message = "${updatedCount}개의 알림이 읽음 처리되었습니다"

        return ResponseEntity.ok(mapOf("message" to message))
    }

    /**
     * 전체 알림 조회 API (페이지네이션)
     *
     * 현재 로그인한 사용자의 모든 알림을 페이지 단위로 조회합니다.
     * 읽음/미확인 알림을 모두 포함하며, 최신순으로 정렬됩니다.
     *
     * ## HTTP Method: GET
     * - 리소스 조회 (읽기 전용)
     * - 멱등성 O
     *
     * ## URL: /api/notifications?page=0&size=20
     * - page: 페이지 번호 (0부터 시작)
     * - size: 페이지 크기 (기본값 20)
     * - 쿼리 파라미터로 페이지 제어
     *
     * ## 요청 예시
     * ```http
     * GET /api/notifications?page=0&size=20 HTTP/1.1
     * Host: api.example.com
     * Authorization: Bearer {JWT_TOKEN}
     * ```
     *
     * ## 응답 예시
     * ```json
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     *
     * {
     *   "content": [
     *     {
     *       "id": 123,
     *       "type": "NEW_REVIEW",
     *       "content": "홍길동님이 '해리포터'에 리뷰를 남겼습니다",
     *       "relatedId": 456,
     *       "isRead": false,
     *       "createdAt": "2025-12-05T14:30:45",
     *       "sender": {
     *         "id": 789,
     *         "nickname": "홍길동"
     *       }
     *     }
     *   ],
     *   "pageable": {
     *     "pageNumber": 0,
     *     "pageSize": 20
     *   },
     *   "totalElements": 150,
     *   "totalPages": 8,
     *   "last": false,
     *   "first": true
     * }
     * ```
     *
     * ## 프론트엔드 통합 (무한 스크롤)
     * ```javascript
     * let currentPage = 0;
     * let isLoading = false;
     * let hasMore = true;
     *
     * async function loadMoreNotifications() {
     *   if (isLoading || !hasMore) return;
     *
     *   isLoading = true;
     *   const response = await fetch(`/api/notifications?page=${currentPage}&size=20`);
     *   const data = await response.json();
     *
     *   renderNotifications(data.content);  // 알림 목록에 추가
     *
     *   currentPage++;
     *   hasMore = !data.last;  // 마지막 페이지 여부
     *   isLoading = false;
     * }
     *
     * // 스크롤 이벤트 리스너
     * window.addEventListener('scroll', () => {
     *   if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 100) {
     *     loadMoreNotifications();  // 하단 도달 시 추가 로드
     *   }
     * });
     * ```
     *
     * ## 페이지네이션 vs 커서 기반
     * 1. **현재: 페이지 번호 기반 (Offset-based)**
     *    - 장점: 구현 간단, Spring Data JPA 기본 지원
     *    - 단점: 대량 데이터 시 성능 저하 (OFFSET 10000)
     *    - 적합: 중소 규모 데이터 (수만 건)
     *
     * 2. **대안: 커서 기반 (Cursor-based)**
     *    - 예: ?cursor=2025-12-05T14:30:45
     *    - 장점: 대량 데이터 성능 우수, 실시간 데이터 변경 대응
     *    - 단점: 구현 복잡, 임의 페이지 이동 불가
     *    - 적합: 대규모 데이터, 무한 스크롤
     *
     * ## 정렬 순서
     * - 기본: createdAt DESC (최신순)
     * - 향후 개선: 쿼리 파라미터로 정렬 옵션 제공
     *   - ?sort=createdAt,desc
     *   - ?sort=type,asc&sort=createdAt,desc
     *
     * ## 필터링 (향후 개선)
     * - 알림 타입 필터: ?type=NEW_REVIEW,NEW_COMMENT
     * - 읽음 여부 필터: ?isRead=false
     * - 기간 필터: ?startDate=2025-12-01&endDate=2025-12-31
     *
     * @RequestParam 어노테이션:
     * - 쿼리 파라미터를 메서드 파라미터로 바인딩
     * - defaultValue = "0": page 생략 시 기본값
     * - 타입 변환 실패 시 400 Bad Request
     *
     * @param user 현재 로그인한 사용자 정보
     * @param page 페이지 번호 (0부터 시작, 기본값 0)
     * @return ResponseEntity<Page<NotificationResponse>> 페이지 정보 포함 알림 목록 (200 OK)
     */
    @GetMapping
    fun getAllNotifications(
        @AuthenticationPrincipal user: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int
    ): ResponseEntity<Page<NotificationResponse>> {
        // 1단계: 사용자 엔티티 조회
        val member = memberRepository.findById(user.id)
            .orElseThrow { RuntimeException("사용자를 찾을 수 없습니다") }

        // 2단계: Pageable 객체 생성
        // PageRequest.of():
        // - page: 페이지 번호 (0부터 시작)
        // - 20: 페이지 크기 (한 페이지당 20개)
        // - Sort.by().descending(): 정렬 조건
        //   - "createdAt" 필드 기준 내림차순 (최신순)
        //   - ascending(): 오름차순 (오래된 순)
        //
        // Pageable 인터페이스:
        // - Spring Data JPA의 페이징 추상화
        // - Repository 메서드에 Pageable을 전달하면 자동으로 LIMIT, OFFSET 쿼리 생성
        // - 예: SELECT * FROM notification WHERE recipient_id = ? ORDER BY created_at DESC LIMIT 20 OFFSET 0
        val pageable = PageRequest.of(page, 20, Sort.by("createdAt").descending())

        // 3단계: 전체 알림 조회 (페이지네이션)
        // notificationService.getAllNotifications():
        // - Page<Notification> 반환
        // - 읽음/미확인 모두 포함
        // - Pageable에 따라 쿼리 생성
        val notifications = notificationService.getAllNotifications(member, pageable)

        // 4단계: Page<Notification> → Page<NotificationResponse> 변환
        // notifications.map { NotificationResponse.from(it) }:
        // - Spring Data의 Page.map() 메서드
        // - 각 Notification을 NotificationResponse로 변환
        // - 페이지 메타데이터는 유지 (totalElements, totalPages 등)
        //
        // WHY Page 그대로 반환?
        // - 프론트엔드에서 페이지 네비게이션 구현 용이
        // - 전체 개수, 페이지 수 등 메타데이터 자동 포함
        // - Spring Data JPA의 표준 응답 형식
        val response = notifications.map { NotificationResponse.from(it) }

        return ResponseEntity.ok(response)
    }
}

/**
 * 알림 응답 DTO
 *
 * Notification 엔티티를 JSON으로 직렬화하기 위한 데이터 전송 객체입니다.
 * API 응답의 일관성을 유지하고, 불필요한 필드를 숨기는 역할을 합니다.
 *
 * ## WHY DTO 사용?
 *
 * 1. **API 스펙 안정성**
 *    - Entity 변경 시 API 응답에 직접 영향 방지
 *    - API 버전 관리 용이
 *
 * 2. **보안**
 *    - 민감한 필드 노출 방지 (예: 내부 ID, 메타데이터)
 *    - 필요한 정보만 선별하여 전송
 *
 * 3. **성능**
 *    - JPA Lazy Loading 에러 방지
 *    - JSON 직렬화 최적화
 *    - 네트워크 트래픽 절약
 *
 * 4. **명확성**
 *    - 프론트엔드 개발자에게 명확한 API 스펙 제공
 *    - Swagger/OpenAPI 문서 생성 용이
 *
 * ## 필드 설명
 * - id: 알림 ID (클릭 시 읽음 처리에 사용)
 * - type: 알림 타입 (NEW_REVIEW, NEW_COMMENT 등)
 * - content: 알림 내용 (이미 템플릿 적용된 메시지)
 * - relatedId: 관련 리소스 ID (예: 리뷰 ID, 댓글 ID)
 * - isRead: 읽음 여부 (UI에서 강조 표시 용도)
 * - createdAt: 생성 시각 (상대 시간 표시 용도: "3분 전")
 * - sender: 발신자 정보 (id, nickname만 포함)
 *
 * ## 향후 개선 사항
 * - createdAtRelative 필드 추가 ("3분 전", "1시간 전")
 * - relatedUrl 필드 추가 (클릭 시 이동할 URL)
 * - priority 필드 추가 (우선순위별 정렬 및 강조)
 * - thumbnailUrl 필드 추가 (알림 아이콘 이미지)
 *
 * data class:
 * - Kotlin의 데이터 클래스
 * - equals(), hashCode(), toString() 자동 생성
 * - copy() 메서드 자동 생성 (불변 객체 패턴)
 * - 주로 DTO, Value Object로 사용
 */
data class NotificationResponse(
    val id: Long,
    val type: String,
    val content: String,
    val relatedId: Long?,
    val isRead: Boolean,
    val createdAt: String,
    val sender: SenderInfo?
) {
    /**
     * 발신자 정보 DTO
     *
     * 알림을 보낸 사용자의 간략한 정보입니다.
     * Member 엔티티의 전체 정보를 노출하지 않고, 필요한 필드만 포함합니다.
     *
     * ## 필드 설명
     * - id: 회원 ID (프로필 페이지 이동 용도)
     * - nickname: 닉네임 (UI에 표시)
     *
     * ## WHY email 포함하지 않음?
     * - 프라이버시: 타인의 이메일 노출 방지
     * - 불필요: 알림에서 이메일 정보 필요 없음
     * - 최소 권한 원칙: 필요한 정보만 전달
     */
    data class SenderInfo(
        val id: Long,
        val nickname: String
    )

    companion object {
        /**
         * Entity → DTO 변환 팩토리 메서드
         *
         * Notification 엔티티를 NotificationResponse DTO로 변환합니다.
         * 정적 팩토리 메서드 패턴을 사용하여 변환 로직을 캡슐화합니다.
         *
         * ## WHY 팩토리 메서드?
         * - 생성자보다 명확한 의도 전달 (from = "~로부터 생성")
         * - 변환 로직 중앙화 (여러 곳에서 재사용)
         * - 유연성: 다양한 변환 전략 추가 가능
         *
         * ## 변환 로직
         * 1. 기본 필드 복사 (id, type, content, relatedId, isRead)
         * 2. LocalDateTime → String 변환 (ISO-8601 형식)
         * 3. sender?.let: null-safe 변환 (sender가 null이면 null 반환)
         *
         * @param notification 변환할 Notification 엔티티
         * @return NotificationResponse DTO 객체
         */
        fun from(notification: Notification): NotificationResponse {
            return NotificationResponse(
                id = notification.id!!,  // !! null이 아님을 단언 (DB 저장 후에는 항상 id 존재)
                type = notification.type.name,  // Enum → String
                content = notification.content,
                relatedId = notification.relatedId,  // nullable 그대로 전달
                isRead = notification.isRead,
                createdAt = notification.createdAt.toString(),  // LocalDateTime → ISO-8601 String
                sender = notification.sender?.let {  // null-safe 변환
                    SenderInfo(
                        id = it.id!!,
                        nickname = it.nickname
                    )
                }
            )
        }
    }
}

/*
 * 컨트롤러 설계 회고 및 개선 사항
 *
 * ## 현재 구현의 장점
 * 1. ✅ RESTful 설계 원칙 준수
 * 2. ✅ 명확한 URL 구조
 * 3. ✅ 페이지네이션 지원
 * 4. ✅ DTO 패턴 적용
 * 5. ✅ 상세한 문서화
 *
 * ## 개선이 필요한 부분
 *
 * ### 1. 예외 처리 개선
 * - 현재: RuntimeException 사용
 * - 개선: 커스텀 예외 클래스 정의
 * ```kotlin
 * class MemberNotFoundException(memberId: Long) : RuntimeException("회원을 찾을 수 없습니다: $memberId")
 * class NotificationNotFoundException(notificationId: Long) : RuntimeException("알림을 찾을 수 없습니다: $notificationId")
 * class UnauthorizedNotificationAccessException : RuntimeException("알림에 접근할 권한이 없습니다")
 * ```
 * - @ControllerAdvice로 전역 예외 처리
 *
 * ### 2. 권한 검증 추가
 * - 현재: 타인의 알림 접근 방지 로직 없음
 * - 개선: NotificationService에서 권한 검증
 * ```kotlin
 * fun markAsRead(notificationId: Long, currentUserId: Long) {
 *     val notification = notificationRepository.findById(notificationId)
 *         .orElseThrow { NotificationNotFoundException(notificationId) }
 *
 *     if (notification.recipient.id != currentUserId) {
 *         throw UnauthorizedNotificationAccessException()
 *     }
 *
 *     notification.markAsRead()
 * }
 * ```
 *
 * ### 3. 캐싱 추가 (선택적)
 * - 알림 개수는 캐시 적용 고려
 * ```kotlin
 * @Cacheable(value = ["notificationCount"], key = "#user.id")
 * fun getUnreadCount(@AuthenticationPrincipal user: UserPrincipal): ResponseEntity<*> {
 *     // ...
 * }
 * ```
 * - 알림 발송 시 캐시 무효화
 * ```kotlin
 * @CacheEvict(value = ["notificationCount"], key = "#recipient.id")
 * fun sendNotification(...) {
 *     // ...
 * }
 * ```
 *
 * ### 4. Rate Limiting 추가
 * - Bucket4j 또는 Spring Cloud Gateway 활용
 * ```kotlin
 * @RateLimiter(name = "notifications")
 * @GetMapping("/unread")
 * fun getUnreadNotifications(...) {
 *     // ...
 * }
 * ```
 *
 * ### 5. API 버전 관리
 * - URL에 버전 명시
 * - 예: /api/v1/notifications
 * - 향후 Breaking Change 시 v2 추가
 *
 * ### 6. HATEOAS 적용 (선택적)
 * - 응답에 관련 링크 포함
 * ```json
 * {
 *   "id": 123,
 *   "content": "...",
 *   "_links": {
 *     "self": {"href": "/api/notifications/123"},
 *     "markAsRead": {"href": "/api/notifications/123/read"},
 *     "related": {"href": "/api/novels/1/episodes/5"}
 *   }
 * }
 * ```
 *
 * ### 7. 비동기 처리 (선택적)
 * - 대량 알림 조회 시 비동기 처리
 * ```kotlin
 * @GetMapping
 * suspend fun getAllNotifications(...): ResponseEntity<*> {
 *     // Kotlin Coroutines 활용
 * }
 * ```
 *
 * ### 8. WebSocket 엔드포인트 추가 (선택적)
 * - STOMP 메시지 핸들러
 * ```kotlin
 * @MessageMapping("/notifications/subscribe")
 * @SendToUser("/topic/notifications")
 * fun subscribeNotifications(@Payload request: SubscribeRequest): SubscribeResponse {
 *     // ...
 * }
 * ```
 *
 * ## Production Checklist
 * - [ ] 커스텀 예외 클래스 정의
 * - [ ] @ControllerAdvice 전역 예외 처리
 * - [ ] 권한 검증 로직 추가
 * - [ ] Rate Limiting 설정
 * - [ ] 로깅 추가 (요청/응답, 에러)
 * - [ ] 모니터링 메트릭 (Prometheus)
 * - [ ] API 문서화 (Swagger/OpenAPI)
 * - [ ] 통합 테스트 작성
 * - [ ] 성능 테스트 (JMeter, Gatling)
 * - [ ] 보안 테스트 (OWASP Top 10)
 */
