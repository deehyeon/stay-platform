package com.stayplatform.stay.adapter;

import com.stayplatform.global.webapi.ApiControllerAdvice;
import com.stayplatform.stay.application.StaySearchService;
import com.stayplatform.stay.application.dto.StaySearchItemRes;
import com.stayplatform.stay.application.dto.StaySearchRes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StaySearchControllerTest {

    @Mock
    private StaySearchService staySearchService;

    @InjectMocks
    private StaySearchController staySearchController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(staySearchController)
                .setControllerAdvice(new ApiControllerAdvice())
                .build();
    }

    @Test
    void search_정상_요청시_200_반환() throws Exception {
        // given
        StaySearchItemRes item = new StaySearchItemRes(
                1L, "리버사이드 호텔",
                10L, "디럭스 트윈",
                2, 3,
                "SUPPLIER_A", 300000L, "KRW", false
        );
        given(staySearchService.search(any())).willReturn(new StaySearchRes(List.of(item), List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.results").isArray())
                .andExpect(jsonPath("$.data.results[0].hotelName").value("리버사이드 호텔"))
                .andExpect(jsonPath("$.data.failedSuppliers").isEmpty());
    }

    @Test
    void search_체크아웃이_체크인보다_빠르면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-04")
                        .param("checkOut", "2026-09-01")
                        .param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void search_adults_0이면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("INVALID_ADULT_COUNT"));
    }

    @Test
    void search_부분실패시_failedSuppliers_포함() throws Exception {
        // given
        StaySearchItemRes item = new StaySearchItemRes(
                2L, "남산 가든 스테이",
                20L, "스탠다드 더블",
                2, 5,
                "SUPPLIER_B", 250000L, "KRW", true
        );
        given(staySearchService.search(any()))
                .willReturn(new StaySearchRes(List.of(item), List.of("SUPPLIER_A")));

        // when & then
        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedSuppliers[0]").value("SUPPLIER_A"))
                .andExpect(jsonPath("$.data.results").isArray());
    }
}
