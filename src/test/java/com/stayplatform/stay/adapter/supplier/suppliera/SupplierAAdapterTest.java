package com.stayplatform.stay.adapter.supplier.suppliera;

import com.stayplatform.stay.application.dto.SupplierAvailability;
import com.stayplatform.stay.application.dto.SupplierHotelInfo;
import com.stayplatform.stay.domain.SearchCondition;
import com.stayplatform.stay.exception.SupplierUnavailableException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierAAdapterTest {

    private MockWebServer mockWebServer;
    private SupplierAAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        // CB: failureRateThreshold=100 → 절대 열리지 않음 / Retry: maxAttempts=1 → 재시도 없음
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom().slidingWindowSize(100).failureRateThreshold(100f).build());
        RetryRegistry retryRegistry = RetryRegistry.of(
                RetryConfig.custom().maxAttempts(1).build());
        adapter = new SupplierAAdapter(webClient, cbRegistry, retryRegistry);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchHotelList_정상응답_내부모델변환() {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
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
                        """)
                .addHeader("Content-Type", "application/json"));

        // when
        List<SupplierHotelInfo> result = adapter.fetchHotelList().collectList().block();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).supplierHotelCode()).isEqualTo("A-10023");
        assertThat(result.get(0).name()).isEqualTo("Riverside Hotel Seoul");
        assertThat(result.get(0).roomTypes()).hasSize(1);
        assertThat(result.get(0).roomTypes().get(0).supplierRoomTypeCode()).isEqualTo("DLX-TWN");
    }

    @Test
    void fetchHotelList_HTTP503_예외발생() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        // when & then
        assertThatThrownBy(() -> adapter.fetchHotelList().collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
    }

    @Test
    void fetchAvailability_정상응답_총액계산_및_재고최솟값() {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
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
                        """)
                .addHeader("Content-Type", "application/json"));

        SearchCondition condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        // when
        List<SupplierAvailability> result = adapter.fetchAvailability(List.of("A-10023"), condition)
                .collectList().block();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalPrice()).isEqualTo(429_000L); // (120000+12000) + (150000+15000) + (120000+12000)
        assertThat(result.get(0).remainingRooms()).isEqualTo(1);    // min(3, 1, 5)
        assertThat(result.get(0).currency()).isEqualTo("KRW");
        assertThat(result.get(0).breakfastIncluded()).isFalse();
    }

    @Test
    void fetchAvailability_HTTP503_예외발생() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        SearchCondition condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        // when & then
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-10023"), condition).collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
    }

    @Test
    void fetchAvailability_숙소75개_2회_분할호출() {
        // given
        String emptyBody = """
                {"items": []}
                """;
        mockWebServer.enqueue(new MockResponse().setBody(emptyBody).addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody(emptyBody).addHeader("Content-Type", "application/json"));

        List<String> hotelCodes = IntStream.range(0, 75)
                .mapToObj(i -> String.format("A-%05d", i))
                .toList();
        SearchCondition condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        // when
        adapter.fetchAvailability(hotelCodes, condition).collectList().block();

        // then
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }
}
