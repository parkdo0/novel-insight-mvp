package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Member
import com.sparta.novel_insight.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * # RefreshTokenRepository
 *
 * ## 목적
 * - Refresh Token의 CRUD 및 보안 관련 쿼리 제공
 * - 토큰 검증, 무효화, 만료 처리 등 실무 필수 기능 지원
 *
 * ## Spring Data JPA의 Query Method 네이밍 규칙
 * - findBy{Property}: 조회 (SELECT)
 * - deleteBy{Property}: 삭제 (DELETE)
 * - countBy{Property}: 개수 세기 (COUNT)
 * - existsBy{Property}: 존재 여부 (EXISTS)
 *
 * ## 실무 권장 쿼리 패턴
 *
 * ### 1. 토큰 조회 (검증용)
 * - `findByToken()`: 가장 빈번한 쿼리 (매 요청마다 실행 가능)
 * - 인덱스 필수: token 컬럼에 UNIQUE INDEX
 *
 * ### 2. 사용자별 토큰 관리
 * - `findAllByMember()`: 사용자의 모든 활성 토큰 조회
 * - 활용: "로그인된 기기 목록" 표시
 *
 * ### 3. 토큰 무효화 (로그아웃)
 * - `deleteByToken()`: 단일 기기 로그아웃
 * - `deleteAllByMember()`: 모든 기기에서 로그아웃
 * - `deleteByExpiresAtBefore()`: 만료된 토큰 정리 (배치)
 *
 * ### 4. 보안 검증
 * - IP 주소 & User Agent 검증
 * - Reuse Detection (재사용 감지)
 *
 * ## 성능 최적화
 * - @Query 어노테이션: 복잡한 쿼리는 명시적 JPQL 사용
 * - @Modifying: DELETE/UPDATE 쿼리는 필수
 * - Batch Delete: 만료 토큰 일괄 삭제로 성능 향상
 */
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    /**
     * 토큰 값으로 Refresh Token 조회
     *
     * ## 사용 빈도: 매우 높음 (매 요청마다 실행 가능)
     * ## 성능 최적화
     * - token 컬럼에 UNIQUE INDEX 필수
     * - 인덱스가 없으면 Full Table Scan → 성능 저하
     *
     * ## 사용 예시
     * ```kotlin
     * val refreshToken = refreshTokenRepository.findByToken(tokenValue)
     *     ?: throw InvalidTokenException("Invalid refresh token")
     *
     * if (refreshToken.isExpired()) {
     *     throw TokenExpiredException("Refresh token has expired")
     * }
     * ```
     *
     * ## JPA Query Method 생성 규칙
     * - findBy + Property → SELECT * FROM refresh_token WHERE token = ?
     *
     * @param token Refresh Token 값
     * @return RefreshToken 엔티티 (없으면 null)
     */
    fun findByToken(token: String): RefreshToken?

    /**
     * 사용자의 모든 Refresh Token 조회
     *
     * ## 활용 시나리오
     * 1. "로그인된 기기 목록" 표시
     *    - 예: "Chrome (Windows) - 마지막 활동: 2시간 전"
     * 2. 의심스러운 활동 감지
     *    - 예: 한 사용자가 10개 이상의 토큰 보유 → 계정 공유 의심
     * 3. 전체 로그아웃 (모든 기기)
     *
     * ## 사용 예시
     * ```kotlin
     * val allTokens = refreshTokenRepository.findAllByMember(member)
     * println("현재 로그인된 기기: ${allTokens.size}개")
     * ```
     *
     * ## 성능 고려사항
     * - member_id 컬럼에 INDEX 필수
     * - FetchType.LAZY 설정으로 Member 정보는 조회 안 함
     *
     * @param member Member 엔티티
     * @return 해당 사용자의 모든 Refresh Token 리스트
     */
    fun findAllByMember(member: Member): List<RefreshToken>

    /**
     * 특정 토큰 삭제 (단일 기기 로그아웃)
     *
     * ## 활용 시나리오
     * - 사용자가 "로그아웃" 버튼 클릭
     * - 현재 기기에서만 로그아웃 (다른 기기는 유지)
     *
     * ## 사용 예시
     * ```kotlin
     * // Controller
     * @PostMapping("/logout")
     * fun logout(@CookieValue("refreshToken") token: String) {
     *     refreshTokenRepository.deleteByToken(token)
     *     // 쿠키도 삭제
     *     response.addCookie(Cookie("refreshToken", "").apply {
     *         maxAge = 0
     *         isHttpOnly = true
     *     })
     * }
     * ```
     *
     * ## 트랜잭션
     * - @Transactional이 자동으로 적용됨 (Repository 인터페이스)
     *
     * @param token 삭제할 Refresh Token 값
     */
    fun deleteByToken(token: String)

    /**
     * 사용자의 모든 Refresh Token 삭제 (모든 기기 로그아웃)
     *
     * ## 활용 시나리오
     * 1. 사용자가 "모든 기기에서 로그아웃" 클릭
     * 2. 비밀번호 변경 시 보안 조치
     * 3. 계정 탈취 의심 시 긴급 조치
     * 4. 회원 탈퇴 시 정리
     *
     * ## 사용 예시
     * ```kotlin
     * // 비밀번호 변경 시
     * @Transactional
     * fun changePassword(memberId: Long, newPassword: String) {
     *     val member = memberRepository.findById(memberId).orElseThrow()
     *     member.password = passwordEncoder.encode(newPassword)
     *     memberRepository.save(member)
     *
     *     // 보안: 모든 기기에서 로그아웃
     *     refreshTokenRepository.deleteAllByMember(member)
     * }
     * ```
     *
     * ## 성능
     * - member_id로 삭제하므로 INDEX 필수
     * - 대량 삭제 시 Batch Delete 최적화
     *
     * @param member Member 엔티티
     */
    fun deleteAllByMember(member: Member)

    /**
     * 만료된 Refresh Token 일괄 삭제 (배치 작업용)
     *
     * ## 목적
     * - DB 용량 절약 (만료된 토큰은 더이상 사용되지 않음)
     * - 테이블 크기 감소 → 쿼리 성능 향상
     *
     * ## 실행 주기
     * - 매일 새벽 3시 (사용자 적은 시간대)
     * - Spring Scheduler 사용
     *
     * ## 사용 예시
     * ```kotlin
     * @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
     * @Transactional
     * fun cleanupExpiredTokens() {
     *     val now = LocalDateTime.now()
     *     val deletedCount = refreshTokenRepository.deleteByExpiresAtBefore(now)
     *     logger.info("만료된 Refresh Token $deletedCount 개 삭제 완료")
     * }
     * ```
     *
     * ## 성능 최적화
     * - expires_at 컬럼에 INDEX 필수
     * - @Modifying(clearAutomatically = true): 영속성 컨텍스트 자동 정리
     *
     * @param dateTime 기준 시각 (이 시각 이전에 만료된 토큰 삭제)
     * @return 삭제된 토큰 개수
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :dateTime")
    fun deleteByExpiresAtBefore(@Param("dateTime") dateTime: LocalDateTime): Int

    /**
     * 사용자의 활성 토큰 개수 조회
     *
     * ## 활용 시나리오
     * 1. 동시 로그인 기기 제한
     *    - 예: 최대 5개 기기까지만 허용
     * 2. 계정 공유 감지
     *    - 예: 10개 이상 토큰 보유 시 경고
     *
     * ## 사용 예시
     * ```kotlin
     * val activeTokenCount = refreshTokenRepository.countByMemberAndExpiresAtAfter(
     *     member = member,
     *     dateTime = LocalDateTime.now()
     * )
     *
     * if (activeTokenCount >= 5) {
     *     throw TooManyDevicesException("최대 5개 기기까지만 로그인 가능합니다")
     * }
     * ```
     *
     * @param member Member 엔티티
     * @param dateTime 기준 시각 (이 시각 이후 만료되는 토큰만 카운트)
     * @return 활성 토큰 개수
     */
    fun countByMemberAndExpiresAtAfter(member: Member, dateTime: LocalDateTime): Long

    /**
     * 토큰 존재 여부 확인 (검증용)
     *
     * ## 활용 시나리오
     * - 토큰이 유효한지 빠르게 확인 (엔티티 조회 불필요)
     * - COUNT(*) 대신 EXISTS 사용 → 더 빠름
     *
     * ## 사용 예시
     * ```kotlin
     * if (!refreshTokenRepository.existsByToken(tokenValue)) {
     *     throw InvalidTokenException("존재하지 않는 토큰입니다")
     * }
     * ```
     *
     * ## 성능
     * - EXISTS는 첫 번째 일치하는 행을 찾으면 즉시 리턴
     * - COUNT는 모든 행을 세야 함
     *
     * @param token Refresh Token 값
     * @return 존재하면 true, 없으면 false
     */
    fun existsByToken(token: String): Boolean

    /**
     * IP 주소와 User Agent로 토큰 조회 (보안 검증용)
     *
     * ## 활용 시나리오
     * - 토큰 사용 시 발급 환경과 동일한지 검증
     * - IP/User Agent가 다르면 의심스러운 활동으로 간주
     *
     * ## 사용 예시
     * ```kotlin
     * val token = refreshTokenRepository.findByToken(tokenValue)
     *     ?: throw InvalidTokenException()
     *
     * // 보안 검증: IP와 User Agent가 일치하는지 확인
     * if (token.ipAddress != null && token.ipAddress != currentIp) {
     *     logger.warn("IP 불일치: 발급=${token.ipAddress}, 현재=$currentIp")
     *     // 선택적으로 토큰 무효화 또는 재인증 요구
     * }
     * ```
     *
     * ## 주의사항
     * - 모바일 사용자는 IP가 자주 변경됨 (WiFi ↔ 4G/5G)
     * - 너무 엄격하게 적용하면 사용자 불편
     * - 경고 로그만 남기고 허용하는 것도 방법
     *
     * @param member Member 엔티티
     * @param ipAddress IP 주소
     * @param userAgent User Agent 문자열
     * @return 일치하는 토큰 리스트
     */
    fun findAllByMemberAndIpAddressAndUserAgent(
        member: Member,
        ipAddress: String?,
        userAgent: String?
    ): List<RefreshToken>

    /**
     * 오래된 미사용 토큰 삭제 (보안 강화)
     *
     * ## 목적
     * - 30일 이상 사용되지 않은 토큰 자동 삭제
     * - 방치된 토큰의 보안 위험 감소
     *
     * ## 실행 주기
     * - 매주 일요일 새벽 4시
     *
     * ## 사용 예시
     * ```kotlin
     * @Scheduled(cron = "0 0 4 * * SUN")  // 매주 일요일 새벽 4시
     * @Transactional
     * fun cleanupUnusedTokens() {
     *     val threshold = LocalDateTime.now().minusDays(30)
     *     val deletedCount = refreshTokenRepository.deleteByLastUsedAtBeforeOrLastUsedAtIsNull(threshold)
     *     logger.info("30일 미사용 토큰 $deletedCount 개 삭제 완료")
     * }
     * ```
     *
     * ## 로직
     * - lastUsedAt이 null → 한번도 사용 안 함 (발급 후 방치)
     * - lastUsedAt < threshold → 오래 전에 사용
     *
     * @param dateTime 기준 시각 (이 시각 이전 사용 토큰 삭제)
     * @return 삭제된 토큰 개수
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        DELETE FROM RefreshToken rt
        WHERE rt.lastUsedAt < :dateTime
        OR (rt.lastUsedAt IS NULL AND rt.createdAt < :dateTime)
    """)
    fun deleteUnusedTokens(@Param("dateTime") dateTime: LocalDateTime): Int
}
