package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.DeviceType
import com.sparta.novel_insight.domain.DropReason
import com.sparta.novel_insight.domain.EpisodeReadLog
import com.sparta.novel_insight.repository.EpisodeReadLogRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * # UserBehaviorLogService (Phase 6)
 *
 * 사용자 행동 로그 관리 서비스입니다.
 *
 * ## 핵심 책임
 *
 * 1. **읽기 세션 관리**: 사용자가 에피소드를 읽기 시작/종료할 때 로그 생성/업데이트
 * 2. **스크롤 깊이 추적**: 실시간으로 사용자의 스크롤 진행도를 업데이트
 * 3. **이탈 감지**: 사용자가 읽기를 중단한 지점과 이유를 기록
 * 4. **실시간 집계**: Redis를 통한 실시간 지표 (현재 읽는 사용자 수, 조회수 등)
 *
 * ## WHY 비동기 처리?
 *
 * ### 문제: 사용자 경험 저하
 * ```kotlin
 * // ❌ 동기 처리 (나쁜 예)
 * @GetMapping("/episode/{id}")
 * fun getEpisode(@PathVariable id: Long): EpisodeDto {
 *     val episode = episodeService.getEpisode(id)
 *     logService.createReadLog(episode.id) // ← DB 쓰기 대기
 *     return episode
 * }
 * // 사용자 응답 시간: 150ms (에피소드 조회) + 50ms (로그 저장) = 200ms
 * ```
 *
 * ### 해결: 비동기 + Redis
 * ```kotlin
 * // ✅ 비동기 처리 (좋은 예)
 * @GetMapping("/episode/{id}")
 * fun getEpisode(@PathVariable id: Long): EpisodeDto {
 *     val episode = episodeService.getEpisode(id)
 *     logService.createReadLogAsync(episode.id) // ← 백그라운드 처리
 *     return episode
 * }
 * // 사용자 응답 시간: 150ms (에피소드 조회)
 * // 로그 저장은 백그라운드에서 처리
 * ```
 *
 * ## 실시간 지표 처리 전략
 *
 * ### 1차: Redis (초 단위)
 * - 현재 읽는 사용자 수
 * - 실시간 조회수
 *
 * ### 2차: MySQL (시간 단위 배치)
 * - DropOffPoint 집계
 * - 완독률 계산
 *
 * ### 3차: MySQL Cache (일 단위 배치)
 * - AuthorDashboardCache 갱신
 * - 트렌드 분석
 *
 * @see EpisodeReadLog 원본 로그 Entity
 * @see DropOffAnalysisService 집계 서비스
 */
