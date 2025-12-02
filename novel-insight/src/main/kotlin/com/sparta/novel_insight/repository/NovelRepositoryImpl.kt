package com.sparta.novel_insight.repository

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sparta.novel_insight.domain.Novel
import com.sparta.novel_insight.domain.QNovel
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class NovelRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : NovelCustomRepository {

    override fun search(title: String?, author: String?, pageable: Pageable): Page<Novel> {
        val novel = QNovel.novel

        // 1. 조건 목록 (재사용을 위해 변수로 뺌)
        val builder = queryFactory
            .selectFrom(novel)
            .where(
                eqTitle(title),
                eqAuthor(author)
            )

        // 2. 리스트 조회 (페이징 적용)
        val content = builder
            .offset(pageable.offset) // 몇 번째부터? (0, 10, 20...)
            .limit(pageable.pageSize.toLong()) // 몇 개 가져와? (10개)
            .fetch()

        // 3. 전체 개수 조회 (카운트 쿼리)
        // (Page 객체는 전체 페이지 수를 알아야 하니까 count 쿼리가 필요함)
        val total = queryFactory
            .select(novel.count())
            .from(novel)
            .where(
                eqTitle(title),
                eqAuthor(author)
            )
            .fetchOne() ?: 0L

        // 4. Page 객체로 감싸서 리턴
        return PageImpl(content, pageable, total)
    }

    // ... (eqTitle, eqAuthor 함수는 그대로 둠) ...
    private fun eqTitle(title: String?): BooleanExpression? {
        if (title.isNullOrEmpty()) return null
        return QNovel.novel.title.contains(title)
    }

    private fun eqAuthor(author: String?): BooleanExpression? {
        if (author.isNullOrEmpty()) return null
        return QNovel.novel.author.eq(author)
    }
}