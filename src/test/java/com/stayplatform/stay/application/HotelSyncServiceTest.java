package com.stayplatform.stay.application;

import com.stayplatform.fixture.TestEntityFixture;
import com.stayplatform.stay.application.dto.SupplierHotelInfo;
import com.stayplatform.stay.application.dto.SupplierRoomTypeInfo;
import com.stayplatform.stay.application.required.HotelRepository;
import com.stayplatform.stay.application.required.RoomTypeRepository;
import com.stayplatform.stay.application.required.SupplierHotelMappingRepository;
import com.stayplatform.stay.application.required.SupplierPort;
import com.stayplatform.stay.application.required.SupplierRepository;
import com.stayplatform.stay.application.required.SupplierRoomTypeMappingRepository;
import com.stayplatform.stay.domain.Hotel;
import com.stayplatform.stay.domain.RoomType;
import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierHotelMapping;
import com.stayplatform.stay.domain.SupplierRoomTypeMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HotelSyncServiceTest {

    @Mock
    private SupplierPort supplierPort;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private SupplierHotelMappingRepository supplierHotelMappingRepository;

    @Mock
    private SupplierRoomTypeMappingRepository supplierRoomTypeMappingRepository;

    private HotelSyncService hotelSyncService;

    @BeforeEach
    void setUp() {
        hotelSyncService = new HotelSyncService(
                List.of(supplierPort),
                supplierRepository,
                hotelRepository,
                roomTypeRepository,
                supplierHotelMappingRepository,
                supplierRoomTypeMappingRepository
        );
    }

    @Test
    void sync_신규_공급사코드_신규_호텔_및_매핑_생성() {
        // given
        Supplier supplier = TestEntityFixture.createSupplier("SUPPLIER_A", "Supplier A");
        Hotel hotel = TestEntityFixture.createHotel("리버사이드 호텔");
        SupplierHotelInfo hotelInfo = new SupplierHotelInfo("A-10023", "리버사이드 호텔", List.of());

        given(supplierPort.supplierCode()).willReturn("SUPPLIER_A");
        given(supplierPort.supplierName()).willReturn("Supplier A");
        given(supplierRepository.findByCode("SUPPLIER_A")).willReturn(Optional.of(supplier));
        given(supplierPort.fetchHotelList()).willReturn(Flux.just(hotelInfo));
        given(supplierHotelMappingRepository.findBySupplierAndHotelCode(supplier, "A-10023"))
                .willReturn(Optional.empty());
        given(hotelRepository.save(any())).willReturn(hotel);
        given(supplierHotelMappingRepository.save(any()))
                .willReturn(SupplierHotelMapping.create(supplier, "A-10023", hotel));

        // when
        hotelSyncService.sync();

        // then
        then(hotelRepository).should().save(any(Hotel.class));
        then(supplierHotelMappingRepository).should().save(any(SupplierHotelMapping.class));
    }

    @Test
    void sync_기존_공급사코드_재동기화시_기존_매핑_유지() {
        // given
        Supplier supplier = TestEntityFixture.createSupplier("SUPPLIER_A", "Supplier A");
        Hotel existingHotel = TestEntityFixture.createHotel("리버사이드 호텔");
        SupplierHotelMapping existingMapping = SupplierHotelMapping.create(supplier, "A-10023", existingHotel);
        SupplierHotelInfo hotelInfo = new SupplierHotelInfo("A-10023", "리버사이드 호텔", List.of());

        given(supplierPort.supplierCode()).willReturn("SUPPLIER_A");
        given(supplierPort.supplierName()).willReturn("Supplier A");
        given(supplierRepository.findByCode("SUPPLIER_A")).willReturn(Optional.of(supplier));
        given(supplierPort.fetchHotelList()).willReturn(Flux.just(hotelInfo));
        given(supplierHotelMappingRepository.findBySupplierAndHotelCode(supplier, "A-10023"))
                .willReturn(Optional.of(existingMapping));

        // when
        hotelSyncService.sync();

        // then — 기존 매핑이 있으면 Hotel을 새로 생성하지 않음
        then(hotelRepository).should(never()).save(any(Hotel.class));
        then(supplierHotelMappingRepository).should(never()).save(any(SupplierHotelMapping.class));
    }

    @Test
    void sync_신규_객실타입_매핑_생성() {
        // given
        Supplier supplier = TestEntityFixture.createSupplier("SUPPLIER_A", "Supplier A");
        Hotel hotel = TestEntityFixture.createHotel("리버사이드 호텔");
        SupplierHotelMapping existingMapping = SupplierHotelMapping.create(supplier, "A-10023", hotel);
        SupplierRoomTypeInfo roomTypeInfo = new SupplierRoomTypeInfo("DLX-TWN", "디럭스 트윈", 2);
        SupplierHotelInfo hotelInfo = new SupplierHotelInfo("A-10023", "리버사이드 호텔", List.of(roomTypeInfo));

        given(supplierPort.supplierCode()).willReturn("SUPPLIER_A");
        given(supplierPort.supplierName()).willReturn("Supplier A");
        given(supplierRepository.findByCode("SUPPLIER_A")).willReturn(Optional.of(supplier));
        given(supplierPort.fetchHotelList()).willReturn(Flux.just(hotelInfo));
        given(supplierHotelMappingRepository.findBySupplierAndHotelCode(supplier, "A-10023"))
                .willReturn(Optional.of(existingMapping));
        given(supplierRoomTypeMappingRepository.findBySupplierAndHotelCodeAndRoomTypeCode(supplier, "A-10023", "DLX-TWN"))
                .willReturn(Optional.empty());
        given(roomTypeRepository.save(any())).willReturn(RoomType.create(hotel, "디럭스 트윈", 2));
        given(supplierRoomTypeMappingRepository.save(any()))
                .willReturn(SupplierRoomTypeMapping.create(supplier, "A-10023", "DLX-TWN",
                        RoomType.create(hotel, "디럭스 트윈", 2)));

        // when
        hotelSyncService.sync();

        // then
        then(roomTypeRepository).should().save(any(RoomType.class));
        then(supplierRoomTypeMappingRepository).should().save(any(SupplierRoomTypeMapping.class));
    }

    @Test
    void sync_기존_객실타입코드_재동기화시_기존_매핑_유지() {
        // given
        Supplier supplier = TestEntityFixture.createSupplier("SUPPLIER_A", "Supplier A");
        Hotel hotel = TestEntityFixture.createHotel("리버사이드 호텔");
        RoomType existingRoomType = RoomType.create(hotel, "디럭스 트윈", 2);
        SupplierHotelMapping existingHotelMapping = SupplierHotelMapping.create(supplier, "A-10023", hotel);
        SupplierRoomTypeMapping existingRoomTypeMapping =
                SupplierRoomTypeMapping.create(supplier, "A-10023", "DLX-TWN", existingRoomType);
        SupplierRoomTypeInfo roomTypeInfo = new SupplierRoomTypeInfo("DLX-TWN", "디럭스 트윈", 2);
        SupplierHotelInfo hotelInfo = new SupplierHotelInfo("A-10023", "리버사이드 호텔", List.of(roomTypeInfo));

        given(supplierPort.supplierCode()).willReturn("SUPPLIER_A");
        given(supplierPort.supplierName()).willReturn("Supplier A");
        given(supplierRepository.findByCode("SUPPLIER_A")).willReturn(Optional.of(supplier));
        given(supplierPort.fetchHotelList()).willReturn(Flux.just(hotelInfo));
        given(supplierHotelMappingRepository.findBySupplierAndHotelCode(supplier, "A-10023"))
                .willReturn(Optional.of(existingHotelMapping));
        given(supplierRoomTypeMappingRepository.findBySupplierAndHotelCodeAndRoomTypeCode(supplier, "A-10023", "DLX-TWN"))
                .willReturn(Optional.of(existingRoomTypeMapping));

        // when
        hotelSyncService.sync();

        // then — 기존 매핑이 있으면 RoomType을 새로 생성하지 않음
        then(roomTypeRepository).should(never()).save(any(RoomType.class));
        then(supplierRoomTypeMappingRepository).should(never()).save(any(SupplierRoomTypeMapping.class));
    }
}
