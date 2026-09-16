# 구현 계획

## 필수 구현 체크리스트

### ① 숙박 상품 통합 모델 설계

공급사마다 다른 표현 방식을 흡수하는 자사 표준 숙박 상품 모델을 설계한다.
DB에는 요금·재고를 저장하지 않고, **공급사 코드 ↔ 내부 식별자 매핑만 저장**한다.

- [x] `Supplier` 열거형 — `SUPPLIER_A`, `SUPPLIER_B`
- [x] `Hotel` 엔티티 — 내부 숙소 식별자, 숙소명
- [x] `RoomType` 엔티티 — 내부 객실 타입 식별자, 객실 타입명, 최대 수용 인원 (`maxOccupancy`)
- [x] `SupplierHotelMapping` 엔티티 — `(supplier, hotelCode)` → 내부 호텔 ID. 복합 유니크 제약
- [x] `SupplierRoomTypeMapping` 엔티티 — `(supplier, hotelCode, roomTypeCode)` → 내부 객실 타입 ID. 복합 유니크 제약
- [x] findOrCreate(upsert) 보장 — 같은 공급사 코드는 항상 동일한 내부 ID 반환
- [x] **README.md 기재**: 요금 표준 모델 설계 근거 (net vs gross 통일 방식, 구현하지 않은 것)

### ② Supplier 연동 어댑터

WebClient로 각 공급사 API를 호출하고, 응답을 내부 도메인 모델로 변환한다.
공급사 전용 DTO는 어댑터 패키지 밖으로 절대 노출하지 않는다.

- [x] `WebClient` 빈 설정 — 공급사별 `baseUrl`, `X-Api-Key` 헤더, `connectTimeout`, `responseTimeout` 각각 설정
- [x] `SupplierPort` 인터페이스 정의 — 숙소 목록 조회 + 재고·요금 조회
- [x] **Supplier A** 어댑터
  - [x] `GET /a/v1/hotels` — 숙소 목록 조회
  - [x] `GET /a/v1/availability?hotelCodes=...` — 재고·요금 조회 (최대 50개)
  - [x] `dailyRates[].nightlyRate + taxAmount` 합산으로 총액 계산
  - [x] HTTP `4xx`/`5xx` → `SupplierUnavailableException` 변환
  - [x] 50개 초과 시 청크 분할 → 병렬 호출 → 결과 합산
- [x] **Supplier B** 어댑터
  - [x] `GET /b/api/properties` — 숙소 목록 조회
  - [x] `GET /b/api/search?propertyIds=...` — 재고·요금 조회 (최대 50개)
  - [x] `resultCode != "0000"` 또는 `data == null` → `SupplierUnavailableException` 변환 (**HTTP 200이어도**)
  - [x] `totalPrice` 그대로 사용 (세금 포함 총액)
  - [x] 50개 초과 시 청크 분할 → 병렬 호출 → 결과 합산
- [x] **README.md 기재**: 신규 공급사 추가 시 수정 범위 (어댑터 + 등록만, 도메인/애플리케이션 불변)

### ③ 통합 검색 API

고객 검색 요청 1건을 받아 모든 공급사를 병렬 조회하고 결과를 통합해 반환한다.

- [x] `GET /api/v1/stays/search?checkIn=&checkOut=&adults=&children=` 엔드포인트
- [x] `SearchCondition` 값 객체 — `checkIn`, `checkOut`, `adults`, `children`
- [x] 파라미터 검증 — `checkIn < checkOut`, `adults >= 1`
- [x] DB에서 전체 숙소 매핑 조회 → 공급사별 `hotelCode` 목록 구성
- [x] 공급사별 재고·요금 API **병렬 호출** (WebClient)
- [x] 50개 초과 시 청크 분할 처리
- [x] 연박 재고 판정 — `min(remainingRooms)` across all nights
- [x] 응답 필수 포함 필드 7가지 (아래 참조)
- [x] 부분 실패 시 성공 공급사 결과만 반환 + 실패 사실 응답에 포함
- [x] **README.md 기재**: 0 재고 상품 처리 방침 (제외 vs 0으로 노출)

**응답 필수 포함 필드**

| 필드 | 설명 |
|------|------|
| 내부 숙소 식별자 · 숙소명 | 공급사 코드가 아닌 자사 식별자 |
| 내부 객실 타입 식별자 · 객실 타입명 | 동일 |
| 최대 수용 인원 | 객실 1실 기준 |
| 예약 가능 객실 수 | 0이면 예약 불가 |
| 출처 공급사 | 어느 공급사에서 온 상품인지 |
| 요금 | 구성 방식은 자유 설계 |
| 부분 실패 사실 | 일부 공급사 실패 시 응답에 드러나야 함 |

### ④ 연동 견고성

외부 연동은 실패를 전제로 설계한다.

- [x] **타임아웃** — `connectTimeout`과 `responseTimeout` 각각 설정 (yaml 값 주입)
- [x] **부분 실패 허용** — 공급사 A 실패해도 B 결과만으로 응답 가능
- [x] **실패 판정 통일** — Supplier B의 `resultCode != "0000"` 또는 `data == null` → A의 4xx/5xx와 동일한 실패로 처리
- [x] **README.md 기재**: 타임아웃 값을 그렇게 정한 이유

### ⑤ Mock Supplier 구성

