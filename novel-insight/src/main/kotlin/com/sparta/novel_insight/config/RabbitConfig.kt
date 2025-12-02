package com.sparta.novel_insight.config

import org.springframework.amqp.core.Queue
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig {

    // 1. 대기열(Queue) 생성: 이름은 'novel.notification'
    @Bean
    fun notificationQueue(): Queue {
        // durable=true: RabbitMQ가 꺼져도 메시지 안 날아가게 설정
        return Queue("novel.notification", true)
    }

    // 2. 메시지 변환기: 객체를 JSON으로 바꿔서 보내줌 (이거 없으면 깨짐)
    @Bean
    fun jsonMessageConverter(): Jackson2JsonMessageConverter {
        return Jackson2JsonMessageConverter()
    }
}