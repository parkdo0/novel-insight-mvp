package com.sparta.novel_insight.service

import com.sparta.novel_insight.dto.AuthorInsightResponse
import com.sparta.novel_insight.dto.NovelInsightResponse
import com.sparta.novel_insight.dto.RecentReviewAnalysis
import com.sparta.novel_insight.repository.ReviewAnalysisRepository
// Spring의 @Service 어노테이션
// 이 클래스가 비즈니스 로직을 담당하는 서비스 계층임을 나타냄
import org.springframework.stereotype.Service
// Transactional 어노테이션: 트랜잭션 관리
import org.springframework.transaction.annotation.Transactional
// Kotlin 표준 라이브러리의 수학 함수
// round(): 반올림 함수
import kotlin.math.round

/**
 * 감성 분석 인사이트 서비스
 *
 * Python 감성 분석 서비스가 저장한 결과를 조회하여,
 * 작가에게 제공할 통계 정보를 가공하는 비즈니스 로직을 담당합니다.
 *
 * 서비스 계층의 역할:
 * 1. 비즈니스 로직 구현
 *    - Repository에서 조회한 원시 데이터를 가공
 *    - 비율 계산, 반올림 등 추가 처리 수행
 *
 * 2. 트랜잭션 관리
 *    - @Transactional을 사용하여 데이터 일관성 보장
 *    - 여러 Repository 호출을 하나의 트랜잭션으로 묶음
 *
 * 3. 계층 간 데이터 변환
 *    - Entity → DTO 변환
 *    - Repository의 배열 결과 → 의미있는 객체로 변환
 *
 * 4. 재사용 가능한 로직 제공
 *    - Controller는 Service를 호출하여 비즈니스 로직 실행
 *    - 동일한 로직을 여러 Controller에서 재사용 가능
 *
 * @Service 어노테이션:
 * - Spring 컨텍스트에 빈(Bean)으로 등록
 * - 싱글톤 패턴으로 관리 (애플리케이션당 하나의 인스턴스)
 * - 의존성 주입 (Dependency Injection) 가능
 * - @Component의 특수화된 형태 (의미를 명확히 하기 위해 사용)
 */
