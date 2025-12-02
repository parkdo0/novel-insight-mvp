package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.CommentLike
import com.sparta.novel_insight.domain.EpisodeComment
import org.springframework.data.jpa.repository.JpaRepository

interface EpisodeCommentRepository : JpaRepository<EpisodeComment, Long> {
    fun findAllByEpisodeIdOrderByCreatedAtDesc(episodeId: Long): List<EpisodeComment>
}

interface CommentLikeRepository : JpaRepository<CommentLike, Long> {
    fun findByUserIdAndCommentId(userId: Long, commentId: Long): CommentLike?
}