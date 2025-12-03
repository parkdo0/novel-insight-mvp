package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.ReviewAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewAnalysisRepository : JpaRepository<ReviewAnalysis, Long> {

    /**
     * 소설별 감성 분석 통계 조회
     */
    @Query("""
        SELECT
            COUNT(ra) as totalReviews,
            SUM(CASE WHEN ra.sentiment = 'POSITIVE' THEN 1 ELSE 0 END) as positiveCount,
            SUM(CASE WHEN ra.sentiment = 'NEGATIVE' THEN 1 ELSE 0 END) as negativeCount,
            SUM(CASE WHEN ra.sentiment = 'NEUTRAL' THEN 1 ELSE 0 END) as neutralCount,
            AVG(ra.score) as avgScore
        FROM ReviewAnalysis ra
        WHERE ra.novelId = :novelId
    """)
    fun getStatisticsByNovelId(@Param("novelId") novelId: Long): Array<Any>?

    /**
     * 작가별 소설들의 통계 조회
     */
    @Query("""
        SELECT
            COUNT(ra) as totalReviews,
            AVG(ra.score) as avgScore
        FROM ReviewAnalysis ra
        WHERE ra.authorId = :authorId
    """)
    fun getStatisticsByAuthorId(@Param("authorId") authorId: Long): Array<Any>?

    /**
     * 최근 N개 리뷰 분석 결과 조회
     */
    fun findTop10ByNovelIdOrderByAnalyzedAtDesc(novelId: Long): List<ReviewAnalysis>
}
