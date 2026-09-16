package com.stayplatform.stay.adapter.supplier.dto;

import java.util.List;

public record SupplierHotelInfo(
        String supplierHotelCode,
        String name,
        List<SupplierRoomTypeInfo> roomTypes
) {
}
