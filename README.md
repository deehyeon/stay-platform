# Stay Platform

여러 외부 숙박 공급사(Supplier)의 상품을 하나의 플랫폼으로 통합하는 채널 연동 백엔드 시스템.

---

## 빌드 및 실행

### 사전 요구사항

| 항목 | 버전 |
|------|------|
| Java | 21 |
| PostgreSQL | 15+ |
| Redis | 7+ |

### 데이터베이스 설정

```sql
CREATE DATABASE stayplatformdb;
CREATE USER stayplatform WITH PASSWORD 'stayplatform';
GRANT ALL PRIVILEGES ON DATABASE stayplatformdb TO stayplatform;
```

### 빌드

```bash
./gradlew build
```

### 실행

```bash
./gradlew bootRun
```

앱 기동 시 자동으로 다음 두 가지가 실행된다.

1. **Mock Supplier 서버** (포트 9090) — Supplier A·B의 Mock HTTP 서버
2. **숙소 목록 동기화** — 공급사 숙소 목록 API를 호출해 내부 매핑 테이블 구성

---

## 설계 의사결정

### 1. 통합 요금 모델 — Gross 총액 채택

#### 두 공급사의 요금 방식

| 공급사 | 방식 | 세금 | 단위 |
|--------|------|------|------|
| Supplier A | net | 별도(`taxAmount`) | 날짜별 1박 단가 |
| Supplier B | gross | 포함 | 숙박 전체 총액만 |

#### 결정: Gross 총액으로 통일

Supplier B는 `totalPrice`(전체 총액, 세금 포함)만 제공하고 **날짜별 단가를 전혀 제공하지 않는다.** 따라서 두 공급사를 같은 단위로 맞추려면 Gross 총액이 유일한 선택지다.

- Supplier A: `Σ(nightlyRate + taxAmount)` for each night → 총액 계산 후 저장
- Supplier B: `totalPrice` 그대로 사용

#### 구현하지 않은 것

- **날짜별 단가**: Supplier B에서 제공하지 않으므로 표현 불가
- **세금 금액 분리**: Supplier B에서 역산 불가 (`taxIncluded: true`만 알림)
- **야간 단가(net) 방식**: Supplier B를 흡수할 수 없음

고객에게는 숙박 기간 전체의 총액(`totalPrice`)과 조식 포함 여부(`breakfastIncluded`)를 노출한다. 날짜별 단가·세금 분리는 이 시스템의 응답 범위가 아니다.

---

### 2. 숙소 목록 동기화 전략 — 앱 기동 시 1회

공급사의 재고·요금 API는 숙소 코드 목록을 인자로 받는다. 즉 "어떤 숙소를 물어볼지" 우리가 먼저 알고 있어야 한다. 그 목록의 출처가 공급사의 숙소 목록 API다.

#### 선택한 전략: 앱 기동 시 1회 동기화 (`HotelSyncRunner`)

```
앱 기동 → ApplicationRunner.run() → HotelSyncService.sync() → 공급사별 숙소 목록 API 호출 → 내부 매핑 저장
```

#### 선택 근거

- 숙소 목록은 **자주 바뀌지 않는다.** 새 숙소 입점이나 계약 종료는 수시로 발생하지 않음.
- 매 검색마다 호출하면 응답 지연이 2배 이상 증가하고 공급사 Rate Limit에 걸릴 위험이 있다.
- 주기적 갱신(`@Scheduled`)은 중복 실행 방지, 분산 환경에서의 단일 실행 보장 등 복잡도가 급증한다.
- 기동 시 1회로 시작하고, 운영 중 갱신이 필요하면 앱 재시작으로 대응하는 것이 현재 범위에서 가장 단순하고 안전하다.

#### upsert 보장

`findOrCreate` 패턴으로, 같은 공급사 코드가 다시 조회되어도 항상 같은 내부 식별자를 반환한다. 중복 저장 없음.

