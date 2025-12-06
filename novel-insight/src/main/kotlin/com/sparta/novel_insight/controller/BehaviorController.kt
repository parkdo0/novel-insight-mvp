package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.DeviceType
import com.sparta.novel_insight.security.UserPrincipal
import com.sparta.novel_insight.service.UserBehaviorLogService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import jakarta.servlet.http.HttpServletRequest

//
// # BehaviorController (Phase 6)
//
// 사용자 행동 로그 수집 API 컨트롤러입니다.
//
// ## 목적
//
// 프론트엔드에서 사용자의 읽기 행동을 실시간으로 서버에 전송하여:
// 1. 어디서 멈추는지 (스크롤 깊이)
// 2. 어디서 이탈하는지 (이탈 지점)
// 3. 얼마나 읽는지 (읽기 시간)
//
// 이 데이터가 대시보드의 이탈 분석 차트로 표시됩니다.
//
// ## API 엔드포인트
//
// ### 1. POST /api/behavior/start
// - 사용자가 에피소드 페이지에 진입할 때 호출
// - 읽기 세션 시작 로그 생성
//
// ### 2. POST /api/behavior/scroll
// - 사용자가 스크롤할 때 주기적으로 호출 (5초마다)
// - 최대 스크롤 깊이 업데이트
//
// ### 3. POST /api/behavior/end
// - 사용자가 페이지를 벗어날 때 호출
// - 읽기 세션 종료, 최종 데이터 저장
//
// ## 프론트엔드 연동 예시
//
// ```javascript
// // ViewerPage.vue
// onMounted(async () => {
//   // 1. 읽기 시작
//   await api.post('/api/behavior/start', {
//     episodeId: 123,
//     novelId: 456
//   });
//
//   // 2. 스크롤 추적 (5초마다)
//   setInterval(() => {
//     const scrollDepth = (window.scrollY / document.body.scrollHeight) * 100;
//     api.post('/api/behavior/scroll', { scrollDepth });
//   }, 5000);
//
//   // 3. 페이지 이탈 시
//   window.addEventListener('beforeunload', () => {
//     navigator.sendBeacon('/api/behavior/end', JSON.stringify({
//       scrollDepth: maxScrollDepth
//     }));
//   });
// });
// ```
//
// @see UserBehaviorLogService 행동 로그 서비스
// @see EpisodeReadLog 로그 엔티티
//
@RestController
@RequestMapping("/api/behavior")
class BehaviorController(
    private val userBehaviorLogService: UserBehaviorLogService
) {

    //
    // 읽기 세션 시작
    //
    // ## 호출 시점
    //
    // - 사용자가 에피소드 페이지에 진입할 때
    // - ViewerPage 컴포넌트의 onMounted에서 호출
    //
    // ## Request Body
    //
    // ```json
    // {
    //   "episodeId": 123,
    //   "novelId": 456
    // }
    // ```
    //
    // ## Response
    //
    // ```json
    // {
    //   "logId": 789,
    //   "sessionId": "abc123..."
    // }
    // ```
    //
    // @param request 읽기 시작 요청
    // @param user 인증된 사용자 (비회원인 경우 null)
    // @param httpRequest HTTP 요청 (User-Agent, Referer 추출용)
    // @return 로그 ID와 세션 ID
    //
    @PostMapping("/start")
    fun startReading(
        @RequestBody request: StartReadingRequest,
        @AuthenticationPrincipal user: UserPrincipal?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        // 1. 세션 ID 생성 또는 가져오기
        val sessionId = httpRequest.session.id

        // 2. User-Agent에서 디바이스 타입 추출
        val userAgent = httpRequest.getHeader("User-Agent") ?: ""
        val deviceType = detectDeviceType(userAgent)

        // 3. Referer 추출
        val referrer = httpRequest.getHeader("Referer")

        // 4. 읽기 세션 시작
        val logId = userBehaviorLogService.startReadingSession(
            userId = user?.id,
            episodeId = request.episodeId,
            novelId = request.novelId,
            sessionId = sessionId,
            deviceType = deviceType,
            referrer = referrer
        )

        return ResponseEntity.ok(mapOf(
            "logId" to logId,
            "sessionId" to sessionId
        ))
    }

    //
    // 스크롤 깊이 업데이트
    //
    // ## 호출 시점
    //
    // - 사용자가 스크롤할 때 주기적으로 (5초마다)
    // - setInterval로 구현
    //
    // ## Request Body
    //
    // ```json
    // {
    //   "scrollDepth": 45.25
    // }
    // ```
    //
    // ## Response
    //
    // ```json
    // {
    //   "message": "스크롤 깊이 업데이트 성공"
    // }
    // ```
    //
    // @param request 스크롤 깊이 요청
    // @param httpRequest HTTP 요청 (세션 ID 추출용)
    // @return 성공 메시지
    //
    @PostMapping("/scroll")
    fun updateScroll(
        @RequestBody request: UpdateScrollRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        val sessionId = httpRequest.session.id

        userBehaviorLogService.updateScrollDepth(
            sessionId = sessionId,
            scrollDepth = BigDecimal.valueOf(request.scrollDepth)
        )

        return ResponseEntity.ok(mapOf(
            "message" to "스크롤 깊이 업데이트 성공"
        ))
    }

    //
    // 읽기 세션 종료
    //
    // ## 호출 시점
    //
    // - 사용자가 페이지를 벗어날 때
    // - window.addEventListener('beforeunload', ...)
    // - navigator.sendBeacon() 사용 (페이지 unload 중에도 전송 보장)
    //
    // ## Request Body
    //
    // ```json
    // {
    //   "scrollDepth": 78.5
    // }
    // ```
    //
    // ## Response
    //
    // ```json
    // {
    //   "message": "읽기 세션 종료 성공"
    // }
    // ```
    //
    // @param request 세션 종료 요청
    // @param httpRequest HTTP 요청 (세션 ID 추출용)
    // @return 성공 메시지
    //
    @PostMapping("/end")
    fun endReading(
        @RequestBody request: EndReadingRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        val sessionId = httpRequest.session.id

        userBehaviorLogService.endReadingSession(
            sessionId = sessionId,
            scrollDepth = BigDecimal.valueOf(request.scrollDepth)
        )

        return ResponseEntity.ok(mapOf(
            "message" to "읽기 세션 종료 성공"
        ))
    }

    //
    // User-Agent에서 디바이스 타입 감지
    //
    // ## 감지 로직
    //
    // - "Mobile" 포함 → MOBILE
    // - "Tablet" 또는 "iPad" 포함 → TABLET
    // - 그 외 → DESKTOP
    //
    // @param userAgent User-Agent 문자열
    // @return 디바이스 타입
    //
    private fun detectDeviceType(userAgent: String): DeviceType {
        return when {
            userAgent.contains("Mobile", ignoreCase = true) -> DeviceType.MOBILE
            userAgent.contains("Tablet", ignoreCase = true) || userAgent.contains("iPad", ignoreCase = true) -> DeviceType.TABLET
            else -> DeviceType.DESKTOP
        }
    }
}

//
// Request DTOs
//

data class StartReadingRequest(
    val episodeId: Long,
    val novelId: Long
)

data class UpdateScrollRequest(
    val scrollDepth: Double  // 0-100
)

data class EndReadingRequest(
    val scrollDepth: Double  // 0-100
)
