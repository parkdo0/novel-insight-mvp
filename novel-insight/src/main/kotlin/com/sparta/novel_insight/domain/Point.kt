package com.sparta.novel_insight.domain

import jakarta.persistence.*

@Entity
@Table(name = "point")
class Point(
    @Column(nullable = false, unique = true)
    var memberId: Long, // 누구 돈인지 (편의상 FK 대신 ID만 저장)

    @Column(nullable = false)
    var amount: Long = 0, // 잔액

    // ▼▼▼ [추가] 낙관적 락을 위한 버전 컬럼 ▼▼▼
    @Version
    var version: Long = 0,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

) {
    // 충전 함수 (비즈니스 로직)
    fun charge(money: Long) {
        this.amount += money
    }
}