package com.sparta.novel_insight.config

import com.sparta.novel_insight.common.JwtPlugin
import com.sparta.novel_insight.security.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtPlugin: JwtPlugin
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val jwt = getJwtFromRequest(request)

        if (jwt != null) {
            jwtPlugin.validateToken(jwt).onSuccess { claims ->
                val userId = claims.body.subject.toLong()
                val email = claims.body["email"] as String
                val role = claims.body["role"] as String

                // ★ [수정] 위에서 바꾼 변수명(grantedAuthorities)에 맞춰서 주입
                val principal = UserPrincipal(
                    id = userId,
                    email = email,
                    grantedAuthorities = setOf(SimpleGrantedAuthority("ROLE_$role"))
                )

                // Authentication 객체 생성 (여기서는 getAuthorities()를 호출하므로 문제없음)
                val authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION)
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}