package com.sparta.novel_insight.controller

import com.sparta.novel_insight.service.MockDataGeneratorService
import com.sparta.novel_insight.service.MockDataSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * # MockDataController
 *
 * Mock 데이터 생성 API 컨트롤러입니다.
 *
 * ## WHY API로 제공?
 *
 * - 개발/스테이징 환경에서 쉽게 테스트 데이터 생성
 * - Postman/curl로 즉시 실행 가능
 * - 필요한 만큼만 데이터 생성 가능
 * - 프로덕션에서는 비활성화 (Profile 분리)
 *
 * ## 사용 예시
 *
 * ```bash
 * # 전체 Mock 데이터 생성 (기존 데이터 유지)
 * POST http://localhost:8080/api/mock/generate-all
 *
 * # 전체 Mock 데이터 생성 (기존 데이터 삭제)
 * POST http://localhost:8080/api/mock/generate-all?clearExisting=true
 *
 * # 특정 데이터만 생성
 * POST http://localhost:8080/api/mock/generate-members?authors=20&readers=500
 * POST http://localhost:8080/api/mock/generate-read-logs?count=100000
 *
 * # 기존 데이터 전체 삭제
 * DELETE http://localhost:8080/api/mock/clear-all
 * ```
 *
 * ## 주의사항
 *
 * ⚠️ **프로덕션에서는 비활성화 필수!**
 *
 * ```yaml
 * # application-prod.yml
 * mock-data:
 *   enabled: false
 * ```
 */
