# Lab 00: 거래내역 조회 API - Count Query 최적화

## 개요

페이징 처리 시 매번 실행되는 COUNT 쿼리를 스킵하여 **46% 성능 향상**을 달성한 최적화 기법이다.

## 문제 상황

### 일반적인 페이징 쿼리

```sql
-- 1. 데이터 조회 쿼리
SELECT * FROM transaction 
WHERE amount BETWEEN 10000 AND 50000
ORDER BY approve_date_time DESC
LIMIT 20 OFFSET 0;

-- 2. 카운트 쿼리 (매번 실행!)
SELECT COUNT(*) FROM transaction
WHERE amount BETWEEN 10000 AND 50000;
```

### 문제점

| 페이지 | 데이터 쿼리 | 카운트 쿼리 | 총 쿼리 |
|--------|------------|------------|---------|
| 1페이지 | O | O | 2개 |
| 2페이지 | O | O (불필요!) | 2개 |
| 3페이지 | O | O (불필요!) | 2개 |
| ... | ... | ... | ... |

**카운트 쿼리 특징:**
- 100만 건 테이블에서 **0.3~0.5초** 소요
- 검색 조건이 복잡할수록 더 느려짐
- 2페이지 이후에는 **totalElements가 변하지 않음** (대부분의 경우)

## 해결 방법

### 핵심 아이디어

> 첫 페이지에서 받은 `totalElements`를 클라이언트가 보관하고,
> 다음 페이지 요청 시 그 값을 전달하면 카운트 쿼리 스킵!

```
[1페이지 요청] → 카운트 쿼리 실행 → totalElements: 400,254 반환
[2페이지 요청] + totalElements=400254 → 카운트 쿼리 스킵!
[3페이지 요청] + totalElements=400254 → 카운트 쿼리 스킵!
```

### 구현 코드

#### 1. DTO에 totalElements 파라미터 추가

```kotlin
// TransactionSearchRequest.kt
data class TransactionSearchRequest(
    // ... 기존 검색 조건들 ...
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long? = null  // 클라이언트가 전달하는 총 건수
) {
    /**
     * 카운트 쿼리 필요 여부 판단
     * - totalElements가 null이면 → 카운트 실행
     * - totalElements가 있으면 → 카운트 스킵 (클라이언트가 전달한 값 사용)
     * 페이지 번호와 무관하게 totalElements 존재 여부로만 결정한다.
     */
    fun needsCountQuery(): Boolean = totalElements == null
}
```

#### 2. Repository에서 조건부 카운트 실행

```kotlin
// TransactionRepositoryImpl.kt
override fun search(request: TransactionSearchRequest): Page<Transaction> {
    val pageable = PageRequest.of(request.getValidatedPage(), request.getValidatedSize())
    
    // 데이터 조회 (항상 실행)
    val content = queryFactory
        .selectFrom(transaction)
        .where(*whereConditions.toTypedArray())
        .orderBy(orderSpecifier)
        .offset(pageable.offset)
        .limit(pageable.pageSize.toLong())
        .fetch()
    
    // 카운트 쿼리 (조건부 실행!)
    val total = if (request.needsCountQuery()) {
        // 첫 페이지: 카운트 쿼리 실행
        queryFactory
            .select(transaction.count())
            .from(transaction)
            .where(*whereConditions.toTypedArray())
            .fetchOne() ?: 0L
    } else {
        // 이후 페이지: 클라이언트가 전달한 값 사용
        request.totalElements!!
    }
    
    return PageImpl(content, pageable, total)
}
```

#### 3. Controller에서 파라미터 수신

```kotlin
// TransactionController.kt
@GetMapping
fun searchTransactions(
    // ... 기존 파라미터들 ...
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
    @RequestParam(required = false) totalElements: Long?  // 추가!
): ResponseEntity<ApiResponse<PageResponse<TransactionListResponse>>> {
    
    val request = TransactionSearchRequest(
        // ...
        totalElements = totalElements
    )
    // ...
}
```

## 클라이언트 사용법

### 1. 첫 페이지 요청

```http
GET /api/v1/transactions?page=0&size=20&minAmount=10000&maxAmount=50000
```

**응답:**
```json
{
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 400254,  // ← 이 값을 저장!
    "totalPages": 20013,
    "hasNext": true
  }
}
```

