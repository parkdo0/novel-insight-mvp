package com.sparta.novel_insight.repository

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sparta.novel_insight.domain.Novel
import com.sparta.novel_insight.domain.QNovel
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

/**
 * ========================================
 * NovelRepositoryImpl - QueryDSL 동적 쿼리 구현체
 * ========================================
 *
 * [핵심 개념]
 * 1. Custom Repository 패턴: Spring Data JPA 기본 메소드 외 복잡한 쿼리 구현
 * 2. QueryDSL 동적 쿼리: BooleanExpression으로 조건 동적 조합
 * 3. 페이징 최적화: count 쿼리와 데이터 조회 쿼리 분리
 *
 * [5~6년차 개발자가 반드시 알아야 할 필수 개념]
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 1. Custom Repository 패턴
 *    [구조]
 *    NovelRepository (인터페이스)
 *      ├─ extends JpaRepository (Spring Data JPA 기본 메소드)
 *      └─ extends NovelCustomRepository (커스텀 메소드)
 *           └─ 구현: NovelRepositoryImpl (이 클래스)
 *
 *    [네이밍 규칙 - 매우 중요!]
 *    - 구현 클래스 이름: {Repository인터페이스명}Impl
 *    - 예: NovelRepository → NovelRepositoryImpl
 *    - 다른 이름 사용 시 Spring이 자동 인식 못함 (수동 설정 필요)
 *
 * 2. 왜 동적 쿼리가 필요한가?
 *    [문제 상황]
 *    검색 조건이 선택적일 때:
 *    - title만 있을 수도 있고
 *    - author만 있을 수도 있고
 *    - 둘 다 있을 수도 있고
 *    - 아무것도 없을 수도 있음
 *
 *    [나쁜 방법 - JPQL 문자열 조합]
 *    var jpql = "SELECT n FROM Novel n WHERE 1=1"
 *    if (title != null) jpql += " AND n.title LIKE :title"
 *    if (author != null) jpql += " AND n.author = :author"
 *    → 유지보수 어렵고, SQL Injection 위험
 *
 *    [좋은 방법 - QueryDSL BooleanExpression]
 *    .where(eqTitle(title), eqAuthor(author))
 *    → null인 조건은 자동으로 무시됨 (깔끔!)
 *
 * 3. BooleanExpression 패턴 (핵심!)
 *    - WHERE 절의 각 조건을 메소드로 분리
 *    - null 반환 시 해당 조건 무시
 *    - AND 조건 자동 처리 (,로 구분)
 *
 *    [예시]
 *    title = "해리포터", author = null
 *    → WHERE novel.title LIKE '%해리포터%'  (author 조건 무시)
 *
 *    title = null, author = "J.K. 롤링"
 *    → WHERE novel.author = 'J.K. 롤링'  (title 조건 무시)
 *
 *    title = "해리포터", author = "J.K. 롤링"
 *    → WHERE novel.title LIKE '%해리포터%' AND novel.author = 'J.K. 롤링'
 *
 * 4. 페이징 최적화 - count 쿼리 분리 (매우 중요!)
 *    [문제점]
 *    Spring Data JPA의 기본 Page<T>는:
 *    - 데이터 조회 쿼리 + count 쿼리를 자동 실행
 *    - 하지만 count 쿼리도 JOIN, ORDER BY를 포함하면 느려짐
 *
 *    [최적화 방법]
 *    1. 데이터 조회: JOIN, ORDER BY, LIMIT, OFFSET 포함
 *    2. count 쿼리: JOIN 없이 단순 COUNT(*)만 실행
 *       → 훨씬 빠름!
 *
 *    [예시]
 *    데이터 조회 쿼리:
 *      SELECT n.* FROM novel n
 *      LEFT JOIN review r ON n.id = r.novel_id
 *      ORDER BY n.created_at DESC
 *      LIMIT 10 OFFSET 0
 *
 *    count 쿼리:
 *      SELECT COUNT(n.id) FROM novel n
 *      (JOIN 제거, ORDER BY 제거 → 성능 향상!)
 *
 * 5. Q클래스 사용법
 *    - QNovel.novel: static 인스턴스 (기본 별칭)
 *    - novel.title, novel.author: 엔티티 필드 접근
 *    - Type-safe: 오타 시 컴파일 에러
 *
 * 6. QueryDSL 조회 메소드
 *    - fetch(): List<T> 반환 (여러 건)
 *    - fetchOne(): T 반환 (단건, 없으면 null, 2개 이상이면 예외)
 *    - fetchFirst(): 첫 번째 결과만 (LIMIT 1)
 *    - fetchCount(): Long 반환 (deprecated → count() 사용)
 *
 * 7. 성능 고려사항
 *    a. 인덱스 활용
 *       - title: VARCHAR 필드 → LIKE '%검색어%'는 인덱스 미사용
 *       - 개선: Full-Text Search 또는 Elasticsearch 도입
 *       - author: 완전 일치(eq) → 인덱스 활용 가능
 *         CREATE INDEX idx_novel_author ON novel(author);
 *
 *    b. N+1 문제 방지
 *       - 이 쿼리는 Novel만 조회 → N+1 없음
 *       - 만약 reviews도 함께 조회한다면:
 *         .leftJoin(novel.reviews).fetchJoin()
 *
 *    c. 페이징 성능
 *       - offset이 크면 느려짐 (DB가 앞 데이터를 다 읽고 버림)
 *       - 개선: No Offset 방식 (WHERE id > lastId LIMIT 10)
 *
 * 8. IoC/DI 적용 분석
 *    [Spring의 동작]
 *    1. NovelRepository 인터페이스 스캔
 *    2. JpaRepository 기본 구현체 자동 생성
 *    3. NovelRepositoryImpl 발견 (Impl 규칙)
 *    4. 두 구현체를 합쳐서 하나의 프록시 빈 생성
 *    5. 다른 빈에서 NovelRepository 주입 시 이 프록시 주입
 *
 *    [결과]
 *    novelRepository.findAll()  → Spring Data JPA 기본 메소드
 *    novelRepository.search()   → 우리가 만든 QueryDSL 메소드
 *    → 모두 하나의 인터페이스로 사용 가능!
 *
 * 9. 테스트 전략
 *    - @DataJpaTest: JPA 관련 빈만 로드 (빠름)
 *    - @AutoConfigureTestDatabase: 실제 DB 또는 H2 선택
 *    - 테스트 데이터: @BeforeEach에서 저장
 *    - 검증: title, author 조합별로 테스트 케이스 작성
 *
 * 10. 실무 확장 사례
 *    - 정렬 조건 추가: Pageable.sort 활용
 *    - 다중 조건 검색: genre, publishYear 등 추가
 *    - 통계 쿼리: Projections.bean()으로 DTO 직접 조회
 *    - 서브쿼리: JPAExpressions.select()
 *
 * [실무 체크리스트]
 * ✓ Custom Repository 네이밍 규칙 준수 (Impl)
 * ✓ BooleanExpression null 반환으로 동적 쿼리 구현
 * ✓ 페이징 시 count 쿼리 최적화 (JOIN 제거)
 * ✓ LIKE 검색 시 인덱스 전략 고려
 * ✓ N+1 문제 확인 (fetch join 필요 여부)
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 */
class NovelRepositoryImpl(
    /**
     * [DI - 생성자 주입]
     * - JPAQueryFactory: QueryDslConfig에서 @Bean으로 등록한 인스턴스
     * - Spring이 자동으로 주입 (싱글톤)
     * - Thread-safe하므로 여러 요청에서 동시 사용 가능
     */
    private val queryFactory: JPAQueryFactory
) : NovelCustomRepository {

    /**
     * [동적 검색 쿼리 + 페이징]
     *
     * @param title 제목 검색어 (부분 일치, nullable)
     * @param author 작가명 (완전 일치, nullable)
     * @param pageable 페이징 정보 (page, size, sort)
     * @return Page<Novel> 검색 결과 + 페이징 메타데이터
     *
     * [실행되는 SQL 예시]
     * title = "해리포터", author = null, page = 0, size = 10
     *
     * 1. 데이터 조회 쿼리:
     *    SELECT * FROM novel
     *    WHERE title LIKE '%해리포터%'
     *    LIMIT 10 OFFSET 0;
     *
     * 2. count 쿼리:
     *    SELECT COUNT(*) FROM novel
     *    WHERE title LIKE '%해리포터%';
     *
     * [페이징 동작]
     * - page = 0, size = 10 → OFFSET 0, LIMIT 10
     * - page = 1, size = 10 → OFFSET 10, LIMIT 10
     * - page = 2, size = 10 → OFFSET 20, LIMIT 10
     */
    override fun search(title: String?, author: String?, pageable: Pageable): Page<Novel> {
        /**
         * [Q클래스 인스턴스]
         * - QNovel.novel: static 기본 인스턴스 (별칭 'novel')
         * - 하나의 쿼리에서 같은 테이블을 여러 번 JOIN할 때는:
         *   val novel1 = QNovel("n1")
         *   val novel2 = QNovel("n2")
         */
        val novel = QNovel.novel

        /**
         * [1단계] 쿼리 빌더 생성
         * - where() 안의 null 값은 자동으로 무시됨 (핵심!)
         * - eqTitle(), eqAuthor()가 null 반환 시 해당 조건 제외
         * - AND 조건은 쉼표(,)로 자동 연결
         */
        val builder = queryFactory
            .selectFrom(novel)
            .where(
                eqTitle(title),      // title이 null이면 이 조건 무시
                eqAuthor(author)     // author가 null이면 이 조건 무시
            )

        /**
         * [2단계] 데이터 조회 (페이징 적용)
         * - offset: 건너뛸 개수 (page * size)
         * - limit: 가져올 개수 (size)
         * - fetch(): List<Novel> 반환
         *
         * [주의] order by 미적용!
         * - Pageable.sort를 활용하려면:
         *   .orderBy(novel.createdAt.desc())
         *   또는 Pageable의 Sort를 QueryDSL OrderSpecifier로 변환
         */
        val content = builder
            .offset(pageable.offset)              // OFFSET (page * size)
            .limit(pageable.pageSize.toLong())    // LIMIT size
            .fetch()                              // 실행!

        /**
         * [3단계] 전체 개수 조회 (count 쿼리)
         * - Page 객체는 totalPages를 계산하기 위해 전체 개수 필요
         * - count 쿼리는 JOIN, ORDER BY 제거하여 최적화
         *   (이 예제는 JOIN이 없지만, 복잡한 쿼리에서는 중요!)
         * - fetchOne(): Long? 반환 (null 가능성 있음)
         * - ?: 0L → null이면 0 반환
         *
         * [최적화 개선 방안]
         * - 첫 페이지이고 content.size < pageSize면 count 쿼리 생략 가능
         * - Spring Data JPA의 @QueryHints로 count 쿼리 커스터마이징
         */
        val total = queryFactory
            .select(novel.count())
            .from(novel)
            .where(
                eqTitle(title),
                eqAuthor(author)
            )
            .fetchOne() ?: 0L

        /**
         * [4단계] Page 객체 생성
         * - PageImpl: Spring Data의 Page 인터페이스 구현체
         * - content: 실제 데이터 (List<Novel>)
         * - pageable: 페이징 정보 (page, size, sort)
         * - total: 전체 개수 (totalPages 계산에 사용)
         *
         * [Page 객체의 메소드]
         * - getContent(): 데이터 리스트
         * - getTotalElements(): 전체 개수 (total)
         * - getTotalPages(): 전체 페이지 수 (ceil(total / size))
         * - getNumber(): 현재 페이지 번호 (0부터 시작)
         * - getSize(): 페이지 크기
         * - hasNext(): 다음 페이지 존재 여부
         * - hasPrevious(): 이전 페이지 존재 여부
         */
        return PageImpl(content, pageable, total)
    }

    /**
     * [BooleanExpression - 동적 조건 메소드]
     *
     * title이 null이면 null 반환 → where 절에서 무시됨
     * title이 있으면 BooleanExpression 반환 → WHERE 조건 추가
     *
     * [LIKE 검색 메소드]
     * - contains(title): LIKE '%title%' (부분 일치)
     * - startsWith(title): LIKE 'title%' (시작)
     * - endsWith(title): LIKE '%title' (끝)
     * - eq(title): = 'title' (완전 일치)
     *
     * [성능 고려사항]
     * - LIKE '%검색어%': 양쪽 와일드카드 → 인덱스 사용 불가
     * - LIKE '검색어%': 앞에만 와일드카드 → 인덱스 사용 가능 (B-Tree 특성)
     * - 대용량 검색: Full-Text Index 또는 Elasticsearch 고려
     *
     * [재사용성]
     * 이런 BooleanExpression 메소드는:
     * - 여러 쿼리에서 재사용 가능
     * - 테스트 작성이 쉬움
     * - 조건 변경 시 한 곳만 수정하면 됨
     */
    private fun eqTitle(title: String?): BooleanExpression? {
        if (title.isNullOrEmpty()) return null
        return QNovel.novel.title.contains(title)
    }

    /**
     * [완전 일치 검색]
     * - eq(author): author = '작가명' (정확히 일치해야 함)
     * - 인덱스 활용 가능: CREATE INDEX idx_author ON novel(author);
     * - 카디널리티: author 필드의 고유 값 비율
     *   - 높음 (다양한 작가): 인덱스 효과 좋음
     *   - 낮음 (작가 몇 명뿐): 인덱스 효과 낮음
     *
     * [실무 팁]
     * - 작가 검색이 자주 사용된다면 인덱스 생성 필수
     * - 복합 인덱스: (author, created_at) 고려
     *   → 특정 작가의 최신 작품 조회 시 유리
     */
    private fun eqAuthor(author: String?): BooleanExpression? {
        if (author.isNullOrEmpty()) return null
        return QNovel.novel.author.eq(author)
    }
}