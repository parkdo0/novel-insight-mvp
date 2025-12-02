package com.sparta.novel_insight.client

import com.sparta.novel_insight.dto.AnalysisRequest
import com.sparta.novel_insight.dto.AnalysisResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

// name: 그냥 이름, url: 파이썬 서버 주소
@FeignClient(name = "python-client", url = "http://localhost:8000")
interface PythonClient {

    // 파이썬의 @app.post("/analyze") 주소와 맞춤
    @PostMapping("/analyze")
    fun analyzeText(@RequestBody request: AnalysisRequest): AnalysisResponse
}