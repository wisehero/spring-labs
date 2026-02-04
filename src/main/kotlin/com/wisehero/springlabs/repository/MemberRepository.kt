package com.wisehero.springlabs.repository

import com.wisehero.springlabs.entity.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberRepository : JpaRepository<Member, Long> {

    // Lab 07-4: 기본 조회 (@ManyToOne N+1 발생 관찰용)
    @Query("SELECT m FROM Member m WHERE m.name LIKE :prefix%")
    fun findAllByNamePrefix(@Param("prefix") prefix: String): List<Member>

    // Lab 07-4: JOIN FETCH로 @ManyToOne N+1 해결
    @Query("SELECT m FROM Member m JOIN FETCH m.team WHERE m.name LIKE :prefix%")
    fun findAllWithTeamByJoinFetch(@Param("prefix") prefix: String): List<Member>

    // Lab 07: 테스트 데이터 정리용
    fun deleteByNameStartingWith(prefix: String): Long
}
