package com.sparta.novel_insight.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

//
// # AuthorDashboardCache Entity (Phase 6)
//
// 작가 대시보드 통계를 사전 계산하여 저장하는 캐시 테이블입니다.
//
// ## WHY 캐시 테이블이 필요한가?
//
// ### 문제: 실시간 계산은 너무 느리다
// ```kotlin
// // 작가 대시보드 조회 시 필요한 쿼리들
// val totalViews = episodeReadLogRepository.countByAuthorId(authorId)  // 1번
// val uniqueReaders = episodeReadLogRepository.countDistinctSessionIdByAuthorId(authorId)  // 2번
// val sentimentStats = reviewAnalysisRepository.getStatsByAuthorId(authorId)  // 3번
// val topEpisodes = episodeReadLogRepository.findTopEpisodesByAuthorId(authorId)  // 4번
// val dropOffEpisodes = dropOffPointRepository.findWorstEpisodesByAuthorId(authorId)  // 5번
// // ... 10개 이상의 복잡한 쿼리 → 5초 이상 소요
// ```
//
// ### 해결: 배치 작업으로 사전 계산
// ```kotlin
// // 매일 새벽 2시에 모든 통계 계산하여 저장
// val cache = authorDashboardCacheRepository.findByAuthorIdAndDate(authorId, LocalDate.now())
// // 응답 시간: 10ms 이내
// ```
//
// ## 캐싱 전략 (3-Tier)
//
// 1. **Redis**: 실시간 (현재 읽는 사용자 수)
// 2. **MySQL Cache Table** (이 테이블): 시간/일 단위
// 3. **MySQL Raw Log**: 영구 보관
//
// ## 집계 주기
//
// - **DAILY**: 매일 통계 (기본)
// - **WEEKLY**: 주간 통계 (트렌드 분석)
// - **MONTHLY**: 월간 통계 (장기 트렌드)
//
// ## 실무 활용 예시
//
// ```kotlin
// // 작가 대시보드 조회
// GET /api/dashboard/author/123
//
// val cache = authorDashboardCacheRepository.findByAuthorIdAndDateAndPeriodType(
// authorId = 123,
// aggregatedDate = LocalDate.now(),
// periodType = PeriodType.DAILY
// )
//
// // 응답 즉시 반환 (10ms)
// return DashboardResponse(
// totalViews = cache.totalViews,
// uniqueReaders = cache.uniqueReaders,
// topEpisodes = cache.topEpisodesJson,  // JSON 그대로 반환
// sentimentStats = ...
// )
// ```
//
// @see EpisodeReadLog 원본 로그
// @see DropOffPoint 이탈 지점 집계
@Entity
@Table(
    name = "author_dashboard_cache",
    indexes = [
        Index(name = "idx_author_date", columnList = "author_id,cache_date"),
        Index(name = "idx_author_novel_date", columnList = "author_id,novel_id,cache_date")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_author_novel_date_period",
            columnNames = ["author_id", "novel_id", "cache_date", "period_type"]
        )
    ]
)
class AuthorDashboardCache(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // ===================================
    // 기본 정보
    // ===================================

    //
    // 작가 ID
    @Column(nullable = false)
    val authorId: Long,

    //
    // 소설 ID
    //
    // nullable=true인 이유:
    // - NULL = 작가 전체 통계
    // - NOT NULL = 특정 소설 통계
    //
    // 예시:
    // - (authorId=123, novelId=NULL) → 작가 123의 모든 소설 통계
    // - (authorId=123, novelId=456) → 작가 123의 소설 456 통계
    @Column(nullable = true)
    val novelId: Long? = null,

    // ===================================
    // 조회 통계
    // ===================================

    //
    // 전체 조회수
    //
    // episode_read_log.count()
    @Column(nullable = false)
    var totalViews: Int = 0,

    //
    // 고유 독자 수
    //
    // episode_read_log.distinctBy(sessionId).count()
    //
    // WHY 중요?
    // - 실제 독자 수 (중복 제거)
    // - 팬층 규모 측정
    @Column(nullable = false)
    var uniqueReaders: Int = 0,

    //
    // 평균 읽기 시간 (초)
    //
    // episode_read_log.reading_duration_seconds.avg()
    //
    // WHY 중요?
    // - 몰입도 지표
    // - 평균 10분 이상 = 높은 몰입도
    @Column(nullable = false)
    var avgReadingTimeSeconds: Int = 0,

    //
    // 평균 완독률 (%)
    //
    // drop_off_point.completion_rate.avg()
    //
    // WHY 중요?
    // - 퀄리티 지표
    // - 70% 이상 = 우수
    @Column(nullable = false, precision = 5, scale = 2)
    var completionRate: BigDecimal = BigDecimal.ZERO,

    // ===================================
    // 감정 분석 통계 (Phase 5 통합)
    // ===================================

    //
    // 전체 리뷰 수
    //
    // review_analysis.count()
    @Column(nullable = false)
    var totalReviews: Int = 0,

    //
    // 긍정률 (%)
    //
    // review_analysis.sentiment='POSITIVE'.count() / total * 100
    @Column(nullable = false, precision = 5, scale = 2)
    var positiveRate: BigDecimal = BigDecimal.ZERO,

    //
    // 부정률 (%)
    @Column(nullable = false, precision = 5, scale = 2)
    var negativeRate: BigDecimal = BigDecimal.ZERO,

    //
    // 기쁨 감정 비율 (%) - Phase 5
    //
    // review_analysis.emotion='JOY'.count() / total * 100
    @Column(nullable = false, precision = 5, scale = 2)
    var joyRate: BigDecimal = BigDecimal.ZERO,

    //
    // 분노 감정 비율 (%) - Phase 5
    //
    // WHY 중요?
    // - 독자 불만 지표
    // - 급증 시 즉시 대응 필요
    @Column(nullable = false, precision = 5, scale = 2)
    var angerRate: BigDecimal = BigDecimal.ZERO,

    // ===================================
    // 복잡한 통계 (JSON 저장)
    // ===================================

    //
    // 인기 에피소드 TOP 10 (JSON)
    //
    // WHY JSON?
    // - 복잡한 데이터 구조 (리스트, 중첩 객체)
    // - 별도 테이블 불필요
    // - 프론트엔드로 그대로 전달 가능
    //
    // 예시:
    // ```json
    // [
    // {
    // "episodeId": 123,
    // "title": "1화: 환생",
    // "views": 15000,
    // "completionRate": 85.2,
    // "joyRate": 45.3
    // },
    // ...
    // ]
    // ```
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = true, columnDefinition = "json")
    var topEpisodesJson: String? = null,

    //
    // 이탈률 높은 에피소드 TOP 10 (JSON)
    //
    // WHY 필요?
    // - 작가 피드백: "이 에피소드들을 검토하세요"
    //
    // 예시:
    // ```json
    // [
    // {
    // "episodeId": 456,
    // "title": "10화: 전투",
    // "completionRate": 45.2,
    // "dropOffRange": "20-30%",
    // "dropOffCount": 450
    // },
    // ...
    // ]
    // ```
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = true, columnDefinition = "json")
    var dropOffEpisodesJson: String? = null,

    // ===================================
    // 집계 기간
    // ===================================

    //
    // 집계 날짜
    @Column(name = "cache_date", nullable = false)
    val aggregatedDate: LocalDate,

    //
    // 기간 타입
    //
    // - DAILY: 일간 통계
    // - WEEKLY: 주간 통계
    // - MONTHLY: 월간 통계
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val periodType: PeriodType,

    // ===================================
    // 타임스탬프
    // ===================================

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    //
    // 캐시 유효성 검증
    //
    // WHY 필요?
    // - 오래된 캐시는 재생성 필요
    // - 24시간 이상 지난 DAILY 캐시는 무효
    fun isExpired(): Boolean {
        return when (periodType) {
            PeriodType.DAILY -> aggregatedDate < LocalDate.now()
            PeriodType.WEEKLY -> aggregatedDate < LocalDate.now().minusWeeks(1)
            PeriodType.MONTHLY -> aggregatedDate < LocalDate.now().minusMonths(1)
        }
    }

    //
    // 전체 인사이트 점수 계산
    //
    // WHY 필요?
    // - 작품의 전반적인 퀄리티 지표
    // - 다른 작가들과 비교 가능
    //
    // 계산 공식:
    // - 완독률 (40%)
    // - 긍정률 (30%)
    // - 기쁨 감정 비율 (20%)
    // - 분노 감정 비율 (10%, 역산)
    //
    // @return 0-100점
    fun calculateInsightScore(): BigDecimal {
        val completionWeight = completionRate * BigDecimal("0.4")
        val positiveWeight = positiveRate * BigDecimal("0.3")
        val joyWeight = joyRate * BigDecimal("0.2")
        val angerPenalty = angerRate * BigDecimal("0.1")

        return (completionWeight + positiveWeight + joyWeight - angerPenalty)
            .coerceIn(BigDecimal.ZERO, BigDecimal("100"))
    }
}

//
// 기간 타입
enum class PeriodType {
    DAILY,    // 일간
    WEEKLY,   // 주간
    MONTHLY   // 월간
}
