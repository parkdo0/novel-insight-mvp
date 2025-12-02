package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.time.LocalDateTime

enum class TransactionType { CHARGE, USE }

@Entity
@Table(name = "point_history")
class PointHistory(
    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionType,

    @Column(nullable = false)
    val amount: Long,

    @Column(nullable = false)
    val description: String,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)