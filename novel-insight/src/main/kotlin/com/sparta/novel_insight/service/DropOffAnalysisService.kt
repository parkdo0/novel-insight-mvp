package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.DropOffPoint
import com.sparta.novel_insight.repository.DropOffPointRepository
import com.sparta.novel_insight.repository.EpisodeReadLogRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * # DropOffAnalysisService (Phase 6)
 *
 * 이탈 지점 분석 및 집계 서비스입니다.
 *
 * ## 핵심 책임
 *
 * 1. **시간 단위 집계**: EpisodeReadLog → DropOffPoint 변환
 * 2. **완독률 분석**: 에피소드별 완독률 계산
 * 3. **이탈 패턴 분석**: 10% 구간별 이탈자 수 집계
 * 4. **베스트/워스트 에피소드 조회**: 작가 피드백용 데이터 제공
 *
 * ## WHY 배치 집계가 필요한가?
 *
 * ### 문제: 실시간 계산은 느리다
 *
 * ```kotlin
 * // ❌ 실시간 계산 (나쁜 예)
 * @GetMapping("/novels/{novelId}/episodes/worst")
 * fun getWorstEpisodes(@PathVariable novelId: Long): List<EpisodeStatsDto> {
 *     val episodes = episodeRepository.findByNovelId(novelId)
 *
 *     // 각 에피소드마다 로그를 조회해서 계산 (느림!)
 *     return episodes.map { episode ->
 *         val logs = episodeReadLogRepository.findByEpisodeId(episode.id)
 *         val completionRate = logs.count { it.isCompleted } / logs.size.toDouble()
 *
 *         EpisodeStatsDto(
 *             episodeId = episode.id,
 *             completionRate = completionRate
 *         )
 *     }.sortedBy { it.completionRate }
 * }
 * // 50개 에피소드 × 각 10만 건 로그 → 수 초 ~ 수십 초 소요
 * ```
 *
 * ### 해결: 배치 사전 계산
 *
 * ```kotlin
 * // ✅ 배치 집계 (좋은 예)
 * @GetMapping("/novels/{novelId}/episodes/worst")
 * fun getWorstEpisodes(@PathVariable novelId: Long): List<EpisodeStatsDto> {
 *     // 사전 계산된 집계 데이터 조회 (빠름!)
 *     return dropOffPointRepository.findWorstEpisodesByNovelId(
 *         novelId = novelId,
 *         date = LocalDate.now(),
 *         limit = 10
 *     ).map { it.toDto() }
 * }
 * // 10ms 이내 응답
 * ```
 *
 * ## 배치 스케줄
 *
 * ### 시간 단위 집계 (Hourly)
 * - **실행 시각**: 매 시간 5분 (예: 01:05, 02:05, ...)
 * - **집계 범위**: 지난 1시간 로그
 * - **집계 대상**: DropOffPoint 업데이트
 * - **소요 시간**: 1-2분 (로그 수에 따라 다름)
 *
 * ### 일 단위 집계 (Daily)
 * - **실행 시각**: 매일 새벽 2시
 * - **집계 범위**: 어제 하루 로그
 * - **집계 대상**: AuthorDashboardCache 업데이트
 * - **소요 시간**: 5-10분
 *
 * ## 실무 최적화 전략
 *
 * ### 1. UPSERT 패턴
 * ```kotlin
 * // 기존 집계가 있으면 UPDATE, 없으면 INSERT
 * val existing = dropOffPointRepository.findByEpisodeIdAndAggregatedDate(episodeId, date)
 * if (existing != null) {
 *     existing.updateStats(logs)
 *     dropOffPointRepository.save(existing)
 * } else {
 *     val newDropOff = DropOffPoint.fromLogs(episodeId, novelId, logs, date)
 *     dropOffPointRepository.save(newDropOff)
 * }
 * ```
 *
 * ### 2. 배치 처리 (Batch Processing)
 * - Spring Data JPA의 saveAll() 사용
 * - 한 번에 100개씩 배치 저장
 * - N+1 문제 방지
 *
 * ### 3. 파티셔닝 (Partitioning)
 * - aggregated_date 기준으로 월 단위 파티셔닝
 * - 오래된 파티션은 별도 테이블로 아카이빙
 *
 * @see EpisodeReadLog 원본 로그
 * @see DropOffPoint 집계 결과
 * @see AuthorDashboardService 작가 대시보드
 */
