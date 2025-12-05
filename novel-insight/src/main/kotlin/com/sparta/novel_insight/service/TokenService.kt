package com.sparta.novel_insight.service

import com.sparta.novel_insight.common.JwtPlugin
import com.sparta.novel_insight.domain.Member
import com.sparta.novel_insight.domain.RefreshToken
import com.sparta.novel_insight.dto.TokenPair
import com.sparta.novel_insight.repository.MemberRepository
import com.sparta.novel_insight.repository.RefreshTokenRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * # TokenService - 토큰 생성, 갱신, 무효화 서비스
 *
 * ## 목적
 * - Access Token & Refresh Token 생명주기 관리
 * - Token Rotation 구현 (보안 강화)
 * - 토큰 무효화 (로그아웃, 비밀번호 변경 등)
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. Token Rotation (토큰 회전)
 * - Refresh Token 사용 시마다 새로운 Refresh Token 발급
 * - 기존 Refresh Token은 즉시 무효화
 * - **이유**: 탈취된 Refresh Token의 재사용 방지
 *
 * ### 2. Reuse Detection (재사용 감지)
 * - 이미 사용된 Refresh Token이 다시 사용되면 의심스러운 활동으로 간주
 * - 해당 사용자의 모든 Refresh Token 무효화 (긴급 조치)
 * - **시나리오**: 공격자가 탈취한 토큰 사용 → 정상 사용자도 갱신 시도 → 재사용 감지
 *
 * ### 3. IP & User Agent 추적
 * - 토큰 발급 시 클라이언트 정보 저장
 * - 토큰 사용 시 정보 검증 (선택적)
 * - **활용**: "새로운 기기에서 로그인" 알림, 의심스러운 활동 감지
 *
 * ### 4. 트랜잭션 관리
 * - 토큰 생성/갱신은 @Transactional로 원자성 보장
 * - DB 저장 실패 시 토큰 발급도 롤백
 *
 * ## 설계 패턴
 * - **Service Layer Pattern**: 비즈니스 로직 집중
 * - **Single Responsibility Principle**: 토큰 관리만 담당
 * - **Dependency Injection**: JwtPlugin, Repository 주입
 *
 * ## 보안 고려사항
 * - Refresh Token은 DB에 저장하여 무효화 가능
 * - Access Token은 DB에 저장하지 않음 (짧은 수명, 성능)
 * - 토큰 재사용 감지 시 즉시 대응
 */
