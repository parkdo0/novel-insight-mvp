package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.AuthorDashboardCache
import com.sparta.novel_insight.domain.PeriodType
import com.sparta.novel_insight.service.AuthorDashboardService
import com.sparta.novel_insight.service.DropOffAnalysisService
import com.sparta.novel_insight.service.UserBehaviorLogService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate

//
// # AuthorDashboardController (Phase 6)
//
// 작가 대시보드 통합 API 컨트롤러입니다.
//
// ## 제공하는 API
//
// ### 1. 작가 대시보드 조회
// - GET /api/dashboard/author/{authorId}
// - 작가의 전체 통계 조회
// - Phase 5 감성 분석 + Phase 6 행동 분석 통합
//
// ### 2. 소설별 대시보드 조회
// - GET /api/dashboard/novels/{novelId}
// - 특정 소설의 통계 조회
//
// ### 3. 소설 비교
// - GET /api/dashboard/author/{authorId}/novels
// - 작가의 모든 소설 통계 비교
//
// ### 4. 트렌드 분석
// - GET /api/dashboard/author/{authorId}/trend
// - 최근 N일간의 트렌드 데이터
//
// ### 5. 에피소드 이탈 분석
// - GET /api/dashboard/novels/{novelId}/episodes/best
// - GET /api/dashboard/novels/{novelId}/episodes/worst
// - GET /api/dashboard/episodes/{episodeId}/drop-off
//
// ### 6. 실시간 지표
// - GET /api/dashboard/episodes/{episodeId}/current-readers
// - GET /api/dashboard/episodes/{episodeId}/views
//
// ## API 응답 예시
//
// ```json
// {
// "authorId": 123,
// "aggregatedDate": "2024-01-15",
// "periodType": "DAILY",
// "totalViews": 15000,
// "uniqueReaders": 8500,
// "avgReadingTimeMinutes": 7,
// "completionRate": 67.5,
// "totalReviews": 234,
// "positiveRate": 72.5,
// "negativeRate": 15.0,
// "insightScore": 78.5,
// "topEpisodes": [
// {"episodeId": 10, "completionRate": 92, "totalReaders": 5200}
// ],
// "dropOffEpisodes": [
// {"episodeId": 7, "completionRate": 42, "highestDropOffRange": "20-30%"}
// ]
// }
// ```
//
// ## 에러 응답
//
// ```json
// {
// "error": "CACHE_NOT_FOUND",
// "message": "해당 날짜의 캐시 데이터가 없습니다.",
// "hint": "배치 작업이 아직 실행되지 않았을 수 있습니다."
// }
// ```
//
// @see AuthorDashboardService 대시보드 서비스
// @see DropOffAnalysisService 이탈 분석 서비스
// @see UserBehaviorLogService 행동 로그 서비스
@RestController
@RequestMapping("/api/dashboard")
class AuthorDashboardController(
    private val authorDashboardService: AuthorDashboardService,
    private val dropOffAnalysisService: DropOffAnalysisService,
    private val userBehaviorLogService: UserBehaviorLogService
) {

    //
    // 작가 대시보드 조회
    //
    // ## 요청 예시
    //
    // ```bash
    // # 오늘 일간 통계
    // GET /api/dashboard/author/123
    //
    // # 특정 날짜 일간 통계
    // GET /api/dashboard/author/123?date=2024-01-15
    //
    // # 주간 통계
    // GET /api/dashboard/author/123?periodType=WEEKLY
    // ```
    //
    // ## 응답 필드 설명
    //
    // - **totalViews**: 전체 조회수 (중복 포함)
    // - **uniqueReaders**: 고유 독자 수 (sessionId 기준)
    // - **avgReadingTimeMinutes**: 평균 읽기 시간 (분)
    // - **completionRate**: 완독률 (%)
    // - **totalReviews**: 리뷰 수
    // - **positiveRate**: 긍정 리뷰 비율 (%)
    // - **negativeRate**: 부정 리뷰 비율 (%)
    // - **insightScore**: 종합 품질 점수 (0-100)
    //
    // @param authorId 작가 ID
    // @param date 조회 날짜 (기본: 오늘)
    // @param periodType 기간 타입 (DAILY, WEEKLY, MONTHLY)
    // @return 작가 대시보드 DTO
    @GetMapping("/author/{authorId}")
    fun getAuthorDashboard(
        @PathVariable authorId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false, defaultValue = "DAILY") periodType: PeriodType
    ): ResponseEntity<AuthorDashboardDto> {
        val targetDate = date ?: LocalDate.now()

        val cache = authorDashboardService.getAuthorDashboard(
            authorId = authorId,
            date = targetDate,
            periodType = periodType
        )

        return if (cache != null) {
            ResponseEntity.ok(cache.toDto())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    //
    // 소설별 대시보드 조회
    //
    // ## 요청 예시
    //
    // ```bash
    // GET /api/dashboard/novels/456
    // GET /api/dashboard/novels/456?date=2024-01-15
    // ```
    //
    // @param novelId 소설 ID
    // @param date 조회 날짜
    // @param periodType 기간 타입
    // @return 소설 대시보드 DTO
    @GetMapping("/novels/{novelId}")
    fun getNovelDashboard(
        @PathVariable novelId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false, defaultValue = "DAILY") periodType: PeriodType
    ): ResponseEntity<AuthorDashboardDto> {
        val targetDate = date ?: LocalDate.now()

        val cache = authorDashboardService.getNovelDashboard(
            novelId = novelId,
            date = targetDate,
            periodType = periodType
        )

        return if (cache != null) {
            ResponseEntity.ok(cache.toDto())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    //
    // 작가의 모든 소설 비교
    //
    // ## 요청 예시
    //
    // ```bash
    // GET /api/dashboard/author/123/novels
    // ```
    //
    // ## 응답 예시
    //
    // ```json
    // [
    // {
    // "novelId": 456,
    // "novelTitle": "운명의 만남",
    // "totalViews": 10000,
    // "completionRate": 75.0,
    // "positiveRate": 80.0
    // },
    // {
    // "novelId": 789,
    // "novelTitle": "금지된 사랑",
    // "totalViews": 5000,
    // "completionRate": 65.0,
    // "positiveRate": 70.0
    // }
    // ]
    // ```
    //
    // @param authorId 작가 ID
    // @param date 조회 날짜
    // @param periodType 기간 타입
    // @return 소설 대시보드 리스트
    @GetMapping("/author/{authorId}/novels")
    fun compareNovels(
        @PathVariable authorId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false, defaultValue = "DAILY") periodType: PeriodType
    ): ResponseEntity<List<AuthorDashboardDto>> {
        val targetDate = date ?: LocalDate.now()

        val caches = authorDashboardService.getAllNovelDashboards(
            authorId = authorId,
            date = targetDate,
            periodType = periodType
        )

        return ResponseEntity.ok(caches.map { it.toDto() })
    }

    //
    // 트렌드 분석 조회
    //
    // ## 요청 예시
    //
    // ```bash
    // # 최근 30일 트렌드
    // GET /api/dashboard/author/123/trend
    //
    // # 최근 7일 트렌드
    // GET /api/dashboard/author/123/trend?limit=7
    //
    // # 주간 트렌드 (최근 12주)
    // GET /api/dashboard/author/123/trend?periodType=WEEKLY&limit=12
    // ```
    //
    // ## 활용 예시 (프론트엔드)
    //
    // ```javascript
    // // Chart.js로 트렌드 차트 그리기
    // const trendData = await fetch('/api/dashboard/author/123/trend').then(r => r.json());
    //
    // new Chart(ctx, {
    // type: 'line',
    // data: {
    // labels: trendData.map(d => d.aggregatedDate),
    // datasets: [
    // {
    // label: '조회수',
    // data: trendData.map(d => d.totalViews),
    // borderColor: 'blue'
    // },
    // {
    // label: '완독률 (%)',
    // data: trendData.map(d => d.completionRate),
    // borderColor: 'green'
    // }
    // ]
    // }
    // });
    // ```
    //
    // @param authorId 작가 ID
    // @param limit 조회 개수 (기본: 30)
    // @param periodType 기간 타입
    // @return 트렌드 데이터 리스트
    @GetMapping("/author/{authorId}/trend")
    fun getTrend(
        @PathVariable authorId: Long,
        @RequestParam(required = false, defaultValue = "30") limit: Int,
        @RequestParam(required = false, defaultValue = "DAILY") periodType: PeriodType
    ): ResponseEntity<List<AuthorDashboardDto>> {
        val trend = authorDashboardService.getTrend(
            authorId = authorId,
            limit = limit,
            periodType = periodType
        )

        return ResponseEntity.ok(trend.map { it.toDto() })
    }

    //
    // 베스트 에피소드 조회
    //
    // ## 요청 예시
    //
    // ```bash
    // # 완독률 높은 에피소드 TOP 10
    // GET /api/dashboard/novels/456/episodes/best
    //
    // # TOP 5만 조회
    // GET /api/dashboard/novels/456/episodes/best?limit=5
    // ```
    //
    // ## 응답 예시
    //
    // ```json
    // [
    // {
    // "episodeId": 10,
    // "episodeTitle": "운명적인 만남",
    // "completionRate": 92.0,
    // "totalReaders": 5200,
    // "avgScrollDepth": 95.5
    // }
    // ]
    // ```
    //
    // @param novelId 소설 ID
    // @param date 조회 날짜
    // @param limit 조회 개수
    // @return 베스트 에피소드 리스트
    @GetMapping("/novels/{novelId}/episodes/best")
    fun getBestEpisodes(
        @PathVariable novelId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<List<EpisodeStatsDto>> {
        val targetDate = date ?: LocalDate.now()

        val episodes = dropOffAnalysisService.getBestEpisodes(
            novelId = novelId,
            date = targetDate,
            limit = limit
        )

        return ResponseEntity.ok(episodes.map { it.toDto() })
    }

    //
    // 워스트 에피소드 조회
    //
    // ## 요청 예시
    //
    // ```bash
    // # 완독률 낮은 에피소드 TOP 10
    // GET /api/dashboard/novels/456/episodes/worst
    // ```
    //
    // ## 작가 피드백 활용
    //
    // "이 에피소드들의 완독률이 낮습니다:
    // 1. 7화 - 지루한 설명 (완독률 42%)
    // → 20-30% 구간에서 이탈 급증
    // → 앞부분을 더 흥미롭게 수정하세요!"
    //
    // @param novelId 소설 ID
    // @param date 조회 날짜
    // @param limit 조회 개수
    // @return 워스트 에피소드 리스트
    @GetMapping("/novels/{novelId}/episodes/worst")
    fun getWorstEpisodes(
        @PathVariable novelId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<List<EpisodeStatsDto>> {
        val targetDate = date ?: LocalDate.now()

        val episodes = dropOffAnalysisService.getWorstEpisodes(
            novelId = novelId,
            date = targetDate,
            limit = limit
        )

        return ResponseEntity.ok(episodes.map { it.toDto() })
    }

    //
    // 에피소드 이탈 지점 상세 조회
    //
    // ## 요청 예시
    //
    // ```bash
    // GET /api/dashboard/episodes/789/drop-off
    // ```
    //
    // ## 응답 예시
    //
    // ```json
    // {
    // "episodeId": 789,
    // "dropOffMap": {
    // "0-10%": 50,
    // "10-20%": 120,
    // "20-30%": 200,
    // "30-40%": 180,
    // ...
    // },
    // "highestDropOffRange": "20-30%",
    // "recommendation": "20-30% 구간을 검토하세요. 독자들이 이 부분에서 많이 이탈합니다."
    // }
    // ```
    //
    // @param episodeId 에피소드 ID
    // @param date 조회 날짜
    // @return 이탈 지점 상세 정보
    @GetMapping("/episodes/{episodeId}/drop-off")
    fun getDropOffDetail(
        @PathVariable episodeId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
    ): ResponseEntity<DropOffDetailDto> {
        val targetDate = date ?: LocalDate.now()

        val dropOffMap = dropOffAnalysisService.getDropOffDetail(
            episodeId = episodeId,
            date = targetDate
        )

        if (dropOffMap.isEmpty()) {
            return ResponseEntity.notFound().build()
        }

        // 가장 이탈이 많은 구간 찾기
        val highestDropOffRange = dropOffMap.maxByOrNull { it.value }?.key ?: "0-10%"

        val recommendation = when {
            highestDropOffRange.startsWith("0-") || highestDropOffRange.startsWith("10-") ->
                "에피소드 앞부분을 검토하세요. 첫 장면이 독자를 사로잡지 못하고 있습니다."
            highestDropOffRange.startsWith("20-") || highestDropOffRange.startsWith("30-") ->
                "에피소드 중반부를 검토하세요. 전개가 지루하거나 불필요한 설명이 많을 수 있습니다."
            else ->
                "에피소드 후반부를 검토하세요. 마무리가 약하거나 다음 화로의 연결이 부족할 수 있습니다."
        }

        return ResponseEntity.ok(
            DropOffDetailDto(
                episodeId = episodeId,
                dropOffMap = dropOffMap,
                highestDropOffRange = highestDropOffRange,
                recommendation = recommendation
            )
        )
    }

    //
    // 현재 읽는 사용자 수 조회 (실시간)
    //
    // ## 요청 예시
    //
    // ```bash
    // GET /api/dashboard/episodes/789/current-readers
    // ```
    //
    // ## 응답 예시
    //
    // ```json
    // {
    // "episodeId": 789,
    // "currentReaders": 42
    // }
    // ```
    //
    // ## 프론트엔드 활용
    //
    // ```javascript
    // // 5초마다 갱신
    // setInterval(async () => {
    // const data = await fetch('/api/dashboard/episodes/789/current-readers')
    // .then(r => r.json());
    // document.getElementById('current-readers').textContent = data.currentReaders;
    // }, 5000);
    // ```
    //
    // @param episodeId 에피소드 ID
    // @return 현재 읽는 사용자 수
    @GetMapping("/episodes/{episodeId}/current-readers")
    fun getCurrentReaders(
        @PathVariable episodeId: Long
    ): ResponseEntity<CurrentReadersDto> {
        val count = userBehaviorLogService.getCurrentReaderCount(episodeId)

        return ResponseEntity.ok(
            CurrentReadersDto(
                episodeId = episodeId,
                currentReaders = count
            )
        )
    }

    //
    // 에피소드 누적 조회수 조회 (실시간)
    //
    // ## 요청 예시
    //
    // ```bash
    // GET /api/dashboard/episodes/789/views
    // ```
    //
    // ## 응답 예시
    //
    // ```json
    // {
    // "episodeId": 789,
    // "totalViews": 15234
    // }
    // ```
    //
    // @param episodeId 에피소드 ID
    // @return 누적 조회수
    @GetMapping("/episodes/{episodeId}/views")
    fun getTotalViews(
        @PathVariable episodeId: Long
    ): ResponseEntity<ViewsDto> {
        val count = userBehaviorLogService.getTotalViewCount(episodeId)

        return ResponseEntity.ok(
            ViewsDto(
                episodeId = episodeId,
                totalViews = count
            )
        )
    }
}

//
// 작가 대시보드 DTO
data class AuthorDashboardDto(
    val authorId: Long,
    val novelId: Long?,
    val aggregatedDate: LocalDate,
    val periodType: PeriodType,
    val totalViews: Int,
    val uniqueReaders: Int,
    val avgReadingTimeMinutes: Int,
    val completionRate: BigDecimal,
    val totalReviews: Int,
    val positiveRate: BigDecimal,
    val negativeRate: BigDecimal,
    val insightScore: BigDecimal,
    val topEpisodes: String?,
    val dropOffEpisodes: String?
)

//
// 에피소드 통계 DTO
data class EpisodeStatsDto(
    val episodeId: Long,
    val completionRate: BigDecimal,
    val totalReaders: Int,
    val completedReaders: Int,
    val avgScrollDepth: BigDecimal
)

//
// 이탈 지점 상세 DTO
data class DropOffDetailDto(
    val episodeId: Long,
    val dropOffMap: Map<String, Int>,
    val highestDropOffRange: String,
    val recommendation: String
)

//
// 현재 읽는 사용자 DTO
data class CurrentReadersDto(
    val episodeId: Long,
    val currentReaders: Int
)

//
// 조회수 DTO
data class ViewsDto(
    val episodeId: Long,
    val totalViews: Long
)

//
// AuthorDashboardCache → DTO 확장 함수
fun AuthorDashboardCache.toDto(): AuthorDashboardDto {
    return AuthorDashboardDto(
        authorId = this.authorId,
        novelId = this.novelId,
        aggregatedDate = this.aggregatedDate,
        periodType = this.periodType,
        totalViews = this.totalViews,
        uniqueReaders = this.uniqueReaders,
        avgReadingTimeMinutes = this.avgReadingTimeSeconds / 60,
        completionRate = this.completionRate,
        totalReviews = this.totalReviews,
        positiveRate = this.positiveRate,
        negativeRate = this.negativeRate,
        insightScore = this.calculateInsightScore(),
        topEpisodes = this.topEpisodesJson,
        dropOffEpisodes = this.dropOffEpisodesJson
    )
}

//
// DropOffPoint → DTO 확장 함수
fun com.sparta.novel_insight.domain.DropOffPoint.toDto(): EpisodeStatsDto {
    return EpisodeStatsDto(
        episodeId = this.episodeId,
        completionRate = this.completionRate,
        totalReaders = this.totalReaders,
        completedReaders = this.completedReaders,
        avgScrollDepth = this.avgScrollDepth
    )
}
