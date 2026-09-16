package com.stayplatform.stay.application.dto;

import java.util.List;

public record SupplierHotelInfo(
        String supplierHotelCode,
        String name,
        List<SupplierRoomTypeInfo> roomTypes
) {
}
