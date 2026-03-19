# Lab 01: @Transactional 자기 호출 함정

## 개요

Spring의 `@Transactional`이 같은 클래스 내부에서 호출될 때 무시되는 문제를 실험한다.

프록시 기반 AOP의 동작 원리를 이해하지 못하면 **트랜잭션이 적용되지 않는 버그**가 발생한다. 자기 호출(self-invocation) 문제는 코드 리뷰에서도 놓치기 쉽고, 런타임에 예외 없이 조용히 실패하여 디버깅이 어렵다. `REQUIRES_NEW`, `readOnly`, `timeout` 등 모든 `@Transactional` 속성이 동일하게 무시된다.

## 핵심 개념

### AOP 프록시 동작 원리

```
[Controller] → [CGLIB Proxy] → [Target Bean (실제 객체)]
                    ↑
              TransactionInterceptor
              (@Transactional 처리)
```

Spring AOP는 **프록시 패턴**으로 동작한다:
1. 외부에서 Bean을 호출하면 → **프록시**가 가로챔 → `@Transactional` 동작
2. 같은 클래스 내부에서 호출하면 → `this.method()` → **프록시 우회** → `@Transactional` 무시!

### CGLIB 프록시 생성 파이프라인

Bean 생성 시 Spring이 프록시를 감싸는 전체 흐름:

```
1. BeanFactory가 TransactionExperimentService 인스턴스 생성
2. BeanPostProcessor 체인 실행
   └─ InfrastructureAdvisorAutoProxyCreator.postProcessAfterInitialization()
      └─ @Transactional 메서드 발견 → Advisor(TransactionInterceptor) 등록
      └─ CGLIB 서브클래스 생성: TransactionExperimentService$$SpringCGLIB$$0
      └─ 프록시 인스턴스를 Bean으로 등록 (원본 대체)
3. 다른 Bean(Controller 등)에 주입되는 것은 프록시 인스턴스
```

**Kotlin과 CGLIB**: Kotlin 클래스는 기본적으로 `final`이므로 CGLIB 서브클래싱이 불가능하다. 이 프로젝트는 `build.gradle`에 `kotlin-spring` 플러그인(`org.jetbrains.kotlin.plugin.spring`)을 적용하여, `@Service`, `@Transactional` 등이 붙은 클래스와 그 메서드를 자동으로 `open`으로 만든다. 이 플러그인 없이는 프록시 생성이 실패한다.

### 왜 `this.method()`는 프록시를 우회하는가?

이것이 이 실험의 핵심이다. 단계별로 추적하면:

```
1. Controller가 proxy.experimentSelfInvocation() 호출
2. CGLIB 프록시가 호출을 가로챔
3. TransactionInterceptor.invoke() 실행 → 트랜잭션 시작
4. 실제 target 객체의 experimentSelfInvocation() 호출
   ↑ 이 시점에서 this = target 객체 (프록시가 아님!)
5. target 내부에서 this.innerMethodWithRequiresNew() 호출
   → this는 프록시가 아닌 raw target이므로 TransactionInterceptor 미실행
   → @Transactional(REQUIRES_NEW) 완전 무시
6. innerMethodWithRequiresNew()는 일반 메서드 호출처럼 실행
```

**핵심**: `TransactionInterceptor`가 target 메서드를 실행할 때, `target.method()`를 호출한다. target 객체 내부에서의 `this`는 프록시 래퍼가 아닌 실제 객체를 가리킨다. 따라서 `this.innerMethod()`는 프록시 인터셉터 체인을 완전히 건너뛴다.

### TransactionInterceptor 내부 흐름

프록시를 통해 `@Transactional` 메서드가 호출될 때의 상세 흐름:

```
TransactionInterceptor.invoke(MethodInvocation)
  └─ TransactionAspectSupport.invokeWithinTransaction()
     ├─ 1. TransactionAttribute 읽기 (propagation, isolation, readOnly 등)
     │     └─ AnnotationTransactionAttributeSource에서 @Transactional 파싱
     ├─ 2. PlatformTransactionManager.getTransaction(TransactionDefinition)
     │     └─ JpaTransactionManager.doGetTransaction()
     │        └─ TransactionSynchronizationManager에서 기존 EntityManager/Connection 확인
     │     └─ propagation에 따라:
     │        ├─ REQUIRED + 기존 tx 있음 → handleExistingTransaction() (참여)
     │        ├─ REQUIRED + 기존 tx 없음 → doBegin() (새 트랜잭션)
     │        └─ REQUIRES_NEW → suspend() + doBegin() (항상 새 트랜잭션)
     ├─ 3. 트랜잭션 이름 설정: "className.methodName"
     │     └─ TransactionSynchronizationManager.setCurrentTransactionName()
     │     └─ ThreadLocal<String>에 저장
     ├─ 4. target 메서드 실행
     └─ 5. 결과에 따라:
           ├─ 정상 → commitTransactionAfterReturning()
           └─ 예외 → completeTransactionAfterThrowing() → rollback 여부 결정
```

