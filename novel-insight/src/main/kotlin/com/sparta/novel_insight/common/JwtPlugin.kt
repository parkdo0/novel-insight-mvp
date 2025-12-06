package com.sparta.novel_insight.common

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * # JwtPlugin - JWT 토큰 생성 및 검증
 *
 * ## 목적
 * - JWT Access Token & Refresh Token 생성
 * - JWT 토큰 서명 검증 (HMAC SHA256)
 * - 토큰 만료 시간 관리
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. 토큰 타입별 설계 원칙
 * #### Access Token
 * - **수명**: 짧게 (15분 ~ 1시간)
 * - **저장 위치**: 클라이언트 메모리 또는 HttpOnly Cookie
 * - **포함 정보**: 사용자 ID, 이메일, 권한 등 (많이 포함 가능)
 * - **이유**: 짧은 수명이므로 탈취되어도 피해 제한적
 *
 * #### Refresh Token
 * - **수명**: 길게 (7~30일)
 * - **저장 위치**: HttpOnly Cookie + DB
 * - **포함 정보**: 사용자 ID만 (최소 정보)
 * - **이유**: 긴 수명이므로 탈취 시 피해 최소화 위해 최소 정보만
 *
 * ### 2. 보안 메커니즘
 * - **HMAC SHA256**: 대칭키 암호화 (secret key 기반)
 * - **토큰 서명**: 위조 방지 (서명이 틀리면 즉시 거부)
 * - **만료 시간**: 자동 만료로 장기 탈취 방지
 *
 * ### 3. JWT Claims 구조
 * ```json
 * {
 *   "sub": "123",              // subject: 사용자 ID
 *   "iss": "novel-insight",    // issuer: 발급자
 *   "iat": 1234567890,         // issued at: 발급 시각
 *   "exp": 1234571490,         // expiration: 만료 시각
 *   "email": "user@example.com", // 커스텀: 이메일 (Access Token만)
 *   "role": "USER"             // 커스텀: 권한 (Access Token만)
 * }
 * ```
 *
 * ## 사용 예시
 * ```kotlin
 * // 로그인 시 토큰 쌍 생성
 * val accessToken = jwtPlugin.generateAccessToken(
 *     subject = member.id.toString(),
 *     email = member.email,
 *     role = "USER"
 * )
 * val refreshToken = jwtPlugin.generateRefreshToken(
 *     subject = member.id.toString()
 * )
 *
 * // 토큰 검증
 * jwtPlugin.validateToken(token).onSuccess { claims ->
 *     val userId = claims.body.subject
 *     val email = claims.body["email"] as String
 * }.onFailure { exception ->
 *     // 토큰 만료 또는 위조
 * }
 * ```
 *
 * ## 설계 패턴
 * - **Strategy Pattern**: Access/Refresh 토큰 생성 전략 분리
 * - **Template Method**: generateToken() 공통 로직 재사용
 */
