package com.stayplatform.stay.application;

import com.stayplatform.stay.application.dto.SupplierAvailability;
import com.stayplatform.stay.application.required.SupplierHotelMappingRepository;
import com.stayplatform.stay.application.required.SupplierPort;
import com.stayplatform.stay.application.required.SupplierRepository;
import com.stayplatform.stay.application.required.SupplierRoomTypeMappingRepository;
import com.stayplatform.stay.application.dto.StaySearchItemRes;
import com.stayplatform.stay.application.dto.StaySearchRes;
import com.stayplatform.stay.domain.SearchCondition;
import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierHotelMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaySearchService {

    private final List<SupplierPort> supplierPorts;
    private final SupplierRepository supplierRepository;
    private final SupplierHotelMappingRepository supplierHotelMappingRepository;
    private final SupplierRoomTypeMappingRepository supplierRoomTypeMappingRepository;

    @Transactional(readOnly = true)
    public StaySearchRes search(SearchCondition condition) {
        List<SupplierContext> contexts = buildContexts();
        List<String> failedSuppliers = new CopyOnWriteArrayList<>();

        List<TaggedAvailability> rawResults = Flux
                .merge(contexts.stream()
                        .map(ctx -> ctx.port()
                                .fetchAvailability(ctx.hotelCodes(), condition)
                                .map(a -> new TaggedAvailability(ctx.supplier(), a))
                                .onErrorResume(e -> {
                                    log.warn("공급사 {} 조회 실패: {}", ctx.supplier().getCode(), e.getMessage());
                                    failedSuppliers.add(ctx.supplier().getCode());
                                    return Flux.empty();
                                }))
                        .toList())
                .collectList()
                .block();

        List<StaySearchItemRes> items = (rawResults == null ? List.<TaggedAvailability>of() : rawResults)
                .stream()
                .map(raw -> toSearchItem(raw.supplier(), raw.availability()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        return new StaySearchRes(items, List.copyOf(failedSuppliers));
    }

    private List<SupplierContext> buildContexts() {
        return supplierPorts.stream()
                .flatMap(port -> supplierRepository.findByCode(port.supplierCode())
                        .map(supplier -> {
                            List<String> codes = supplierHotelMappingRepository.findAllBySupplier(supplier)
                                    .stream()
                                    .map(SupplierHotelMapping::getHotelCode)
                                    .toList();
                            return Stream.of(new SupplierContext(port, supplier, codes));
                        })
                        .orElseGet(Stream::empty))
                .toList();
    }

    private Optional<StaySearchItemRes> toSearchItem(Supplier supplier, SupplierAvailability availability) {
        return supplierHotelMappingRepository
                .findBySupplierAndHotelCode(supplier, availability.supplierHotelCode())
                .flatMap(hotelMapping ->
                        supplierRoomTypeMappingRepository
                                .findBySupplierAndHotelCodeAndRoomTypeCode(
                                        supplier,
                                        availability.supplierHotelCode(),
                                        availability.supplierRoomTypeCode())
                                .map(roomTypeMapping -> new StaySearchItemRes(
                                        hotelMapping.getHotel().getId(),
                                        hotelMapping.getHotel().getName(),
                                        roomTypeMapping.getRoomType().getId(),
                                        roomTypeMapping.getRoomType().getName(),
                                        roomTypeMapping.getRoomType().getMaxOccupancy(),
                                        availability.remainingRooms(),
                                        supplier.getCode(),
                                        availability.totalPrice(),
                                        availability.currency(),
                                        availability.breakfastIncluded()
                                )));
    }

    private record SupplierContext(SupplierPort port, Supplier supplier, List<String> hotelCodes) {}

    private record TaggedAvailability(Supplier supplier, SupplierAvailability availability) {}
}
