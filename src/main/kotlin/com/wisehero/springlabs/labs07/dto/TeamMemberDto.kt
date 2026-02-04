package com.wisehero.springlabs.labs07.dto

/**
 * Lab 07-5: DTO Projection용
 *
 * JPQL 생성자 표현식(SELECT new ...)으로 직접 생성되며,
 * 엔티티를 거치지 않으므로 N+1 문제가 원천적으로 발생하지 않습니다.
 */
data class TeamMemberDto(
    val teamName: String,
    val memberName: String,
    val memberRole: String
)
