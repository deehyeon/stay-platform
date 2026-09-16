package com.stayplatform.stay.domain;

import java.time.LocalDate;

public record SearchCondition(
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int children
) {
    public static SearchCondition of(LocalDate checkIn, LocalDate checkOut, int adults, int children) {
        return new SearchCondition(checkIn, checkOut, adults, children);
    }
}
