package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Novel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

// <Entity클래스, PK타입>
interface NovelRepository : JpaRepository<Novel, Long>, NovelCustomRepository {
    // 텅 비어 있어도 기본적인 CRUD(저장, 조회, 수정, 삭제)가 다 들어있습니다.
    // ▼▼▼ 이 부분이 빠져 있어서 에러가 난 겁니다! ▼▼▼
    @Query("SELECT n FROM Novel n LEFT JOIN FETCH n.reviews")
    fun findAllWithReviews(pageable: Pageable): Page<Novel>

    // 작가별 소설 조회
    fun findByAuthorId(authorId: Long): List<Novel>
}