# Lab 07: N+1 Problem

## 개요

JPA에서 가장 빈번하게 발생하는 성능 문제인 **N+1 쿼리 문제**를 직접 재현하고, 다양한 해결 방법을 실험합니다.

N+1 문제란: 연관 엔티티를 조회할 때 **1번의 메인 쿼리** + **N번의 추가 쿼리**가 발생하여 데이터가 많을수록 성능이 급격히 저하되는 현상입니다.

### 실험 연관관계

```
Team (1) ──── (N) Member      ← @OneToMany / @ManyToOne LAZY
  │
  └──── (N) TeamTag           ← MultipleBagFetchException 실험용
```

## 핵심 개념

### N+1 문제 발생 메커니즘

```
1. entityManager.createQuery("SELECT t FROM Team t")  →  1 SQL (Team 목록)
2. team.getMembers().size()                            →  N SQL (각 Team의 members)
   ├─ SELECT * FROM member WHERE team_id = 1
   ├─ SELECT * FROM member WHERE team_id = 2
   ├─ SELECT * FROM member WHERE team_id = 3
   └─ ... (Team 수만큼 반복)
```

### 왜 발생하는가?

| 계층 | 동작 |
|------|------|
| **JPA/Hibernate** | `FetchType.LAZY`는 연관 컬렉션을 프록시로 감싸서 실제 접근 시점까지 로딩을 지연 |
| **PersistenceContext** | `team.members`에 접근하면 프록시가 `LazyInitializationException`을 방지하기 위해 개별 SELECT 실행 |
| **JDBC** | 각 Team마다 별도 PreparedStatement 실행 → DB 왕복 N회 |

### EAGER로 바꾸면 해결되는가?

**아닙니다.** `FetchType.EAGER`는 N+1을 해결하지 않고, 오히려 더 나쁜 결과를 초래합니다:
- 모든 조회에서 무조건 연관 엔티티를 로딩 (필요 없을 때도)
- JPQL 사용 시 EAGER여도 JOIN이 아닌 **추가 SELECT**로 로딩 (여전히 N+1)
- 성능 제어 불가능

### 해결 방법 비교

| 방법 | SQL 수 | JOIN 타입 | 특징 |
|------|--------|-----------|------|
| LAZY (기본) | 1+N | - | N+1 발생 |
| **JOIN FETCH** | 1 | INNER JOIN | JPQL 수정 필요, DISTINCT 필요 |
| **@EntityGraph** | 1 | LEFT OUTER JOIN | 어노테이션만으로 해결 |
| **DTO Projection** | 1 | JOIN | 엔티티 아닌 값 직접 조회 |
| **@BatchSize** | 1+⌈N/size⌉ | - | IN 절로 배치 로딩 |

## 실험 목록

| 실험 | 주제 | HTTP | 핵심 관찰 포인트 |
|------|------|------|----------------|
| 7-1 | @OneToMany N+1 관찰 | POST | findAll + members 루프 → 1+N SQL |
| 7-2 | JPQL JOIN FETCH 해결 | POST | JOIN FETCH → 1 SQL |
| 7-3 | @EntityGraph 해결 | POST | @EntityGraph → 1 SQL (LEFT JOIN) |
| 7-4 | @ManyToOne N+1 관찰 및 해결 | POST | Member findAll + team 접근 → N+1 → JOIN FETCH 해결 |
| 7-5 | DTO Projection으로 N+1 회피 | POST | 생성자 표현식으로 1 SQL |
| 7-6 | MultipleBagFetchException | POST | 2개 List 동시 JOIN FETCH → 예외 → 순차 fetch 해결 |

## 실험 상세

### 실험 7-1: @OneToMany N+1 문제 관찰

- **엔드포인트**: `POST /api/v1/experiments/n-plus-one/7-1/basic-n-plus-one`
- **동작**: Team 전체 조회 후 각 Team의 members에 접근하여 N+1 발생

#### 내부 동작 원리

```
호출 흐름:
1. teamRepository.findAllByNamePrefix(prefix)
   → Hibernate: SELECT t FROM Team t WHERE t.name LIKE ?
   → 실제 SQL: SELECT * FROM team WHERE name LIKE 'NPLUS-%'
   → 결과: 5개 Team 엔티티가 PersistenceContext에 로딩
   → members 필드: LazyInitializationProxy (아직 로딩 안 됨)

2. team.members.size (각 Team마다)
   → Hibernate가 프록시 초기화 감지
   → AbstractLazyInitializer.initialize() 호출
   → SELECT * FROM member WHERE team_id = ?
   → 결과: PersistenceContext에 Member 엔티티 추가
   → 이 과정이 Team 수(N)만큼 반복!
```

- **예상 결과**: SQL 실행 횟수 = 6 (1 + 5)
- **의미**: LAZY 로딩은 접근 시점마다 개별 쿼리를 실행하므로, 대량 데이터에서 심각한 성능 저하 유발

---

### 실험 7-2: JPQL JOIN FETCH로 N+1 해결

