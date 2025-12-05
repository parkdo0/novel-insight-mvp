package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * # EpisodeReadLog Entity (Phase 6)
 *
 * 사용자가 에피소드를 읽은 행동 로그를 저장하는 Entity입니다.
 *
 * ## WHY 이 Entity가 필요한가?
 *
 * 작가에게 다음 질문에 답하기 위함:
 * 1. **독자들이 어느 에피소드를 가장 많이 읽는가?** (인기 파악)
 * 2. **독자들이 얼마나 깊이 읽는가?** (흥미도 측정)
 * 3. **어느 지점에서 이탈하는가?** (문제 지점 발견)
 * 4. **완독률은 얼마나 되는가?** (퀄리티 지표)
 *
 * ## 주요 측정 지표
 *
 * - **조회수**: 에피소드가 몇 번 열렸는가
 * - **스크롤 깊이**: 사용자가 얼마나 아래로 스크롤했는가 (0-100%)
 * - **완독률**: 90% 이상 읽은 사용자 비율
 * - **읽기 시간**: 에피소드를 읽는 데 걸린 시간
 * - **이탈 지점**: 사용자가 이탈한 정확한 위치 (%)
 *
 * ## 실무 활용 예시
 *
 * ```kotlin
 * // 사용자가 에피소드 페이지 진입
 * val log = EpisodeReadLog(
 *     userId = 123,
 *     episodeId = 456,
 *     novelId = 789,
 *     sessionId = "abc123",
 *     deviceType = DeviceType.MOBILE,
 *     startedAt = LocalDateTime.now()
 * )
 * episodeReadLogRepository.save(log)
 *
 * // 사용자가 스크롤 (프론트엔드에서 주기적으로 전송)
 * log.updateScrollDepth(45.2.toBigDecimal()) // 45.2% 스크롤
 *
 * // 사용자가 페이지 이탈
 * log.markAsDropped(45.2.toBigDecimal(), DropReason.USER_ACTION)
 * episodeReadLogRepository.save(log)
 * ```
 *
 * @see DropOffPoint 이탈 지점 집계 테이블
 * @see AuthorDashboardCache 작가 대시보드 캐시
 */
