package com.stayplatform.stay.adapter.supplier.supplierb;

import com.stayplatform.stay.adapter.supplier.SupplierChunkUtil;
import com.stayplatform.stay.adapter.supplier.SupplierPort;
import com.stayplatform.stay.adapter.supplier.dto.SupplierAvailability;
import com.stayplatform.stay.adapter.supplier.dto.SupplierHotelInfo;
import com.stayplatform.stay.adapter.supplier.dto.SupplierRoomTypeInfo;
import com.stayplatform.stay.adapter.supplier.supplierb.dto.SupplierBAvailabilityData;
import com.stayplatform.stay.adapter.supplier.supplierb.dto.SupplierBAvailabilityItem;
import com.stayplatform.stay.adapter.supplier.supplierb.dto.SupplierBHotelListData;
import com.stayplatform.stay.adapter.supplier.supplierb.dto.SupplierBPropertyItem;
import com.stayplatform.stay.adapter.supplier.supplierb.dto.SupplierBResponse;
import com.stayplatform.stay.domain.DailyInventory;
import com.stayplatform.stay.domain.InventoryCalculator;
import com.stayplatform.stay.domain.SearchCondition;
import com.stayplatform.stay.exception.SupplierUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Component
public class SupplierBAdapter implements SupplierPort {

    private static final int CHUNK_SIZE = 50;

    private final WebClient webClient;

    public SupplierBAdapter(@Qualifier("supplierBWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String supplierCode() {
        return "SUPPLIER_B";
    }

    @Override
    public String supplierName() {
        return "Supplier B";
    }

    @Override
    public Flux<SupplierHotelInfo> fetchHotelList() {
        return webClient.get()
                .uri("/b/api/properties")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<SupplierBResponse<SupplierBHotelListData>>() {})
                .flatMap(res -> {
                    if (!res.isSuccess()) {
                        return Mono.error(new SupplierUnavailableException());
                    }
                    return Mono.just(res.data());
                })
                .flatMapMany(data -> Flux.fromIterable(data.items()))
                .map(this::toHotelInfo);
    }

    @Override
    public Flux<SupplierAvailability> fetchAvailability(List<String> supplierHotelCodes, SearchCondition condition) {
        return Flux.fromIterable(SupplierChunkUtil.partition(supplierHotelCodes, CHUNK_SIZE))
                .flatMap(chunk -> fetchAvailabilityChunk(chunk, condition));
    }

    private Flux<SupplierAvailability> fetchAvailabilityChunk(List<String> propertyCodes, SearchCondition condition) {
        return webClient.get()
                .uri(builder -> builder
                        .path("/b/api/search")
                        .queryParam("propertyIds", String.join(",", propertyCodes))
                        .queryParam("checkIn", condition.checkIn())
                        .queryParam("checkOut", condition.checkOut())
                        .queryParam("adults", condition.adults())
                        .queryParam("children", condition.children())
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<SupplierBResponse<SupplierBAvailabilityData>>() {})
                .flatMap(res -> {
                    if (!res.isSuccess()) {
                        return Mono.error(new SupplierUnavailableException());
                    }
                    return Mono.just(res.data());
                })
                .flatMapMany(data -> Flux.fromIterable(data.items()))
                .map(this::toAvailability);
    }

    private SupplierHotelInfo toHotelInfo(SupplierBPropertyItem item) {
        List<SupplierRoomTypeInfo> roomTypes = item.rooms().stream()
                .map(r -> new SupplierRoomTypeInfo(r.roomId(), r.roomName(), r.maxOccupancy()))
                .toList();
        return new SupplierHotelInfo(item.propertyId(), item.propertyName(), roomTypes);
    }

    private SupplierAvailability toAvailability(SupplierBAvailabilityItem item) {
        List<DailyInventory> inventory = item.inventory().stream()
                .map(inv -> new DailyInventory(LocalDate.parse(inv.date()), inv.remainingRooms()))
                .toList();
        return new SupplierAvailability(
                item.propertyId(),
                item.roomId(),
                item.totalPrice(),
                item.currency(),
                item.breakfastIncluded(),
                InventoryCalculator.calculate(inventory)
        );
    }
}
