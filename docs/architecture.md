# 아키텍처 설계

## 전체 구조

헥사고날 아키텍처(Ports & Adapters)를 적용한다.
비즈니스 로직은 `domain` + `application` 레이어에만 존재하고, 외부 기술(JPA, Redis, WebClient)은 `adapter`에 위치한다.

```
com.stayplatform
├── global/                          # 전 도메인 공통
│   ├── config/                      # Spring 설정 빈
│   ├── domain/                      # BaseEntity, AbstractEntity
│   ├── exception/                   # ErrorType, GlobalException, GlobalErrorType
│   └── webapi/                      # ApiResponse, ApiControllerAdvice
│
├── stay/                            # 숙박 상품 도메인
│   ├── domain/
│   │   ├── Hotel.java
│   │   ├── RoomType.java
│   │   └── Supplier.java
│   │
│   ├── application/
│   │   ├── StaySearchService.java       # 통합 검색 유스케이스
│   │   ├── HotelSyncService.java        # 숙소 목록 동기화
│   │   ├── provided/                    # 외부(Controller)에 제공하는 포트
│   │   │   └── StaySearchUseCase.java
│   │   ├── required/                    # 외부(Repository 등)에 요구하는 포트
│   │   │   ├── HotelRepository.java
│   │   │   ├── RoomTypeRepository.java
│   │   │   ├── SupplierHotelMappingRepository.java
│   │   │   └── SupplierRoomTypeMappingRepository.java
│   │   └── dto/
│   │       ├── SearchCondition.java
│   │       └── StaySearchResult.java
│   │
│   ├── adapter/
│   │   ├── web/
│   │   │   ├── StaysApi.java            # Swagger 어노테이션 전용 인터페이스
│   │   │   └── StaysController.java     # GET /api/v1/stays/search
│   │   ├── persistence/
│   │   │   ├── HotelJpaRepository.java
│   │   │   └── mapping/
│   │   │       ├── SupplierHotelMapping.java
│   │   │       ├── SupplierRoomTypeMapping.java
│   │   │       └── ...JpaRepository.java
│   │   └── supplier/
│   │       ├── SupplierPort.java        # 공급사 어댑터 공통 인터페이스
│   │       ├── suppliera/
│   │       │   ├── SupplierAAdapter.java
│   │       │   └── dto/                 # Supplier A 전용 DTO (도메인 밖으로 노출 금지)
│   │       └── supplierb/
│   │           ├── SupplierBAdapter.java
│   │           └── dto/                 # Supplier B 전용 DTO (도메인 밖으로 노출 금지)
│   │
│   └── exception/
│       ├── StayErrorType.java
│       └── StayException.java
│
└── mock/                            # Mock 공급사 서버 (포트 9090, 별도 실행)
    └── MockSupplierController.java
```

---

## 핵심 흐름

### [사전] 숙소 목록 동기화

```
앱 기동(또는 스케줄)
→ Supplier A GET /a/v1/hotels
→ Supplier B GET /b/api/properties
→ 각 숙소·객실 타입을 SupplierHotelMapping / SupplierRoomTypeMapping으로 upsert
→ 매핑 없으면 Hotel·RoomType 신규 생성 후 매핑 저장
```

### [검색] 통합 검색 요청

```
GET /api/v1/stays/search?checkIn=&checkOut=&adults=&children=
→ DB에서 전체 숙소 매핑 조회 → 공급사별 코드 목록 구성
→ 50개 단위 청크 분할
→ Supplier A·B 재고·요금 병렬 조회 (WebClient)
   ├─ 실패 공급사: 결과 제외, 실패 사실 기록
   └─ 성공 공급사: 응답 → 내부 도메인 모델 변환
→ 매핑 테이블로 공급사 코드 → 내부 식별자 변환
→ 연박 재고 판정: min(remainingRooms) across all nights
→ 통합 결과 반환 (부분 실패 사실 포함)
```

---

## 주요 설계 의사결정

### 1. 숙소 목록 동기화 전략

**결정 필요**: 언제 공급사 숙소 목록 API를 호출해 매핑을 생성할 것인가.

| 전략 | 장점 | 단점 |
|------|------|------|
| 앱 기동 시 1회 (`ApplicationRunner`) | 구현 단순, 항상 최신 상태로 시작 | 기동 시간 증가, 공급사 장애 시 기동 실패 가능 |
| 주기적 갱신 (`@Scheduled`) | 신규 숙소 자동 반영 | 스케줄 주기 동안 신규 숙소 누락 |
| 별도 관리 API | 명시적 제어 | 수동 운영 필요 |

숙소 목록은 자주 바뀌지 않고 재고·요금은 매번 바뀐다. 이 성격 차이를 전략에 반영한다.
**→ 선택한 전략과 근거를 README.md에 명시한다.**

### 2. 요금 표준 모델

Supplier A(net, 날짜별)와 Supplier B(gross, 총액)의 요금 방식이 다르다. 내부 표준 모델을 정해야 한다.

| 옵션 | 내용 | 장점 | 단점 |
|------|------|------|------|
| 총액(gross)으로 통일 | A의 `Σ(nightlyRate + taxAmount)` 계산, B의 `totalPrice` 그대로 | 고객 노출 값이 단순 | 날짜별 단가 정보 소실 |
| net으로 통일 | B의 세금 역산 불가능하므로 사실상 불가 | — | B가 세금 금액을 제공하지 않아 불가 |
| 공급사별 별도 보존 | 원본 그대로 유지 | 정보 손실 없음 | 비교 불가, 응답 구조 복잡 |

