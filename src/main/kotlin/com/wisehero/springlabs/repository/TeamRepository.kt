package com.wisehero.springlabs.repository

import com.wisehero.springlabs.entity.Team
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TeamRepository : JpaRepository<Team, Long> {

    // Lab 07-1: 기본 조회 (N+1 발생 관찰용)
    @Query("SELECT t FROM Team t WHERE t.name LIKE :prefix%")
    fun findAllByNamePrefix(@Param("prefix") prefix: String): List<Team>

    // Lab 07-2: JOIN FETCH로 N+1 해결
    @Query("SELECT DISTINCT t FROM Team t JOIN FETCH t.members WHERE t.name LIKE :prefix%")
    fun findAllWithMembersByJoinFetch(@Param("prefix") prefix: String): List<Team>

    // Lab 07-3: @EntityGraph로 N+1 해결
    @EntityGraph(attributePaths = ["members"])
    @Query("SELECT t FROM Team t WHERE t.name LIKE :prefix%")
    fun findAllWithMembersByEntityGraph(@Param("prefix") prefix: String): List<Team>

    // Lab 07-6: 2개 컬렉션 동시 JOIN FETCH (MultipleBagFetchException 유도)
    @Query("SELECT DISTINCT t FROM Team t JOIN FETCH t.members JOIN FETCH t.tags WHERE t.name LIKE :prefix%")
    fun findAllWithMembersAndTagsByJoinFetch(@Param("prefix") prefix: String): List<Team>

    // Lab 07-6: 순차 fetch 1단계 - members만
    @Query("SELECT DISTINCT t FROM Team t JOIN FETCH t.members WHERE t.name LIKE :prefix%")
    fun findAllWithMembersOnly(@Param("prefix") prefix: String): List<Team>

    // Lab 07-6: 순차 fetch 2단계 - tags만 (이미 로딩된 Team의 ID로)
    @Query("SELECT DISTINCT t FROM Team t JOIN FETCH t.tags WHERE t.id IN :ids")
    fun findAllWithTagsByIds(@Param("ids") ids: List<Long>): List<Team>

    // Lab 07: 테스트 데이터 정리용
    fun deleteByNameStartingWith(prefix: String): Long
}
