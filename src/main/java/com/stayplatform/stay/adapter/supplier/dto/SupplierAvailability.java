package com.stayplatform.stay.adapter.supplier.dto;

public record SupplierAvailability(
        String supplierHotelCode,
        String supplierRoomTypeCode,
        long totalPrice,
        String currency,
        boolean breakfastIncluded,
        int remainingRooms
) {
}
