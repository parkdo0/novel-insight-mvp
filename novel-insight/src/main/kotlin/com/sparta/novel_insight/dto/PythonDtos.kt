package com.sparta.novel_insight.dto

// 보낼 때 (파이썬의 AnalysisRequest와 매칭)
data class AnalysisRequest(
    val text: String
)

// 받을 때 (파이썬의 리턴값과 매칭)
data class AnalysisResponse(
    val status: String,
    val result: String
)