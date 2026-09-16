package com.stayplatform.stay.domain;

import java.time.LocalDate;

public record DailyInventory(
        LocalDate date,
        int remainingRooms
) {
}