@Entity
@Table(
    name = "episode_read_log",
    indexes = [
        Index(name = "idx_user_episode", columnList = "user_id,episode_id"),
        Index(name = "idx_episode_created", columnList = "episode_id,created_at"),
        Index(name = "idx_novel_created", columnList = "novel_id,created_at"),
        Index(name = "idx_session", columnList = "session_id"),
        Index(name = "idx_completed", columnList = "is_completed,episode_id")
    ]
)
class EpisodeReadLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // ===================================
    // 기본 정보
    // ===================================

    /**
     * 사용자 ID
     *
     * nullable=true인 이유:
     * - 비회원도 추적 가능하도록 (sessionId로 대체)
     * - 회원 전환율 측정 가능
     */
    @Column(nullable = true)
    val userId: Long? = null,

    /**
     * 에피소드 ID
     *
     * WHY nullable=false?
     * - 에피소드는 필수 (어떤 에피소드를 읽었는지 알아야 함)
     */
    @Column(nullable = false)
    val episodeId: Long,

    /**
     * 소설 ID
     *
     * WHY 중복 저장?
     * - 조회 성능 최적화 (JOIN 불필요)
     * - 소설별 통계 조회 시 빠른 필터링
     */
    @Column(nullable = false)
    val novelId: Long,

    // ===================================
    // 읽기 행동 정보
    // ===================================

    /**
     * 읽기 시작 시간
     *
     * 사용자가 에피소드 페이지에 진입한 시간
     */
    @Column(nullable = false)
    val startedAt: LocalDateTime,

    /**
     * 읽기 종료 시간
     *
     * nullable=true인 이유:
     * - 사용자가 아직 읽고 있는 중이면 NULL
     * - 페이지 이탈 시 업데이트
     */
    @Column(nullable = true)
    var endedAt: LocalDateTime? = null,

    /**
     * 읽기 시간 (초)
     *
     * endedAt - startedAt으로 계산
     * NULL = 아직 읽는 중
     */
    @Column(nullable = true)
    var readingDurationSeconds: Int? = null,

    // ===================================
    // 스크롤 정보
    // ===================================

    /**
     * 최대 스크롤 깊이 (0-100%)
     *
     * WHY DECIMAL(5,2)?
     * - 소수점 2자리까지 저장 (예: 45.25%)
     * - 정밀한 이탈 지점 파악 가능
     *
     * 측정 방법:
     * ```javascript
     * // 프론트엔드에서 스크롤 이벤트 리스너
     * const scrollDepth = (window.scrollY / document.body.scrollHeight) * 100
     * // 서버로 주기적으로 전송 (3초마다 or 페이지 이탈 시)
     * ```
     */
    @Column(nullable = false, precision = 5, scale = 2)
    var maxScrollDepth: BigDecimal = BigDecimal.ZERO,

    /**
     * 완독 여부
     *
     * WHY 90% 이상?
     * - 100%까지 스크롤하는 사용자는 드뭄 (댓글 섹션 등)
     * - 90% = 실질적으로 완독한 것으로 간주
     */
    @Column(nullable = false)
    var isCompleted: Boolean = false,

    // ===================================
    // 세션 정보
    // ===================================

    /**
     * 세션 ID
     *
     * WHY 필요?
     * - 비회원 추적 (쿠키 기반)
     * - 동일 사용자의 여러 세션 구분
     * - 실시간 "현재 읽는 사용자 수" 계산
     */
    @Column(nullable = false, length = 100)
    val sessionId: String,

    /**
     * 디바이스 타입
     *
     * WHY 필요?
     * - 디바이스별 이탈률 분석 (모바일 vs 데스크톱)
     * - 디바이스별 읽기 패턴 차이 파악
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val deviceType: DeviceType,

    /**
     * 유입 경로 (Referrer)
     *
     * WHY 필요?
     * - 어디서 유입되었는지 (검색엔진, 소셜미디어, 직접 방문 등)
     * - 마케팅 효과 측정
     */
    @Column(nullable = true, length = 500)
    val referrer: String? = null,

    // ===================================
    // 이탈 정보
    // ===================================

    /**
     * 이탈 지점 (%)
     *
     * 사용자가 페이지를 떠난 시점의 스크롤 위치
     * NULL = 아직 읽는 중
     */
    @Column(nullable = true, precision = 5, scale = 2)
    var droppedAtPercentage: BigDecimal? = null,

    /**
     * 이탈 이유
     *
     * WHY 필요?
     * - 의도적 이탈 vs 기술적 문제 구분
     * - USER_ACTION: 사용자가 직접 닫음 (흥미 없음)
     * - TIMEOUT: 일정 시간 동안 활동 없음 (자리 비움?)
     * - ERROR: 네트워크 오류, 페이지 크래시 등
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 50)
    var dropReason: DropReason? = null,

    // ===================================
    // 타임스탬프
    // ===================================

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 스크롤 깊이 업데이트
     *
     * 프론트엔드에서 주기적으로 호출 (3초마다)
     * 최대값만 유지 (사용자가 위로 스크롤해도 최대값은 유지)
     */
    fun updateScrollDepth(scrollDepth: BigDecimal) {
        if (scrollDepth > maxScrollDepth) {
            maxScrollDepth = scrollDepth
        }

        // 90% 이상 스크롤 시 완독으로 간주
        if (scrollDepth >= BigDecimal("90.00") && !isCompleted) {
            isCompleted = true
        }
    }

    /**
     * 이탈 처리
     *
     * 사용자가 페이지를 떠날 때 호출
     */
    fun markAsDropped(scrollDepth: BigDecimal, reason: DropReason) {
        this.endedAt = LocalDateTime.now()
        this.droppedAtPercentage = scrollDepth
        this.dropReason = reason

        // 읽기 시간 계산 (초 단위)
        this.readingDurationSeconds = java.time.Duration.between(startedAt, endedAt).seconds.toInt()
    }

    /**
     * 정상 완료 처리
     *
     * 사용자가 에피소드를 끝까지 읽고 다음 에피소드로 이동한 경우
     */
    fun markAsCompleted() {
        this.endedAt = LocalDateTime.now()
        this.isCompleted = true
        this.readingDurationSeconds = java.time.Duration.between(startedAt, endedAt).seconds.toInt()
    }
}

/**
 * 디바이스 타입
 */
enum class DeviceType {
    MOBILE,   // 모바일 (스마트폰)
    TABLET,   // 태블릿
    DESKTOP   // 데스크톱 (PC)
}

/**
 * 이탈 이유
 */
enum class DropReason {
    USER_ACTION,  // 사용자가 직접 페이지 닫음 (뒤로 가기, 탭 닫기 등)
    TIMEOUT,      // 일정 시간 동안 활동 없음 (5분 이상)
    ERROR         // 네트워크 오류, 페이지 크래시 등
}
