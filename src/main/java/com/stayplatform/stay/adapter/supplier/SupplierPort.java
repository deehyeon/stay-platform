package com.stayplatform.stay.adapter.supplier;

import com.stayplatform.stay.domain.SearchCondition;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SupplierPort {
    Flux<SupplierHotelInfo> fetchHotelList();
    Flux<SupplierAvailability> fetchAvailability(List<String> supplierHotelCodes, SearchCondition condition);
}
