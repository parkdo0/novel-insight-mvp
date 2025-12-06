package com.sparta.novel_insight.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * # SecurityConfig - Spring Security 보안 설정
 *
 * ## 실무 권장사항 적용 (Production Best Practices)
 *
 * ### 1. CSRF (Cross-Site Request Forgery) 보호
 *
 * #### 문제: Cookie 기반 인증의 CSRF 취약점
 * - HttpOnly Cookie는 XSS는 방어하지만 CSRF는 방어 불가
 * - 공격 시나리오:
 *   ```html
 *   <!-- 공격자의 사이트 (evil.com) -->
 *   <form action="http://novel-insight.com/api/auth/logout-all" method="POST">
 *     <button>무료 포인트 받기!</button>
 *   </form>
 *   ```
 *   - 사용자가 버튼 클릭 → 브라우저가 자동으로 Cookie 전송
 *   - 사용자도 모르게 모든 기기에서 로그아웃됨
 *
 * #### 해결: CSRF 토큰
 * - 모든 POST/PUT/DELETE 요청에 CSRF 토큰 필요
 * - CSRF 토큰은 Cookie에 저장되고, 요청 시 Header에 포함
 * - 공격자는 CSRF 토큰을 알 수 없으므로 공격 실패
 *
 * ### 2. CORS (Cross-Origin Resource Sharing)
 * - 프론트엔드(localhost:5173)와 백엔드(localhost:8080) 간 통신 허용
 * - allowCredentials = true: Cookie 전송 허용
 *
 * ### 3. JWT Authentication Filter
 * - UsernamePasswordAuthenticationFilter 전에 실행
 * - Cookie 또는 Header에서 JWT 추출 및 검증
 *
 * ## CSRF 활성화 방법
 *
 * ### MVP 단계 (현재)
 * - CSRF 비활성화 (개발 편의성)
 * - 이유: 프론트엔드 통합 전, Postman 테스트 편의
 * - **주의**: 프로덕션 배포 전 반드시 활성화 필요!
 *
 * ### 프로덕션 단계 (배포 시)
 * - CSRF 활성화 (아래 주석 참고)
 * - 프론트엔드에서 CSRF 토큰 처리 필요
 *
 * ## CSRF 활성화 코드 (주석 처리됨)
 * ```kotlin
 * .csrf { csrf ->
 *     csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
 *         .ignoringRequestMatchers(
 *             "/signup",           // 회원가입: CSRF 제외 (신규 사용자)
 *             "/login"             // 로그인: CSRF 제외 (신규 세션)
 *         )
 * }
 * ```
 *
 * ## 프론트엔드 CSRF 토큰 처리
 * ```javascript
 * // 1. CSRF 토큰 읽기 (Cookie)
 * function getCsrfToken() {
 *   const cookies = document.cookie.split(';')
 *   const csrfCookie = cookies.find(c => c.trim().startsWith('XSRF-TOKEN='))
 *   return csrfCookie ? csrfCookie.split('=')[1] : null
 * }
 *
 * // 2. 모든 POST/PUT/DELETE 요청에 CSRF 토큰 포함
 * fetch('/api/auth/logout', {
 *   method: 'POST',
 *   credentials: 'include',  // Cookie 전송
 *   headers: {
 *     'X-XSRF-TOKEN': getCsrfToken()  // CSRF 토큰
 *   }
 * })
 * ```
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    /**
     * 비밀번호 암호화 방식
     *
     * ## BCrypt (Blowfish Crypt)
     * - 암호화 알고리즘: Blowfish
     * - Salt 자동 생성: 같은 비밀번호도 다른 해시값
     * - Adaptive: rounds를 높여 미래의 연산 능력 증가에 대응
     *
     * ## 사용 예시
     * ```kotlin
     * val encodedPassword = passwordEncoder.encode("password123")
     * // $2a$10$XYZ... (salt + hash)
     *
     * val isMatch = passwordEncoder.matches("password123", encodedPassword)
     * // true
     * ```
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    /**
     * Spring Security 필터 체인 설정
     *
     * ## 필터 실행 순서
     * 1. CorsFilter: CORS 정책 적용
     * 2. CsrfFilter: CSRF 토큰 검증 (현재 비활성화)
     * 3. JwtAuthenticationFilter: JWT 토큰 검증 ← 커스텀 필터
     * 4. UsernamePasswordAuthenticationFilter: 기본 인증 필터
     * 5. FilterSecurityInterceptor: 권한 검증
     *
     * ## 인증 vs 인가
     * - **인증 (Authentication)**: "당신이 누구인가?" (JwtAuthenticationFilter)
     * - **인가 (Authorization)**: "당신이 이것을 할 수 있는가?" (authorizeHttpRequests)
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            // 1. CORS 설정 (Cross-Origin Resource Sharing)
            .cors { it.configurationSource(corsConfigurationSource()) }

            // 2. CSRF 설정 (Cross-Site Request Forgery)
            // MVP 단계: 비활성화 (개발 편의성)
            // 프로덕션 단계: 아래 주석 해제하여 활성화
            .csrf { it.disable() }

            /* ===================================
             * [프로덕션 단계] CSRF 활성화 코드
             * ===================================
             * .csrf { csrf ->
             *     // CookieCsrfTokenRepository: CSRF 토큰을 Cookie에 저장
             *     // withHttpOnlyFalse(): JavaScript로 Cookie 읽기 가능 (필수)
             *     csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
             *         // CSRF 체크 제외 엔드포인트
             *         .ignoringRequestMatchers(
             *             "/signup",   // 회원가입: 신규 사용자, CSRF 토큰 없음
             *             "/login"     // 로그인: 신규 세션, CSRF 토큰 없음
             *         )
             * }
             *
             * ## CSRF 활성화 시 주의사항
             * 1. 프론트엔드에서 CSRF 토큰 처리 필요
             * 2. 모든 POST/PUT/DELETE 요청에 X-XSRF-TOKEN 헤더 포함
             * 3. GET 요청은 CSRF 체크 안 함
             */

            // 3. 인가 설정 (Authorization)
            .authorizeHttpRequests { auth ->
                // OPTIONS 요청 허용 (CORS Preflight)
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // GET 요청 허용 (조회 API)
                auth.requestMatchers(HttpMethod.GET, "/novels/**", "/episodes/**", "/reviews/**").permitAll()

                // 인증 불필요 엔드포인트
                auth.requestMatchers(
                    "/signup",              // 회원가입
                    "/login",               // 로그인
                    "/login/vulnerable",    // (데모용) 취약한 로그인
                    "/api/**"               // API 엔드포인트 (임시, 나중에 세분화)
                ).permitAll()

                // 나머지는 인증 필요
                auth.anyRequest().authenticated()
            }

            // 4. JWT 인증 필터 추가
            // UsernamePasswordAuthenticationFilter 전에 실행
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

            .build()
    }

    /**
     * CORS 설정
     *
     * ## 목적
     * - 프론트엔드(localhost:5173)와 백엔드(localhost:8080) 간 통신 허용
     * - Cookie 기반 인증 지원
     *
     * ## 주요 설정
     * - **allowedOrigins**: 허용할 프론트엔드 주소
     * - **allowedMethods**: 허용할 HTTP 메서드
     * - **allowedHeaders**: 허용할 HTTP 헤더 (모든 헤더 허용)
     * - **allowCredentials**: Cookie 전송 허용 (매우 중요!)
     * - **exposedHeaders**: 프론트엔드가 접근 가능한 응답 헤더
     *
     * ## allowCredentials = true의 중요성
     * - false인 경우: Cookie가 전송되지 않음 → 인증 불가
     * - true인 경우: 브라우저가 자동으로 Cookie 전송
     *
     * ## 프로덕션 배포 시 변경사항
     * ```kotlin
     * configuration.allowedOrigins = listOf(
     *     "https://novel-insight.com",  // 실제 도메인
     *     "https://www.novel-insight.com"
     * )
     * ```
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // 허용할 프론트엔드 주소
        configuration.allowedOrigins = listOf(
            "http://localhost",
            "http://localhost:5173",  // Vite 기본 포트
            "http://127.0.0.1"
        )

        // 허용할 HTTP 메서드
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")

        // 허용할 HTTP 헤더 (모든 헤더)
        configuration.allowedHeaders = listOf("*")

        // Cookie 전송 허용 (매우 중요!)
        configuration.allowCredentials = true

        // 프론트엔드가 접근 가능한 응답 헤더
        configuration.exposedHeaders = listOf("Authorization")

        // 모든 경로에 CORS 설정 적용
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
