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

    @Column(name = "novel_id", nullable = false, insertable = false, updatable = false)
    var novelId: Long, // 소설 ID (중복이지만 MockDataGenerator 호환성 위해 추가)

    @Column(nullable = false)
    var rating: BigDecimal = BigDecimal.ZERO, // 평점 (1.0 ~ 5.0)

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(), // 작성 시간

    @ManyToOne(fetch = FetchType.LAZY) // [중요] 소설(N) : 리뷰(1) 관계 아님! 리뷰(N) : 소설(1)
    @JoinColumn(name = "novel_id", insertable = false, updatable = false) // FK 컬럼명 지정 (novelId와 매핑)
    @JsonIgnore // ★★★ [이걸 추가하세요] ★★★
    var novel: Novel,  // 이제 JSON으로 변환할 때 얘는 쳐다보지 않습니다.

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)