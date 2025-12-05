package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.DropOffPoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/**
 * # DropOffPointRepository (Phase 6)
 *
 * 이탈 지점 집계 Repository입니다.
 *
 * ## 주요 쿼리 메서드
 *
 * - 에피소드별 이탈 통계 조회
 * - 완독률 낮은 에피소드 조회
 * - 작가별 평균 완독률 조회
 */
interface DropOffPointRepository : JpaRepository<DropOffPoint, Long> {

    /**
     * 에피소드 + 날짜로 집계 데이터 조회
     *
     * WHY 필요?
     * - UPSERT 로직 (있으면 UPDATE, 없으면 INSERT)
     * - 배치 작업에서 사용
     */
    fun findByEpisodeIdAndAggregatedDate(episodeId: Long, aggregatedDate: LocalDate): DropOffPoint?

    /**
     * 소설의 모든 에피소드 이탈 통계 조회
     *
     * 작가 대시보드에서 사용:
     * - 소설 전체 평균 완독률 계산
     */
    fun findByNovelIdAndAggregatedDate(novelId: Long, aggregatedDate: LocalDate): List<DropOffPoint>

    /**
     * 완독률이 낮은 에피소드 TOP N 조회
     *
     * WHY 필요?
     * - 작가 피드백: "이 에피소드들을 검토하세요"
     *
     * ```kotlin
     * val worstEpisodes = repository.findWorstEpisodesByNovelId(
     *     novelId = 123,
     *     date = LocalDate.now(),
     *     limit = 10
     * )
     * ```
     */
    @Query(
        """
        SELECT d FROM DropOffPoint d
        WHERE d.novelId = :novelId
        AND d.aggregatedDate = :date
        ORDER BY d.completionRate ASC
        LIMIT :limit
        """
    )
    fun findWorstEpisodesByNovelId(
        @Param("novelId") novelId: Long,
        @Param("date") date: LocalDate,
        @Param("limit") limit: Int = 10
    ): List<DropOffPoint>

    /**
     * 완독률이 높은 에피소드 TOP N 조회
     *
     * WHY 필요?
     * - 작가 피드백: "이 에피소드들이 성공적입니다"
     */
    @Query(
        """
        SELECT d FROM DropOffPoint d
        WHERE d.novelId = :novelId
        AND d.aggregatedDate = :date
        ORDER BY d.completionRate DESC
        LIMIT :limit
        """
    )
    fun findBestEpisodesByNovelId(
        @Param("novelId") novelId: Long,
        @Param("date") date: LocalDate,
        @Param("limit") limit: Int = 10
    ): List<DropOffPoint>

    /**
     * 소설의 평균 완독률 조회
     *
     * 작가 대시보드 캐시 생성 시 사용
     */
    @Query(
        """
        SELECT AVG(d.completionRate)
        FROM DropOffPoint d
        WHERE d.novelId = :novelId
        AND d.aggregatedDate = :date
        """
    )
    fun getAvgCompletionRateByNovelId(
        @Param("novelId") novelId: Long,
        @Param("date") date: LocalDate
    ): Double?
}