@Component
class JwtPlugin(
    /**
     * JWT 서명 비밀키
     * - 환경변수에서 주입 (절대 하드코딩 금지)
     * - 최소 32자 이상 권장 (HMAC SHA256)
     * - 노출 시 모든 토큰 무효화 필요
     */
    @Value("\${jwt.secret}") private val secret: String,

    /**
     * JWT 발급자 (Issuer)
     * - 토큰이 어느 서비스에서 발급되었는지 식별
     * - 멀티 서비스 환경에서 유용
     */
    @Value("\${jwt.issuer}") private val issuer: String,

    /**
     * Access Token 유효 시간 (시간 단위)
     * - 실무 권장: 1시간
     * - 짧을수록 안전하지만 사용자 불편
     */
    @Value("\${jwt.access-duration-hour}") private val accessDurationHour: Long,

    /**
     * Refresh Token 유효 시간 (일 단위)
     * - 실무 권장: 7~14일
     * - 너무 길면 보안 위험, 너무 짧으면 사용자 불편
     */
    @Value("\${jwt.refresh-duration-day}") private val refreshDurationDay: Long
) {

    /**
     * Access Token 생성
     *
     * ## 특징
     * - 짧은 유효기간 (1시간)
     * - 사용자 정보 포함 (email, role 등)
     * - 매 요청마다 Authorization 헤더 또는 Cookie로 전송
     *
     * ## 사용 시점
     * - 로그인 성공 시
     * - Refresh Token으로 갱신 시
     *
     * ## 포함 정보
     * - subject: 사용자 ID (PK)
     * - email: 이메일 주소
     * - role: 권한 (USER, ADMIN 등)
     *
     * ## 사용 예시
     * ```kotlin
     * val token = jwtPlugin.generateAccessToken(
     *     subject = member.id.toString(),
     *     email = member.email,
     *     role = "USER"
     * )
     * ```
     *
     * @param subject 사용자 ID (보통 Member.id)
     * @param email 이메일 주소
     * @param role 사용자 권한
     * @return JWT Access Token 문자열
     */
    fun generateAccessToken(subject: String, email: String, role: String): String {
        val claims: Claims = Jwts.claims()
            .setSubject(subject)
            .setIssuer(issuer)
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plus(Duration.ofHours(accessDurationHour))))

        // Access Token만 상세 정보 포함
        claims["email"] = email
        claims["role"] = role

        return buildJwt(claims)
    }

    /**
     * Refresh Token 생성
     *
     * ## 특징
     * - 긴 유효기간 (14일)
     * - 최소 정보만 포함 (subject만)
     * - DB에 저장하여 무효화 가능
     *
     * ## 사용 시점
     * - 로그인 성공 시 (Access Token과 함께)
     * - Refresh Token 갱신 시 (Token Rotation)
     *
     * ## 포함 정보
     * - subject: 사용자 ID만 (보안을 위해 최소 정보)
     *
     * ## 왜 최소 정보만?
     * - Refresh Token은 수명이 길어 탈취 위험 높음
     * - 탈취되어도 추가 정보 노출 방지
     * - DB 조회로 사용자 정보 확인 (성능 trade-off)
     *
     * ## 사용 예시
     * ```kotlin
     * val refreshToken = jwtPlugin.generateRefreshToken(
     *     subject = member.id.toString()
     * )
     *
     * // DB에 저장
     * refreshTokenRepository.save(RefreshToken(
     *     token = refreshToken,
     *     member = member,
     *     expiresAt = LocalDateTime.now().plusDays(14)
     * ))
     * ```
     *
     * @param subject 사용자 ID (보통 Member.id)
     * @return JWT Refresh Token 문자열
     */
    fun generateRefreshToken(subject: String): String {
        val claims: Claims = Jwts.claims()
            .setSubject(subject)
            .setIssuer(issuer)
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plus(Duration.ofDays(refreshDurationDay))))

        // Refresh Token은 최소 정보만 (보안)
        // email, role 등 포함하지 않음

        return buildJwt(claims)
    }

    /**
     * JWT 빌드 공통 로직 (Template Method Pattern)
     *
     * ## 역할
     * - Claims를 받아 JWT 문자열 생성
     * - HMAC SHA256으로 서명
     *
     * ## 서명 과정
     * 1. Claims를 JSON으로 직렬화
     * 2. Base64 인코딩
     * 3. Secret Key로 HMAC SHA256 서명 생성
     * 4. Header.Payload.Signature 형식으로 조립
     *
     * @param claims JWT Claims (subject, issuer, exp 등)
     * @return 서명된 JWT 토큰 문자열
     */
    private fun buildJwt(claims: Claims): String {
        val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

        return Jwts.builder()
            .setClaims(claims)
            .signWith(key) // HMAC SHA256 서명
            .compact()     // Header.Payload.Signature 형식으로 조립
    }

    /**
     * JWT 토큰 검증
     *
     * ## 검증 항목
     * 1. **서명 검증**: Secret Key로 서명이 올바른지 확인
     * 2. **만료 시간 검증**: exp 시각이 현재보다 이전이면 거부
     * 3. **발급자 검증**: iss가 일치하는지 확인 (자동)
     *
     * ## 사용 예시
     * ```kotlin
     * jwtPlugin.validateToken(token).onSuccess { claims ->
     *     val userId = claims.body.subject
     *     val email = claims.body["email"] as? String
     *     val role = claims.body["role"] as? String
     *
     *     // SecurityContext에 인증 정보 설정
     *     val authentication = UsernamePasswordAuthenticationToken(
     *         userId, null, listOf(SimpleGrantedAuthority(role))
     *     )
     *     SecurityContextHolder.getContext().authentication = authentication
     * }.onFailure { exception ->
     *     when (exception) {
     *         is ExpiredJwtException -> {
     *             // 토큰 만료 → Refresh Token으로 갱신 필요
     *         }
     *         is SignatureException -> {
     *             // 서명 불일치 → 위조된 토큰
     *         }
     *         else -> {
     *             // 기타 오류
     *         }
     *     }
     * }
     * ```
     *
     * ## 성능 고려사항
     * - 매 요청마다 실행되므로 최적화 중요
     * - HMAC SHA256 연산 자체는 매우 빠름 (~0.1ms)
     * - DB 조회 없이 토큰만으로 검증 (Stateless)
     *
     * @param jwt JWT 토큰 문자열
     * @return Result<Jws<Claims>> - 성공 시 Claims, 실패 시 Exception
     */
    fun validateToken(jwt: String): Result<Jws<Claims>> {
        return kotlin.runCatching {
            val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

            Jwts.parserBuilder()
                .setSigningKey(key) // Secret Key로 서명 검증
                .build()
                .parseClaimsJws(jwt) // 파싱 + 검증 (실패 시 Exception)
        }
    }
}