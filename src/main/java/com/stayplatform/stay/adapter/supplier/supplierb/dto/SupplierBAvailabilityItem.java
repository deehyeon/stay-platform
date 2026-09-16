package com.stayplatform.stay.adapter.supplier.supplierb.dto;

import java.util.List;

public record SupplierBAvailabilityItem(
        String propertyId,
        String propertyName,
        String roomId,
        String roomName,
        int maxOccupancy,
        boolean breakfastIncluded,
        String currency,
        long totalPrice,
        boolean taxIncluded,
        List<SupplierBInventory> inventory
) {
}