### 트랜잭션 이름(Transaction Name)이란?

이 실험에서 트랜잭션이 같은지 판별하는 데 사용하는 `TransactionSynchronizationManager.getCurrentTransactionName()`은:

- `ThreadLocal<String>` 변수에 저장된 값
- `TransactionInterceptor`가 트랜잭션을 시작할 때 `fully-qualified-class-name.method-name` 형식으로 설정
- 자기 호출 시 `TransactionInterceptor`가 실행되지 않으므로 이 값이 변경되지 않음 → 외부 메서드의 이름이 그대로 유지
- DB 레벨의 물리적 트랜잭션 ID와는 별개의 Spring 메타데이터

## 실험 목록

| 실험 | 이름 | HTTP Method | 핵심 관찰 포인트 |
|------|------|-------------|-----------------|
| 1-A | 자기 호출 (Self-Invocation) | GET | REQUIRES_NEW 무시 → tx name 동일 |
| 1-B | 외부 호출 (External Call) | GET | REQUIRES_NEW 정상 → tx name 상이 |

## 실험 상세

### 실험 1-A: 자기 호출 (Self-Invocation) 테스트

- **엔드포인트**: `GET /api/v1/experiments/self-invocation`
- **동작**: 같은 클래스 내에서 `REQUIRES_NEW` 메서드를 직접 호출하여 프록시 우회를 관찰

#### 내부 동작 원리

```
호출 흐름:
1. Controller → proxy.experimentSelfInvocation()
   └─ CGLIB 프록시가 호출 가로챔
   └─ TransactionInterceptor 실행
      └─ JpaTransactionManager.doBegin() → 새 트랜잭션 시작
      └─ tx name = "...TransactionExperimentService.experimentSelfInvocation"
      └─ ThreadLocal에 트랜잭션 메타데이터 설정

2. target.experimentSelfInvocation() 실행
   └─ getCurrentTransactionName() → "...experimentSelfInvocation" (방금 설정된 값)
   └─ this.innerMethodWithRequiresNew() 호출
      ↑ this = raw target → 프록시 우회 → TransactionInterceptor 미실행
      └─ @Transactional(REQUIRES_NEW) 완전 무시
      └─ getCurrentTransactionName() → "...experimentSelfInvocation" (변경 안 됨!)

3. 결과 비교:
   outer = "...experimentSelfInvocation"
   inner = "...experimentSelfInvocation"
   → same_transaction = true (같은 트랜잭션!)
```

#### 실험 코드

```kotlin
@Transactional
fun experimentSelfInvocation(): Map<String, Any> {
    val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()

    // 자기 호출 - 프록시 우회!
    val innerResult = innerMethodWithRequiresNew()

    result["same_transaction"] = (outerTxName == innerResult["tx_name"])  // true!
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
fun innerMethodWithRequiresNew(): Map<String, Any?> {
    val txName = TransactionSynchronizationManager.getCurrentTransactionName()
    return mapOf("tx_name" to txName)
}
```

- **예상 결과**: `same_transaction = true` (REQUIRES_NEW 무시됨)
- **의미**: 자기 호출은 `this.method()`와 동일하므로 CGLIB 프록시의 인터셉터 체인을 건너뛰며, `@Transactional`의 모든 속성이 무시된다.

---

### 실험 1-B: 외부 호출 테스트 (정상 케이스)

- **엔드포인트**: `GET /api/v1/experiments/external-call`
- **동작**: 별도 Bean의 `REQUIRES_NEW` 메서드를 호출하여 프록시를 통한 정상 동작을 확인

#### 내부 동작 원리

```
호출 흐름:
1. Controller → proxy(TransactionExperimentService).experimentExternalCall()
   └─ TransactionInterceptor 실행 → Outer 트랜잭션 시작
   └─ tx name = "...TransactionExperimentService.experimentExternalCall"

2. target.experimentExternalCall() 내부에서:
   └─ externalService.methodWithRequiresNew() 호출
      ↑ externalService는 Controller가 주입받은 프록시 참조
      └─ CGLIB 프록시가 호출 가로챔
      └─ TransactionInterceptor 실행
         └─ REQUIRES_NEW 감지
         └─ 기존 Outer 트랜잭션 suspend (ThreadLocal 상태 저장)
         └─ JpaTransactionManager.doBegin() → 새 트랜잭션 시작
         └─ tx name = "...TransactionExperimentExternalService.methodWithRequiresNew"
         └─ 새로운 DB 커넥션 획득

3. methodWithRequiresNew() 실행 완료
   └─ Inner 트랜잭션 commit
   └─ Outer 트랜잭션 resume (ThreadLocal 상태 복원)

4. 결과 비교:
   outer = "...experimentExternalCall"
   inner = "...methodWithRequiresNew"
   → same_transaction = false (다른 트랜잭션!)
```

