package com.sparta.novel_insight.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ========================================
 * QueryDslConfig - QueryDSL 설정 클래스
 * ========================================
 *
 * [QueryDSL이란?]
 * - Type-safe한 동적 쿼리를 작성할 수 있게 해주는 프레임워크
 * - JPQL 문자열을 Java/Kotlin 코드로 대체하여 컴파일 타임 오류 검증
 * - IDE의 자동 완성 기능 활용 가능
 *
 * [5~6년차 개발자가 반드시 알아야 할 필수 개념]
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 1. 왜 QueryDSL을 사용하는가?
 * ┌────────────────────────────────────────────────────────────────┐
 * │ 방법              │ 장점              │ 단점                      │
 * ├────────────────────────────────────────────────────────────────┤
 * │ JPQL (문자열)     │ 간단함            │ 런타임 오류, 동적 쿼리 어려움 │
 * │ Criteria API      │ Type-safe         │ 복잡함, 가독성 낮음          │
 * │ Native Query      │ 성능 최적화       │ DB 종속적, 유지보수 어려움    │
 * │ QueryDSL (추천)   │ Type-safe, 간결   │ 초기 설정 복잡, Q클래스 필요  │
 * └────────────────────────────────────────────────────────────────┘
 *
 * 2. JPQL의 문제점 (QueryDSL이 해결하는 것)
 *    a. 문자열 기반 쿼리 → 오타 시 런타임 에러
 *       ❌ @Query("SELECT n FROM Novle n")  // "Novle" 오타 → 런타임 에러!
 *       ✅ queryFactory.selectFrom(novel)   // 컴파일 타임에 오류 검출
 *
 *    b. 동적 쿼리 작성 어려움
 *       - WHERE 절에 조건을 동적으로 추가하려면 문자열 조합 필요
 *       - QueryDSL: BooleanExpression으로 깔끔하게 조합 가능
 *
 *    c. IDE 지원 부족
 *       - JPQL 문자열은 자동 완성, 리팩토링 불가
 *       - QueryDSL: Q클래스 덕분에 자동 완성 지원
 *
 * 3. Q클래스란?
 *    - Entity 클래스(Novel.kt)를 기반으로 자동 생성되는 메타데이터 클래스
 *    - build 시 kapt(Kotlin Annotation Processing Tool)가 생성
 *    - 위치: build/generated/source/kapt/main/com/sparta/.../QNovel.kt
 *    - 사용: QNovel.novel.title, QNovel.novel.author 등으로 필드 접근
 *
 *    [Q클래스 생성 과정]
 *    1. @Entity 클래스 작성 (Novel.kt)
 *    2. build.gradle.kts에 kapt 플러그인 추가
 *    3. querydsl-apt 의존성 추가
 *    4. Gradle build 실행
 *    5. QNovel.kt 자동 생성 (build/generated/...)
 *
 * 4. EntityManager vs JPAQueryFactory
 *    - EntityManager: JPA의 핵심 인터페이스 (영속성 컨텍스트 관리)
 *    - JPAQueryFactory: EntityManager를 래핑하여 QueryDSL 쿼리 생성
 *
 *    [관계도]
 *    Spring Container
 *      ↓ (생성 및 주입)
 *    EntityManager (JPA 표준)
 *      ↓ (래핑)
 *    JPAQueryFactory (QueryDSL)
 *      ↓ (사용)
 *    NovelRepositoryImpl (우리 코드)
 *
 * 5. @Bean vs @Component
 *    - @Bean: 개발자가 직접 생성 로직을 작성 (메소드 레벨)
 *    - @Component: Spring이 기본 생성자로 자동 생성 (클래스 레벤)
 *
 *    이 경우 JPAQueryFactory는:
 *    - 외부 라이브러리 클래스 → @Component 불가
 *    - 생성 시 EntityManager 필요 → @Bean으로 직접 생성
 *
 * 6. IoC/DI 적용 분석
 *    [단계 1] Spring Boot 시작 시
 *    - @Configuration 스캔 → QueryDslConfig 클래스 발견
 *    - EntityManager를 생성자 주입 (Spring Data JPA가 자동 제공)
 *
 *    [단계 2] @Bean 메소드 실행
 *    - jpaQueryFactory() 메소드 호출
 *    - new JPAQueryFactory(em) 생성
 *    - Spring 컨테이너에 "jpaQueryFactory" 이름으로 빈 등록
 *
 *    [단계 3] 다른 빈에서 사용
 *    - NovelRepositoryImpl 생성 시 JPAQueryFactory 자동 주입
 *    - 싱글톤 스코프 → 애플리케이션 전체에서 하나의 인스턴스 공유
 *
 * 7. 빈 라이프사이클
 *    1. 인스턴스화: new JPAQueryFactory(em)
 *    2. 의존성 주입: (이미 완료 - 생성자에서 받음)
 *    3. 초기화 콜백: (없음)
 *    4. 사용: 애플리케이션 실행 중
 *    5. 소멸 콜백: 애플리케이션 종료 시 (JVM 종료)
 *
 * 8. Thread-Safety
 *    - EntityManager: Thread-safe하지 않음
 *      → 하지만 Spring이 프록시로 래핑하여 Thread-local로 관리
 *      → 각 HTTP 요청마다 별도의 EntityManager 사용
 *    - JPAQueryFactory: Thread-safe함 (EntityManager 프록시 덕분)
 *      → 싱글톤으로 사용해도 안전
 *
 * 9. 실무 적용 시 주의사항
 *    - Q클래스는 .gitignore에 추가 (build 산출물)
 *    - Gradle clean 시 Q클래스 삭제됨 → build 필요
 *    - Kotlin에서는 kapt, Java에서는 annotationProcessor 사용
 *
 * 10. 성능 최적화
 *    - QueryDSL은 JPQL로 변환되므로 성능 차이 없음
 *    - 하지만 동적 쿼리로 불필요한 JOIN 제거 가능 → 성능 향상
 *    - fetch join, 서브쿼리, 인덱스 활용은 개발자 몫
 *
 * [실무 체크리스트]
 * ✓ build.gradle.kts에 kapt, querydsl-apt 추가
 * ✓ Q클래스가 생성되는지 확인 (build/generated/...)
 * ✓ Repository에서 JPAQueryFactory 주입받아 사용
 * ✓ 동적 쿼리는 BooleanExpression으로 조합
 * ✓ 페이징 쿼리는 count 쿼리 분리하여 최적화
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 */
@Configuration  // IoC: Spring이 이 클래스를 설정 클래스로 인식하여 빈 스캔
class QueryDslConfig(
    /**
     * [DI - 생성자 주입]
     * - EntityManager: JPA의 핵심 인터페이스 (영속성 컨텍스트 관리)
     * - Spring Data JPA가 자동으로 생성하여 주입
     * - Thread-safe한 프록시 객체 (실제로는 ThreadLocal 기반)
     *
     * [EntityManager의 역할]
     * 1. 엔티티 CRUD: persist(), find(), remove()
     * 2. 영속성 컨텍스트 관리: 1차 캐시, 변경 감지(Dirty Checking)
     * 3. JPQL/QueryDSL 쿼리 실행
     * 4. 트랜잭션 관리: @Transactional과 연동
     */
    private val em: EntityManager
) {
    /**
     * [JPAQueryFactory 빈 등록]
     *
     * JPAQueryFactory: QueryDSL 쿼리를 생성하는 팩토리 클래스
     *
     * [왜 @Bean으로 등록하는가?]
     * 1. JPAQueryFactory는 외부 라이브러리 클래스 → @Component 불가
     * 2. 생성 시 EntityManager 필요 → 직접 생성 로직 작성
     * 3. 싱글톤으로 관리하여 모든 Repository에서 재사용
     *
     * [사용 예시]
     * class NovelRepositoryImpl(
     *     private val queryFactory: JPAQueryFactory  // 자동 주입!
     * ) {
     *     fun search() {
     *         queryFactory.selectFrom(QNovel.novel)...
     *     }
     * }
     *
     * [동작 원리]
     * 1. 이 메소드는 Spring 애플리케이션 시작 시 한 번만 실행
     * 2. 반환된 JPAQueryFactory 객체를 Spring 컨테이너에 빈으로 등록
     * 3. 이후 다른 빈에서 JPAQueryFactory를 주입받아 사용
     *
     * [참고] @Bean 메소드명이 빈 이름이 됨
     * - 기본: "jpaQueryFactory" (메소드명)
     * - 변경: @Bean(name = "customName")
     */
    @Bean
    fun jpaQueryFactory(): JPAQueryFactory {
        return JPAQueryFactory(em)
    }
}