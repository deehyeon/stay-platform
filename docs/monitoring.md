# 연동 지표·모니터링 설계

공급사 연동의 건전성을 파악하기 위해 수집할 지표와 모니터링 전략을 정의한다.  
실제 구현보다 설계에 집중하며, 구현 가능한 수준의 구체적인 방향을 제시한다.

---

## 1. 수집할 핵심 지표

### 1.1 공급사별 호출 지표

| 지표 | 설명 | 중요도 |
|------|------|--------|
| 성공률 | 전체 호출 대비 성공 응답 비율 | 🔴 높음 |
| 응답 지연 (P50 / P95 / P99) | 응답 시간 분포 | 🔴 높음 |
| 타임아웃 비율 | 전체 호출 대비 타임아웃 발생 비율 | 🔴 높음 |
| 오류율 | 전체 호출 대비 오류 발생 비율 | 🔴 높음 |
| 초당 호출 수 (RPS) | 공급사 Rate Limit 관리 | 🟡 중간 |

### 1.2 서킷 브레이커 상태 지표

| 지표 | 설명 |
|------|------|
| CB 상태 | `CLOSED` / `OPEN` / `HALF_OPEN` |
| CB OPEN 전환 횟수 | 공급사 장애 빈도 추적 |
| HALF_OPEN 성공/실패 수 | 복구 시도 성공률 |

### 1.3 검색 API 지표

| 지표 | 설명 |
|------|------|
| 검색 응답 시간 | 전체 검색 요청의 P50 / P95 |
| 부분 실패 비율 | 공급사 일부 실패로 인해 완전하지 않은 응답 비율 |
| 전체 실패 비율 | 모든 공급사 실패로 빈 결과 반환 비율 |

---

## 2. 지표 수집 방법

### 2.1 Micrometer + Actuator

Spring Boot Actuator와 Micrometer를 통해 지표를 자동 수집한다.

```yaml
# application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    tags:
      application: stay-platform
```

### 2.2 Resilience4j 자동 지표 연동

`resilience4j-micrometer` 의존성을 추가하면 CB·Retry 지표가 자동으로 Micrometer에 등록된다.

```groovy
// build.gradle
implementation 'io.github.resilience4j:resilience4j-micrometer:2.3.0'
```

자동 노출되는 지표 예시:

| Micrometer 지표명 | 설명 |
|------------------|------|
| `resilience4j.circuitbreaker.state` | CB 상태 (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `resilience4j.circuitbreaker.calls` | 호출 수 (성공/실패/미허용) |
| `resilience4j.circuitbreaker.failure.rate` | 실패율 |
| `resilience4j.retry.calls` | Retry 호출 수 (성공/재시도/실패) |

### 2.3 WebClient 응답 시간 측정

WebClient 호출에 Micrometer `Timer`를 적용해 응답 지연을 측정한다.

```java
// SupplierAAdapter — 응답 시간 측정 예시
private final MeterRegistry meterRegistry;

private Flux<SupplierHotelInfo> fetchWithMetrics() {
    Timer.Sample sample = Timer.start(meterRegistry);
    return webClient.get()
        .uri("/a/v1/hotels")
        .retrieve()
        .bodyToFlux(...)
        .doOnComplete(() ->
            sample.stop(meterRegistry.timer("supplier.request",
                "supplier", "A",
                "endpoint", "hotel_list",
                "status", "success")))
        .doOnError(e ->
            sample.stop(meterRegistry.timer("supplier.request",
                "supplier", "A",
                "endpoint", "hotel_list",
                "status", "error")));
}
```

### 2.4 커스텀 Counter — 부분 실패 추적

```java
// StaySearchService — 공급사 실패 시 카운터 증가
Counter.builder("supplier.partial.failure")
    .tag("supplier", supplier.name())
    .register(meterRegistry)
    .increment();
```

---

## 3. Prometheus + Grafana 시각화 설계

### 3.1 구성

```
Stay Platform App
  └─ /actuator/prometheus  ← HTTP scrape
       └─ Prometheus (수집·저장)
            └─ Grafana (시각화·알림)
```

docker-compose 확장 예시:

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9091:9090"

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
```

### 3.2 대시보드 패널 구성

**패널 1 — 공급사별 성공률 (Gauge)**
```promql
rate(resilience4j_circuitbreaker_calls_total{kind="successful", name="supplierA"}[5m])
/
rate(resilience4j_circuitbreaker_calls_total{name="supplierA"}[5m])
```

**패널 2 — 응답 지연 히스토그램 (Heatmap)**
```promql
histogram_quantile(0.95,
  rate(supplier_request_seconds_bucket{supplier="A"}[5m])
)
```

**패널 3 — 서킷 브레이커 상태 (State Timeline)**
```promql
resilience4j_circuitbreaker_state{name="supplierA"}
```
- 0 = CLOSED (초록)
- 1 = OPEN (빨강)
- 2 = HALF_OPEN (노랑)

**패널 4 — 타임아웃 비율 (Time Series)**
```promql
rate(supplier_request_total{status="timeout"}[5m])
/
rate(supplier_request_total[5m])
```

**패널 5 — 부분 실패 비율 (Time Series)**
```promql
rate(supplier_partial_failure_total[5m])
/
rate(stay_search_total[5m])
```

---

## 4. 알림 기준 설계

| 조건 | 심각도 | 알림 예시 |
|------|--------|---------|
| 공급사 성공률 < 80% (5분 기준) | 🟡 Warning | "Supplier A 성공률 저하: 75%" |
| 공급사 성공률 < 50% (1분 기준) | 🔴 Critical | "Supplier A 장애 의심 — CB OPEN 임박" |
| CB 상태 OPEN 전환 | 🔴 Critical | "Supplier B 서킷 브레이커 OPEN — 호출 차단 중" |
| P95 응답 시간 > 4,000ms | 🟡 Warning | "Supplier A 응답 지연: P95 4,200ms" |
| 검색 API 전체 실패율 > 5% | 🔴 Critical | "통합 검색 실패 급증" |

---

## 5. 현재 구현 상태와의 관계

| 항목 | 현재 상태 | 추가 필요 |
|------|----------|---------|
| Resilience4j CB·Retry | 구현 완료 | `resilience4j-micrometer` 의존성 추가 |
| Actuator 설정 | 기본 포함 | `prometheus` 엔드포인트 노출 설정 |
| 응답 시간 측정 | 미구현 | WebClient 호출에 `Timer` 적용 |
| 부분 실패 카운터 | 미구현 | `StaySearchService`에 `Counter` 추가 |
| Prometheus 수집 | 미구현 | docker-compose에 Prometheus 추가 |
| Grafana 대시보드 | 미구현 | 대시보드 JSON 작성 |

Resilience4j가 이미 적용되어 있으므로, `resilience4j-micrometer`와 Actuator prometheus 엔드포인트 노출만 추가하면 CB·Retry 관련 핵심 지표는 즉시 수집 가능하다.
