package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.MemberRole
import com.sparta.novel_insight.service.AuthorDashboardService
import com.sparta.novel_insight.service.MockDataGeneratorService
import com.sparta.novel_insight.service.MockDataSummary
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

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

    private val executor = Executors.newCachedThreadPool()

    // ★★★ [추천] 처음부터 완전히 새로 생성 (기존 데이터 삭제) - 실시간 진행률 표시! ★★★
    //
    // ## 사용 예시 (브라우저에서 바로 접속!)
    //
    // http://localhost:8080/api/mock/reset-and-generate
    //
    // 모든 데이터를 삭제하고 새로 생성합니다.
    // 실시간으로 진행 상황을 볼 수 있습니다!
    //
    @GetMapping("/reset-and-generate", produces = ["text/event-stream;charset=UTF-8"])
    fun resetAndGenerateAll(response: jakarta.servlet.http.HttpServletResponse): SseEmitter {
        // UTF-8 인코딩 설정
        response.characterEncoding = "UTF-8"
        response.contentType = "text/event-stream;charset=UTF-8"

        val emitter = SseEmitter(600000L) // 10분 타임아웃

        executor.execute {
            val startTime = System.currentTimeMillis()
            try {
                // 1. 모든 데이터 삭제
                sendProgress(emitter, "[1/5] 기존 데이터 삭제 중...")
                mockDataGeneratorService.clearAllData()
                sendProgress(emitter, "[1/5] 기존 데이터 삭제 완료!")

                // 2. 스키마 수정
                sendProgress(emitter, "[2/5] 스키마 수정 중 (episode.content -> LONGTEXT)...")
                mockDataGeneratorService.fixSchema()
                sendProgress(emitter, "[2/5] 스키마 수정 완료!")

                // 3. Mock 데이터 생성
                sendProgress(emitter, "[3/5] Mock 데이터 생성 시작...")
                sendProgress(emitter, "    회원 생성 중 (작가 50명, 독자 1000명)...")
                val summary = mockDataGeneratorService.generateAllMockData(false)
                sendProgress(emitter, "[3/5] Mock 데이터 생성 완료!")
                sendProgress(emitter, "    - 회원: ${summary.members}명")
                sendProgress(emitter, "    - 소설: ${summary.novels}편")
                sendProgress(emitter, "    - 에피소드: ${summary.episodes}개 (3000자+ 내용)")
                sendProgress(emitter, "    - 리뷰: ${summary.reviews}개")
                sendProgress(emitter, "    - 읽기 로그: ${summary.readLogs}개")

                // 4. 대시보드 캐시 생성 (오늘 날짜로!)
                sendProgress(emitter, "[4/5] 대시보드 캐시 생성 중...")
                authorDashboardService.generateTodayDashboardCache()
                sendProgress(emitter, "[4/5] 대시보드 캐시 생성 완료!")

                // 5. 완료
                val duration = (System.currentTimeMillis() - startTime) / 1000
                sendProgress(emitter, "[5/5] 모든 작업 완료! (소요시간: ${duration}초)")
                sendProgress(emitter, "")
                sendProgress(emitter, "이제 대시보드에서 데이터를 확인할 수 있습니다!")

                emitter.complete()
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    sendProgress(emitter, "오류 발생: ${e.message}")
                    emitter.completeWithError(e)
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }

        return emitter
    }

    private fun sendProgress(emitter: SseEmitter, message: String) {
        Thread.sleep(50) // 약간의 딜레이로 브라우저 버퍼링 방지
        try {
            val utf8Message = String(message.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
            emitter.send(SseEmitter.event().data(utf8Message).name("progress"))
        } catch (e: Exception) {
            // 클라이언트 연결이 끊어진 경우 무시
        }
    }

    // 기존 데이터에 추가로 생성 (기존 데이터 유지)
    //
    // ## 사용 예시 (브라우저에서 바로 접속!)
    //
    // http://localhost:8080/api/mock/add-data
    //
    // 기존 데이터를 유지하면서 추가 데이터를 생성합니다.
    // 주의: 이메일 중복 등의 이유로 실패할 수 있습니다.
    //
    @GetMapping("/add-data")
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
    @GetMapping("/generate-all")
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
    // http://localhost:8080/api/mock/generate-members?authors=20&readers=500
    @GetMapping("/generate-members")
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
    // http://localhost:8080/api/mock/generate-read-logs?count=100000
    @GetMapping("/generate-read-logs")
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

    // 기존 데이터 전체 삭제 (TRUNCATE로 빠르게!)
    // http://localhost:8080/api/mock/clear-all
    @GetMapping("/clear-all")
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

    // 스키마 수정 (episode.content → LONGTEXT)
    // http://localhost:8080/api/mock/fix-schema
    @GetMapping("/fix-schema")
    fun fixSchema(): ResponseEntity<Map<String, Any>> {
        try {
            mockDataGeneratorService.fixSchema()
            return ResponseEntity.ok(mapOf("success" to true, "message" to "Schema fixed!"))
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                mapOf("success" to false, "message" to "Failed: ${e.message}")
            )
        }
    }

    // Mock 데이터 생성 상태 확인
    // http://localhost:8080/api/mock/status
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
    // http://localhost:8080/api/mock/generate-dashboard-cache
    @GetMapping("/generate-dashboard-cache")
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
    // http://localhost:8080/api/mock/generate-analytics-data?logCount=10000
    @GetMapping("/generate-analytics-data")
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
