package com.stayplatform.fixture;

import com.stayplatform.stay.domain.Hotel;
import com.stayplatform.stay.domain.RoomType;
import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierHotelMapping;
import com.stayplatform.stay.domain.SupplierRoomTypeMapping;

public class TestEntityFixture {

    public static Supplier createSupplier(String code, String name) {
        return Supplier.create(code, name);
    }

    public static Hotel createHotel(String name) {
        return Hotel.create(name);
    }

    public static RoomType createRoomType(Hotel hotel, String name, int maxOccupancy) {
        return RoomType.create(hotel, name, maxOccupancy);
    }

    public static SupplierHotelMapping createHotelMapping(Supplier supplier, String hotelCode, Hotel hotel) {
        return SupplierHotelMapping.create(supplier, hotelCode, hotel);
    }

    public static SupplierRoomTypeMapping createRoomTypeMapping(
            Supplier supplier, String hotelCode, String roomTypeCode, RoomType roomType) {
        return SupplierRoomTypeMapping.create(supplier, hotelCode, roomTypeCode, roomType);
    }
}