### 2. 다음 페이지 요청 (totalElements 전달)

```http
GET /api/v1/transactions?page=1&size=20&minAmount=10000&maxAmount=50000&totalElements=400254
```

→ **카운트 쿼리 스킵됨!**

### JavaScript 예시

```javascript
class TransactionApi {
    constructor() {
        this.totalElements = null;
        this.currentFilters = null;
    }
    
    async search(filters, page = 0) {
        // 필터가 바뀌면 totalElements 리셋
        if (JSON.stringify(filters) !== JSON.stringify(this.currentFilters)) {
            this.totalElements = null;
            this.currentFilters = filters;
        }
        
        const params = new URLSearchParams({
            ...filters,
            page,
            size: 20
        });
        
        // totalElements가 있으면 전달
        if (this.totalElements !== null) {
            params.set('totalElements', this.totalElements);
        }
        
        const response = await fetch(`/api/v1/transactions?${params}`);
        const data = await response.json();
        
        // 첫 페이지에서 totalElements 저장
        if (this.totalElements === null) {
            this.totalElements = data.data.totalElements;
        }
        
        return data;
    }
}

// 사용 예시
const api = new TransactionApi();

// 첫 페이지 (카운트 쿼리 O)
await api.search({ minAmount: 10000, maxAmount: 50000 }, 0);

// 다음 페이지 (카운트 쿼리 X)
await api.search({ minAmount: 10000, maxAmount: 50000 }, 1);
await api.search({ minAmount: 10000, maxAmount: 50000 }, 2);

// 필터 변경 시 자동 리셋
await api.search({ transactionState: '거래승인' }, 0);  // 카운트 쿼리 O
```

## 성능 측정 결과

### 테스트 환경
- 데이터: 1,000,000건
- 검색 조건: 금액 범위 (10,000 ~ 50,000원)
- 결과: 400,254건

### 측정 결과

| 구분 | 응답 시간 | 카운트 쿼리 |
|------|----------|------------|
| 첫 페이지 (totalElements 없음) | **0.734s** | O |
| 두번째 페이지 (totalElements 있음) | **0.396s** | X |

```
성능 향상: (0.734 - 0.396) / 0.734 = 46%
```

### 시각화

```
첫 페이지 요청:
├─ 데이터 쿼리: 0.4s ████████████████
└─ 카운트 쿼리: 0.3s ████████████
                     총 0.734s

두번째 페이지 요청:
├─ 데이터 쿼리: 0.4s ████████████████
└─ 카운트 쿼리: 스킵! 
                     총 0.396s (46% 개선!)
```

## 주의사항

### 1. 데이터 변경 시 totalElements 불일치

```
시나리오:
1. 사용자 A가 1페이지 조회 → totalElements: 100
2. 다른 사용자가 데이터 10건 삭제
3. 사용자 A가 2페이지 조회 (totalElements=100 전달)
→ 실제로는 90건인데 100건으로 표시됨!
```

**대응 방안:**
- 실시간성이 중요한 경우: 일정 시간마다 카운트 갱신
- 대부분의 경우: 허용 가능한 오차로 간주

### 2. 검색 조건 변경 시 리셋 필수

```kotlin
// 잘못된 사용
GET /api/v1/transactions?transactionState=거래승인&totalElements=400254
// → 금액 검색의 totalElements를 거래상태 검색에 사용하면 안 됨!
```

**클라이언트 책임:**
- 검색 조건이 바뀌면 `totalElements`를 null로 리셋
- 첫 페이지부터 다시 시작

### 3. 정렬 변경 시

정렬만 변경하면 totalElements는 동일하므로 계속 사용 가능:
```http
# 같은 totalElements 사용 가능
GET /api/v1/transactions?minAmount=10000&sortBy=amount&totalElements=400254
```

### 4. 마지막 페이지에서 빈 결과

```
시나리오:
1. 사용자 A가 1페이지 조회 → totalElements: 100, totalPages: 5 (size=20)
2. 다른 사용자가 데이터 30건 삭제 → 실제 70건
3. 사용자 A가 4페이지(offset=60) 요청 → 10건 반환 (정상)
4. 사용자 A가 5페이지(offset=80) 요청 → 빈 결과! (실제 데이터는 70건뿐)
→ UI에 "5페이지"가 존재하지만 내용이 비어있는 상태
```

