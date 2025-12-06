package com.sparta.novel_insight.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime

//
// # GlobalExceptionHandler
//
// 애플리케이션 전역에서 발생하는 예외를 일관되게 처리합니다.
//
// ## WHY @ControllerAdvice?
//
// ### 문제: 컨트롤러마다 중복된 예외 처리
//
// ```kotlin
// // ❌ 나쁜 예: 각 컨트롤러에서 try-catch 반복
// @RestController
// class MemberController {
//     @GetMapping("/members/{id}")
//     fun getMember(@PathVariable id: Long): ResponseEntity<*> {
//         try {
//             val member = memberService.getMember(id)
//             return ResponseEntity.ok(member)
//         } catch (e: MemberNotFoundException) {
//             return ResponseEntity.status(404).body(mapOf("error" to e.message))
//         } catch (e: Exception) {
//             return ResponseEntity.status(500).body(mapOf("error" to "서버 오류"))
//         }
//     }
// }
// ```
//
// ### 해결: @ControllerAdvice로 중앙 집중식 예외 처리
//
// ```kotlin
// // ✅ 좋은 예: 컨트롤러는 비즈니스 로직에만 집중
// @RestController
// class MemberController(private val memberService: MemberService) {
//     @GetMapping("/members/{id}")
//     fun getMember(@PathVariable id: Long): MemberDto {
//         return memberService.getMember(id) // 예외는 GlobalExceptionHandler가 처리
//     }
// }
//
// @ControllerAdvice
// class GlobalExceptionHandler {
//     @ExceptionHandler(MemberNotFoundException::class)
//     fun handleNotFound(ex: MemberNotFoundException): ResponseEntity<ErrorResponse> {
//         return ResponseEntity.status(404).body(...)
//     }
// }
// ```
//
// ## 실무 장점
//
// ### 1. 일관된 에러 응답 포맷
// - 모든 API 에러 응답이 동일한 구조
// - 프론트엔드가 예측 가능한 에러 처리 가능
//
// ### 2. 코드 중복 제거
// - 100개 컨트롤러에서 예외 처리 코드 제거
// - 예외 처리 로직이 한 곳에 집중
//
// ### 3. 로깅 일원화
// - 모든 예외를 한 곳에서 로깅
// - 에러 추적 및 모니터링 용이
//
// ### 4. 보안 향상
// - 프로덕션 환경에서 Stack Trace 노출 방지
// - 사용자 친화적인 에러 메시지 제공
//
// ## 에러 응답 포맷 (RFC 7807 Problem Details)
//
// ```json
// {
//   "message": "회원을 찾을 수 없습니다. (ID: 123)",
//   "errorCode": "MEMBER_NOT_FOUND",
//   "timestamp": "2025-12-06T10:30:45",
//   "path": "/api/members/123"
// }
// ```
//

//
// 에러 응답 DTO
//
// 클라이언트에게 전달되는 통일된 에러 응답 형식입니다.
//
// @property message 사용자 친화적인 에러 메시지
// @property errorCode 에러 코드 (예: MEMBER_NOT_FOUND, UNAUTHORIZED)
// @property timestamp 에러 발생 시각
// @property path 에러가 발생한 API 경로
//
data class ErrorResponse(
    val message: String,
    val errorCode: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val path: String? = null
)

//
// 전역 예외 처리기
//
// @ControllerAdvice: 모든 @Controller에서 발생하는 예외를 가로채서 처리
// @RestControllerAdvice를 사용하면 @ResponseBody가 자동으로 적용됨
//
@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    //
    // MemberNotFoundException 처리
    //
    // HTTP 404 Not Found 응답을 반환합니다.
    //
    // @param ex 발생한 예외
    // @param request 요청 정보
    // @return 404 에러 응답
    //
    @ExceptionHandler(MemberNotFoundException::class)
    fun handleMemberNotFound(
        ex: MemberNotFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Member not found: ID={}", ex.id)

        val error = ErrorResponse(
            message = ex.message ?: "회원을 찾을 수 없습니다.",
            errorCode = "MEMBER_NOT_FOUND",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }

    //
    // NovelNotFoundException 처리
    //
    // HTTP 404 Not Found 응답을 반환합니다.
    //
    @ExceptionHandler(NovelNotFoundException::class)
    fun handleNovelNotFound(
        ex: NovelNotFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Novel not found: ID={}", ex.id)

        val error = ErrorResponse(
            message = ex.message ?: "소설을 찾을 수 없습니다.",
            errorCode = "NOVEL_NOT_FOUND",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }

    //
    // EpisodeNotFoundException 처리
    //
    // HTTP 404 Not Found 응답을 반환합니다.
    //
    @ExceptionHandler(EpisodeNotFoundException::class)
    fun handleEpisodeNotFound(
        ex: EpisodeNotFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Episode not found: ID={}", ex.id)

        val error = ErrorResponse(
            message = ex.message ?: "에피소드를 찾을 수 없습니다.",
            errorCode = "EPISODE_NOT_FOUND",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }

    //
    // NotificationNotFoundException 처리
    //
    // HTTP 404 Not Found 응답을 반환합니다.
    //
    @ExceptionHandler(NotificationNotFoundException::class)
    fun handleNotificationNotFound(
        ex: NotificationNotFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Notification not found: ID={}", ex.id)

        val error = ErrorResponse(
            message = ex.message ?: "알림을 찾을 수 없습니다.",
            errorCode = "NOTIFICATION_NOT_FOUND",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }

    //
    // UnauthorizedException 처리
    //
    // HTTP 403 Forbidden 응답을 반환합니다.
    //
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(
        ex: UnauthorizedException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Unauthorized access: {}", ex.message)

        val error = ErrorResponse(
            message = ex.message ?: "권한이 없습니다.",
            errorCode = "UNAUTHORIZED",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error)
    }

    //
    // InvalidRequestException 처리
    //
    // HTTP 400 Bad Request 응답을 반환합니다.
    //
    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(
        ex: InvalidRequestException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid request: {}", ex.message)

        val error = ErrorResponse(
            message = ex.message ?: "유효하지 않은 요청입니다.",
            errorCode = "INVALID_REQUEST",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    //
    // DuplicateResourceException 처리
    //
    // HTTP 409 Conflict 응답을 반환합니다.
    //
    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(
        ex: DuplicateResourceException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Duplicate resource: {}", ex.message)

        val error = ErrorResponse(
            message = ex.message ?: "중복된 데이터가 존재합니다.",
            errorCode = "DUPLICATE_RESOURCE",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error)
    }

    //
    // 기타 모든 예외 처리
    //
    // HTTP 500 Internal Server Error 응답을 반환합니다.
    //
    // ## WHY 별도 처리?
    //
    // - 예상하지 못한 예외 발생 시 안전장치 역할
    // - Stack Trace 노출 방지 (보안)
    // - 사용자에게는 간단한 메시지, 서버 로그에는 상세 정보
    //
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)

        val error = ErrorResponse(
            message = "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
            errorCode = "INTERNAL_SERVER_ERROR",
            path = request.getDescription(false).removePrefix("uri=")
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }
}