- **엔드포인트**: `POST /api/v1/experiments/n-plus-one/7-2/join-fetch`
- **동작**: `JOIN FETCH`로 Team과 Member를 한 번에 조회

#### 내부 동작 원리

```
JPQL:
  SELECT DISTINCT t FROM Team t JOIN FETCH t.members WHERE t.name LIKE ?

실제 SQL:
  SELECT DISTINCT t.*, m.*
  FROM team t
  INNER JOIN member m ON t.id = m.team_id
  WHERE t.name LIKE 'NPLUS-%'

Hibernate 처리:
1. 단일 SQL로 Team + Member 데이터를 모두 가져옴
2. ResultSet의 각 행을 Team 엔티티 + Member 엔티티로 변환
3. Team.members 컬렉션에 Member 직접 할당 (프록시 아닌 실제 컬렉션)
4. PersistenceContext에 모두 등록
5. 이후 team.members 접근 시 추가 SQL 없음 (이미 초기화됨)
```

**DISTINCT가 필요한 이유:**
- JOIN 결과는 카테시안 곱 → Team 1이 Member 3명이면 Team 1이 3행 반복
- DISTINCT가 없으면 `teams` 리스트에 동일 Team이 3번 포함됨
- Hibernate 6+에서는 `PASS_DISTINCT_THROUGH` 힌트가 기본 적용되어 SQL에는 DISTINCT 미포함, Hibernate 레벨에서만 중복 제거

- **예상 결과**: SQL 실행 횟수 = 1
- **의미**: 가장 널리 사용되는 N+1 해결책. INNER JOIN이므로 members가 없는 Team은 제외됨

---

### 실험 7-3: @EntityGraph로 N+1 해결

- **엔드포인트**: `POST /api/v1/experiments/n-plus-one/7-3/entity-graph`
- **동작**: `@EntityGraph`로 JPQL 수정 없이 fetch 전략 변경

#### 내부 동작 원리

```
Repository 메서드:
  @EntityGraph(attributePaths = ["members"])
  @Query("SELECT t FROM Team t WHERE t.name LIKE :prefix%")
  fun findAllWithMembersByEntityGraph(prefix: String): List<Team>

Hibernate 처리:
1. @EntityGraph → Hibernate가 LoadPlan에 "members" 속성을 EAGER fetch로 추가
2. 실제 SQL: LEFT OUTER JOIN 생성
   SELECT t.*, m.*
   FROM team t
   LEFT OUTER JOIN member m ON t.id = m.team_id
   WHERE t.name LIKE ?
3. LEFT OUTER JOIN이므로 members가 없는 Team도 포함됨 (JOIN FETCH와의 차이!)
```

**JOIN FETCH vs @EntityGraph:**

| 항목 | JOIN FETCH | @EntityGraph |
|------|-----------|-------------|
| JOIN 타입 | INNER JOIN | LEFT OUTER JOIN |
| JPQL 수정 | 필요 | 불필요 (어노테이션만) |
| members 없는 엔티티 | 제외됨 | 포함됨 |
| 재사용성 | 쿼리에 종속 | 같은 쿼리에 다른 Graph 적용 가능 |

- **예상 결과**: SQL 실행 횟수 = 1
- **의미**: JPQL을 수정하지 않고도 N+1을 해결할 수 있어 유지보수에 유리

---

### 실험 7-4: @ManyToOne N+1 관찰 및 해결

- **엔드포인트**: `POST /api/v1/experiments/n-plus-one/7-4/many-to-one-n-plus-one`
- **동작**: Member → Team 방향에서도 N+1 발생 → JOIN FETCH로 해결

#### 내부 동작 원리

```
Part 1 - N+1 발생:
1. memberRepository.findAllByNamePrefix(prefix)
   → SELECT * FROM member WHERE name LIKE ?
   → 15명 Member 로딩 (team 필드는 프록시)

2. member.team.name (각 Member마다)
   → 팀 프록시 초기화 → SELECT * FROM team WHERE id = ?
   → 단, 같은 팀의 두 번째 Member부터는 PersistenceContext 캐시 히트!
   → 실제 SQL: 1(Member 조회) + 5(고유 팀 수) = 6

Part 2 - JOIN FETCH 해결:
1. memberRepository.findAllWithTeamByJoinFetch(prefix)
   → SELECT m.*, t.* FROM member m JOIN team t ON m.team_id = t.id WHERE m.name LIKE ?
   → 1 SQL로 Member + Team 모두 로딩
```

**PersistenceContext 캐시 효과:**
- 같은 트랜잭션 내에서 이미 로딩된 엔티티는 1차 캐시에서 반환
- Member-1, Member-2, Member-3이 모두 Team-1이면 Team-1은 1번만 SELECT
- 따라서 N+1의 N은 "전체 Member 수"가 아닌 "고유 Team 수"

- **예상 결과**: Part 1: SQL 6회 (1+5) → Part 2: SQL 1회
- **의미**: `@ManyToOne`도 LAZY 설정 시 동일한 N+1 문제 발생

---

### 실험 7-5: DTO Projection으로 N+1 회피

