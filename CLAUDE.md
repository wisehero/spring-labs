# Spring Labs - Project Instructions

## 프로젝트 개요

Spring Framework의 숨겨진 동작들을 직접 실험하고 검증하는 실험실 프로젝트.
각 Lab은 하나의 주제에 대해 여러 실험(Experiment)을 수행하고, 결과를 API 응답 + 콘솔 로그로 확인한다.

## 기술 스택

- **Framework**: Spring Boot 4.0.1
- **Language**: Kotlin 2.2.21 / Java 21
- **Build**: Gradle (Groovy DSL - `build.gradle`)
- **ORM**: JPA + Hibernate + QueryDSL 5.1.0
- **DB**: MySQL 8.0 (Docker, port 13306)
- **SQL 로깅**: p6spy 3.9.1

## 프로젝트 구조

```
com.wisehero.springlabs/
├── common/
│   ├── dto/          # ApiResponse, PageResponse
│   └── exception/    # GlobalExceptionHandler, ErrorCode, BusinessException
├── config/           # QueryDslConfig 등
├── entity/           # JPA Entity
├── repository/       # JPA Repository + QueryDSL Custom
├── labsXX/           # 각 Lab 패키지 (labs01, labs02, ...)
│   ├── Lab0XController.kt
│   ├── XXXExperimentService.kt
│   ├── XXXExperimentInnerService.kt  (필요시)
│   └── dto/
└── transaction/      # Lab 00의 프로덕션 구현체
    ├── controller/
    ├── service/
    └── dto/

docs/labs/            # 각 Lab의 상세 문서
src/main/resources/
├── http/             # IntelliJ HTTP Client 테스트 파일
└── application.properties
sql/                  # DB 스키마 + 초기 데이터
```

## Lab 추가 규칙

### 패키지 & 파일 네이밍

| 구성 요소 | 네이밍 규칙 | 예시 |
|-----------|------------|------|
| 패키지 | `labsXX` (2자리 숫자) | `labs05` |
| Controller | `Lab0XController.kt` | `Lab05Controller.kt` |
| Service | `[주제]ExperimentService.kt` | `NplusOneExperimentService.kt` |
| Inner Service | `[주제]ExperimentInnerService.kt` | 별도 빈 필요시만 |
| DTO | `dto/[주제]Result.kt` | `dto/NplusOneResult.kt` |
| 문서 | `docs/labs/lab-XX-[영문-kebab-case].md` | `docs/labs/lab-05-n-plus-one.md` |
| HTTP 테스트 | 기존 `experiment-api.http`에 추가 | 섹션 구분 주석 사용 |

### 엔드포인트 규칙

- 기본 경로: `/api/v1/experiments/[lab-주제]/`
- 실험별: `X-Y/[실험-이름]` (X: Lab 번호, Y: 실험 번호)
- 조회 실험: `GET`, 데이터 변경 실험: `POST`
- 정리: `DELETE /api/v1/experiments/[lab-주제]/cleanup`

### 필수 패턴

**1. ApiResponse 래핑** - 모든 응답은 `ApiResponse<T>`로 래핑한다.
```kotlin
ResponseEntity.ok(ApiResponse.success(result, "실험 X-Y 완료: ${result.conclusion}"))
```

**2. 로그 헤더** - Controller에서 실험 시작 시 박스 로그를 출력한다.
```kotlin
log.info("╔════════════════════════════════════════════════════════════╗")
log.info("║  실험 X-Y: 실험 제목                                       ║")
log.info("╚════════════════════════════════════════════════════════════╝")
```

**3. 테스트 데이터 격리** - 각 Lab은 고유 prefix로 테스트 데이터를 생성하고, cleanup 메서드를 반드시 제공한다.
```kotlin
// 예: Lab 03은 "BT-", Lab 04는 "PROP-4-X-"
fun cleanupTestData(): Int
```

**4. Self-injection** - 같은 클래스 내에서 `@Transactional` 메서드를 호출해야 할 때 self-injection 패턴을 사용한다.
```kotlin
@Lazy @Autowired private lateinit var self: MyExperimentService
self.someTransactionalMethod()  // 프록시를 통해 호출
```

