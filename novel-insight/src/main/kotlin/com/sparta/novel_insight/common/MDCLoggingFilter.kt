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

//
// ========================================
// MDCLoggingFilter - 분산 추적을 위한 로깅 필터
// ========================================
//
// [핵심 개념]
// 1. MDC (Mapped Diagnostic Context): ThreadLocal 기반 로그 컨텍스트 저장소
// 2. TraceId: 하나의 HTTP 요청을 식별하는 고유 ID
// 3. Filter: Servlet 스펙의 요청/응답 전처리기
//
// [5~6년차 개발자가 반드시 알아야 할 필수 개념]
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//
// 1. Filter vs Interceptor vs AOP (매우 중요!)
// ┌───────────────────────────────────────────────────────────────────────┐
// │ 구분        │ Filter               │ Interceptor           │ AOP       │
// ├───────────────────────────────────────────────────────────────────────┤
// │ 스펙        │ Java Servlet         │ Spring MVC            │ Spring    │
// │ 실행 시점   │ Servlet 전/후        │ Controller 전/후      │ 메소드 전/후│
// │ 설정 위치   │ web.xml 또는 @Component│ WebMvcConfigurer    │ @Aspect   │
// │ 예외 처리   │ 어려움               │ 가능                   │ 가능      │
// │ Request/    │ 가능                 │ 가능                   │ 불가      │
// │ Response    │                      │                        │           │
// │ 주 용도     │ 인코딩, 인증, 로깅   │ 권한 검증, 공통 로직  │ 트랜잭션  │
// └───────────────────────────────────────────────────────────────────────┘
//
// [실행 순서]
// Client → Filter → DispatcherServlet → Interceptor → Controller → AOP → Service
// ↑
// 현재 Filter (MDCLoggingFilter)
//
// 2. 왜 Interceptor가 아닌 Filter를 사용하는가?
// a. MDC 설정은 모든 요청에 적용되어야 함
// - Filter는 DispatcherServlet보다 먼저 실행
// - Interceptor는 Spring MVC 내부에서만 동작
// - 정적 리소스(/css, /js) 요청도 TraceId 필요
//
// b. TraceId는 최대한 빨리 설정되어야 함
// - Filter가 가장 먼저 실행되므로 모든 로그에 TraceId 포함
// - 인증 필터(JwtAuthenticationFilter)도 TraceId 로그 출력 가능
//
// c. Spring 컨텍스트 없이도 동작
// - Filter는 Servlet Container가 관리
// - Spring Boot 종료 중에도 로그 추적 가능
//
// 3. MDC (Mapped Diagnostic Context) 상세 설명
// [MDC란?]
// - SLF4J가 제공하는 ThreadLocal 기반 맵
// - Key-Value 형태로 로그 컨텍스트 저장
// - 같은 스레드 내 모든 로거가 자동으로 값 사용
//
// [동작 원리]
// 1. MDC.put("traceId", "12ab34cd")
// → ThreadLocal.set(Map("traceId" → "12ab34cd"))
// 2. log.info("사용자 로그인")
// → [12ab34cd] 사용자 로그인 (자동으로 TraceId 추가)
// 3. MDC.clear()
// → ThreadLocal.remove()
//
// [ThreadLocal 주의사항]
// - Thread Pool 환경에서는 clear() 필수!
// - Tomcat은 스레드를 재사용 → MDC 값이 다음 요청에 남을 수 있음
// - 메모리 누수 방지: finally 블록에서 반드시 clear()
//
// 4. @Order(HIGHEST_PRECEDENCE) 설명
// [Filter 실행 순서]
// - Spring Boot는 여러 Filter를 체인으로 연결
// - @Order 값이 작을수록 먼저 실행
// - HIGHEST_PRECEDENCE = Integer.MIN_VALUE (가장 먼저)
//
// [이 프로젝트의 Filter 실행 순서]
// 1. MDCLoggingFilter (Order: HIGHEST_PRECEDENCE)
// → TraceId 생성 및 MDC 설정
// 2. JwtAuthenticationFilter (Order: 미지정, 기본값)
// → JWT 검증 및 SecurityContext 설정
// 3. Spring Security Filter Chain
// → CSRF, CORS, 인가 처리
//
// [왜 가장 먼저 실행되어야 하는가?]
// - 모든 Filter의 로그에 TraceId가 포함되어야 함
// - JwtAuthenticationFilter에서 인증 실패 로그를 찍을 때도 TraceId 필요
// - 예: [a1b2c3d4] JWT 토큰 검증 실패
//
// 5. TraceId 활용 시나리오 (분산 추적)
// [문제 상황]
// - MSA 환경에서 여러 서비스 간 요청 추적 어려움
// - 로그가 여러 서버에 분산 → 하나의 요청 흐름 파악 불가
//
// [해결 방법]
// 1. 첫 서비스(Gateway)에서 TraceId 생성
// 2. HTTP 헤더로 다음 서비스에 전달
// - X-Trace-Id: a1b2c3d4
// 3. 각 서비스는 받은 TraceId를 MDC에 저장
// 4. 모든 로그에 TraceId 포함
// 5. 로그 집계 도구(ELK, Splunk)로 TraceId 검색
// → 하나의 요청 전체 흐름 추적 가능
//
// [실무 코드 예시]
// val traceId = request.getHeader("X-Trace-Id") ?: UUID.randomUUID().toString()
// MDC.put("traceId", traceId)
//
// 6. 로그 패턴 설정 (logback.xml)
// <pattern>%d{yyyy-MM-dd HH:mm:ss} [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
// ↑
// MDC 값 출력
// [결과]
// 2025-12-03 14:30:45 [a1b2c3d4] INFO  c.s.n.controller.UserController - 사용자 로그인 시도
// 2025-12-03 14:30:45 [a1b2c3d4] INFO  c.s.n.service.UserService - 사용자 조회: user@example.com
// 2025-12-03 14:30:45 [a1b2c3d4] INFO  c.s.n.service.UserService - 로그인 성공
//
// 7. Filter 라이프사이클
// a. 초기화: init() - 서버 시작 시 한 번 실행 (생략 가능)
// b. 요청 처리: doFilter() - 모든 HTTP 요청마다 실행
// c. 종료: destroy() - 서버 종료 시 한 번 실행 (생략 가능)
//
// [doFilter() 동작]
// 1. 전처리: TraceId 생성 및 MDC 설정
// 2. chain.doFilter() 호출 → 다음 Filter 또는 Servlet 실행
// 3. 후처리: MDC.clear() (finally 블록에서 보장)
//
// 8. @Component vs FilterRegistrationBean
// [방법 1] @Component (현재 사용)
// - 간단하고 직관적
// - 모든 URL에 자동 적용
//
// [방법 2] FilterRegistrationBean (세밀한 제어)
// @Bean
// fun mdcFilter(): FilterRegistrationBean<MDCLoggingFilter> {
// val registration = FilterRegistrationBean<MDCLoggingFilter>()
// registration.filter = MDCLoggingFilter()
// registration.addUrlPatterns("/api/*")  // 특정 URL만
// registration.order = Ordered.HIGHEST_PRECEDENCE
// return registration
// }
//
// 9. 성능 고려사항
// - UUID 생성은 빠름 (나노초 단위)
// - ThreadLocal 읽기/쓰기도 빠름
// - 모든 요청마다 실행되지만 성능 영향 미미
// - substring(0, 8): 로그 가독성 향상 (32자 → 8자)
//
// 10. 실무 확장 사례
// a. 사용자 ID 추가
// - JWT에서 userId 추출 후 MDC.put("userId", userId)
// - 로그: [a1b2c3d4][user123] 주문 생성
//
// b. 요청 IP 추가
// - MDC.put("ip", request.remoteAddr)
// - 보안 감사 로그에 유용
//
// c. 요청 URL 추가
// - MDC.put("url", request.requestURI)
// - 특정 API의 로그만 필터링 가능
//
// [실무 체크리스트]
// ✓ MDC.clear()를 finally 블록에서 반드시 호출
// ✓ @Order로 Filter 실행 순서 명시
// ✓ TraceId를 HTTP 헤더로 전파 (MSA 환경)
// ✓ logback.xml에 %X{traceId} 패턴 추가
// ✓ 로그 집계 도구(ELK)와 연동
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Component  // IoC: Spring 컨테이너가 Filter 빈으로 등록 → 자동으로 필터 체인에 추가
@Order(Ordered.HIGHEST_PRECEDENCE)  // 가장 먼저 실행 (다른 모든 Filter보다 우선)
class MDCLoggingFilter : Filter {

