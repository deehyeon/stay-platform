# 도메인 리서치 기록

공급사 스펙 분석과 통합 숙박 상품 모델 설계 과정에서의 리서치 기록.

---

## 1. 공급사 API 스펙 비교 분석

### 1.1 식별자 체계

| 항목 | Supplier A | Supplier B | 비고 |
|------|-----------|-----------|------|
| 숙소 식별자 | `hotelCode` | `propertyId` | 공급사 안에서만 유일 |
| 객실 타입 식별자 | `roomTypeCode` | `roomId` | **해당 숙소 안에서만 유일** |
| 공통 키 | 없음 | 없음 | 동일 숙소 판별 불가 |

**핵심 발견**: 객실 타입 식별자는 숙소 범위 내에서만 유일하다. 즉 `roomTypeCode: "STD-DBL"`이 서로 다른 숙소에 중복 존재할 수 있다. 이 때문에 매핑 키는 `(공급사, 숙소 코드, 객실 타입 코드)` 세 값의 복합 키가 되어야 한다.

### 1.2 요금 모델 차이

| 항목 | Supplier A | Supplier B |
|------|-----------|-----------|
| 요금 단위 | 날짜별 1박 단가 | 숙박 전체 총액 |
| 세금 포함 여부 | 별도 (`taxAmount`) | 포함 (`taxIncluded: true`) |
| 세금 역산 가능 여부 | 가능 | **불가** |
| 날짜별 단가 제공 | 제공 | **미제공** |

### 1.3 실패 표현 방식 차이

| 항목 | Supplier A | Supplier B |
|------|-----------|-----------|
| 실패 시 HTTP 상태 | `4xx` / `5xx` | **항상 200** |
| 실패 정보 위치 | HTTP 상태 코드 | 응답 본문 `resultCode` |
| 실패 판정 조건 | HTTP 상태 코드 | `resultCode != "0000"` 또는 `data == null` |

**핵심 발견**: Supplier B는 장애 상황에서도 HTTP 200을 반환한다. `onStatus()`만으로는 실패를 잡을 수 없고, 반드시 응답 본문 `resultCode`를 확인해야 한다. 이를 확인하지 않으면 장애를 정상 응답으로 처리하는 버그가 숨어있게 된다.

### 1.4 조식 포함 여부

- 두 공급사 모두 `breakfastIncluded` 필드를 제공하지만 재고·요금 API(②)에서만 알려준다.
- 같은 숙소·같은 객실 타입이라도 공급사마다 조식 포함 여부가 다를 수 있다.
  - 예시: Riverside Hotel Seoul / Deluxe Twin → A는 `false`, B는 `true`

---

## 2. 통합 숙박 상품 모델 설계

### 2.1 검토한 방향

#### 방향 A: 공급사별 모델 그대로 노출
- 장점: 정보 손실 없음
- 단점: 클라이언트가 공급사마다 다른 응답 형식을 알아야 함. 통합의 의미가 없음.
- **탈락**

#### 방향 B: 공통 필드만 추출
- 장점: 단순한 모델
- 단점: 두 공급사가 공통으로 제공하는 정보가 많지 않아 정보량이 너무 적음.
- **탈락**

#### 방향 C: 내부 표준 모델 정의 (채택)
- 각 공급사 응답을 내부 표준 모델로 변환하는 책임을 어댑터에게 부여
- 어댑터 계층이 공급사 고유 DTO를 내부 모델로 변환. 도메인·애플리케이션 레이어는 공급사 구분 없이 내부 모델만 다룸.
- **채택**

### 2.2 내부 표준 모델 구성

```
SupplierAvailability
├── supplierHotelCode    # 공급사 쪽 숙소 코드 (매핑 조회용)
├── supplierRoomTypeCode # 공급사 쪽 객실 타입 코드
├── totalPrice           # 숙박 전체 총액 (gross)
├── currency             # ISO 4217 통화 코드
├── breakfastIncluded    # 조식 포함 여부
└── remainingRooms       # 연박 재고 (min across all nights)
```

**버린 정보**: 날짜별 단가, 날짜별 세금, 세금 분리 금액 → Supplier B에서 제공하지 않아 표준화 불가

### 2.3 요금 표준: Gross 총액 채택

Net(세금 별도) 통일을 검토했으나 즉시 탈락했다. Supplier B는 세금 금액을 제공하지 않아(`taxIncluded: true`만 알려줌) 역산이 불가능하다.

```
Supplier A 총액 계산:
  Σ(nightlyRate + taxAmount) for each night

Supplier B 총액:
  totalPrice 그대로 사용
```

---

## 3. 공급사 코드 ↔ 내부 식별자 매핑 설계

### 3.1 매핑이 필요한 이유

공급사의 재고·요금 API는 지역으로 검색해 주지 않는다. 숙소 코드 목록을 직접 넘겨야 한다. 즉 "어떤 숙소를 물어볼지" 우리가 먼저 알고 있어야 하고, 그 목록의 출처가 공급사 숙소 목록 API다. 이 매핑을 저장하지 않으면 검색 자체가 불가능하다.

### 3.2 매핑 테이블 구조

| 매핑 대상 | 키 | 자사 쪽 |
|---------|---|--------|
| 숙소 | `(Supplier enum, hotelCode / propertyId)` | `Hotel.id` |
| 객실 타입 | `(Supplier enum, hotelCode, roomTypeCode / roomId)` | `RoomType.id` |

