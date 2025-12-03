package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 리뷰 감성 분석 결과 (Python 서비스에서 저장)
 */
@Entity
@Table(name = "review_analysis")
class ReviewAnalysis(
    @Column(nullable = false)
    var novelId: Long,

    @Column(nullable = false)
    var authorId: Long,

    @Column(length = 1000, nullable = false)
    var content: String,

    @Column(length = 20, nullable = false)
    var sentiment: String, // POSITIVE, NEGATIVE, NEUTRAL

    @Column(nullable = false)
    var score: Double,

    @Column(nullable = false)
    var positiveCount: Int = 0,

    @Column(nullable = false)
    var negativeCount: Int = 0,

    @Column(nullable = false)
    var analyzedAt: LocalDateTime = LocalDateTime.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)