**대응 방안:**
- 빈 결과가 반환되면 클라이언트에서 `totalElements`를 리셋하고 첫 페이지부터 다시 조회
- 또는 빈 결과 시 서버가 자동으로 카운트 쿼리를 재실행하여 갱신된 totalElements를 반환

### 5. URL 공유 시 오래된 totalElements

```
시나리오:
1. 사용자 A가 조회 → URL: /transactions?page=3&totalElements=400254
2. 이 URL을 사용자 B에게 공유
3. 사용자 B가 접속 → 실제 데이터는 500,000건으로 증가한 상태
→ totalElements=400254가 그대로 사용되어 잘못된 페이지 수 표시
```

**대응 방안:**
- `totalElements`를 URL 파라미터 대신 클라이언트 메모리(상태)에만 보관
- 또는 URL에 포함하되 일정 시간이 지나면 무시하는 TTL 로직 추가

### 6. Deep Pagination 문제는 별도

이 최적화는 COUNT 쿼리만 스킵하며, OFFSET 기반 페이징의 근본 문제는 해결하지 않는다:

```sql
-- OFFSET이 크면 데이터 쿼리 자체가 느려짐
SELECT * FROM transaction WHERE ... ORDER BY id LIMIT 20 OFFSET 900000;
-- → 900,020건을 읽고 900,000건을 버림!
```