**5. 로거** - SLF4J LoggerFactory를 사용한다.
```kotlin
private val log = LoggerFactory.getLogger(javaClass)
```

## 코드 스타일

- 한국어 주석, 한국어 로그 메시지 사용
- `data class` DTO 사용
- constructor injection (primary constructor)
- Entity에 `allOpen` 플러그인 적용 (build.gradle에 설정됨)
- IDENTITY 전략으로 ID 생성

## 설정 참고

- **OSIV 비활성화**: `spring.jpa.open-in-view=false`
- **Hibernate batch**: `batch_size=50`, `order_inserts=true`
- **rewriteBatchedStatements**: MySQL URL 파라미터에 포함
- **트랜잭션 로깅**: TRACE 레벨로 전체 활성화
- **p6spy**: SQL 실행 시간 + 실제 쿼리 로깅

## 실행 방법

```bash
docker-compose up -d          # MySQL 시작
./gradlew bootRun             # 애플리케이션 시작
# IntelliJ HTTP Client 또는 curl로 실험 실행
```

---

## 문서 작성 규칙 (docs/labs/)

### 핵심 원칙: "동작 원리"를 반드시 설명한다

각 Lab 문서는 단순히 "어떻게 사용하는가"가 아니라 **"내부에서 어떻게 동작하는가"**를 상세히 설명해야 한다.
실험 결과만 나열하지 말고, **왜 그런 결과가 나오는지 프레임워크/라이브러리의 내부 메커니즘까지 파고들어 설명**한다.

### 동작 원리 설명 요구사항

| 항목 | 설명 | 예시 |
|------|------|------|
| **Spring 내부 동작** | 프록시, AOP, TransactionManager 등의 처리 흐름 | "CGLIB 프록시가 메서드 호출을 가로채서 TransactionInterceptor로 위임" |
| **Hibernate 내부 동작** | Session, PersistenceContext, FlushMode 등의 상태 변화 | "FlushMode가 MANUAL로 변경되어 commit 시점에도 flush 하지 않음" |
| **JDBC/DB 레벨 동작** | Connection 상태, 실제 SQL, DB 엔진 동작 | "connection.setReadOnly(true)가 MySQL InnoDB에서 하는 일" |
| **호출 흐름도** | 메서드 호출 → 프록시 → 트랜잭션 매니저 → DB까지의 전체 흐름 | 텍스트 다이어그램 또는 단계별 설명 |
| **반직관적 동작의 이유** | "왜 예외가 안 나는가?", "왜 데이터가 남아있는가?" 등 | "rollback-only 마킹은 물리 트랜잭션이 아닌 논리 트랜잭션에서 발생" |

### 문서 구조

```markdown
# Lab XX: [한글 제목]

## 개요
- 이 실험에서 다루는 주제와 실무 중요성

## 핵심 개념
- 관련 개념 테이블/다이어그램
- **동작 원리 상세 설명** ← 여기가 핵심

## 실험 목록
- 실험 번호, 이름, HTTP Method, 핵심 관찰 포인트 테이블

## 실험 상세
### 실험 X-Y: [제목]
- **엔드포인트**: HTTP method + path
- **동작**: 무엇을 하는지 한 줄 요약
- **내부 동작 원리**: Spring/Hibernate/DB가 내부에서 어떻게 처리하는지
- **예상 결과**: 기대되는 응답/로그
- **의미**: 이 실험이 증명하는 것

## 정리
- Best Practices
- 실무 적용 가이드
- 참고 자료 (공식 문서, Javadoc 등)
```

### 설명 스타일

- **계층별 분석**: Spring → Hibernate → JDBC → DB 순서로 각 레이어에서 일어나는 일을 분리해서 설명
- **비교 테이블**: 동작 차이를 테이블로 정리 (예: REQUIRED vs REQUIRES_NEW)
- **흔한 오해 → 실제 동작**: 잘못 알려진 내용을 먼저 제시하고, 실험으로 반증
- **코드 + 설명**: 핵심 코드 스니펫과 함께 주석으로 동작 설명
- **한국어** 사용, 기술 용어는 영문 그대로 유지 (예: FlushMode, dirty checking, proxy)
