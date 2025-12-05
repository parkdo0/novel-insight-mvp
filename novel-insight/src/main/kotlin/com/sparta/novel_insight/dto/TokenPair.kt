package com.sparta.novel_insight.dto

/**
 * # TokenPair - 토큰 쌍 DTO
 *
 * ## 목적
 * - Access Token과 Refresh Token을 하나의 객체로 묶어서 반환
 * - 로그인 시 두 토큰을 함께 발급
 *
 * ## 사용 시나리오
 * 1. **로그인**: 이메일/비밀번호 인증 성공 시
 * 2. **Token Rotation**: Refresh Token으로 새 토큰 쌍 발급
 *
 * ## 실무 권장사항
 * - Access Token: Response Body 또는 HttpOnly Cookie
 * - Refresh Token: HttpOnly Cookie (XSS 방지)
 *
 * ## 사용 예시
 * ```kotlin
 * // TokenService에서 생성
 * val tokenPair = tokenService.generateTokenPair(member)
 *
 * // Controller에서 Cookie로 반환
 * response.addCookie(Cookie("accessToken", tokenPair.accessToken).apply {
 *     httpOnly = true
 *     secure = true
 *     path = "/"
 *     maxAge = 3600 // 1시간
 * })
 *
 * response.addCookie(Cookie("refreshToken", tokenPair.refreshToken).apply {
 *     httpOnly = true
 *     secure = true
 *     path = "/api/auth/refresh"  // Refresh API에서만 사용
 *     maxAge = 1209600 // 14일
 * })
 * ```
 */
data class TokenPair(
    /**
     * Access Token
     * - JWT 형식
     * - 짧은 수명 (1시간)
     * - 사용자 정보 포함 (email, role)
     * - 모든 API 요청에 포함
     */
    val accessToken: String,

    /**
     * Refresh Token
     * - JWT 형식
     * - 긴 수명 (14일)
     * - 최소 정보만 포함 (userId)
     * - Token Refresh API에서만 사용
     */
    val refreshToken: String
)
