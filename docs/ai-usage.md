# AI 활용 기록

이 프로젝트는 Claude Code (claude-sonnet-4-6)를 활용해 구현했다.

---

## 활용 방식 요약

| 단계 | 활용 목적 | AI 기여도 | 직접 판단 |
|------|---------|---------|---------|
| 설계 | 아키텍처 구조 논의 | 중 | 최종 구조 결정 |
| 구현 | 보일러플레이트, 테스트 코드 생성 | 높음 | 요구사항 해석, 경계 판단 |
| 리팩토링 | 구조 개선 방향 제안 | 중 | 리팩토링 필요성 판단 |
| 문서 | 초안 작성 | 높음 | 내용 검증, 오류 수정 |

---

## 단계별 활용 내역

### 1. 설계 단계

**헥사고날 아키텍처 Port 위치 결정**

- 처음에 `SupplierPort` 인터페이스를 `adapter/supplier/` 아래에 배치했다. AI와 논의 중 "포트는 도메인이 외부에 요구하는 계약이므로 `application/required/`에 있어야 한다"는 원칙을 확인했다.
- 직접 판단한 것: 포트를 이동하는 것이 현재 코드 구조에서 실제로 의미 있는지 여부. 결론적으로 이동했다.

**도메인 엔티티 관계 설계**

- `Hotel`, `RoomType`, `SupplierHotelMapping`, `SupplierRoomTypeMapping` 4개 테이블 구조를 AI와 함께 검토했다.
- 직접 판단한 것: "공급사가 다르면 내부 식별자도 달라도 된다"는 결정. AI는 병합 여부를 선택지로 제시했고, 과제 범위와 복잡도를 고려해 별도 식별자를 부여하는 기본 동작을 채택했다.

---

### 2. 구현 단계

**Supplier B `resultCode` 실패 판정**

- AI가 생성한 초기 `SupplierBAdapter`에서 `resultCode` 확인 로직이 누락되어 있었다. HTTP 200이지만 실패인 케이스를 정상으로 처리하는 버그였다.
- 직접 발견하고 수정을 지시했다: `resultCode != "0000"` 케이스와 `data == null` 케이스 모두 `SupplierUnavailableException`을 던지도록.
- 이 케이스를 별도 테스트로 반드시 검증해야 한다는 것도 직접 판단했다.

**WebFlux 도입 범위**

- AI는 WebFlux 전면 도입(R2DBC 포함)을 제안했다.
- 직접 판단한 것: JPA 레이어를 R2DBC로 교체하면 트랜잭션 관리 방식이 바뀌고 팀 학습 곡선이 급증한다. 공급사 병렬 호출이 핵심 목적이므로 WebClient + `Flux.merge()`로 충분하다. `Spring MVC + WebClient` 혼용을 채택했다.

**50개 청크 분할 유틸리티**

- `SupplierChunkUtil`은 AI가 생성했고 그대로 사용했다. `Collections.nCopies` 대신 `IntStream.range`로 구현된 방식을 확인하고 문제없다고 판단했다.

**테스트 코드**

- MockWebServer 기반 어댑터 테스트는 AI가 초안을 작성했다. 검증 항목이 충분한지 직접 확인했다.
  - Supplier B `resultCode: E503` (HTTP 200) 케이스 — 누락되어 추가 요청
  - `data == null` 케이스 — 누락되어 추가 요청
  - 75개 숙소 코드 → 2회 분할 호출 검증 — 직접 필요하다고 판단해 추가 요청

- `StaySearchServiceTest`에서 부분 실패 테스트(공급사 A 실패 시 B 결과만 반환)를 AI가 작성했다. `failedSuppliers` 필드에 실패 공급사가 포함되는지 검증하는 assertion이 빠져 있어 추가했다.

---

### 3. Mock 서버 리팩토링

**리팩토링 필요성 직접 판단**

