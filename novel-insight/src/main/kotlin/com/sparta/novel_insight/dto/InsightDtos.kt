package com.sparta.novel_insight.dto

import com.sparta.novel_insight.domain.ReviewAnalysis

/**
 * 소설별 감성 분석 통계 응답 DTO
 *
 * API 응답으로 클라이언트에게 전달되는 데이터 구조입니다.
 * Repository의 집계 결과를 가공하여 사용자 친화적인 형태로 변환합니다.
 *
 * DTO (Data Transfer Object)를 사용하는 이유:
 * 1. 엔티티 직접 노출 방지
 *    - 엔티티는 데이터베이스 구조와 밀접하게 연결되어 있음
 *    - 엔티티를 직접 반환하면 내부 구조가 외부에 노출됨
 *    - 엔티티 변경 시 API 스펙도 함께 변경되는 문제 발생
 *
 * 2. 필요한 데이터만 전송
 *    - 엔티티의 모든 필드를 보낼 필요 없음
 *    - 네트워크 트래픽 절약 및 보안 향상
 *
 * 3. API 명세 안정성
 *    - DTO는 API 스펙과 직접 연결
 *    - 내부 구조 변경에 영향받지 않음
 *
 * data class를 사용하는 이유:
 * - equals(), hashCode(), toString() 자동 생성
 * - copy() 메서드로 불변성 유지하면서 값 변경 가능
 * - 구조 분해(destructuring) 지원
 * - 간결한 코드 (Java보다 훨씬 짧음)
 */
data class NovelInsightResponse(
    /**
     * 소설 ID
     *
     * 어떤 소설에 대한 통계인지 식별하는 값입니다.
     */
    val novelId: Long,

    /**
     * 전체 리뷰 개수
     *
     * Python 감성 분석 서비스가 분석한 총 리뷰 수입니다.
     * 이 소설에 대해 독자들이 작성한 모든 리뷰의 개수를 나타냅니다.
     */
    val totalReviews: Long,

    /**
     * 긍정 비율 (%)
     *
     * 전체 리뷰 중 긍정(POSITIVE)으로 분석된 리뷰의 비율입니다.
     * 0-100 범위의 값 (예: 68.7 = 68.7%)
     *
     * 계산식: (긍정 리뷰 수 / 전체 리뷰 수) * 100
     */
    val positiveRate: Double,

    /**
     * 부정 비율 (%)
     *
     * 전체 리뷰 중 부정(NEGATIVE)으로 분석된 리뷰의 비율입니다.
     * 0-100 범위의 값 (예: 18.0 = 18.0%)
     */
    val negativeRate: Double,

    /**
     * 중립 비율 (%)
     *
     * 전체 리뷰 중 중립(NEUTRAL)으로 분석된 리뷰의 비율입니다.
     * 0-100 범위의 값 (예: 13.3 = 13.3%)
     *
     * positiveRate + negativeRate + neutralRate = 100.0
     */
    val neutralRate: Double,

    /**
     * 평균 감성 점수
     *
     * 모든 리뷰의 감성 점수(0-100)를 평균한 값입니다.
     * 점수가 높을수록 전반적으로 긍정적인 반응을 의미합니다.
     *
     * 해석 가이드:
     * - 70 이상: 매우 긍정적
     * - 50-70: 긍정적
     * - 30-50: 부정적
     * - 30 이하: 매우 부정적
     */
    val avgScore: Double,

    /**
     * 최근 리뷰 분석 결과 목록
     *
     * 최신 10개 리뷰의 감성 분석 결과를 포함합니다.
     * 작가가 최근 독자 반응을 빠르게 파악할 수 있도록 제공합니다.
     *
     * List<RecentReviewAnalysis>:
     * - List: 순서가 있는 컬렉션 (최신순 정렬)
     * - RecentReviewAnalysis: 개별 리뷰 정보를 담은 DTO
     * - 빈 리스트일 수 있음 (리뷰가 없는 경우)
     */
    val recentReviews: List<RecentReviewAnalysis>
)

/**
 * 최근 리뷰 분석 결과 DTO
 *
 * 개별 리뷰의 감성 분석 결과를 표현하는 DTO입니다.
 * NovelInsightResponse의 recentReviews 필드에서 사용됩니다.
 *
 * 엔티티 vs DTO:
 * - 엔티티 (ReviewAnalysis): 데이터베이스와 직접 매핑, 모든 필드 포함
 * - DTO (RecentReviewAnalysis): API 응답용, 필요한 필드만 선별
 */
