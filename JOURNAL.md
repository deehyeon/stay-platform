# 개발 과정 기록 (Progress Journal)

---

## Day 1 — 설계 · 기반 구조 (9/15)

### 수행 내용

- 프로젝트 초기 설정 (Spring Boot, Gradle, PostgreSQL, Redis 의존성)
- GitHub 이슈 템플릿, PR 템플릿, CodeRabbit 설정
- `global` 레이어 구현 (`ApiResponse`, `GlobalException`, `BaseEntity`, `AbstractEntity`)
- 설계 문서 작성 (`docs/architecture.md`, `docs/implementation-plan.md`)
- 숙박 도메인 엔티티 설계 및 구현 (`Hotel`, `RoomType`, `Supplier`, 매핑 테이블)

### 의사결정 — 헥사고날 아키텍처 채택

처음부터 구조를 잡는 데 시간을 썼다.  
공급사가 늘어날 때 서비스 코드를 건드리지 않아야 한다는 요구사항이 명확했기 때문에, Ports & Adapters 패턴을 선택했다.

- `SupplierPort` 인터페이스 하나로 모든 공급사를 동일하게 다룬다
- `HotelSyncService`와 `StaySearchService`는 `List<SupplierPort>`를 주입받으므로 새 공급사 어댑터 클래스를 추가하는 것만으로 자동 참여한다
- 공급사 전용 DTO는 어댑터 패키지(`adapter/supplier/supplierX/dto/`) 밖으로 노출하지 않는다

포트 인터페이스를 `application/required/`에 두었다. 처음에는 `adapter/supplier/` 아래에 뒀다가, 포트가 도메인 관점에서 정의되어야 한다는 원칙에 따라 이동했다.

### 의사결정 — 도메인 엔티티 구조

`Hotel`, `RoomType` 테이블을 내부 식별자로 두고, `SupplierHotelMapping` / `SupplierRoomTypeMapping`이 공급사 코드 ↔ 내부 ID를 연결한다.

공급사가 다르면 내부 식별자도 다르게 부여하는 것이 기본 동작이다. A의 `A-10023`과 B의 `B77120`이 같은 숙소라도 각각 별도의 내부 숙소 ID를 가진다. 병합(중복 상품 병합)은 선택 구현으로 분류했다.

### 막힌 지점

Spring Boot 4.0에서 `@DataJpaTest`가 제거되어 Repository 테스트 구성을 `@SpringBootTest(webEnvironment = NONE)`으로 바꿔야 했다. Redis auto-configuration 때문에 테스트가 Redis 연결을 시도해 실패하는 문제가 있었고, `application-test.yaml`에 `spring.autoconfigure.exclude`로 Redis 자동구성을 제외해 해결했다.

---

## Day 2 — 공급사 연동 어댑터 · 통합 검색 API (9/16)

### 수행 내용

- WebClient 설정 및 공급사 프로퍼티 바인딩
- `SupplierAAdapter`, `SupplierBAdapter` 구현 + 단위 테스트
- `InventoryCalculator` 도메인 유틸리티 추출 + 테스트
- `SupplierChunkUtil` (50개 청크 분할) 추출
- `HotelSyncService` + `HotelSyncRunner` 구현 + 테스트
- 통합 검색 API `GET /api/v1/stays/search` 구현 + 테스트
- Mock Supplier 서버 구현 (포트 9090)

### 의사결정 — 요금 모델: Gross 총액 채택

두 공급사의 요금 방식이 달라 내부 표준을 정해야 했다.

| 공급사 | 방식 |
|--------|------|
| Supplier A | 날짜별 1박 net 단가(`nightlyRate`) + 세금 별도(`taxAmount`) |
| Supplier B | 숙박 전체 gross 총액(`totalPrice`) — 날짜별 단가 없음 |