초기 구현은 단일 클래스에 Supplier A·B 핸들러 로직이 모두 들어 있었다. 신규 공급사 추가 시 이 클래스를 수정해야 하는 구조임을 직접 파악하고, Port·Adapter 구조로 전면 리팩토링을 요청했다.

- `MockSupplierHandler` 인터페이스 → `AbstractMockSupplierHandler` → `SupplierAMockHandler`, `SupplierBMockHandler`
- `MockSupplierServer`가 `List<MockSupplierHandler>`를 자동 주입받아 라우팅

이 구조는 `SupplierPort` + 어댑터의 설계 원칙과 동일하다. 직접 판단한 것: "Mock 서버에도 같은 원칙을 적용하는 게 일관성 있다."

---

### 4. Resilience4j 도입

**CB와 Retry 연산자 순서**

Resilience4j Reactor에서 `transformDeferred` 순서가 어떻게 동작하는지 AI와 함께 분석했다.

```java
// CB 안쪽, Retry 바깥쪽
flux
    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
    .transformDeferred(RetryOperator.of(retry))
```

- AI가 순서 설명과 `CallNotPermittedException`을 `ignoreExceptions`에 추가해야 한다는 것을 설명했다.
- 직접 판단한 것: 이 순서가 실제로 원하는 동작(각 재시도가 CB를 통과)인지를 테스트로 검증하기로 결정.

**CB HALF_OPEN 테스트 트러블슈팅**

`SupplierResilienceTest`에서 CB가 OPEN 후 `sleep(150ms)` 뒤에 HALF_OPEN 상태를 직접 어설트했더니 실패했다. Resilience4j는 다음 호출 시도가 있을 때 상태를 전이시킨다는 것을 AI를 통해 확인했다.

- 수정: `sleep` 후 HALF_OPEN 중간 어설트를 제거하고, 바로 성공 응답으로 실제 호출 → CLOSED 전이를 확인하는 방식으로 변경.

---

### 5. 문서 작성

**README 설계 의사결정 섹션**

구현 완료 후 README에 설계 근거를 작성할 때 AI가 초안을 작성했다. 다음을 직접 검토하고 수정했다.

- Gross 총액 채택 이유가 "Supplier B가 날짜별 단가를 제공하지 않는다"는 핵심 이유를 명확히 서술했는지 확인
- 타임아웃 값 근거(connect 3000ms, response 5000ms)가 실제 설정 파일과 일치하는지 확인
- "구현하지 않은 것" 항목이 실제로 구현하지 않은 것인지 코드를 직접 확인

**docs/architecture.md 패키지 구조**

AI가 생성한 패키지 구조 초안에 존재하지 않는 경로(`provided/`, `web/`, `mock/MockSupplierController`)가 포함되어 있어 실제 코드를 직접 확인하고 수정을 지시했다.

---

## AI를 사용하지 않은 결정

- **무엇을 구현할지 우선순위 판단** — 7일 기간 내 핵심 흐름 위주로 무엇을 먼저 할지
- **Gross 총액 채택 최종 결정** — Supplier B 스펙을 직접 읽고 net 통일이 불가능하다는 것을 파악한 후 결정
- **ApplicationRunner 동기화 전략 선택** — 운영 복잡도와 과제 범위를 직접 저울질
- **0 재고 상품 노출 여부** — 고객 경험 관점에서 직접 판단
- **Mock 서버 리팩토링 필요성** — 구조적 문제를 직접 인식하고 개선 요청

---

## 주의한 점

- AI가 생성한 코드는 요구사항 스펙과 직접 대조했다. 특히 Supplier B의 `resultCode` 처리, `data == null` 처리는 스펙 원문을 읽고 검증했다.
- 인터뷰에서 임의 지점을 짚어 설명을 요청할 수 있으므로, AI가 작성한 코드도 모두 직접 읽고 이해했다.
- 이 문서는 AI가 초안을 작성하고 내용의 정확성을 직접 검토한 후 확정했다.
