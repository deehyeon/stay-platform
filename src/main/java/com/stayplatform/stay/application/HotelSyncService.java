package com.stayplatform.stay.application;

import com.stayplatform.stay.adapter.persistence.HotelRepository;
import com.stayplatform.stay.adapter.persistence.RoomTypeRepository;
import com.stayplatform.stay.adapter.persistence.SupplierHotelMappingRepository;
import com.stayplatform.stay.adapter.persistence.SupplierRepository;
import com.stayplatform.stay.adapter.persistence.SupplierRoomTypeMappingRepository;
import com.stayplatform.stay.adapter.supplier.SupplierPort;
import com.stayplatform.stay.adapter.supplier.dto.SupplierHotelInfo;
import com.stayplatform.stay.adapter.supplier.dto.SupplierRoomTypeInfo;
import com.stayplatform.stay.domain.Hotel;
import com.stayplatform.stay.domain.RoomType;
import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierHotelMapping;
import com.stayplatform.stay.domain.SupplierRoomTypeMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelSyncService {

    private final List<SupplierPort> supplierPorts;
    private final SupplierRepository supplierRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final SupplierHotelMappingRepository supplierHotelMappingRepository;
    private final SupplierRoomTypeMappingRepository supplierRoomTypeMappingRepository;

    @Transactional
    public void sync() {
        for (SupplierPort port : supplierPorts) {
            syncSupplier(port);
        }
    }

    private void syncSupplier(SupplierPort port) {
        log.info("공급사 동기화 시작: {}", port.supplierCode());
        Supplier supplier = findOrCreateSupplier(port.supplierCode(), port.supplierName());
        List<SupplierHotelInfo> hotelInfos = port.fetchHotelList().collectList().block();
        if (hotelInfos == null) {
            log.warn("공급사 {} 숙소 목록 조회 실패", port.supplierCode());
            return;
        }
        for (SupplierHotelInfo hotelInfo : hotelInfos) {
            syncHotel(supplier, hotelInfo);
        }
        log.info("공급사 동기화 완료: {} ({}개 숙소)", port.supplierCode(), hotelInfos.size());
    }

    private Supplier findOrCreateSupplier(String code, String name) {
        return supplierRepository.findByCode(code)
                .orElseGet(() -> supplierRepository.save(Supplier.create(code, name)));
    }

    private void syncHotel(Supplier supplier, SupplierHotelInfo hotelInfo) {
        SupplierHotelMapping hotelMapping = supplierHotelMappingRepository
                .findBySupplierAndHotelCode(supplier, hotelInfo.supplierHotelCode())
                .orElseGet(() -> {
                    Hotel hotel = hotelRepository.save(Hotel.create(hotelInfo.name()));
                    return supplierHotelMappingRepository.save(
                            SupplierHotelMapping.create(supplier, hotelInfo.supplierHotelCode(), hotel));
                });

        for (SupplierRoomTypeInfo roomTypeInfo : hotelInfo.roomTypes()) {
            syncRoomType(supplier, hotelInfo.supplierHotelCode(), hotelMapping.getHotel(), roomTypeInfo);
        }
    }

    private void syncRoomType(Supplier supplier, String hotelCode, Hotel hotel, SupplierRoomTypeInfo roomTypeInfo) {
        supplierRoomTypeMappingRepository
                .findBySupplierAndHotelCodeAndRoomTypeCode(supplier, hotelCode, roomTypeInfo.supplierRoomTypeCode())
                .orElseGet(() -> {
                    RoomType roomType = roomTypeRepository.save(
                            RoomType.create(hotel, roomTypeInfo.name(), roomTypeInfo.maxOccupancy()));
                    return supplierRoomTypeMappingRepository.save(
                            SupplierRoomTypeMapping.create(supplier, hotelCode, roomTypeInfo.supplierRoomTypeCode(), roomType));
                });
    }
}
