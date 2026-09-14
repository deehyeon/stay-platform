---
paths:
  - "src/test/**"
---

# 테스트 상세 규칙

## 네이밍

| 항목 | 패턴 | 예시 |
|------|------|------|
| 클래스명 | `{Class}Test` | `StaySearchServiceTest` |
| 메서드명 | `{methodName}_{testCase}` | `search_공급사A_타임아웃시_B결과만_반환` |

메서드명은 **한국어**로 작성 (가독성 우선).

```java
@Test
void search_공급사A_타임아웃시_B결과만_반환()

@Test
void fetchAvailability_resultCodeE503시_예외_발생()

@Test
void findOrCreate_동일공급사코드_재조회시_같은ID_반환()

@Test
void calculateAvailableRooms_연박시_최솟값_반환()
```

## 테스트 Base 구조

| 테스트 유형 | 구성 |
|------------|------|
| Service (단위) | `@ExtendWith(MockitoExtension.class)` + 직접 mocking |
| Repository (슬라이스) | `@DataJpaTest` + `@ActiveProfiles("test")` (H2) |
| Controller (슬라이스) | `@WebMvcTest` + `MockMvc` |
| 통합 테스트 | `@SpringBootTest` + `@AutoConfigureMockMvc` |

## Repository 테스트 — 최소 검증 항목

**대상**: 새로 추가하거나 수정하는 Repository 커스텀 메서드 (파생 쿼리, `@Query`, `@EntityGraph`, `JOIN FETCH`).
`JpaRepository` 기본 메서드(`save`, `findById` 등)는 대상 외.

**필수 검증**:
1. 결과 `List` 크기
2. **의도한 엔티티의 식별자(ID/코드) 포함 여부**
3. **유니크 제약 동작** — 매핑 테이블의 복합 유니크 키 (`supplier + hotelCode`) 위반 시 예외 발생 여부

```java
// then
assertThat(result).hasSize(1);
assertThat(result).extracting(SupplierHotelMapping::getHotelCode)
        .containsExactly("A-10023");
```

## Fixture

| Fixture | 용도 |
|---------|------|
| `TestFixture` | Request DTO, 검색 조건 등 파라미터 생성 |
| `TestEntityFixture` | Entity, Domain 객체 생성 |

기존 함수 **최대한 재사용**, 필요할 때만 추가.

```java
public class TestEntityFixture {

    public static Hotel createHotel(String name) {
        return Hotel.create(name);
    }

    public static SupplierHotelMapping createMapping(Supplier supplier, String hotelCode, Hotel hotel) {
        return SupplierHotelMapping.create(supplier, hotelCode, hotel);
    }
}

public class TestFixture {

    public static SearchCondition createCondition() {
        return SearchCondition.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 4),
                2, 0
        );
    }
}
```

## BDDMockito 패턴

```java
// given — any() 사용
given(supplierAAdapter.fetchAvailability(any(), any()))
        .willReturn(Flux.just(normalResult));

// when
StaySearchResponse response = staySearchService.search(condition);

// then — eq()로 검증
then(supplierAAdapter).should().fetchAvailability(eq(List.of("A-10023")), eq(condition));
```

- `given`: `any()` 사용
- `then`: `eq()`로 검증 (모든 파라미터가 `eq()`면 생략 가능)

## 중복 제거

반복되는 mock 코드 → 공통 메서드로 추출:

```java
private void setupSupplierANormalResponse() {
    given(supplierAAdapter.fetchAvailability(any(), any()))
            .willReturn(Flux.just(normalAvailabilityResult));
}

private void setupSupplierAError() {
    given(supplierAAdapter.fetchAvailability(any(), any()))
            .willReturn(Flux.error(new SupplierUnavailableException(...)));
}
```

## Given / When / Then 구조 필수

```java
@Test
void findOrCreate_동일공급사코드_재조회시_같은ID_반환() {
    // given
    Hotel hotel = hotelRepository.save(TestEntityFixture.createHotel("테스트 숙소"));

    // when
    SupplierHotelMapping first  = mappingService.findOrCreate(Supplier.SUPPLIER_A, "A-10023", hotel);
    SupplierHotelMapping second = mappingService.findOrCreate(Supplier.SUPPLIER_A, "A-10023", hotel);

    // then
    assertThat(first.getId()).isEqualTo(second.getId());
}
```

---

## 공급사 어댑터 테스트 — 필수 검증 시나리오

공급사 연동 어댑터는 아래 케이스를 반드시 테스트한다.

### Supplier B resultCode 실패 판정

Supplier B는 **HTTP가 항상 200**이므로 응답 본문 `resultCode`로만 실패를 판정한다.
이를 테스트하지 않으면 장애가 정상으로 처리되는 버그가 숨어있을 수 있다.

```java
@Test
void fetchAvailability_resultCodeE503시_예외_발생() {
    // given — HTTP 200 + resultCode: E503
    String errorBody = """
            {"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}
            """;
    mockWebServer.enqueue(new MockResponse().setBody(errorBody).setResponseCode(200));

    // when & then
    assertThatThrownBy(() -> supplierBAdapter.fetchAvailability(List.of("B77120"), condition).blockLast())
            .isInstanceOf(SupplierUnavailableException.class);
}

@Test
void fetchAvailability_dataNull시_예외_발생() {
    // given — HTTP 200 + data: null (resultCode가 "0000"이어도 data null이면 실패)
    String errorBody = """
            {"resultCode":"0000","resultMessage":"SUCCESS","data":null}
            """;
    mockWebServer.enqueue(new MockResponse().setBody(errorBody).setResponseCode(200));

    // when & then
    assertThatThrownBy(() -> supplierBAdapter.fetchAvailability(List.of("B77120"), condition).blockLast())
            .isInstanceOf(SupplierUnavailableException.class);
}
```

