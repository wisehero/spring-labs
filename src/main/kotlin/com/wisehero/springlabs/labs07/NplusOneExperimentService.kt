package com.wisehero.springlabs.labs07

import com.wisehero.springlabs.entity.Member
import com.wisehero.springlabs.entity.Team
import com.wisehero.springlabs.entity.TeamTag
import com.wisehero.springlabs.labs07.dto.NplusOneResult
import com.wisehero.springlabs.labs07.dto.TeamMemberDto
import com.wisehero.springlabs.repository.MemberRepository
import com.wisehero.springlabs.repository.TeamRepository
import com.wisehero.springlabs.repository.TeamTagRepository
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ==========================================
 * Lab 07: N+1 Problem 실험
 * ==========================================
 *
 * Team(1) → Member(N), Team(1) → TeamTag(N) 연관관계로
 * N+1 문제 발생과 해결 방법을 실험합니다.
 *
 * 실험 목록:
 * 7-1: @OneToMany N+1 문제 관찰
 * 7-2: JPQL JOIN FETCH로 해결
 * 7-3: @EntityGraph로 해결
 * 7-4: @ManyToOne N+1 문제 관찰 및 해결
 * 7-5: DTO Projection으로 N+1 회피
 * 7-6: MultipleBagFetchException과 순차 Fetch 해결
 */
