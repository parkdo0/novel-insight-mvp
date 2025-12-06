package com.sparta.novel_insight.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import javax.sql.DataSource

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
     * Mock Data 생성 API
     *
     * 사용법 (브라우저에서 바로 실행 가능!):
     * http://localhost:8080/api/admin/generate-mock-data
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
     *
     * @return 성공 메시지 및 생성된 데이터 개수
     */
    @PostMapping("/generate-mock-data")
    @org.springframework.web.bind.annotation.GetMapping("/generate-mock-data")
    fun generateMockData(): ResponseEntity<Map<String, Any>> {
        return try {
            val jdbcTemplate = JdbcTemplate(dataSource)

            // mock-data.sql 파일 읽기
            val sqlFile = File("/home/user/novel-insight-mvp/mock-data.sql")

            if (!sqlFile.exists()) {
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "success" to false,
                        "message" to "mock-data.sql 파일을 찾을 수 없습니다.",
                        "path" to sqlFile.absolutePath
                    )
                )
            }

            val sqlScript = sqlFile.readText()

            // SQL 스크립트를 세미콜론으로 분리하여 각 문장 실행
            // DELIMITER 처리를 위해 특별한 파싱 필요
            val statements = parseSqlScript(sqlScript)

            var executedCount = 0
            val startTime = System.currentTimeMillis()

            // 각 SQL 문 실행
            statements.forEach { sql ->
                if (sql.isNotBlank()) {
                    try {
                        jdbcTemplate.execute(sql)
                        executedCount++
                    } catch (e: Exception) {
                        // 프로시저 DROP 실패 등은 무시
                        if (!sql.contains("DROP PROCEDURE")) {
                            println("SQL 실행 오류 (무시됨): ${e.message}")
                        }
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000

            // 데이터 개수 확인
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
                    "executedStatements" to executedCount,
                    "durationSeconds" to durationSeconds,
                    "data" to counts
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
     * SQL 스크립트 파싱
     * DELIMITER 변경을 고려하여 파싱
     */
    private fun parseSqlScript(script: String): List<String> {
        val statements = mutableListOf<String>()
        var currentStatement = StringBuilder()
        var currentDelimiter = ";"
        var inDelimiterBlock = false

        script.lines().forEach { line ->
            val trimmedLine = line.trim()

            when {
                // DELIMITER 변경
                trimmedLine.startsWith("DELIMITER") -> {
                    val newDelimiter = trimmedLine.substring(9).trim()
                    if (newDelimiter == "$$") {
                        inDelimiterBlock = true
                        currentDelimiter = "$$"
                    } else {
                        inDelimiterBlock = false
                        currentDelimiter = ";"

                        // 이전 문장 저장
                        if (currentStatement.isNotBlank()) {
                            statements.add(currentStatement.toString().trim())
                            currentStatement = StringBuilder()
                        }
                    }
                }
                // 주석 무시
                trimmedLine.startsWith("--") || trimmedLine.startsWith("#") -> {
                    // 주석은 무시
                }
                // 빈 줄 무시
                trimmedLine.isEmpty() -> {
                    // 빈 줄 무시
                }
                // SQL 문장 추가
                else -> {
                    currentStatement.append(line).append("\n")

                    // Delimiter 체크
                    if (trimmedLine.endsWith(currentDelimiter)) {
                        val sql = currentStatement.toString()
                            .removeSuffix(currentDelimiter)
                            .trim()

                        if (sql.isNotBlank()) {
                            statements.add(sql)
                        }
                        currentStatement = StringBuilder()
                    }
                }
            }
        }

        // 마지막 문장 처리
        if (currentStatement.isNotBlank()) {
            statements.add(currentStatement.toString().trim())
        }

        return statements
    }
}
