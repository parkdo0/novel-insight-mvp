package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "purchase",
    // ★ 핵심: 한 유저는 같은 에피소드를 두 번 살 수 없다! (DB 레벨 방어)
    uniqueConstraints = [UniqueConstraint(columnNames = ["userId", "episodeId"])]
)
class Purchase(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val episodeId: Long,

    @Column(nullable = false)
    val price: Int, // 구매 당시 가격 (가격 변동 대비)

    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)