data class RecentReviewAnalysis(
    /**
     * 리뷰 내용 (최대 100자)
     *
     * 원본 리뷰는 최대 1000자까지 저장되지만,
     * API 응답에서는 100자로 제한하여 전송합니다.
     * (네트워크 트래픽 절약 및 UI 표시 최적화)
     */
    val content: String,

    /**
     * 감성 분류 결과
     *
     * POSITIVE, NEGATIVE, NEUTRAL 중 하나의 값입니다.
     * 프론트엔드에서 색상이나 아이콘으로 시각화할 때 사용합니다.
     * (예: POSITIVE → 초록색, NEGATIVE → 빨간색)
     */
    val sentiment: String,

    /**
     * 감성 점수 (0-100)
     *
     * 해당 리뷰의 감성 점수입니다.
     * 프론트엔드에서 프로그레스 바나 게이지로 표시할 수 있습니다.
     */
    val score: Double,

    /**
     * 분석 일시 (문자열)
     *
     * 감성 분석이 수행된 시각입니다.
     * LocalDateTime을 문자열로 변환하여 전송합니다.
     *
     * 형식 예시: "2025-12-03T14:30:45"
     * (ISO 8601 형식)
     */
    val analyzedAt: String
) {
    /**
     * companion object
     *
     * Kotlin의 static 멤버를 정의하는 영역입니다.
     * Java의 static 메서드와 유사하지만, 더 강력한 기능 제공합니다.
     *
     * 이 클래스에서는 엔티티 → DTO 변환 팩토리 메서드를 제공합니다.
     */
    companion object {
        /**
         * ReviewAnalysis 엔티티를 RecentReviewAnalysis DTO로 변환
         *
         * 팩토리 메서드 패턴을 사용하여 객체 생성을 캡슐화합니다.
         * 생성자를 직접 호출하는 대신 의미있는 이름의 메서드를 제공합니다.
         *
         * 사용 예시:
         * val entity: ReviewAnalysis = repository.findById(1)
         * val dto: RecentReviewAnalysis = RecentReviewAnalysis.from(entity)
         *
         * 장점:
         * 1. 의도가 명확함 (from이라는 이름으로 변환임을 알 수 있음)
         * 2. 변환 로직이 DTO 내부에 캡슐화됨
         * 3. 여러 변환 방법이 필요하면 메서드 추가 가능
         *    (예: fromWithFullContent, fromWithoutScore 등)
         *
         * @param analysis ReviewAnalysis 엔티티 객체
         * @return RecentReviewAnalysis DTO 객체
         */
        fun from(analysis: ReviewAnalysis): RecentReviewAnalysis {
            return RecentReviewAnalysis(
                // content.take(100): 문자열의 앞 100자만 추출
                // 원본이 100자 미만이면 전체 반환
                // Kotlin의 표준 라이브러리 확장 함수
                content = analysis.content.take(100),

                // 감성 분류는 그대로 전달
                sentiment = analysis.sentiment,

                // 점수도 그대로 전달
                score = analysis.score,

                // LocalDateTime을 문자열로 변환
                // toString(): ISO 8601 형식의 문자열로 변환
                // 예: 2025-12-03T14:30:45
                analyzedAt = analysis.analyzedAt.toString()
            )
        }
    }
}

/**
 * 작가별 전체 통계 응답 DTO
 *
 * 특정 작가의 모든 소설에 대한 통합 통계를 표현합니다.
 * 소설별로 구분하지 않고 작가 단위로 집계된 정보를 제공합니다.
 *
 * 사용 사례:
 * - 작가 프로필 페이지에서 "내 전체 작품에 대한 독자 반응" 표시
 * - 작가 랭킹 시스템 (평균 점수 기준)
 * - 작가별 독자 만족도 대시보드
 */
data class AuthorInsightResponse(
    /**
     * 작가 ID
     *
     * 어떤 작가에 대한 통계인지 식별하는 값입니다.
     */
    val authorId: Long,

    /**
     * 전체 리뷰 개수
     *
     * 해당 작가의 모든 소설에 대한 총 리뷰 수입니다.
     * 작가의 인기도를 나타내는 지표로 사용할 수 있습니다.
     */
    val totalReviews: Long,

    /**
     * 평균 감성 점수
     *
     * 작가의 모든 소설 리뷰에 대한 평균 감성 점수입니다.
     * 작가의 전반적인 작품 품질을 나타내는 지표입니다.
     *
     * 활용 방안:
     * - 작가 등급 시스템 (골드/실버/브론즈)
     * - 추천 작가 선정 기준
     * - 작가 성장 추이 분석 (시간별 점수 변화)
     */
    val avgScore: Double
)

/*
 * DTO 설계 원칙:
 *
 * 1. 불변성 (Immutability)
 *    - val만 사용 (var 사용 금지)
 *    - 한 번 생성된 객체는 변경 불가
 *    - 스레드 안전성 보장
 *
 * 2. 직렬화 가능
 *    - data class는 자동으로 직렬화 지원
 *    - JSON으로 쉽게 변환 가능 (Jackson, Gson)
 *
 * 3. 계층 분리
 *    - Controller → DTO → Service
 *    - Service → Entity → Repository
 *    - DTO와 Entity를 명확히 분리
 *
 * 4. 검증 (Validation)
 *    - 요청 DTO에는 @Valid, @NotNull 등 검증 어노테이션 추가
 *    - 응답 DTO는 검증 불필요 (서버에서 생성하므로)
 *
 * 5. 문서화
 *    - 각 필드의 의미와 제약사항 명시
 *    - API 문서 자동 생성 도구(Swagger) 활용
 */
