package com.sparta.novel_insight.domain

// JPA (Java Persistence API) 관련 임포트
// JPA는 자바에서 ORM(Object-Relational Mapping)을 위한 표준 API
// Kotlin에서는 jakarta.persistence 패키지 사용 (과거 javax.persistence에서 변경됨)
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 리뷰 감성 분석 결과 엔티티 (JPA Entity)
 *
 * Python 감성 분석 서비스에서 분석한 결과를 저장하는 테이블의 JPA 매핑 클래스입니다.
 * Python service의 database.py에 정의된 ReviewAnalysis 모델과 동일한 테이블을 공유합니다.
 *
 * 데이터 흐름:
 * 1. Spring Boot → Kafka → Python 서비스
 * 2. Python 서비스 → MySQL에 분석 결과 저장 (INSERT)
 * 3. Spring Boot → MySQL에서 분석 결과 조회 (SELECT)
 *
 * JPA 엔티티 역할:
 * - 데이터베이스 테이블과 Java/Kotlin 객체를 자동으로 매핑
 * - SQL 작성 없이 객체 지향적으로 데이터베이스 조작 가능
 * - EntityManager가 자동으로 INSERT/UPDATE/DELETE/SELECT 쿼리 생성
 */
@Entity  // JPA 엔티티 클래스임을 나타내는 어노테이션
         // 이 어노테이션이 있어야 JPA가 테이블과 매핑 가능
@Table(name = "review_analysis")  // 매핑될 실제 테이블 이름 지정
                                   // 생략 시 클래스 이름을 snake_case로 변환한 이름 사용
class ReviewAnalysis(
    /**
     * 소설 ID (외래키 역할, 하지만 명시적 FK 제약조건은 없음)
     *
     * Novel 엔티티의 id와 연결되는 값입니다.
     * 엔티티 간 연관관계를 @ManyToOne으로 매핑할 수도 있지만,
     * 여기서는 간단하게 ID만 저장하는 방식 사용 (성능 최적화)
     */
    @Column(nullable = false)  // NOT NULL 제약조건
                               // 데이터베이스 컬럼 설정을 정의하는 어노테이션
    var novelId: Long,         // Long: Kotlin의 64비트 정수 타입 (Java의 long)

    /**
     * 작가 ID
     *
     * 작가별 통계를 집계할 때 사용합니다.
     * 소설(Novel)을 거치지 않고 바로 작가 정보를 저장하여
     * 조인(JOIN) 없이 빠른 집계 가능
     */
    @Column(nullable = false)
    var authorId: Long,

    /**
     * 리뷰 내용 (최대 1000자)
     *
     * Python 서비스에서 분석한 원본 텍스트를 저장합니다.
     * length = 1000: VARCHAR(1000)으로 생성
     * MySQL에서 한글은 UTF-8(utf8mb4) 기준 한 글자당 3-4바이트 소비
     */
    @Column(length = 1000, nullable = false)
    var content: String,       // String: Kotlin의 문자열 타입

    /**
     * 감성 분류 결과
     *
     * Python 감성 분석기가 판별한 결과를 저장합니다.
     * 가능한 값: "POSITIVE" (긍정), "NEGATIVE" (부정), "NEUTRAL" (중립)
     *
     * Enum 타입을 사용할 수도 있지만, 여기서는 단순 문자열 사용
     * (Python과 데이터 형식 통일을 위해)
     */
    @Column(length = 20, nullable = false)  // VARCHAR(20)
    var sentiment: String,

    /**
     * 감성 점수 (0-100 범위)
     *
     * 점수가 높을수록 긍정적, 낮을수록 부정적입니다.
     * - 70 이상: POSITIVE
     * - 30 이하: NEGATIVE
     * - 30-70: NEUTRAL
     *
     * Double: Kotlin의 64비트 부동소수점 타입 (Java의 double)
     * MySQL에서는 DOUBLE 타입으로 저장됨
     */
    @Column(nullable = false)
    var score: Double,

    /**
     * 발견된 긍정 단어 개수
     *
     * Python 감성 분석기가 텍스트에서 찾은 긍정 단어의 개수입니다.
     * 예: "재미있어요", "좋아요", "최고" 등
     *
     * 기본값 0: 긍정 단어가 하나도 없을 수 있음
     */
    @Column(nullable = false)
    var positiveCount: Int = 0,  // Int: Kotlin의 32비트 정수 타입
                                 // MySQL에서는 INT 타입

    /**
     * 발견된 부정 단어 개수
     *
     * Python 감성 분석기가 텍스트에서 찾은 부정 단어의 개수입니다.
     * 예: "지루해", "별로", "최악" 등
     */
    @Column(nullable = false)
    var negativeCount: Int = 0,

    /**
     * 분석 일시
     *
     * Python 서비스에서 감성 분석을 완료한 시각입니다.
     * LocalDateTime: 자바 8의 날짜/시간 API (타임존 정보 없음)
     * MySQL에서는 DATETIME 타입으로 저장됨
     *
     * 기본값 LocalDateTime.now(): 객체 생성 시점의 현재 시각
     * (Python 서비스에서 명시적으로 시간을 전달하지 않으면 이 값 사용)
     */
    @Column(nullable = false)
    var analyzedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 기본키 (Primary Key)
     *
     * @Id: 이 필드가 기본키임을 나타냄
     * @GeneratedValue: 값 생성 전략 지정
     *   - strategy = GenerationType.IDENTITY: 데이터베이스의 AUTO_INCREMENT 사용
     *   - MySQL이 자동으로 1, 2, 3, ... 순서로 ID 생성
     *
     * Long?: Nullable Long 타입
     *   - 객체 생성 시에는 id가 null (아직 DB에 저장 전)
     *   - DB에 저장 후 JPA가 자동으로 생성된 ID 값을 채워줌
     *
     * val: 불변(immutable) 변수
     *   - 한 번 생성된 ID는 변경되지 않아야 하므로 val 사용
     *
     * 기본값 null: 새로운 엔티티 생성 시 ID는 아직 없음
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)

/*
 * Kotlin의 data class가 아닌 일반 class를 사용하는 이유:
 *
 * JPA 엔티티는 다음 요구사항을 만족해야 합니다:
 * 1. 기본 생성자 (no-arg constructor) 필요
 * 2. 프록시(Proxy) 생성을 위해 open class여야 함
 * 3. var 필드 사용 (값 변경 가능해야 함)
 *
 * data class는 불변성을 권장하므로 JPA와 잘 맞지 않습니다.
 * 따라서 일반 class 사용 + kotlin-jpa 플러그인으로 JPA 요구사항 충족
 *
 * kotlin-jpa 플러그인은 자동으로:
 * - no-arg constructor 생성
 * - class와 메서드를 open으로 변경
 */
