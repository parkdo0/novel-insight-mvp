package com.sparta.novel_insight.dto

data class EpisodeResponse(
    val id: Long,
    val title: String,
    val content: String,
    val price: Int,
    val isFree: Boolean,
    val isPurchased: Boolean,
    val viewCount: Long,
    val purchaseCount: Long,
    val novelId: Long,        // 소설 ID (이전/다음화 네비게이션용)
    val episodeNumber: Int    // 에피소드 번호
)