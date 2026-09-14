package com.stayplatform.stay.adapter.persistence;

import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierRoomTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRoomTypeMappingRepository extends JpaRepository<SupplierRoomTypeMapping, Long> {

    Optional<SupplierRoomTypeMapping> findBySupplierAndHotelCodeAndRoomTypeCode(
            Supplier supplier, String hotelCode, String roomTypeCode);

    List<SupplierRoomTypeMapping> findAllBySupplierAndHotelCode(Supplier supplier, String hotelCode);
}
