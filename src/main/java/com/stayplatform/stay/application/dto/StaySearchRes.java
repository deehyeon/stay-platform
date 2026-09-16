package com.stayplatform.stay.application.dto;

import java.util.List;

public record StaySearchRes(
        List<StaySearchItemRes> results,
        List<String> failedSuppliers
) {
}
