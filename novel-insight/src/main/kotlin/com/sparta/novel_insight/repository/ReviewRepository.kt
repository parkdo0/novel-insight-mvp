package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Review
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewRepository : JpaRepository<Review, Long> {
    // 기본 CRUD만 있으면 됩니다.
}