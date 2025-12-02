package com.sparta.novel_insight.dto

import com.sparta.novel_insight.domain.Review
import java.time.LocalDateTime

data class ReviewResponse(
    val id: Long,
    val content: String,
    val authorEmail: String?, // 작성자 누군지 알려줌 (나중에 채울 예정)
    val createdAt: LocalDateTime
) {
    // Entity -> DTO 변환하는 '정적 팩토리 메소드' (실무 패턴)
    companion object {
        fun from(review: Review): ReviewResponse {
            return ReviewResponse(
                id = review.id!!,
                content = review.content,
                authorEmail = "익명", // 지금은 로그인 기능 안 붙여서 임시로 넣음
                createdAt = LocalDateTime.now() // 임시 (Entity에 시간 필드 있으면 그걸로 대체)
            )
        }
    }
}