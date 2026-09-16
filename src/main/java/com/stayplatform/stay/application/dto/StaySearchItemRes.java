package com.stayplatform.stay.application.dto;

public record StaySearchItemRes(
        Long hotelId,
        String hotelName,
        Long roomTypeId,
        String roomTypeName,
        int maxOccupancy,
        int remainingRooms,
        String supplier,
        long totalPrice,
        String currency,
        boolean breakfastIncluded
) {
}
