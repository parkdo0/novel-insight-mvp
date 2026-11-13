package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Episode
import org.springframework.data.jpa.repository.JpaRepository

interface EpisodeRepository : JpaRepository<Episode, Long> {
    // 소설 ID로 에피소드 목록 찾기 (에피소드 번호 순)
    fun findAllByNovelIdOrderByEpisodeNumberAsc(novelId: Long): List<Episode>

    // 기존 메서드 (정렬 없음) - 하위 호환성 유지
    fun findAllByNovelId(novelId: Long): List<Episode>
}