@Service
class NplusOneExperimentService(
    private val teamRepository: TeamRepository,
    private val memberRepository: MemberRepository,
    private val teamTagRepository: TeamTagRepository,
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TEST_PREFIX = "NPLUS-"
        const val TEAM_COUNT = 5
        const val MEMBERS_PER_TEAM = 3
        const val TAGS_PER_TEAM = 2
    }

    // ==========================================
    // SQL 측정 유틸리티
    // ==========================================

    private fun getSessionFactory(): SessionFactory =
        entityManager.entityManagerFactory.unwrap(SessionFactory::class.java)

    private fun clearStatistics() {
        getSessionFactory().statistics.clear()
    }

    private fun getQueryCount(): Long =
        getSessionFactory().statistics.queryExecutionCount

    // ==========================================
    // 테스트 데이터 생성
    // ==========================================

    private fun createTestData(prefix: String, withTags: Boolean = false): List<Team> {
        val teams = mutableListOf<Team>()
        for (i in 1..TEAM_COUNT) {
            val team = Team(name = "${prefix}Team-$i")
            for (j in 1..MEMBERS_PER_TEAM) {
                val member = Member(
                    name = "${prefix}Member-$i-$j",
                    role = if (j == 1) "LEADER" else "MEMBER",
                    team = team
                )
                team.members.add(member)
            }
            if (withTags) {
                for (k in 1..TAGS_PER_TEAM) {
                    val tag = TeamTag(
                        tagName = "${prefix}Tag-$i-$k",
                        team = team
                    )
                    team.tags.add(tag)
                }
            }
            teams.add(team)
        }
        return teamRepository.saveAllAndFlush(teams)
    }

    // ==========================================
    // 실험 7-1: @OneToMany N+1 문제 관찰
    // ==========================================

    @Transactional
    fun experiment7_1_basicNPlusOne(): NplusOneResult {
        val prefix = "${TEST_PREFIX}7-1-"

        // 1. 테스트 데이터 생성
        val savedTeams = createTestData(prefix)
        log.info("[7-1] 테스트 데이터 생성: Team ${savedTeams.size}건, 각 ${MEMBERS_PER_TEAM}명 Member")

        // 2. 1차 캐시 초기화 (LAZY 로딩이 확실히 발생하도록)
        entityManager.flush()
        entityManager.clear()

        // 3. SQL 카운터 초기화
        clearStatistics()

        // 4. Team 전체 조회 (1 SQL: SELECT FROM team)
        val teams = teamRepository.findAllByNamePrefix(prefix)
        log.info("[7-1] findAll 실행 → Team ${teams.size}건 조회")

        // 5. 각 Team의 members에 접근 → N개 추가 SQL 발생!
        var totalMembers = 0
        teams.forEach { team ->
            val memberCount = team.members.size  // LAZY 로딩 트리거!
            totalMembers += memberCount
            log.info("[7-1]   Team '${team.name}' → members ${memberCount}명 (추가 SELECT 발생)")
        }

        // 6. SQL 실행 횟수 확인
        val sqlCount = getQueryCount()
        log.info("[7-1] 총 SQL 실행 횟수: ${sqlCount} (예상: 1+${teams.size}=${1 + teams.size})")

        return NplusOneResult.basicNPlusOne(
            teamCount = teams.size,
            memberCount = totalMembers,
            sqlCount = sqlCount,
            details = mapOf(
                "prefix" to prefix,
                "sqlBreakdown" to "1(Team 조회) + ${teams.size}(각 Team의 members 조회) = ${1 + teams.size}",
                "fetchType" to "LAZY (기본값)",
                "triggerPoint" to "team.members.size 접근 시점"
            )
        )
    }

    // ==========================================
    // 실험 7-2: JPQL JOIN FETCH로 해결
    // ==========================================

    @Transactional
    fun experiment7_2_joinFetch(): NplusOneResult {
        val prefix = "${TEST_PREFIX}7-2-"

        val savedTeams = createTestData(prefix)
        log.info("[7-2] 테스트 데이터 생성: Team ${savedTeams.size}건")

        entityManager.flush()
        entityManager.clear()
        clearStatistics()

        // JOIN FETCH로 Team과 Member를 한 번에 조회
        val teams = teamRepository.findAllWithMembersByJoinFetch(prefix)
        log.info("[7-2] JOIN FETCH 실행 → Team ${teams.size}건 조회")

        // members 접근 시 추가 SQL 없음!
        var totalMembers = 0
        teams.forEach { team ->
            val memberCount = team.members.size  // 이미 로딩됨 → SQL 없음
            totalMembers += memberCount
            log.info("[7-2]   Team '${team.name}' → members ${memberCount}명 (추가 SQL 없음)")
        }

        val sqlCount = getQueryCount()
        log.info("[7-2] 총 SQL 실행 횟수: ${sqlCount} (예상: 1)")

        return NplusOneResult.joinFetch(
            teamCount = teams.size,
            memberCount = totalMembers,
            sqlCount = sqlCount,
            details = mapOf(
                "prefix" to prefix,
                "jpql" to "SELECT DISTINCT t FROM Team t JOIN FETCH t.members WHERE t.name LIKE :prefix%",
                "joinType" to "INNER JOIN",
                "distinct" to "카테시안 곱 중복 제거를 위해 DISTINCT 필수"
            )
        )
    }

    // ==========================================
    // 실험 7-3: @EntityGraph로 해결
    // ==========================================

    @Transactional
    fun experiment7_3_entityGraph(): NplusOneResult {
        val prefix = "${TEST_PREFIX}7-3-"

        val savedTeams = createTestData(prefix)
        log.info("[7-3] 테스트 데이터 생성: Team ${savedTeams.size}건")

        entityManager.flush()
        entityManager.clear()
        clearStatistics()

        // @EntityGraph로 Team과 Member를 LEFT JOIN으로 한 번에 조회
        val teams = teamRepository.findAllWithMembersByEntityGraph(prefix)
        log.info("[7-3] @EntityGraph 실행 → Team ${teams.size}건 조회")

        var totalMembers = 0
        teams.forEach { team ->
            val memberCount = team.members.size
            totalMembers += memberCount
            log.info("[7-3]   Team '${team.name}' → members ${memberCount}명 (추가 SQL 없음)")
        }

        val sqlCount = getQueryCount()
        log.info("[7-3] 총 SQL 실행 횟수: ${sqlCount} (예상: 1)")

        return NplusOneResult.entityGraph(
            teamCount = teams.size,
            memberCount = totalMembers,
            sqlCount = sqlCount,
            details = mapOf(
                "prefix" to prefix,
                "annotation" to "@EntityGraph(attributePaths = [\"members\"])",
                "joinType" to "LEFT OUTER JOIN (EntityGraph 기본 동작)",
                "advantage" to "JPQL 수정 없이 어노테이션만으로 fetch 전략 변경 가능"
            )
        )
    }

    // ==========================================
    // 실험 7-4: @ManyToOne N+1 관찰 및 해결
    // ==========================================

    @Transactional
    fun experiment7_4_manyToOneNPlusOne(): NplusOneResult {
        val prefix = "${TEST_PREFIX}7-4-"

        val savedTeams = createTestData(prefix)
        val totalMembers = savedTeams.sumOf { it.members.size }
        log.info("[7-4] 테스트 데이터 생성: Team ${savedTeams.size}건, Member ${totalMembers}건")

        // === Part 1: N+1 발생 관찰 ===
        entityManager.flush()
        entityManager.clear()
        clearStatistics()

        val members = memberRepository.findAllByNamePrefix(prefix)
        log.info("[7-4] Part 1: Member findAll → ${members.size}건 조회")

        // 각 Member의 team에 접근 → 팀별로 추가 SELECT 발생
        val teamNames = mutableSetOf<String>()
        members.forEach { member ->
            val teamName = member.team.name  // LAZY 로딩 트리거!
            teamNames.add(teamName)
        }
        log.info("[7-4] Part 1: ${teamNames.size}개 팀 접근 → 추가 SQL 발생")

        val nPlusOneSqlCount = getQueryCount()
        log.info("[7-4] Part 1 SQL 실행 횟수: ${nPlusOneSqlCount} (1+고유팀수=${1 + teamNames.size})")

        // === Part 2: JOIN FETCH로 해결 ===
        entityManager.flush()
        entityManager.clear()
        clearStatistics()

        val membersWithTeam = memberRepository.findAllWithTeamByJoinFetch(prefix)
        log.info("[7-4] Part 2: JOIN FETCH → ${membersWithTeam.size}건 조회")

        // team 접근 시 추가 SQL 없음
        membersWithTeam.forEach { member ->
            member.team.name  // 이미 로딩됨
        }

        val joinFetchSqlCount = getQueryCount()
        log.info("[7-4] Part 2 SQL 실행 횟수: ${joinFetchSqlCount} (예상: 1)")

        return NplusOneResult.manyToOneNPlusOne(
            memberCount = members.size,
            teamCount = teamNames.size,
            nPlusOneSqlCount = nPlusOneSqlCount,
            joinFetchSqlCount = joinFetchSqlCount,
            details = mapOf(
                "prefix" to prefix,
                "part1_description" to "Member findAll → 각 member.team.name 접근 → N+1 발생",
                "part1_sqlCount" to nPlusOneSqlCount,
                "part2_description" to "JOIN FETCH m.team → 1 SQL로 해결",
                "part2_sqlCount" to joinFetchSqlCount,
                "uniqueTeamCount" to teamNames.size,
                "note" to "@ManyToOne LAZY도 접근 시 개별 SELECT가 발생하여 N+1 문제 동일"
            )
        )
    }

    // ==========================================
    // 실험 7-5: DTO Projection으로 N+1 회피
    // ==========================================

    @Transactional
    fun experiment7_5_dtoProjection(): NplusOneResult {
        val prefix = "${TEST_PREFIX}7-5-"

        val savedTeams = createTestData(prefix)
        log.info("[7-5] 테스트 데이터 생성: Team ${savedTeams.size}건")

        entityManager.flush()
        entityManager.clear()
        clearStatistics()

        // JPQL 생성자 표현식으로 DTO 직접 조회
        val dtos = entityManager.createQuery(
            """
            SELECT new com.wisehero.springlabs.labs07.dto.TeamMemberDto(
                t.name, m.name, m.role
            )
            FROM Team t JOIN t.members m
            WHERE t.name LIKE :prefix
            ORDER BY t.name, m.name
            """.trimIndent(),
            TeamMemberDto::class.java
        ).setParameter("prefix", "$prefix%")
            .resultList

        log.info("[7-5] DTO Projection → ${dtos.size}건 조회")
        dtos.forEach { dto ->
            log.info("[7-5]   Team='${dto.teamName}', Member='${dto.memberName}', Role='${dto.memberRole}'")
        }

        val sqlCount = getQueryCount()
        log.info("[7-5] 총 SQL 실행 횟수: ${sqlCount} (예상: 1)")

        return NplusOneResult.dtoProjection(
            resultCount = dtos.size,
            sqlCount = sqlCount,
            details = mapOf(
                "prefix" to prefix,
                "jpql" to "SELECT new TeamMemberDto(t.name, m.name, m.role) FROM Team t JOIN t.members m",
                "dtoSample" to if (dtos.isNotEmpty()) dtos.first() else "empty",
                "advantage" to "엔티티를 영속성 컨텍스트에 올리지 않으므로 lazy loading 자체가 불가능",
                "tradeoff" to "필요한 필드만 조회하여 메모리 효율적이지만, 엔티티 수정 불가"
            )
        )
    }

    // ==========================================
    // 실험 7-6: MultipleBagFetchException
    // ==========================================

    @Transactional
    fun experiment7_6_multipleBagFetch(): NplusOneResult {
        val prefix = "${TEST_PREFIX}7-6-"

        // Tag 포함 데이터 생성
        val savedTeams = createTestData(prefix, withTags = true)
        val totalMembers = savedTeams.sumOf { it.members.size }
        val totalTags = savedTeams.sumOf { it.tags.size }
        log.info("[7-6] 테스트 데이터 생성: Team ${savedTeams.size}건, Member ${totalMembers}건, Tag ${totalTags}건")

        entityManager.flush()
        entityManager.clear()

        // === Part 1: 2개 List 동시 JOIN FETCH → MultipleBagFetchException 예외 ===
        var exceptionOccurred = false
        var exceptionType: String? = null
        var exceptionMessage: String? = null

        try {
            log.info("[7-6] Part 1: 2개 List 동시 JOIN FETCH 시도...")
            teamRepository.findAllWithMembersAndTagsByJoinFetch(prefix)
            log.info("[7-6] Part 1: 예외 미발생 (예상과 다름)")
        } catch (e: Exception) {
            exceptionOccurred = true
            // 원인 예외 추출 (Spring이 래핑할 수 있음)
            var cause: Throwable? = e
            while (cause != null) {
                if (cause::class.simpleName?.contains("MultipleBagFetchException") == true) {
                    exceptionType = cause::class.simpleName
                    exceptionMessage = cause.message?.take(200)
                    break
                }
                cause = cause.cause
            }
            if (exceptionType == null) {
                exceptionType = e::class.simpleName
                exceptionMessage = e.message?.take(200)
            }
            log.info("[7-6] Part 1: 예외 발생! $exceptionType: $exceptionMessage")
        }

        // === Part 2: 순차 Fetch로 해결 ===
        entityManager.clear()
        clearStatistics()

        log.info("[7-6] Part 2: 순차 fetch 시작...")

        // 1단계: members만 JOIN FETCH
        val teamsWithMembers = teamRepository.findAllWithMembersOnly(prefix)
        log.info("[7-6] Part 2-1: members JOIN FETCH → Team ${teamsWithMembers.size}건")

        // 2단계: 이미 로딩된 Team의 ID로 tags JOIN FETCH
        val teamIds = teamsWithMembers.map { it.id!! }
        val teamsWithTags = teamRepository.findAllWithTagsByIds(teamIds)
        log.info("[7-6] Part 2-2: tags JOIN FETCH → Team ${teamsWithTags.size}건")

        // 검증: 모든 데이터가 로딩되었는지 확인
        var verifiedMembers = 0
        var verifiedTags = 0
        teamsWithMembers.forEach { team ->
            verifiedMembers += team.members.size
            verifiedTags += team.tags.size  // 2단계에서 로딩됨
        }
        log.info("[7-6] Part 2 검증: members=${verifiedMembers}건, tags=${verifiedTags}건")

        val sequentialSqlCount = getQueryCount()
        log.info("[7-6] Part 2 SQL 실행 횟수: ${sequentialSqlCount} (예상: 2)")

        return NplusOneResult.multipleBagFetch(
            teamCount = teamsWithMembers.size,
            memberCount = verifiedMembers,
            tagCount = verifiedTags,
            exceptionOccurred = exceptionOccurred,
            exceptionType = exceptionType,
            sequentialSqlCount = sequentialSqlCount,
            details = mapOf(
                "prefix" to prefix,
                "part1_exception" to (exceptionType ?: "none"),
                "part1_message" to (exceptionMessage ?: "none"),
                "part2_strategy" to "순차 fetch: 1차(members JOIN FETCH) + 2차(tags JOIN FETCH by IDs)",
                "part2_sqlCount" to sequentialSqlCount,
                "whyException" to "Hibernate는 2개 이상 List(Bag) 타입 컬렉션의 동시 JOIN FETCH를 금지. " +
                    "카테시안 곱으로 인한 데이터 폭증과 중복 문제 방지.",
                "alternatives" to listOf(
                    "1. 순차 fetch (이 실험의 해결책)",
                    "2. List 대신 Set 사용 (Bag → Set으로 변경)",
                    "3. @BatchSize로 IN 절 배치 로딩"
                )
            )
        )
    }

    // ==========================================
    // 정리
    // ==========================================

    @Transactional
    fun cleanupTestData(): Int {
        val deletedTeams = teamRepository.deleteByNameStartingWith(TEST_PREFIX)
        if (deletedTeams > 0) {
            log.info("Lab 07 테스트 데이터 삭제: Team ${deletedTeams}건 (cascade로 Member, TeamTag 포함)")
        }
        return deletedTeams.toInt()
    }
}
