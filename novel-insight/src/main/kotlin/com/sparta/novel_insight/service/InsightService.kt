package com.sparta.novel_insight.service

import com.sparta.novel_insight.dto.AuthorInsightResponse
import com.sparta.novel_insight.dto.NovelInsightResponse
import com.sparta.novel_insight.dto.RecentReviewAnalysis
import com.sparta.novel_insight.repository.ReviewAnalysisRepository
import org.springframework.stereotype.Service
import kotlin.math.round

@Service
class InsightService(
    private val reviewAnalysisRepository: ReviewAnalysisRepository
) {

    /**
     * 소설별 감성 분석 통계 조회
     */
    fun getNovelInsight(novelId: Long): NovelInsightResponse {
        val stats = reviewAnalysisRepository.getStatisticsByNovelId(novelId)
            ?: return NovelInsightResponse(
                novelId = novelId,
                totalReviews = 0,
                positiveRate = 0.0,
                negativeRate = 0.0,
                neutralRate = 0.0,
                avgScore = 0.0,
                recentReviews = emptyList()
            )

        val total = (stats[0] as Long).toDouble()
        val positiveCount = (stats[1] as Long).toDouble()
        val negativeCount = (stats[2] as Long).toDouble()
        val neutralCount = (stats[3] as Long).toDouble()
        val avgScore = stats[4] as? Double ?: 0.0

        val recentReviews = reviewAnalysisRepository
            .findTop10ByNovelIdOrderByAnalyzedAtDesc(novelId)
            .map { RecentReviewAnalysis.from(it) }

        return NovelInsightResponse(
            novelId = novelId,
            totalReviews = total.toLong(),
            positiveRate = if (total > 0) round((positiveCount / total) * 1000) / 10 else 0.0,
            negativeRate = if (total > 0) round((negativeCount / total) * 1000) / 10 else 0.0,
            neutralRate = if (total > 0) round((neutralCount / total) * 1000) / 10 else 0.0,
            avgScore = round(avgScore * 100) / 100,
            recentReviews = recentReviews
        )
    }

    /**
     * 작가 전체 통계 조회
     */
    fun getAuthorInsight(authorId: Long): AuthorInsightResponse {
        val stats = reviewAnalysisRepository.getStatisticsByAuthorId(authorId)
            ?: return AuthorInsightResponse(
                authorId = authorId,
                totalReviews = 0,
                avgScore = 0.0
            )

        val total = stats[0] as Long
        val avgScore = stats[1] as? Double ?: 0.0

        return AuthorInsightResponse(
            authorId = authorId,
            totalReviews = total,
            avgScore = round(avgScore * 100) / 100
        )
    }
}
