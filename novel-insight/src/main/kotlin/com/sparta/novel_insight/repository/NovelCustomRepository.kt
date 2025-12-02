package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Novel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface NovelCustomRepository {
    // [수정] Pageable 추가, Return 타입 Page<Novel>로 변경
    fun search(title: String?, author: String?, pageable: Pageable): Page<Novel>
}