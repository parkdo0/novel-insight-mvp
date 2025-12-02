package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Episode
import org.springframework.data.jpa.repository.JpaRepository

interface EpisodeRepository : JpaRepository<Episode, Long> {
    // 소설 ID로 에피소드 목록 찾기
    fun findAllByNovelId(novelId: Long): List<Episode>
}