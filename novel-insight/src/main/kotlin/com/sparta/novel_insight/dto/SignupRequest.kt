package com.sparta.novel_insight.dto

import com.sparta.novel_insight.domain.MemberRole

// data class: Java의 Lombok @Data랑 비슷합니다. (Getter, Setter, ToString 자동 생성)
data class SignupRequest(
    val email: String,
    val password: String,
    val nickname: String,
    val role: MemberRole? = null // 선택사항: null이면 기본값 READER 사용
)