실제 외부 서비스를 호출하지 않는다. Supplier A·B를 흉내내는 Mock을 직접 구성한다.

- [x] **별도 포트**(9090) 실행 — 메인 앱과 같은 포트에 두면 스레드 데드락 발생
- [x] Supplier A Mock
  - [x] `GET /a/v1/hotels` — 정상 응답
  - [x] `GET /a/v1/availability` — 모드에 따라 정상 / HTTP `503` / 무응답
- [x] Supplier B Mock
  - [x] `GET /b/api/properties` — 정상 응답
  - [x] `GET /b/api/search` — 모드에 따라 정상 / HTTP `200` + `resultCode: E503` / 무응답
- [x] 모드 전환 API — `POST /control/{supplier}/mode?value=normal|error|no-response`
- [x] 무응답 모드 — 연결은 되나 응답 없음 (타임아웃 검증용)

### ⑥ 설계 근거 문서 (README.md 필수)

- [x] 빌드·실행 방법
- [x] 통합 상품 모델 설계 의사결정 (무엇을 표준으로 삼고 무엇을 버렸는지, 그 근거)
- [x] 숙소 목록 동기화 전략 (앱 기동 시 1회 / 주기적 / 별도 명령 — 선택 근거)
- [x] 타임아웃 값 설정 근거
- [x] 0 재고 상품 처리 방침 (응답에서 제외 vs 0으로 노출)
- [x] WebFlux 전면 도입 여부와 그 이유

---

## 구현 예정 후보

| 항목 | 상태 | 구현 방식 |
|------|------|-----------|
| 재시도 정책 | 진행 중 | Resilience4j Retry — 무엇을 재시도하고 무엇을 하지 않을지, 백오프 전략 |
| 서킷 브레이커 | 진행 중 | Resilience4j CircuitBreaker — 반복 실패 공급사 차단 및 복구 시점 |
| 연동 지표·모니터링 설계 | 미구현 | 공급사별 성공률·응답 지연·타임아웃 비율 |
| 요금/재고 캐시 전략 | 미구현 | Redis TTL 설계, 캐시 스탬피드 방지, 정합성 오차 허용 범위 |
| 정규화 실패 데이터 격리 | 미구현 | 변환 불가 응답을 버리지 않고 격리·기록 |
| 중복 상품 병합 | 미구현 | 두 공급사가 같은 숙소를 각자 코드로 팔 때 — 하나로 합칠지 각각 노출할지 |
| 통화(currency) 처리 | 미구현 | 다통화 상품을 함께 노출할 때의 설계 |
| 예약 대행 흐름 | 미구현 | 공급사에 예약 생성·취소 + 실패 시 보상 처리 (설계 또는 구현) |

---

## 비범위 (Out of Scope)

- 인증/인가, 결제 연동
- 관리자 기능
- 프론트엔드
- 실제 외부 상용 API 연동
- 지역·키워드 검색 필터 (공급사가 지역 정보를 제공하지 않음)
- 정렬·페이징

---

## 개발 순서

```
Phase 1 — 도메인 모델 + 식별자 매핑
  Hotel, RoomType, Supplier
  SupplierHotelMapping, SupplierRoomTypeMapping
  Repository 포트 + JPA 어댑터

Phase 2 — WebClient + 공급사 어댑터 공통 구조
  WebClient 설정 (타임아웃, baseUrl, 헤더)
  SupplierPort 인터페이스
  공통 예외 (SupplierUnavailableException)

Phase 3 — Supplier A 어댑터
  숙소 목록 API 호출
  재고·요금 API 호출 (청크 분할 포함)
  응답 → 내부 모델 변환

Phase 4 — Supplier B 어댑터
  숙소 목록 API 호출
  재고·요금 API 호출 (resultCode 실패 판정 포함)
  응답 → 내부 모델 변환

Phase 5 — 숙소 목록 동기화
  HotelSyncService (ApplicationRunner)
  upsert 로직

Phase 6 — 통합 검색 API
  GET /api/v1/stays/search
  병렬 호출, 청크 분할, 결과 통합, 부분 실패 처리

Phase 7 — Mock Supplier
  별도 포트(9090), 3가지 모드, 모드 전환 API
  Port·Adapter 구조로 리팩토링 (MockSupplierHandler 인터페이스)

Phase 8 — 선택 구현 (시간 여유 시)
  Resilience4j Retry / CircuitBreaker
  모니터링 설계
```

---

## 핵심 검증 시나리오

| 시나리오 | 검증 항목 |
|----------|-----------|
| 정상 흐름 | A·B 모두 정상 → 통합 검색 결과 반환 |
| 부분 실패 | A 장애 → B 결과만 반환 + `failedSuppliers` 포함 |
| Supplier B 장애 탐지 | HTTP 200 + `resultCode: E503` → 실패로 처리 |
| `data: null` 처리 | HTTP 200 + `data: null` → 실패로 처리 |
| 타임아웃 | 무응답 모드 → timeout 후 부분 실패 응답 |
| 연박 재고 | `remainingRooms: [3, 0, 5]` → 예약 가능 객실 수 `0` |
| 배치 분할 | 숙소 75개 → 50 + 25개로 2회 API 호출 |
| 동일 코드 재조회 | 같은 공급사 코드 두 번 조회 → 동일 내부 ID |
