package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.SectionReplayLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SectionReplayLogRepository : JpaRepository<SectionReplayLog, Long> {

    // 특정 에피소드의 구간별 재조회 통계
    // 명장면 분석에 사용
    @Query("""
        SELECT s.sectionId, COUNT(DISTINCT s.sessionId) as uniqueReplays, SUM(s.replayCount) as totalReplays
        FROM SectionReplayLog s
        WHERE s.episodeId = :episodeId
        GROUP BY s.sectionId
        ORDER BY totalReplays DESC
    """)
    fun findSectionStatsByEpisodeId(@Param("episodeId") episodeId: Long): List<Array<Any>>

    // 특정 소설의 에피소드별 명장면 구간 조회
    @Query("""
        SELECT s.episodeId, s.sectionId, COUNT(DISTINCT s.sessionId) as uniqueReplays, SUM(s.replayCount) as totalReplays
        FROM SectionReplayLog s
        WHERE s.novelId = :novelId
        GROUP BY s.episodeId, s.sectionId
        ORDER BY totalReplays DESC
    """)
    fun findTopHighlightsByNovelId(@Param("novelId") novelId: Long): List<Array<Any>>

    // 특정 세션의 특정 에피소드 구간 조회 (중복 방지)
    fun findBySessionIdAndEpisodeIdAndSectionId(
        sessionId: String,
        episodeId: Long,
        sectionId: Int
    ): SectionReplayLog?

    // 특정 사용자의 재조회 로그 조회
    fun findByUserIdAndEpisodeId(userId: Long, episodeId: Long): List<SectionReplayLog>

    // 에피소드별 재조회 횟수 합계 (인기 에피소드 분석)
    @Query("""
        SELECT s.episodeId, SUM(s.replayCount) as totalReplays
        FROM SectionReplayLog s
        WHERE s.novelId = :novelId
        GROUP BY s.episodeId
        ORDER BY totalReplays DESC
    """)
    fun findEpisodeReplayRankByNovelId(@Param("novelId") novelId: Long): List<Array<Any>>
}
