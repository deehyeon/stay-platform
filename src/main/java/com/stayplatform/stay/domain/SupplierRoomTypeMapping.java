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
        name = "supplier_room_type_mappings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"supplier_id", "hotel_code", "room_type_code"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierRoomTypeMapping extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "hotel_code", nullable = false)
    private String hotelCode;

    @Column(name = "room_type_code", nullable = false)
    private String roomTypeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    private SupplierRoomTypeMapping(Supplier supplier, String hotelCode, String roomTypeCode, RoomType roomType) {
        this.supplier = supplier;
        this.hotelCode = hotelCode;
        this.roomTypeCode = roomTypeCode;
        this.roomType = roomType;
    }

    public static SupplierRoomTypeMapping create(Supplier supplier, String hotelCode, String roomTypeCode, RoomType roomType) {
        return new SupplierRoomTypeMapping(supplier, hotelCode, roomTypeCode, roomType);
    }
}
