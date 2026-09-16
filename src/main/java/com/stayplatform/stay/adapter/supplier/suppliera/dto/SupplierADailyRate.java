package com.stayplatform.stay.adapter.supplier.suppliera.dto;

public record SupplierADailyRate(
        String date,
        int remainingRooms,
        long nightlyRate,
        long taxAmount
) {
}