@Service
class InsightService(
    /**
     * ReviewAnalysisRepository 의존성 주입
     *
     * 생성자 주입 방식 (Constructor Injection):
     * - Kotlin의 primary constructor 파라미터로 선언
     * - private val로 선언하여 불변 필드로 관리
     * - Spring이 자동으로 Repository 빈을 찾아서 주입
     *
     * 생성자 주입의 장점:
     * 1. 불변성: val로 선언하여 변경 불가
     * 2. 필수 의존성: 생성자 파라미터로 필수 의존성임을 명시
     * 3. 테스트 용이: Mock 객체를 쉽게 주입 가능
     * 4. 순환 참조 방지: 컴파일 시점에 감지 가능
     *
     * 필드 주입(@Autowired) 대신 생성자 주입을 권장하는 이유:
     * - 필드 주입은 리플렉션 사용으로 성능 저하
     * - 순환 참조 발생 시 런타임에 에러 (생성자 주입은 컴파일 시점 감지)
     * - 테스트에서 Mock 주입이 어려움
     */
    private val reviewAnalysisRepository: ReviewAnalysisRepository
) {

    /**
     * 소설별 감성 분석 통계 조회
     *
     * 특정 소설에 대한 독자 리뷰의 감성 분석 결과를 집계하여
     * 작가 대시보드에 표시할 통계 정보를 반환합니다.
     *
     * @Transactional(readOnly = true): 읽기 전용 트랜잭션
     * - 목적: 조회 성능 최적화
     * - 효과:
     *   1. Dirty Checking 비활성화 → 메모리 절약
     *   2. flush 모드를 MANUAL로 변경 → 불필요한 flush 방지
     *   3. DB에 읽기 힌트 전달 → 일부 DB는 읽기 최적화 적용
     * - 주의: readOnly = true인 트랜잭션 내에서 INSERT/UPDATE/DELETE 시도 시
     *   TransactionSystemException 발생
     *
     * 처리 흐름:
     * 1. Repository에서 집계 쿼리 실행 (JPQL)
     * 2. 결과가 없으면 기본값 반환 (모든 값 0)
     * 3. 배열 형태의 결과를 개별 변수로 분해
     * 4. 비율 계산 (소수점 첫째 자리까지 반올림)
     * 5. 최근 리뷰 10개 조회
     * 6. Entity → DTO 변환
     * 7. NovelInsightResponse 객체 생성 및 반환
     *
     * @param novelId 조회할 소설의 ID
     * @return NovelInsightResponse 소설 감성 분석 통계 DTO
     */
    @Transactional(readOnly = true)  // 읽기 전용 최적화
    fun getNovelInsight(novelId: Long): NovelInsightResponse {
        // 1단계: Repository에서 통계 데이터 조회
        // getStatisticsByNovelId()는 Array<Any>? 타입 반환 (Nullable)
        // ?: 연산자: null일 경우 오른쪽 값 반환 (Elvis operator)
        val stats = reviewAnalysisRepository.getStatisticsByNovelId(novelId)
            ?: return NovelInsightResponse(
                // 리뷰가 없는 경우 모든 값을 0으로 설정한 응답 반환
                // Early return 패턴으로 이후 로직 불필요
                novelId = novelId,
                totalReviews = 0,
                positiveRate = 0.0,
                negativeRate = 0.0,
                neutralRate = 0.0,
                avgScore = 0.0,
                recentReviews = emptyList()  // 빈 리스트 반환
            )

        // 2단계: 배열 결과를 개별 변수로 변환
        // Repository 쿼리 결과는 Array<Any> 형태
        // 각 요소를 적절한 타입으로 캐스팅 필요

        // total: 전체 리뷰 개수 (Long → Double 변환)
        // as Long: 타입 캐스팅 (안전하지 않음, 실패 시 ClassCastException)
        // toDouble(): 비율 계산을 위해 Double로 변환
        val total = (stats[0] as Long).toDouble()

        // positiveCount: 긍정 리뷰 개수
        val positiveCount = (stats[1] as Long).toDouble()

        // negativeCount: 부정 리뷰 개수
        val negativeCount = (stats[2] as Long).toDouble()

        // neutralCount: 중립 리뷰 개수
        val neutralCount = (stats[3] as Long).toDouble()

        // avgScore: 평균 감성 점수
        // as? Double: 안전한 캐스팅 (실패 시 null 반환)
        // ?: 0.0: null일 경우 기본값 0.0 사용
        val avgScore = stats[4] as? Double ?: 0.0

        // 3단계: 최근 리뷰 10개 조회 및 DTO 변환
        val recentReviews = reviewAnalysisRepository
            // 최신 리뷰 10개를 분석 일시 기준 내림차순으로 조회
            // List<ReviewAnalysis> 반환
            .findTop10ByNovelIdOrderByAnalyzedAtDesc(novelId)
            // map: 리스트의 각 요소를 변환하는 고차 함수
            // it: 람다의 암시적 파라미터 (각 ReviewAnalysis 객체)
            // RecentReviewAnalysis.from(it): Entity → DTO 변환
            .map { RecentReviewAnalysis.from(it) }
            // 최종 결과: List<RecentReviewAnalysis>

        // 4단계: NovelInsightResponse 객체 생성 및 반환
        return NovelInsightResponse(
            novelId = novelId,

            // toLong(): Double을 다시 Long으로 변환
            // (계산을 위해 Double로 변환했다가 다시 원래 타입으로 복원)
            totalReviews = total.toLong(),

            // 긍정 비율 계산
            // if (total > 0): 0으로 나누기 방지
            // (positiveCount / total) * 1000: 비율에 1000을 곱함
            // round(): 반올림
            // / 10: 10으로 나눠서 소수점 첫째 자리까지 표현
            // 예: (150 / 200) * 1000 = 750.0 → round(750) → 750 / 10 = 75.0%
            positiveRate = if (total > 0) round((positiveCount / total) * 1000) / 10 else 0.0,

            // 부정 비율 계산 (동일한 방식)
            negativeRate = if (total > 0) round((negativeCount / total) * 1000) / 10 else 0.0,

            // 중립 비율 계산 (동일한 방식)
            neutralRate = if (total > 0) round((neutralCount / total) * 1000) / 10 else 0.0,

            // 평균 점수 반올림 (소수점 둘째 자리까지)
            // * 100: 100을 곱함
            // round(): 반올림
            // / 100: 100으로 나눔
            // 예: 72.456 * 100 = 7245.6 → round(7246) → 7246 / 100 = 72.46
            avgScore = round(avgScore * 100) / 100,

            // 최근 리뷰 목록 (이미 DTO로 변환됨)
            recentReviews = recentReviews
        )
    }

    /**
     * 작가별 전체 통계 조회
     *
     * 특정 작가의 모든 소설에 대한 감성 분석 통계를 집계합니다.
     * 소설별로 구분하지 않고 작가 단위로 합산된 정보를 제공합니다.
     *
     * @Transactional(readOnly = true): 읽기 전용 트랜잭션
     * - 조회 성능 최적화 (Dirty Checking 비활성화)
     *
     * 처리 흐름:
     * 1. Repository에서 집계 쿼리 실행
     * 2. 결과가 없으면 기본값 반환
     * 3. 평균 점수 반올림 (소수점 둘째 자리)
     * 4. AuthorInsightResponse 객체 반환
     *
     * 소설 통계와의 차이점:
     * - 긍정/부정/중립 비율 없음 (간단한 요약만 제공)
     * - 최근 리뷰 목록 없음 (작가 전체 통계는 추상적인 수치만 제공)
     * - 계산 로직이 훨씬 단순함
     *
     * @param authorId 조회할 작가의 ID
     * @return AuthorInsightResponse 작가 전체 통계 DTO
     */
    @Transactional(readOnly = true)  // 읽기 전용 최적화
    fun getAuthorInsight(authorId: Long): AuthorInsightResponse {
        // Repository에서 작가별 통계 조회
        // null일 경우 기본값 반환 (Early return 패턴)
        val stats = reviewAnalysisRepository.getStatisticsByAuthorId(authorId)
            ?: return AuthorInsightResponse(
                authorId = authorId,
                totalReviews = 0,
                avgScore = 0.0
            )

        // 배열 결과를 개별 변수로 변환
        // 작가 통계는 2개의 값만 반환 (totalReviews, avgScore)
        // 소설 통계(5개)보다 간단함

        // total: 작가의 모든 소설에 대한 전체 리뷰 수
        val total = stats[0] as Long

        // avgScore: 작가의 모든 소설 리뷰에 대한 평균 감성 점수
        // 안전한 캐스팅 + null 처리
        val avgScore = stats[1] as? Double ?: 0.0

        // AuthorInsightResponse 객체 생성 및 반환
        return AuthorInsightResponse(
            authorId = authorId,
            totalReviews = total,

            // 평균 점수 반올림 (소수점 둘째 자리까지)
            // 예: 75.123 → 75.12
            avgScore = round(avgScore * 100) / 100
        )
    }
}

