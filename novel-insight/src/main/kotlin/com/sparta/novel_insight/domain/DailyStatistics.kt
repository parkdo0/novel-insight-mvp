package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
class DailyStatistics(
    @Column(nullable = false)
    var totalViewCount: Long, // 조회수 총합

    @Column(nullable = false)
    var activeNovelCount: Long, // 소설 개수

    @Column(nullable = false)
    var date: LocalDate = LocalDate.now(), // 집계 날짜

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)