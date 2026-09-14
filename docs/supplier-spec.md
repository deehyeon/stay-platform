# 공급사 API 명세

## 공통 규약

두 공급사가 동일하게 적용하는 규약이다.

| 항목 | 규약 |
|------|------|
| 인증 | 요청 헤더 `X-Api-Key: <발급키>` |
| 날짜 형식 | `YYYY-MM-DD` |
| 날짜 경계 | 체크아웃일은 숙박일에 포함되지 않음 (9/1 체크인 / 9/4 체크아웃 = 3박) |
| 인원 | 요청은 `adults`, `children` 분리. `maxOccupancy`는 성인+아동 합산 기준 |
| 재고 | 날짜별 잔여 객실 수 (`remainingRooms`, 정수) |
| 통화 | ISO 4217 코드 (`KRW`, `USD` 등) |
| 금액 | 통화 최소 단위 정수 (KRW는 원 단위, 소수점 없음) |
| 조식 | `breakfastIncluded` (boolean) — 재고·요금 API(②)에서만 제공 |

### 조회 2단계 구조

공급사는 지역으로 재고·요금을 검색해주지 않는다. 성격이 다른 두 API로 나뉜다.

| 단계 | 성격 | 변경 빈도 |
|------|------|-----------|
| ① 숙소 목록 | 숙소·객실 타입 전체 목록. 조건 파라미터 없음. 요금·재고 없음 | 자주 바뀌지 않음 |
| ② 재고·요금 조회 | 숙소 코드 목록을 받아 bulk 조회. 호출마다 값이 달라짐 | 실시간 |

- ②는 한 번에 **최대 50개** 숙소 코드. 초과하면 오류
- ①을 언제 호출할지 (기동 시 1회 / 주기적 / 별도 명령)는 직접 판단 필요

### 식별자 유일성 범위

| 식별자 | 유일성 범위 |
|--------|------------|
| 숙소 식별자 (`hotelCode` / `propertyId`) | 공급사 안에서 유일 |
| 객실 타입 식별자 (`roomTypeCode` / `roomId`) | 해당 숙소 안에서만 유일 |

객실 타입 하나를 유일하게 가리키려면 **(공급사, 숙소 코드, 객실 타입 코드)** 세 값이 필요하다.

---

## Supplier A

**특징**: 날짜별 1박 단가 · 세금 별도(net) · HTTP 상태 코드로 실패 표현

### ① 숙소 목록

```
GET /a/v1/hotels
X-Api-Key: {key}
```

요금·재고 없음. 조식 포함 여부도 ②에서만 제공.

**응답 예시**
```json
{
  "items": [
    {
      "hotelCode": "A-10023",
      "hotelName": "Riverside Hotel Seoul",
      "roomTypes": [
        { "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin", "maxOccupancy": 2 }
      ]
    }
  ]
}
```

### ② 재고·요금 조회

```
GET /a/v1/availability?hotelCodes=A-10023,A-10044
  &checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0
X-Api-Key: {key}
```

`hotelCodes`: 쉼표 구분, **최대 50개**

**응답 예시**
```json
{
  "items": [
    {
      "hotelCode": "A-10023",
      "hotelName": "Riverside Hotel Seoul",
      "roomTypeCode": "DLX-TWN",
      "roomTypeName": "Deluxe Twin",
      "maxOccupancy": 2,
      "breakfastIncluded": false,
      "currency": "KRW",
      "dailyRates": [
        { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 120000, "taxAmount": 12000 },
        { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 150000, "taxAmount": 15000 },
        { "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 120000, "taxAmount": 12000 }
      ]
    }
  ]
}
```

**요금 규약**
- `nightlyRate`: 세금 별도(net) 금액
- 해당 날짜 결제 금액: `nightlyRate + taxAmount`
- 숙박 전체 총액: `Σ(nightlyRate + taxAmount)` for each night

### 응답 필드 사전

