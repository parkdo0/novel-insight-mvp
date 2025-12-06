package com.sparta.novel_insight.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * # RefreshToken 엔티티
 *
 * ## 목적
 * - JWT Access Token의 짧은 유효기간 문제를 해결
 * - 사용자가 자주 로그인하지 않아도 되도록 장기 인증 유지
 * - 토큰 무효화(Logout, 탈취 감지 시)를 위한 DB 기반 관리
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. 토큰 저장 전략
 * - **Access Token**: 클라이언트에만 저장 (DB 저장 X)
 *   - 이유: 짧은 수명(15분~1시간), DB 부하 방지
 * - **Refresh Token**: DB에 저장 (이 엔티티)
 *   - 이유: 긴 수명(7~30일), 무효화 필요, 보안 추적
 *
 * ### 2. 보안 메커니즘
 * - **Token Rotation**: Refresh 시마다 새 Refresh Token 발급
 * - **Reuse Detection**: 재사용된 토큰 감지 시 전체 무효화
 * - **IP/User Agent 검증**: 토큰 탈취 감지
 *
 * ### 3. 유효기간 설정
 * - Access Token: 15분 ~ 1시간 (짧을수록 안전)
 * - Refresh Token: 7일 ~ 30일 (편의성과 보안의 균형)
 *
 * ## 연관관계 설계
 * - Member : RefreshToken = 1 : N
 * - 한 사용자가 여러 기기에서 동시 로그인 가능 (모바일, PC, 태블릿 등)
 * - 각 기기마다 별도의 Refresh Token 발급
 *
 * ## 인덱스 전략
 * - token: UNIQUE INDEX (조회 성능 최적화)
 * - member_id: INDEX (사용자별 토큰 조회)
 * - expiresAt: INDEX (만료 토큰 정리 배치 작업용)
 */
@Entity
@Table(
    name = "refresh_token",
    indexes = [
        Index(name = "idx_refresh_token_member", columnList = "member_id"),
        Index(name = "idx_refresh_token_expires", columnList = "expires_at")
    ]
)
class RefreshToken(
    /**
     * 토큰 값
     *
     * - UUID 또는 암호화된 랜덤 문자열 사용
     * - unique=true: 토큰 중복 방지 (보안)
     * - length=500: JWT는 Header.Payload.Signature로 길 수 있음
     *
     * 생성 방식 예시:
     * ```kotlin
     * UUID.randomUUID().toString()  // 36자
     * // 또는 JWT 형식
     * Jwts.builder()...compact()    // ~200자
     * ```
     */
    @Column(nullable = false, unique = true, length = 500)
    var token: String,

    /**
     * 사용자 참조 (Member와의 연관관계)
     *
     * - ManyToOne: 한 사용자가 여러 Refresh Token 보유 가능
     * - FetchType.LAZY: 성능 최적화 (토큰 검증 시 Member 정보 불필요)
     * - cascade 미설정: Member 삭제 시 별도 처리 필요 (비즈니스 로직)
     *
     * 왜 LAZY?
     * - 토큰 검증은 초당 수백~수천 건 발생 가능
     * - Member 정보는 대부분 필요 없음 (member_id만 있으면 됨)
     * - JOIN 쿼리 제거로 DB 부하 감소
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,

    /**
     * 만료 시간
     *
     * - 일반적으로 7~30일 후로 설정
     * - 환경변수로 설정 가능하게 (JWT_REFRESH_DURATION_DAY)
     * - 만료된 토큰은 정기적으로 삭제 (Batch 작업)
     *
     * 설정 예시:
     * ```kotlin
     * expiresAt = LocalDateTime.now().plusDays(14)  // 14일
     * ```
     *
     * 만료 토큰 정리 쿼리:
     * ```sql
     * DELETE FROM refresh_token WHERE expires_at < NOW()
     * ```
     */
    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    /**
     * 생성 시간
     *
     * - updatable=false: 한번 생성되면 변경 불가 (감사 목적)
     * - 토큰 발급 시점 추적
     * - 보안 감사(Audit) 및 로그 분석에 활용
     *
     * 활용 예시:
     * - "언제 로그인했는지" 사용자에게 알림
     * - 의심스러운 로그인 패턴 감지
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 사용 시간
     *
     * - Refresh Token으로 Access Token을 갱신할 때마다 업데이트
     * - Token Rotation 추적용
     * - Null 가능: 아직 한 번도 사용되지 않은 경우
     *
     * 활용 예시:
     * - 30일 미사용 토큰 자동 삭제
     * - "마지막 활동 시간" 사용자에게 표시
     *
     * Reuse Detection:
     * ```kotlin
     * if (token.lastUsedAt != null && token.lastUsedAt < requestTime) {
     *     // 이미 사용된 토큰이 다시 사용됨 → 탈취 의심
     *     deleteAllTokensForMember(member)
     * }
     * ```
     */
    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null,

    /**
     * IP 주소 (보안 강화 - 선택적)
     *
     * - 토큰 발급 시점의 클라이언트 IP 저장
     * - length=45: IPv6 지원 (IPv4: 15자, IPv6: 39자)
     * - Null 가능: IP 추적을 원하지 않는 경우
     *
     * 활용 예시:
     * - 토큰 사용 시 IP가 다르면 경고 또는 재인증 요구
     * - 지리적으로 불가능한 로그인 감지 (한국 → 미국 1분 내)
     *
     * 주의사항:
     * - 모바일 사용자는 IP가 자주 변경됨 (WiFi ↔ 4G/5G)
     * - 너무 엄격하게 검증하면 사용자 경험 저하
     */
    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,

    /**
     * User Agent (보안 강화 - 선택적)
     *
     * - 토큰 발급 시점의 브라우저/앱 정보 저장
     * - 토큰이 다른 기기에서 사용되는지 감지
     *
     * 활용 예시:
     * - Chrome에서 발급한 토큰이 Firefox에서 사용 → 경고
     * - "새로운 기기에서 로그인했습니다" 알림
     *
     * User Agent 예시:
     * ```
     * Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0
     * Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Safari/604.1
     * ```
     */
    @Column(name = "user_agent", length = 255)
    var userAgent: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
) {
    /**
     * 토큰 만료 여부 확인
     *
     * - expiresAt과 현재 시각 비교
     * - true: 만료됨 (사용 불가)
     * - false: 유효함 (사용 가능)
     *
     * 사용 예시:
     * ```kotlin
     * if (refreshToken.isExpired()) {
     *     throw TokenExpiredException("Refresh token has expired")
     * }
     * ```
     */
    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expiresAt)
    }

    /**
     * 토큰 사용 기록 업데이트
     *
     * - Refresh Token으로 Access Token을 갱신할 때 호출
     * - lastUsedAt을 현재 시각으로 업데이트
     *
     * 사용 예시:
     * ```kotlin
     * val newAccessToken = generateAccessToken(member)
     * refreshToken.markAsUsed()
     * refreshTokenRepository.save(refreshToken)
     * ```
     */
    fun markAsUsed() {
        this.lastUsedAt = LocalDateTime.now()
    }

    /**
     * 토큰 발급 후 경과 일수
     *
     * - 보안 정책 적용용 (예: 30일 경과 시 재인증 요구)
     *
     * 사용 예시:
     * ```kotlin
     * if (refreshToken.getDaysSinceCreation() > 30) {
     *     // 30일 이상 된 토큰은 재인증 요구
     *     throw ReAuthenticationRequiredException()
     * }
     * ```
     */
    fun getDaysSinceCreation(): Long {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toDays()
    }
}
