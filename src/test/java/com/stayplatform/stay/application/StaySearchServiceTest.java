package com.stayplatform.stay.application;

import com.stayplatform.fixture.TestEntityFixture;
import com.stayplatform.stay.adapter.persistence.SupplierHotelMappingRepository;
import com.stayplatform.stay.adapter.persistence.SupplierRepository;
import com.stayplatform.stay.adapter.persistence.SupplierRoomTypeMappingRepository;
import com.stayplatform.stay.adapter.supplier.SupplierPort;
import com.stayplatform.stay.adapter.supplier.dto.SupplierAvailability;
import com.stayplatform.stay.application.dto.StaySearchRes;
import com.stayplatform.stay.domain.Hotel;
import com.stayplatform.stay.domain.RoomType;
import com.stayplatform.stay.domain.SearchCondition;
import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierHotelMapping;
import com.stayplatform.stay.domain.SupplierRoomTypeMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StaySearchServiceTest {

    @Mock
    private SupplierPort supplierAPort;

    @Mock
    private SupplierPort supplierBPort;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierHotelMappingRepository supplierHotelMappingRepository;

    @Mock
    private SupplierRoomTypeMappingRepository supplierRoomTypeMappingRepository;

    private StaySearchService staySearchService;

    private SearchCondition condition;
    private Supplier supplierA;
    private Hotel hotel;
    private RoomType roomType;
    private SupplierHotelMapping hotelMapping;
    private SupplierRoomTypeMapping roomTypeMapping;
    private SupplierAvailability availabilityA;

    @BeforeEach
    void setUp() {
        staySearchService = new StaySearchService(
                List.of(supplierAPort, supplierBPort),
                supplierRepository,
                supplierHotelMappingRepository,
                supplierRoomTypeMappingRepository
        );

        condition = SearchCondition.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 4),
                2, 0
        );

        supplierA = TestEntityFixture.createSupplier("SUPPLIER_A", "Supplier A");
        hotel = TestEntityFixture.createHotel("리버사이드 호텔");
        roomType = TestEntityFixture.createRoomType(hotel, "디럭스 트윈", 2);
        hotelMapping = TestEntityFixture.createHotelMapping(supplierA, "A-10023", hotel);
        roomTypeMapping = TestEntityFixture.createRoomTypeMapping(supplierA, "A-10023", "DLX-TWN", roomType);
        availabilityA = new SupplierAvailability("A-10023", "DLX-TWN", 300000L, "KRW", false, 3);
    }

    @Test
    void search_A_B_모두_정상_응답시_통합_결과_반환() {
        // given
        Supplier supplierB = TestEntityFixture.createSupplier("SUPPLIER_B", "Supplier B");
        Hotel hotelB = TestEntityFixture.createHotel("남산 가든 스테이");
        RoomType roomTypeB = TestEntityFixture.createRoomType(hotelB, "스탠다드 더블", 2);
        SupplierHotelMapping hotelMappingB = TestEntityFixture.createHotelMapping(supplierB, "B77120", hotelB);
        SupplierRoomTypeMapping roomTypeMappingB = TestEntityFixture.createRoomTypeMapping(supplierB, "B77120", "R001", roomTypeB);
        SupplierAvailability availabilityB = new SupplierAvailability("B77120", "R001", 250000L, "KRW", true, 5);

        setupSupplierA(availabilityA, hotelMapping, roomTypeMapping);
        setupSupplierB(supplierB, hotelB, availabilityB, hotelMappingB, roomTypeMappingB);

        // when
        StaySearchRes result = staySearchService.search(condition);

        // then
        assertThat(result.results()).hasSize(2);
        assertThat(result.failedSuppliers()).isEmpty();
    }

    @Test
    void search_A_실패시_B_결과만_반환하고_failedSuppliers_포함() {
        // given
        Supplier supplierB = TestEntityFixture.createSupplier("SUPPLIER_B", "Supplier B");
        Hotel hotelB = TestEntityFixture.createHotel("남산 가든 스테이");
        RoomType roomTypeB = TestEntityFixture.createRoomType(hotelB, "스탠다드 더블", 2);
        SupplierHotelMapping hotelMappingB = TestEntityFixture.createHotelMapping(supplierB, "B77120", hotelB);
        SupplierRoomTypeMapping roomTypeMappingB = TestEntityFixture.createRoomTypeMapping(supplierB, "B77120", "R001", roomTypeB);
        SupplierAvailability availabilityB = new SupplierAvailability("B77120", "R001", 250000L, "KRW", true, 5);

        given(supplierAPort.supplierCode()).willReturn("SUPPLIER_A");
        given(supplierRepository.findByCode("SUPPLIER_A")).willReturn(Optional.of(supplierA));
        given(supplierHotelMappingRepository.findAllBySupplier(supplierA)).willReturn(List.of(hotelMapping));
        given(supplierAPort.fetchAvailability(any(), any()))
                .willReturn(Flux.error(new RuntimeException("Supplier A 타임아웃")));

        setupSupplierB(supplierB, hotelB, availabilityB, hotelMappingB, roomTypeMappingB);

        // when
        StaySearchRes result = staySearchService.search(condition);

        // then
        assertThat(result.results()).hasSize(1);
        assertThat(result.failedSuppliers()).containsExactly("SUPPLIER_A");
        assertThat(result.results().get(0).supplier()).isEqualTo("SUPPLIER_B");
    }

    @Test
    void search_A_B_모두_실패시_빈_결과와_failedSuppliers_반환() {
        // given
        Supplier supplierB = TestEntityFixture.createSupplier("SUPPLIER_B", "Supplier B");

        given(supplierAPort.supplierCode()).willReturn("SUPPLIER_A");
        given(supplierRepository.findByCode("SUPPLIER_A")).willReturn(Optional.of(supplierA));
        given(supplierHotelMappingRepository.findAllBySupplier(supplierA)).willReturn(List.of(hotelMapping));
        given(supplierAPort.fetchAvailability(any(), any()))
                .willReturn(Flux.error(new RuntimeException("Supplier A 오류")));

        given(supplierBPort.supplierCode()).willReturn("SUPPLIER_B");
        given(supplierRepository.findByCode("SUPPLIER_B")).willReturn(Optional.of(supplierB));
        given(supplierHotelMappingRepository.findAllBySupplier(supplierB)).willReturn(List.of());
        given(supplierBPort.fetchAvailability(any(), any()))
                .willReturn(Flux.error(new RuntimeException("Supplier B 오류")));

        // when
        StaySearchRes result = staySearchService.search(condition);

        // then
        assertThat(result.results()).isEmpty();
        assertThat(result.failedSuppliers()).containsExactlyInAnyOrder("SUPPLIER_A", "SUPPLIER_B");
    }

    private void setupSupplierA(SupplierAvailability availability,
                                 SupplierHotelMapping hotelMapping,
                                 SupplierRoomTypeMapping roomTypeMapping) {
        given(supplierAPort.supplierCode()).willReturn("SUPPLIER_A");
        given(supplierRepository.findByCode("SUPPLIER_A")).willReturn(Optional.of(supplierA));
        given(supplierHotelMappingRepository.findAllBySupplier(supplierA)).willReturn(List.of(hotelMapping));
        given(supplierAPort.fetchAvailability(any(), any())).willReturn(Flux.just(availability));
        given(supplierHotelMappingRepository.findBySupplierAndHotelCode(supplierA, availability.supplierHotelCode()))
                .willReturn(Optional.of(hotelMapping));
        given(supplierRoomTypeMappingRepository.findBySupplierAndHotelCodeAndRoomTypeCode(
                supplierA, availability.supplierHotelCode(), availability.supplierRoomTypeCode()))
                .willReturn(Optional.of(roomTypeMapping));
    }

    private void setupSupplierB(Supplier supplierB, Hotel hotelB,
                                 SupplierAvailability availability,
                                 SupplierHotelMapping hotelMappingB,
                                 SupplierRoomTypeMapping roomTypeMappingB) {
        given(supplierBPort.supplierCode()).willReturn("SUPPLIER_B");
        given(supplierRepository.findByCode("SUPPLIER_B")).willReturn(Optional.of(supplierB));
        given(supplierHotelMappingRepository.findAllBySupplier(supplierB)).willReturn(List.of(hotelMappingB));
        given(supplierBPort.fetchAvailability(any(), any())).willReturn(Flux.just(availability));
        given(supplierHotelMappingRepository.findBySupplierAndHotelCode(supplierB, availability.supplierHotelCode()))
                .willReturn(Optional.of(hotelMappingB));
        given(supplierRoomTypeMappingRepository.findBySupplierAndHotelCodeAndRoomTypeCode(
                supplierB, availability.supplierHotelCode(), availability.supplierRoomTypeCode()))
                .willReturn(Optional.of(roomTypeMappingB));
    }
}
