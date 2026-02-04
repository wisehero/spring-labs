package com.wisehero.springlabs.repository

import com.wisehero.springlabs.entity.TeamTag
import org.springframework.data.jpa.repository.JpaRepository

interface TeamTagRepository : JpaRepository<TeamTag, Long> {

    // Lab 07: 테스트 데이터 정리용
    fun deleteByTagNameStartingWith(prefix: String): Long
}
