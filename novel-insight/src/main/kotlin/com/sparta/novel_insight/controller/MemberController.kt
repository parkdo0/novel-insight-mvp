package com.sparta.novel_insight.controller

import com.sparta.novel_insight.common.JwtPlugin
import com.sparta.novel_insight.domain.Member
import com.sparta.novel_insight.dto.LoginRequest
import com.sparta.novel_insight.dto.SignupRequest
import com.sparta.novel_insight.repository.MemberRepository
import com.sparta.novel_insight.repository.PointRepository
import com.sparta.novel_insight.security.UserPrincipal
import com.sparta.novel_insight.service.PointService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MemberController(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtPlugin: JwtPlugin,
    private val pointService: PointService,
    private val pointRepository: PointRepository
) {

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<String> {
        val encodedPassword = passwordEncoder.encode(request.password)

        val newMember = Member(
            email = request.email,
            password = encodedPassword,
            nickname = request.nickname
        )

        val savedMember = memberRepository.save(newMember)
        pointService.createWallet(savedMember.id!!)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body("회원가입 성공")
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<String> {
        val member = memberRepository.findByEmail(request.email)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("이메일 또는 비밀번호가 틀렸습니다.")

        if (!passwordEncoder.matches(request.password, member.password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("이메일 또는 비밀번호가 틀렸습니다.")
        }

        val token = jwtPlugin.generateAccessToken(
            subject = member.id.toString(),
            email = member.email,
            role = "USER"
        )

        return ResponseEntity.ok(token)
    }

    @GetMapping("/users/me")
    fun getMyInfo(@AuthenticationPrincipal user: UserPrincipal): ResponseEntity<Map<String, Any>> {
        val member = memberRepository.findById(user.id).orElseThrow()
        val point = pointRepository.findByMemberId(user.id)

        val response = mapOf(
            "id" to member.id!!, // ★ [추가] 내 ID를 내려줍니다.
            "email" to member.email,
            "nickname" to member.nickname,
            "point" to (point?.amount ?: 0)
        )
        return ResponseEntity.ok(response)
    }
}