package com.sparta.novel_insight.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

/**
 * Kafka Producer 서비스
 * 리뷰/로그 데이터를 Kafka로 전송
 */
@Service
class KafkaProducerService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(KafkaProducerService::class.java)

    companion object {
        const val REVIEW_TOPIC = "novel-reviews"  // 리뷰 로그 토픽
    }

    /**
     * 리뷰 로그를 Kafka로 전송
     * @param novelId 소설 ID
     * @param authorId 작가 ID
     * @param reviewContent 리뷰 내용
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
            // JSON으로 변환
            val jsonLog = objectMapper.writeValueAsString(logData)

            // Kafka로 전송 (Key: novelId, Value: JSON)
            kafkaTemplate.send(REVIEW_TOPIC, novelId.toString(), jsonLog)

            log.info("📤 Kafka로 리뷰 로그 전송 완료: novelId=$novelId, traceId=$traceId")
        } catch (e: Exception) {
            log.error("❌ Kafka 전송 실패: ${e.message}", e)
            // ★ 중요: Kafka 장애 시에도 서비스는 정상 동작해야 함 (예외를 던지지 않음)
        }
    }
}
