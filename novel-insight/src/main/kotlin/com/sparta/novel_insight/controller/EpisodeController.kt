package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.*
import com.sparta.novel_insight.dto.EpisodeResponse
import com.sparta.novel_insight.repository.*
import com.sparta.novel_insight.security.UserPrincipal
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
class EpisodeController(
    private val episodeRepository: EpisodeRepository,
    private val novelRepository: NovelRepository,
    private val purchaseRepository: PurchaseRepository,
    private val commentRepository: EpisodeCommentRepository,
    private val likeRepository: CommentLikeRepository,
    private val redisTemplate: StringRedisTemplate // Redis 주입
) {

    @GetMapping("/novels/{novelId}/episodes")
    fun getEpisodes(
        @PathVariable novelId: Long,
        @AuthenticationPrincipal user: UserPrincipal?
    ): ResponseEntity<List<EpisodeResponse>> {
        val episodes = episodeRepository.findAllByNovelId(novelId)
        val novel = novelRepository.findById(novelId).orElseThrow()
        val isAuthor = user?.id == novel.authorId

        val purchasedIds = if (user != null && episodes.isNotEmpty()) {
            val ids = episodes.map { it.id!! }
            purchaseRepository.findPurchasedEpisodeIds(user.id, ids).toSet()
        } else {
            emptySet()
        }

        val response = episodes.map { ep ->
            EpisodeResponse(
                id = ep.id!!,
                title = ep.title,
                content = ep.content,
                price = ep.price,
                isFree = ep.isFree,
                isPurchased = isAuthor || ep.isFree || purchasedIds.contains(ep.id),
                viewCount = ep.viewCount,
                purchaseCount = ep.purchaseCount
            )
        }
        return ResponseEntity.ok(response)
    }

    // ★★★ [이 부분이 수정되었습니다] ★★★
    @GetMapping("/episodes/{id}")
    @Transactional
    fun getEpisodeDetail(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserPrincipal?
    ): ResponseEntity<Episode> {
        val episode = episodeRepository.findById(id)
            .orElseThrow { RuntimeException("회차를 찾을 수 없습니다.") }

        // ★★★ [버그 수정] 유료 에피소드 구매 여부 체크 ★★★
        if (!episode.isFree && episode.price > 0) {
            // 로그인하지 않은 경우
            if (user == null) {
                return ResponseEntity.status(403).body(null)
            }
            // 작가 본인이 아니고, 구매하지 않은 경우
            val isAuthor = episode.novel.authorId == user.id
            val isPurchased = purchaseRepository.existsByUserIdAndEpisodeId(user.id, id)
            if (!isAuthor && !isPurchased) {
                return ResponseEntity.status(403).body(null)
            }
        }

        // 1. 사용자 식별 (로그인 ID 또는 guest)
        val viewerId = user?.id?.toString() ?: "guest"

        // 2. Redis Key 생성 (예: view:episode:1:user:10)
        val key = "view:episode:$id:user:$viewerId"

        // 3. Redis에 키가 없으면(첫방문) true, 있으면 false 반환
        // (24시간 동안 유지됨)
        val isFirstView = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", Duration.ofHours(24)) ?: false

        // 4. 첫 방문일 때만 조회수 증가
        if (isFirstView) {
            episode.increaseView()
            episode.novel.viewCount++ // 소설 전체 조회수도 증가
            // (Transactional이 걸려있어서 자동 저장됨)
        }

        return ResponseEntity.ok(episode)
    }

    @PostMapping("/novels/{novelId}/episodes")
    fun createEpisode(@PathVariable novelId: Long, @RequestBody request: CreateEpisodeRequest, @AuthenticationPrincipal user: UserPrincipal): ResponseEntity<String> {
        val novel = novelRepository.findById(novelId).orElseThrow()
        if (novel.authorId != user.id) return ResponseEntity.status(403).body("본인의 소설에만 회차를 등록할 수 있습니다.")
        val episode = Episode(
            title = request.title, content = request.content,
            price = if (request.isFree) 0 else 100, isFree = request.isFree, novel = novel
        )
        episodeRepository.save(episode)
        return ResponseEntity.ok("등록 완료")
    }

    @GetMapping("/episodes/{episodeId}/comments")
    fun getComments(@PathVariable episodeId: Long, @AuthenticationPrincipal user: UserPrincipal?): ResponseEntity<List<CommentResponse>> {
        val comments = commentRepository.findAllByEpisodeIdOrderByCreatedAtDesc(episodeId)
        val response = comments.map { CommentResponse(it.id!!, it.content, it.writerName, it.likeCount, it.dislikeCount, user != null && user.id == it.writerId) }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/episodes/{episodeId}/comments")
    fun addComment(@PathVariable episodeId: Long, @RequestBody request: CreateCommentRequest, @AuthenticationPrincipal user: UserPrincipal): ResponseEntity<String> {
        val episode = episodeRepository.findById(episodeId).orElseThrow()
        val comment = EpisodeComment(content=request.content, writerName=user.email.split("@")[0], writerId=user.id, episode=episode)
        commentRepository.save(comment)
        return ResponseEntity.ok("등록 완료")
    }

    @PostMapping("/comments/{commentId}/like")
    @Transactional
    fun toggleLike(@PathVariable commentId: Long, @RequestParam type: LikeType, @AuthenticationPrincipal user: UserPrincipal): ResponseEntity<String> {
        val comment = commentRepository.findById(commentId).orElseThrow()
        val existing = likeRepository.findByUserIdAndCommentId(user.id, commentId)
        if (existing != null) return ResponseEntity.badRequest().body("이미 참여했습니다.")
        likeRepository.save(CommentLike(userId=user.id, comment=comment, type=type))
        if (type == LikeType.LIKE) comment.likeCount++ else comment.dislikeCount++
        return ResponseEntity.ok("반영 완료")
    }
}

// DTOs
data class CreateEpisodeRequest(val title: String, val content: String, val isFree: Boolean)
data class CreateCommentRequest(val content: String)
data class CommentResponse(val id: Long, val content: String, val writerName: String, val likeCount: Long, val dislikeCount: Long, val isMine: Boolean)