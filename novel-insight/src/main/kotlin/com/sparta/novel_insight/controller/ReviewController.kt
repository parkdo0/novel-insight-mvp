package com.sparta.novel_insight.controller

import com.sparta.novel_insight.dto.ReviewRequest
import com.sparta.novel_insight.dto.ReviewResponse
import com.sparta.novel_insight.domain.Review
import com.sparta.novel_insight.repository.NovelRepository
import com.sparta.novel_insight.repository.ReviewRepository
import com.sparta.novel_insight.service.NotificationService
import com.sparta.novel_insight.service.RabbitProducer
import org.apache.commons.text.StringEscapeUtils // ★ 이 녀석이 핵심!
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ReviewController(
    private val reviewRepository: ReviewRepository,
    private val novelRepository: NovelRepository,
    private val notificationService: NotificationService, // 1. 주입
    private val rabbitProducer: RabbitProducer
) {

    @PostMapping("/reviews")
    fun writeReview(@RequestBody request: ReviewRequest): ResponseEntity<ReviewResponse> { // ★ 리턴 타입 변경 (Entity -> DTO)

        val novel = novelRepository.findById(request.novelId)
            .orElseThrow { RuntimeException("소설 없음") }

        // ★ [XSS 방어] "<script>" -> "&lt;script&gt;" 변환
        // 이제 브라우저는 이걸 코드가 아니라 '글자'로 인식합니다.
        val safeContent = StringEscapeUtils.escapeHtml4(request.content)

        val review = Review(
            content = safeContent,
            novel = novel
        )

        reviewRepository.save(review)

        // ★★★ [범인] 여기서 발목이 잡힙니다.
        // 알림이 다 갈 때까지 사용자는 3초 동안 로딩 바만 보고 있어야 합니다.
        // ★★★ [수정] 3초 걸리던 로직 삭제 -> 0.01초 걸리는 큐 전송으로 대체
        // notificationService.sendToAuthor() (삭제)
        rabbitProducer.sendNotification("새 리뷰가 달렸습니다: ${safeContent}")
        // ★ [Entity 은닉] DTO로 변환해서 리턴
        return ResponseEntity.ok(ReviewResponse.from(review))
    }
}