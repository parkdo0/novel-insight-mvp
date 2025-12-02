package com.sparta.novel_insight.service

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.messaging.handler.annotation.Header // ★ 이 import가 맞는지 꼭 확인!
import org.springframework.stereotype.Service

@Service
class RabbitConsumer {

    private val log = LoggerFactory.getLogger(RabbitConsumer::class.java)

    // ★ @Header("traceId") traceId: String? -> 헤더가 없을 수도 있으니 nullable(?)로 받아야 안전함
    @RabbitListener(queues = ["novel.notification"])
    fun receiveMessage(message: String, @Header("traceId") traceId: String?) {

        // 1. 헤더에서 꺼낸 ID를 MDC에 넣기 (없으면 "NO_ID")
        MDC.put("traceId", traceId ?: "NO_ID")

        try {
            log.info("📥 [Consumer] 메시지 수신! (TraceId 이어받음): {}", message)

            // 작업 시뮬레이션
            Thread.sleep(3000)

            log.info("✅ [Consumer] 알림 발송 완료!")
        } finally {
            // 2. 스레드 오염 방지 청소
            MDC.clear()
        }
    }
}