대량 데이터에서 뒤쪽 페이지까지 자주 접근한다면 [Cursor 기반 페이징](#1-cursor-기반-페이징-keyset-pagination)을 검토하세요.

## 데이터 변경 시 OFFSET 기반 페이지네이션의 문제

> 이 섹션의 실험 코드: `Lab00PaginationConsistencyTest.kt`

OFFSET 기반 페이지네이션은 페이지 사이에 데이터가 추가/삭제되면 **중복 노출**이나 **데이터 누락**이 발생합니다.

### 실험 1: 데이터 추가 시 중복 발생

```
초기 데이터: [10, 9, 8, 7, 6, 5, 4, 3, 2, 1] (ID 내림차순)

1. 1페이지 요청 (offset=0, limit=5)
   → 결과: [10, 9, 8, 7, 6]

2. 새 데이터 INSERT (ID 11)
   → 전체 데이터: [11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]

3. 2페이지 요청 (offset=5, limit=5)
   → 결과: [6, 5, 4, 3, 2]
              ↑ ID 6이 1페이지에도 있었음 → 중복!
```

**원인:** 새 데이터가 정렬 순서의 앞쪽에 삽입되면서 기존 데이터의 offset이 1칸씩 뒤로 밀림

### 실험 2: 데이터 삭제 시 누락 발생

```
초기 데이터: [10, 9, 8, 7, 6, 5, 4, 3, 2, 1]

1. 1페이지 요청 (offset=0, limit=5)
   → 결과: [10, 9, 8, 7, 6]

2. ID 10 DELETE
   → 전체 데이터: [9, 8, 7, 6, 5, 4, 3, 2, 1]

3. 2페이지 요청 (offset=5, limit=5)
   → 결과: [4, 3, 2, 1]
   → ID 5는 어디에도 없음 → 누락!
```

**원인:** 데이터가 삭제되면서 기존 데이터의 offset이 1칸씩 앞으로 당겨짐

### 실험 3: totalElements 캐싱 시 불일치

```
1. 1페이지 조회 → totalElements: 10, totalPages: 2
2. 3건 삭제 → 실제 7건
3. 캐싱된 totalElements=10으로 2페이지 요청
   → 응답의 totalElements: 10 (실제: 7)
   → UI에 잘못된 페이지 수 표시
```

## 해결책: Cursor(Keyset) 기반 페이지네이션

OFFSET 대신 **마지막으로 본 ID**를 기준으로 다음 페이지를 조회합니다.

### 핵심 원리

```sql
-- OFFSET 기반 (불안정)
SELECT * FROM transaction ORDER BY id DESC LIMIT 5 OFFSET 5;
-- → "5번째부터" → 데이터 변경 시 어디가 "5번째"인지 바뀜

-- CURSOR 기반 (안정)
SELECT * FROM transaction WHERE id < 6 ORDER BY id DESC LIMIT 5;
-- → "ID 6보다 작은 것" → 데이터 변경과 무관하게 정확한 위치
```

### 구현 코드

#### 1. Response DTO

```kotlin
// CursorPageResponse.kt
data class CursorPageResponse<T : Any>(
    val content: List<T>,
    val size: Int,
    val nextCursor: Long?,   // null이면 마지막 페이지
    val hasNext: Boolean
)
```

#### 2. Repository

```kotlin
// TransactionRepositoryImpl.kt
override fun searchWithCursor(
    request: TransactionSearchRequest,
    cursorId: Long?,
    size: Int
): List<Transaction> {
    val whereConditions = buildWhereConditions(request).toMutableList()

    if (cursorId != null) {
        whereConditions.add(transaction.id.lt(cursorId))
    }

    // size + 1개를 조회하여 다음 페이지 존재 여부를 판단
    return queryFactory
        .selectFrom(transaction)
        .where(*whereConditions.toTypedArray())
        .orderBy(transaction.id.desc())
        .limit((size + 1).toLong())
        .fetch()
}
```

#### 3. API 엔드포인트

```http
# 첫 페이지
GET /api/v1/transactions/cursor?size=20

# 다음 페이지 (이전 응답의 nextCursor 전달)
GET /api/v1/transactions/cursor?cursorId=12345&size=20
```

### 실험 4: 데이터 추가 시에도 중복 없음

```
초기 데이터: [10, 9, 8, 7, 6, 5, 4, 3, 2, 1]

1. 1페이지 조회 (cursor 없음)
   → 결과: [10, 9, 8, 7, 6], nextCursor: 6

2. 새 데이터 INSERT (ID 11)

3. 2페이지 조회 (WHERE id < 6)
   → 결과: [5, 4, 3, 2, 1]
   → ID 11은 조건(id < 6)에 해당하지 않으므로 영향 없음
   → 중복 0건!
```

### 실험 5: 데이터 삭제 시에도 누락 없음

```
1. 1페이지 조회 → [10, 9, 8, 7, 6], nextCursor: 6

2. ID 10 DELETE

3. 2페이지 조회 (WHERE id < 6)
   → 결과: [5, 4, 3, 2, 1]
   → 삭제된 ID 10은 이미 1페이지에서 조회됨
   → 누락 0건!
```

### OFFSET vs CURSOR 비교

| 항목 | OFFSET 기반 | CURSOR 기반 |
|------|------------|------------|
| 데이터 추가 시 | 중복 발생 | 중복 없음 |
| 데이터 삭제 시 | 누락 발생 | 누락 없음 |
| 특정 페이지 점프 | 가능 (page=5) | 불가능 |
| Deep Pagination 성능 | 느림 (OFFSET이 커질수록) | 일정 (항상 INDEX SCAN) |
| totalElements | 필요 (COUNT 쿼리) | 불필요 |
| 구현 복잡도 | 낮음 | 약간 높음 |
| 적합한 UI | 페이지 번호 네비게이션 | 무한 스크롤, "더보기" 버튼 |

## 기타 대안적 접근법

### 1. 비동기 카운트

```kotlin
// 데이터 먼저 반환, 카운트는 비동기로
@Async
fun countAsync(request: TransactionSearchRequest): CompletableFuture<Long>
```

### 2. 추정 카운트

```sql
-- MySQL 통계 기반 추정 (매우 빠름, 정확도 낮음)
EXPLAIN SELECT * FROM transaction WHERE ...
-- rows 컬럼의 값 사용
```

## 결론

| 항목 | 결과 |
|------|------|
| 구현 난이도 | ⭐ (매우 쉬움) |
| 성능 개선 | **46%** |
| 클라이언트 변경 | 필요 (totalElements 전달) |
| 트레이드오프 | 데이터 변경 시 오차 가능 |

**핵심:**
- 간단한 구현으로 **페이징 성능 46% 개선**
- 클라이언트와의 협업 필요 (totalElements 전달)
- 대부분의 목록 조회 API에 적용 가능
- 데이터 변경이 빈번한 환경에서는 **Cursor 기반 페이지네이션** 병행 검토

## 참고 자료

- [High-Performance Java Persistence - Pagination](https://vladmihalcea.com/jpa-pagination-next-button/)
- [Spring Data JPA - Slice vs Page](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.special-parameters)
