package com.sparta.novel_insight.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.sql.Connection
import javax.sql.DataSource
import kotlin.concurrent.thread

/**
 * 관리자 전용 컨트롤러
 * Mock Data 생성 등 개발/테스트용 기능 제공
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    @Autowired private val dataSource: DataSource
) {

    /**
     * Mock Data 생성 시작 API
     *
     * 사용법:
     * http://localhost:8080/api/admin/generate-mock-data-start
     *
     * 이 API를 호출하면 백그라운드에서 Mock Data 생성이 시작되고,
     * 바로 응답을 받습니다. 터미널에서 진행 상황을 확인하세요.
     */
    @GetMapping("/generate-mock-data-start")
    fun generateMockDataStart(): ResponseEntity<Map<String, Any>> {
        thread {
            try {
                generateMockDataAsync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Mock Data 생성이 백그라운드에서 시작되었습니다.",
                "tip" to "터미널에서 진행 상황을 확인하세요."
            )
        )
    }

    /**
     * Mock Data 생성 + 실시간 진행 상황 스트리밍
     *
     * 사용법 (브라우저에서 바로 실행 가능!):
     * http://localhost:8080/api/admin/generate-mock-data-stream
     *
     * Server-Sent Events를 사용하여 실시간으로 진행 상황을 보여줍니다.
     *
     * ⚠️ 주의: 기존 데이터를 모두 삭제하고 새로운 Mock Data를 생성합니다!
     *
     * 생성 데이터:
     * - 회원 1,050명 (작가 50명 + 독자 1,000명)
     * - 소설 150편
     * - 에피소드 ~5,000개
     * - 리뷰 50,000개
     * - 댓글 100,000개
     * - 읽기 로그 500,000개
     *
     * 예상 소요 시간: 10-15분
     */
    @GetMapping("/generate-mock-data-stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun generateMockDataStream(): SseEmitter {
        val emitter = SseEmitter(900000L) // 15분 타임아웃

        thread {
            try {
                sendProgress(emitter, "🚀 Mock Data 생성 시작...")
                sendProgress(emitter, "📂 mock-data.sql 파일 읽는 중...")

                val resource = ClassPathResource("mock-data.sql")
                if (!resource.exists()) {
                    sendProgress(emitter, "❌ 오류: mock-data.sql 파일을 찾을 수 없습니다.")
                    emitter.complete()
                    return@thread
                }

                sendProgress(emitter, "✅ 파일 발견!")
                sendProgress(emitter, "🗄️ 데이터베이스 연결 중...")

                val startTime = System.currentTimeMillis()

                // ScriptUtils를 사용하여 SQL 스크립트 실행 (DELIMITER 지원)
                dataSource.connection.use { connection: Connection ->
                    connection.autoCommit = false

                    sendProgress(emitter, "✅ 연결 성공!")
                    sendProgress(emitter, "🔄 SQL 스크립트 실행 중...")
                    sendProgress(emitter, "")
                    sendProgress(emitter, "📊 단계별 진행 상황:")
                    sendProgress(emitter, "1️⃣ 기존 데이터 삭제 중...")

                    try {
                        // SQL 스크립트 실행
                        ScriptUtils.executeSqlScript(connection, resource)
                        connection.commit()

                        sendProgress(emitter, "")
                        sendProgress(emitter, "✅ SQL 스크립트 실행 완료!")
                    } catch (e: Exception) {
                        connection.rollback()
                        throw e
                    }
                }

                val endTime = System.currentTimeMillis()
                val durationSeconds = (endTime - startTime) / 1000

                // 데이터 개수 확인
                sendProgress(emitter, "")
                sendProgress(emitter, "📊 생성된 데이터 확인 중...")

                val jdbcTemplate = JdbcTemplate(dataSource)
                val counts = mapOf(
                    "members" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member", Long::class.java) ?: 0),
                    "novels" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM novel", Long::class.java) ?: 0),
                    "episodes" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode", Long::class.java) ?: 0),
                    "reviews" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review", Long::class.java) ?: 0),
                    "comments" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode_comment", Long::class.java) ?: 0),
                    "readLogs" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode_read_log", Long::class.java) ?: 0)
                )

                sendProgress(emitter, "")
                sendProgress(emitter, "=".repeat(50))
                sendProgress(emitter, "🎉 Mock Data 생성 완료!")
                sendProgress(emitter, "=".repeat(50))
                sendProgress(emitter, "")
                sendProgress(emitter, "📊 생성된 데이터:")
                sendProgress(emitter, "  👥 회원: ${counts["members"]}명")
                sendProgress(emitter, "  📚 소설: ${counts["novels"]}편")
                sendProgress(emitter, "  📖 에피소드: ${counts["episodes"]}개")
                sendProgress(emitter, "  ⭐ 리뷰: ${counts["reviews"]}개")
                sendProgress(emitter, "  💬 댓글: ${counts["comments"]}개")
                sendProgress(emitter, "  📈 읽기 로그: ${counts["readLogs"]}개")
                sendProgress(emitter, "")
                sendProgress(emitter, "⏱️ 소요 시간: ${durationSeconds}초 (${durationSeconds / 60}분 ${durationSeconds % 60}초)")
                sendProgress(emitter, "")
                sendProgress(emitter, "✨ 이제 프론트엔드에서 데이터를 확인할 수 있습니다!")

                emitter.complete()

            } catch (e: Exception) {
                sendProgress(emitter, "")
                sendProgress(emitter, "❌ 오류 발생: ${e.message}")
                e.printStackTrace()
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    /**
     * Mock Data 생성 (동기 방식 - JSON 응답)
     *
     * Server-Sent Events를 지원하지 않는 클라이언트용
     */
    @GetMapping("/generate-mock-data")
    fun generateMockData(): ResponseEntity<Map<String, Any>> {
        return try {
            val resource = ClassPathResource("mock-data.sql")
            if (!resource.exists()) {
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "success" to false,
                        "message" to "mock-data.sql 파일을 찾을 수 없습니다.",
                        "tip" to "/generate-mock-data-stream을 사용하면 실시간 진행 상황을 볼 수 있습니다."
                    )
                )
            }

            val startTime = System.currentTimeMillis()

            // ScriptUtils를 사용하여 SQL 스크립트 실행
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    ScriptUtils.executeSqlScript(connection, resource)
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }

            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000

            // 데이터 개수 확인
            val jdbcTemplate = JdbcTemplate(dataSource)
            val counts = mapOf(
                "members" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member", Long::class.java) ?: 0),
                "novels" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM novel", Long::class.java) ?: 0),
                "episodes" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode", Long::class.java) ?: 0),
                "reviews" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review", Long::class.java) ?: 0),
                "comments" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode_comment", Long::class.java) ?: 0),
                "readLogs" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode_read_log", Long::class.java) ?: 0)
            )

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Mock Data 생성 완료!",
                    "durationSeconds" to durationSeconds,
                    "data" to counts,
                    "tip" to "/generate-mock-data-stream을 사용하면 실시간 진행 상황을 볼 수 있습니다."
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "message" to "Mock Data 생성 중 오류 발생",
                    "error" to (e.message ?: "알 수 없는 오류")
                )
            )
        }
    }

    /**
     * 비동기 Mock Data 생성 (내부 사용)
     */
    private fun generateMockDataAsync() {
        val resource = ClassPathResource("mock-data.sql")
        if (!resource.exists()) {
            println("mock-data.sql 파일을 찾을 수 없습니다.")
            return
        }

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                ScriptUtils.executeSqlScript(connection, resource)
                connection.commit()
                println("Mock Data 생성 완료!")
            } catch (e: Exception) {
                connection.rollback()
                println("Mock Data 생성 실패: ${e.message}")
                throw e
            }
        }
    }

    /**
     * SSE 진행 상황 전송 헬퍼 함수
     */
    private fun sendProgress(emitter: SseEmitter, message: String) {
        Thread.sleep(100) // 메시지가 너무 빨리 전송되지 않도록
        try {
            emitter.send(
                SseEmitter.event()
                    .data(message)
                    .name("progress")
            )
        } catch (e: Exception) {
            // 클라이언트 연결이 끊어진 경우 무시
        }
    }
}
