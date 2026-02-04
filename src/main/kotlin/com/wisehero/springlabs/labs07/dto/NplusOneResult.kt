package com.wisehero.springlabs.labs07.dto

/**
 * Lab 07: N+1 Problem 실험 결과 DTO
 *
 * 각 실험의 SQL 실행 횟수와 N+1 발생 여부를 구조화하여 반환합니다.
 */
data class NplusOneResult(
    val experimentId: String,
    val experimentName: String,
    val description: String,
    val teamCount: Int? = null,
    val memberCount: Int? = null,
    val tagCount: Int? = null,
    val sqlCount: Long? = null,
    val expectedSqlCount: Long? = null,
    val nPlusOneOccurred: Boolean = false,
    val exceptionOccurred: Boolean = false,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
    val details: Map<String, Any> = emptyMap(),
    val conclusion: String
) {
    companion object {

        // 실험 7-1: @OneToMany N+1 관찰
        fun basicNPlusOne(
            teamCount: Int,
            memberCount: Int,
            sqlCount: Long,
            details: Map<String, Any> = emptyMap()
        ) = NplusOneResult(
            experimentId = "7-1",
            experimentName = "@OneToMany N+1 문제 관찰",
            description = "Team 전체 조회 후 각 Team의 members에 접근하여 N+1 쿼리 발생을 확인",
            teamCount = teamCount,
            memberCount = memberCount,
            sqlCount = sqlCount,
            expectedSqlCount = 1L + teamCount,
            nPlusOneOccurred = true,
            details = details,
            conclusion = "N+1 발생! Team ${teamCount}건 조회 시 SQL ${sqlCount}회 실행 (1+N=${1 + teamCount}). " +
                "findAll로 Team을 조회한 후 각 Team의 members(LAZY)에 접근할 때마다 " +
                "추가 SELECT가 발생합니다."
        )

        // 실험 7-2: JPQL JOIN FETCH 해결
        fun joinFetch(
            teamCount: Int,
            memberCount: Int,
            sqlCount: Long,
            details: Map<String, Any> = emptyMap()
        ) = NplusOneResult(
            experimentId = "7-2",
            experimentName = "JPQL JOIN FETCH로 N+1 해결",
            description = "JOIN FETCH로 Team과 Member를 한 번에 조회하여 N+1 문제를 해결",
            teamCount = teamCount,
            memberCount = memberCount,
            sqlCount = sqlCount,
            expectedSqlCount = 1L,
            nPlusOneOccurred = false,
            details = details,
            conclusion = "N+1 해결! JOIN FETCH로 SQL ${sqlCount}회만 실행. " +
                "Team ${teamCount}건과 Member ${memberCount}건을 INNER JOIN 1회로 모두 로딩. " +
                "이후 members 접근 시 추가 쿼리 없음."
        )

        // 실험 7-3: @EntityGraph 해결
        fun entityGraph(
            teamCount: Int,
            memberCount: Int,
            sqlCount: Long,
            details: Map<String, Any> = emptyMap()
        ) = NplusOneResult(
            experimentId = "7-3",
            experimentName = "@EntityGraph로 N+1 해결",
            description = "@EntityGraph(attributePaths)로 Team과 Member를 LEFT JOIN으로 한 번에 조회",
            teamCount = teamCount,
            memberCount = memberCount,
            sqlCount = sqlCount,
            expectedSqlCount = 1L,
            nPlusOneOccurred = false,
            details = details,
            conclusion = "N+1 해결! @EntityGraph로 SQL ${sqlCount}회만 실행. " +
                "LEFT OUTER JOIN으로 Team ${teamCount}건과 Member ${memberCount}건 로딩. " +
                "JOIN FETCH와 달리 JPQL을 수정하지 않고 어노테이션만으로 해결."
        )

        // 실험 7-4: @ManyToOne N+1 관찰 및 해결
        fun manyToOneNPlusOne(
            memberCount: Int,
            teamCount: Int,
            nPlusOneSqlCount: Long,
            joinFetchSqlCount: Long,
            details: Map<String, Any> = emptyMap()
        ) = NplusOneResult(
            experimentId = "7-4",
            experimentName = "@ManyToOne N+1 문제 관찰 및 해결",
            description = "Member 전체 조회 후 각 Member의 team에 접근하여 N+1 발생 → JOIN FETCH로 해결",
            teamCount = teamCount,
            memberCount = memberCount,
            sqlCount = nPlusOneSqlCount,
            expectedSqlCount = 1L + teamCount,
            nPlusOneOccurred = true,
            details = details,
            conclusion = "@ManyToOne에서도 N+1 발생! Member 조회 시 SQL ${nPlusOneSqlCount}회 → " +
                "JOIN FETCH 적용 후 ${joinFetchSqlCount}회로 감소. " +
                "LAZY 로딩된 team에 접근할 때마다 팀별 SELECT가 발생합니다."
        )

        // 실험 7-5: DTO Projection
        fun dtoProjection(
            resultCount: Int,
            sqlCount: Long,
            details: Map<String, Any> = emptyMap()
        ) = NplusOneResult(
            experimentId = "7-5",
            experimentName = "DTO Projection으로 N+1 회피",
            description = "JPQL 생성자 표현식으로 필요 데이터만 직접 조회하여 N+1 자체를 회피",
            memberCount = resultCount,
            sqlCount = sqlCount,
            expectedSqlCount = 1L,
            nPlusOneOccurred = false,
            details = details,
            conclusion = "N+1 원천 차단! DTO Projection으로 SQL ${sqlCount}회만 실행, ${resultCount}건 조회. " +
                "엔티티가 아닌 값을 직접 조회하므로 영속성 컨텍스트에 올리지 않고 " +
                "lazy loading 자체가 발생하지 않습니다."
        )

        // 실험 7-6: MultipleBagFetchException
        fun multipleBagFetch(
            teamCount: Int,
            memberCount: Int,
            tagCount: Int,
            exceptionOccurred: Boolean,
            exceptionType: String?,
            sequentialSqlCount: Long,
            details: Map<String, Any> = emptyMap()
        ) = NplusOneResult(
            experimentId = "7-6",
            experimentName = "MultipleBagFetchException과 순차 Fetch 해결",
            description = "2개 List 컬렉션을 동시 JOIN FETCH → 예외 발생 → 순차 fetch로 해결",
            teamCount = teamCount,
            memberCount = memberCount,
            tagCount = tagCount,
            sqlCount = sequentialSqlCount,
            expectedSqlCount = 2L,
            nPlusOneOccurred = false,
            exceptionOccurred = exceptionOccurred,
            exceptionType = exceptionType,
            details = details,
            conclusion = if (exceptionOccurred)
                "MultipleBagFetchException 발생 확인! 2개 List 컬렉션 동시 JOIN FETCH는 불가. " +
                    "순차 fetch로 해결: 1차(members) + 2차(tags) = SQL ${sequentialSqlCount}회. " +
                    "Hibernate는 카테시안 곱 방지를 위해 2개 이상 Bag(List) 동시 fetch를 금지합니다."
            else
                "예외 미발생 (Set 사용 등으로 Bag이 아님). 순차 fetch로 SQL ${sequentialSqlCount}회 실행."
        )
    }
}
