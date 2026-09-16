package com.stayplatform.stay.adapter;

import com.stayplatform.global.webapi.response.ApiResponse;
import com.stayplatform.stay.application.StaySearchService;
import com.stayplatform.stay.application.dto.StaySearchRes;
import com.stayplatform.stay.domain.SearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/stays")
@RequiredArgsConstructor
public class StaySearchController {

    private final StaySearchService staySearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<StaySearchRes>> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int adults,
            @RequestParam(defaultValue = "0") int children) {
        SearchCondition condition = SearchCondition.of(checkIn, checkOut, adults, children);
        StaySearchRes result = staySearchService.search(condition);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
