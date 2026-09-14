package com.stayplatform.stay.domain;

import com.stayplatform.global.domain.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "supplier_hotel_mappings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"supplier_id", "hotel_code"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierHotelMapping extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "hotel_code", nullable = false)
    private String hotelCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    private SupplierHotelMapping(Supplier supplier, String hotelCode, Hotel hotel) {
        this.supplier = supplier;
        this.hotelCode = hotelCode;
        this.hotel = hotel;
    }

    public static SupplierHotelMapping create(Supplier supplier, String hotelCode, Hotel hotel) {
        return new SupplierHotelMapping(supplier, hotelCode, hotel);
    }
}
