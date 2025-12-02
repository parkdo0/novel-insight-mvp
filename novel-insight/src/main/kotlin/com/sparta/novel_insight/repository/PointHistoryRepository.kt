package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.PointHistory
import org.springframework.data.jpa.repository.JpaRepository

interface PointHistoryRepository : JpaRepository<PointHistory, Long> {
    // PointHistoryRepository.kt
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<PointHistory>
}