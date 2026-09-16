package com.stayplatform.stay.domain;

import com.stayplatform.global.exception.common.GlobalErrorType;
import com.stayplatform.global.exception.common.GlobalException;

import java.time.LocalDate;

public record SearchCondition(
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children
) {
    public static SearchCondition of(LocalDate checkIn, LocalDate checkOut, int adults, int children) {
        if (!checkOut.isAfter(checkIn)) {
            throw new GlobalException(GlobalErrorType.INVALID_DATE_RANGE);
        }
        if (adults < 1) {
            throw new GlobalException(GlobalErrorType.INVALID_ADULT_COUNT);
        }
        return new SearchCondition(checkIn, checkOut, adults, children);
    }
}
