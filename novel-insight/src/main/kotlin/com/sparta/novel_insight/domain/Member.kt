package com.sparta.novel_insight.domain

import jakarta.persistence.*

@Entity
@Table(name = "member") // 테이블 이름 'member'
class Member(
    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var password: String, // ★★★ 여기에 암호화 안 된 비번이 들어갈 겁니다.

    @Column(nullable = false)
    var nickname: String,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)