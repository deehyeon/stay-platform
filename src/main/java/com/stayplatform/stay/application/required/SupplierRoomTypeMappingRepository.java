package com.stayplatform.stay.application.required;

import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierRoomTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRoomTypeMappingRepository extends JpaRepository<SupplierRoomTypeMapping, Long> {

    Optional<SupplierRoomTypeMapping> findBySupplierAndHotelCodeAndRoomTypeCode(
            Supplier supplier, String hotelCode, String roomTypeCode);
}