| 필드 | 타입 | 의미 |
|------|------|------|
| `hotelCode` | string | 숙소 식별자. Supplier A 안에서만 유일 |
| `hotelName` | string | 숙소명 |
| `roomTypes[]` | array | 숙소가 가진 객실 타입 목록 (①에만) |
| `roomTypeCode` | string | 객실 타입 식별자. 해당 숙소 안에서만 유일 |
| `roomTypeName` | string | 객실 타입명 |
| `maxOccupancy` | int | 객실 1실의 최대 수용 인원 (성인+아동 합산) |
| `breakfastIncluded` | boolean | 요금에 조식 포함 여부 (②에만) |
| `currency` | string | nightlyRate·taxAmount의 통화 |
| `dailyRates[].date` | date | 숙박일 (체크인일부터 체크아웃 전날까지) |
| `dailyRates[].remainingRooms` | int | 그날 예약 가능한 해당 타입 객실 수 |
| `dailyRates[].nightlyRate` | int | 그날 1박 요금 — 세금 별도(net) |
| `dailyRates[].taxAmount` | int | 그날 1박에 붙는 세금 |

### 에러 응답 (HTTP 상태 코드)

```json
{ "error": "INVALID_DATE_RANGE", "message": "checkOut must be after checkIn" }
```

| 상태 코드 | error | 의미 |
|-----------|-------|------|
| `400` | `INVALID_DATE_RANGE` / `INVALID_PARAMETER` | 잘못된 요청 |
| `400` | `TOO_MANY_HOTEL_CODES` | hotelCodes 50개 초과 |
| `401` | `UNAUTHORIZED` | 인증 실패 |
| `429` | `RATE_LIMIT_EXCEEDED` | 호출 한도 초과 |
| `500` | `INTERNAL_ERROR` | 공급사 내부 오류 |
| `503` | `SERVICE_UNAVAILABLE` | 일시적 장애 |

---

## Supplier B

**특징**: 숙박 전체 총액 · 세금 포함(gross) · 항상 HTTP 200 + 본문 `resultCode`로 실패 표현

> ⚠️ Supplier B는 장애 상황에서도 HTTP 200을 반환한다.
> `resultCode` 확인 없이는 장애를 정상으로 처리하게 된다.

### ① 숙소 목록

```
GET /b/api/properties
X-Api-Key: {key}
```

**응답 예시**
```json
{
  "resultCode": "0000",
  "resultMessage": "SUCCESS",
  "data": {
    "items": [
      {
        "propertyId": "B77120",
        "propertyName": "Riverside Hotel Seoul",
        "rooms": [
          { "roomId": "R-401", "roomName": "Deluxe Twin Room", "maxOccupancy": 2 }
        ]
      }
    ]
  }
}
```

### ② 재고·요금 조회

```
GET /b/api/search?propertyIds=B77120
  &checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0
X-Api-Key: {key}
```

`propertyIds`: 쉼표 구분, **최대 50개**

**응답 예시 (성공)**
```json
{
  "resultCode": "0000",
  "resultMessage": "SUCCESS",
  "data": {
    "items": [
      {
        "propertyId": "B77120",
        "propertyName": "Riverside Hotel Seoul",
        "roomId": "R-401",
        "roomName": "Deluxe Twin Room",
        "maxOccupancy": 2,
        "breakfastIncluded": true,
        "currency": "KRW",
        "totalPrice": 452000,
        "taxIncluded": true,
        "inventory": [
          { "date": "2026-09-01", "remainingRooms": 3 },
          { "date": "2026-09-02", "remainingRooms": 1 },
          { "date": "2026-09-03", "remainingRooms": 5 }
        ]
      }
    ]
  }
}
```

**요금 규약**
- `totalPrice`: 숙박 기간 전체 총액, 세금 포함(gross)
- 날짜별 요금 없음
- 세금 금액 별도 없음 (`taxIncluded: true`만 제공)