### 부분 실패 허용

한 공급사 실패가 전체 응답을 막아선 안 된다.

```java
@Test
void search_공급사A_실패시_B결과만_반환하고_실패사실_포함() {
    // given
    given(supplierAAdapter.fetchAvailability(any(), any()))
            .willReturn(Flux.error(new SupplierUnavailableException(Supplier.SUPPLIER_A, "timeout")));
    given(supplierBAdapter.fetchAvailability(any(), any()))
            .willReturn(Flux.just(bResult));

    // when
    StaySearchResponse response = staySearchService.search(condition);

    // then
    assertThat(response.results()).isNotEmpty();
    assertThat(response.failedSuppliers()).contains(Supplier.SUPPLIER_A);
}
```

### 배치 청크 분할 (50개 초과)

숙소가 50개를 초과할 때 청크 단위로 분할해 호출하는지 검증한다.

```java
@Test
void fetchAvailability_숙소75개_2회_분할호출() {
    // given — 호텔 코드 75개
    List<String> hotelCodes = IntStream.range(0, 75)
            .mapToObj(i -> "A-" + String.format("%05d", i))
            .toList();

    // when
    supplierAAdapter.fetchAvailability(hotelCodes, condition).collectList().block();

    // then — 50개 + 25개로 2회 호출
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
}
```

### 연박 재고 판정

N박 예약 가능 객실 수 = 기간 내 모든 날짜의 `remainingRooms` **최솟값**.

```java
@Test
void calculateAvailableRooms_연박시_최솟값_반환() {
    // given — 3박, remainingRooms: 3 / 0 / 5
    List<DailyInventory> inventory = List.of(
            new DailyInventory(LocalDate.of(2026, 9, 1), 3),
            new DailyInventory(LocalDate.of(2026, 9, 2), 0),
            new DailyInventory(LocalDate.of(2026, 9, 3), 5)
    );

    // when
    int available = InventoryCalculator.calculate(inventory);

    // then
    assertThat(available).isEqualTo(0);
}

@Test
void calculateAvailableRooms_하루라도_재고0이면_예약불가() {
    // ...
    assertThat(available).isEqualTo(0);
}
```

---

## 도메인 예외 테스트

예외 발생 케이스는 반드시 테스트:

```java
@Test
void search_체크아웃이_체크인보다_빠르면_예외_발생() {
    assertThatThrownBy(() -> SearchCondition.of(
                LocalDate.of(2026, 9, 4),
                LocalDate.of(2026, 9, 1),
                2, 0))
            .isInstanceOf(GlobalException.class)
            .extracting(e -> ((GlobalException) e).getErrorType())
            .isEqualTo(GlobalErrorType.INVALID_DATE_RANGE);
}
```

## 커버리지 제외 대상

테스트 불필요:
- `dto/` — 데이터 클래스
- `exception/` — 단순 enum / 생성자
- `config/` — Spring 설정 빈
- `webapi/` — 응답 포맷 클래스

## 테스트 파일 위치

```
src/test/java/com/stayplatform/
├── stay/
│   ├── domain/
│   │   └── InventoryCalculatorTest.java    # 연박 재고 판정 단위 테스트
│   ├── application/
│   │   └── StaySearchServiceTest.java      # 부분 실패, 병렬 호출
│   └── adapter/
│       ├── supplier/
│       │   ├── SupplierAAdapterTest.java   # resultCode, 청크 분할
│       │   └── SupplierBAdapterTest.java   # resultCode E503, data null
│       └── persistence/
│           └── SupplierMappingRepositoryTest.java
└── global/
    └── ...
```

---

## Mock 공급사 구성

연동 견고성을 검증하려면 Mock이 아래 세 가지 상황을 재현할 수 있어야 한다.

| 상황 | 재현 방법 |
|------|-----------|
| 정상 응답 | 스펙에 맞는 성공 응답 반환 |
| 공급사 장애 | Supplier A: HTTP `503` / Supplier B: HTTP `200` + `resultCode: E503` |
| 무응답 | 연결은 되나 응답 없음 — 타임아웃·부분 실패 검증용 |

### 구성 규칙

- Mock 서버는 메인 애플리케이션과 **다른 포트** (예: `9090`)에서 실행한다  
  같은 포트에 두면 자기 자신을 HTTP로 호출하게 되어 스레드가 묶이는 문제가 생긴다
- 모드 전환은 `POST /control/{supplier}/mode?value=normal|error|no-response` API로 동적 제어한다
- Mock 코드 품질은 평가 대상이 아니다. 최소 뼈대로 구성하고 시간을 아낀다

### 검증 범위

Mock이 세 가지 상황을 재현할 수 있어야 아래 항목을 실제로 동작함을 보일 수 있다:
- 타임아웃 설정이 실제로 동작하는지
- 한 공급사 실패 시 나머지 결과만으로 응답하는지 (부분 실패 허용)

---

## DO NOT

- Repository 커스텀 메서드 추가/수정 시 테스트 누락 금지 → 결과 `List` 크기 검증 필수
- **Supplier B `resultCode` + `data == null` 검증 테스트 누락 금지** → 장애를 정상으로 처리하는 버그 발생
- 부분 실패 테스트 누락 금지 → 한 공급사 실패 시 전체 실패가 되는 버그를 잡지 못함
- 배치 청크 테스트 누락 금지 → 숙소 50개 초과 시 분할 호출 검증 필수
- 연박 재고 최솟값 계산 테스트 누락 금지 → 재고 0인 날짜를 무시하는 버그 발생
- mock 설정 코드 중복 금지 → 공통 메서드로 추출
- `Controller` 테스트에서 비즈니스 로직 검증 금지 → Service 단위 테스트에서 처리
