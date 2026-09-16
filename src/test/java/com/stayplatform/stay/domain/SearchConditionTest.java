package com.stayplatform.stay.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SearchConditionTest {

    @Test
    void of_검색조건_생성() {
        LocalDate checkIn = LocalDate.of(2026, 9, 1);
        LocalDate checkOut = LocalDate.of(2026, 9, 4);

        SearchCondition condition = SearchCondition.of(checkIn, checkOut, 2, 1);

        assertThat(condition.checkIn()).isEqualTo(checkIn);
        assertThat(condition.checkOut()).isEqualTo(checkOut);
        assertThat(condition.adults()).isEqualTo(2);
        assertThat(condition.children()).isEqualTo(1);
    }
}
