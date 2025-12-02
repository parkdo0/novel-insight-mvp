package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "novel")
class Novel(
    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var author: String, // 작가 필명

    // ★ [추가] 작가(회원) ID (본인 인증용)
    @Column(nullable = false)
    var authorId: Long,

    @Lob
    var description: String? = null,

    var viewCount: Long = 0, // 전체 조회수

    var createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "novel", fetch = FetchType.LAZY)
    var episodes: MutableList<Episode> = mutableListOf(),

    @OneToMany(mappedBy = "novel", fetch = FetchType.LAZY)
    var reviews: MutableList<Review> = mutableListOf(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)