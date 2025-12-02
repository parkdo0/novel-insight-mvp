package com.sparta.novel_insight.common

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.*

@Component
class JwtPlugin(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.issuer}") private val issuer: String,
    @Value("\${jwt.access-duration-hour}") private val accessDurationHour: Long
) {

    // 1. 토큰 생성 (로그인 성공 시 호출)
    fun generateAccessToken(subject: String, email: String, role: String): String {
        return generateToken(subject, email, role, Duration.ofHours(accessDurationHour))
    }

    private fun generateToken(subject: String, email: String, role: String, expirationPeriod: Duration): String {
        val claims: Claims = Jwts.claims()
            .setSubject(subject) // 보통 User ID (PK)를 넣습니다.
            .setIssuer(issuer)
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plus(expirationPeriod)))

        // 커스텀 정보 넣기 (이메일, 권한 등)
        claims["email"] = email
        claims["role"] = role

        val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

        return Jwts.builder()
            .setClaims(claims)
            .signWith(key) // 비밀키로 도장 꽝!
            .compact()
    }

    // 2. 토큰 검증 (요청 들어올 때마다 호출)
    fun validateToken(jwt: String): Result<Jws<Claims>> {
        return kotlin.runCatching {
            val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

            Jwts.parserBuilder()
                .setSigningKey(key) // 비밀키로 서명 확인
                .build()
                .parseClaimsJws(jwt) // 파싱 성공하면 OK, 위조됐거나 만료되면 Exception 터짐
        }
    }
}