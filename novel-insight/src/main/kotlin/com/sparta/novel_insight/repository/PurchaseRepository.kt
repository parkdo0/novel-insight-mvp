package com.sparta.novel_insight.repository

import com.sparta.novel_insight.domain.Purchase
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PurchaseRepository : JpaRepository<Purchase, Long> {

    fun existsByUserIdAndEpisodeId(userId: Long, episodeId: Long): Boolean

    // ★ [추가] "이 유저가 산 에피소드 ID 리스트를 다 줘!" (소설 ID는 Join으로 필터링 등 가능하지만, 여기선 간단히 유저 기준 전체 조회 후 메모리 필터링하거나, IN 쿼리 사용)
    // 성능 최적화를 위해 "현재 조회하려는 에피소드 ID 목록" 중에 산 것만 찾습니다.
    @Query("SELECT p.episodeId FROM Purchase p WHERE p.userId = :userId AND p.episodeId IN :episodeIds")
    fun findPurchasedEpisodeIds(@Param("userId") userId: Long, @Param("episodeIds") episodeIds: List<Long>): List<Long>
}