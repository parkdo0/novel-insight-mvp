package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.PointHistory
import com.sparta.novel_insight.domain.Purchase
import com.sparta.novel_insight.domain.TransactionType
import com.sparta.novel_insight.repository.EpisodeRepository
import com.sparta.novel_insight.repository.MemberRepository
import com.sparta.novel_insight.repository.PointHistoryRepository
import com.sparta.novel_insight.repository.PointRepository
import com.sparta.novel_insight.repository.PurchaseRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class PurchaseService(
    private val pointRepository: PointRepository,
    private val episodeRepository: EpisodeRepository,
    private val purchaseRepository: PurchaseRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val redissonClient: RedissonClient // (분산락은 여기선 생략했지만 실무에선 필수)
) {

    @Transactional
    fun purchaseEpisode(userId: Long, episodeId: Long) {
        // 1. 에피소드 조회
        val episode = episodeRepository.findById(episodeId)
            .orElseThrow { RuntimeException("존재하지 않는 에피소드입니다.") }

        // 2. 검증
        if (episode.isFree || episode.price == 0) throw RuntimeException("무료 에피소드입니다.")
        if (purchaseRepository.existsByUserIdAndEpisodeId(userId, episodeId)) throw RuntimeException("이미 구매한 에피소드입니다.")

        // 3. 지갑 & 잔액 확인
        val point = pointRepository.findByMemberId(userId) ?: throw RuntimeException("포인트 지갑이 없습니다.")
        if (point.amount < episode.price) throw RuntimeException("포인트가 부족합니다.")

        // 4. 결제 (차감)
        point.amount -= episode.price

        // 5. 구매 내역 저장
        val purchase = Purchase(userId = userId, episodeId = episodeId, price = episode.price)
        purchaseRepository.save(purchase)

        // 6. 가계부 기록
        val history = PointHistory(
            userId = userId, type = TransactionType.USE, amount = episode.price.toLong(),
            description = "${episode.title} 구매"
        )
        pointHistoryRepository.save(history)

        // ★★★ [수정] 에피소드 구매수 증가 (이게 빠졌었습니다!)
        episode.increasePurchase()
        // (Dirty Checking에 의해 자동으로 DB Update 쿼리가 나갑니다)
    }
}