    //
    // [doFilter 메소드]
    //
    // 모든 HTTP 요청마다 실행되는 필터 로직
    //
    // @param request  HTTP 요청 객체 (ServletRequest)
    // @param response HTTP 응답 객체 (ServletResponse)
    // @param chain    필터 체인 (다음 필터 또는 서블릿으로 전달)
    //
    // [실행 흐름]
    // 1. TraceId 생성 (UUID 8자리)
    // 2. MDC에 TraceId 저장 (ThreadLocal)
    // 3. 다음 필터/서블릿 실행 (chain.doFilter)
    // 4. 응답 완료 후 MDC 정리 (finally)
    //
    // [중요] try-finally 패턴
    // - chain.doFilter()에서 예외 발생해도 MDC.clear() 보장
    // - Thread Pool 환경에서 메모리 누수 방지
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        //
        // [1단계] TraceId 생성
        //
        // UUID.randomUUID(): 128비트 고유 식별자 생성
        // - 예: 550e8400-e29b-41d4-a716-446655440000
        // - 충돌 확률: 거의 0% (2^122개 생성해도 50% 확률로 충돌 없음)
        //
        // substring(0, 8): 앞 8자리만 사용
        // - 이유: 로그 가독성 향상 (32자 → 8자)
        // - 충돌 가능성 증가하지만 단일 서버에서는 문제 없음
        // - 분산 환경에서는 전체 UUID 사용 권장
        val uuid = UUID.randomUUID().toString().substring(0, 8)

