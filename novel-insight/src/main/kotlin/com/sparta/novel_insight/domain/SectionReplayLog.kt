package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

//
// # SectionReplayLog Entity (명장면 분석)
//
// 사용자가 특정 구간을 반복 조회하는 행동을 추적합니다.
//
// ## WHY 이 Entity가 필요한가?
//
// 명장면을 찾기 위함:
// 1. **독자들이 다시 보는 구간** = 인상적인 장면
// 2. **반복 조회 횟수가 높은 구간** = 명장면 후보
// 3. **다수의 독자가 재조회하는 구간** = 검증된 명장면
//
// ## 측정 방법
//
// - 스크롤/페이지 뷰어에서 사용자가 이전에 본 구간으로 돌아가면 로그 기록
// - "위로 스크롤" 또는 "이전 페이지로 이동" 이벤트 감지
// - 동일 구간 재방문 횟수 집계
//
// ## 활용 예시
//
// - "독자들이 가장 많이 다시 본 장면 TOP 10"
// - "명장면 하이라이트" 기능 자동 추천
// - 작가에게 "이 부분이 인기있어요" 피드백 제공
//
@Entity
@Table(
    name = "section_replay_log",
    indexes = [
        Index(name = "idx_section_episode", columnList = "episode_id,section_percentage"),
        Index(name = "idx_section_user", columnList = "user_id,episode_id"),
        Index(name = "idx_section_created", columnList = "created_at"),
        Index(name = "idx_section_novel", columnList = "novel_id,created_at")
    ]
)
class SectionReplayLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 사용자 ID (nullable = 비회원도 추적)
    @Column(nullable = true)
    val userId: Long? = null,

    // 에피소드 ID
    @Column(nullable = false)
    val episodeId: Long,

    // 소설 ID (조회 최적화)
    @Column(nullable = false)
    val novelId: Long,

    // 세션 ID (비회원 추적용)
    @Column(nullable = false, length = 100)
    val sessionId: String,

    // 재조회 구간 시작 % (0-100)
    // 예: 25.00 = 에피소드의 25% 지점
    @Column(nullable = false, precision = 5, scale = 2)
    val sectionPercentage: BigDecimal,

    // 재조회 구간 끝 % (0-100)
    // 예: 30.00 = 에피소드의 30% 지점
    @Column(nullable = false, precision = 5, scale = 2)
    val sectionEndPercentage: BigDecimal,

    // 구간 ID (10% 단위로 구분)
    // 0 = 0-10%, 1 = 10-20%, ..., 9 = 90-100%
    @Column(nullable = false)
    val sectionId: Int,

    // 해당 구간 재조회 횟수 (세션 내)
    @Column(nullable = false)
    var replayCount: Int = 1,

    // 재조회 시 체류 시간 (초)
    @Column(nullable = true)
    var durationSeconds: Int? = null,

    // 뷰어 모드 (스크롤/페이지)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val viewerMode: ViewerMode,

    // 디바이스 타입
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val deviceType: DeviceType,

    // 타임스탬프
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = true)
    var updatedAt: LocalDateTime? = null
) {
    // 재조회 횟수 증가
    fun incrementReplayCount() {
        this.replayCount++
        this.updatedAt = LocalDateTime.now()
    }

    // 체류 시간 업데이트
    fun updateDuration(seconds: Int) {
        this.durationSeconds = (this.durationSeconds ?: 0) + seconds
        this.updatedAt = LocalDateTime.now()
    }
}

// 뷰어 모드
enum class ViewerMode {
    SCROLL,  // 스크롤 방식
    PAGE     // 페이지 넘김 방식
}
