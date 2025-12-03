package com.sparta.novel_insight.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

/**
 * Kafka Producer
 * 리뷰 로그를 Kafka로 비동기 전송
 */
@Service
class KafkaProducerService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val REVIEW_TOPIC = "novel-reviews"
    }

    /**
     * 리뷰 로그 전송 (비동기)
     */
    fun sendReviewLog(novelId: Long, authorId: Long, reviewContent: String) {
        val traceId = MDC.get("traceId") ?: "NO-TRACE"

        val logData = mapOf(
            "traceId" to traceId,
            "timestamp" to System.currentTimeMillis(),
            "novelId" to novelId,
            "authorId" to authorId,
            "content" to reviewContent
        )

        try {
            val jsonLog = objectMapper.writeValueAsString(logData)

            val future: CompletableFuture<SendResult<String, String>> =
                kafkaTemplate.send(REVIEW_TOPIC, novelId.toString(), jsonLog)

            future.whenComplete { result, ex ->
                if (ex == null) {
                    log.info("✅ Kafka 전송 성공: novelId=$novelId, offset=${result.recordMetadata.offset()}")
                } else {
                    log.error("❌ Kafka 전송 실패: novelId=$novelId", ex)
                }
            }
        } catch (e: Exception) {
            log.error("❌ Kafka 전송 중 예외 발생: novelId=$novelId", e)
        }
    }
}
