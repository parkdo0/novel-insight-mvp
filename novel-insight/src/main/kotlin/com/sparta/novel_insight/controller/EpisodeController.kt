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
    private val redisTemplate: StringRedisTemplate, // Redis 주입
    // ★★★ Phase 2: WebSocket 실시간 알림 시스템 추가 ★★★
    private val notificationService: com.sparta.novel_insight.service.NotificationService, // 알림 발송 서비스
    private val memberRepository: com.sparta.novel_insight.repository.MemberRepository // 작가 조회용
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

        // 다음 회차 번호 계산 (기존 에피소드 개수 + 1)
        val nextEpisodeNumber = novel.episodes.size + 1

        val episode = Episode(
            title = request.title,
            content = request.content,
            episodeNumber = nextEpisodeNumber,
            price = if (request.isFree) 0 else 100,
            isFree = request.isFree,
            novel = novel
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
        // 1단계: Episode 조회
        val episode = episodeRepository.findById(episodeId).orElseThrow()

        // 2단계: Comment 생성 및 저장
        val comment = EpisodeComment(content=request.content, writerName=user.email.split("@")[0], writerId=user.id, episode=episode)
        commentRepository.save(comment)

        // ★★★ 3단계: WebSocket 실시간 알림 발송 (Phase 2 추가) ★★★
        // 댓글 작성 시 작가에게 NEW_COMMENT 알림 전송
        //
        // WHY 댓글에만 알림?
        // - 작가는 독자 반응에 관심이 많음
        // - 댓글은 직접적인 소통 수단
        // - 작가의 답글 유도 → 커뮤니티 활성화
        //
        // 알림 발송 흐름:
        // 1. Episode → Novel → authorId로 작가 조회
        // 2. NEW_COMMENT 타입 알림 생성
        // 3. DB 저장 + WebSocket 발송
        // 4. 작가가 온라인이면 즉시 알림 수신
        try {
            // 작가 조회
            // episode.novel.authorId: Episode의 소설 작가 ID
            // FetchType.LAZY이므로 novel 접근 시 추가 쿼리 발생
            // @Transactional 없으면 LazyInitializationException 발생 가능
            // → 현재는 컨트롤러에 @Transactional 없음 (주의 필요)
            //
            // TODO: 성능 최적화
            // - Episode 조회 시 JOIN FETCH novel 사용
            // - 또는 이 메서드에 @Transactional 추가
            val authorId = episode.novel.authorId
            val author = memberRepository.findById(authorId).orElse(null)

            // 알림 발송 (작가가 존재하는 경우만)
            if (author != null) {
                // 댓글 작성자 정보
                // user.email.split("@")[0]: 이메일 앞부분을 닉네임으로 사용
                // 예: "hong@example.com" → "hong"
                // TODO: Member 엔티티에 nickname 필드 추가 권장
                val commenterNickname = user.email.split("@")[0]

                // 알림 메시지 생성
                // NotificationType.NEW_COMMENT.getTemplate():
                // - "hong님이 '회차제목'에 댓글을 남겼습니다"
                // - 템플릿 파라미터: nickname, episodeTitle
                val message = com.sparta.novel_insight.domain.NotificationType.NEW_COMMENT.getTemplate(
                    mapOf(
                        "nickname" to commenterNickname,
                        "episodeTitle" to episode.title
                    )
                )

                // 댓글 작성자 Member 조회 (sender 정보)
                // WHY sender 필요?
                // - 알림 UI에 작성자 프로필 표시
                // - "누가 댓글을 남겼는지" 명확히 표시
                // - 향후 답글 기능 구현 시 필요
                val sender = memberRepository.findById(user.id).orElse(null)

                // NotificationService로 알림 발송
                // 파라미터:
                // - type: NEW_COMMENT
                // - recipient: author (작가)
                // - sender: 댓글 작성자 Member
                // - content: 생성된 메시지
                // - relatedId: comment.id (댓글 상세 페이지 이동용)
                //
                // 내부 동작:
                // 1. DB 저장: notification 테이블에 INSERT
                // 2. WebSocket 발송: /topic/notifications/{authorId}
                // 3. 작가가 구독 중이면 실시간 수신
                // 4. 프론트엔드: 브라우저 알림, 뱃지 업데이트
                notificationService.sendNotification(
                    type = com.sparta.novel_insight.domain.NotificationType.NEW_COMMENT,
                    recipient = author,
                    sender = sender,
                    content = message,
                    relatedId = comment.id // 프론트엔드에서 /episodes/{episodeId}/comments/{commentId} 이동
                )
            }
        } catch (e: Exception) {
            // 알림 발송 실패 시 로그만 출력 (댓글 저장은 성공 유지)
            // WHY 예외를 먹어버리는가?
            // - 알림 시스템 장애가 댓글 작성을 막으면 안 됨
            // - 사용자 경험 우선: 댓글은 반드시 저장되어야 함
            // - 알림은 부가 기능 (Nice-to-have)
            // - 오프라인 작가는 나중에 알림 조회 가능
            println("댓글 알림 발송 실패: ${e.message}")
            // TODO: 실무에서는 SLF4J Logger 사용
            // logger.error("댓글 알림 발송 실패: episodeId=$episodeId, commentId=${comment.id}", e)
        }

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