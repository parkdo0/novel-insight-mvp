package com.sparta.novel_insight.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class UserPrincipal(
    val id: Long,
    val email: String,
    val grantedAuthorities: Collection<GrantedAuthority> // ★ 이름 변경 (충돌 방지)
) : UserDetails {

    // ★ 여기서 명시적으로 연결 (절대 에러 안 남)
    override fun getAuthorities(): Collection<GrantedAuthority> = grantedAuthorities

    override fun getPassword(): String = ""
    override fun getUsername(): String = email

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}