package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.Point
import com.sparta.novel_insight.repository.PointRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class PointService(
    private val pointRepository: PointRepository,
    private val redissonClient: RedissonClient // (1) 주입
) {

    fun createWallet(memberId: Long) {
        if (pointRepository.findByMemberId(memberId) == null) {
            pointRepository.save(Point(memberId = memberId, amount = 0))
        }
    }

    // ★★★ Redis 분산 락 적용
    fun chargePoint(memberId: Long, money: Long) {
        // (2) 락 이름 생성 (유저별로 잠금)
        // "lock:point:1" 이런 식으로 키가 생깁니다.
        val lock = redissonClient.getLock("lock:point:$memberId")

        try {
            // (3) 락 획득 시도 (최대 10초 기다리고, 1초 동안 잡고 있음)
            // tryLock(대기시간, 점유시간, 시간단위)
            val available = lock.tryLock(10, 1, TimeUnit.SECONDS)

            if (!available) {
                println("🔒 락 획득 실패 (누가 사용 중)")
                return
            }

            // (4) 핵심 로직 (DB 조회 -> 수정 -> 저장)
            // 여기는 안전지대입니다. 한 번에 한 명만 들어옵니다.
            val point = pointRepository.findByMemberId(memberId)
                ?: throw RuntimeException("지갑 없음")

            point.charge(money)

            pointRepository.save(point) // 즉시 저장

        } catch (e: InterruptedException) {
            throw RuntimeException("락 획득 중 에러")
        } finally {
            // (5) 락 해제 (필수!)
            // 이거 안 하면 영원히 아무도 접근 못 합니다.
            if (lock.isLocked && lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}