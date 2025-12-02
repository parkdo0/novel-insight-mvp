package com.sparta.novel_insight.common

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.*

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // ★ 제일 먼저 실행되게 설정 (보안 필터보다 앞)
class MDCLoggingFilter : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        // 1. 꼬리표(TraceId) 만들기
        val uuid = UUID.randomUUID().toString().substring(0, 8) // 너무 기니까 8자리만

        // 2. MDC(스레드 전용 주머니)에 넣기
        // 이제 이 스레드가 로그를 찍을 때마다 자동으로 [uuid]가 붙습니다.
        MDC.put("traceId", uuid)

        try {
            // 3. 원래 하려던 작업 진행 (Controller -> Service -> Repo ...)
            chain.doFilter(request, response)
        } finally {
            // 4. ★★★ [필수] 작업 끝나면 꼬리표 떼기
            // 스레드는 재사용되기 때문에(Thread Pool), 안 지우면 다음 사람한테 꼬리표가 붙음!
            MDC.clear()
        }
    }
}