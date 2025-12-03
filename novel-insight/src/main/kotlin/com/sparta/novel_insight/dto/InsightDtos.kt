package com.sparta.novel_insight.dto

import com.sparta.novel_insight.domain.ReviewAnalysis

/**
 * 소설 감성 분석 통계 응답
 */
data class NovelInsightResponse(
    val novelId: Long,
    val totalReviews: Long,
    val positiveRate: Double,
    val negativeRate: Double,
    val neutralRate: Double,
    val avgScore: Double,
    val recentReviews: List<RecentReviewAnalysis>
)

/**
 * 최근 리뷰 분석 결과
 */
data class RecentReviewAnalysis(
    val content: String,
    val sentiment: String,
    val score: Double,
    val analyzedAt: String
) {
    companion object {
        fun from(analysis: ReviewAnalysis): RecentReviewAnalysis {
            return RecentReviewAnalysis(
                content = analysis.content.take(100), // 최대 100자
                sentiment = analysis.sentiment,
                score = analysis.score,
                analyzedAt = analysis.analyzedAt.toString()
            )
        }
    }
}

/**
 * 작가 전체 통계 응답
 */
data class AuthorInsightResponse(
    val authorId: Long,
    val totalReviews: Long,
    val avgScore: Double
)
