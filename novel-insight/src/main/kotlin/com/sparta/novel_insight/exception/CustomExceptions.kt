package com.sparta.novel_insight.exception

//
// # Custom Exception Classes
//
// 비즈니스 로직에서 발생하는 도메인별 예외를 정의합니다.
//
// ## WHY Custom Exception?
//
// ### 문제: 일반 예외는 의미 전달이 불명확
//
// ```kotlin
// // ❌ 나쁜 예: 일반 예외
// val member = memberRepository.findById(id)
//     ?: throw RuntimeException("Member not found") // 어떤 예외인지 불명확
// ```
//
// ### 해결: 타입 기반 예외 처리
//
// ```kotlin
// // ✅ 좋은 예: 커스텀 예외
// val member = memberRepository.findById(id)
//     ?: throw MemberNotFoundException(id) // 명확한 의미 전달
// ```
//
// ## 실무 활용
//
// ### 1. 컨트롤러에서 예외 발생
//
// ```kotlin
// @GetMapping("/members/{id}")
// fun getMember(@PathVariable id: Long): MemberDto {
//     val member = memberRepository.findById(id).orElseThrow {
//         MemberNotFoundException(id)
//     }
//     return member.toDto()
// }
// ```
//
// ### 2. @ControllerAdvice에서 전역 처리
//
// ```kotlin
// @ExceptionHandler(MemberNotFoundException::class)
// fun handleMemberNotFound(ex: MemberNotFoundException): ResponseEntity<ErrorResponse> {
//     return ResponseEntity.status(404).body(
//         ErrorResponse(
//             message = ex.message,
//             errorCode = "MEMBER_NOT_FOUND"
//         )
//     )
// }
// ```
//
// ### 3. 클라이언트는 일관된 에러 응답 수신
//
// ```json
// {
//   "message": "회원을 찾을 수 없습니다. (ID: 123)",
//   "errorCode": "MEMBER_NOT_FOUND",
//   "timestamp": "2025-12-06T10:30:45"
// }
// ```
//

//
// 회원을 찾을 수 없을 때 발생하는 예외
//
// @param id 찾으려고 한 회원 ID
//
// ## 사용 예시
//
// ```kotlin
// val member = memberRepository.findById(memberId)
//     ?: throw MemberNotFoundException(memberId)
// ```
//
class MemberNotFoundException(
    val id: Long
) : RuntimeException("회원을 찾을 수 없습니다. (ID: $id)")

//
// 소설을 찾을 수 없을 때 발생하는 예외
//
// @param id 찾으려고 한 소설 ID
//
class NovelNotFoundException(
    val id: Long
) : RuntimeException("소설을 찾을 수 없습니다. (ID: $id)")

//
// 에피소드를 찾을 수 없을 때 발생하는 예외
//
// @param id 찾으려고 한 에피소드 ID
//
class EpisodeNotFoundException(
    val id: Long
) : RuntimeException("에피소드를 찾을 수 없습니다. (ID: $id)")

//
// 알림을 찾을 수 없을 때 발생하는 예외
//
// @param id 찾으려고 한 알림 ID
//
class NotificationNotFoundException(
    val id: Long
) : RuntimeException("알림을 찾을 수 없습니다. (ID: $id)")

//
// 권한이 없을 때 발생하는 예외
//
// @param message 권한 부족 상세 메시지
//
// ## 사용 예시
//
// ```kotlin
// // 소설 작가만 수정 가능
// if (novel.authorId != currentUserId) {
//     throw UnauthorizedException("이 소설을 수정할 권한이 없습니다.")
// }
// ```
//
class UnauthorizedException(
    message: String
) : RuntimeException(message)

//
// 유효하지 않은 요청 데이터일 때 발생하는 예외
//
// @param message 유효성 검증 실패 메시지
//
// ## 사용 예시
//
// ```kotlin
// if (password.length < 8) {
//     throw InvalidRequestException("비밀번호는 최소 8자 이상이어야 합니다.")
// }
// ```
//
class InvalidRequestException(
    message: String
) : RuntimeException(message)

//
// 중복된 데이터가 있을 때 발생하는 예외
//
// @param message 중복 데이터 상세 메시지
//
// ## 사용 예시
//
// ```kotlin
// if (memberRepository.existsByEmail(email)) {
//     throw DuplicateResourceException("이미 사용 중인 이메일입니다: $email")
// }
// ```
//
class DuplicateResourceException(
    message: String
) : RuntimeException(message)
