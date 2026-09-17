package com.stayplatform.stay.adapter.supplier;

import com.stayplatform.stay.adapter.supplier.suppliera.SupplierAAdapter;
import com.stayplatform.stay.adapter.supplier.supplierb.SupplierBAdapter;
import com.stayplatform.stay.domain.SearchCondition;
import com.stayplatform.stay.exception.SupplierUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierResilienceTest {

    private MockWebServer mockWebServer;
    private WebClient webClient;

    // CB: 3회 슬라이딩 윈도우, 실패율 50% 이상 시 OPEN / Retry: 최대 3회, 대기 10ms
    private static final CircuitBreakerConfig CB_CONFIG = CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build();

    private static final RetryConfig RETRY_CONFIG = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(10))
            .retryExceptions(SupplierUnavailableException.class)
            .ignoreExceptions(CallNotPermittedException.class)
            .build();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ──── Retry ────

    @Test
    void supplierA_재시도_성공_2번실패후_3번째_성공() {
        // given — 2번 실패, 3번째 성공
        enqueueError503();
        enqueueError503();
        enqueueSupplierAEmptySuccess();

        SupplierAAdapter adapter = buildAdapterA(CB_CONFIG, RETRY_CONFIG);
        SearchCondition condition = condition();

        // when
        adapter.fetchAvailability(List.of("A-10023"), condition).collectList().block();

        // then — 총 3회 호출
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    void supplierA_재시도_3회_모두실패_예외전파() {
        // given — 3번 모두 실패 (최대 재시도 소진)
        enqueueError503();
        enqueueError503();
        enqueueError503();

        SupplierAAdapter adapter = buildAdapterA(CB_CONFIG, RETRY_CONFIG);
        SearchCondition condition = condition();

        // when & then
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-10023"), condition).collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    void supplierB_재시도_성공_2번실패후_3번째_성공() {
        // given — 2번 실패(E503), 3번째 성공
        enqueueSupplierBError();
        enqueueSupplierBError();
        enqueueSupplierBEmptySuccess();

        SupplierBAdapter adapter = buildAdapterB(CB_CONFIG, RETRY_CONFIG);
        SearchCondition condition = condition();

        // when
        adapter.fetchAvailability(List.of("B77120"), condition).collectList().block();

        // then — 총 3회 호출
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    // ──── CircuitBreaker ────

    @Test
    void supplierA_서킷브레이커_OPEN_후_CallNotPermitted() throws InterruptedException {
        // given — 4회 중 3회 실패 → 실패율 75% → OPEN
        enqueueSupplierAEmptySuccess();
        enqueueError503();
        enqueueError503();
        enqueueError503();

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CB_CONFIG);
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
        SupplierAAdapter adapter = new SupplierAAdapter(webClient, cbRegistry, retryRegistry);
        SearchCondition condition = condition();

        // 슬라이딩 윈도우를 채워 CB를 OPEN 상태로 만든다
        adapter.fetchAvailability(List.of("A-10001"), condition).collectList().block();
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-10002"), condition).collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-10003"), condition).collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-10004"), condition).collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);

        CircuitBreaker cb = cbRegistry.circuitBreaker("supplierA");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // when — OPEN 상태에서 추가 호출 시 CallNotPermittedException
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-99999"), condition).collectList().block())
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void supplierA_서킷브레이커_HALFOPEN_성공_후_CLOSED() throws InterruptedException {
        // given — 슬라이딩 윈도우를 채워 CB OPEN
        enqueueError503();
        enqueueError503();
        enqueueError503();
        enqueueError503();

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CB_CONFIG);
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
        SupplierAAdapter adapter = new SupplierAAdapter(webClient, cbRegistry, retryRegistry);
        SearchCondition condition = condition();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-10001"), condition).collectList().block())
                    .isInstanceOf(SupplierUnavailableException.class);
        }

        CircuitBreaker cb = cbRegistry.circuitBreaker("supplierA");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // waitDurationInOpenState(100ms) 경과 후 호출 시도 → HALF_OPEN 거쳐 CLOSED 복구
        Thread.sleep(150);
        enqueueSupplierAEmptySuccess();
        adapter.fetchAvailability(List.of("A-10001"), condition).collectList().block();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ──── helpers ────

    private SupplierAAdapter buildAdapterA(CircuitBreakerConfig cbConfig, RetryConfig retryConfig) {
        return new SupplierAAdapter(webClient,
                CircuitBreakerRegistry.of(cbConfig),
                RetryRegistry.of(retryConfig));
    }

    private SupplierBAdapter buildAdapterB(CircuitBreakerConfig cbConfig, RetryConfig retryConfig) {
        return new SupplierBAdapter(webClient,
                CircuitBreakerRegistry.of(cbConfig),
                RetryRegistry.of(retryConfig));
    }

    private SearchCondition condition() {
        return SearchCondition.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);
    }

    private void enqueueError503() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
    }

    private void enqueueSupplierAEmptySuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {"items":[]}
                        """)
                .addHeader("Content-Type", "application/json"));
    }

    private void enqueueSupplierBError() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}
                        """)
                .addHeader("Content-Type", "application/json"));
    }

    private void enqueueSupplierBEmptySuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[]}}
                        """)
                .addHeader("Content-Type", "application/json"));
    }
}