#### 현재 범위 밖 (향후 고려)

- 주기적 갱신: `@Scheduled` + Shedlock(분산 환경에서 단일 실행 보장)
- 관리자 명령 기반 수동 갱신 API

---

### 3. 타임아웃 설정 근거

```yaml
supplier:
  a:
    timeout:
      connect-ms: 3000   # 연결 타임아웃
      response-ms: 5000  # 응답 타임아웃
  b:
    timeout:
      connect-ms: 3000
      response-ms: 5000
```

| 항목 | 값 | 근거 |
|------|-----|------|
| Connect timeout | 3,000ms | 내부망 연동 기준 TCP 핸드셰이크는 수십~수백ms 수준. 3초면 네트워크 순단까지 허용하면서 무한 대기는 차단한다. |
| Response timeout | 5,000ms | 공급사가 DB 조회 후 응답을 구성하는 시간을 감안. 5초 초과 시 검색 UX가 현저히 저하되므로 실패로 처리하고 나머지 공급사 결과만 반환한다. |

Supplier B의 **무응답 모드**(`Thread.sleep(600_000)`)에서 5초 후 타임아웃이 발생하고, `onErrorResume`으로 흡수하여 나머지 공급사 결과만 응답하는 것을 통해 검증한다.

---

### 4. 0 재고 상품 처리 방침 — 응답에 포함 (0으로 노출)

연박 재고 판정: `min(remainingRooms)` across all dates in the stay period

```
예) 9/1: 3개, 9/2: 0개, 9/3: 5개 → remainingRooms = 0 (예약 불가)
```

**0인 상품을 응답에서 제외하지 않고 `remainingRooms: 0`으로 노출한다.**

#### 근거

- 고객이 해당 숙소·객실 타입이 존재한다는 사실은 알 수 있다. "이 객실은 있는데 그 기간에 꽉 찼다"는 정보 자체가 가치 있음.
- 클라이언트가 `remainingRooms == 0`을 기준으로 필터링하거나 "예약 불가" UI를 렌더링할 수 있다.
- 제외 시 클라이언트는 해당 객실이 존재하는지조차 알 수 없어 정보 손실이 발생한다.

예약 불가 여부는 `remainingRooms == 0`으로 판단한다.

---

### 5. WebFlux 전면 도입 여부 — 공급사 호출에만 부분 도입

#### 도입한 범위: 공급사 API 병렬 호출

```java
Flux.merge(supplierPorts.stream()
    .map(ctx -> ctx.port().fetchAvailability(...).onErrorResume(...))
    .toList())
.collectList().block()
```

`Flux.merge()`로 여러 공급사를 **동시에** 호출하고, 결과를 `.block()`으로 블로킹 컨텍스트로 복귀한다.

#### 도입하지 않은 범위: JPA / 데이터베이스 레이어

Spring Data JPA는 블로킹 JDBC 기반이다. R2DBC로 전면 전환하면:
- 트랜잭션 관리 방식 변경 (`@Transactional` → Reactive Transaction)
- N+1 해결 방식 변경 (EntityGraph → `flatMap` 기반 조합)
- 팀 학습 곡선 급증

공급사 호출의 병렬화가 핵심 목적이므로 WebClient + `Flux.merge()`로 충분하다. JPA 레이어는 블로킹으로 유지하고, `.block()` 이후 동기 컨텍스트에서 처리한다.

---

### 6. 신규 공급사 추가 시 수정 범위

헥사고날 아키텍처의 Port·Adapter 패턴으로 추가 시 변경 범위를 최소화했다.

| 변경 필요 | 내용 |
|-----------|------|
| **어댑터 클래스 1개** | `SupplierPort`를 구현하는 `SupplierCAdapter` 작성 (WebClient 호출 + 응답 변환) |
| **Mock 핸들러 1개** | `AbstractMockSupplierHandler`를 상속하는 `SupplierCMockHandler @Component` 작성 |
| **application.yaml** | `supplier.c.base-url`, `api-key`, `timeout` 항목 추가 |
| **WebClientConfig** | `supplierCWebClient` 빈 추가 |

