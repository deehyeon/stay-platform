package com.stayplatform.stay.domain;

import java.util.List;

public class InventoryCalculator {

    private InventoryCalculator() {
    }

    public static int calculate(List<DailyInventory> inventory) {
        return inventory.stream()
                .mapToInt(DailyInventory::remainingRooms)
                .min()
                .orElse(0);
    }
}