@Service
class DropOffAnalysisService(
    private val episodeReadLogRepository: EpisodeReadLogRepository,
    private val dropOffPointRepository: DropOffPointRepository
) {

    /**
     * 시간 단위 이탈 지점 집계
     *
     * Spring Scheduler로 매 시간 5분에 실행됩니다.
     *
     * ## 실행 흐름
     *
     * 1. 지난 1시간 동안의 EpisodeReadLog 조회
     * 2. 에피소드별로 그룹핑
     * 3. 각 에피소드마다 DropOffPoint 생성/업데이트
     * 4. 배치 저장
     *
     * ## 실무 배치 설정 예시
     *
     * ```kotlin
     * // application.yml
     * spring:
     *   task:
     *     scheduling:
     *       pool:
     *         size: 10  # 스레드 풀 크기
     *       thread-name-prefix: batch-scheduler-
     * ```
     *
     * ## WHY 매 시간 5분?
     *
     * - 정각 (00분): 시스템 부하 집중 (다른 배치 작업과 겹칠 가능성)
     * - 5분: 로그가 충분히 쌓인 시점
     * - 시간당 한 번씩 집계 = 최신 데이터 유지
     */
    @Scheduled(cron = "0 5 * * * *") // 매 시간 5분
    @Transactional
    fun aggregateHourlyDropOffPoints() {
        val now = LocalDateTime.now()
        val oneHourAgo = now.minusHours(1)

        // 1. 지난 1시간 로그 조회
        val logs = episodeReadLogRepository.findByCreatedAtAfter(oneHourAgo)

        if (logs.isEmpty()) {
            return
        }

        // 2. 에피소드별로 그룹핑
        val groupedByEpisode = logs.groupBy { it.episodeId }

        // 3. 각 에피소드마다 집계
        val today = LocalDate.now()
        val dropOffPoints = mutableListOf<DropOffPoint>()

        groupedByEpisode.forEach { (episodeId, episodeLogs) ->
            if (episodeLogs.isEmpty()) return@forEach

            val novelId = episodeLogs.first().novelId

            // 기존 집계가 있는지 확인 (UPSERT)
            val existing = dropOffPointRepository.findByEpisodeIdAndAggregatedDate(episodeId, today)

            if (existing != null) {
                // 기존 집계 업데이트
                updateDropOffPoint(existing, episodeLogs)
                dropOffPoints.add(existing)
            } else {
                // 새로운 집계 생성
                val newDropOff = DropOffPoint.fromLogs(episodeId, novelId, episodeLogs, today)
                dropOffPoints.add(newDropOff)
            }
        }

        // 4. 배치 저장
        dropOffPointRepository.saveAll(dropOffPoints)
    }

    /**
     * 기존 DropOffPoint 업데이트
     *
     * WHY 새로 만들지 않고 업데이트?
     * - 하루 동안 여러 번 배치가 실행되므로 누적 집계 필요
     * - 예: 01:05 배치 → 100명 집계
     *       02:05 배치 → 추가 50명 집계 (누적 150명)
     *
     * @param dropOff 기존 집계 데이터
     * @param newLogs 새로운 로그들
     */
    private fun updateDropOffPoint(dropOff: DropOffPoint, newLogs: List<com.sparta.novel_insight.domain.EpisodeReadLog>) {
        // 전체 독자 수 증가 (세션 중복 제거)
        val newUniqueReaders = newLogs.distinctBy { it.sessionId }.size
        dropOff.totalReaders += newUniqueReaders

        // 완독자 수 증가
        val newCompletedReaders = newLogs.count { it.isCompleted }
        dropOff.completedReaders += newCompletedReaders

        // 평균 스크롤 깊이 재계산
        val totalScrollDepth = newLogs.sumOf { it.maxScrollDepth }
        val currentTotalScroll = dropOff.avgScrollDepth.multiply(dropOff.totalReaders.toBigDecimal())
        dropOff.avgScrollDepth = (currentTotalScroll + totalScrollDepth)
            .divide((dropOff.totalReaders + newLogs.size).toBigDecimal(), 2, java.math.RoundingMode.HALF_UP)

        // 이탈 지점 집계 누적
        newLogs.forEach { log ->
            log.droppedAtPercentage?.let { dropped ->
                when {
                    dropped < java.math.BigDecimal("10") -> dropOff.drop0To10Percent++
                    dropped < java.math.BigDecimal("20") -> dropOff.drop10To20Percent++
                    dropped < java.math.BigDecimal("30") -> dropOff.drop20To30Percent++
                    dropped < java.math.BigDecimal("40") -> dropOff.drop30To40Percent++
                    dropped < java.math.BigDecimal("50") -> dropOff.drop40To50Percent++
                    dropped < java.math.BigDecimal("60") -> dropOff.drop50To60Percent++
                    dropped < java.math.BigDecimal("70") -> dropOff.drop60To70Percent++
                    dropped < java.math.BigDecimal("80") -> dropOff.drop70To80Percent++
                    dropped < java.math.BigDecimal("90") -> dropOff.drop80To90Percent++
                    else -> dropOff.drop90To100Percent++
                }
            }
        }

        // 완독률 재계산
        dropOff.updateCompletionRate()

        // 업데이트 시간 갱신
        dropOff.updatedAt = LocalDateTime.now()
    }

    /**
     * 소설의 베스트 에피소드 조회
     *
     * 완독률이 가장 높은 에피소드를 반환합니다.
     *
     * ## 실무 활용 예시
     *
     * ```kotlin
     * // AuthorDashboardController.kt
     * @GetMapping("/dashboard/best-episodes")
     * fun getBestEpisodes(): List<EpisodeStatsDto> {
     *     return dropOffAnalysisService.getBestEpisodes(
     *         novelId = getCurrentUserNovelId(),
     *         limit = 10
     *     )
     * }
     * ```
     *
     * ## 작가 피드백 활용
     *
     * "귀하의 소설에서 완독률이 가장 높은 에피소드입니다:
     * 1. 10화 - 운명적인 만남 (완독률 92%)
     * 2. 15화 - 첫 키스 (완독률 89%)
     * 3. 23화 - 반전의 진실 (완독률 87%)
     *
     * → 이런 스타일의 에피소드를 더 많이 작성하세요!"
     *
     * @param novelId 소설 ID
     * @param date 집계 날짜 (기본: 오늘)
     * @param limit 조회 개수 (기본: 10개)
     * @return 베스트 에피소드 리스트
     */
    fun getBestEpisodes(
        novelId: Long,
        date: LocalDate = LocalDate.now(),
        limit: Int = 10
    ): List<DropOffPoint> {
        return dropOffPointRepository.findBestEpisodesByNovelId(novelId, date, limit)
    }

    /**
     * 소설의 워스트 에피소드 조회
     *
     * 완독률이 가장 낮은 에피소드를 반환합니다.
     *
     * ## 작가 피드백 활용
     *
     * "귀하의 소설에서 완독률이 낮은 에피소드입니다:
     * 1. 7화 - 지루한 설명 (완독률 42%)
     * 2. 12화 - 불필요한 장면 (완독률 45%)
     * 3. 18화 - 템포 저하 (완독률 48%)
     *
     * → 이 에피소드들을 검토하고 개선하세요!
     * → 이탈 지점 분석: 7화는 30-40% 구간에서 이탈 급증"
     *
     * @param novelId 소설 ID
     * @param date 집계 날짜 (기본: 오늘)
     * @param limit 조회 개수 (기본: 10개)
     * @return 워스트 에피소드 리스트
     */
    fun getWorstEpisodes(
        novelId: Long,
        date: LocalDate = LocalDate.now(),
        limit: Int = 10
    ): List<DropOffPoint> {
        return dropOffPointRepository.findWorstEpisodesByNovelId(novelId, date, limit)
    }

    /**
     * 에피소드별 이탈 지점 상세 조회
     *
     * 10% 구간별 이탈자 수를 반환합니다.
     *
     * ## 실무 활용 예시 (프론트엔드)
     *
     * ```javascript
     * // episode-analytics.js
     * const dropOffData = await fetch('/api/episodes/123/drop-off-detail').then(r => r.json());
     *
     * // Chart.js로 막대 그래프 렌더링
     * new Chart(ctx, {
     *     type: 'bar',
     *     data: {
     *         labels: ['0-10%', '10-20%', '20-30%', '30-40%', '40-50%',
     *                  '50-60%', '60-70%', '70-80%', '80-90%', '90-100%'],
     *         datasets: [{
     *             label: '이탈자 수',
     *             data: [50, 120, 200, 180, 100, 80, 50, 30, 20, 10],
     *             backgroundColor: 'rgba(255, 99, 132, 0.5)'
     *         }]
     *     }
     * });
     * ```
     *
     * ## 인사이트 예시
     *
     * "20-30% 구간에서 이탈이 급증했습니다 (200명).
     * → 에피소드 앞부분을 검토하세요!
     * → 첫 장면이 지루하거나 불필요한 설명이 많을 수 있습니다."
     *
     * @param episodeId 에피소드 ID
     * @param date 집계 날짜 (기본: 오늘)
     * @return 이탈 지점 맵 (0-10% → 이탈자 수)
     */
    fun getDropOffDetail(
        episodeId: Long,
        date: LocalDate = LocalDate.now()
    ): Map<String, Int> {
        val dropOff = dropOffPointRepository.findByEpisodeIdAndAggregatedDate(episodeId, date)
            ?: return emptyMap()

        return dropOff.getDropOffMap()
    }

    /**
     * 소설의 평균 완독률 조회
     *
     * 작가 대시보드 캐시 생성 시 사용됩니다.
     *
     * ## 계산 방식
     *
     * ```
     * 평균 완독률 = SUM(각 에피소드 완독률) / 에피소드 개수
     * ```
     *
     * ## 실무 활용
     *
     * - 작가 대시보드 메인 지표
     * - 소설 퀄리티 점수 계산
     * - 다른 작가와 비교 지표
     *
     * @param novelId 소설 ID
     * @param date 집계 날짜 (기본: 오늘)
     * @return 평균 완독률 (0-100)
     */
    fun getAverageCompletionRate(
        novelId: Long,
        date: LocalDate = LocalDate.now()
    ): Double {
        return dropOffPointRepository.getAvgCompletionRateByNovelId(novelId, date) ?: 0.0
    }

    /**
     * 소설의 모든 에피소드 통계 조회
     *
     * AuthorDashboardService에서 사용합니다.
     *
     * @param novelId 소설 ID
     * @param date 집계 날짜 (기본: 오늘)
     * @return 에피소드 통계 리스트
     */
    fun getAllEpisodeStats(
        novelId: Long,
        date: LocalDate = LocalDate.now()
    ): List<DropOffPoint> {
        return dropOffPointRepository.findByNovelIdAndAggregatedDate(novelId, date)
    }

    /**
     * 일 단위 전체 집계 (재계산)
     *
     * Spring Scheduler로 매일 새벽 2시에 실행됩니다.
     *
     * ## WHY 필요?
     *
     * - 시간 단위 집계는 증분 업데이트 (누적)
     * - 가끔 불일치가 발생할 수 있음 (네트워크 오류, 배치 실패 등)
     * - 일 단위로 전체 재계산해서 정확도 보장
     *
     * ## 실행 흐름
     *
     * 1. 어제 하루 동안의 모든 EpisodeReadLog 조회
     * 2. 에피소드별로 그룹핑
     * 3. DropOffPoint 전체 재계산 (기존 삭제 후 재생성)
     * 4. 배치 저장
     *
     * @param targetDate 집계 대상 날짜 (기본: 어제)
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @Transactional
    fun aggregateDailyDropOffPoints(targetDate: LocalDate = LocalDate.now().minusDays(1)) {
        val startDateTime = targetDate.atStartOfDay()
        val endDateTime = targetDate.plusDays(1).atStartOfDay()

        // 1. 하루 동안의 모든 로그 조회
        val allLogs = episodeReadLogRepository.findByCreatedAtAfter(startDateTime)
            .filter { it.createdAt.isBefore(endDateTime) }

        if (allLogs.isEmpty()) {
            return
        }

        // 2. 에피소드별로 그룹핑
        val groupedByEpisode = allLogs.groupBy { it.episodeId }

        // 3. 각 에피소드마다 재계산
        val dropOffPoints = groupedByEpisode.map { (episodeId, logs) ->
            val novelId = logs.first().novelId
            DropOffPoint.fromLogs(episodeId, novelId, logs, targetDate)
        }

        // 4. 기존 집계 삭제 후 재생성
        dropOffPoints.forEach { dropOff ->
            val existing = dropOffPointRepository.findByEpisodeIdAndAggregatedDate(dropOff.episodeId, targetDate)
            existing?.let { dropOffPointRepository.delete(it) }
        }

        dropOffPointRepository.saveAll(dropOffPoints)
    }
}
