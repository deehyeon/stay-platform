package com.stayplatform.stay.adapter.supplier;

import com.stayplatform.stay.adapter.supplier.dto.SupplierAvailability;
import com.stayplatform.stay.adapter.supplier.dto.SupplierHotelInfo;
import com.stayplatform.stay.domain.SearchCondition;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SupplierPort {
    String supplierCode();
    String supplierName();
    Flux<SupplierHotelInfo> fetchHotelList();
    Flux<SupplierAvailability> fetchAvailability(List<String> supplierHotelCodes, SearchCondition condition);
}
