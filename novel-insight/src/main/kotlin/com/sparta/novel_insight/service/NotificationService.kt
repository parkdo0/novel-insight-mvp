package com.sparta.novel_insight.service

import org.springframework.stereotype.Service

@Service
class NotificationService {

    fun sendToAuthor() {
        // 일부러 3초 동안 멈추게 함 (메일 서버 통신 지연 시뮬레이션)
        println("📧 알림 발송 시작... (동기 방식)")
        try {
            Thread.sleep(3000) // 3초대기
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        println("✅ 알림 발송 완료!")
    }
}