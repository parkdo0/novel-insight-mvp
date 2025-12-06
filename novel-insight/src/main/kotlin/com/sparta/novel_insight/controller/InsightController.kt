package com.sparta.novel_insight.controller

import com.sparta.novel_insight.dto.AuthorInsightResponse
import com.sparta.novel_insight.dto.NovelInsightResponse
import com.sparta.novel_insight.service.InsightService
// Spring Web MVC 관련 임포트
// ResponseEntity: HTTP 응답을 표현하는 클래스 (상태 코드, 헤더, 바디 포함)
import org.springframework.http.ResponseEntity
// REST API 어노테이션들
// @GetMapping: HTTP GET 요청 매핑
// @PathVariable: URL 경로 변수 추출
// @RequestMapping: 컨트롤러의 기본 URL 경로 설정
// @RestController: REST API 컨트롤러 선언
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 작가 인사이트 API 컨트롤러
 *
 * Python 감성 분석 서비스가 저장한 분석 결과를 조회하여,
 * 작가에게 독자 반응 통계를 제공하는 REST API 엔드포인트를 정의합니다.
 *
 * 제공하는 API:
 * 1. GET /api/insights/novels/{novelId} - 소설별 감성 분석 통계
 * 2. GET /api/insights/authors/{authorId} - 작가별 전체 통계
 *
 * 컨트롤러 계층의 역할:
 * 1. HTTP 요청 수신
 *    - 클라이언트(프론트엔드)의 HTTP 요청을 받음
 *    - URL 경로, 쿼리 파라미터, 요청 바디 파싱
 *
 * 2. 요청 검증
 *    - 파라미터 유효성 검사
 *    - 인증/인가 확인 (필요시 @PreAuthorize 등 사용)
 *
 * 3. 서비스 호출
 *    - 비즈니스 로직은 서비스 계층에 위임
 *    - 컨트롤러는 얇게 유지 (Thin Controller)
 *
 * 4. HTTP 응답 반환
 *    - 서비스 결과를 HTTP 응답으로 변환
 *    - 상태 코드, 헤더, 바디 설정
 *
 * @RestController 어노테이션:
 * - @Controller + @ResponseBody의 결합
 * - 모든 메서드의 반환값이 HTTP 응답 바디로 직렬화됨
 * - JSON 형태로 자동 변환 (Jackson 사용)
 * - View를 반환하지 않음 (HTML 렌더링 없음)
 *
 * @RequestMapping("/api/insights"):
 * - 이 컨트롤러의 모든 엔드포인트는 /api/insights로 시작
 * - 개별 메서드의 경로는 이 경로에 추가됨
 * - 예: @GetMapping("/novels/{novelId}") → /api/insights/novels/{novelId}
 */