@Service
class UserBehaviorLogService(
    private val episodeReadLogRepository: EpisodeReadLogRepository,
    private val redisTemplate: RedisTemplate<String, String>
) {

    companion object {
        /**
         * Redis Key 패턴
         */
        private const val REDIS_KEY_CURRENT_READERS = "episode:readers:current:{episodeId}"
        private const val REDIS_KEY_VIEW_COUNT = "episode:views:count:{episodeId}"
        private const val REDIS_KEY_SESSION_LOG = "episode:session:{sessionId}"

        /**
         * 세션 타임아웃 (30분)
         *
         * WHY 30분?
         * - 사용자가 읽다가 잠시 멈춤 (화장실, 전화 등)
         * - 너무 짧으면 (5분) → 세션이 자주 끊김
         * - 너무 길면 (1시간) → 메모리 낭비
         */
        private const val SESSION_TIMEOUT_MINUTES = 30L

        /**
         * 완독 기준 (90%)
         *
         * WHY 90%?
         * - 100%는 너무 엄격 (마지막 줄까지 안 읽을 수 있음)
         * - 90%면 대부분 읽었다고 볼 수 있음
         */
        private const val COMPLETION_THRESHOLD = "90.00"
    }

    /**
     * 읽기 세션 시작
     *
     * 사용자가 에피소드를 열었을 때 호출됩니다.
     *
     * ## 처리 흐름
     *
     * 1. Redis에 현재 읽는 사용자로 등록 (실시간 지표용)
     * 2. MySQL에 EpisodeReadLog 생성 (영구 보관용)
     * 3. Redis에 세션 정보 저장 (로그 ID 매핑)
     *
     * ## 실무 활용 예시
     *
     * ```kotlin
     * // EpisodeController.kt
     * @GetMapping("/episodes/{episodeId}")
     * fun getEpisode(
     *     @PathVariable episodeId: Long,
     *     @RequestHeader("User-Agent") userAgent: String,
     *     @RequestHeader("Referer") referer: String?,
     *     @SessionAttribute("userId") userId: Long?,
     *     @SessionAttribute("sessionId") sessionId: String
     * ): EpisodeDto {
     *     val episode = episodeService.getEpisode(episodeId)
     *
     *     // 비동기 로그 생성
     *     userBehaviorLogService.startReadingSession(
     *         userId = userId,
     *         episodeId = episodeId,
     *         novelId = episode.novelId,
     *         sessionId = sessionId,
     *         deviceType = DeviceType.fromUserAgent(userAgent),
     *         referrer = referer
     *     )
     *
     *     return episode
     * }
     * ```
     *
     * @param userId 사용자 ID (비회원인 경우 null)
     * @param episodeId 에피소드 ID
     * @param novelId 소설 ID
     * @param sessionId 세션 ID (쿠키 기반)
     * @param deviceType 디바이스 타입 (MOBILE, TABLET, DESKTOP)
     * @param referrer 유입 경로 (검색엔진, SNS 등)
     * @return 생성된 로그 ID
     */
    @Async
    @Transactional
    fun startReadingSession(
        userId: Long?,
        episodeId: Long,
        novelId: Long,
        sessionId: String,
        deviceType: DeviceType,
        referrer: String?
    ): Long {
        // 1. MySQL에 로그 생성
        val readLog = EpisodeReadLog(
            userId = userId,
            episodeId = episodeId,
            novelId = novelId,
            startedAt = LocalDateTime.now(),
            sessionId = sessionId,
            deviceType = deviceType,
            referrer = referrer
        )

        val savedLog = episodeReadLogRepository.save(readLog)

        // 2. Redis에 현재 읽는 사용자 등록
        val currentReadersKey = REDIS_KEY_CURRENT_READERS.replace("{episodeId}", episodeId.toString())
        redisTemplate.opsForSet().add(currentReadersKey, sessionId)
        redisTemplate.expire(currentReadersKey, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES)

        // 3. Redis에 세션 → 로그 ID 매핑 저장
        val sessionLogKey = REDIS_KEY_SESSION_LOG.replace("{sessionId}", sessionId)
        redisTemplate.opsForValue().set(
            sessionLogKey,
            savedLog.id.toString(),
            SESSION_TIMEOUT_MINUTES,
            TimeUnit.MINUTES
        )

        // 4. Redis 조회수 증가
        val viewCountKey = REDIS_KEY_VIEW_COUNT.replace("{episodeId}", episodeId.toString())
        redisTemplate.opsForValue().increment(viewCountKey)

        return savedLog.id!!
    }

    /**
     * 스크롤 깊이 업데이트
     *
     * 프론트엔드에서 주기적으로 (5초마다) 호출됩니다.
     *
     * ## 프론트엔드 연동 예시
     *
     * ```javascript
     * // episode-reader.js
     * let maxScrollDepth = 0;
     *
     * // 스크롤 이벤트 리스너
     * window.addEventListener('scroll', () => {
     *     const scrollPercentage = (window.scrollY / document.body.scrollHeight) * 100;
     *     maxScrollDepth = Math.max(maxScrollDepth, scrollPercentage);
     * });
     *
     * // 5초마다 서버에 전송
     * setInterval(() => {
     *     fetch('/api/behavior/scroll', {
     *         method: 'POST',
     *         body: JSON.stringify({
     *             sessionId: getSessionId(),
     *             scrollDepth: maxScrollDepth
     *         })
     *     });
     * }, 5000);
     * ```
     *
     * ## WHY 5초마다?
     *
     * - 너무 자주 (1초) → 서버 부하 증가
     * - 너무 느리게 (30초) → 데이터 정확도 감소
     * - 5초 = 적절한 균형
     *
     * @param sessionId 세션 ID
     * @param scrollDepth 스크롤 깊이 (0-100)
     */
    @Async
    @Transactional
    fun updateScrollDepth(sessionId: String, scrollDepth: BigDecimal) {
        // 1. Redis에서 로그 ID 조회
        val sessionLogKey = REDIS_KEY_SESSION_LOG.replace("{sessionId}", sessionId)
        val logIdStr = redisTemplate.opsForValue().get(sessionLogKey) ?: return

        val logId = logIdStr.toLongOrNull() ?: return

        // 2. MySQL 업데이트
        val readLog = episodeReadLogRepository.findById(logId).orElse(null) ?: return
        readLog.updateScrollDepth(scrollDepth)
        episodeReadLogRepository.save(readLog)

        // 3. 완독 시 Redis에서 제거
        if (scrollDepth >= BigDecimal(COMPLETION_THRESHOLD)) {
            val currentReadersKey = REDIS_KEY_CURRENT_READERS.replace("{episodeId}", readLog.episodeId.toString())
            redisTemplate.opsForSet().remove(currentReadersKey, sessionId)
        }
    }

    /**
     * 읽기 세션 종료 (정상 종료)
     *
     * 사용자가 페이지를 벗어날 때 호출됩니다.
     *
     * ## 프론트엔드 연동 예시
     *
     * ```javascript
     * // episode-reader.js
     * // 페이지 이탈 시 호출
     * window.addEventListener('beforeunload', () => {
     *     // Beacon API로 전송 (페이지 unload 중에도 전송 보장)
     *     navigator.sendBeacon('/api/behavior/end', JSON.stringify({
     *         sessionId: getSessionId(),
     *         scrollDepth: maxScrollDepth
     *     }));
     * });
     * ```
     *
     * ## WHY Beacon API?
     *
     * - 일반 fetch/XHR은 페이지 unload 중 취소될 수 있음
     * - Beacon API는 브라우저가 전송을 보장
     * - 비동기, 논블로킹
     *
     * @param sessionId 세션 ID
     * @param scrollDepth 최종 스크롤 깊이
     */
    @Async
    @Transactional
    fun endReadingSession(sessionId: String, scrollDepth: BigDecimal) {
        // 1. Redis에서 로그 ID 조회
        val sessionLogKey = REDIS_KEY_SESSION_LOG.replace("{sessionId}", sessionId)
        val logIdStr = redisTemplate.opsForValue().get(sessionLogKey) ?: return

        val logId = logIdStr.toLongOrNull() ?: return

        // 2. MySQL 업데이트
        val readLog = episodeReadLogRepository.findById(logId).orElse(null) ?: return

        readLog.endedAt = LocalDateTime.now()
        readLog.updateScrollDepth(scrollDepth)

        // 읽기 시간 계산
        readLog.readingDurationSeconds = Duration.between(readLog.startedAt, readLog.endedAt).seconds.toInt()

        episodeReadLogRepository.save(readLog)

        // 3. Redis에서 현재 읽는 사용자 제거
        val currentReadersKey = REDIS_KEY_CURRENT_READERS.replace("{episodeId}", readLog.episodeId.toString())
        redisTemplate.opsForSet().remove(currentReadersKey, sessionId)

        // 4. Redis 세션 정보 삭제
        redisTemplate.delete(sessionLogKey)
    }

    /**
     * 읽기 세션 종료 (이탈)
     *
     * 사용자가 중간에 이탈했을 때 호출됩니다.
     *
     * ## 이탈 감지 시나리오
     *
     * ### 1. 사용자 액션 (USER_ACTION)
     * - 뒤로가기 버튼
     * - 다른 에피소드로 이동
     * - 브라우저 탭 닫기
     *
     * ### 2. 타임아웃 (TIMEOUT)
     * - 30분간 스크롤 업데이트 없음
     * - Spring Batch로 주기적으로 체크
     *
     * ### 3. 에러 (ERROR)
     * - 네트워크 오류
     * - 서버 오류
     *
     * @param sessionId 세션 ID
     * @param scrollDepth 이탈 시점 스크롤 깊이
     * @param reason 이탈 이유
     */
    @Async
    @Transactional
    fun markAsDropped(sessionId: String, scrollDepth: BigDecimal, reason: DropReason) {
        // 1. Redis에서 로그 ID 조회
        val sessionLogKey = REDIS_KEY_SESSION_LOG.replace("{sessionId}", sessionId)
        val logIdStr = redisTemplate.opsForValue().get(sessionLogKey) ?: return

        val logId = logIdStr.toLongOrNull() ?: return

        // 2. MySQL 업데이트
        val readLog = episodeReadLogRepository.findById(logId).orElse(null) ?: return

        readLog.markAsDropped(scrollDepth, reason)
        episodeReadLogRepository.save(readLog)

        // 3. Redis 정리
        val currentReadersKey = REDIS_KEY_CURRENT_READERS.replace("{episodeId}", readLog.episodeId.toString())
        redisTemplate.opsForSet().remove(currentReadersKey, sessionId)
        redisTemplate.delete(sessionLogKey)
    }

    /**
     * 현재 읽는 사용자 수 조회 (실시간)
     *
     * Redis 기반으로 빠르게 조회합니다.
     *
     * ## 실무 활용 예시
     *
     * ```kotlin
     * // EpisodeDto.kt
     * data class EpisodeDto(
     *     val id: Long,
     *     val title: String,
     *     val content: String,
     *     val currentReaders: Int // ← 실시간 표시
     * )
     *
     * // EpisodeService.kt
     * fun getEpisode(episodeId: Long): EpisodeDto {
     *     val episode = episodeRepository.findById(episodeId)
     *     val currentReaders = userBehaviorLogService.getCurrentReaderCount(episodeId)
     *
     *     return EpisodeDto(
     *         // ...
     *         currentReaders = currentReaders
     *     )
     * }
     * ```
     *
     * @param episodeId 에피소드 ID
     * @return 현재 읽는 사용자 수
     */
    fun getCurrentReaderCount(episodeId: Long): Int {
        val currentReadersKey = REDIS_KEY_CURRENT_READERS.replace("{episodeId}", episodeId.toString())
        return redisTemplate.opsForSet().size(currentReadersKey)?.toInt() ?: 0
    }

    /**
     * 에피소드 누적 조회수 조회 (실시간)
     *
     * Redis 기반 카운터입니다.
     *
     * ## WHY Redis 카운터?
     *
     * - MySQL COUNT 쿼리는 느림 (수백만 건 로그)
     * - Redis는 메모리 기반으로 매우 빠름
     * - 배치 작업으로 주기적으로 MySQL에 동기화
     *
     * @param episodeId 에피소드 ID
     * @return 누적 조회수
     */
    fun getTotalViewCount(episodeId: Long): Long {
        val viewCountKey = REDIS_KEY_VIEW_COUNT.replace("{episodeId}", episodeId.toString())
        val count = redisTemplate.opsForValue().get(viewCountKey)
        return count?.toLongOrNull() ?: 0L
    }

    /**
     * 타임아웃된 세션 정리
     *
     * Spring Batch로 주기적으로 (5분마다) 실행됩니다.
     *
     * ## WHY 필요?
     *
     * - 사용자가 beforeunload 이벤트 없이 탭을 닫거나 네트워크 오류 발생
     * - Redis에는 남아있지만 실제로는 읽지 않는 세션
     * - 주기적으로 정리해야 정확한 "현재 읽는 사용자 수" 표시
     *
     * ## 실무 배치 예시
     *
     * ```kotlin
     * // ScheduledTasks.kt
     * @Component
     * class ScheduledTasks(
     *     private val userBehaviorLogService: UserBehaviorLogService
     * ) {
     *     @Scheduled(cron = "0 */5 * * * *") // 5분마다
     *     fun cleanupTimeoutSessions() {
     *         userBehaviorLogService.cleanupTimeoutSessions()
     *     }
     * }
     * ```
     */
    @Transactional
    fun cleanupTimeoutSessions() {
        // 30분 이전에 시작된 세션 조회
        val cutoffTime = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES)
        val timeoutLogs = episodeReadLogRepository.findByCreatedAtAfter(cutoffTime)
            .filter { it.endedAt == null }

        timeoutLogs.forEach { log ->
            // 타임아웃으로 이탈 처리
            log.markAsDropped(log.maxScrollDepth, DropReason.TIMEOUT)
            episodeReadLogRepository.save(log)

            // Redis 정리
            val currentReadersKey = REDIS_KEY_CURRENT_READERS.replace("{episodeId}", log.episodeId.toString())
            redisTemplate.opsForSet().remove(currentReadersKey, log.sessionId)

            val sessionLogKey = REDIS_KEY_SESSION_LOG.replace("{sessionId}", log.sessionId)
            redisTemplate.delete(sessionLogKey)
        }
    }
}
