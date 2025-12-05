package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.EpisodeReadLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

//
// # EpisodeReadLogRepository (Phase 6)
//
// 에피소드 조회 로그 Repository입니다.
//
// ## 주요 쿼리 메서드
//
// - 시간 범위 조회: 배치 집계용
// - 에피소드별 통계: 인기 에피소드 파악
// - 세션별 조회: 중복 제거 통계
interface EpisodeReadLogRepository : JpaRepository<EpisodeReadLog, Long> {

    //
    // 특정 시간 이후의 로그 조회
    //
    // 배치 집계 작업에서 사용:
    // - 매 시간 5분에 실행
    // - 지난 1시간 동안의 로그를 집계
    //
    // ```kotlin
    // val logs = repository.findByCreatedAtAfter(
    // LocalDateTime.now().minusHours(1)
    // )
    // ```
    fun findByCreatedAtAfter(createdAt: LocalDateTime): List<EpisodeReadLog>

    //
    // 에피소드별, 날짜별 로그 조회
    //
    // 특정 에피소드의 일간 통계 계산용
    fun findByEpisodeIdAndCreatedAtBetween(
        episodeId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<EpisodeReadLog>

    //
    // 소설별, 날짜별 로그 조회
    //
    // 소설 전체 통계 계산용
    fun findByNovelIdAndCreatedAtBetween(
        novelId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<EpisodeReadLog>

    //
    // 에피소드별 고유 독자 수 조회
    //
    // sessionId 기준 중복 제거
    //
    // WHY @Query?
    // - Spring Data JPA가 자동 생성 못함
    // - DISTINCT 쿼리 필요
    @Query("SELECT COUNT(DISTINCT e.sessionId) FROM EpisodeReadLog e WHERE e.episodeId = :episodeId")
    fun countUniqueReadersByEpisodeId(@Param("episodeId") episodeId: Long): Long

    //
    // 에피소드별 완독률 조회
    //
    // 완독자 수 / 전체 독자 수
    @Query(
        """
        SELECT
            (CAST(SUM(CASE WHEN e.isCompleted = true THEN 1 ELSE 0 END) AS double) / COUNT(*)) * 100
        FROM EpisodeReadLog e
        WHERE e.episodeId = :episodeId
        """
    )
    fun getCompletionRateByEpisodeId(@Param("episodeId") episodeId: Long): Double?

    //
    // 현재 읽고 있는 사용자 조회
    //
    // endedAt이 NULL인 로그 = 현재 읽는 중
    //
    // WHY 필요?
    // - 실시간 "현재 N명이 읽는 중" 표시
    // - Redis 캐시와 함께 사용
    @Query("SELECT e FROM EpisodeReadLog e WHERE e.episodeId = :episodeId AND e.endedAt IS NULL")
    fun findCurrentlyReadingByEpisodeId(@Param("episodeId") episodeId: Long): List<EpisodeReadLog>
}
