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
    internal val episodeReadLogRepository: EpisodeReadLogRepository,
    private val purchaseRepository: PurchaseRepository,
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val episodeCommentRepository: EpisodeCommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val notificationRepository: NotificationRepository,
    private val authorDashboardCacheRepository: AuthorDashboardCacheRepository,
    private val sectionReplayLogRepository: SectionReplayLogRepository,
    private val dropOffPointRepository: DropOffPointRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val entityManager: jakarta.persistence.EntityManager,
    private val passwordEncoder: org.springframework.security.crypto.password.PasswordEncoder
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

        // 에피소드 제목 패턴 - 스토리 진행에 따라 다르게 적용
        private val EPISODE_TITLE_OPENING = listOf(
            "프롤로그: 운명의 시작",
            "예언된 자",
            "낯선 세계",
            "각성의 전조",
            "운명의 장난"
        )

        private val EPISODE_TITLE_DEVELOPMENT = listOf(
            "숨겨진 진실",
            "의문의 단서",
            "예상치 못한 만남",
            "성장의 징후",
            "새로운 동료",
            "시련의 시작",
            "위기의 징조",
            "불길한 예감",
            "비밀의 문",
            "결의를 다지며",
            "스승의 가르침",
            "수련의 길",
            "첫 번째 관문",
            "의심과 믿음",
            "전진하는 발걸음"
        )

        private val EPISODE_TITLE_CLIMAX = listOf(
            "결전의 서막",
            "폭풍 전야",
            "최후의 선택",
            "반격의 시간",
            "진정한 힘",
            "깨어난 능력",
            "운명의 대결",
            "숨겨진 카드",
            "역전의 기회",
            "최강자의 등장"
        )

        private val EPISODE_TITLE_ENDING = listOf(
            "여명이 밝아올 때",
            "새로운 시작",
            "에필로그",
            "끝나지 않은 이야기",
            "다음을 향해"
        )

        // 문단 템플릿 - 장르별, 상황별로 다양하게
        private val SCENE_DESCRIPTIONS = listOf(
            "창밖으로 비치는 햇살이 방 안을 환하게 물들였다. 먼지 입자들이 빛을 타고 춤추듯 흩날리는 모습이 마치 작은 요정들의 무도회 같았다.",
            "바람이 불어왔다. 차갑지도 따뜻하지도 않은, 그저 존재를 알리듯 스치는 바람이었다. 그 바람에 실려 온 향기가 코끝을 간질였다.",
            "어둠이 깊어지는 밤, 별빛만이 유일한 안내자였다. 달은 구름 사이로 숨었다 나타나기를 반복하며 묘한 분위기를 연출했다.",
            "고요한 적막이 흘렀다. 아무 소리도 들리지 않는 그 침묵 속에서 심장 소리만이 또렷하게 울려 퍼졌다. 쿵, 쿵, 쿵.",
            "눈앞에 펼쳐진 광경은 상상을 초월했다. 이런 곳이 정말 존재한다니, 살면서 단 한 번도 본 적 없는 절경이었다."
        )

        private val CHARACTER_ACTIONS = listOf(
            "그는 천천히 고개를 들었다. 눈앞에 펼쳐진 광경에 숨이 멎는 듯했다. 이것이 바로 자신이 찾던 것인가. 오랜 여정의 끝에 마침내.",
            "손에 쥔 검이 파르르 떨렸다. 긴장 때문이 아니었다. 두려움도 아니었다. 그것은 기대였다. 곧 시작될 전투에 대한 기대.",
            "눈을 감았다. 어둠 속에서 더 명확하게 들려오는 것들이 있었다. 바람 소리, 풀잎이 스치는 소리, 그리고 멀리서 다가오는 발자국 소리.",
            "주먹을 불끈 쥐었다. 이빨을 악물었다. 이번만큼은 절대로 물러서지 않을 것이다. 지금이 아니면 기회는 영영 오지 않을 테니까.",
            "미소를 지었다. 차갑지도 따뜻하지도 않은, 의미를 알 수 없는 미소였다. 상대방은 그 미소의 의미를 알지 못해 불안해했다."
        )

        private val DIALOGUE_TEMPLATES = listOf(
            "\"이게 대체 무슨 상황이지?\"\n\n그의 물음에 아무도 대답하지 않았다. 모두가 눈앞의 광경에 충격을 받은 것이 분명했다. 침묵이 길어질수록 불안감도 커져갔다.",
            "\"걱정 마. 내가 다 알아서 할 테니까.\"\n\n자신감 넘치는 목소리였다. 하지만 그 말을 하는 당사자조차도 마음속으로는 확신이 없었다. 그저 허세일 뿐이었다.",
            "\"왜 그랬어? 대체 왜?\"\n\n목소리가 갈라졌다. 분노인지 슬픔인지 구분할 수 없는 감정이 터져 나왔다. 대답을 들어야 했다. 반드시.",
            "\"시간이 없어. 지금 바로 출발해야 해.\"\n\n다급한 목소리가 공기를 가르며 퍼져나갔다. 모두가 알았다. 이것이 마지막 기회라는 것을.",
            "\"믿어도 되는 거야?\"\n\n의심 가득한 눈빛이 상대를 향했다. 신뢰는 쉽게 주어지는 것이 아니었다. 특히 이런 상황에서는."
        )

        private val INNER_THOUGHTS = listOf(
            "'이건 분명 덫이야. 하지만 다른 선택지가 없잖아.'\n\n머릿속에서 수많은 생각이 교차했다. 위험을 감수하더라도 앞으로 나아가야 했다. 망설임은 죽음과 같았다.",
            "'강해져야 해. 지금보다 더, 훨씬 더.'\n\n결심은 굳건했다. 아직 부족했다. 더 많은 시련이 필요했다. 그래야만 소중한 것들을 지킬 수 있으니까.",
            "'왜 하필 나인 거지? 수많은 사람들 중에서.'\n\n운명이라는 것을 믿지 않았다. 하지만 지금 상황은 운명이라는 말 외에는 설명할 방법이 없었다.",
            "'포기하면 안 돼. 여기서 포기하면 모든 게 끝이야.'\n\n지친 몸을 이끌고 한 발짝 더 내딛었다. 끝이 보이지 않는 길이었지만, 멈출 수는 없었다.",
            "'이상하다. 분명 뭔가 놓치고 있어.'\n\n직감이 경고를 보내고 있었다. 겉으로 보이는 것이 전부가 아니었다. 더 깊이 파고들어야 했다."
        )

        private val TENSION_SCENES = listOf(
            "심장이 미친 듯이 뛰었다. 입술이 바짝 말랐다. 한 순간의 실수도 용납되지 않는 상황이었다. 모든 감각을 곤두세우고 집중했다.",
            "손에서 식은땀이 흘러내렸다. 근육이 긴장으로 경직되어 갔다. 숨을 참았다. 지금은 어떤 소리도 내서는 안 되었다.",
            "시간이 느리게 흘러가는 것 같았다. 아니, 멈춘 것 같았다. 찰나의 순간이 영원처럼 느껴졌다. 이것이 바로 극한의 집중인가.",
            "피가 끓어올랐다. 온몸의 세포가 활성화되는 것이 느껴졌다. 이 느낌을 알았다. 전투 본능이 깨어나고 있었다.",
            "공포가 엄습해왔다. 다리가 후들거렸다. 하지만 도망칠 수 없었다. 뒤에는 지켜야 할 사람들이 있었으니까."
        )

        private val FANTASY_ELEMENTS = listOf(
            "마나가 소용돌이치기 시작했다. 공기 중의 원소들이 불안정하게 요동쳤다. 곧 무언가 일어날 것 같은 예감이 들었다.",
            "고대의 룬 문자가 빛을 발했다. 오래전 잊혀진 언어가 되살아나듯 공명했다. 이것이 바로 전설에서 말하던 그것인가.",
            "공간이 일그러졌다. 시공간의 법칙이 무시당하는 듯한 기이한 현상이었다. 눈앞의 광경이 믿기지 않았다.",
            "내면의 힘이 깨어나고 있었다. 잠들어 있던 잠재력이 마침내 눈을 뜨는 순간이었다. 온몸을 타고 흐르는 새로운 에너지.",
            "차원의 틈새로 무언가가 비쳐 보였다. 이 세계가 아닌 다른 곳. 그곳에서 부름의 소리가 들려오고 있었다."
        )

        private val ROMANCE_ELEMENTS = listOf(
            "눈이 마주쳤다. 순간, 세상의 모든 소음이 멈춘 것 같았다. 심장이 한 박자 쉬고 다시 뛰기 시작했다. 더 빠르게, 더 강하게.",
            "손끝이 살짝 닿았다. 그 작은 접촉이 온몸에 전류를 흘려보낸 것 같았다. 얼굴이 붉어지는 것을 느꼈다.",
            "그 사람의 향기가 바람에 실려왔다. 익숙한, 그리고 그리웠던 향기. 떠올리지 않으려 해도 자꾸만 생각나는 사람.",
            "목소리가 떨렸다. 하고 싶은 말은 많았지만 입술이 쉽게 열리지 않았다. 이 감정을 어떻게 전해야 할지 몰랐다.",
            "등 뒤로 따스한 온기가 느껴졌다. 아무 말 없이 곁에 서 있는 그 존재만으로도 마음이 편안해졌다."
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

        // 댓글 템플릿
        private val POSITIVE_COMMENTS = listOf(
            "이 장면 진짜 소름돋았어요ㅠㅠ",
            "역시 작가님... 감동입니다",
            "와 전개 대박... 다음화 기대됩니다",
            "이 부분에서 눈물났어요ㅜㅜ",
            "주인공 멋있다!! 응원해요",
            "최고의 회차입니다 강추!",
            "몰입감 장난아니네요",
            "이게 바로 명작이지...",
            "읽는 내내 심장이 두근두근",
            "작가님 필력에 감탄합니다"
        )

        private val NEGATIVE_COMMENTS = listOf(
            "이 전개는 좀 아쉽네요",
            "주인공 좀 답답하다...",
            "여기서 왜 이러는 건지 이해가 안 돼요",
            "분량이 너무 짧아요ㅠㅠ",
            "다음 화 빨리 올려주세요!!",
            "설정이 좀 이상한 것 같은데...",
            "조금 지루한 감이 있네요"
        )

        private val NEUTRAL_COMMENTS = listOf(
            "오 흥미롭네요",
            "다음 화 기대됩니다",
            "잘 읽었습니다",
            "재미있게 봤어요",
            "계속 연재해주세요!",
            "구독 완료~",
            "이제 시작인가요?",
            "감사합니다!"
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
    // @param clearExisting deprecated - use clearAllData() separately
    // @return 생성된 데이터 개수 요약
    @Transactional
    fun generateAllMockData(clearExisting: Boolean = false): MockDataSummary {
        println("🚀 Starting mock data generation...")

        // 1. 회원 생성
        println("👥 Generating members...")
        val members = generateMembers(authorCount = 50, readerCount = 1000)
        val authors = members.filter { it.role == MemberRole.AUTHOR }
        val readers = members.filter { it.role == MemberRole.READER }
        println("✅ Members: ${members.size} (Authors: ${authors.size}, Readers: ${readers.size})")

        // 2. 소설 생성
        println("📚 Generating novels...")
        val novels = generateNovels(count = 150, authors = authors)
        println("✅ Novels: ${novels.size}")

        // 3. 에피소드 생성 (3000자 이상의 내용)
        println("📖 Generating episodes with rich content...")
        val episodes = generateEpisodes(novels = novels)
        println("✅ Episodes: ${episodes.size}")

        // 4. 리뷰 생성
        println("💬 Generating reviews...")
        val reviews = generateReviews(count = 10000, readers = readers, novels = novels)
        println("✅ Reviews: ${reviews.size}")

        // 5. 읽기 로그 생성 (대시보드용) - 10만개로 줄임
        println("📊 Generating read logs for dashboard...")
        val readLogs = generateReadLogs(count = 100000, readers = readers, episodes = episodes, days = 30)
        println("✅ Read logs: ${readLogs.size}")

        // 6. DropOffPoint 생성 (이탈 지점 분석용)
        println("📉 Generating drop-off points for analysis...")
        val dropOffPoints = generateDropOffPoints(episodes = episodes, days = 30)
        println("✅ Drop-off points: ${dropOffPoints.size}")

        // 7. 댓글 생성
        println("💬 Generating comments...")
        val comments = generateComments(episodes = episodes, readers = readers, commentsPerEpisode = 5)
        println("✅ Comments: ${comments.size}")

        println("🎉 Mock data generation completed!")

        return MockDataSummary(
            members = members.size,
            novels = novels.size,
            episodes = episodes.size,
            reviews = reviews.size,
            readLogs = readLogs.size,
            dropOffPoints = dropOffPoints.size,
            comments = comments.size
        )
    }

    // 기존 데이터 전체 삭제 (TRUNCATE) + episode.content를 LONGTEXT로 변경
    @Transactional
    fun clearAllData() {
        println("🗑️ Clearing all data...")
        val startTime = System.currentTimeMillis()

        // FK 체크 비활성화
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate()

        // 모든 테이블 TRUNCATE (테이블 구조는 유지)
        // author_dashboard_cache는 별도로 DROP/CREATE 처리
        val tables = listOf(
            "section_replay_log",
            "drop_off_point",
            "episode_read_log",
            "review_analysis",
            "comment_like",
            "episode_comment",
            "notification",
            "purchase",
            "point_history",
            "point",
            "refresh_token",
            "review",
            "episode",
            "novel",
            "member"
        )

        tables.forEach { table ->
            try {
                entityManager.createNativeQuery("TRUNCATE TABLE $table").executeUpdate()
                println("  ✓ TRUNCATE $table")
            } catch (e: Exception) {
                println("  ! TRUNCATE $table 실패 (테이블 없음)")
            }
        }

        // ★★★ episode.content를 LONGTEXT로 변경 ★★★
        try {
            entityManager.createNativeQuery(
                "ALTER TABLE episode MODIFY COLUMN content LONGTEXT NOT NULL"
            ).executeUpdate()
            println("  ✓ episode.content → LONGTEXT")
        } catch (e: Exception) {
            println("  ! episode.content 변경 실패: ${e.message}")
        }

        // ★★★ author_dashboard_cache 테이블 재생성 ★★★
        try {
            entityManager.createNativeQuery("DROP TABLE IF EXISTS author_dashboard_cache").executeUpdate()
            entityManager.createNativeQuery("""
                CREATE TABLE author_dashboard_cache (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    author_id BIGINT NOT NULL,
                    novel_id BIGINT NULL,
                    total_views INT NOT NULL DEFAULT 0,
                    unique_readers INT NOT NULL DEFAULT 0,
                    avg_reading_time_seconds INT NOT NULL DEFAULT 0,
                    completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
                    total_reviews INT NOT NULL DEFAULT 0,
                    positive_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
                    negative_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
                    joy_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
                    anger_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
                    top_episodes_json JSON NULL,
                    drop_off_episodes_json JSON NULL,
                    cache_date DATE NOT NULL,
                    period_type VARCHAR(20) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_author_date (author_id, cache_date),
                    INDEX idx_author_novel_date (author_id, novel_id, cache_date),
                    UNIQUE KEY uk_author_novel_date_period (author_id, novel_id, cache_date, period_type)
                )
            """).executeUpdate()
            println("  ✓ author_dashboard_cache 테이블 재생성")
        } catch (e: Exception) {
            println("  ! author_dashboard_cache 테이블 재생성 실패: ${e.message}")
        }

        // ★★★ drop_off_point 테이블 재생성 ★★★
        try {
            entityManager.createNativeQuery("DROP TABLE IF EXISTS drop_off_point").executeUpdate()
            entityManager.createNativeQuery("""
                CREATE TABLE drop_off_point (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    episode_id BIGINT NOT NULL,
                    novel_id BIGINT NOT NULL,
                    drop0to10percent INT NOT NULL DEFAULT 0,
                    drop10to20percent INT NOT NULL DEFAULT 0,
                    drop20to30percent INT NOT NULL DEFAULT 0,
                    drop30to40percent INT NOT NULL DEFAULT 0,
                    drop40to50percent INT NOT NULL DEFAULT 0,
                    drop50to60percent INT NOT NULL DEFAULT 0,
                    drop60to70percent INT NOT NULL DEFAULT 0,
                    drop70to80percent INT NOT NULL DEFAULT 0,
                    drop80to90percent INT NOT NULL DEFAULT 0,
                    drop90to100percent INT NOT NULL DEFAULT 0,
                    total_readers INT NOT NULL DEFAULT 0,
                    completed_readers INT NOT NULL DEFAULT 0,
                    completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
                    avg_scroll_depth DECIMAL(5,2) NOT NULL DEFAULT 0,
                    aggregated_date DATE NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_episode_date (episode_id, aggregated_date),
                    INDEX idx_novel_date (novel_id, aggregated_date),
                    INDEX idx_completion_rate (completion_rate),
                    UNIQUE KEY uk_episode_date (episode_id, aggregated_date)
                )
            """).executeUpdate()
            println("  ✓ drop_off_point 테이블 재생성")
        } catch (e: Exception) {
            println("  ! drop_off_point 테이블 재생성 실패: ${e.message}")
        }

        // FK 체크 다시 활성화
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate()

        entityManager.flush()
        entityManager.clear()

        val duration = System.currentTimeMillis() - startTime
        println("✅ Done in ${duration}ms!")
    }

    // 스키마만 수정 (별도 호출용)
    @Transactional
    fun fixSchema() {
        println("🔧 Fixing schema...")
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate()
        try {
            entityManager.createNativeQuery(
                "ALTER TABLE episode MODIFY COLUMN content LONGTEXT NOT NULL"
            ).executeUpdate()
            println("  ✓ episode.content → LONGTEXT")
        } catch (e: Exception) {
            println("  ! ${e.message}")
        }
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate()
        entityManager.flush()
        entityManager.clear()
        println("✅ Schema fixed!")
    }

    // 회원 데이터 생성
    //
    // @param authorCount 작가 수
    // @param readerCount 독자 수
    // @return 생성된 회원 리스트
    @Transactional
    fun generateMembers(authorCount: Int = 50, readerCount: Int = 1000): List<Member> {
        val members = mutableListOf<Member>()
        val encodedPassword = passwordEncoder.encode("password123!")

        // 작가 생성
        repeat(authorCount) { i ->
            val member = Member(
                email = "author${i + 1}@novel.com",
                password = encodedPassword,
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
                password = encodedPassword,
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
                    title = generateEpisodeTitle(i + 1, episodeCount),
                    content = generateEpisodeContent(novel.genre),
                    episodeNumber = i + 1,
                    price = if (isFree) 0 else Random.nextInt(100, 500),
                    isFree = isFree,
                    publishedAt = novel.createdAt.plusDays(i.toLong()),
                    createdAt = novel.createdAt.plusDays(i.toLong()),
                    novel = novel
                )
                episodes.add(episode)
            }

            // 배치 저장 (소설 10개마다)
            if (episodes.size >= 300) {
                episodeRepository.saveAll(episodes)
                println("Progress: ${episodes.size} episodes generated for ${novel.title}")
                episodes.clear()
            }
        }

        // 남은 에피소드 저장
        if (episodes.isNotEmpty()) {
            episodeRepository.saveAll(episodes)
        }

        return episodeRepository.findAll().toList()
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
                createdAt = LocalDateTime.now().minusDays(Random.nextLong(30))
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

    private fun generateEpisodeTitle(number: Int, totalEpisodes: Int): String {
        val progressRatio = number.toDouble() / totalEpisodes

        val title = when {
            number == 1 -> EPISODE_TITLE_OPENING.random()
            progressRatio < 0.2 -> EPISODE_TITLE_OPENING.random()
            progressRatio < 0.7 -> EPISODE_TITLE_DEVELOPMENT.random()
            progressRatio < 0.9 -> EPISODE_TITLE_CLIMAX.random()
            else -> EPISODE_TITLE_ENDING.random()
        }

        return "${number}화: $title"
    }

    // 3000자 이상의 현실적인 에피소드 내용 생성
    //
    // 여러 문단을 조합하여 스토리처럼 보이는 긴 콘텐츠를 생성합니다.
    // 이탈 지점 분석을 위해 다양한 길이의 문단을 생성합니다.
    private fun generateEpisodeContent(genre: String): String {
        val paragraphs = mutableListOf<String>()

        // 도입부 (장면 설명)
        paragraphs.add(SCENE_DESCRIPTIONS.random())
        paragraphs.add("")

        // 캐릭터 행동
        paragraphs.add(CHARACTER_ACTIONS.random())
        paragraphs.add("")

        // 장르별 요소 추가
        when {
            genre.contains("판타지") || genre.contains("무협") -> {
                paragraphs.add(FANTASY_ELEMENTS.random())
                paragraphs.add("")
            }
            genre.contains("로맨스") || genre.contains("BL") -> {
                paragraphs.add(ROMANCE_ELEMENTS.random())
                paragraphs.add("")
            }
            else -> {
                paragraphs.add(TENSION_SCENES.random())
                paragraphs.add("")
            }
        }

        // 대화
        paragraphs.add(DIALOGUE_TEMPLATES.random())
        paragraphs.add("")

        // 내면의 생각
        paragraphs.add(INNER_THOUGHTS.random())
        paragraphs.add("")

        // 긴장감 있는 장면
        paragraphs.add(TENSION_SCENES.random())
        paragraphs.add("")

        // 추가 장면들 (3000자 이상을 만들기 위해)
        repeat(Random.nextInt(3, 6)) {
            val elements = listOf(
                SCENE_DESCRIPTIONS,
                CHARACTER_ACTIONS,
                DIALOGUE_TEMPLATES,
                INNER_THOUGHTS,
                TENSION_SCENES,
                if (genre.contains("판타지") || genre.contains("무협")) FANTASY_ELEMENTS else TENSION_SCENES,
                if (genre.contains("로맨스") || genre.contains("BL")) ROMANCE_ELEMENTS else CHARACTER_ACTIONS
            )
            paragraphs.add(elements.random().random())
            paragraphs.add("")
        }

        // 마무리 장면
        paragraphs.add(SCENE_DESCRIPTIONS.random())
        paragraphs.add("")
        paragraphs.add(CHARACTER_ACTIONS.random())

        val content = paragraphs.joinToString("\n")

        // 최소 3000자 보장
        return if (content.length < 3000) {
            val additionalContent = StringBuilder(content)
            while (additionalContent.length < 3000) {
                additionalContent.append("\n\n")
                additionalContent.append(listOf(
                    SCENE_DESCRIPTIONS,
                    CHARACTER_ACTIONS,
                    DIALOGUE_TEMPLATES,
                    INNER_THOUGHTS,
                    TENSION_SCENES
                ).random().random())
            }
            additionalContent.toString()
        } else {
            content
        }
    }

    // DropOffPoint 데이터 생성 (이탈 지점 분석용)
    //
    // EpisodeReadLog를 기반으로 에피소드별 이탈 지점 통계를 집계합니다.
    // @param episodes 에피소드 리스트
    // @param days 집계 기간 (일)
    @Transactional
    fun generateDropOffPoints(episodes: List<Episode>, days: Int = 30): List<DropOffPoint> {
        val dropOffPoints = mutableListOf<DropOffPoint>()

        // 최근 N일간 날짜별로 생성
        for (dayOffset in 0 until days) {
            val targetDate = LocalDate.now().minusDays(dayOffset.toLong())

            episodes.forEach { episode ->
                val novelId = episode.novel.id!!
                val episodeId = episode.id!!

                // 랜덤 통계 생성
                val totalReaders = Random.nextInt(50, 500)
                val completedReaders = (totalReaders * Random.nextDouble(0.3, 0.9)).toInt()
                val completionRate = BigDecimal(completedReaders.toDouble() / totalReaders * 100)
                    .setScale(2, java.math.RoundingMode.HALF_UP)

                // 이탈 구간별 카운트 (10% 구간)
                val remainingDropped = totalReaders - completedReaders
                val dropCounts = mutableListOf<Int>()
                var remainingToDistribute = remainingDropped

                // 0-10%, 10-20%, ... 90-100% 구간
                repeat(10) { range ->
                    val dropInRange = if (range < 9) {
                        // 초반에 더 많은 이탈
                        val weight = when (range) {
                            0, 1 -> 0.25  // 0-20%에서 50% 이탈
                            2, 3 -> 0.15  // 20-40%에서 30% 이탈
                            else -> 0.05   // 나머지
                        }
                        (remainingDropped * weight).toInt().coerceAtMost(remainingToDistribute)
                    } else {
                        remainingToDistribute  // 마지막 구간에 나머지
                    }
                    dropCounts.add(dropInRange)
                    remainingToDistribute -= dropInRange
                }

                val dropOffPoint = DropOffPoint(
                    episodeId = episodeId,
                    novelId = novelId,
                    totalReaders = totalReaders,
                    completedReaders = completedReaders,
                    completionRate = completionRate,
                    avgScrollDepth = BigDecimal(Random.nextDouble(40.0, 90.0)).setScale(2, java.math.RoundingMode.HALF_UP),
                    drop0To10Percent = dropCounts[0],
                    drop10To20Percent = dropCounts[1],
                    drop20To30Percent = dropCounts[2],
                    drop30To40Percent = dropCounts[3],
                    drop40To50Percent = dropCounts[4],
                    drop50To60Percent = dropCounts[5],
                    drop60To70Percent = dropCounts[6],
                    drop70To80Percent = dropCounts[7],
                    drop80To90Percent = dropCounts[8],
                    drop90To100Percent = dropCounts[9],
                    aggregatedDate = targetDate
                )
                dropOffPoints.add(dropOffPoint)
            }

            // 배치 저장
            if (dropOffPoints.size >= 1000) {
                dropOffPointRepository.saveAll(dropOffPoints)
                dropOffPoints.clear()
            }
        }

        // 남은 데이터 저장
        if (dropOffPoints.isNotEmpty()) {
            dropOffPointRepository.saveAll(dropOffPoints)
        }

        return dropOffPointRepository.findAll().toList()
    }

    // 댓글 생성
    //
    // 에피소드별로 댓글을 생성합니다.
    //
    // @param episodes 에피소드 리스트
    // @param readers 독자 리스트
    // @param commentsPerEpisode 에피소드당 평균 댓글 수
    @Transactional
    fun generateComments(
        episodes: List<Episode>,
        readers: List<Member>,
        commentsPerEpisode: Int = 5
    ): List<EpisodeComment> {
        val comments = mutableListOf<EpisodeComment>()

        episodes.forEach { episode ->
            // 에피소드당 0~commentsPerEpisode*2 개의 댓글 랜덤 생성
            val commentCount = Random.nextInt(0, commentsPerEpisode * 2 + 1)

            repeat(commentCount) {
                val reader = readers.random()
                val sentiment = Random.nextDouble()
                val content = when {
                    sentiment < 0.6 -> POSITIVE_COMMENTS.random()
                    sentiment < 0.8 -> NEUTRAL_COMMENTS.random()
                    else -> NEGATIVE_COMMENTS.random()
                }

                val comment = EpisodeComment(
                    content = content,
                    writerName = reader.nickname,
                    writerId = reader.id!!,
                    episode = episode,
                    likeCount = Random.nextLong(0, 50),
                    dislikeCount = Random.nextLong(0, 10),
                    createdAt = LocalDateTime.now().minusDays(Random.nextLong(0, 30))
                )
                comments.add(comment)
            }
        }

        episodeCommentRepository.saveAll(comments)
        return comments
    }
}

// Mock 데이터 생성 요약
data class MockDataSummary(
    val members: Int,
    val novels: Int,
    val episodes: Int,
    val reviews: Int,
    val readLogs: Int,
    val dropOffPoints: Int = 0,
    val comments: Int = 0
)

// MemberRole enum은 domain 패키지에 정의되어 있습니다
