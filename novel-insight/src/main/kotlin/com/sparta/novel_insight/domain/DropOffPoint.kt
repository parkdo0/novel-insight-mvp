package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * # DropOffPoint Entity (Phase 6)
 *
 * 에피소드별 이탈 지점 집계 테이블입니다.
 *
 * ## WHY 별도의 집계 테이블이 필요한가?
 *
 * ### 문제: EpisodeReadLog만으로는 느리다
 * ```sql
 * -- 에피소드별 이탈 지점 조회 (느림!)
 * SELECT
 *     CASE
 *         WHEN dropped_at_percentage BETWEEN 0 AND 10 THEN '0-10%'
 *         WHEN dropped_at_percentage BETWEEN 10 AND 20 THEN '10-20%'
 *         ...
 *     END as range,
 *     COUNT(*) as count
 * FROM episode_read_log
 * WHERE episode_id = 123
 * GROUP BY range;
 * -- 1년 후 수백만 건 로그 → 3초 이상 소요
 * ```
 *
 * ### 해결: 배치 집계로 사전 계산
 * ```sql
 * -- 매 시간 배치 작업으로 집계 (빠름!)
 * SELECT * FROM drop_off_point WHERE episode_id = 123;
 * -- 사전 계산된 결과 → 10ms 이내 응답
 * ```
 *
 * ## 집계 주기
 *
 * - **실시간**: Redis 캐시 (현재 읽는 사용자 수 등)
 * - **시간 단위**: 이 테이블 (이탈 지점 통계)
 * - **일 단위**: AuthorDashboardCache (작가 대시보드)
 *
 * ## 실무 활용 예시
 *
 * ```kotlin
 * // Spring Batch Job (매 시간 실행)
 * @Scheduled(cron = "0 5 * * * *") // 매 시간 5분
 * fun aggregateDropOffPoints() {
 *     val logs = episodeReadLogRepository.findByCreatedAtAfter(
 *         LocalDateTime.now().minusHours(1)
 *     )
 *
 *     val grouped = logs.groupBy { it.episodeId }
 *
 *     grouped.forEach { (episodeId, logs) ->
 *         val dropOff = DropOffPoint.fromLogs(episodeId, logs, LocalDate.now())
 *         dropOffPointRepository.save(dropOff)
 *     }
 * }
 * ```
 *
 * @see EpisodeReadLog 원본 로그
 * @see AuthorDashboardCache 작가 대시보드 캐시
 */