### 응답 필드 사전

| 필드 | 타입 | 의미 |
|------|------|------|
| `resultCode` | string | 처리 결과 코드. `"0000"`이 성공 |
| `resultMessage` | string | 결과 메시지 |
| `data` | object | 성공 시에만 값이 있고, 실패 시 `null` |
| `propertyId` | string | 숙소 식별자 (A의 `hotelCode`에 대응) |
| `propertyName` | string | 숙소명 |
| `rooms[]` | array | 객실 타입 목록 (①에만) |
| `roomId` | string | 객실 타입 식별자 (A의 `roomTypeCode`에 대응). 개별 물리 객실이 아님 |
| `roomName` | string | 객실 타입명 |
| `maxOccupancy` | int | 최대 수용 인원 (성인+아동 합산) |
| `breakfastIncluded` | boolean | 요금에 조식 포함 여부 (②에만) |
| `currency` | string | `totalPrice`의 통화 |
| `totalPrice` | int | 숙박 기간 전체 총액 — 세금 포함(gross) |
| `taxIncluded` | boolean | 항상 `true` |
| `inventory[].date` | date | 숙박일 |
| `inventory[].remainingRooms` | int | 그날 예약 가능한 해당 타입 객실 수 |

### 에러 응답 (HTTP는 항상 200)

```json
{ "resultCode": "E503", "resultMessage": "TEMPORARILY_UNAVAILABLE", "data": null }
```

| `resultCode` | 의미 |
|--------------|------|
| `"0000"` | 성공 |
| `"E400"` | 잘못된 요청 (propertyIds 50개 초과 포함) |
| `"E401"` | 인증 실패 |
| `"E429"` | 호출 한도 초과 |
| `"E500"` | 공급사 내부 오류 |
| `"E503"` | 일시적 장애 |

**실패 판정 조건 (둘 중 하나라도 해당하면 실패)**
1. `resultCode != "0000"`
2. `data == null`

---

## 공급사 비교

| 항목 | Supplier A | Supplier B |
|------|------------|------------|
| 숙소 목록 엔드포인트 | `GET /a/v1/hotels` | `GET /b/api/properties` |
| 재고·요금 엔드포인트 | `GET /a/v1/availability` | `GET /b/api/search` |
| 숙소 식별자 필드명 | `hotelCode` | `propertyId` |
| 객실 타입 식별자 필드명 | `roomTypeCode` | `roomId` |
| 객실 타입 목록 필드명 | `roomTypes[]` | `rooms[]` |
| 날짜별 재고 필드명 | `dailyRates[].remainingRooms` | `inventory[].remainingRooms` |
| 요금 방식 | 날짜별 1박 단가 (`nightlyRate`) + 세금 (`taxAmount`) — **net** | 숙박 전체 총액 (`totalPrice`) — 세금 포함 **gross** |
| 실패 표현 | HTTP `4xx` / `5xx` | HTTP 항상 `200`, 본문 `resultCode`로만 실패 전달 |
| 조식 포함 여부 | `breakfastIncluded: false` (예시) | `breakfastIncluded: true` (예시) |
| 최대 숙소 코드 수 | 50개 | 50개 |

---

## 예시 데이터 비고

- Supplier A의 `A-10023`과 Supplier B의 `B77120`은 예시 데이터 기준으로 같은 숙소·같은 객실 타입이다 (`Riverside Hotel Seoul` / Deluxe Twin). 단, 스펙상 이를 알려주는 공통 키는 없다.
- 같은 숙소임에도 총액이 다르다 — A 합산 429,000원 / B 452,000원. **조건이 다르기 때문이다**: B는 조식 포함, A는 미포함.
- `Namsan Garden Stay`는 Supplier A에만 있으며, `2026-09-02`의 재고가 `0`이다.
- 예시 데이터 그대로 쓸 필요 없음. 스펙의 구조만 지키면 된다.
