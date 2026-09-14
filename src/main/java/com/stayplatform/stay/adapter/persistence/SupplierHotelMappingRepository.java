package com.stayplatform.stay.adapter.persistence;

import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierHotelMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierHotelMappingRepository extends JpaRepository<SupplierHotelMapping, Long> {

    Optional<SupplierHotelMapping> findBySupplierAndHotelCode(Supplier supplier, String hotelCode);

    List<SupplierHotelMapping> findAllBySupplier(Supplier supplier);
}
