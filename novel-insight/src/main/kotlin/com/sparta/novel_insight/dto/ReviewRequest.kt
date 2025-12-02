package com.sparta.novel_insight.dto

data class ReviewRequest(
    val novelId: Long, // 어떤 소설에 달 건지
    val content: String // 리뷰 내용 (여기에 해킹 코드가 들어올 예정)
)