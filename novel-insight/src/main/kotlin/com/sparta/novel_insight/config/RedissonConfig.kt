package com.sparta.novel_insight.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig {

    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        // Step 0에서 띄운 Docker Redis 주소
        config.useSingleServer().address = "redis://127.0.0.1:6379"
        return Redisson.create(config)
    }
}