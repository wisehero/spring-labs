# Lab 01: @Transactional 자기 호출 함정

## 개요

Spring의 `@Transactional`이 같은 클래스 내부에서 호출될 때 무시되는 문제를 실험합니다.

## 핵심 개념

### AOP 프록시 동작 원리

```
[Client] → [Proxy] → [Target Bean]
              ↑
         AOP Advice 적용
         (@Transactional 처리)
```

Spring AOP는 **프록시 패턴**을 사용합니다:
1. 외부에서 Bean을 호출하면 → **프록시**가 가로챔 → `@Transactional` 동작
2. 같은 클래스 내부에서 호출하면 → `this.method()` → **프록시 우회** → `@Transactional` 무시!

### 문제 상황

```kotlin
@Service
class MyService {
    
    @Transactional
    fun outerMethod() {
        // 내부 호출 - this.innerMethod() 와 동일
        innerMethod()  // ⚠️ REQUIRES_NEW 무시됨!
    }
    
    @Transactional(propagation = REQUIRES_NEW)
    fun innerMethod() {
        // 새 트랜잭션이어야 하지만... 같은 트랜잭션!
    }
}
```

## 실험 코드

### 위치
```
src/main/kotlin/com/wisehero/springdemo/experiment/TransactionExperimentService.kt
```

### 실험 A: 자기 호출 (문제 케이스)

```kotlin
@Transactional
fun experimentSelfInvocation(): Map<String, Any> {
    val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
    log.info("🔵 [OUTER] 트랜잭션: $outerTxName")
    
    // 자기 호출 - 프록시 우회!
    val innerResult = innerMethodWithRequiresNew()
    
    return mapOf(
        "outer_tx" to outerTxName,
        "inner_tx" to innerResult["tx_name"],
        "same_transaction" to (outerTxName == innerResult["tx_name"])  // true!
    )
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
fun innerMethodWithRequiresNew(): Map<String, Any?> {
    val txName = TransactionSynchronizationManager.getCurrentTransactionName()
    log.info("🟢 [INNER] 트랜잭션: $txName")
    return mapOf("tx_name" to txName)
}
```

### 실험 B: 외부 호출 (정상 케이스)

```kotlin
@Transactional
fun experimentExternalCall(externalService: TransactionExperimentExternalService): Map<String, Any> {
    val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
    
    // 외부 서비스 호출 - 프록시를 통함!
    val innerResult = externalService.methodWithRequiresNew()
    
    return mapOf(
        "outer_tx" to outerTxName,
        "inner_tx" to innerResult["tx_name"],
        "same_transaction" to (outerTxName == innerResult["tx_name"])  // false!
    )
}
```

## 테스트 방법

### API 호출

```bash
# 실험 A: 자기 호출
curl http://localhost:8080/api/v1/experiments/self-invocation

# 실험 B: 외부 호출
curl http://localhost:8080/api/v1/experiments/external-call
```

### 예상 결과

**실험 A (자기 호출):**
```json
{
  "data": {
    "outer_tx_name": "...experimentSelfInvocation",
    "inner_result": {
      "tx_name": "...experimentSelfInvocation"  // 같음!
    },
    "same_transaction": true  // ⚠️ REQUIRES_NEW 무시됨
  }
}
```

**실험 B (외부 호출):**
```json
{
  "data": {
    "outer_tx_name": "...experimentExternalCall",
    "inner_result": {
      "tx_name": "...methodWithRequiresNew"  // 다름!
    },
    "same_transaction": false  // ✅ REQUIRES_NEW 정상 동작
  }
}
```

### 로그 확인

```
========== 실험 1-A: 자기 호출 테스트 시작 ==========
🔵 [OUTER] 트랜잭션 이름: ...experimentSelfInvocation
🔵 [OUTER] 트랜잭션 활성: true
⚠️ 내부 메서드 호출 (this.innerMethodWithRequiresNew())
🟢 [INNER - REQUIRES_NEW] 트랜잭션 이름: ...experimentSelfInvocation  ← 같음!
========== 실험 1-A: 결과 ==========
🔴 같은 트랜잭션인가? true
💡 REQUIRES_NEW가 무시되었다면 같은 트랜잭션!
```

## 해결 방법

### 1. 별도 서비스로 분리 (권장)

```kotlin
@Service
class OuterService(private val innerService: InnerService) {
    
    @Transactional
    fun outerMethod() {
        innerService.innerMethod()  // ✅ 프록시를 통해 호출
    }
}

@Service
class InnerService {
    @Transactional(propagation = REQUIRES_NEW)
    fun innerMethod() { /* ... */ }
}
```

### 2. Self-Injection

```kotlin
@Service
class MyService {
    @Autowired
    private lateinit var self: MyService  // 자기 자신 주입
    
    @Transactional
    fun outerMethod() {
        self.innerMethod()  // ✅ 프록시를 통해 호출
    }
    
    @Transactional(propagation = REQUIRES_NEW)
    fun innerMethod() { /* ... */ }
}
```

### 3. ApplicationContext 사용

```kotlin
@Service
class MyService(private val context: ApplicationContext) {
    
    @Transactional
    fun outerMethod() {
        val self = context.getBean(MyService::class.java)
        self.innerMethod()  // ✅ 프록시를 통해 호출
    }
}
```

### 4. AspectJ 모드 사용 (고급)

```kotlin
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
```
- 컴파일 타임/로드 타임 위빙 필요
- 설정이 복잡함

## 결론

| 호출 방식 | @Transactional 동작 | 이유 |
|----------|---------------------|------|
| 외부 호출 | ✅ 정상 | 프록시를 통해 호출됨 |
| 자기 호출 | ❌ 무시됨 | `this.method()`로 프록시 우회 |

**Best Practice:**
- 트랜잭션 경계가 필요한 메서드는 **별도 서비스로 분리**
- 자기 호출이 필요하면 **Self-Injection** 사용
- 코드 리뷰 시 자기 호출 패턴 주의!

## 참고 자료

- [Spring Docs: Understanding AOP Proxies](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop-understanding-aop-proxies)
- [Baeldung: Self-Invocation with Spring AOP](https://www.baeldung.com/spring-aop-self-invocation)
