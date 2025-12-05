package com.sparta.novel_insight.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparta.novel_insight.domain.AuthorDashboardCache
import com.sparta.novel_insight.domain.PeriodType
import com.sparta.novel_insight.repository.AuthorDashboardCacheRepository
import com.sparta.novel_insight.repository.EpisodeReadLogRepository
import com.sparta.novel_insight.repository.ReviewAnalysisRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

//
// # AuthorDashboardService (Phase 6)
//
// 작가 대시보드 통합 서비스입니다.
//
// ## 핵심 책임
//
// 1. **데이터 통합**: Phase 5 (감성 분석) + Phase 6 (행동 분석) 통합
// 2. **캐시 관리**: AuthorDashboardCache 생성 및 갱신
// 3. **통계 제공**: 작가 대시보드에 필요한 모든 지표 제공
// 4. **트렌드 분석**: 일간/주간/월간 트렌드 분석
//
// ## WHY 캐시가 필요한가?
//
// ### 문제: 실시간 집계는 느리다
//
// ```kotlin
// // ❌ 실시간 집계 (나쁜 예)
// @GetMapping("/dashboard")
// fun getDashboard(): DashboardDto {
// // 1. 조회수 집계 (수백만 건 로그)
// val totalViews = episodeReadLogRepository.countByAuthorId(authorId)
//
// // 2. 완독률 계산 (복잡한 조인 쿼리)
// val completionRate = dropOffPointRepository.getAvgByAuthorId(authorId)
//
// // 3. 감성 분석 집계 (수만 건 리뷰)
// val sentiment = reviewAnalysisRepository.getStatsByAuthorId(authorId)
//
// // ... 10개 이상의 쿼리 실행
// // 총 소요 시간: 5-10초 (사용자가 기다릴 수 없는 시간)
// }
// ```
//
// ### 해결: 사전 계산된 캐시
//
// ```kotlin
// // ✅ 캐시 조회 (좋은 예)
// @GetMapping("/dashboard")
// fun getDashboard(): DashboardDto {
// // 사전 계산된 캐시 단일 조회
// val cache = authorDashboardCacheRepository.findByAuthorIdAndDate(
// authorId = authorId,
// date = LocalDate.now(),
// periodType = PeriodType.DAILY
// )
//
// return cache?.toDto() ?: DashboardDto.empty()
// // 응답 시간: 10ms 이내
// }
// ```
//
// ## 배치 스케줄
//
// ### 일 단위 집계 (Daily)
// - **실행 시각**: 매일 새벽 3시
// - **집계 범위**: 어제 하루 데이터
// - **집계 내용**:
// - Phase 6: 조회수, 완독률, 독자 수, 읽기 시간
// - Phase 5: 리뷰 수, 감성 점수, 긍정/부정 비율
// - 통합: 베스트/워스트 에피소드, 인사이트 점수
//
// ### 주간/월간 집계 (Weekly/Monthly)
// - **실행 시각**: 매주 월요일 새벽 4시 / 매월 1일 새벽 5시
// - **집계 방식**: 일간 캐시 데이터를 합산
//
// ## 데이터 통합 예시
//
// ```json
// {
// "authorId": 123,
// "novelId": null,  // null = 작가 전체 통계
// "aggregatedDate": "2024-01-15",
// "periodType": "DAILY",
//
// // Phase 6: 행동 분석 데이터
// "totalViews": 15000,
// "uniqueReaders": 8500,
// "avgReadingTimeSeconds": 420,
// "completionRate": 67.5,
//
// // Phase 5: 감성 분석 데이터
// "totalReviews": 234,
// "positiveRate": 72.5,
// "negativeRate": 15.0,
// "neutralRate": 12.5,
//
// // 통합 분석
// "topEpisodesJson": "[{\"episodeId\": 10, \"title\": \"...\", \"completionRate\": 92}]",
// "dropOffEpisodesJson": "[{\"episodeId\": 7, \"completionRate\": 42}]",
// "insightScore": 78.5  // 종합 품질 지표
// }
// ```
//
// ## 실무 최적화
//
// ### 1. 캐시 무효화 (Cache Invalidation)
// - 작가가 에피소드 수정/삭제 → 캐시 재생성
// - 대량 리뷰 유입 → 캐시 재생성
//
// ### 2. 캐시 워밍 (Cache Warming)
// - 인기 작가 캐시는 만료 전에 미리 갱신
// - 사용자 요청 전에 백그라운드로 갱신
//
// ### 3. 캐시 계층화 (Cache Hierarchy)
// - Redis (실시간): 현재 읽는 사용자 수
// - MySQL Cache (일간): AuthorDashboardCache
// - MySQL Raw Log (영구): EpisodeReadLog
//
// @see AuthorDashboardCache 캐시 Entity
// @see DropOffAnalysisService 행동 분석
// @see ReviewAnalysisRepository 감성 분석
@Service
class AuthorDashboardService(
    private val authorDashboardCacheRepository: AuthorDashboardCacheRepository,
    private val episodeReadLogRepository: EpisodeReadLogRepository,
    private val reviewAnalysisRepository: ReviewAnalysisRepository,
    private val dropOffAnalysisService: DropOffAnalysisService,
    private val objectMapper: ObjectMapper
) {

    //
    // 작가 대시보드 조회
    //
    // 캐시된 대시보드 데이터를 반환합니다.
    //
    // ## 실무 활용 예시
    //
    // ```kotlin
    // // AuthorDashboardController.kt
    // @GetMapping("/author/dashboard")
    // fun getDashboard(
    // @SessionAttribute("authorId") authorId: Long
    // ): DashboardDto {
    // return authorDashboardService.getAuthorDashboard(
    // authorId = authorId,
    // periodType = PeriodType.DAILY
    // )
    // }
    // ```
    //
    // @param authorId 작가 ID
    // @param date 조회 날짜 (기본: 오늘)
    // @param periodType 기간 타입 (DAILY, WEEKLY, MONTHLY)
    // @return 대시보드 캐시 (없으면 null)
    fun getAuthorDashboard(
        authorId: Long,
        date: LocalDate = LocalDate.now(),
        periodType: PeriodType = PeriodType.DAILY
    ): AuthorDashboardCache? {
        return authorDashboardCacheRepository.findByAuthorIdAndNovelIdIsNullAndAggregatedDateAndPeriodType(
            authorId = authorId,
            aggregatedDate = date,
            periodType = periodType
        )
    }

    //
    // 소설별 대시보드 조회
    //
    // 특정 소설에 대한 통계를 반환합니다.
    //
    // ## 실무 활용 예시
    //
    // ```kotlin
    // // NovelDashboardController.kt
    // @GetMapping("/novels/{novelId}/dashboard")
    // fun getNovelDashboard(@PathVariable novelId: Long): DashboardDto {
    // return authorDashboardService.getNovelDashboard(novelId)
    // }
    // ```
    //
    // @param novelId 소설 ID
    // @param date 조회 날짜 (기본: 오늘)
    // @param periodType 기간 타입
    // @return 소설 대시보드 캐시 (없으면 null)
    fun getNovelDashboard(
        novelId: Long,
        date: LocalDate = LocalDate.now(),
        periodType: PeriodType = PeriodType.DAILY
    ): AuthorDashboardCache? {
        // novelId로 캐시 조회 (authorId는 캐시에서 찾아야 함)
        // 실무에서는 Novel 엔티티에서 authorId를 먼저 조회
        val cache = authorDashboardCacheRepository.findAll()
            .find { it.novelId == novelId && it.aggregatedDate == date && it.periodType == periodType }

        return cache
    }

    //
    // 작가의 모든 소설 대시보드 조회
    //
    // 작가가 쓴 모든 소설의 통계를 비교할 때 사용합니다.
    //
    // ## 실무 활용 예시
    //
    // ```kotlin
    // // 소설별 비교 화면
    // @GetMapping("/author/novels/compare")
    // fun compareNovels(): List<NovelDashboardDto> {
    // return authorDashboardService.getAllNovelDashboards(authorId)
    // .sortedByDescending { it.totalViews }
    // }
    // ```
    //
    // @param authorId 작가 ID
    // @param date 조회 날짜
    // @param periodType 기간 타입
    // @return 소설별 대시보드 리스트
    fun getAllNovelDashboards(
        authorId: Long,
        date: LocalDate = LocalDate.now(),
        periodType: PeriodType = PeriodType.DAILY
    ): List<AuthorDashboardCache> {
        return authorDashboardCacheRepository.findByAuthorIdAndNovelIdIsNotNullAndAggregatedDateAndPeriodType(
            authorId = authorId,
            aggregatedDate = date,
            periodType = periodType
        )
    }

    //
    // 일 단위 대시보드 캐시 생성
    //
    // Spring Scheduler로 매일 새벽 3시에 실행됩니다.
    //
    // ## 실행 흐름
    //
    // 1. 어제 날짜의 모든 로그 조회
    // 2. 작가별로 그룹핑
    // 3. 각 작가마다:
    // a. 행동 분석 데이터 집계 (Phase 6)
    // b. 감성 분석 데이터 집계 (Phase 5)
    // c. 통합 인사이트 계산
    // d. JSON 필드 생성 (베스트/워스트 에피소드)
    // 4. AuthorDashboardCache 저장
    //
    // ## WHY 새벽 3시?
    //
    // - 새벽 2시: DropOffPoint 일 단위 집계 완료
    // - 새벽 3시: DropOffPoint 데이터를 활용해 대시보드 생성
    // - 새벽 시간대: 트래픽 최소, 시스템 부하 최소
    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    @Transactional
    fun generateDailyDashboardCache() {
        val targetDate = LocalDate.now().minusDays(1)

        // 1. 어제 하루의 모든 로그 조회
        val startDateTime = targetDate.atStartOfDay()
        val endDateTime = targetDate.plusDays(1).atStartOfDay()

        val allLogs = episodeReadLogRepository.findByCreatedAtAfter(startDateTime)
            .filter { it.createdAt.isBefore(endDateTime) }

        if (allLogs.isEmpty()) {
            return
        }

        // 2. 작가별로 그룹핑 (novelId → authorId 매핑 필요)
        // 실무에서는 Novel 엔티티를 조회해야 하지만, 여기서는 간단히 처리
        val groupedByNovel = allLogs.groupBy { it.novelId }

        // 3. 각 소설마다 대시보드 생성
        val caches = mutableListOf<AuthorDashboardCache>()

        groupedByNovel.forEach { (novelId, logs) ->
            // 실무: Novel 엔티티에서 authorId 조회
            // val novel = novelRepository.findById(novelId).orElse(null) ?: return@forEach
            // val authorId = novel.authorId

            // 임시: 첫 로그에서 추출 (실무에서는 위 방식 사용)
            val authorId = novelId // FIXME: Novel 엔티티 연동 필요

            val cache = generateNovelDashboardCache(
                authorId = authorId,
                novelId = novelId,
                logs = logs,
                targetDate = targetDate,
                periodType = PeriodType.DAILY
            )

            caches.add(cache)
        }

        // 4. 작가 전체 통계 생성 (novelId = null)
        val groupedByAuthor = allLogs.groupBy { it.novelId } // FIXME: authorId로 그룹핑
        groupedByAuthor.forEach { (authorId, logs) ->
            val cache = generateAuthorDashboardCache(
                authorId = authorId,
                logs = logs,
                targetDate = targetDate,
                periodType = PeriodType.DAILY
            )

            caches.add(cache)
        }

        // 5. 배치 저장
        authorDashboardCacheRepository.saveAll(caches)
    }

    //
    // 소설별 대시보드 캐시 생성
    //
    // @param authorId 작가 ID
    // @param novelId 소설 ID
    // @param logs 에피소드 읽기 로그
    // @param targetDate 집계 날짜
    // @param periodType 기간 타입
    // @return 생성된 캐시
    private fun generateNovelDashboardCache(
        authorId: Long,
        novelId: Long,
        logs: List<com.sparta.novel_insight.domain.EpisodeReadLog>,
        targetDate: LocalDate,
        periodType: PeriodType
    ): AuthorDashboardCache {
        // Phase 6: 행동 분석 데이터
        val totalViews = logs.size
        val uniqueReaders = logs.distinctBy { it.sessionId }.size
        val avgReadingTime = logs.mapNotNull { it.readingDurationSeconds }.average().toInt()
        val completionRate = logs.count { it.isCompleted }.toDouble() / logs.size * 100

        // Phase 5: 감성 분석 데이터
        val sentimentStats = reviewAnalysisRepository.getStatisticsByNovelId(novelId)
        val totalReviews = (sentimentStats?.get(0) as? Long)?.toInt() ?: 0
        val positiveCount = (sentimentStats?.get(1) as? Long)?.toInt() ?: 0
        val negativeCount = (sentimentStats?.get(2) as? Long)?.toInt() ?: 0
        val neutralCount = (sentimentStats?.get(3) as? Long)?.toInt() ?: 0

        val positiveRate = if (totalReviews > 0) {
            BigDecimal(positiveCount).divide(BigDecimal(totalReviews), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else {
            BigDecimal.ZERO
        }

        val negativeRate = if (totalReviews > 0) {
            BigDecimal(negativeCount).divide(BigDecimal(totalReviews), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else {
            BigDecimal.ZERO
        }

        // 베스트/워스트 에피소드 (JSON)
        val bestEpisodes = dropOffAnalysisService.getBestEpisodes(novelId, targetDate, 5)
        val worstEpisodes = dropOffAnalysisService.getWorstEpisodes(novelId, targetDate, 5)

        val topEpisodesJson = objectMapper.writeValueAsString(
            bestEpisodes.map {
                mapOf(
                    "episodeId" to it.episodeId,
                    "completionRate" to it.completionRate,
                    "totalReaders" to it.totalReaders
                )
            }
        )

        val dropOffEpisodesJson = objectMapper.writeValueAsString(
            worstEpisodes.map {
                mapOf(
                    "episodeId" to it.episodeId,
                    "completionRate" to it.completionRate,
                    "totalReaders" to it.totalReaders,
                    "highestDropOffRange" to it.getHighestDropOffRange().first
                )
            }
        )

        // 캐시 생성
        return AuthorDashboardCache(
            authorId = authorId,
            novelId = novelId,
            totalViews = totalViews,
            uniqueReaders = uniqueReaders,
            avgReadingTimeSeconds = avgReadingTime,
            completionRate = BigDecimal(completionRate).setScale(2, RoundingMode.HALF_UP),
            totalReviews = totalReviews,
            positiveRate = positiveRate,
            negativeRate = negativeRate,
            topEpisodesJson = topEpisodesJson,
            dropOffEpisodesJson = dropOffEpisodesJson,
            aggregatedDate = targetDate,
            periodType = periodType
        )
    }

    //
    // 작가 전체 대시보드 캐시 생성
    //
    // novelId = null인 캐시 (작가의 모든 소설 통합)
    //
    // @param authorId 작가 ID
    // @param logs 모든 소설의 로그
    // @param targetDate 집계 날짜
    // @param periodType 기간 타입
    // @return 생성된 캐시
    private fun generateAuthorDashboardCache(
        authorId: Long,
        logs: List<com.sparta.novel_insight.domain.EpisodeReadLog>,
        targetDate: LocalDate,
        periodType: PeriodType
    ): AuthorDashboardCache {
        // Phase 6: 행동 분석 데이터 (전체 소설 합산)
        val totalViews = logs.size
        val uniqueReaders = logs.distinctBy { it.sessionId }.size
        val avgReadingTime = logs.mapNotNull { it.readingDurationSeconds }.average().toInt()
        val completionRate = logs.count { it.isCompleted }.toDouble() / logs.size * 100

        // Phase 5: 감성 분석 데이터 (전체 소설 합산)
        val sentimentStats = reviewAnalysisRepository.getStatisticsByAuthorId(authorId)
        val totalReviews = (sentimentStats?.get(0) as? Long)?.toInt() ?: 0

        // 작가 전체 통계는 소설별 통계의 평균 사용
        val allNovelCaches = getAllNovelDashboards(authorId, targetDate, periodType)
        val avgPositiveRate = if (allNovelCaches.isNotEmpty()) {
            allNovelCaches.map { it.positiveRate }.reduce { acc, rate -> acc + rate }
                .divide(BigDecimal(allNovelCaches.size), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val avgNegativeRate = if (allNovelCaches.isNotEmpty()) {
            allNovelCaches.map { it.negativeRate }.reduce { acc, rate -> acc + rate }
                .divide(BigDecimal(allNovelCaches.size), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return AuthorDashboardCache(
            authorId = authorId,
            novelId = null, // 작가 전체 통계
            totalViews = totalViews,
            uniqueReaders = uniqueReaders,
            avgReadingTimeSeconds = avgReadingTime,
            completionRate = BigDecimal(completionRate).setScale(2, RoundingMode.HALF_UP),
            totalReviews = totalReviews,
            positiveRate = avgPositiveRate,
            negativeRate = avgNegativeRate,
            topEpisodesJson = null, // 작가 전체는 에피소드 상세 없음
            dropOffEpisodesJson = null,
            aggregatedDate = targetDate,
            periodType = periodType
        )
    }

    //
    // 트렌드 분석 조회
    //
    // 최근 N일간의 캐시 데이터를 반환합니다.
    //
    // ## 실무 활용 예시
    //
    // ```kotlin
    // // 최근 30일 트렌드
    // val trend = authorDashboardService.getTrend(authorId, 30)
    //
    // // 차트 데이터 생성
    // val chartData = trend.map {
    // ChartPoint(
    // date = it.aggregatedDate,
    // views = it.totalViews,
    // completionRate = it.completionRate
    // )
    // }
    // ```
    //
    // @param authorId 작가 ID
    // @param limit 조회 개수 (기본: 30일)
    // @param periodType 기간 타입
    // @return 트렌드 데이터 (최신순)
    fun getTrend(
        authorId: Long,
        limit: Int = 30,
        periodType: PeriodType = PeriodType.DAILY
    ): List<AuthorDashboardCache> {
        return authorDashboardCacheRepository.findRecentCachesByAuthorId(
            authorId = authorId,
            periodType = periodType,
            limit = limit
        )
    }

    //
    // 캐시 만료 처리
    //
    // Spring Scheduler로 매일 새벽 4시에 실행됩니다.
    //
    // ## WHY 필요?
    //
    // - 30일 이상 된 캐시는 삭제
    // - 디스크 공간 절약
    // - 쿼리 성능 향상 (인덱스 크기 감소)
    //
    // ## 실무 고려사항
    //
    // - 삭제 전 아카이빙 (별도 테이블로 이동)
    // - 통계용으로 월간 집계는 영구 보관
    // - 일간 집계만 30일 후 삭제
    @Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시
    @Transactional
    fun cleanupExpiredCache() {
        val cutoffDate = LocalDate.now().minusDays(30)
        authorDashboardCacheRepository.deleteByAggregatedDateBefore(cutoffDate)
    }
}