@RestController
@RequestMapping("/api/mock")
class MockDataController(
    private val mockDataGeneratorService: MockDataGeneratorService
) {

    /**
     * 전체 Mock 데이터 생성
     *
     * ## 생성 데이터
     *
     * - 작가 50명 + 독자 1,000명
     * - 소설 150편
     * - 에피소드 ~5,000개
     * - 리뷰 50,000개 (감성 분석 포함)
     * - 읽기 로그 500,000개 (최근 30일)
     *
     * ## 소요 시간
     *
     * - **예상**: 5-10분 (500,000개 로그 생성)
     * - **메모리**: ~1GB 사용
     * - **디스크**: ~500MB 증가
     *
     * ## 응답 예시
     *
     * ```json
     * {
     *   "success": true,
     *   "message": "Mock data generated successfully",
     *   "summary": {
     *     "members": 1050,
     *     "novels": 150,
     *     "episodes": 4823,
     *     "reviews": 50000,
     *     "readLogs": 500000
     *   },
     *   "duration": "8m 32s"
     * }
     * ```
     *
     * @param clearExisting 기존 데이터 삭제 여부 (기본: false)
     * @return 생성 결과 요약
     */
    @PostMapping("/generate-all")
    fun generateAllMockData(
        @RequestParam(required = false, defaultValue = "false") clearExisting: Boolean
    ): ResponseEntity<MockDataResponse> {
        val startTime = System.currentTimeMillis()

        try {
            val summary = mockDataGeneratorService.generateAllMockData(clearExisting)
            val duration = (System.currentTimeMillis() - startTime) / 1000

            return ResponseEntity.ok(
                MockDataResponse(
                    success = true,
                    message = "Mock data generated successfully",
                    summary = summary,
                    durationSeconds = duration
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                MockDataResponse(
                    success = false,
                    message = "Failed to generate mock data: ${e.message}",
                    summary = null,
                    durationSeconds = (System.currentTimeMillis() - startTime) / 1000
                )
            )
        }
    }

    /**
     * 회원 데이터만 생성
     *
     * ```bash
     * POST /api/mock/generate-members?authors=20&readers=500
     * ```
     *
     * @param authors 작가 수 (기본: 50)
     * @param readers 독자 수 (기본: 1000)
     * @return 생성된 회원 수
     */
    @PostMapping("/generate-members")
    fun generateMembers(
        @RequestParam(required = false, defaultValue = "50") authors: Int,
        @RequestParam(required = false, defaultValue = "1000") readers: Int
    ): ResponseEntity<Map<String, Any>> {
        try {
            val members = mockDataGeneratorService.generateMembers(authors, readers)

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Members generated successfully",
                    "count" to members.size,
                    "authors" to authors,
                    "readers" to readers
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to generate members: ${e.message}"
                )
            )
        }
    }

    /**
     * 읽기 로그만 대량 생성 (성능 테스트용)
     *
     * ## 사용 예시
     *
     * ```bash
     * # 10만 개 로그 생성 (빠름)
     * POST /api/mock/generate-read-logs?count=100000
     *
     * # 100만 개 로그 생성 (느림, ~10분)
     * POST /api/mock/generate-read-logs?count=1000000
     * ```
     *
     * @param count 로그 수
     * @param days 최근 N일간 데이터
     * @return 생성 결과
     */
    @PostMapping("/generate-read-logs")
    fun generateReadLogs(
        @RequestParam(required = false, defaultValue = "500000") count: Int,
        @RequestParam(required = false, defaultValue = "30") days: Int
    ): ResponseEntity<Map<String, Any>> {
        val startTime = System.currentTimeMillis()

        try {
            // 기존 독자 및 에피소드 조회
            val readers = mockDataGeneratorService.memberRepository.findAll()
                .filter { it.role == com.sparta.novel_insight.service.MemberRole.READER }
            val episodes = mockDataGeneratorService.episodeRepository.findAll()

            if (readers.isEmpty() || episodes.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "success" to false,
                        "message" to "No members or episodes found. Please generate members and novels first."
                    )
                )
            }

            val logs = mockDataGeneratorService.generateReadLogs(count, readers.toList(), episodes.toList(), days)
            val duration = (System.currentTimeMillis() - startTime) / 1000

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Read logs generated successfully",
                    "count" to logs.size,
                    "durationSeconds" to duration
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to generate read logs: ${e.message}",
                    "durationSeconds" to (System.currentTimeMillis() - startTime) / 1000
                )
            )
        }
    }

    /**
     * 기존 데이터 전체 삭제
     *
     * ## 주의사항
     *
     * ⚠️ **되돌릴 수 없습니다!**
     *
     * - 모든 테이블 데이터 삭제
     * - FK 제약조건 역순으로 삭제
     * - 프로덕션 환경에서는 절대 실행 금지
     *
     * ```bash
     * DELETE /api/mock/clear-all
     * ```
     *
     * @return 삭제 결과
     */
    @DeleteMapping("/clear-all")
    fun clearAllData(): ResponseEntity<Map<String, Any>> {
        try {
            mockDataGeneratorService.clearAllData()

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "All mock data cleared successfully"
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to clear data: ${e.message}"
                )
            )
        }
    }

    /**
     * Mock 데이터 생성 상태 확인
     *
     * 현재 DB에 있는 데이터 개수를 반환합니다.
     *
     * ```bash
     * GET /api/mock/status
     * ```
     *
     * @return 현재 데이터 개수
     */
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<Map<String, Any>> {
        try {
            val memberCount = mockDataGeneratorService.memberRepository.count()
            val novelCount = mockDataGeneratorService.novelRepository.count()
            val episodeCount = mockDataGeneratorService.episodeRepository.count()
            val reviewCount = mockDataGeneratorService.reviewRepository.count()
            val readLogCount = mockDataGeneratorService.episodeReadLogRepository.count()

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "data" to mapOf(
                        "members" to memberCount,
                        "novels" to novelCount,
                        "episodes" to episodeCount,
                        "reviews" to reviewCount,
                        "readLogs" to readLogCount
                    ),
                    "message" to "Current data counts"
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to get status: ${e.message}"
                )
            )
        }
    }
}

/**
 * Mock 데이터 생성 응답 DTO
 */
data class MockDataResponse(
    val success: Boolean,
    val message: String,
    val summary: MockDataSummary?,
    val durationSeconds: Long
)