- **엔드포인트**: `POST /api/v1/experiments/n-plus-one/7-5/dto-projection`
- **동작**: JPQL 생성자 표현식으로 필요 데이터만 직접 조회

#### 내부 동작 원리

```
JPQL:
  SELECT new com.wisehero.springlabs.labs07.dto.TeamMemberDto(
      t.name, m.name, m.role
  )
  FROM Team t JOIN t.members m
  WHERE t.name LIKE :prefix

실제 SQL:
  SELECT t.name, m.name, m.role
  FROM team t
  INNER JOIN member m ON t.id = m.team_id
  WHERE t.name LIKE ?

Hibernate 처리:
1. SELECT 절에 엔티티가 아닌 스칼라 값만 포함
2. ResultSet → TeamMemberDto 생성자로 직접 변환
3. PersistenceContext에 아무것도 등록하지 않음!
4. 엔티티가 아니므로 dirty checking, lazy loading 모두 불가
5. 따라서 N+1 문제가 원천적으로 발생할 수 없음
```

**DTO Projection의 장단점:**

| 장점 | 단점 |
|------|------|
| N+1 원천 차단 | 엔티티 수정 불가 |
| 필요 컬럼만 SELECT → 메모리/네트워크 절약 | 연관관계 탐색 불가 |
| PersistenceContext 오버헤드 없음 | JPQL에 DTO 전체 경로 명시 필요 |

- **예상 결과**: SQL 실행 횟수 = 1
- **의미**: 읽기 전용 조회에서는 DTO Projection이 가장 효율적

---

### 실험 7-6: MultipleBagFetchException과 순차 Fetch 해결

- **엔드포인트**: `POST /api/v1/experiments/n-plus-one/7-6/multiple-bag-fetch`
- **동작**: 2개 List 컬렉션 동시 JOIN FETCH → 예외 → 순차 fetch로 해결

#### 내부 동작 원리

```
Part 1 - 예외 발생:
JPQL: SELECT DISTINCT t FROM Team t JOIN FETCH t.members JOIN FETCH t.tags WHERE ...

Hibernate 처리:
1. t.members(List) → Bag 타입으로 인식
2. t.tags(List) → Bag 타입으로 인식
3. 2개 Bag 동시 fetch 감지 → MultipleBagFetchException 발생!

왜 예외를 던지는가?
- 2개 컬렉션을 JOIN하면 카테시안 곱 발생
- Team-1이 Member 3명 + Tag 2개면 → 3×2 = 6행
- Hibernate가 어떤 행이 어떤 컬렉션에 속하는지 구분 불가
- 데이터 중복/잘못된 결과 방지를 위해 사전 차단

Part 2 - 순차 Fetch 해결:
1단계: SELECT DISTINCT t FROM Team t JOIN FETCH t.members WHERE ...
  → Team + Member 로딩 (1 SQL)
  → PersistenceContext에 Team 엔티티 등록

2단계: SELECT DISTINCT t FROM Team t JOIN FETCH t.tags WHERE t.id IN :ids
  → 이미 로딩된 Team의 tags 컬렉션에 Tag 할당 (1 SQL)
  → PersistenceContext의 동일 Team 엔티티에 tags 추가

결과: 총 2 SQL로 모든 데이터 로딩 완료
```

**MultipleBagFetchException 해결 방법:**

| 방법 | 설명 | SQL 수 |
|------|------|--------|
| **순차 Fetch** (이 실험) | 컬렉션을 하나씩 JOIN FETCH | 2 |
| **List → Set 변경** | Bag이 아닌 Set은 동시 fetch 가능 | 1 |
| **@BatchSize** | IN 절로 배치 로딩 | 1 + ⌈N/batchSize⌉ |

- **예상 결과**: Part 1: `MultipleBagFetchException` 발생, Part 2: SQL 2회
- **의미**: Hibernate의 Bag 타입 제약을 이해하고 적절한 우회 방법을 선택해야 함

---

## 정리

### Best Practices

1. **기본 전략**: `FetchType.LAZY` + 필요 시 JOIN FETCH
2. **읽기 전용 조회**: DTO Projection 우선 고려
3. **복수 컬렉션**: `List` → `Set` 변경 또는 순차 fetch
4. **절대 금지**: `FetchType.EAGER` (N+1을 해결하지 않으며 제어 불가)
5. **모니터링**: Hibernate Statistics로 SQL 실행 횟수 상시 체크

### 실무 적용 가이드

| 상황 | 추천 해결법 |
|------|------------|
| 단일 컬렉션 조회 | JOIN FETCH 또는 @EntityGraph |
| 읽기 전용 API | DTO Projection |
| 복수 컬렉션 조회 | 순차 Fetch 또는 Set 사용 |
| 목록 조회 + 페이징 | @BatchSize (JOIN FETCH는 페이징 불가) |
| 동적 조건 | QueryDSL + @EntityGraph |

### 참고 자료

- [Hibernate ORM Documentation - Fetching](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#fetching)
- [Spring Data JPA - Entity Graph](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-graph.html)
- [Vlad Mihalcea - N+1 Query Problem](https://vladmihalcea.com/n-plus-1-query-problem/)
