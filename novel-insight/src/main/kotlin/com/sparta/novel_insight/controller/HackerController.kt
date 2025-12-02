package com.sparta.novel_insight.controller

import jakarta.persistence.EntityManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.Arrays // ★ 이 줄 추가 필요!

@RestController
class HackerController(
    private val entityManager: EntityManager // JPA의 심장 (직접 쿼리 날리기용)
) {

    // 로그인 요청용 DTO (내부 클래스로 간단히)
    data class LoginRequest(val email: String)

    @PostMapping("/login/vulnerable")
    fun dangerousLogin(@RequestBody request: LoginRequest): ResponseEntity<String> {
        val sql = "SELECT * FROM member WHERE email = '" + request.email + "'"

        // 결과는 Object 배열의 리스트로 나옵니다. (List<Object[]>)
        val result = entityManager.createNativeQuery(sql).resultList

        return if (result.isNotEmpty()) {
            // [수정 전] "${result[0]}" -> 주소값만 나옴
            // [수정 후] Arrays.toString() -> 내용물을 까서 보여줌!
            val stolenData = result[0] as Array<*>
            ResponseEntity.ok("로그인 성공! (훔친 정보: ${Arrays.toString(stolenData)})")
        } else {
            ResponseEntity.status(401).body("로그인 실패")
        }
    }
}