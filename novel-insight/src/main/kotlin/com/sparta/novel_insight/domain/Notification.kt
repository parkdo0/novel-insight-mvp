package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * # Notification - 알림 엔티티
 *
 * ## 목적
 * - 실시간 알림 및 알림 이력 관리
 * - WebSocket을 통한 즉시 알림 + DB 저장으로 영속성 확보
 * - 사용자가 놓친 알림도 나중에 확인 가능
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. 알림 전송 전략
 * #### 실시간 알림 (WebSocket)
 * - 사용자가 온라인일 때 즉시 전송
 * - `/topic/notifications/{userId}` 토픽으로 발송
 * - 빠른 피드백, 사용자 경험 향상
 *
 * #### 영속성 알림 (DB 저장)
 * - 사용자가 오프라인일 때를 대비한 저장
 * - 로그인 시 미읽은 알림 목록 조회
 * - 알림 이력 추적 (감사 목적)
 *
 * ### 2. 읽음 처리 (isRead)
 * - 사용자가 알림을 클릭하면 isRead = true
 * - 미읽은 알림 개수 배지 표시 (UI)
 * - 일정 기간 후 자동 삭제 (30일 등)
 *
 * ### 3. 성능 최적화
 * #### 인덱스 전략
 * - `(recipient_id, is_read, created_at)` 복합 인덱스
 * - 미읽은 알림 조회 쿼리 최적화
 *   ```sql
 *   SELECT * FROM notification
 *   WHERE recipient_id = ? AND is_read = false
 *   ORDER BY created_at DESC
 *   LIMIT 20
 *   ```
 *
 * #### 오래된 알림 정리
 * - 배치 작업으로 30일 이상 된 알림 삭제
 * - 읽은 알림은 7일 후 삭제
 * - DB 용량 절약, 조회 성능 향상
 *
 * ### 4. 알림 중복 방지
 * - 같은 이벤트에 대한 중복 알림 방지
 * - 예: 한 소설에 리뷰 10개 → 알림 10개 (X)
 * - 해결: 5분 내 같은 타입 알림 합치기 "3명이 리뷰를 남겼습니다"
 *
 * ## 연관관계 설계
 * - Member : Notification = 1 : N (수신자)
 * - Member : Notification = 1 : N (발신자, 선택적)
 *
 * ## relatedId 활용
 * - NEW_REVIEW: Review.id
 * - NEW_COMMENT: Comment.id
 * - NOVEL_PURCHASE: Purchase.id
 * - 프론트엔드에서 알림 클릭 시 해당 페이지로 이동
 */
@Entity
@Table(
    name = "notification",
    indexes = [
        // 미읽은 알림 조회 최적화
        Index(name = "idx_notification_recipient_read", columnList = "recipient_id,is_read,created_at"),
        // 발신자별 알림 조회 (통계용)
        Index(name = "idx_notification_sender", columnList = "sender_id")
    ]
)
class Notification(
    /**
     * 알림 타입
     * - NEW_REVIEW, NEW_COMMENT, COMMENT_LIKE 등
     * - Enum으로 관리하여 타입 안정성 확보
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var type: NotificationType,

    /**
     * 수신자 (알림을 받을 사용자)
     * - 작가, 댓글 작성자 등
     * - FetchType.LAZY: 알림 조회 시 Member 정보 불필요 (성능 최적화)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    var recipient: Member,

    /**
     * 발신자 (알림을 발생시킨 사용자, 선택적)
     * - 리뷰 작성자, 댓글 작성자 등
     * - null 가능: 시스템 알림(SYSTEM_NOTICE)은 발신자 없음
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    var sender: Member? = null,

    /**
     * 알림 내용
     * - 사용자에게 표시될 메시지
     * - 템플릿 엔진으로 동적 생성
     * - 예: "홍길동님이 '환생한 검성'에 리뷰를 남겼습니다"
     */
    @Column(nullable = false, length = 500)
    var content: String,

    /**
     * 관련 엔티티 ID
     * - NEW_REVIEW: Review.id
     * - NEW_COMMENT: Comment.id
     * - NOVEL_PURCHASE: Purchase.id
     * - 프론트엔드에서 알림 클릭 시 해당 페이지로 이동
     *
     * ## 사용 예시 (프론트엔드)
     * ```javascript
     * function handleNotificationClick(notification) {
     *     switch(notification.type) {
     *         case 'NEW_REVIEW':
     *             router.push(`/reviews/${notification.relatedId}`)
     *             break
     *         case 'NEW_COMMENT':
     *             router.push(`/episodes/${notification.relatedId}#comments`)
     *             break
     *     }
     *     markAsRead(notification.id)
     * }
     * ```
     */
    @Column(name = "related_id")
    var relatedId: Long? = null,

    /**
     * 읽음 여부
     * - false: 미읽음 (기본값)
     * - true: 읽음
     *
     * ## 읽음 처리 시점
     * - 사용자가 알림을 클릭할 때
     * - 알림 목록 페이지에서 "모두 읽음" 버튼 클릭
     *
     * ## 미읽은 알림 개수 조회
     * ```kotlin
     * val unreadCount = notificationRepository.countByRecipientAndIsReadFalse(member)
     * ```
     */
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    /**
     * 생성 시간
     * - 알림 발생 시각
     * - 최신 알림부터 정렬 (ORDER BY created_at DESC)
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
) {
    /**
     * 알림 읽음 처리
     *
     * ## 사용 예시
     * ```kotlin
     * @PutMapping("/notifications/{id}/read")
     * fun markAsRead(@PathVariable id: Long): ResponseEntity<*> {
     *     val notification = notificationRepository.findById(id).orElseThrow()
     *     notification.markAsRead()
     *     notificationRepository.save(notification)
     *     return ResponseEntity.ok("읽음 처리 완료")
     * }
     * ```
     */
    fun markAsRead() {
        this.isRead = true
    }

    /**
     * 알림 생성 후 경과 시간 (분)
     *
     * ## 사용 예시
     * - "방금 전", "3분 전", "1시간 전" 등 상대적 시간 표시
     * - 프론트엔드에서 표시용
     *
     * ```kotlin
     * val minutesAgo = notification.getMinutesAgo()
     * val displayTime = when {
     *     minutesAgo < 1 -> "방금 전"
     *     minutesAgo < 60 -> "${minutesAgo}분 전"
     *     minutesAgo < 1440 -> "${minutesAgo / 60}시간 전"
     *     else -> "${minutesAgo / 1440}일 전"
     * }
     * ```
     */
    fun getMinutesAgo(): Long {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes()
    }
}
