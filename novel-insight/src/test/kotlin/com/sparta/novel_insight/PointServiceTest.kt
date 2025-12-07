package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.Member
import com.sparta.novel_insight.repository.MemberRepository
import com.sparta.novel_insight.repository.PointRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class PointServiceTest @Autowired constructor(
    private val pointService: PointService,
    private val pointRepository: PointRepository,
    private val memberRepository: MemberRepository
) {

    @Test
    fun `concurrencyTest`() {
        // 0. 청소 (중요!)
        pointRepository.deleteAll()

        // 1. 테스트용 멤버 생성
        val member = memberRepository.findByEmail("test@point.com") ?: memberRepository.save(
            Member(
                email = "test@point.com",
                password = "test1234",
                nickname = "포인트테스트"
            )
        )
        val memberId = member.id!!

        // 2. 지갑 생성
        pointService.createWallet(memberId)

        // 2. 공격 준비 (스레드 32개로 조절 - DB 커넥션 풀 고려)
        val threadCount = 100
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        // 3. 공격 시작
        for (i in 0 until threadCount) {
            executorService.submit {
                try {
                    // 그냥 호출만 하면 됩니다. (Redisson이 알아서 대기시킴)
                    pointService.chargePoint(memberId, 1000)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // 4. 결과 확인
        val finalPoint = pointRepository.findByMemberId(memberId)!!
        println("최종 잔액: ${finalPoint.amount}원")

        assertEquals(100 * 1000L, finalPoint.amount)
    }
}