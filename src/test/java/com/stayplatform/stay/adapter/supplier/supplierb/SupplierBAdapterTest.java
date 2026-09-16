package com.stayplatform.stay.adapter.supplier.supplierb;

import com.stayplatform.stay.application.dto.SupplierAvailability;
import com.stayplatform.stay.application.dto.SupplierHotelInfo;
import com.stayplatform.stay.domain.SearchCondition;
import com.stayplatform.stay.exception.SupplierUnavailableException;
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

class SupplierBAdapterTest {

    private MockWebServer mockWebServer;
    private SupplierBAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        adapter = new SupplierBAdapter(webClient);
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
                        """)
                .addHeader("Content-Type", "application/json"));

        // when
        List<SupplierHotelInfo> result = adapter.fetchHotelList().collectList().block();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).supplierHotelCode()).isEqualTo("B77120");
        assertThat(result.get(0).name()).isEqualTo("Riverside Hotel Seoul");
        assertThat(result.get(0).roomTypes()).hasSize(1);
        assertThat(result.get(0).roomTypes().get(0).supplierRoomTypeCode()).isEqualTo("R-401");
    }

    @Test
    void fetchHotelList_resultCodeE503_예외발생() {
        // given — HTTP 200 + resultCode: E503
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "resultCode": "E503",
                          "resultMessage": "TEMPORARILY_UNAVAILABLE",
                          "data": null
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        // when & then
        assertThatThrownBy(() -> adapter.fetchHotelList().collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
    }

    @Test
    void fetchAvailability_정상응답_재고최솟값() {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
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
                        """)
                .addHeader("Content-Type", "application/json"));

        SearchCondition condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        // when
        List<SupplierAvailability> result = adapter.fetchAvailability(List.of("B77120"), condition)
                .collectList().block();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalPrice()).isEqualTo(452_000L);
        assertThat(result.get(0).remainingRooms()).isEqualTo(1); // min(3, 1, 5)
        assertThat(result.get(0).currency()).isEqualTo("KRW");
        assertThat(result.get(0).breakfastIncluded()).isTrue();
    }

    @Test
    void fetchAvailability_resultCodeE503_예외발생() {
        // given — HTTP 200 + resultCode: E503
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "resultCode": "E503",
                          "resultMessage": "TEMPORARILY_UNAVAILABLE",
                          "data": null
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        SearchCondition condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        // when & then
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("B77120"), condition).collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
    }

    @Test
    void fetchAvailability_dataNull_예외발생() {
        // given — HTTP 200 + resultCode: "0000" 이지만 data: null
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "resultCode": "0000",
                          "resultMessage": "SUCCESS",
                          "data": null
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        SearchCondition condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        // when & then
        assertThatThrownBy(() -> adapter.fetchAvailability(List.of("B77120"), condition).collectList().block())
                .isInstanceOf(SupplierUnavailableException.class);
    }

    @Test
    void fetchAvailability_숙소75개_2회_분할호출() {
        // given
        String successBody = """
                {
                  "resultCode": "0000",
                  "resultMessage": "SUCCESS",
                  "data": { "items": [] }
                }
                """;
        mockWebServer.enqueue(new MockResponse().setBody(successBody).addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setBody(successBody).addHeader("Content-Type", "application/json"));

        List<String> propertyCodes = IntStream.range(0, 75)
                .mapToObj(i -> String.format("B%05d", i))
                .toList();
        SearchCondition condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        // when
        adapter.fetchAvailability(propertyCodes, condition).collectList().block();

        // then
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }
}
