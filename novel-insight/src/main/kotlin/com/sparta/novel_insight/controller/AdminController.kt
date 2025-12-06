package com.sparta.novel_insight.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * 관리자 전용 컨트롤러
 * Mock Data 생성 등 개발/테스트용 기능 제공
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    @Autowired private val jdbcTemplate: JdbcTemplate
) {

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
    @GetMapping("/generate-mock-data-stream", produces = ["text/event-stream;charset=UTF-8"])
    fun generateMockDataStream(response: jakarta.servlet.http.HttpServletResponse): SseEmitter {
        response.characterEncoding = "UTF-8"
        response.contentType = "text/event-stream;charset=UTF-8"

        val emitter = SseEmitter(900000L) // 15분 타임아웃

        thread {
            try {
                sendProgress(emitter, "🚀 Mock Data 생성 시작...")
                sendProgress(emitter, "🗄️ 데이터베이스 연결 중...")

                val startTime = System.currentTimeMillis()

                // 1. 기존 데이터 삭제
                sendProgress(emitter, "")
                sendProgress(emitter, "📊 단계별 진행 상황:")
                sendProgress(emitter, "1️⃣ 기존 데이터 삭제 중...")

                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0")
                jdbcTemplate.execute("TRUNCATE TABLE episode_read_log")
                jdbcTemplate.execute("TRUNCATE TABLE review_analysis")
                jdbcTemplate.execute("TRUNCATE TABLE review")
                jdbcTemplate.execute("TRUNCATE TABLE episode_comment")
                jdbcTemplate.execute("TRUNCATE TABLE episode")
                jdbcTemplate.execute("TRUNCATE TABLE novel")
                jdbcTemplate.execute("TRUNCATE TABLE member")
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1")

                sendProgress(emitter, "   ✅ 기존 데이터 삭제 완료")

                // 2. 작가 50명 생성
                sendProgress(emitter, "2️⃣ 작가 50명 생성 중...")
                generateAuthors()
                sendProgress(emitter, "   ✅ 작가 50명 생성 완료")

                // 3. 독자 1,000명 생성
                sendProgress(emitter, "3️⃣ 독자 1,000명 생성 중...")
                generateReaders(emitter)
                sendProgress(emitter, "   ✅ 독자 1,000명 생성 완료")

                // 4. 소설 150편 생성
                sendProgress(emitter, "4️⃣ 소설 150편 생성 중...")
                generateNovels()
                sendProgress(emitter, "   ✅ 소설 150편 생성 완료")

                // 5. 에피소드 생성
                sendProgress(emitter, "5️⃣ 에피소드 생성 중...")
                val episodeCount = generateEpisodes(emitter)
                sendProgress(emitter, "   ✅ 에피소드 ${episodeCount}개 생성 완료")

                // 6. 리뷰 50,000개 생성
                sendProgress(emitter, "6️⃣ 리뷰 50,000개 생성 중...")
                generateReviews(emitter)
                sendProgress(emitter, "   ✅ 리뷰 50,000개 생성 완료")

                // 7. 댓글 100,000개 생성
                sendProgress(emitter, "7️⃣ 댓글 100,000개 생성 중...")
                generateComments(emitter)
                sendProgress(emitter, "   ✅ 댓글 100,000개 생성 완료")

                // 8. 읽기 로그 500,000개 생성
                sendProgress(emitter, "8️⃣ 읽기 로그 500,000개 생성 중...")
                generateReadLogs(emitter)
                sendProgress(emitter, "   ✅ 읽기 로그 500,000개 생성 완료")

                val endTime = System.currentTimeMillis()
                val durationSeconds = (endTime - startTime) / 1000

                // 데이터 개수 확인
                sendProgress(emitter, "")
                sendProgress(emitter, "📊 생성된 데이터 확인 중...")

                val counts = mapOf(
                    "members" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member", Long::class.java) ?: 0),
                    "novels" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM novel", Long::class.java) ?: 0),
                    "episodes" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode", Long::class.java) ?: 0),
                    "reviews" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review", Long::class.java) ?: 0),
                    "comments" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode_comment", Long::class.java) ?: 0),
                    "readLogs" to (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode_read_log", Long::class.java) ?: 0)
                )

                sendProgress(emitter, "")
                sendProgress(emitter, "=" * 50)
                sendProgress(emitter, "🎉 Mock Data 생성 완료!")
                sendProgress(emitter, "=" * 50)
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
     * 작가 50명 생성
     */
    private fun generateAuthors() {
        val password = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy" // "password123"

        val authorNames = listOf(
            "김민서", "이현우", "박지연", "최서준", "정유진", "강동욱", "윤소희", "장민재", "임서연", "한지우",
            "오준혁", "신예은", "권태희", "송지훈", "배수아", "류정민", "홍다은", "고은성", "문채원", "서준호",
            "조민지", "황서우", "양하윤", "노시우", "구도윤", "손수빈", "안재현", "탁지안", "피예준", "하지민",
            "백서윤", "남우진", "도연우", "표유나", "마지호", "진시은", "석민준", "옥지율", "추아린", "설건우",
            "곽도경", "복지우", "방하은", "편지안", "변서윤", "사민석", "갈수현", "주하율", "목시후", "감나은"
        )

        authorNames.forEachIndexed { index, name ->
            val authorNum = index + 1
            jdbcTemplate.update(
                """
                INSERT INTO member (email, password, nickname, role, created_at)
                VALUES (?, ?, ?, 'AUTHOR', NOW())
                """,
                "author${authorNum}@novel.com",
                password,
                "${name} 작가"
            )
        }
    }

    /**
     * 독자 1,000명 생성 (배치 처리)
     */
    private fun generateReaders(emitter: SseEmitter) {
        val password = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
        val batchSize = 100

        for (batch in 1..10) {
            val values = mutableListOf<String>()
            for (i in 1..batchSize) {
                val readerNum = (batch - 1) * batchSize + i
                values.add("('reader${readerNum}@novel.com', '$password', '독자${readerNum}', 'READER', NOW())")
            }

            jdbcTemplate.execute(
                """
                INSERT INTO member (email, password, nickname, role, created_at)
                VALUES ${values.joinToString(", ")}
                """
            )

            if (batch % 2 == 0) {
                sendProgress(emitter, "   - ${batch * batchSize}명 생성됨...")
            }
        }
    }

    /**
     * 소설 150편 생성 (작가당 3편)
     */
    private fun generateNovels() {
        val genres = listOf("판타지", "로맨스", "무협", "현대물", "SF")
        val statuses = listOf("ONGOING", "ONGOING", "ONGOING", "COMPLETED")

        val novelTitles = listOf(
            "별빛 아래의 약속", "잃어버린 왕국의 전설", "시간을 달리는 소녀", "마법사의 귀환", "검의 노래",
            "영원한 사랑의 서약", "어둠 속의 빛", "운명의 수레바퀴", "천년의 약속", "신들의 전쟁"
        )

        for (authorId in 1..50) {
            for (novelNum in 1..3) {
                val title = "${novelTitles[(authorId + novelNum) % novelTitles.size]} ${authorId}-${novelNum}"
                val genre = genres[(authorId + novelNum) % genres.size]
                val status = statuses[(authorId + novelNum) % statuses.size]
                val viewCount = (1000..100000).random()

                jdbcTemplate.update(
                    """
                    INSERT INTO novel (title, author, author_id, description, genre, status, view_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, DATE_SUB(NOW(), INTERVAL ? DAY))
                    """,
                    title,
                    "작가${authorId}",
                    authorId,
                    "${title}의 흥미진진한 이야기가 펼쳐집니다.",
                    genre,
                    status,
                    viewCount,
                    (1..365).random()
                )
            }
        }
    }

    /**
     * 에피소드 생성 (소설당 30-40개)
     */
    private fun generateEpisodes(emitter: SseEmitter): Int {
        var totalEpisodes = 0

        for (novelId in 1..150) {
            val episodeCount = (30..40).random()
            val values = mutableListOf<String>()

            for (ep in 1..episodeCount) {
                val isFree = if (ep <= 3) 1 else 0
                val price = if (ep <= 3) 0 else (100..500).random()
                val viewCount = (500..50000).random()
                val purchaseCount = (50..5000).random()

                values.add("('${ep}화: 에피소드 제목', '에피소드 본문 내용...', $ep, $price, $isFree, $viewCount, $purchaseCount, DATE_SUB(NOW(), INTERVAL ${(1..180).random()} DAY), NOW(), $novelId)")
            }

            jdbcTemplate.execute(
                """
                INSERT INTO episode (title, content, episode_number, price, is_free, view_count, purchase_count, published_at, created_at, novel_id)
                VALUES ${values.joinToString(", ")}
                """
            )

            totalEpisodes += episodeCount

            if (novelId % 30 == 0) {
                sendProgress(emitter, "   - ${novelId}/150 소설 처리됨 (에피소드 ${totalEpisodes}개)...")
            }
        }

        return totalEpisodes
    }

    /**
     * 리뷰 50,000개 생성
     */
    private fun generateReviews(emitter: SseEmitter) {
        val reviewContents = listOf(
            "정말 재미있게 읽었습니다!", "스토리 전개가 탄탄하네요.", "다음 화가 기대됩니다!",
            "작가님 응원합니다!", "캐릭터가 매력적이에요.", "몰입도가 정말 좋습니다.",
            "최고의 소설입니다!", "강추합니다!", "후회 없는 선택이었어요.", "명작이네요!"
        )

        val batchSize = 1000
        val totalReviews = 50000

        for (batch in 1..(totalReviews / batchSize)) {
            val values = mutableListOf<String>()

            for (i in 1..batchSize) {
                val novelId = (1..150).random()
                val userId = 51 + (1..1000).random() // 독자 ID
                val rating = (30..50).random() / 10.0 // 3.0 ~ 5.0
                val content = reviewContents.random()

                values.add("('$content', $userId, $novelId, $rating, DATE_SUB(NOW(), INTERVAL ${(1..90).random()} DAY))")
            }

            jdbcTemplate.execute(
                """
                INSERT INTO review (content, user_id, novel_id, rating, created_at)
                VALUES ${values.joinToString(", ")}
                """
            )

            if (batch % 10 == 0) {
                sendProgress(emitter, "   - ${batch * batchSize}/${totalReviews} 리뷰 생성됨...")
            }
        }
    }

    /**
     * 댓글 100,000개 생성
     */
    private fun generateComments(emitter: SseEmitter) {
        val commentContents = listOf(
            "재밌어요!", "다음 화 빨리 주세요!", "감동적이네요.", "최고입니다!",
            "작가님 사랑해요!", "완전 몰입했어요.", "이 장면 최고!", "ㅠㅠ 감동",
            "대박!", "계속 읽게 되네요.", "명장면이에요!", "와... 소름"
        )

        val batchSize = 1000
        val totalComments = 100000

        // 전체 에피소드 수 조회
        val totalEpisodes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode", Int::class.java) ?: 0

        for (batch in 1..(totalComments / batchSize)) {
            val values = mutableListOf<String>()

            for (i in 1..batchSize) {
                val episodeId = (1..totalEpisodes).random()
                val readerId = 51 + (1..1000).random()
                val content = commentContents.random()
                val writerName = "독자${readerId - 50}"

                values.add("('$content', '$writerName', $readerId, $episodeId, 0, 0, DATE_SUB(NOW(), INTERVAL ${(1..60).random()} DAY))")
            }

            jdbcTemplate.execute(
                """
                INSERT INTO episode_comment (content, writer_name, writer_id, episode_id, like_count, dislike_count, created_at)
                VALUES ${values.joinToString(", ")}
                """
            )

            if (batch % 20 == 0) {
                sendProgress(emitter, "   - ${batch * batchSize}/${totalComments} 댓글 생성됨...")
            }
        }
    }

    /**
     * 읽기 로그 500,000개 생성
     */
    private fun generateReadLogs(emitter: SseEmitter) {
        val batchSize = 2000
        val totalLogs = 500000

        val totalEpisodes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM episode", Int::class.java) ?: 0

        // 각 에피소드의 novel_id를 미리 조회
        val episodeNovelMap = mutableMapOf<Int, Int>()
        jdbcTemplate.query("SELECT id, novel_id FROM episode") { rs ->
            episodeNovelMap[rs.getInt("id")] = rs.getInt("novel_id")
        }

        val deviceTypes = listOf("MOBILE", "TABLET", "DESKTOP")

        for (batch in 1..(totalLogs / batchSize)) {
            val values = mutableListOf<String>()

            for (i in 1..batchSize) {
                val episodeId = (1..totalEpisodes).random()
                val novelId = episodeNovelMap[episodeId] ?: 1
                val userId = 51 + (1..1000).random()
                val maxScrollDepth = (0..100).random()
                val readingDurationSeconds = (60..3600).random()
                val isCompleted = if (maxScrollDepth >= 90) 1 else 0
                val sessionId = "session_${userId}_${System.currentTimeMillis()}_${i}"
                val deviceType = deviceTypes.random()
                val daysAgo = (1..30).random()

                values.add("($userId, $episodeId, $novelId, DATE_SUB(NOW(), INTERVAL $daysAgo DAY), NULL, $readingDurationSeconds, $maxScrollDepth, $isCompleted, '$sessionId', '$deviceType', DATE_SUB(NOW(), INTERVAL $daysAgo DAY))")
            }

            jdbcTemplate.execute(
                """
                INSERT INTO episode_read_log (user_id, episode_id, novel_id, started_at, ended_at, reading_duration_seconds, max_scroll_depth, is_completed, session_id, device_type, created_at)
                VALUES ${values.joinToString(", ")}
                """
            )

            if (batch % 25 == 0) {
                sendProgress(emitter, "   - ${batch * batchSize}/${totalLogs} 읽기 로그 생성됨...")
            }
        }
    }

    /**
     * SSE 진행 상황 전송 헬퍼 함수
     */
    private fun sendProgress(emitter: SseEmitter, message: String) {
        Thread.sleep(100)
        try {
            // UTF-8로 명시적으로 인코딩
            val utf8Message = String(message.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
            emitter.send(
                SseEmitter.event()
                    .data(utf8Message)
                    .name("progress")
            )
        } catch (e: Exception) {
            // 클라이언트 연결이 끊어진 경우 무시
        }
    }

    private operator fun String.times(n: Int) = this.repeat(n)
}
