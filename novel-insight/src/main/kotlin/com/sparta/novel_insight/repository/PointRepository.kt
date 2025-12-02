package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Point
import org.springframework.data.jpa.repository.JpaRepository

interface PointRepository : JpaRepository<Point, Long> {
    // 멤버 ID로 포인트 지갑 찾기
    fun findByMemberId(memberId: Long): Point?
}