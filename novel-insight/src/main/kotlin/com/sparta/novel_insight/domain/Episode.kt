package com.sparta.novel_insight.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*

@Entity
@Table(name = "episode")
class Episode(
    @Column(nullable = false)
    var title: String,

    @Lob
    @Column(nullable = false)
    var content: String,

    @Column(nullable = false)
    var price: Int = 0,

    @Column(nullable = false)
    var isFree: Boolean = true,

    // ★ [추가] 통계 데이터
    var viewCount: Long = 0,
    var purchaseCount: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id")
    @JsonIgnore
    var novel: Novel,

    @OneToMany(mappedBy = "episode", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var comments: MutableList<EpisodeComment> = mutableListOf(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
) {
    // 편의 메소드
    fun increaseView() { this.viewCount++ }
    fun increasePurchase() { this.purchaseCount++ }
}