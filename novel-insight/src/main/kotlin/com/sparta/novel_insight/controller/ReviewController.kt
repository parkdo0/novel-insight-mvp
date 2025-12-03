package com.sparta.novel_insight.controller

import com.sparta.novel_insight.dto.ReviewRequest
import com.sparta.novel_insight.dto.ReviewResponse
import com.sparta.novel_insight.domain.Review
import com.sparta.novel_insight.repository.NovelRepository
import com.sparta.novel_insight.repository.ReviewRepository
import com.sparta.novel_insight.service.RabbitProducer
import com.sparta.novel_insight.service.KafkaProducerService
import org.apache.commons.text.StringEscapeUtils
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 리뷰 API
 */
@RestController
class ReviewController(
    private val reviewRepository: ReviewRepository,
    private val novelRepository: NovelRepository,
    private val rabbitProducer: RabbitProducer,
    private val kafkaProducerService: KafkaProducerService
) {

    @PostMapping("/reviews")
    fun writeReview(@RequestBody request: ReviewRequest): ResponseEntity<ReviewResponse> {
        val novel = novelRepository.findById(request.novelId)
            .orElseThrow { RuntimeException("소설 없음") }

        val safeContent = StringEscapeUtils.escapeHtml4(request.content)

        val review = Review(content = safeContent, novel = novel)
        reviewRepository.save(review)

        rabbitProducer.sendNotification("새 리뷰: ${safeContent.take(20)}...")

        kafkaProducerService.sendReviewLog(
            novelId = novel.id!!,
            authorId = novel.authorId,
            reviewContent = safeContent
        )

        return ResponseEntity.ok(ReviewResponse.from(review))
    }
}
