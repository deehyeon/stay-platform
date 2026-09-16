package com.stayplatform.stay.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryCalculatorTest {

    @Test
    void calculate_연박시_최솟값_반환() {
        // given
        List<DailyInventory> inventory = List.of(
                new DailyInventory(LocalDate.of(2026, 9, 1), 3),
                new DailyInventory(LocalDate.of(2026, 9, 2), 0),
                new DailyInventory(LocalDate.of(2026, 9, 3), 5)
        );

        // when
        int result = InventoryCalculator.calculate(inventory);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    void calculate_하루라도_재고0이면_예약불가() {
        // given
        List<DailyInventory> inventory = List.of(
                new DailyInventory(LocalDate.of(2026, 9, 1), 5),
                new DailyInventory(LocalDate.of(2026, 9, 2), 0),
                new DailyInventory(LocalDate.of(2026, 9, 3), 3)
        );

        // when
        int result = InventoryCalculator.calculate(inventory);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    void calculate_모든날짜_재고있으면_최솟값_반환() {
        // given
        List<DailyInventory> inventory = List.of(
                new DailyInventory(LocalDate.of(2026, 9, 1), 5),
                new DailyInventory(LocalDate.of(2026, 9, 2), 2),
                new DailyInventory(LocalDate.of(2026, 9, 3), 8)
        );

        // when
        int result = InventoryCalculator.calculate(inventory);

        // then
        assertThat(result).isEqualTo(2);
    }
}
