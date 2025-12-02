package com.sparta.novel_insight.controller

import com.sparta.novel_insight.security.UserPrincipal
import com.sparta.novel_insight.service.PurchaseService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PurchaseController(
    private val purchaseService: PurchaseService
    // memberRepository는 이제 필요 없습니다! (삭제)
) {

    @PostMapping("/episodes/{episodeId}/purchase")
    fun purchaseEpisode(
        @PathVariable episodeId: Long,
        // ★ [수정] String 대신 UserPrincipal 객체로 받음
        @AuthenticationPrincipal user: UserPrincipal?
    ): ResponseEntity<String> {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.")
        }

        // ★ [핵심] DB 조회 없이 토큰에 있던 ID(user.id)를 바로 사용!
        purchaseService.purchaseEpisode(user.id, episodeId)

        return ResponseEntity.ok("구매 완료")
    }
}