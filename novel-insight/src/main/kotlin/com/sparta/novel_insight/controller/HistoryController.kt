package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.PointHistory
import com.sparta.novel_insight.repository.PointHistoryRepository
import com.sparta.novel_insight.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HistoryController(
    private val pointHistoryRepository: PointHistoryRepository
) {

    @GetMapping("/users/me/history")
    fun getMyHistory(@AuthenticationPrincipal user: UserPrincipal): ResponseEntity<List<PointHistory>> {
        // (Repository에 메소드 추가 필요) 유저 ID로 전체 내역 조회 (최신순)
        val historyList = pointHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(user.id)
        return ResponseEntity.ok(historyList)
    }
}