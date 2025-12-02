package com.sparta.novel_insight.domain

import jakarta.persistence.*

enum class LikeType { LIKE, DISLIKE }

@Entity
@Table(
    name = "comment_like",
    uniqueConstraints = [UniqueConstraint(columnNames = ["userId", "commentId"])]
)
class CommentLike(
    @Column(nullable = false)
    val userId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    val comment: EpisodeComment,

    @Enumerated(EnumType.STRING)
    val type: LikeType,

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)