@RestController
@RequestMapping("/api/insights")
class InsightController(
    /**
     * InsightService 의존성 주입
     *
     * 생성자 주입 방식:
     * - Spring이 자동으로 서비스 빈을 찾아서 주입
     * - private val로 선언하여 불변 필드로 관리
     * - 컨트롤러는 서비스만 의존 (Repository 직접 접근 금지)
     *
     * 계층 구조:
     * [Client] → [Controller] → [Service] → [Repository] → [Database]
     *
     * 의존성 주입 장점:
     * - 테스트 용이: Mock 서비스로 대체 가능
     * - 결합도 낮음: 서비스 구현 변경 시 컨트롤러 수정 불필요
     * - 재사용성: 동일한 서비스를 여러 컨트롤러에서 사용 가능
     */
    private val insightService: InsightService
) {

    /**
     * 소설별 감성 분석 통계 조회 API
     *
     * 특정 소설에 대한 독자 리뷰의 감성 분석 결과를 집계하여 반환합니다.
     * 작가 대시보드에서 "내 소설에 대한 독자 반응" 정보를 표시할 때 사용됩니다.
     *
     * HTTP Method: GET
     * - 데이터 조회 작업이므로 GET 사용
     * - 서버 상태를 변경하지 않음 (멱등성 보장)
     * - 브라우저 캐싱 가능
     *
     * URL 패턴: /api/insights/novels/{novelId}
     * - {novelId}: 경로 변수 (Path Variable)
     * - 예: /api/insights/novels/1 → novelId = 1
     *
     * 요청 예시:
     * GET /api/insights/novels/1
     * (요청 바디 없음)
     *
     * 응답 예시:
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * {
     *   "novelId": 1,
     *   "totalReviews": 150,
     *   "positiveRate": 68.7,
     *   "negativeRate": 18.0,
     *   "neutralRate": 13.3,
     *   "avgScore": 72.5,
     *   "recentReviews": [
     *     {
     *       "content": "이 소설 정말 재미있어요!",
     *       "sentiment": "POSITIVE",
     *       "score": 85.5,
     *       "analyzedAt": "2025-12-03T14:30:45"
     *     },
     *     ...
     *   ]
     * }
     *
     * @GetMapping 어노테이션:
     * - HTTP GET 요청을 이 메서드에 매핑
     * - value = "/novels/{novelId}": URL 경로 패턴 정의
     * - @RequestMapping의 method = RequestMethod.GET과 동일
     *
     * @PathVariable 어노테이션:
     * - URL 경로의 변수를 메서드 파라미터로 추출
     * - {novelId}와 파라미터명 novelId가 일치하면 자동 매핑
     * - 타입 변환 자동 수행 (String → Long)
     * - 변환 실패 시 400 Bad Request 응답
     *
     * @param novelId 조회할 소설의 ID (경로 변수에서 추출)
     * @return ResponseEntity<NovelInsightResponse> HTTP 응답 (200 OK + 통계 데이터)
     *
     * ResponseEntity:
     * - HTTP 응답을 표현하는 Spring의 클래스
     * - 상태 코드, 헤더, 바디를 포함
     * - ResponseEntity.ok(body): 200 OK 응답 생성
     */
    @GetMapping("/novels/{novelId}")
    fun getNovelInsight(@PathVariable novelId: Long): ResponseEntity<NovelInsightResponse> {
        // 서비스 계층 호출
        // 비즈니스 로직은 서비스에 위임 (컨트롤러는 얇게 유지)
        // 서비스에서 NovelInsightResponse DTO를 반환받음
        val insight = insightService.getNovelInsight(novelId)

        // HTTP 200 OK 응답 반환
        // ResponseEntity.ok(): 200 상태 코드 + 바디(insight)
        // Spring이 자동으로 insight 객체를 JSON으로 직렬화
        //
        // 직렬화 과정:
        // NovelInsightResponse 객체 → Jackson ObjectMapper → JSON 문자열
        //
        // Content-Type 헤더 자동 설정: application/json
        return ResponseEntity.ok(insight)

        // 참고: 예외 처리
        // - novelId가 유효하지 않은 경우 (음수 등): 서비스에서 처리
        // - 데이터베이스 오류: @ControllerAdvice로 전역 예외 처리 권장
        // - 404 Not Found가 필요하면: ResponseEntity.notFound().build()
    }

    /**
     * 작가별 전체 통계 조회 API
     *
     * 특정 작가의 모든 소설에 대한 감성 분석 통계를 집계하여 반환합니다.
     * 작가 프로필 페이지에서 "내 전체 작품에 대한 독자 반응" 정보를 표시할 때 사용됩니다.
     *
     * HTTP Method: GET
     * - 데이터 조회 작업
     * - 멱등성 보장 (같은 요청을 여러 번 보내도 결과 동일)
     *
     * URL 패턴: /api/insights/authors/{authorId}
     * - {authorId}: 경로 변수 (작가 ID)
     * - 예: /api/insights/authors/1 → authorId = 1
     *
     * 요청 예시:
     * GET /api/insights/authors/1
     *
     * 응답 예시:
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * {
     *   "authorId": 1,
     *   "totalReviews": 500,
     *   "avgScore": 75.2
     * }
     *
     * @param authorId 조회할 작가의 ID (경로 변수)
     * @return ResponseEntity<AuthorInsightResponse> HTTP 응답 (200 OK + 통계 데이터)
     */
    @GetMapping("/authors/{authorId}")
    fun getAuthorInsight(@PathVariable authorId: Long): ResponseEntity<AuthorInsightResponse> {
        // 서비스 호출하여 작가 통계 조회
        val insight = insightService.getAuthorInsight(authorId)

        // HTTP 200 OK 응답 반환
        // AuthorInsightResponse 객체를 JSON으로 직렬화
        return ResponseEntity.ok(insight)
    }
}

/*
 * REST API 설계 원칙:
 *
 * 1. 적절한 HTTP 메서드 사용
 *    - GET: 조회 (리소스 상태 변경 없음)
 *    - POST: 생성
 *    - PUT: 전체 수정
 *    - PATCH: 부분 수정
 *    - DELETE: 삭제
 *
 * 2. 명확한 URL 구조
 *    - /api/insights/novels/{id}: 리소스 중심 설계
 *    - 동사 대신 명사 사용 (GET /api/getNovelInsight → GET /api/insights/novels/{id})
 *    - 계층 구조 표현 (콜렉션/항목)
 *
 * 3. 적절한 HTTP 상태 코드
 *    - 200 OK: 성공
 *    - 201 Created: 생성 성공
 *    - 400 Bad Request: 잘못된 요청
 *    - 404 Not Found: 리소스 없음
 *    - 500 Internal Server Error: 서버 오류
 *
 * 4. 일관된 응답 형식
 *    - JSON 형태의 응답
 *    - DTO 사용 (엔티티 직접 노출 금지)
 *    - 에러 응답도 일관된 형식 유지
 *
 * 5. 버전 관리
 *    - URL에 버전 포함: /api/v1/insights
 *    - 또는 헤더 사용: Accept: application/vnd.api.v1+json
 *    - 하위 호환성 유지
 *
 * 6. 페이지네이션
 *    - 대량 데이터는 페이지 분할
 *    - 쿼리 파라미터 사용: ?page=1&size=10
 *    - 전체 개수 정보 제공
 *
 * 7. 필터링 및 정렬
 *    - 쿼리 파라미터: ?sort=avgScore,desc&filter=positive
 *    - 복잡한 검색은 POST /api/insights/search 고려
 *
 * 8. 에러 처리
 *    - @ControllerAdvice로 전역 예외 처리
 *    - 명확한 에러 메시지 제공
 *    - 스택 트레이스는 숨김 (프로덕션)
 *
 * 9. 보안
 *    - 인증: JWT, OAuth 등
 *    - 인가: @PreAuthorize, 역할 기반 접근 제어
 *    - XSS, CSRF 방어
 *    - Rate Limiting (요청 제한)
 *
 * 10. 문서화
 *     - Swagger/OpenAPI 사용
 *     - API 스펙 자동 생성
 *     - 예시 요청/응답 제공
 */
