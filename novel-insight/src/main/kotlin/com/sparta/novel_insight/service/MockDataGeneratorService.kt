package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.*
import com.sparta.novel_insight.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

// # MockDataGeneratorService
//
// 테스트 및 데모용 Mock 데이터 생성 서비스입니다.
//
// ## WHY Mock 데이터가 필요한가?
//
// ### 문제: 실제 데이터가 부족
// - 회원 수 적음 → 통계 신뢰도 낮음
// - 소설만 많고 에피소드/리뷰 부족 → 대시보드 텅 빔
// - 읽기 로그 없음 → Phase 6 기능 테스트 불가
// - 다양한 패턴 부족 → 엣지 케이스 검증 불가
//
// ### 해결: 실무 시나리오 Mock 데이터
// - **현실적인 분포**: 인기 소설 vs 일반 소설
// - **다양한 패턴**: 완독, 중간 이탈, 초반 이탈
// - **시계열 데이터**: 최근 30일간의 트렌드
// - **대용량 데이터**: 성능 벤치마크용
//
// ## 생성 데이터
//
// ### 1. Member (회원)
// - 작가 50명: 활발한 작가 20명, 일반 작가 30명
// - 독자 1000명: 열성 독자 200명, 일반 독자 800명
//
// ### 2. Novel (소설)
// - 150편: 인기 소설 30편, 일반 소설 120편
// - 장르: 판타지, 로맨스, 무협, 현대물 등
//
// ### 3. Episode (에피소드)
// - 소설당 20-50개 (총 ~5,000개)
// - 인기 소설은 에피소드 많음
//
// ### 4. Review (리뷰)
// - 50,000개: 다양한 감성 (긍정 60%, 부정 20%, 중립 20%)
// - ReviewAnalysis도 함께 생성
//
// ### 5. EpisodeReadLog (읽기 로그)
// - 500,000개: 최근 30일간
// - 패턴: 완독(40%), 중간 이탈(30%), 초반 이탈(30%)
// - 디바이스: Mobile(60%), Desktop(30%), Tablet(10%)
//
// ## 사용 예시
//
// ```bash
// # 전체 Mock 데이터 생성
// POST /api/mock/generate-all
//
// # 특정 데이터만 생성
// POST /api/mock/generate-members?count=100
// POST /api/mock/generate-novels?count=50
// POST /api/mock/generate-read-logs?count=10000
// ```
@Service
class MockDataGeneratorService(
    internal val memberRepository: MemberRepository,
    internal val novelRepository: NovelRepository,
    internal val episodeRepository: EpisodeRepository,
    internal val reviewRepository: ReviewRepository,
    private val reviewAnalysisRepository: ReviewAnalysisRepository,
    internal val episodeReadLogRepository: EpisodeReadLogRepository
) {

    companion object {
        // 한국 성씨 TOP 20
        private val KOREAN_SURNAMES = listOf(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍"
        )

        // 한국 이름 음절
        private val KOREAN_NAME_SYLLABLES = listOf(
            "민", "서", "지", "수", "하", "은", "현", "우", "준", "윤",
            "도", "시", "아", "연", "재", "혁", "영", "선", "미", "경"
        )

        // 소설 장르
        private val GENRES = listOf(
            "판타지", "현대판타지", "무협", "로맨스", "로맨스판타지",
            "BL", "미스터리", "스릴러", "SF", "역사", "퓨전사극"
        )

        // 소설 제목 템플릿
        private val NOVEL_TITLE_TEMPLATES = listOf(
            "{형용사} {명사}",
            "{명사}의 {명사}",
            "{동사}하는 {명사}",
            "{명사}가 된 {명사}",
            "환생한 {명사}",
            "회귀한 {명사}",
            "전생의 {명사}",
            "{명사}의 귀환"
        )

        private val ADJECTIVES = listOf("찬란한", "어두운", "빛나는", "숨겨진", "금지된", "전설의", "불멸의", "잊혀진")
        private val NOUNS = listOf("검성", "마법사", "황제", "기사", "암살자", "영주", "현자", "용사", "악당", "공작")
        private val VERBS = listOf("지배", "정복", "구원", "복수", "사랑", "수호", "파괴", "창조")

        // 에피소드 제목 패턴
        private val EPISODE_PATTERNS = listOf(
            "{n}화 - 운명적인 만남",
            "{n}화 - 첫 번째 시련",
            "{n}화 - 숨겨진 진실",
            "{n}화 - 각성",
            "{n}화 - 반전의 시작",
            "{n}화 - 위기의 순간",
            "{n}화 - 새로운 힘",
            "{n}화 - 결전의 서막"
        )

        // 리뷰 템플릿
        private val POSITIVE_REVIEWS = listOf(
            "정말 재미있어요! 다음 화가 너무 기대됩니다.",
            "스토리 전개가 완벽합니다. 강추!",
            "캐릭터가 매력적이고 몰입도가 높아요.",
            "이런 소설을 기다렸어요. 대박!",
            "전개가 빠르고 지루할 틈이 없네요.",
            "작가님 필력이 대단하십니다.",
            "킹갓작가님 감사합니다! 핵꿀잼!",
            "이 정도 퀄리티면 유료로 봐도 아깝지 않아요."
        )

        private val NEGATIVE_REVIEWS = listOf(
            "전개가 너무 느려요. 지루합니다.",
            "주인공이 공감이 안 가네요.",
            "스토리가 너무 뻔해요.",
            "설정 구멍이 많아서 몰입이 안 됩니다.",
            "캐릭터가 너무 평면적이에요.",
            "작가님 좀 더 신경 써주세요.",
            "앞뒤가 안 맞는 전개가 너무 많아요.",
            "기대했는데 실망이네요."
        )

        private val NEUTRAL_REVIEWS = listOf(
            "나쁘지 않은데 특별한 건 없네요.",
            "그냥 시간 때우기 좋은 소설입니다.",
            "평범한 스토리예요.",
            "무난하게 읽을 만합니다.",
            "기대치를 낮추면 볼 만해요.",
            "호불호가 갈릴 것 같아요.",
            "아직 초반이라 판단하기 이르네요.",
            "더 지켜봐야 할 것 같습니다."
        )
    }

    // 전체 Mock 데이터 생성
    //
    // ## 실행 순서 (FK 제약조건 고려)
    //
    // 1. Member 생성 (작가 + 독자)
    // 2. Novel 생성 (FK: authorId)
    // 3. Episode 생성 (FK: novelId)
    // 4. Review + ReviewAnalysis 생성 (FK: userId, novelId)
    // 5. EpisodeReadLog 생성 (FK: userId, episodeId, novelId)
    //
    // @param clearExisting 기존 데이터 삭제 여부
    // @return 생성된 데이터 개수 요약
    @Transactional
    fun generateAllMockData(clearExisting: Boolean = false): MockDataSummary {
        if (clearExisting) {
            clearAllData()
        }

        val members = generateMembers(authorCount = 50, readerCount = 1000)
        val novels = generateNovels(count = 150, authors = members.filter { it.role == MemberRole.AUTHOR })
        val episodes = generateEpisodes(novels = novels)
        val reviews = generateReviews(count = 50000, readers = members.filter { it.role == MemberRole.READER }, novels = novels)
        val readLogs = generateReadLogs(count = 500000, readers = members.filter { it.role == MemberRole.READER }, episodes = episodes, days = 30)

        return MockDataSummary(
            members = members.size,
            novels = novels.size,
            episodes = episodes.size,
            reviews = reviews.size,
            readLogs = readLogs.size
        )
    }

    // 기존 데이터 전체 삭제
    //
    // WHY 순서가 중요?
    // - FK 제약조건 역순으로 삭제해야 함
    @Transactional
    fun clearAllData() {
        episodeReadLogRepository.deleteAll()
        reviewAnalysisRepository.deleteAll()
        reviewRepository.deleteAll()
        episodeRepository.deleteAll()
        novelRepository.deleteAll()
        memberRepository.deleteAll()
    }

    // 회원 데이터 생성
    //
    // @param authorCount 작가 수
    // @param readerCount 독자 수
    // @return 생성된 회원 리스트
    @Transactional
    fun generateMembers(authorCount: Int = 50, readerCount: Int = 1000): List<Member> {
        val members = mutableListOf<Member>()

        // 작가 생성
        repeat(authorCount) { i ->
            val member = Member(
                email = "author${i + 1}@novel.com",
                password = "password123!",
                nickname = generateKoreanName() + " 작가",
                role = MemberRole.AUTHOR,
                createdAt = LocalDateTime.now().minusDays(Random.nextLong(365))
            )
            members.add(member)
        }

        // 독자 생성
        repeat(readerCount) { i ->
            val member = Member(
                email = "reader${i + 1}@novel.com",
                password = "password123!",
                nickname = generateKoreanName(),
                role = MemberRole.READER,
                createdAt = LocalDateTime.now().minusDays(Random.nextLong(365))
            )
            members.add(member)
        }

        return memberRepository.saveAll(members)
    }

    // 소설 데이터 생성
    //
    // @param count 소설 수
    // @param authors 작가 리스트
    // @return 생성된 소설 리스트
    @Transactional
    fun generateNovels(count: Int = 150, authors: List<Member>): List<Novel> {
        val novels = mutableListOf<Novel>()

        repeat(count) { i ->
            val author = authors.random()
            val novel = Novel(
                title = generateNovelTitle(),
                author = author.nickname,
                authorId = author.id!!,
                description = "흥미진진한 ${GENRES.random()} 소설입니다.",
                genre = GENRES.random(),
                status = if (Random.nextDouble() < 0.8) NovelStatus.ONGOING else NovelStatus.COMPLETED,
                createdAt = LocalDateTime.now().minusDays(Random.nextLong(365))
            )
            novels.add(novel)
        }

        return novelRepository.saveAll(novels)
    }

    // 에피소드 데이터 생성
    //
    // 인기 소설은 에피소드가 많고, 일반 소설은 적음
    //
    // @param novels 소설 리스트
    // @return 생성된 에피소드 리스트
    @Transactional
    fun generateEpisodes(novels: List<Novel>): List<Episode> {
        val episodes = mutableListOf<Episode>()

        novels.forEach { novel ->
            // 인기 소설 여부 랜덤 결정 (20%는 인기 소설)
            val isPopular = Random.nextDouble() < 0.2
            val episodeCount = if (isPopular) Random.nextInt(40, 80) else Random.nextInt(10, 30)

            repeat(episodeCount) { i ->
                val isFree = i < 3 // 처음 3개는 무료
                val episode = Episode(
                    title = generateEpisodeTitle(i + 1),
                    content = "에피소드 내용입니다. ".repeat(Random.nextInt(50, 200)),
                    episodeNumber = i + 1,
                    price = if (isFree) 0 else Random.nextInt(100, 500),
                    isFree = isFree,
                    publishedAt = novel.createdAt.plusDays(i.toLong()),
                    createdAt = novel.createdAt.plusDays(i.toLong()),
                    novel = novel
                )
                episodes.add(episode)
            }
        }

        return episodeRepository.saveAll(episodes)
    }

    // 리뷰 데이터 생성 (ReviewAnalysis도 함께 생성)
    //
    // @param count 리뷰 수
    // @param readers 독자 리스트
    // @param novels 소설 리스트
    // @return 생성된 리뷰 리스트
    @Transactional
    fun generateReviews(count: Int = 50000, readers: List<Member>, novels: List<Novel>): List<Review> {
        val reviews = mutableListOf<Review>()
        val analyses = mutableListOf<ReviewAnalysis>()

        repeat(count) { i ->
            val reader = readers.random()
            val novel = novels.random()

            // 감성 분포: 긍정 60%, 부정 20%, 중립 20%
            val sentimentRoll = Random.nextDouble()
            val (content, sentiment, score) = when {
                sentimentRoll < 0.6 -> Triple(
                    POSITIVE_REVIEWS.random(),
                    "POSITIVE",
                    Random.nextDouble(70.0, 100.0)
                )
                sentimentRoll < 0.8 -> Triple(
                    NEGATIVE_REVIEWS.random(),
                    "NEGATIVE",
                    Random.nextDouble(0.0, 30.0)
                )
                else -> Triple(
                    NEUTRAL_REVIEWS.random(),
                    "NEUTRAL",
                    Random.nextDouble(30.0, 70.0)
                )
            }

            val rating = BigDecimal(((score / 20).toInt() + 1).coerceIn(1, 5))
            val review = Review(
                content = content,
                userId = reader.id!!,
                novelId = novel.id!!,
                rating = rating,
                createdAt = LocalDateTime.now().minusDays(Random.nextLong(30)),
                novel = novel
            )
            reviews.add(review)

            // ReviewAnalysis 생성
            val analysis = ReviewAnalysis(
                novelId = novel.id!!,
                authorId = novel.authorId,
                content = content,
                sentiment = sentiment,
                score = score,
                positiveCount = if (sentiment == "POSITIVE") Random.nextInt(1, 5) else 0,
                negativeCount = if (sentiment == "NEGATIVE") Random.nextInt(1, 5) else 0,
                analyzedAt = review.createdAt.plusMinutes(Random.nextLong(1, 10))
            )
            analyses.add(analysis)

            // 배치 저장 (1000개씩)
            if (reviews.size % 1000 == 0) {
                reviewRepository.saveAll(reviews)
                reviewAnalysisRepository.saveAll(analyses)
                reviews.clear()
                analyses.clear()
            }
        }

        // 남은 데이터 저장
        if (reviews.isNotEmpty()) {
            reviewRepository.saveAll(reviews)
            reviewAnalysisRepository.saveAll(analyses)
        }

        return reviewRepository.findAll().toList()
    }

    // 읽기 로그 데이터 생성 (Phase 6)
    //
    // ## 현실적인 패턴 생성
    //
    // 1. **완독 (40%)**: 90-100% 스크롤
    // 2. **중간 이탈 (30%)**: 40-80% 스크롤
    // 3. **초반 이탈 (30%)**: 5-30% 스크롤
    //
    // @param count 로그 수
    // @param readers 독자 리스트
    // @param episodes 에피소드 리스트
    // @param days 최근 N일간 데이터
    // @return 생성된 로그 리스트
    @Transactional
    fun generateReadLogs(
        count: Int = 500000,
        readers: List<Member>,
        episodes: List<Episode>,
        days: Int = 30
    ): List<EpisodeReadLog> {
        val logs = mutableListOf<EpisodeReadLog>()

        repeat(count) { i ->
            val reader = if (Random.nextDouble() < 0.7) readers.random() else null // 30%는 비회원
            val episode = episodes.random()

            // 읽기 패턴 결정
            val patternRoll = Random.nextDouble()
            val (scrollDepth, isCompleted, dropReason) = when {
                // 완독 (40%)
                patternRoll < 0.4 -> Triple(
                    BigDecimal(Random.nextDouble(90.0, 100.0)).setScale(2, java.math.RoundingMode.HALF_UP),
                    true,
                    null
                )
                // 중간 이탈 (30%)
                patternRoll < 0.7 -> Triple(
                    BigDecimal(Random.nextDouble(40.0, 80.0)).setScale(2, java.math.RoundingMode.HALF_UP),
                    false,
                    DropReason.USER_ACTION
                )
                // 초반 이탈 (30%)
                else -> Triple(
                    BigDecimal(Random.nextDouble(5.0, 30.0)).setScale(2, java.math.RoundingMode.HALF_UP),
                    false,
                    DropReason.USER_ACTION
                )
            }

            // 디바이스 분포: Mobile 60%, Desktop 30%, Tablet 10%
            val deviceRoll = Random.nextDouble()
            val deviceType = when {
                deviceRoll < 0.6 -> DeviceType.MOBILE
                deviceRoll < 0.9 -> DeviceType.DESKTOP
                else -> DeviceType.TABLET
            }

            val startedAt = LocalDateTime.now()
                .minusDays(Random.nextLong(days.toLong()))
                .minusHours(Random.nextLong(24))

            val readingDuration = Random.nextInt(30, 600) // 30초 ~ 10분

            val log = EpisodeReadLog(
                userId = reader?.id,
                episodeId = episode.id!!,
                novelId = episode.novel.id!!,
                sessionId = "session-${Random.nextLong(1000000, 9999999)}",
                startedAt = startedAt,
                endedAt = startedAt.plusSeconds(readingDuration.toLong()),
                readingDurationSeconds = readingDuration,
                maxScrollDepth = scrollDepth,
                isCompleted = isCompleted,
                deviceType = deviceType,
                referrer = if (Random.nextDouble() < 0.3) "https://google.com" else null,
                droppedAtPercentage = if (!isCompleted) scrollDepth else null,
                dropReason = dropReason
            )
            logs.add(log)

            // 배치 저장 (5000개씩)
            if (logs.size % 5000 == 0) {
                episodeReadLogRepository.saveAll(logs)
                logs.clear()
                println("Progress: ${i + 1} / $count logs generated")
            }
        }

        // 남은 데이터 저장
        if (logs.isNotEmpty()) {
            episodeReadLogRepository.saveAll(logs)
        }

        return episodeReadLogRepository.findAll().toList()
    }

    // ========== Helper Functions ==========

    private fun generateKoreanName(): String {
        val surname = KOREAN_SURNAMES.random()
        val name = KOREAN_NAME_SYLLABLES.random() + KOREAN_NAME_SYLLABLES.random()
        return surname + name
    }

    private fun generateNovelTitle(): String {
        val template = NOVEL_TITLE_TEMPLATES.random()
        return template
            .replace("{형용사}", ADJECTIVES.random())
            .replace("{명사}", NOUNS.random())
            .replace("{동사}", VERBS.random())
    }

    private fun generateEpisodeTitle(number: Int): String {
        val pattern = EPISODE_PATTERNS.random()
        return pattern.replace("{n}", number.toString())
    }
}

// Mock 데이터 생성 요약
data class MockDataSummary(
    val members: Int,
    val novels: Int,
    val episodes: Int,
    val reviews: Int,
    val readLogs: Int
)

// MemberRole enum은 domain 패키지에 정의되어 있습니다
