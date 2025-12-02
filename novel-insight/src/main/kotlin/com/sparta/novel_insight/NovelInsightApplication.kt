package com.sparta.novel_insight

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients

@EnableFeignClients
@SpringBootApplication
class NovelInsightApplication

fun main(args: Array<String>) {
	runApplication<NovelInsightApplication>(*args)
}
