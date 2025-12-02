package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {

    // ▼▼▼ 이 줄을 추가해주세요! ▼▼▼
    // JPA의 마법: 함수 이름만 "findBy + 컬럼명"으로 지으면, 알아서 SQL을 짜줍니다.
    // 반환타입 Member?: 해당 이메일이 없을 수도 있으니까 nullable(?)로 받습니다.
    fun findByEmail(email: String): Member?
}