| 변경 불필요 | 이유 |
|-------------|------|
| **도메인 레이어** | `Hotel`, `RoomType`, `Supplier`, 매핑 엔티티 — 공급사 무관한 내부 모델 |
| **애플리케이션 레이어** | `HotelSyncService`, `StaySearchService` — `List<SupplierPort>` 자동 주입 |
| **Mock 서버** | `MockSupplierServer` — `List<MockSupplierHandler>` 자동 주입, 핸들러 추가 시 자동 등록 |

Spring의 `List<SupplierPort>` 자동 주입으로, 새 `@Component` 어댑터를 추가하는 것만으로 동기화·검색 모두 자동으로 참여한다.

---

### 7. 대량 숙소 처리 (50개 초과)

공급사 API는 한 번에 최대 50개 숙소 코드를 받는다. `SupplierChunkUtil.partition()`으로 청크 분할 후 `flatMap()`으로 병렬 호출하고 결과를 합친다.

```java
Flux.fromIterable(SupplierChunkUtil.partition(hotelCodes, 50))
    .flatMap(chunk -> fetchAvailabilityChunk(chunk, condition));
```

현재 예시 데이터는 숙소가 수 개뿐이라 걸리지 않지만, 수천 개 규모에서도 동일한 코드가 동작한다.

---

## 시스템 구성

```
[고객]
  └─ GET /api/v1/stays/search
       └─ StaySearchController
            └─ StaySearchService
                 ├─ SupplierHotelMappingRepository (내부 숙소 코드 조회)
                 ├─ Flux.merge()
                 │    ├─ SupplierAAdapter → GET /a/v1/availability (Mock 9090)
                 │    └─ SupplierBAdapter → GET /b/api/search (Mock 9090)
                 └─ 결과 정규화 + 부분 실패 처리

[앱 기동 시]
  HotelSyncRunner
    └─ HotelSyncService
         ├─ SupplierAAdapter → GET /a/v1/hotels
         └─ SupplierBAdapter → GET /b/api/properties
```

---

## AI 활용 기록

이 프로젝트는 Claude Code (claude-sonnet-4-6)를 활용해 구현했다.

### 활용 방식

- **설계 도구로 활용**: 헥사고날 아키텍처의 Port·Adapter 구조 설계, 각 계층의 책임 분리 방식을 논의했다.
- **구현 속도 도구로 활용**: 반복적인 보일러플레이트(DTO, Repository 인터페이스 등) 생성에 활용했다.
- **코드 리뷰 파트너로 활용**: 구현 후 요구사항 충족 여부, 엣지케이스를 함께 검토했다.

### 판단하고 수정한 것

- **Mock 서버 리팩토링 요청**: 초기 구현이 공급사 2개에 종속된 구조였다. AI가 생성한 코드를 보고 "공급사가 늘면 서버 코드를 수정해야 하는 구조"임을 직접 판단하여 Port·Adapter 구조로 전면 리팩토링을 요청했다.
- **Supplier B 실패 판정**: AI가 생성한 초안에서 `resultCode` 확인 로직이 빠져 있었다. 스펙을 직접 읽고 `resultCode != "0000" || data == null` 조건임을 확인하여 수정을 지시했다.
- **WebFlux 도입 범위**: AI는 전면 WebFlux 전환을 제안했으나, JPA 레이어의 복잡도 증가를 직접 판단하여 WebClient 레이어에만 한정했다.
- **README 내용**: AI가 생성한 설명을 바탕으로 실제 코드와 다른 부분, 누락된 근거를 직접 검토하고 수정했다.

### AI를 사용하지 않은 부분

- 요구사항 분석 및 구현 우선순위 결정
- 아키텍처의 최종 구조 결정
- 각 설계 의사결정의 최종 판단
