package com.sparta.novel_insight.config

import com.sparta.novel_insight.common.JwtPlugin
import com.sparta.novel_insight.security.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * # JwtAuthenticationFilter - JWT 기반 인증 필터
 *
 * ## 목적
 * - 모든 HTTP 요청에서 JWT 토큰을 추출하고 검증
 * - 유효한 토큰이면 SecurityContext에 인증 정보 설정
 * - Spring Security의 인증 체인에 통합
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. HttpOnly Cookie 우선 처리
 * - **기존 방식**: Authorization 헤더에서만 토큰 추출
 *   ```
 *   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6...
 *   ```
 *   - 문제: XSS 공격 시 localStorage에서 토큰 탈취 가능
 *
 * - **개선된 방식**: HttpOnly Cookie 우선 사용
 *   ```
 *   Cookie: accessToken=eyJhbGciOiJIUzI1NiIsInR5cCI6...
 *   ```
 *   - JavaScript 접근 불가 → XSS 방지
 *   - 브라우저가 자동으로 Cookie 전송 → 편의성
 *
 * ### 2. 하위 호환성 유지
 * - Cookie에 토큰이 없으면 Authorization 헤더 확인
 * - 기존 API 클라이언트 (모바일 앱 등) 지원
 *
 * ### 3. OncePerRequestFilter 사용
 * - 같은 요청에 대해 필터가 여러 번 실행되지 않도록 보장
 * - Forward/Include된 요청에서도 한 번만 실행
 *
 * ## 필터 체인 위치
 * ```
 * SecurityFilterChain:
 *   1. CorsFilter
 *   2. CsrfFilter
 *   3. JwtAuthenticationFilter ← 여기
 *   4. UsernamePasswordAuthenticationFilter
 *   5. FilterSecurityInterceptor
 * ```
 *
 * ## 프로세스
 * 1. HttpOnly Cookie에서 accessToken 추출
 * 2. Cookie에 없으면 Authorization 헤더에서 추출
 * 3. JWT 서명 및 만료 시간 검증
 * 4. Claims에서 사용자 정보 추출
 * 5. SecurityContext에 인증 정보 설정
 * 6. 다음 필터로 요청 전달
 */
@Component
class JwtAuthenticationFilter(
    private val jwtPlugin: JwtPlugin
) : OncePerRequestFilter() {

    /**
     * 필터 메인 로직
     *
     * ## 실행 시점
     * - 모든 HTTP 요청마다 실행
     * - Spring Security 필터 체인의 일부
     *
     * ## 인증 flow
     * 1. 토큰 추출 (Cookie 우선, 없으면 헤더)
     * 2. 토큰 검증 (JWT 서명, 만료 시간)
     * 3. SecurityContext에 인증 정보 설정
     * 4. 다음 필터로 전달
     *
     * ## 인증 실패 시
     * - SecurityContext에 인증 정보 설정 안 함
     * - 다음 필터로 계속 진행
     * - @PreAuthorize 등에서 접근 거부 처리
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 1. Cookie 또는 Header에서 JWT 추출
        val jwt = getJwtFromRequest(request)

        // 2. JWT가 있으면 검증 및 인증 정보 설정
        if (jwt != null) {
            jwtPlugin.validateToken(jwt).onSuccess { claims ->
                val userId = claims.body.subject.toLong()
                val email = claims.body["email"] as String
                val role = claims.body["role"] as String

                // 3. UserPrincipal 생성 (사용자 정보)
                val principal = UserPrincipal(
                    id = userId,
                    email = email,
                    grantedAuthorities = setOf(SimpleGrantedAuthority("ROLE_$role"))
                )

                // 4. Authentication 객체 생성
                val authentication = UsernamePasswordAuthenticationToken(
                    principal,       // principal: 인증된 사용자
                    null,           // credentials: 비밀번호 (이미 인증됨, 불필요)
                    principal.authorities  // authorities: 권한 목록
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                // 5. SecurityContext에 인증 정보 설정
                // 이후 @AuthenticationPrincipal로 principal 접근 가능
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        // 6. 다음 필터로 요청 전달
        filterChain.doFilter(request, response)
    }

    /**
     * HTTP 요청에서 JWT 토큰 추출
     *
     * ## 우선순위 (Production Best Practices)
     * 1. **HttpOnly Cookie** (우선) - XSS 방지
     * 2. **Authorization Header** (하위 호환) - 모바일 앱 등
     *
     * ## Cookie 방식 (권장)
     * ```http
     * GET /api/novels HTTP/1.1
     * Cookie: accessToken=eyJhbGciOiJIUzI1NiIsInR5cCI6...
     * ```
     * - 장점: JavaScript 접근 불가 (XSS 방지)
     * - 장점: 브라우저가 자동으로 전송
     * - 단점: CSRF 공격 가능 (CSRF 토큰으로 방어)
     *
     * ## Header 방식 (하위 호환)
     * ```http
     * GET /api/novels HTTP/1.1
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6...
     * ```
     * - 장점: CSRF 공격 불가 (수동으로 헤더 설정 필요)
     * - 단점: XSS 공격 시 localStorage에서 탈취 가능
     * - 사용: 모바일 앱, 데스크톱 앱, Postman 등
     *
     * ## 왜 Cookie 우선?
     * - 웹 브라우저 사용자 보호 (XSS 방어)
     * - 모바일 앱은 Cookie 미사용 → Header로 자동 fallback
     *
     * @param request HTTP 요청
     * @return JWT 토큰 문자열 (없으면 null)
     */
    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        // 1. Cookie에서 accessToken 찾기 (우선)
        val cookies = request.cookies
        if (cookies != null) {
            val accessTokenCookie = cookies.find { it.name == "accessToken" }
            if (accessTokenCookie != null && accessTokenCookie.value.isNotEmpty()) {
                return accessTokenCookie.value
            }
        }

        // 2. Authorization 헤더에서 Bearer 토큰 찾기 (하위 호환)
        val bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION)
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)  // "Bearer " 제거
        } else {
            null
        }
    }
}