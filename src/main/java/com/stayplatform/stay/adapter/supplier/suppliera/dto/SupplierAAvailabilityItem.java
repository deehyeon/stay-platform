package com.stayplatform.stay.adapter.supplier.suppliera.dto;

import java.util.List;

public record SupplierAAvailabilityItem(
        String hotelCode,
        String hotelName,
        String roomTypeCode,
        String roomTypeName,
        int maxOccupancy,
        boolean breakfastIncluded,
        String currency,
        List<SupplierADailyRate> dailyRates
) {
}
