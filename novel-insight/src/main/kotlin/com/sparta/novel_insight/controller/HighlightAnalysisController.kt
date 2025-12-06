package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.DeviceType
import com.sparta.novel_insight.domain.SectionReplayLog
import com.sparta.novel_insight.domain.ViewerMode
import com.sparta.novel_insight.repository.EpisodeRepository
import com.sparta.novel_insight.repository.SectionReplayLogRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

//
// # 명장면 분석 API Controller
//
// 사용자가 특정 구간을 반복 조회하는 행동을 추적하여
// 명장면(하이라이트)을 분석합니다.
//
// ## 주요 기능
//
// 1. 재조회 로그 기록 (프론트에서 전송)
// 2. 에피소드별 명장면 구간 조회
// 3. 소설별 명장면 통계 조회
//
@RestController
@RequestMapping("/api/highlights")
class HighlightAnalysisController(
    private val sectionReplayLogRepository: SectionReplayLogRepository,
    private val episodeRepository: EpisodeRepository
) {

    //
    // 재조회 로그 기록
    //
    // 프론트엔드에서 사용자가 이전에 본 구간으로 돌아갈 때 호출
    //
    // ## 요청 예시
    //
    // ```json
    // {
    //   "episodeId": 123,
    //   "sectionPercentage": 25.5,
    //   "sectionEndPercentage": 30.0,
    //   "sessionId": "abc123",
    //   "viewerMode": "SCROLL",
    //   "deviceType": "MOBILE"
    // }
    // ```
    //
    @PostMapping("/replay-log")
    fun recordReplayLog(
        @RequestBody request: ReplayLogRequest
    ): ResponseEntity<Map<String, Any>> {
        try {
            val episode = episodeRepository.findById(request.episodeId).orElse(null)
                ?: return ResponseEntity.badRequest().body(
                    mapOf("success" to false, "message" to "Episode not found")
                )

            // 10% 단위로 구간 ID 계산
            val sectionId = (request.sectionPercentage.toDouble() / 10).toInt().coerceIn(0, 9)

            // 동일 세션에서 동일 구간 기록이 있는지 확인
            val existingLog = sectionReplayLogRepository.findBySessionIdAndEpisodeIdAndSectionId(
                request.sessionId,
                request.episodeId,
                sectionId
            )

            if (existingLog != null) {
                // 기존 로그의 재조회 횟수 증가
                existingLog.incrementReplayCount()
                request.durationSeconds?.let { existingLog.updateDuration(it) }
                sectionReplayLogRepository.save(existingLog)
            } else {
                // 새 로그 생성
                val newLog = SectionReplayLog(
                    userId = request.userId,
                    episodeId = request.episodeId,
                    novelId = episode.novel.id!!,
                    sessionId = request.sessionId,
                    sectionPercentage = BigDecimal(request.sectionPercentage.toString()),
                    sectionEndPercentage = BigDecimal(request.sectionEndPercentage.toString()),
                    sectionId = sectionId,
                    replayCount = 1,
                    durationSeconds = request.durationSeconds,
                    viewerMode = request.viewerMode,
                    deviceType = request.deviceType
                )
                sectionReplayLogRepository.save(newLog)
            }

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Replay log recorded",
                    "sectionId" to sectionId
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf("success" to false, "message" to "Failed to record replay log: ${e.message}")
            )
        }
    }

    //
    // 에피소드별 명장면 구간 조회
    //
    // ## 응답 예시
    //
    // ```json
    // {
    //   "success": true,
    //   "highlights": [
    //     { "sectionId": 3, "sectionRange": "30%-40%", "uniqueReplays": 150, "totalReplays": 450 },
    //     { "sectionId": 7, "sectionRange": "70%-80%", "uniqueReplays": 120, "totalReplays": 380 }
    //   ]
    // }
    // ```
    //
    @GetMapping("/episodes/{episodeId}")
    fun getEpisodeHighlights(
        @PathVariable episodeId: Long
    ): ResponseEntity<Map<String, Any>> {
        try {
            val stats = sectionReplayLogRepository.findSectionStatsByEpisodeId(episodeId)

            val highlights = stats.map { row ->
                val sectionId = (row[0] as Number).toInt()
                mapOf(
                    "sectionId" to sectionId,
                    "sectionRange" to "${sectionId * 10}%-${(sectionId + 1) * 10}%",
                    "uniqueReplays" to (row[1] as Number).toLong(),
                    "totalReplays" to (row[2] as Number).toLong()
                )
            }

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "episodeId" to episodeId,
                    "highlights" to highlights
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf("success" to false, "message" to "Failed to get highlights: ${e.message}")
            )
        }
    }

    //
    // 소설별 명장면 TOP 10 조회
    //
    @GetMapping("/novels/{novelId}")
    fun getNovelHighlights(
        @PathVariable novelId: Long,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<Map<String, Any>> {
        try {
            val stats = sectionReplayLogRepository.findTopHighlightsByNovelId(novelId)

            val highlights = stats.take(limit).map { row ->
                val episodeId = (row[0] as Number).toLong()
                val sectionId = (row[1] as Number).toInt()
                mapOf(
                    "episodeId" to episodeId,
                    "sectionId" to sectionId,
                    "sectionRange" to "${sectionId * 10}%-${(sectionId + 1) * 10}%",
                    "uniqueReplays" to (row[2] as Number).toLong(),
                    "totalReplays" to (row[3] as Number).toLong()
                )
            }

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "novelId" to novelId,
                    "highlights" to highlights
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf("success" to false, "message" to "Failed to get novel highlights: ${e.message}")
            )
        }
    }

    //
    // 에피소드별 재조회 인기 순위
    //
    @GetMapping("/novels/{novelId}/episodes-rank")
    fun getEpisodeReplayRank(
        @PathVariable novelId: Long,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<Map<String, Any>> {
        try {
            val stats = sectionReplayLogRepository.findEpisodeReplayRankByNovelId(novelId)

            val ranking = stats.take(limit).mapIndexed { index, row ->
                mapOf(
                    "rank" to (index + 1),
                    "episodeId" to (row[0] as Number).toLong(),
                    "totalReplays" to (row[1] as Number).toLong()
                )
            }

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "novelId" to novelId,
                    "ranking" to ranking
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf("success" to false, "message" to "Failed to get episode rank: ${e.message}")
            )
        }
    }
}

// 재조회 로그 요청 DTO
data class ReplayLogRequest(
    val userId: Long? = null,
    val episodeId: Long,
    val sectionPercentage: Double,
    val sectionEndPercentage: Double,
    val sessionId: String,
    val viewerMode: ViewerMode,
    val deviceType: DeviceType,
    val durationSeconds: Int? = null
)
