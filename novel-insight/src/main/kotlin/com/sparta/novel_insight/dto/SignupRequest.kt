package com.sparta.novel_insight.dto

// data class: Java의 Lombok @Data랑 비슷합니다. (Getter, Setter, ToString 자동 생성)
data class SignupRequest(
    val email: String,
    val password: String,
    val nickname: String
)