@Entity
@Table(
    name = "drop_off_point",
    indexes = [
        Index(name = "idx_episode_date", columnList = "episode_id,aggregated_date"),
        Index(name = "idx_novel_date", columnList = "novel_id,aggregated_date"),
        Index(name = "idx_completion_rate", columnList = "completion_rate")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_episode_date",
            columnNames = ["episode_id", "aggregated_date"]
        )
    ]
)
class DropOffPoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // ===================================
    // 기본 정보
    // ===================================

    /**
     * 에피소드 ID
     */
    @Column(nullable = false)
    val episodeId: Long,

    /**
     * 소설 ID
     *
     * WHY 중복 저장?
     * - 소설별 평균 완독률 조회 시 빠른 필터링
     */
    @Column(nullable = false)
    val novelId: Long,

    // ===================================
    // 이탈 지점 통계 (10% 단위)
    // ===================================

    /**
     * 0-10% 구간 이탈자 수
     *
     * WHY 10% 단위?
     * - 시각화 용이 (차트로 표현하기 좋음)
     * - 세밀한 분석 가능
     * - 패턴 발견 용이 (특정 구간에서 이탈 급증 등)
     */
    @Column(nullable = false)
    var drop0To10Percent: Int = 0,

    @Column(nullable = false)
    var drop10To20Percent: Int = 0,

    @Column(nullable = false)
    var drop20To30Percent: Int = 0,

    @Column(nullable = false)
    var drop30To40Percent: Int = 0,

    @Column(nullable = false)
    var drop40To50Percent: Int = 0,

    @Column(nullable = false)
    var drop50To60Percent: Int = 0,

    @Column(nullable = false)
    var drop60To70Percent: Int = 0,

    @Column(nullable = false)
    var drop70To80Percent: Int = 0,

    @Column(nullable = false)
    var drop80To90Percent: Int = 0,

    @Column(nullable = false)
    var drop90To100Percent: Int = 0,

    // ===================================
    // 통계 정보
    // ===================================

    /**
     * 전체 읽은 사람 수
     *
     * 중복 제거된 고유 사용자 수 (sessionId 기준)
     */
    @Column(nullable = false)
    var totalReaders: Int = 0,

    /**
     * 완독자 수
     *
     * is_completed = TRUE인 사용자 수
     */
    @Column(nullable = false)
    var completedReaders: Int = 0,

    /**
     * 완독률 (%)
     *
     * (completedReaders / totalReaders) * 100
     *
     * WHY 중요?
     * - 에피소드 퀄리티 지표
     * - 작가 피드백: "10화 완독률 45% → 검토 필요"
     */
    @Column(nullable = false, precision = 5, scale = 2)
    var completionRate: BigDecimal = BigDecimal.ZERO,

    /**
     * 평균 스크롤 깊이 (%)
     *
     * 모든 사용자의 max_scroll_depth 평균
     *
     * WHY 중요?
     * - 완독률과 다른 지표 (완독은 못해도 깊이 읽을 수 있음)
     * - 흥미도 측정
     */
    @Column(nullable = false, precision = 5, scale = 2)
    var avgScrollDepth: BigDecimal = BigDecimal.ZERO,

    // ===================================
    // 집계 기간
    // ===================================

    /**
     * 집계 날짜
     *
     * WHY DATE 타입?
     * - 시간 단위가 아닌 날짜 단위로 집계
     * - 일별 트렌드 조회 용이
     */
    @Column(nullable = false)
    val aggregatedDate: LocalDate,

    // ===================================
    // 타임스탬프
    // ===================================

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 이탈 지점 맵 반환
     *
     * 프론트엔드 차트 렌더링용
     *
     * @return Map<String, Int> - {"0-10%": 50, "10-20%": 120, ...}
     */
    fun getDropOffMap(): Map<String, Int> {
        return mapOf(
            "0-10%" to drop0To10Percent,
            "10-20%" to drop10To20Percent,
            "20-30%" to drop20To30Percent,
            "30-40%" to drop30To40Percent,
            "40-50%" to drop40To50Percent,
            "50-60%" to drop50To60Percent,
            "60-70%" to drop60To70Percent,
            "70-80%" to drop70To80Percent,
            "80-90%" to drop80To90Percent,
            "90-100%" to drop90To100Percent
        )
    }

    /**
     * 가장 이탈이 많은 구간 찾기
     *
     * WHY 필요?
     * - 작가 인사이트: "20-30% 구간에서 이탈 급증!"
     */
    fun getHighestDropOffRange(): Pair<String, Int> {
        val map = getDropOffMap()
        return map.maxByOrNull { it.value }?.toPair() ?: ("0-10%" to 0)
    }

    /**
     * 완독률 업데이트
     */
    fun updateCompletionRate() {
        completionRate = if (totalReaders > 0) {
            (completedReaders.toBigDecimal() / totalReaders.toBigDecimal()) * BigDecimal("100")
        } else {
            BigDecimal.ZERO
        }
    }

    companion object {
        /**
         * EpisodeReadLog 리스트로부터 DropOffPoint 생성
         *
         * Spring Batch Job에서 사용
         *
         * @param episodeId 에피소드 ID
         * @param logs 에피소드 조회 로그 리스트
         * @param aggregatedDate 집계 날짜
         * @return DropOffPoint 집계 결과
         */
        fun fromLogs(
            episodeId: Long,
            novelId: Long,
            logs: List<EpisodeReadLog>,
            aggregatedDate: LocalDate
        ): DropOffPoint {
            val dropOff = DropOffPoint(
                episodeId = episodeId,
                novelId = novelId,
                aggregatedDate = aggregatedDate
            )

            // 전체 독자 수 (세션 ID 기준 중복 제거)
            dropOff.totalReaders = logs.distinctBy { it.sessionId }.size

            // 완독자 수
            dropOff.completedReaders = logs.count { it.isCompleted }

            // 평균 스크롤 깊이
            dropOff.avgScrollDepth = if (logs.isNotEmpty()) {
                logs.map { it.maxScrollDepth }.reduce { acc, depth -> acc + depth }
                    .divide(logs.size.toBigDecimal(), 2, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            // 이탈 지점 집계 (10% 단위)
            logs.forEach { log ->
                log.droppedAtPercentage?.let { dropped ->
                    when {
                        dropped < BigDecimal("10") -> dropOff.drop0To10Percent++
                        dropped < BigDecimal("20") -> dropOff.drop10To20Percent++
                        dropped < BigDecimal("30") -> dropOff.drop20To30Percent++
                        dropped < BigDecimal("40") -> dropOff.drop30To40Percent++
                        dropped < BigDecimal("50") -> dropOff.drop40To50Percent++
                        dropped < BigDecimal("60") -> dropOff.drop50To60Percent++
                        dropped < BigDecimal("70") -> dropOff.drop60To70Percent++
                        dropped < BigDecimal("80") -> dropOff.drop70To80Percent++
                        dropped < BigDecimal("90") -> dropOff.drop80To90Percent++
                        else -> dropOff.drop90To100Percent++
                    }
                }
            }

            // 완독률 계산
            dropOff.updateCompletionRate()

            return dropOff
        }
    }
}
