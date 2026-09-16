package com.stayplatform.stay.adapter.supplier.suppliera.dto;

import java.util.List;

public record SupplierAHotelItem(
        String hotelCode,
        String hotelName,
        List<SupplierARoomTypeItem> roomTypes
) {
}
