package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.Member
import com.sparta.novel_insight.domain.Notification
import com.sparta.novel_insight.domain.NotificationType
import com.sparta.novel_insight.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * # NotificationService - 실시간 알림 서비스
 *
 * ## 목적
 * - 알림 생성 및 저장 (DB 영속성)
 * - WebSocket을 통한 실시간 알림 발송
 * - 알림 조회, 읽음 처리 등 비즈니스 로직
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. Dual Write 전략 (이중 저장)
 * ```kotlin
 * fun sendNotification(...) {
 *     // 1. DB 저장 (영속성)
 *     notificationRepository.save(notification)
 *
 *     // 2. WebSocket 발송 (실시간)
 *     messagingTemplate.convertAndSend(...)
 * }
 * ```
 *
 * #### WHY Dual Write?
 * - **DB 저장**: 사용자가 오프라인이어도 나중에 확인 가능
 * - **WebSocket**: 사용자가 온라인이면 즉시 알림
 * - **Trade-off**: 두 번의 write 연산, but 사용자 경험 크게 향상
 *
 * ### 2. 알림 중복 방지 (Notification Aggregation)
 * - 짧은 시간 내 같은 타입 알림 여러 개 → 하나로 병합
 * - 예: "3명이 리뷰를 남겼습니다" (알림 3개 → 1개)
 * - 사용자 피로도 감소, DB 용량 절약
 *
 * ### 3. WebSocket 연결 상태 처리
 * - 온라인: 즉시 알림
 * - 오프라인: DB 저장만 (로그인 시 조회)
 * - 발송 실패해도 예외 던지지 않음 (서비스 중단 방지)
 *
 * ## 설계 패턴
 * - **Observer Pattern**: 이벤트 발생 → 알림 발송
 * - **Pub/Sub Pattern**: WebSocket 토픽 구독
 */
@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * 알림 생성 및 실시간 발송
     *
     * ## 프로세스
     * 1. Notification 엔티티 생성
     * 2. DB에 저장 (영속성)
     * 3. WebSocket으로 실시간 발송
     *
     * ## 사용 예시
     * ```kotlin
     * // ReviewService에서 호출
     * notificationService.sendNotification(
     *     type = NotificationType.NEW_REVIEW,
     *     recipient = novel.author,
     *     sender = reviewer,
     *     content = "${reviewer.nickname}님이 리뷰를 남겼습니다",
     *     relatedId = review.id
     * )
     * ```
     */
    @Transactional
    fun sendNotification(
        type: NotificationType,
        recipient: Member,
        sender: Member?,
        content: String,
        relatedId: Long? = null
    ): Notification {
        // 1. DB 저장
        val notification = Notification(
            type = type,
            recipient = recipient,
            sender = sender,
            content = content,
            relatedId = relatedId
        )
        val saved = notificationRepository.save(notification)
        logger.info("알림 저장: id=${saved.id}, type=$type, recipient=${recipient.id}")

        // 2. WebSocket 발송
        try {
            sendWebSocketNotification(saved)
        } catch (e: Exception) {
            logger.error("WebSocket 발송 실패: ${e.message}", e)
        }

        return saved
    }

    /**
     * WebSocket 실시간 발송
     *
     * ## 목적지
     * - `/topic/notifications/{userId}`
     */
    private fun sendWebSocketNotification(notification: Notification) {
        val destination = "/topic/notifications/${notification.recipient.id}"
        val dto = mapOf(
            "id" to notification.id,
            "type" to notification.type.name,
            "content" to notification.content,
            "relatedId" to notification.relatedId,
            "isRead" to notification.isRead,
            "createdAt" to notification.createdAt.toString(),
            "sender" to notification.sender?.let {
                mapOf("id" to it.id, "nickname" to it.nickname)
            }
        )
        messagingTemplate.convertAndSend(destination, dto)
        logger.info("WebSocket 발송 완료: $destination")
    }

    /**
     * 미읽은 알림 조회
     */
    @Transactional(readOnly = true)
    fun getUnreadNotifications(recipient: Member): List<Notification> {
        return notificationRepository.findTop20ByRecipientAndIsReadFalseOrderByCreatedAtDesc(recipient)
    }

    /**
     * 미읽은 알림 개수
     */
    @Transactional(readOnly = true)
    fun getUnreadCount(recipient: Member): Long {
        return notificationRepository.countByRecipientAndIsReadFalse(recipient)
    }

    /**
     * 모든 알림 조회 (페이지네이션)
     *
     * @param recipient 알림 수신자
     * @param pageable 페이지네이션 정보
     * @return 알림 페이지
     */
    @Transactional(readOnly = true)
    fun getAllNotifications(recipient: Member, pageable: Pageable): Page<Notification> {
        return notificationRepository.findAllByRecipient(recipient, pageable)
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    fun markAsRead(notificationId: Long) {
        val notification = notificationRepository.findById(notificationId).orElseThrow {
            IllegalArgumentException("존재하지 않는 알림: id=$notificationId")
        }
        notification.markAsRead()
        notificationRepository.save(notification)
    }

    /**
     * 모든 알림 읽음 처리
     */
    @Transactional
    fun markAllAsRead(recipient: Member): Int {
        return notificationRepository.markAllAsRead(recipient.id!!)
    }

    /* ===================================
     * [LEGACY] 기존 동기식 알림 메서드 (deprecated)
     * ===================================
     * 실무에서는 사용하지 마세요!
     */
    @Deprecated("Use sendNotification() instead", ReplaceWith("sendNotification()"))
    fun sendToAuthor() {
        logger.warn("[DEPRECATED] sendToAuthor() 호출됨. sendNotification() 사용을 권장합니다.")
        println("알림 발송 시작... (동기 방식)")
        try {
            Thread.sleep(3000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            e.printStackTrace()
        }
        println("알림 발송 완료!")
    }
}