@Service
class TokenService(
    private val jwtPlugin: JwtPlugin,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberRepository: MemberRepository,
    @Value("\${jwt.refresh-duration-day}") private val refreshDurationDay: Long
) {

    /**
     * Access Token + Refresh Token 쌍 생성
     *
     * ## 사용 시점
     * - 로그인 성공 시
     * - 소셜 로그인 성공 시
     *
     * ## 프로세스
     * 1. Access Token 생성 (JwtPlugin)
     * 2. Refresh Token 생성 (JwtPlugin)
     * 3. Refresh Token을 DB에 저장
     * 4. TokenPair 반환
     *
     * ## 보안 고려사항
     * - Refresh Token은 DB에 저장하여 무효화 가능
     * - IP 주소와 User Agent 저장 (선택적 보안 강화)
     *
     * ## 사용 예시
     * ```kotlin
     * @PostMapping("/login")
     * fun login(@RequestBody request: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<*> {
     *     val member = memberRepository.findByEmail(request.email) ?: ...
     *     if (!passwordEncoder.matches(request.password, member.password)) { ... }
     *
     *     val tokenPair = tokenService.generateTokenPair(member, httpRequest)
     *
     *     // HttpOnly Cookie로 반환
     *     response.addCookie(createAccessTokenCookie(tokenPair.accessToken))
     *     response.addCookie(createRefreshTokenCookie(tokenPair.refreshToken))
     *
     *     return ResponseEntity.ok("로그인 성공")
     * }
     * ```
     *
     * @param member 로그인한 사용자
     * @param request HTTP 요청 (IP, User Agent 추출용)
     * @return TokenPair (Access + Refresh)
     */
    @Transactional
    fun generateTokenPair(member: Member, request: HttpServletRequest?): TokenPair {
        // 1. Access Token 생성 (사용자 정보 포함)
        val accessToken = jwtPlugin.generateAccessToken(
            subject = member.id.toString(),
            email = member.email,
            role = "USER"
        )

        // 2. Refresh Token 생성 (최소 정보만)
        val refreshTokenValue = jwtPlugin.generateRefreshToken(
            subject = member.id.toString()
        )

        // 3. Refresh Token을 DB에 저장
        val refreshToken = RefreshToken(
            token = refreshTokenValue,
            member = member,
            expiresAt = LocalDateTime.now().plusDays(refreshDurationDay),
            ipAddress = request?.let { getClientIp(it) },
            userAgent = request?.getHeader("User-Agent")
        )
        refreshTokenRepository.save(refreshToken)

        // 4. 토큰 쌍 반환
        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshTokenValue
        )
    }

    /**
     * Refresh Token으로 Access Token 갱신 (Token Rotation 적용)
     *
     * ## Token Rotation 프로세스
     * 1. Refresh Token 검증 (JWT 서명, 만료 시간)
     * 2. DB에서 Refresh Token 조회
     * 3. **Reuse Detection**: 이미 사용된 토큰인지 확인
     *    - lastUsedAt != null → 이미 사용됨 → 탈취 의심 → 모든 토큰 무효화
     * 4. 사용자 정보 조회
     * 5. 새로운 Access Token 생성
     * 6. **새로운 Refresh Token 생성 (Token Rotation)**
     * 7. 기존 Refresh Token 무효화 (DB에서 삭제)
     * 8. 새로운 Refresh Token DB 저장
     *
     * ## Reuse Detection (재사용 감지)
     * - 시나리오:
     *   1. 공격자가 Refresh Token 탈취
     *   2. 공격자가 토큰으로 갱신 (성공, lastUsedAt 업데이트됨)
     *   3. 정상 사용자가 같은 토큰으로 갱신 시도
     *   4. lastUsedAt != null 감지 → 재사용 감지
     *   5. 해당 사용자의 모든 토큰 무효화 (긴급 조치)
     *   6. 사용자에게 재로그인 요구 + 경고 알림
     *
     * ## 사용 예시
     * ```kotlin
     * @PostMapping("/refresh")
     * fun refresh(@CookieValue("refreshToken") token: String, request: HttpServletRequest): ResponseEntity<*> {
     *     return try {
     *         val tokenPair = tokenService.refreshAccessToken(token, request)
     *
     *         // 새로운 토큰 쌍을 Cookie로 반환
     *         response.addCookie(createAccessTokenCookie(tokenPair.accessToken))
     *         response.addCookie(createRefreshTokenCookie(tokenPair.refreshToken))
     *
     *         ResponseEntity.ok("토큰 갱신 성공")
     *     } catch (e: IllegalArgumentException) {
     *         ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.message)
     *     }
     * }
     * ```
     *
     * @param refreshTokenValue Refresh Token 문자열
     * @param request HTTP 요청 (IP, User Agent 추출용)
     * @return TokenPair (새로운 Access + Refresh)
     * @throws IllegalArgumentException 토큰 무효 또는 재사용 감지
     */
    @Transactional
    fun refreshAccessToken(refreshTokenValue: String, request: HttpServletRequest?): TokenPair {
        // 1. JWT 검증 (서명, 만료 시간)
        val claims = jwtPlugin.validateToken(refreshTokenValue).getOrElse {
            throw IllegalArgumentException("유효하지 않거나 만료된 Refresh Token입니다")
        }

        val userId = claims.body.subject

        // 2. DB에서 Refresh Token 조회
        val refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
            ?: throw IllegalArgumentException("존재하지 않는 Refresh Token입니다")

        // 3. Reuse Detection: 이미 사용된 토큰인지 확인
        if (refreshToken.lastUsedAt != null) {
            // 재사용 감지! → 보안 위협 → 모든 토큰 무효화
            refreshTokenRepository.deleteAllByMember(refreshToken.member)
            throw IllegalArgumentException(
                "재사용된 Refresh Token이 감지되었습니다. " +
                "보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해주세요."
            )
        }

        // 4. 만료 확인
        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken)
            throw IllegalArgumentException("만료된 Refresh Token입니다")
        }

        // 5. 사용자 조회
        val member = memberRepository.findById(userId.toLong()).orElseThrow {
            IllegalArgumentException("존재하지 않는 사용자입니다")
        }

        // 6. Token Rotation: 기존 토큰 무효화 (사용 기록 남기지 않고 즉시 삭제)
        refreshTokenRepository.delete(refreshToken)

        // 7. 새로운 토큰 쌍 생성
        return generateTokenPair(member, request)
    }

    /**
     * Refresh Token 검증 및 조회
     *
     * ## 사용 시점
     * - 토큰이 유효한지만 확인 (갱신은 하지 않음)
     * - 관리자 페이지에서 사용자의 활성 토큰 조회
     *
     * @param refreshTokenValue Refresh Token 문자열
     * @return RefreshToken 엔티티
     * @throws IllegalArgumentException 토큰 무효
     */
    @Transactional(readOnly = true)
    fun validateAndGetRefreshToken(refreshTokenValue: String): RefreshToken {
        // JWT 검증
        jwtPlugin.validateToken(refreshTokenValue).getOrElse {
            throw IllegalArgumentException("유효하지 않거나 만료된 Refresh Token입니다")
        }

        // DB 조회
        val refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
            ?: throw IllegalArgumentException("존재하지 않는 Refresh Token입니다")

        // 만료 확인
        if (refreshToken.isExpired()) {
            throw IllegalArgumentException("만료된 Refresh Token입니다")
        }

        return refreshToken
    }

    /**
     * 단일 Refresh Token 무효화 (로그아웃)
     *
     * ## 사용 시점
     * - 사용자가 "로그아웃" 버튼 클릭
     * - 현재 기기에서만 로그아웃 (다른 기기는 유지)
     *
     * ## 사용 예시
     * ```kotlin
     * @PostMapping("/logout")
     * fun logout(@CookieValue("refreshToken") token: String): ResponseEntity<String> {
     *     tokenService.revokeToken(token)
     *
     *     // Cookie 삭제
     *     response.addCookie(Cookie("accessToken", "").apply { maxAge = 0 })
     *     response.addCookie(Cookie("refreshToken", "").apply { maxAge = 0 })
     *
     *     return ResponseEntity.ok("로그아웃 성공")
     * }
     * ```
     *
     * @param refreshTokenValue Refresh Token 문자열
     */
    @Transactional
    fun revokeToken(refreshTokenValue: String) {
        refreshTokenRepository.deleteByToken(refreshTokenValue)
    }

    /**
     * 사용자의 모든 Refresh Token 무효화 (모든 기기 로그아웃)
     *
     * ## 사용 시점
     * 1. 사용자가 "모든 기기에서 로그아웃" 클릭
     * 2. 비밀번호 변경 시 보안 조치
     * 3. 계정 탈취 의심 시 긴급 조치
     * 4. 회원 탈퇴 시
     *
     * ## 사용 예시
     * ```kotlin
     * @PutMapping("/password")
     * fun changePassword(@RequestBody request: ChangePasswordRequest, @AuthenticationPrincipal userId: String) {
     *     val member = memberRepository.findById(userId.toLong()).orElseThrow()
     *     member.password = passwordEncoder.encode(request.newPassword)
     *     memberRepository.save(member)
     *
     *     // 보안: 모든 기기에서 로그아웃
     *     tokenService.revokeAllTokens(member)
     *
     *     return ResponseEntity.ok("비밀번호가 변경되었습니다. 다시 로그인해주세요.")
     * }
     * ```
     *
     * @param member 사용자 엔티티
     */
    @Transactional
    fun revokeAllTokens(member: Member) {
        refreshTokenRepository.deleteAllByMember(member)
    }

    /**
     * 클라이언트 IP 주소 추출
     *
     * ## 고려사항
     * - Proxy, Load Balancer 뒤에서는 X-Forwarded-For 헤더 확인 필요
     * - 우선순위: X-Forwarded-For > X-Real-IP > RemoteAddr
     *
     * ## IP 주소 활용
     * - 보안: 토큰 발급 IP와 사용 IP 비교
     * - 분석: 지역별 접속 통계
     * - 감사: 로그인 기록 추적
     *
     * @param request HTTP 요청
     * @return 클라이언트 IP 주소
     */
    private fun getClientIp(request: HttpServletRequest): String {
        // Proxy를 통한 요청인 경우
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (xForwardedFor != null && xForwardedFor.isNotEmpty()) {
            // X-Forwarded-For: client, proxy1, proxy2 형식
            return xForwardedFor.split(",")[0].trim()
        }

        // Nginx 등의 Reverse Proxy 사용 시
        val xRealIp = request.getHeader("X-Real-IP")
        if (xRealIp != null && xRealIp.isNotEmpty()) {
            return xRealIp
        }

        // 직접 연결인 경우
        return request.remoteAddr
    }
}
