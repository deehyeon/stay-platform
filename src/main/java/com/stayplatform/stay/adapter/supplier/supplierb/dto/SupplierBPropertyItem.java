package com.stayplatform.stay.adapter.supplier.supplierb.dto;

import java.util.List;

public record SupplierBPropertyItem(
        String propertyId,
        String propertyName,
        List<SupplierBRoomItem> rooms
) {
}