처음에는 "날짜별 단가로 통일하면 정보량이 많아서 좋겠다"고 생각했다. 그러나 Supplier B는 날짜별 단가를 전혀 제공하지 않고, 세금 역산도 불가능(`taxIncluded: true`만 알려줌)하다. 두 공급사를 같은 단위로 맞추려면 Gross 총액이 유일한 선택이다.

- Supplier A: `Σ(nightlyRate + taxAmount)` for each night → 총액 계산
- Supplier B: `totalPrice` 그대로 사용

날짜별 단가·세금 분리는 이 시스템의 응답 범위가 아닌 것으로 정리했다.

### 의사결정 — 숙소 목록 동기화: 앱 기동 시 1회 (`ApplicationRunner`)

세 가지 전략을 검토했다.

| 전략 | 장점 | 단점 | 탈락 이유 |
|------|------|------|---------|
| 앱 기동 시 1회 | 구현 단순, 항상 최신 상태 시작 | 공급사 장애 시 기동 지연 가능 | — |
| 주기적 갱신(`@Scheduled`) | 신규 숙소 자동 반영 | 분산 환경 단일 실행 보장 복잡도 급증 | 과제 범위 초과 |
| 별도 관리 API | 명시적 제어 | 수동 운영 필요 | 과제 범위 초과 |

숙소 목록은 자주 바뀌지 않는다는 특성과 구현 단순성을 기준으로 `ApplicationRunner` 1회를 채택했다.

`findOrCreate` 패턴으로 동일한 공급사 코드가 재조회되어도 항상 같은 내부 식별자를 반환한다.

### 의사결정 — WebFlux 부분 도입

공급사 병렬 호출에 `Flux.merge()`를 쓰고, JPA 레이어는 블로킹으로 유지했다.

R2DBC로 전면 전환하면 트랜잭션 관리 방식이 바뀌고, 팀 학습 곡선이 급증한다. 공급사 호출의 병렬화가 핵심 목적이므로 `WebClient + Flux.merge() + .block()`로 충분하다.

```java
// StaySearchService — 공급사 병렬 호출
Flux.merge(supplierPorts.stream()
    .map(ctx -> ctx.port().fetchAvailability(...)
        .onErrorResume(SupplierUnavailableException.class, e -> { ... }))
    .toList())
.collectList().block()
```

### 의사결정 — Mock 서버 별도 포트(9090)

처음에 같은 포트(8080)에 두는 것을 고려했다가 버렸다. 애플리케이션이 자기 자신을 HTTP로 호출하게 되면 스레드가 묶이면서 연동 문제로 오해하기 쉬운 실패가 발생한다.

Mock 서버를 `MockSupplierServer`라는 별도 `ApplicationRunner`로 Netty 위에서 9090 포트로 기동한다.

### 의사결정 — Mock 서버 Port·Adapter 구조 적용

처음 구현은 단일 클래스(`MockSupplierController`)에 모든 핸들러 로직을 넣었다. 그런데 신규 공급사가 추가될 때 이 클래스를 수정해야 하는 문제가 있었다. Mock 서버도 `MockSupplierHandler` 인터페이스로 추상화하고, `MockSupplierServer`가 `List<MockSupplierHandler>`를 자동 주입받아 라우팅하도록 리팩토링했다.

신규 공급사 추가 시 `AbstractMockSupplierHandler`를 상속하는 `@Component` 클래스 하나만 추가하면 된다.

### Supplier B 실패 판정 — 반드시 `resultCode` 확인

Supplier B는 장애 상황에서도 HTTP 200을 반환한다. `onStatus()`만으로는 실패를 잡을 수 없다.

```java
// SupplierBAdapter — resultCode 확인
.flatMap(res -> {
    if (!res.isSuccess()) {  // resultCode != "0000" 또는 data == null
        return Mono.error(new SupplierUnavailableException());
    }
    return Mono.just(res.data());
})
```

`resultCode != "0000"` 케이스와 `data == null` 케이스를 별도 테스트로 검증했다.

---

## Day 3 — Resilience4j · README · 문서 (9/17)

### 수행 내용