#### 실험 코드

```kotlin
@Transactional
fun experimentExternalCall(externalService: TransactionExperimentExternalService): Map<String, Any> {
    val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()

    // 외부 서비스 호출 - 프록시를 통함!
    val innerResult = externalService.methodWithRequiresNew()

    result["same_transaction"] = (outerTxName == innerResult["tx_name"])  // false!
}
```

- **예상 결과**: `same_transaction = false` (REQUIRES_NEW 정상 동작)
- **의미**: 외부 Bean 호출은 프록시를 통과하므로 `TransactionInterceptor`가 정상 실행되고, `REQUIRES_NEW`가 새 트랜잭션을 생성한다.

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
      "tx_name": "...experimentSelfInvocation"
    },
    "same_transaction": true
  }
}
```

**실험 B (외부 호출):**
```json
{
  "data": {
    "outer_tx_name": "...experimentExternalCall",
    "inner_result": {
      "tx_name": "...methodWithRequiresNew"
    },
    "same_transaction": false
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
    @Lazy @Autowired
    private lateinit var self: MyService  // 자기 자신의 프록시 주입

    @Transactional
    fun outerMethod() {
        self.innerMethod()  // ✅ 프록시를 통해 호출
    }

    @Transactional(propagation = REQUIRES_NEW)
    fun innerMethod() { /* ... */ }
}
```

`@Lazy`를 사용하면 실제 빈 대신 lazy 프록시가 주입되어 순환 참조를 회피한다. Spring Boot 3.0+에서는 `spring.main.allow-circular-references`가 기본 `false`이므로 `@Lazy` 없이는 시작 실패한다.

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
- 컴파일 타임 위빙(CTW) 또는 로드 타임 위빙(LTW) 필요
- 프록시가 아닌 바이트코드 직접 수정으로 동작하므로 자기 호출 문제 없음
- 설정이 복잡하고 빌드 파이프라인에 AspectJ 컴파일러 추가 필요

### 해결 방법 비교

| 방법 | 장점 | 단점 | 적합한 상황 |
|------|------|------|------------|
| **별도 서비스 분리** | SRP 준수, 테스트 용이 | 클래스 증가 | 대부분의 경우 (권장) |
| **Self-Injection** | 최소한의 코드 변경 | 순환 결합 암시, `@Lazy` 필수 | 분리하기 어려운 경우 |
| **ApplicationContext** | 어디서든 사용 가능 | Service Locator 안티패턴, 테스트 어려움 | 거의 사용하지 않음 |
| **AspectJ** | 완전한 AOP, 자기 호출 문제 없음 | 빌드 복잡성 대폭 증가 | 대규모 프로젝트에서 전사 결정 시 |

## 흔한 오해와 실제 동작

| 흔한 오해 | 실제 동작 |
|-----------|----------|
| "`private` 메서드만 문제" | `public` 메서드도 자기 호출이면 동일하게 무시됨 (이 실험이 증명) |
| "같은 클래스면 안 되고, 같은 패키지면 괜찮다" | 프록시 우회 여부는 `this` 참조 사용 여부에 달림. 다른 Bean이면 같은 패키지여도 정상 동작 |
| "`@Transactional` 없는 메서드에서 호출하면 괜찮다" | 외부 → 내부든, 내부 → 내부든 `this.method()`면 항상 프록시 우회 |
| "Spring Boot에서는 해결됐다" | Spring Boot도 동일한 프록시 기반 AOP를 사용. 해결되지 않았음 |

## 정리

### Best Practices

- 트랜잭션 경계가 필요한 메서드는 **별도 서비스로 분리** (1순위)
- 자기 호출이 불가피하면 **`@Lazy` Self-Injection** 사용 (2순위)
- 코드 리뷰 시 같은 클래스 내 `@Transactional` 메서드 간 호출 패턴을 경계
- `REQUIRES_NEW`, `readOnly`, `timeout` 등 모든 속성이 동일하게 영향받음을 인지

### 관련 Lab

- **Lab 04 (트랜잭션 전파)**: `REQUIRED` vs `REQUIRES_NEW`의 전파 동작을 9가지 실험으로 심층 탐구. Lab 01이 "왜 별도 Bean이 필요한가"의 기초라면, Lab 04는 "별도 Bean에서 전파가 어떻게 동작하는가"의 심화편.

## 참고 자료

- [Spring Docs: Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxies.html)
- [Spring Docs: @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [Baeldung: Self-Invocation with Spring AOP](https://www.baeldung.com/spring-aop-self-invocation)
- Spring Source: `TransactionInterceptor`, `TransactionAspectSupport`, `AbstractPlatformTransactionManager`
