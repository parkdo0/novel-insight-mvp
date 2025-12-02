package com.sparta.novel_insight.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "episode_comment")
class EpisodeComment(
    @Column(nullable = false)
    var content: String,

    @Column(nullable = false)
    var writerName: String, // 작성자 닉네임

    @Column(nullable = false)
    var writerId: Long, // 작성자 ID (삭제 권한 확인용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    @JsonIgnore
    var episode: Episode,

    // 좋아요/싫어요 수
    var likeCount: Long = 0,
    var dislikeCount: Long = 0,

    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)