- Resilience4j CircuitBreaker + Retry 공급사 어댑터 적용
- `SupplierResilienceTest` (통합 테스트) 작성
- README 전면 작성 (빌드·실행 방법, 설계 의사결정)
- `docs/architecture.md` 현재 구현 상태 반영
- `docs/ai-usage.md` 분리
- 시스템 개요 섹션 + Mermaid 다이어그램 추가

### 의사결정 — Resilience4j 도입

타임아웃·부분 실패·서킷 브레이커를 따로 구현하는 것보다 검증된 라이브러리를 쓰는 게 낫다고 판단했다. `resilience4j-reactor`로 Reactor 파이프라인에 직접 연산자로 삽입할 수 있어 기존 코드 흐름을 크게 바꾸지 않아도 됐다.

### 의사결정 — CB와 Retry 연산자 순서

`transformDeferred` 적용 순서는 **CB 안쪽, Retry 바깥쪽**이다.

```java
flux
    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))  // 안쪽
    .transformDeferred(RetryOperator.of(retry))                     // 바깥쪽
```

Retry가 바깥에 있어야 각 재시도 시도가 CB를 통과한다. CB가 이미 OPEN 상태라면 `CallNotPermittedException`이 발생해 재시도가 의미 없으므로, Retry의 `ignoreExceptions`에 `CallNotPermittedException`을 포함시켰다.

### 트러블슈팅 — CB HALF_OPEN 상태 전이

`SupplierResilienceTest`에서 CB가 OPEN → HALF_OPEN으로 전환되는 것을 테스트하려다 문제가 있었다. `waitDurationInOpenState`(100ms) 이후 자동으로 HALF_OPEN이 될 것이라고 기대했지만, Resilience4j는 다음 호출 시도가 있을 때 상태를 전환한다. 즉 `sleep(150ms)` 후 상태를 확인해도 아직 OPEN이다.

```java
// 틀린 접근
Thread.sleep(150);
assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN); // 실패

// 올바른 접근: sleep 후 실제 호출을 해야 HALF_OPEN → CLOSED 전이 발생
Thread.sleep(150);
// 성공 응답 준비 후 호출
adapter.fetchHotelList().collectList().block();
assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
```

### 의사결정 — 구현하지 않은 선택 사항들

| 항목 | 판단 |
|------|------|
| 요금/재고 캐시 전략 | TTL 설계, 캐시 스탬피드 방지 등 복잡도가 높고 실시간 조회가 과제 핵심이라 제외 |
| 중복 상품 병합 | 공통 키가 없어 숙소명 추정이 필요하고 과제 범위 외로 판단 |
| 통화 처리 | 현재 예시 데이터가 모두 KRW라 실질적 필요 없음 |
| 예약 대행 흐름 | 과제 핵심 흐름이 아닌 선택 구현 |
| `@Scheduled` 주기 갱신 | 분산 환경 단일 실행 보장(Shedlock 등) 복잡도가 과제 범위 초과 |

---

## 테스트 전략

### 단위 테스트

- `SupplierAAdapterTest`, `SupplierBAdapterTest` — MockWebServer 기반. 정상 응답, HTTP 오류, Supplier B `resultCode` 실패, 청크 분할(75개 → 2회) 검증
- `InventoryCalculatorTest` — 연박 재고 최솟값 판정
- `StaySearchServiceTest` — Mockito 기반. 부분 실패(한 공급사 실패 시 나머지 결과 반환), 병렬 호출 검증
- `SupplierResilienceTest` — Resilience4j 통합. Retry 성공, Retry 소진, CB OPEN, CB CLOSED 복구

### 통합 테스트

- `StaySearchControllerTest` — `@WebMvcTest` + MockMvc. API 응답 구조 검증
- `HotelSyncServiceTest` — `@SpringBootTest` + H2. upsert 동작 검증

### AI 활용

자세한 내역은 [docs/ai-usage.md](docs/ai-usage.md) 참조.
