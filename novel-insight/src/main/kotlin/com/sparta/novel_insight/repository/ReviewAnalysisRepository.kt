package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.ReviewAnalysis
// Spring Data JPA 관련 임포트
// Spring Data JPA는 JPA를 더 쉽게 사용할 수 있게 해주는 Spring 프레임워크의 모듈
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * ReviewAnalysis 엔티티의 데이터베이스 접근을 담당하는 Repository 인터페이스
 *
 * Spring Data JPA의 JpaRepository를 상속받아 기본적인 CRUD 메서드를 자동으로 제공받습니다.
 * 별도로 구현 클래스를 작성하지 않아도 Spring이 런타임에 자동으로 구현체를 생성합니다.
 *
 * JpaRepository가 자동으로 제공하는 메서드들:
 * - save(entity): 엔티티 저장 또는 업데이트
 * - findById(id): ID로 엔티티 조회
 * - findAll(): 모든 엔티티 조회
 * - deleteById(id): ID로 엔티티 삭제
 * - count(): 전체 레코드 개수
 * 등 약 20여 개의 메서드를 자동 제공
 *
 * JpaRepository<ReviewAnalysis, Long>의 의미:
 * - 첫 번째 타입 파라미터 (ReviewAnalysis): 관리할 엔티티 타입
 * - 두 번째 타입 파라미터 (Long): 엔티티의 기본키(@Id) 타입
 *
 * 이 인터페이스는 주로 통계 조회용으로 사용됩니다:
 * - Python 서비스: 분석 결과를 저장 (INSERT)
 * - Spring Boot: 저장된 분석 결과를 조회하여 통계 제공 (SELECT)
 */
interface ReviewAnalysisRepository : JpaRepository<ReviewAnalysis, Long> {

    /**
     * 소설별 감성 분석 통계 조회
     *
     * 특정 소설에 대한 모든 리뷰의 감성 분석 통계를 집계합니다.
     * 작가 대시보드에서 "이 소설에 대한 독자 반응이 어떤가요?" 정보를 제공할 때 사용됩니다.
     *
     * @Query 어노테이션:
     * - JPQL (Java Persistence Query Language) 쿼리를 작성
     * - JPQL은 테이블이 아닌 엔티티 객체를 대상으로 쿼리 작성
     * - SQL과 유사하지만 FROM 절에 테이블 대신 엔티티 이름 사용
     *
     * 쿼리 설명:
     * 1. SELECT: 집계 결과를 Array로 반환
     *    - COUNT(ra): 전체 리뷰 개수
     *    - SUM(CASE...): 조건부 합계 (긍정/부정/중립 개수)
     *    - AVG(ra.score): 평균 감성 점수
     *
     * 2. CASE WHEN 구문:
     *    - SQL의 조건문 (프로그래밍의 if-else와 유사)
     *    - WHEN ra.sentiment = 'POSITIVE' THEN 1 ELSE 0
     *    - 감성이 POSITIVE면 1, 아니면 0 반환
     *    - SUM()과 함께 사용하여 특정 조건의 레코드 개수 계산
     *
     * 3. FROM ReviewAnalysis ra:
     *    - ReviewAnalysis 엔티티를 대상으로 쿼리
     *    - ra는 별칭(alias) - 테이블명이 길 때 짧게 표현
     *
     * 4. WHERE ra.novelId = :novelId:
     *    - :novelId는 named parameter (이름 기반 파라미터)
     *    - @Param("novelId")로 메서드 파라미터와 매핑
     *    - SQL Injection 방지 (PreparedStatement 사용)
     *
     * 반환 타입 Array<Any>?:
     * - Array: JPQL 집계 쿼리는 배열 형태로 결과 반환
     * - Any: Kotlin의 최상위 타입 (Java의 Object)
     *   - [0]: Long (totalReviews)
     *   - [1]: Long (positiveCount)
     *   - [2]: Long (negativeCount)
     *   - [3]: Long (neutralCount)
     *   - [4]: Double (avgScore)
     * - ?: Nullable - 해당 소설의 리뷰가 없으면 null 반환
     *
     * 사용 예시:
     * val result = repository.getStatisticsByNovelId(1)
     * if (result != null) {
     *     val total = result[0] as Long
     *     val positive = result[1] as Long
     *     // ...
     * }
     */
    @Query("""
        SELECT
            COUNT(ra) as totalReviews,
            SUM(CASE WHEN ra.sentiment = 'POSITIVE' THEN 1 ELSE 0 END) as positiveCount,
            SUM(CASE WHEN ra.sentiment = 'NEGATIVE' THEN 1 ELSE 0 END) as negativeCount,
            SUM(CASE WHEN ra.sentiment = 'NEUTRAL' THEN 1 ELSE 0 END) as neutralCount,
            AVG(ra.score) as avgScore
        FROM ReviewAnalysis ra
        WHERE ra.novelId = :novelId
    """)
    fun getStatisticsByNovelId(@Param("novelId") novelId: Long): Array<Any>?
    // @Param("novelId"): named parameter 매핑
    // 쿼리의 :novelId와 메서드 파라미터 novelId를 연결

