package com.sparta.novel_insight.domain

/**
 * # NotificationType - 알림 타입
 *
 * ## 목적
 * - 알림의 종류를 구분하여 적절한 메시지 및 액션 제공
 * - 프론트엔드에서 알림 타입별로 다른 UI/UX 제공
 *
 * ## 실무 권장사항
 *
 * ### 1. 알림 타입별 처리
 * - 타입에 따라 알림 메시지 템플릿 구성
 * - 타입에 따라 알림 우선순위 설정
 * - 타입에 따라 알림 전송 채널 선택 (WebSocket, Email, Push)
 *
 * ### 2. 확장 가능성
 * - 새로운 알림 타입 추가 시 Enum에 추가
 * - 프론트엔드와 타입 동기화 필요
 *
 * ## 사용 예시
 * ```kotlin
 * val notification = Notification(
 *     type = NotificationType.NEW_REVIEW,
 *     recipient = author,
 *     content = "${reviewer.nickname}님이 리뷰를 남겼습니다",
 *     relatedId = review.id
 * )
 * ```
 */
enum class NotificationType(
    val displayName: String,
    val priority: Int  // 1: 높음, 2: 중간, 3: 낮음
) {
    /**
     * 새 리뷰 알림
     * - 작가에게 새로운 리뷰가 달렸을 때
     * - 우선순위: 높음
     */
    NEW_REVIEW("새 리뷰", 1),

    /**
     * 새 댓글 알림
     * - 작가에게 새로운 댓글이 달렸을 때
     * - 우선순위: 중간
     */
    NEW_COMMENT("새 댓글", 2),

    /**
     * 댓글 좋아요 알림
     * - 내 댓글에 좋아요가 달렸을 때
     * - 우선순위: 낮음
     */
    COMMENT_LIKE("댓글 좋아요", 3),

    /**
     * 리뷰 좋아요 알림 (미구현)
     * - 향후 확장용
     */
    REVIEW_LIKE("리뷰 좋아요", 3),

    /**
     * 소설 구매 알림
     * - 작가에게 소설 회차가 구매되었을 때
     * - 우선순위: 중간
     */
    NOVEL_PURCHASE("회차 구매", 2),

    /**
     * 시스템 공지사항
     * - 관리자가 발송하는 공지사항
     * - 우선순위: 높음
     */
    SYSTEM_NOTICE("시스템 공지", 1);

    /**
     * 알림 메시지 템플릿
     *
     * ## 사용 예시
     * ```kotlin
     * val message = NotificationType.NEW_REVIEW.getTemplate(
     *     mapOf(
     *         "nickname" to "홍길동",
     *         "novelTitle" to "환생한 검성"
     *     )
     * )
     * // 결과: "홍길동님이 '환생한 검성'에 리뷰를 남겼습니다"
     * ```
     */
    fun getTemplate(params: Map<String, String>): String {
        return when (this) {
            NEW_REVIEW -> "${params["nickname"]}님이 '${params["novelTitle"]}'에 리뷰를 남겼습니다"
            NEW_COMMENT -> "${params["nickname"]}님이 '${params["episodeTitle"]}'에 댓글을 남겼습니다"
            COMMENT_LIKE -> "${params["nickname"]}님이 회원님의 댓글을 좋아합니다"
            REVIEW_LIKE -> "${params["nickname"]}님이 회원님의 리뷰를 좋아합니다"
            NOVEL_PURCHASE -> "${params["nickname"]}님이 '${params["episodeTitle"]}'을(를) 구매했습니다"
            SYSTEM_NOTICE -> params["content"] ?: "새로운 공지사항이 있습니다"
        }
    }
}
