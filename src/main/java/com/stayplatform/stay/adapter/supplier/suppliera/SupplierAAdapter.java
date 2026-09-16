package com.stayplatform.stay.adapter.supplier.suppliera;

import com.stayplatform.stay.adapter.supplier.SupplierPort;
import com.stayplatform.stay.adapter.supplier.dto.SupplierAvailability;
import com.stayplatform.stay.adapter.supplier.dto.SupplierHotelInfo;
import com.stayplatform.stay.adapter.supplier.dto.SupplierRoomTypeInfo;
import com.stayplatform.stay.adapter.supplier.suppliera.dto.SupplierAAvailabilityItem;
import com.stayplatform.stay.adapter.supplier.suppliera.dto.SupplierAAvailabilityRes;
import com.stayplatform.stay.adapter.supplier.suppliera.dto.SupplierADailyRate;
import com.stayplatform.stay.adapter.supplier.suppliera.dto.SupplierAHotelItem;
import com.stayplatform.stay.adapter.supplier.suppliera.dto.SupplierAHotelListRes;
import com.stayplatform.stay.domain.SearchCondition;
import com.stayplatform.stay.exception.SupplierUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
public class SupplierAAdapter implements SupplierPort {

    private static final int CHUNK_SIZE = 50;

    private final WebClient webClient;

    public SupplierAAdapter(@Qualifier("supplierAWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Flux<SupplierHotelInfo> fetchHotelList() {
        return webClient.get()
                .uri("/a/v1/hotels")
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(new SupplierUnavailableException()))
                .bodyToMono(SupplierAHotelListRes.class)
                .flatMapMany(res -> Flux.fromIterable(res.items()))
                .map(this::toHotelInfo);
    }

    @Override
    public Flux<SupplierAvailability> fetchAvailability(List<String> supplierHotelCodes, SearchCondition condition) {
        return Flux.fromIterable(partition(supplierHotelCodes, CHUNK_SIZE))
                .flatMap(chunk -> fetchAvailabilityChunk(chunk, condition));
    }

    private Flux<SupplierAvailability> fetchAvailabilityChunk(List<String> hotelCodes, SearchCondition condition) {
        return webClient.get()
                .uri(builder -> builder
                        .path("/a/v1/availability")
                        .queryParam("hotelCodes", String.join(",", hotelCodes))
                        .queryParam("checkIn", condition.checkIn())
                        .queryParam("checkOut", condition.checkOut())
                        .queryParam("adults", condition.adults())
                        .queryParam("children", condition.children())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(new SupplierUnavailableException()))
                .bodyToMono(SupplierAAvailabilityRes.class)
                .flatMapMany(res -> Flux.fromIterable(res.items()))
                .map(this::toAvailability);
    }

    private SupplierHotelInfo toHotelInfo(SupplierAHotelItem item) {
        List<SupplierRoomTypeInfo> roomTypes = item.roomTypes().stream()
                .map(rt -> new SupplierRoomTypeInfo(rt.roomTypeCode(), rt.roomTypeName(), rt.maxOccupancy()))
                .toList();
        return new SupplierHotelInfo(item.hotelCode(), item.hotelName(), roomTypes);
    }

    private SupplierAvailability toAvailability(SupplierAAvailabilityItem item) {
        long totalPrice = item.dailyRates().stream()
                .mapToLong(rate -> rate.nightlyRate() + rate.taxAmount())
                .sum();
        int remainingRooms = item.dailyRates().stream()
                .mapToInt(SupplierADailyRate::remainingRooms)
                .min()
                .orElse(0);
        return new SupplierAvailability(
                item.hotelCode(),
                item.roomTypeCode(),
                totalPrice,
                item.currency(),
                item.breakfastIncluded(),
                remainingRooms
        );
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
