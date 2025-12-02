package com.sparta.novel_insight.controller

import com.sparta.novel_insight.client.PythonClient
import com.sparta.novel_insight.dto.AnalysisRequest
import com.sparta.novel_insight.dto.AnalysisResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AnalysisController(
    private val pythonClient: PythonClient // (1) 주입
) {

    @PostMapping("/api/analyze")
    fun analyze(@RequestBody request: AnalysisRequest): ResponseEntity<AnalysisResponse> {
        println("🤖 Java: 파이썬에게 분석 요청 보냄... (${request.text})")

        // (2) 마치 자바 메소드 부르듯이 호출! (실제로는 HTTP 요청이 나감)
        val response = pythonClient.analyzeText(request)

        println("✅ Java: 응답 받음! -> ${response.result}")
        return ResponseEntity.ok(response)
    }
}