**→ 총액(gross)으로 통일이 현실적. 선택 근거를 README.md에 명시한다.**

### 3. 조식 조건 차이 처리

예시 데이터 기준으로 같은 숙소(Riverside Hotel Seoul / Deluxe Twin)가 두 공급사에서 다른 조건으로 판매된다.
- Supplier A: 조식 미포함 (`breakfastIncluded: false`), 429,000원
- Supplier B: 조식 포함 (`breakfastIncluded: true`), 452,000원

단순히 싼 쪽을 고르면 조건이 다른 상품을 비교하게 된다.
**→ 각각 별도 상품으로 노출한다. `breakfastIncluded` 필드를 응답에 포함해 고객이 구분할 수 있게 한다.**

### 4. 0 재고 상품 처리

연박 재고 판정에서 `remainingRooms = 0`이 되면 예약 불가다.

| 옵션 | 내용 |
|------|------|
| 응답에서 제외 | 고객에게 예약 불가 상품을 보여주지 않음 |
| 0으로 노출 | 고객이 존재 여부는 알되 예약 불가임을 알 수 있음 |

**→ 선택한 방식과 근거를 README.md에 명시한다.**

### 5. 서로 다른 공급사의 동일 숙소 처리

두 공급사가 같은 숙소를 각자의 코드로 취급해도 이를 알려주는 공통 키가 없다.

- **기본 동작**: 공급사가 다르면 내부 식별자도 다르게 부여 (별도 상품으로 노출)
- **선택**: 숙소명·객실 구성 등으로 직접 추정해 하나로 병합 (선택 구현 §3.3)

**→ 기본 동작으로 구현. 병합은 선택 구현으로 분류한다.**

### 6. WebFlux 전면 도입 여부

Spring MVC 위에서 WebClient만 사용한다. WebFlux 전면 도입은 요구사항이 아니다.
**→ 선택 근거를 README.md에 명시한다.**

---

## 연동 견고성 설계

### 타임아웃 설정

```yaml
supplier:
  a:
    timeout:
      connect-ms: 3000    # TCP 연결 타임아웃
      response-ms: 5000   # 전체 응답 수신 타임아웃
  b:
    timeout:
      connect-ms: 3000
      response-ms: 5000
```

**→ 값 설정 근거(공급사 SLA, 허용 지연)를 README.md에 명시한다.**

### 부분 실패 처리

- 공급사 호출 실패 시 예외를 상위로 전파하지 않는다
- 성공한 공급사 결과만 취합해 응답
- 실패한 공급사 목록을 응답에 포함 (`failedSuppliers`)

### 실패 판정 통일

| 공급사 | 실패 조건 | 처리 |
|--------|-----------|------|
| Supplier A | HTTP `4xx` / `5xx` | `SupplierUnavailableException` |
| Supplier B | `resultCode != "0000"` 또는 `data == null` | `SupplierUnavailableException` |

도메인·애플리케이션 레이어는 공급사 구분 없이 동일한 예외 타입으로 처리한다.

### 배치 청크 처리

공급사 재고·요금 API는 한 번에 최대 50개 숙소 코드를 받는다.

```
숙소 목록 → 50개 단위 청크 분할 → 각 청크를 병렬 호출 → 결과 합산
```

숙소 수천 개 규모로 늘어날 경우 청크 수가 많아지므로, 동시 요청 수 제한(concatMap vs flatMap + maxConcurrency)을 설계 문서에 명시한다.

---

## Mock Supplier 설계

```
Main App (port 8080)
    ↓  WebClient
Mock Supplier (port 9090)
    ├── GET /a/v1/hotels           → Supplier A 숙소 목록
    ├── GET /a/v1/availability     → Supplier A 재고·요금 (모드 따라 정상/503/무응답)
    ├── GET /b/api/properties      → Supplier B 숙소 목록
    ├── GET /b/api/search          → Supplier B 재고·요금 (모드 따라 정상/E503/무응답)
    └── POST /control/{a|b}/mode   → 모드 전환
```

**같은 포트에 두면 안 되는 이유**: 자기 자신을 HTTP로 호출하게 되어 스레드가 묶이면서 연동 문제로 오해하기 쉬운 실패가 발생한다.

---

## 선택 구현: 회복탄력성 설계

### 재시도 정책 (Resilience4j Retry)

- **재시도 대상**: 일시적 장애 (`503`, `E503`) — 클라이언트 오류(`400`, `E400`)는 재시도하지 않음
- **재시도 횟수**: 최대 2회 (총 3회 시도)
- **백오프**: 지수 백오프 (1초 → 2초)

### 서킷 브레이커 (Resilience4j CircuitBreaker)

- **OPEN 조건**: 최근 10회 호출 중 실패율 50% 이상
- **HALF-OPEN 전환**: 30초 후 테스트 호출 1회
- **CLOSED 복구**: HALF-OPEN에서 성공 시

### 연동 지표·모니터링 설계

공급사별로 아래 지표를 수집한다.

| 지표 | 설명 |
|------|------|
| 성공률 | 전체 호출 대비 성공 호출 비율 |
| 응답 지연 | P50, P95, P99 응답 시간 |
| 타임아웃 비율 | 전체 호출 대비 타임아웃 발생 비율 |
| 서킷 브레이커 상태 | CLOSED / OPEN / HALF-OPEN |

Micrometer + Actuator를 통해 지표를 노출하고 Prometheus/Grafana로 시각화 가능하도록 설계한다.
