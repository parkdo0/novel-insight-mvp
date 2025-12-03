package com.sparta.novel_insight.controller

import com.sparta.novel_insight.dto.AuthorInsightResponse
import com.sparta.novel_insight.dto.NovelInsightResponse
import com.sparta.novel_insight.service.InsightService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 작가 인사이트 API
 * 감성 분석 결과 통계 조회
 */
@RestController
@RequestMapping("/api/insights")
class InsightController(
    private val insightService: InsightService
) {

    @GetMapping("/novels/{novelId}")
    fun getNovelInsight(@PathVariable novelId: Long): ResponseEntity<NovelInsightResponse> {
        val insight = insightService.getNovelInsight(novelId)
        return ResponseEntity.ok(insight)
    }

    @GetMapping("/authors/{authorId}")
    fun getAuthorInsight(@PathVariable authorId: Long): ResponseEntity<AuthorInsightResponse> {
        val insight = insightService.getAuthorInsight(authorId)
        return ResponseEntity.ok(insight)
    }
}
