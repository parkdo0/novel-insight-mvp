package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Member
import com.sparta.novel_insight.domain.Notification
import com.sparta.novel_insight.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * # NotificationRepository - 알림 데이터 접근
 *
 * ## 목적
 * - 알림 CRUD 및 조회 최적화
 * - 미읽은 알림 관리
 * - 오래된 알림 정리 (배치)
 *
 * ## 실무 권장사항
 *
 * ### 1. 페이지네이션
 * - 알림 목록은 무한 스크롤 또는 페이지네이션 필수
 * - 한 번에 20~50개씩 조회
 * - 오래된 알림일수록 조회 빈도 낮음
 *
 * ### 2. 인덱스 활용
 * - `(recipient_id, is_read, created_at)` 복합 인덱스
 * - WHERE + ORDER BY + LIMIT 쿼리 최적화
 *
 * ### 3. 배치 삭제
 * - 읽은 알림: 7일 후 삭제
 * - 미읽은 알림: 30일 후 삭제
 * - 매일 새벽 실행
 */
interface NotificationRepository : JpaRepository<Notification, Long> {

    /**
     * 사용자의 미읽은 알림 조회 (최신순)
     *
     * ## 사용 시나리오
     * - 알림 벨 아이콘 클릭 시 드롭다운 표시
     * - 최근 미읽은 알림 20개만 표시
     *
     * ## 인덱스 활용
     * - `idx_notification_recipient_read` 사용
     * - WHERE recipient_id = ? AND is_read = false
     * - ORDER BY created_at DESC
     *
     * ## 사용 예시
     * ```kotlin
     * @GetMapping("/notifications/unread")
     * fun getUnreadNotifications(@AuthenticationPrincipal user: UserPrincipal): List<Notification> {
     *     val member = memberRepository.findById(user.id).orElseThrow()
     *     return notificationRepository.findTop20ByRecipientAndIsReadFalseOrderByCreatedAtDesc(member)
     * }
     * ```
     */
    fun findTop20ByRecipientAndIsReadFalseOrderByCreatedAtDesc(recipient: Member): List<Notification>

    /**
     * 사용자의 모든 알림 조회 (페이지네이션)
     *
     * ## 사용 시나리오
     * - 알림 전체 목록 페이지
     * - 무한 스크롤 또는 페이지네이션
     *
     * ## 사용 예시
     * ```kotlin
     * @GetMapping("/notifications")
     * fun getAllNotifications(
     *     @AuthenticationPrincipal user: UserPrincipal,
     *     @RequestParam(defaultValue = "0") page: Int
     * ): Page<Notification> {
     *     val member = memberRepository.findById(user.id).orElseThrow()
     *     val pageable = PageRequest.of(page, 20, Sort.by("createdAt").descending())
     *     return notificationRepository.findAllByRecipient(member, pageable)
     * }
     * ```
     */
    fun findAllByRecipient(recipient: Member, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Notification>

    /**
     * 미읽은 알림 개수 조회
     *
     * ## 사용 시나리오
     * - 알림 벨 아이콘 배지 숫자 표시
     * - 예: "5" (미읽은 알림 5개)
     *
     * ## 성능 최적화
     * - COUNT(*) 대신 인덱스 활용
     * - `idx_notification_recipient_read` 사용
     *
     * ## 사용 예시
     * ```kotlin
     * @GetMapping("/notifications/unread-count")
     * fun getUnreadCount(@AuthenticationPrincipal user: UserPrincipal): Map<String, Long> {
     *     val member = memberRepository.findById(user.id).orElseThrow()
     *     val count = notificationRepository.countByRecipientAndIsReadFalse(member)
     *     return mapOf("count" to count)
     * }
     * ```
     */
    fun countByRecipientAndIsReadFalse(recipient: Member): Long

    /**
     * 모든 알림 읽음 처리
     *
     * ## 사용 시나리오
     * - "모두 읽음" 버튼 클릭
     * - 대량 업데이트 최적화
     *
     * ## 성능 최적화
     * - 단건 UPDATE 반복 (X)
     * - 벌크 UPDATE 사용 (O)
     *
     * ## 사용 예시
     * ```kotlin
     * @PutMapping("/notifications/read-all")
     * @Transactional
     * fun markAllAsRead(@AuthenticationPrincipal user: UserPrincipal): ResponseEntity<*> {
     *     val member = memberRepository.findById(user.id).orElseThrow()
     *     val updatedCount = notificationRepository.markAllAsRead(member.id!!)
     *     return ResponseEntity.ok(mapOf("updatedCount" to updatedCount))
     * }
     * ```
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.isRead = false")
    fun markAllAsRead(@Param("recipientId") recipientId: Long): Int

    /**
     * 오래된 알림 삭제 (배치 작업)
     *
     * ## 정리 정책
     * - 읽은 알림: 7일 후 삭제
     * - 미읽은 알림: 30일 후 삭제 (중요하므로 더 오래 보관)
     *
     * ## 실행 주기
     * - 매일 새벽 3시
     * - Spring Scheduler 사용
     *
     * ## 사용 예시
     * ```kotlin
     * @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
     * @Transactional
     * fun cleanupOldNotifications() {
     *     val readThreshold = LocalDateTime.now().minusDays(7)
     *     val unreadThreshold = LocalDateTime.now().minusDays(30)
     *
     *     val readDeleted = notificationRepository.deleteOldReadNotifications(readThreshold)
     *     val unreadDeleted = notificationRepository.deleteOldUnreadNotifications(unreadThreshold)
     *
     *     logger.info("읽은 알림 ${readDeleted}개, 미읽은 알림 ${unreadDeleted}개 삭제")
     * }
     * ```
     *
     * ## 성능 최적화
     * - @Modifying(clearAutomatically = true): 영속성 컨텍스트 자동 정리
     * - 인덱스 활용: (is_read, created_at)
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.createdAt < :threshold")
    fun deleteOldReadNotifications(@Param("threshold") threshold: LocalDateTime): Int

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.isRead = false AND n.createdAt < :threshold")
    fun deleteOldUnreadNotifications(@Param("threshold") threshold: LocalDateTime): Int

    /**
     * 특정 타입의 최근 알림 조회
     *
     * ## 사용 시나리오
     * - 중복 알림 방지
     * - 예: 5분 내에 같은 소설에 대한 리뷰 알림이 있으면 합치기
     *
     * ## 사용 예시
     * ```kotlin
     * fun sendReviewNotification(review: Review) {
     *     val recentNotifications = notificationRepository
     *         .findRecentNotificationsByType(
     *             recipient = review.novel.author,
     *             type = NotificationType.NEW_REVIEW,
     *             since = LocalDateTime.now().minusMinutes(5)
     *         )
     *
     *     if (recentNotifications.isNotEmpty()) {
     *         // 기존 알림 업데이트: "3명이 리뷰를 남겼습니다"
     *         val existingNotification = recentNotifications.first()
     *         existingNotification.content = "최근 ${recentNotifications.size + 1}명이 리뷰를 남겼습니다"
     *         notificationRepository.save(existingNotification)
     *     } else {
     *         // 새 알림 생성
     *         createNotification(review)
     *     }
     * }
     * ```
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.recipient = :recipient
        AND n.type = :type
        AND n.createdAt >= :since
        ORDER BY n.createdAt DESC
    """)
    fun findRecentNotificationsByType(
        @Param("recipient") recipient: Member,
        @Param("type") type: NotificationType,
        @Param("since") since: LocalDateTime
    ): List<Notification>
}
