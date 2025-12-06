package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.AuthorDashboardCache
import com.sparta.novel_insight.domain.PeriodType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

//
// # AuthorDashboardCacheRepository (Phase 6)
//
// 작가 대시보드 캐시 Repository입니다.
//
// ## 주요 쿼리 메서드
//
// - 작가별 + 날짜별 캐시 조회
// - 소설별 + 날짜별 캐시 조회
// - 만료된 캐시 삭제
interface AuthorDashboardCacheRepository : JpaRepository<AuthorDashboardCache, Long> {

    //
    // 작가 전체 통계 캐시 조회
    //
    // novelId = NULL → 작가 전체 통계
    //
    // ```kotlin
    // val cache = repository.findByAuthorIdAndNovelIdIsNullAndAggregatedDateAndPeriodType(
    // authorId = 123,
    // aggregatedDate = LocalDate.now(),
    // periodType = PeriodType.DAILY
    // )
    // ```
    fun findByAuthorIdAndNovelIdIsNullAndAggregatedDateAndPeriodType(
        authorId: Long,
        aggregatedDate: LocalDate,
        periodType: PeriodType
    ): AuthorDashboardCache?

    //
    // 특정 소설 통계 캐시 조회
    //
    // novelId != NULL → 특정 소설 통계
    fun findByAuthorIdAndNovelIdAndAggregatedDateAndPeriodType(
        authorId: Long,
        novelId: Long,
        aggregatedDate: LocalDate,
        periodType: PeriodType
    ): AuthorDashboardCache?

    //
    // 작가의 모든 소설 캐시 조회
    //
    // WHY 필요?
    // - 작가 대시보드에서 소설별 비교
    fun findByAuthorIdAndNovelIdIsNotNullAndAggregatedDateAndPeriodType(
        authorId: Long,
        aggregatedDate: LocalDate,
        periodType: PeriodType
    ): List<AuthorDashboardCache>

    //
    // 만료된 캐시 삭제
    //
    // WHY 필요?
    // - 오래된 캐시는 디스크 공간 낭비
    // - 주기적으로 정리 (30일 이상 된 캐시)
    //
    // ```kotlin
    // @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    // fun cleanupExpiredCache() {
    // val cutoffDate = LocalDate.now().minusDays(30)
    // repository.deleteByAggregatedDateBefore(cutoffDate)
    // }
    // ```
    fun deleteByAggregatedDateBefore(cutoffDate: LocalDate)

    //
    // 작가의 최근 캐시 조회 (기간별)
    //
    // WHY 필요?
    // - 트렌드 분석 (일간 → 주간 → 월간)
    @Query(
        """
        SELECT c FROM AuthorDashboardCache c
        WHERE c.authorId = :authorId
        AND c.novelId IS NULL
        AND c.periodType = :periodType
        ORDER BY c.aggregatedDate DESC
        LIMIT :limit
        """
    )
    fun findRecentCachesByAuthorId(
        @Param("authorId") authorId: Long,
        @Param("periodType") periodType: PeriodType,
        @Param("limit") limit: Int = 30
    ): List<AuthorDashboardCache>
}
