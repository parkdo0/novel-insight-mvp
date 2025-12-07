package com.sparta.novel_insight.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
class Review(
    @Column(nullable = false)
    var content: String, // 댓글 내용

    @Column(nullable = false)
    var userId: Long, // 리뷰 작성자 ID

    @Column(name = "novel_id", nullable = false)
    var novelId: Long, // 소설 ID - insertable = true로 변경!

    @Column(nullable = false)
    var rating: BigDecimal = BigDecimal.ZERO, // 평점 (1.0 ~ 5.0)

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(), // 작성 시간

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id", insertable = false, updatable = false) // FK는 novelId로 관리
    @JsonIgnore
    var novel: Novel? = null,  // nullable로 변경 (novelId로 INSERT하므로)

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)