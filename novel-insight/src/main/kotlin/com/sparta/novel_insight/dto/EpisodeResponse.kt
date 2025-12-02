package com.sparta.novel_insight.dto

data class EpisodeResponse(
    val id: Long,
    val title: String,
    val content: String,
    val price: Int,
    val isFree: Boolean,
    val isPurchased: Boolean,
    val viewCount: Long,      // ★ 추가
    val purchaseCount: Long   // ★ 추가
)