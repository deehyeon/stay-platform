package com.stayplatform.stay.application.dto;

public record SupplierAvailability(
        String supplierHotelCode,
        String supplierRoomTypeCode,
        long totalPrice,
        String currency,
        boolean breakfastIncluded,
        int remainingRooms
) {
}
