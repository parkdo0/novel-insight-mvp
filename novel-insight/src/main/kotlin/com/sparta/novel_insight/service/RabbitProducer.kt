package com.sparta.novel_insight.service

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class RabbitProducer(
    private val rabbitTemplate: RabbitTemplate
) {
    private val log = LoggerFactory.getLogger(RabbitProducer::class.java)

    fun sendNotification(message: String) {
        val traceId = MDC.get("traceId") ?: UUID.randomUUID().toString().substring(0, 8)

        log.info("📤 RabbitMQ로 메시지 전송 시도: {}", message)

        // ★★★ [수정] 맨 앞에 "" (빈 문자열)을 추가했습니다.
        // 순서: (Exchange이름, RoutingKey, 메시지, 후처리기)
        // 이러면 컴파일러가 헷갈리지 않습니다.
        rabbitTemplate.convertAndSend("", "novel.notification", message) { m ->
            m.messageProperties.headers["traceId"] = traceId
            m
        }

        log.info("✅ 전송 완료! (기다리지 않음)")
    }
}