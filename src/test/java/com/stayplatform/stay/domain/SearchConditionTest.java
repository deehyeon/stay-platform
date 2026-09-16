package com.stayplatform.stay.domain;

import com.stayplatform.global.exception.common.GlobalErrorType;
import com.stayplatform.global.exception.common.GlobalException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void of_체크아웃이_체크인보다_빠르면_예외_발생() {
        assertThatThrownBy(() -> SearchCondition.of(
                LocalDate.of(2026, 9, 4),
                LocalDate.of(2026, 9, 1),
                2, 0))
                .isInstanceOf(GlobalException.class)
                .extracting(e -> ((GlobalException) e).getErrorType())
                .isEqualTo(GlobalErrorType.INVALID_DATE_RANGE);
    }

    @Test
    void of_체크인과_체크아웃이_같으면_예외_발생() {
        assertThatThrownBy(() -> SearchCondition.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1),
                2, 0))
                .isInstanceOf(GlobalException.class)
                .extracting(e -> ((GlobalException) e).getErrorType())
                .isEqualTo(GlobalErrorType.INVALID_DATE_RANGE);
    }

    @Test
    void of_성인이_0명이면_예외_발생() {
        assertThatThrownBy(() -> SearchCondition.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 4),
                0, 0))
                .isInstanceOf(GlobalException.class)
                .extracting(e -> ((GlobalException) e).getErrorType())
                .isEqualTo(GlobalErrorType.INVALID_ADULT_COUNT);
    }
}
