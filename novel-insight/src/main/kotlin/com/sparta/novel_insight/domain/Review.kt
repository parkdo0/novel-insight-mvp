package com.sparta.novel_insight.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*

@Entity
class Review(
    @Column(nullable = false)
    var content: String, // 댓글 내용

    @ManyToOne(fetch = FetchType.LAZY) // [중요] 소설(N) : 리뷰(1) 관계 아님! 리뷰(N) : 소설(1)
    @JoinColumn(name = "novel_id") // FK 컬럼명 지정
    @JsonIgnore // ★★★ [이걸 추가하세요] ★★★
    var novel: Novel,  // 이제 JSON으로 변환할 때 얘는 쳐다보지 않습니다.

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)