객실 타입 코드는 숙소 범위 내에서만 유일하므로 `hotelCode`가 매핑 키에 반드시 포함된다.

### 3.3 검토한 대안들

**대안 1: 매핑 없이 검색마다 숙소 목록 API 호출**
- 탈락 이유: 매 검색마다 ①+② 두 API를 순차 호출해야 해서 응답 지연이 2배 이상 증가. 공급사 Rate Limit 위험.

**대안 2: 검색 조건에 맞는 숙소만 필터링 후 저장**
- 탈락 이유: 공급사가 지역 정보를 제공하지 않아 필터링 기준이 없음. 전체 목록을 저장하는 것이 유일한 선택.

**대안 3: 채택 — 앱 기동 시 1회 전체 목록 저장**
- 숙소 목록은 자주 바뀌지 않음(계약 기반). 재고·요금은 매번 바뀜.
- 이 성격 차이를 전략에 반영: 정적 정보(목록)는 기동 시 1회 로드, 동적 정보(재고·요금)는 매 검색마다 실시간 조회.

### 3.4 동일 공급사 상품의 식별자 일관성 보장

`findOrCreate` 패턴으로 구현했다. 같은 공급사 코드가 다시 조회되어도 항상 같은 내부 식별자를 반환한다.

```java
// HotelSyncService — 핵심 로직
return hotelMappingRepository.findBySupplierAndSupplierHotelCode(supplier, supplierCode)
    .orElseGet(() -> {
        Hotel hotel = hotelRepository.save(Hotel.create(name));
        return hotelMappingRepository.save(
            SupplierHotelMapping.create(supplier, supplierCode, hotel));
    });
```

### 3.5 다른 공급사의 동일 숙소 처리

A의 `A-10023`과 B의 `B77120`이 실제로 같은 숙소(Riverside Hotel Seoul)라도, 공통 키가 없어 자동으로 같다고 판별할 수 없다. 이름·객실 구성으로 추정하는 병합 로직은 과제 선택 구현(§3.3)으로 분류되어 있어 구현하지 않았다.

기본 동작: 공급사가 다르면 내부 식별자도 다르게 부여. 결과적으로 같은 숙소가 두 개의 상품으로 노출된다.

---

## 4. 통합 검색 API 설계

### 4.1 병렬 호출 방식 선택

공급사 호출은 독립적이고, 한 공급사 실패가 다른 공급사 결과에 영향을 주어서는 안 된다.

| 방식 | 설명 | 탈락 이유 |
|------|------|---------|
| 순차 호출 | 공급사 A → B 순서대로 호출 | 전체 응답 시간 = A + B 응답 시간 |
| `Flux.merge()` (채택) | 동시에 호출, 결과를 합산 | — |
| `Flux.zip()` | 동시에 호출, 하나라도 실패 시 전체 실패 | 부분 실패 허용 불가 |

`Flux.merge()` + `onErrorResume()`으로 각 공급사의 실패를 독립적으로 흡수하고, 성공한 공급사 결과만 취합했다.

### 4.2 연박 재고 판정

N박 예약 가능 객실 수 = 해당 기간 모든 날짜의 `remainingRooms` 최솟값

```
예) 9/1: 3개, 9/2: 0개, 9/3: 5개
→ min(3, 0, 5) = 0 (예약 불가)
```

3박 전체를 예약할 수 있는 연속 객실이 있어야 하므로 최솟값이 정확한 판정 방법이다.

### 4.3 0 재고 상품 처리

| 옵션 | 장점 | 단점 |
|------|------|------|
| 응답에서 제외 | 클라이언트 처리 단순 | 해당 숙소·객실 존재 여부를 알 수 없음 |
| 0으로 노출 (채택) | 존재 사실 보존, 클라이언트가 UI 선택 가능 | 예약 불가 상품이 응답에 포함 |

`remainingRooms: 0`으로 노출하기로 결정. 클라이언트가 필터링 여부를 결정하도록 위임.

### 4.4 대량 숙소 처리 (50개 초과)

공급사 API는 한 번에 최대 50개 숙소 코드를 받는다. `SupplierChunkUtil.partition()`으로 청크 분할 후 `flatMap()`으로 병렬 호출한다.

수천 개 규모에서는 동시 요청 수가 많아질 수 있다. 운영 환경에서는 `flatMap(chunk -> ..., maxConcurrency)`로 동시 요청 수를 제한하는 것을 검토할 수 있다.

---

## 5. 신규 공급사 추가 시 변경 범위

헥사고날 아키텍처의 Port·Adapter 패턴으로 추가 시 변경 범위를 최소화했다.

### 변경 필요

| 항목 | 내용 |
|------|------|
| 어댑터 클래스 | `SupplierPort` 구현체 작성 (WebClient 호출 + 응답 변환) |
| Mock 핸들러 | `AbstractMockSupplierHandler` 상속 `@Component` 작성 |
| `application.yaml` | `supplier.c.*` 설정 추가 |
| `WebClientConfig` | `supplierCWebClient` 빈 추가 |
| `Supplier` 열거형 | 새 공급사 enum 값 추가 |

### 변경 불필요

| 항목 | 이유 |
|------|------|
| `HotelSyncService` | `List<SupplierPort>` 자동 주입 |
| `StaySearchService` | 동일 |
| `MockSupplierServer` | `List<MockSupplierHandler>` 자동 주입 |
| 도메인 엔티티 | 공급사 무관한 내부 모델 |
| 매핑 테이블 | `Supplier` 열거형으로 분리 이미 반영 |