        //
        // [2단계] MDC에 TraceId 저장
        //
        // MDC.put(key, value): ThreadLocal Map에 저장
        // - 현재 스레드 전용 저장소
        // - 이후 모든 log.info(), log.error() 등에서 자동으로 traceId 참조
        //
        // [예시]
        // MDC.put("traceId", "12ab34cd")
        // log.info("사용자 로그인")
        // → 출력: [12ab34cd] 사용자 로그인
        MDC.put("traceId", uuid)

        try {
            //
            // [3단계] 다음 필터/서블릿으로 요청 전달
            //
            // chain.doFilter(request, response):
            // - 필터 체인의 다음 요소 실행
            // - 마지막 필터라면 DispatcherServlet 실행
            // - 이후 Controller → Service → Repository 순서로 실행
            // - 모든 계층에서 같은 TraceId 사용 (ThreadLocal 덕분)
            //
            // [주의] 이 메소드는 블로킹
            // - 요청 처리가 완료될 때까지 대기
            // - 응답이 완료되면 제어권이 돌아옴
            chain.doFilter(request, response)
        } finally {
            //
            // [4단계] MDC 정리 (필수!)
            //
            // MDC.clear(): ThreadLocal Map 초기화
            // - 현재 스레드의 모든 MDC 값 제거
            // - ThreadLocal.remove() 호출
            //
            // [왜 필수인가?]
            // 1. Thread Pool 재사용 문제
            // - Tomcat은 스레드를 재사용 (200개 스레드 풀)
            // - MDC를 clear하지 않으면:
            // * 스레드 A: TraceId "12ab34cd" 설정
            // * 요청 완료 후 스레드 풀에 반환
            // * 새 요청이 같은 스레드 A 사용
            // * 이전 TraceId "12ab34cd"가 그대로 남음 (버그!)
            //
            // 2. 메모리 누수 방지
            // - ThreadLocal은 strong reference 유지
            // - clear하지 않으면 GC 불가 → 메모리 누수
            //
            // [finally 블록 사용 이유]
            // - chain.doFilter()에서 예외 발생해도 clear() 보장
            // - 예: NullPointerException, 500 에러 등
            // - 예외가 발생해도 MDC는 정리되어야 함
            MDC.clear()
        }
    }
}