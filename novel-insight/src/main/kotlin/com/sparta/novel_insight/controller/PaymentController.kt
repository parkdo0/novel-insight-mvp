package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.PointHistory
import com.sparta.novel_insight.domain.TransactionType
import com.sparta.novel_insight.repository.PointHistoryRepository
import com.sparta.novel_insight.repository.PointRepository
import com.sparta.novel_insight.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ChargeRequest(val amount: Long)

@RestController
class PaymentController(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository
) {

    @PostMapping("/payments/charge")
    @Transactional
    fun chargePoint(
        @AuthenticationPrincipal user: UserPrincipal,
        @RequestBody request: ChargeRequest
    ): ResponseEntity<String> {

        // 1. 지갑 조회
        val point = pointRepository.findByMemberId(user.id)
            ?: throw RuntimeException("지갑 없음")

        // 2. 포인트 충전 (더하기)
        point.amount += request.amount
        pointRepository.save(point)

        // 3. 내역 기록 (CHARGE)
        val history = PointHistory(
            userId = user.id,
            type = TransactionType.CHARGE,
            amount = request.amount,
            description = "포인트 충전"
        )
        pointHistoryRepository.save(history)

        return ResponseEntity.ok("충전 완료: ${point.amount}P")
    }
}