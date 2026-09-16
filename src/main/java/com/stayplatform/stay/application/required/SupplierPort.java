package com.stayplatform.stay.application.required;

import com.stayplatform.stay.application.dto.SupplierAvailability;
import com.stayplatform.stay.application.dto.SupplierHotelInfo;
import com.stayplatform.stay.domain.SearchCondition;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SupplierPort {
    String supplierCode();
    String supplierName();
    Flux<SupplierHotelInfo> fetchHotelList();
    Flux<SupplierAvailability> fetchAvailability(List<String> supplierHotelCodes, SearchCondition condition);
}
