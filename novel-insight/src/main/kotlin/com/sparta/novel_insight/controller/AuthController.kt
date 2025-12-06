package com.sparta.novel_insight.controller

import com.sparta.novel_insight.security.UserPrincipal
import com.sparta.novel_insight.service.TokenService
import com.sparta.novel_insight.repository.MemberRepository
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * # AuthController - 인증 관련 API
 *
 * ## 담당 기능
 * - Token Refresh: Access Token 갱신
 * - Logout: 토큰 무효화 (단일 기기 / 모든 기기)
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. Token Rotation
 * - Refresh Token 사용 시마다 새로운 Refresh Token 발급
 * - 기존 Refresh Token 즉시 무효화
 * - **이유**: 탈취된 Refresh Token의 재사용 방지
 *
 * ### 2. Reuse Detection (재사용 감지)
 * - 이미 사용된 Refresh Token이 다시 사용되면 보안 위협으로 간주
 * - 해당 사용자의 모든 토큰 무효화 (긴급 조치)
 * - **시나리오**:
 *   1. 공격자가 Refresh Token 탈취
 *   2. 공격자가 토큰으로 갱신 (성공)
 *   3. 정상 사용자가 같은 토큰으로 갱신 시도
 *   4. 재사용 감지 → 모든 기기 로그아웃
 *
 * ### 3. HttpOnly Cookie
 * - Refresh Token은 HttpOnly Cookie로만 전송
 * - JavaScript 접근 불가 → XSS 방지
 * - Path 제한: /api/auth (이 Controller에서만 사용)
 *
 * ### 4. CSRF 보호
 * - Cookie 기반 인증은 CSRF 공격에 취약
 * - Spring Security의 CSRF 토큰 활성화 필요
 * - SameSite=Lax 설정으로 추가 방어
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val tokenService: TokenService,
    private val memberRepository: MemberRepository
) {

    /**
     * Token Refresh API
     *
     * ## 목적
     * - Access Token이 만료되었을 때 새로운 Access Token 발급
     * - 사용자가 재로그인하지 않아도 되도록 함
     *
     * ## Token Rotation 프로세스
     * 1. Refresh Token 검증 (JWT 서명, 만료 시간)
     * 2. DB에서 Refresh Token 조회
     * 3. **Reuse Detection**: 이미 사용된 토큰인지 확인
     *    - lastUsedAt != null → 재사용 → 모든 토큰 무효화
     * 4. 새로운 Access Token 생성
     * 5. **새로운 Refresh Token 생성** (Token Rotation)
     * 6. 기존 Refresh Token 무효화
     * 7. 새로운 토큰 쌍을 HttpOnly Cookie로 반환
     *
     * ## 클라이언트 사용법
     * ```javascript
     * // Access Token 만료 시 자동으로 Refresh 시도
     * async function apiCall(url) {
     *   let response = await fetch(url, {
     *     credentials: 'include'  // Cookie 전송
     *   })
     *
     *   if (response.status === 401) {
     *     // Access Token 만료 → Refresh 시도
     *     const refreshResponse = await fetch('/api/auth/refresh', {
     *       method: 'POST',
     *       credentials: 'include'
     *     })
     *
     *     if (refreshResponse.ok) {
     *       // 새로운 Access Token 발급됨 → 원래 요청 재시도
     *       response = await fetch(url, {
     *         credentials: 'include'
     *       })
     *     } else {
     *       // Refresh Token도 만료 → 로그인 페이지로
     *       window.location.href = '/login'
     *     }
     *   }
     *
     *   return response.json()
     * }
     * ```
     *
     * ## 에러 처리
     * - 401 Unauthorized: Refresh Token 무효/만료
     * - 403 Forbidden: Refresh Token 재사용 감지 (모든 기기 로그아웃됨)
     *
     * @param refreshToken Refresh Token (Cookie에서 자동 추출)
     * @param request HTTP 요청 (IP, User Agent 추출용)
     * @param response HTTP 응답 (Cookie 설정용)
     * @return 성공 메시지 (새로운 토큰은 Cookie로 전송)
     */
    @PostMapping("/refresh")
    fun refreshToken(
        @CookieValue(name = "refreshToken", required = false) refreshToken: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<String> {
        // 1. Refresh Token 존재 여부 확인
        if (refreshToken == null) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("Refresh Token이 없습니다. 다시 로그인해주세요.")
        }

        return try {
            // 2. Token Rotation 수행 (새로운 토큰 쌍 생성)
            val tokenPair = tokenService.refreshAccessToken(refreshToken, request)

            // 3. 새로운 Access Token을 HttpOnly Cookie로 설정
            response.addCookie(createAccessTokenCookie(tokenPair.accessToken))

            // 4. 새로운 Refresh Token을 HttpOnly Cookie로 설정
            response.addCookie(createRefreshTokenCookie(tokenPair.refreshToken))

            // 5. 성공 메시지 반환
            ResponseEntity.ok("토큰 갱신 성공")

        } catch (e: IllegalArgumentException) {
            // Refresh Token 무효/만료/재사용 감지
            // Cookie 삭제 (클라이언트에서 로그인 페이지로 이동하도록)
            response.addCookie(createExpiredCookie("accessToken"))
            response.addCookie(createExpiredCookie("refreshToken"))

            // 재사용 감지 시 403, 일반 오류 시 401
            val status = if (e.message?.contains("재사용") == true) {
                HttpStatus.FORBIDDEN
            } else {
                HttpStatus.UNAUTHORIZED
            }

            ResponseEntity.status(status).body(e.message)
        }
    }

    /**
     * 로그아웃 API (단일 기기)
     *
     * ## 목적
     * - 현재 기기에서만 로그아웃
     * - 다른 기기는 로그인 상태 유지
     *
     * ## 프로세스
     * 1. Refresh Token을 DB에서 삭제
     * 2. Access Token & Refresh Token Cookie 삭제
     *
     * ## 클라이언트 사용법
     * ```javascript
     * const response = await fetch('/api/auth/logout', {
     *   method: 'POST',
     *   credentials: 'include'
     * })
     *
     * if (response.ok) {
     *   window.location.href = '/login'
     * }
     * ```
     *
     * @param refreshToken Refresh Token (Cookie에서 자동 추출)
     * @param response HTTP 응답 (Cookie 삭제용)
     * @return 성공 메시지
     */
    @PostMapping("/logout")
    fun logout(
        @CookieValue(name = "refreshToken", required = false) refreshToken: String?,
        response: HttpServletResponse
    ): ResponseEntity<String> {
        // 1. Refresh Token이 있으면 DB에서 삭제
        if (refreshToken != null) {
            tokenService.revokeToken(refreshToken)
        }

        // 2. Access Token & Refresh Token Cookie 삭제
        response.addCookie(createExpiredCookie("accessToken"))
        response.addCookie(createExpiredCookie("refreshToken"))

        // 3. 성공 메시지 반환
        return ResponseEntity.ok("로그아웃 성공")
    }

    /**
     * 전체 로그아웃 API (모든 기기)
     *
     * ## 목적
     * - 모든 기기에서 로그아웃
     * - 비밀번호 변경 시 보안 조치
     * - 계정 탈취 의심 시 긴급 조치
     *
     * ## 프로세스
     * 1. 사용자의 모든 Refresh Token을 DB에서 삭제
     * 2. 현재 기기의 Cookie 삭제
     *
     * ## 사용 시나리오
     * - 사용자가 "모든 기기에서 로그아웃" 버튼 클릭
     * - 비밀번호 변경 시 자동 호출
     * - 의심스러운 활동 감지 시
     *
     * ## 클라이언트 사용법
     * ```javascript
     * const response = await fetch('/api/auth/logout-all', {
     *   method: 'POST',
     *   credentials: 'include'
     * })
     *
     * if (response.ok) {
     *   alert('모든 기기에서 로그아웃되었습니다')
     *   window.location.href = '/login'
     * }
     * ```
     *
     * @param user 현재 인증된 사용자 (SecurityContext에서 자동 주입)
     * @param response HTTP 응답 (Cookie 삭제용)
     * @return 성공 메시지
     */
    @PostMapping("/logout-all")
    fun logoutAll(
        @AuthenticationPrincipal user: UserPrincipal,
        response: HttpServletResponse
    ): ResponseEntity<String> {
        // 1. 사용자 조회
        val member = memberRepository.findById(user.id).orElseThrow {
            IllegalArgumentException("존재하지 않는 사용자입니다")
        }

        // 2. 사용자의 모든 Refresh Token 삭제
        tokenService.revokeAllTokens(member)

        // 3. 현재 기기의 Cookie 삭제
        response.addCookie(createExpiredCookie("accessToken"))
        response.addCookie(createExpiredCookie("refreshToken"))

        // 4. 성공 메시지 반환
        return ResponseEntity.ok("모든 기기에서 로그아웃되었습니다")
    }

    /**
     * Access Token Cookie 생성
     *
     * ## Cookie 보안 속성
     * - httpOnly = true: JavaScript 접근 불가 (XSS 방지)
     * - secure = false: MVP이므로 HTTP 허용 (프로덕션: true)
     * - path = /: 모든 경로에서 전송
     * - maxAge = 3600: 1시간 (Access Token 수명)
     */
    private fun createAccessTokenCookie(token: String): Cookie {
        return Cookie("accessToken", token).apply {
            isHttpOnly = true
            secure = false       // 프로덕션: true
            path = "/"
            maxAge = 3600        // 1시간
        }
    }

    /**
     * Refresh Token Cookie 생성
     *
     * ## Cookie 보안 속성
     * - httpOnly = true: JavaScript 접근 불가 (XSS 방지)
     * - secure = false: MVP이므로 HTTP 허용 (프로덕션: true)
     * - path = /api/auth: Token Refresh API에서만 전송
     * - maxAge = 1209600: 14일 (Refresh Token 수명)
     */
    private fun createRefreshTokenCookie(token: String): Cookie {
        return Cookie("refreshToken", token).apply {
            isHttpOnly = true
            secure = false       // 프로덕션: true
            path = "/api/auth"  // Refresh API에서만 사용
            maxAge = 1209600     // 14일
        }
    }

    /**
     * Cookie 삭제용 (maxAge=0)
     *
     * ## 로그아웃 시 Cookie 삭제 방법
     * - maxAge = 0: 브라우저에게 Cookie 삭제 지시
     * - value = "": 빈 값으로 설정
     *
     * @param name Cookie 이름
     * @return 삭제용 Cookie
     */
    private fun createExpiredCookie(name: String): Cookie {
        return Cookie(name, "").apply {
            isHttpOnly = true
            secure = false       // 프로덕션: true
            path = if (name == "refreshToken") "/api/auth" else "/"
            maxAge = 0          // 즉시 삭제
        }
    }
}
