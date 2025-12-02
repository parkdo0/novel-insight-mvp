package com.sparta.novel_insight.controller

import com.sparta.novel_insight.domain.Novel
import com.sparta.novel_insight.repository.NovelRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class NovelController(
    private val novelRepository: NovelRepository // 생성자 주입 (Autowired 필요 없음)
) {

    @GetMapping("/novels")
    fun getNovels(): List<Novel> {
        // 페이징 처리: 최신순으로 10개만 가져오기 (100만 건 다 가져오면 서버 터집니다)
        val pageable = PageRequest.of(0, 10, Sort.by("id").descending())

        // [범인] 혹시 아직도 이렇게 쓰고 계신가요?
        // return novelRepository.findAll(pageable).content

        // [정답] 우리가 만든 "Fetch Join" 메소드를 불러야 합니다!
        return novelRepository.findAllWithReviews(pageable).content
    }

    @GetMapping("/novels/search")
    fun searchNovels(
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) author: String?,
        // [추가] 스프링이 알아서 page, size, sort 파라미터를 Pageable로 만들어줍니다.
        // 기본값: 0페이지, 10개씩
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<Page<Novel>> {

        val result = novelRepository.search(title, author, pageable)
        return ResponseEntity.ok(result)
    }

    // 기존 검색 API 아래에 추가
    @GetMapping("/novels/{id}")
    fun getNovelDetail(@PathVariable id: Long): ResponseEntity<Novel> {
        // (실무에선 DTO로 변환해야 하지만, 편의상 Entity 리턴)
        val novel = novelRepository.findById(id)
            .orElseThrow { RuntimeException("소설을 찾을 수 없습니다.") }
        return ResponseEntity.ok(novel)
    }
}