/*
 * Service 계층 설계 원칙:
 *
 * 1. 단일 책임 원칙 (Single Responsibility Principle)
 *    - 하나의 Service는 하나의 도메인 영역을 담당
 *    - InsightService는 감성 분석 통계만 담당
 *    - 다른 비즈니스 로직은 다른 Service로 분리
 *
 * 2. 의존성 주입 (Dependency Injection)
 *    - Repository를 생성자로 주입받음
 *    - 테스트 시 Mock 객체로 대체 가능
 *    - 순환 참조 방지
 *
 * 3. 트랜잭션 관리
 *    - 복잡한 로직에는 @Transactional 사용
 *    - 읽기 전용 트랜잭션: @Transactional(readOnly = true)
 *    - 데이터 일관성 보장
 *
 * 4. 예외 처리
 *    - 비즈니스 예외는 Service에서 처리
 *    - 사용자 정의 예외 클래스 사용 권장
 *    - Controller에게 명확한 에러 정보 전달
 *
 * 5. DTO 사용
 *    - Entity를 Controller에 직접 노출하지 않음
 *    - Service에서 Entity → DTO 변환 수행
 *    - API 스펙과 내부 구조 분리
 *
 * 6. 테스트 용이성
 *    - 비즈니스 로직을 Service에 집중
 *    - Repository를 Mock으로 대체하여 단위 테스트
 *    - Controller는 얇게 유지 (Service 호출만)
 */
