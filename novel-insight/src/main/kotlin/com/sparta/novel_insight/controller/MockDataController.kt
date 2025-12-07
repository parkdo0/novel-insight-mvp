package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.MemberRole
import com.sparta.novel_insight.service.AuthorDashboardService
import com.sparta.novel_insight.service.MockDataGeneratorService
import com.sparta.novel_insight.service.MockDataSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// # MockDataController
//
// Mock 데이터 생성 API 컨트롤러입니다.
//
// ## WHY API로 제공?
//
// - 개발/스테이징 환경에서 쉽게 테스트 데이터 생성
// - Postman/curl로 즉시 실행 가능
// - 필요한 만큼만 데이터 생성 가능
// - 프로덕션에서는 비활성화 (Profile 분리)
//
// ## 사용 예시
//
// ```bash
// # 전체 Mock 데이터 생성 (기존 데이터 유지)
// POST http://localhost:8080/api/mock/generate-all
//
// # 전체 Mock 데이터 생성 (기존 데이터 삭제)
// POST http://localhost:8080/api/mock/generate-all?clearExisting=true
//
// # 특정 데이터만 생성
// POST http://localhost:8080/api/mock/generate-members?authors=20&readers=500
// POST http://localhost:8080/api/mock/generate-read-logs?count=100000
//
// # 기존 데이터 전체 삭제
// DELETE http://localhost:8080/api/mock/clear-all
// ```
//
// ## 주의사항
//
// ⚠️ **프로덕션에서는 비활성화 필수!**
//
// ```yaml
// # application-prod.yml
// mock-data:
//   enabled: false
// ```
@RestController
@RequestMapping("/api/mock")
class MockDataController(
    private val mockDataGeneratorService: MockDataGeneratorService,
    private val authorDashboardService: AuthorDashboardService
) {

    // ★★★ [추천] 처음부터 완전히 새로 생성 (기존 데이터 삭제) ★★★
    //
    // ## 사용 예시
    //
    // ```bash
    // POST /api/mock/reset-and-generate
    // ```
    //
    // 모든 데이터를 삭제하고 새로 생성합니다.
    // 에피소드 내용, 제목 등 모든 것이 새로 생성됩니다.
    //
    @PostMapping("/reset-and-generate")
    fun resetAndGenerateAll(): ResponseEntity<MockDataResponse> {
        val startTime = System.currentTimeMillis()

        try {
            // 1. 모든 데이터 삭제
            mockDataGeneratorService.clearAllData()

            // 2. 새 데이터 생성
            val summary = mockDataGeneratorService.generateAllMockData(false)

            // 3. 대시보드 캐시 생성
            authorDashboardService.generateDailyDashboardCache()

            val duration = (System.currentTimeMillis() - startTime) / 1000

            return ResponseEntity.ok(
                MockDataResponse(
                    success = true,
                    message = "All data reset and regenerated successfully! Dashboard cache also created.",
                    summary = summary,
                    durationSeconds = duration
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.internalServerError().body(
                MockDataResponse(
                    success = false,
                    message = "Failed to reset and generate: ${e.message}",
                    summary = null,
                    durationSeconds = (System.currentTimeMillis() - startTime) / 1000
                )
            )
        }
    }

    // 기존 데이터에 추가로 생성 (기존 데이터 유지)
    //
    // ## 사용 예시
    //
    // ```bash
    // POST /api/mock/add-data
    // ```
    //
    // 기존 데이터를 유지하면서 추가 데이터를 생성합니다.
    // 주의: 이메일 중복 등의 이유로 실패할 수 있습니다.
    //
    @PostMapping("/add-data")
    fun addMockData(): ResponseEntity<MockDataResponse> {
        val startTime = System.currentTimeMillis()

        try {
            val summary = mockDataGeneratorService.generateAllMockData(false)
            val duration = (System.currentTimeMillis() - startTime) / 1000

            return ResponseEntity.ok(
                MockDataResponse(
                    success = true,
                    message = "Mock data added successfully",
                    summary = summary,
                    durationSeconds = duration
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                MockDataResponse(
                    success = false,
                    message = "Failed to add mock data: ${e.message}",
                    summary = null,
                    durationSeconds = (System.currentTimeMillis() - startTime) / 1000
                )
            )
        }
    }

    // [레거시] 전체 Mock 데이터 생성
    @PostMapping("/generate-all")
    fun generateAllMockData(
        @RequestParam(required = false, defaultValue = "false") clearExisting: Boolean
    ): ResponseEntity<MockDataResponse> {
        val startTime = System.currentTimeMillis()

        try {
            if (clearExisting) {
                mockDataGeneratorService.clearAllData()
            }
            val summary = mockDataGeneratorService.generateAllMockData(false)
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

    // 회원 데이터만 생성
    //
    // ```bash
    // POST /api/mock/generate-members?authors=20&readers=500
    // ```
    //
    // @param authors 작가 수 (기본: 50)
    // @param readers 독자 수 (기본: 1000)
    // @return 생성된 회원 수
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

    // 읽기 로그만 대량 생성 (성능 테스트용)
    //
    // ## 사용 예시
    //
    // ```bash
    // # 10만 개 로그 생성 (빠름)
    // POST /api/mock/generate-read-logs?count=100000
    //
    // # 100만 개 로그 생성 (느림, ~10분)
    // POST /api/mock/generate-read-logs?count=1000000
    // ```
    //
    // @param count 로그 수
    // @param days 최근 N일간 데이터
    // @return 생성 결과
    @PostMapping("/generate-read-logs")
    fun generateReadLogs(
        @RequestParam(required = false, defaultValue = "500000") count: Int,
        @RequestParam(required = false, defaultValue = "30") days: Int
    ): ResponseEntity<Map<String, Any>> {
        val startTime = System.currentTimeMillis()

        try {
            // 기존 독자 및 에피소드 조회
            val readers = mockDataGeneratorService.memberRepository.findAll()
                .filter { it.role == MemberRole.READER }
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

    // 기존 데이터 전체 삭제
    //
    // ## 주의사항
    //
    // ⚠️ **되돌릴 수 없습니다!**
    //
    // - 모든 테이블 데이터 삭제
    // - FK 제약조건 역순으로 삭제
    // - 프로덕션 환경에서는 절대 실행 금지
    //
    // ```bash
    // DELETE /api/mock/clear-all
    // ```
    //
    // @return 삭제 결과
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

    // Mock 데이터 생성 상태 확인
    //
    // 현재 DB에 있는 데이터 개수를 반환합니다.
    //
    // ```bash
    // GET /api/mock/status
    // ```
    //
    // @return 현재 데이터 개수
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

    // 대시보드 캐시 수동 생성
    //
    // 배치 작업이 새벽 3시에 실행되기 때문에,
    // 개발/테스트 환경에서 수동으로 캐시를 생성할 때 사용합니다.
    //
    // ## 사용 예시
    //
    // ```bash
    // POST /api/mock/generate-dashboard-cache
    // ```
    //
    // @return 생성 결과
    @PostMapping("/generate-dashboard-cache")
    fun generateDashboardCache(): ResponseEntity<Map<String, Any>> {
        val startTime = System.currentTimeMillis()

        try {
            // 읽기 로그가 있는지 확인
            val readLogCount = mockDataGeneratorService.episodeReadLogRepository.count()
            if (readLogCount == 0L) {
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "success" to false,
                        "message" to "No read logs found. Please generate read logs first using /api/mock/generate-read-logs"
                    )
                )
            }

            // 대시보드 캐시 생성 (배치 작업 수동 실행)
            authorDashboardService.generateDailyDashboardCache()
            val duration = (System.currentTimeMillis() - startTime) / 1000

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Dashboard cache generated successfully",
                    "durationSeconds" to duration
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to generate dashboard cache: ${e.message}",
                    "durationSeconds" to (System.currentTimeMillis() - startTime) / 1000
                )
            )
        }
    }

    // 읽기 로그 + 대시보드 캐시 한번에 생성
    //
    // ## 사용 예시
    //
    // ```bash
    // POST /api/mock/generate-analytics-data?logCount=10000
    // ```
    //
    // @param logCount 생성할 로그 수 (기본: 10000)
    // @return 생성 결과
    @PostMapping("/generate-analytics-data")
    fun generateAnalyticsData(
        @RequestParam(required = false, defaultValue = "10000") logCount: Int
    ): ResponseEntity<Map<String, Any>> {
        val startTime = System.currentTimeMillis()

        try {
            // 1. 기존 독자 및 에피소드 조회
            val readers = mockDataGeneratorService.memberRepository.findAll()
                .filter { it.role == MemberRole.READER }
            val episodes = mockDataGeneratorService.episodeRepository.findAll()

            if (readers.isEmpty() || episodes.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "success" to false,
                        "message" to "No members or episodes found. Please generate members and novels first."
                    )
                )
            }

            // 2. 읽기 로그 생성
            val logs = mockDataGeneratorService.generateReadLogs(logCount, readers.toList(), episodes.toList(), 30)

            // 3. 대시보드 캐시 생성
            authorDashboardService.generateDailyDashboardCache()

            val duration = (System.currentTimeMillis() - startTime) / 1000

            return ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Analytics data generated successfully",
                    "readLogsCreated" to logs.size,
                    "dashboardCacheCreated" to true,
                    "durationSeconds" to duration
                )
            )
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to generate analytics data: ${e.message}",
                    "durationSeconds" to (System.currentTimeMillis() - startTime) / 1000
                )
            )
        }
    }
}

// Mock 데이터 생성 응답 DTO
data class MockDataResponse(
    val success: Boolean,
    val message: String,
    val summary: MockDataSummary?,
    val durationSeconds: Long
)