    /**
     * 작가별 소설들의 통계 조회
     *
     * 특정 작가의 모든 소설에 대한 감성 분석 통계를 집계합니다.
     * "내 모든 소설에 대한 전체 독자 반응은 어떤가요?" 정보를 제공할 때 사용됩니다.
     *
     * 쿼리 특징:
     * - 소설별로 구분하지 않고 작가의 모든 리뷰를 합산
     * - 전체 리뷰 개수와 평균 점수만 제공 (간단한 요약 통계)
     *
     * WHERE ra.authorId = :authorId:
     * - authorId로 필터링
     * - 작가가 쓴 모든 소설의 리뷰를 대상으로 집계
     * - Novel 테이블 조인 불필요 (성능 최적화)
     *
     * 반환 타입 Array<Any>?:
     * - [0]: Long (totalReviews) - 작가의 모든 소설에 대한 전체 리뷰 수
     * - [1]: Double (avgScore) - 전체 리뷰의 평균 감성 점수
     */
    @Query("""
        SELECT
            COUNT(ra) as totalReviews,
            AVG(ra.score) as avgScore
        FROM ReviewAnalysis ra
        WHERE ra.authorId = :authorId
    """)
    fun getStatisticsByAuthorId(@Param("authorId") authorId: Long): Array<Any>?

    /**
     * 최근 N개 리뷰 분석 결과 조회
     *
     * 특정 소설의 최신 리뷰 10개를 분석 일시 기준 내림차순으로 조회합니다.
     * 작가 대시보드에서 "최근 독자들의 반응" 섹션에 표시할 때 사용됩니다.
     *
     * Spring Data JPA의 쿼리 메서드 (Query Method):
     * - @Query 어노테이션 없이 메서드 이름만으로 쿼리 생성
     * - Spring Data JPA가 메서드 이름을 파싱하여 자동으로 쿼리 생성
     *
     * 메서드 이름 파싱 규칙:
     * 1. findTop10By: 최상위 10개 결과 조회
     *    - findTop: 상위 N개 제한
     *    - 10: 조회할 레코드 개수
     *    - By: WHERE 조건절 시작
     *
     * 2. NovelId: WHERE novelId = ? 조건 추가
     *    - 엔티티의 필드 이름과 일치해야 함
     *    - 파라미터의 값과 매핑
     *
     * 3. OrderBy: ORDER BY 절 시작
     *
     * 4. AnalyzedAt: 정렬 기준 컬럼 (analyzedAt 필드)
     *
     * 5. Desc: 내림차순 정렬 (Descending)
     *    - Asc: 오름차순 (Ascending)
     *    - 생략 시 기본값은 Asc
     *
     * 생성되는 SQL (대략적):
     * SELECT * FROM review_analysis
     * WHERE novel_id = ?
     * ORDER BY analyzed_at DESC
     * LIMIT 10
     *
     * 반환 타입 List<ReviewAnalysis>:
     * - 엔티티 리스트 반환
     * - 결과가 없으면 빈 리스트 (empty list) 반환 (null 아님)
     * - 10개 미만이면 있는 만큼만 반환
     *
     * 쿼리 메서드 명명 규칙 장점:
     * - SQL 작성 불필요
     * - 타입 안전성 보장
     * - 가독성 높음
     * - 컴파일 시점에 메서드 이름 검증 (오타 방지)
     *
     * 단점:
     * - 복잡한 쿼리는 메서드 이름이 길어짐
     * - 조인이 많거나 복잡한 집계는 @Query 사용 권장
     */
    fun findTop10ByNovelIdOrderByAnalyzedAtDesc(novelId: Long): List<ReviewAnalysis>
}

/*
 * Repository 계층의 역할:
 *
 * 1. 데이터 접근 추상화
 *    - Service 계층은 데이터베이스 구현 세부사항을 몰라도 됨
 *    - MySQL에서 PostgreSQL로 변경해도 Service 코드는 변경 불필요
 *
 * 2. 재사용성 향상
 *    - 동일한 쿼리를 여러 Service에서 사용 가능
 *    - 중복 코드 제거
 *
 * 3. 테스트 용이성
 *    - Repository를 Mock으로 대체하여 Service 단위 테스트 가능
 *    - 실제 데이터베이스 없이도 테스트 실행 가능
 *
 * 4. 트랜잭션 관리
 *    - Spring이 자동으로 트랜잭션 처리
 *    - @Transactional과 함께 사용하여 데이터 일관성 보장
 *
 * 5. 성능 최적화
 *    - N+1 문제 해결 (EntityGraph, fetch join)
 *    - 쿼리 최적화 및 캐싱
 */
