package com.stayplatform.stay.adapter.persistence;

import com.stayplatform.fixture.TestEntityFixture;
import com.stayplatform.stay.domain.Hotel;
import com.stayplatform.stay.domain.RoomType;
import com.stayplatform.stay.domain.Supplier;
import com.stayplatform.stay.domain.SupplierHotelMapping;
import com.stayplatform.stay.domain.SupplierRoomTypeMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class SupplierMappingRepositoryTest {

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private SupplierHotelMappingRepository supplierHotelMappingRepository;

    @Autowired
    private SupplierRoomTypeMappingRepository supplierRoomTypeMappingRepository;

    private Supplier supplier;
    private Hotel hotel;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        supplier = supplierRepository.save(TestEntityFixture.createSupplier("SUPPLIER_A", "Supplier A"));
        hotel = hotelRepository.save(TestEntityFixture.createHotel("리버사이드 호텔"));
        roomType = roomTypeRepository.save(TestEntityFixture.createRoomType(hotel, "디럭스 트윈", 2));
    }

    @Test
    void findBySupplierAndHotelCode_저장된_매핑_조회() {
        // given
        supplierHotelMappingRepository.save(TestEntityFixture.createHotelMapping(supplier, "A-10023", hotel));

        // when
        Optional<SupplierHotelMapping> result =
                supplierHotelMappingRepository.findBySupplierAndHotelCode(supplier, "A-10023");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getHotelCode()).isEqualTo("A-10023");
    }

    @Test
    void findAllBySupplier_공급사의_전체_숙소_매핑_목록_조회() {
        // given
        Hotel hotel2 = hotelRepository.save(TestEntityFixture.createHotel("남산 가든 스테이"));
        supplierHotelMappingRepository.save(TestEntityFixture.createHotelMapping(supplier, "A-10023", hotel));
        supplierHotelMappingRepository.save(TestEntityFixture.createHotelMapping(supplier, "A-10024", hotel2));

        // when
        List<SupplierHotelMapping> result = supplierHotelMappingRepository.findAllBySupplier(supplier);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SupplierHotelMapping::getHotelCode)
                .containsExactlyInAnyOrder("A-10023", "A-10024");
    }

    @Test
    void save_동일_공급사코드_중복저장시_예외_발생() {
        // given
        supplierHotelMappingRepository.saveAndFlush(TestEntityFixture.createHotelMapping(supplier, "A-10023", hotel));

        // when & then
        assertThatThrownBy(() -> supplierHotelMappingRepository.saveAndFlush(
                TestEntityFixture.createHotelMapping(supplier, "A-10023", hotel)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findBySupplierAndHotelCodeAndRoomTypeCode_저장된_매핑_조회() {
        // given
        supplierRoomTypeMappingRepository.save(
                TestEntityFixture.createRoomTypeMapping(supplier, "A-10023", "DLX-TWN", roomType));

        // when
        Optional<SupplierRoomTypeMapping> result = supplierRoomTypeMappingRepository
                .findBySupplierAndHotelCodeAndRoomTypeCode(supplier, "A-10023", "DLX-TWN");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getRoomTypeCode()).isEqualTo("DLX-TWN");
    }

    @Test
    void findAllBySupplierAndHotelCode_숙소의_전체_객실타입_매핑_목록_조회() {
        // given
        RoomType roomType2 = roomTypeRepository.save(TestEntityFixture.createRoomType(hotel, "스탠다드 더블", 2));
        supplierRoomTypeMappingRepository.save(
                TestEntityFixture.createRoomTypeMapping(supplier, "A-10023", "DLX-TWN", roomType));
        supplierRoomTypeMappingRepository.save(
                TestEntityFixture.createRoomTypeMapping(supplier, "A-10023", "STD-DBL", roomType2));

        // when
        List<SupplierRoomTypeMapping> result = supplierRoomTypeMappingRepository
                .findAllBySupplierAndHotelCode(supplier, "A-10023");

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SupplierRoomTypeMapping::getRoomTypeCode)
                .containsExactlyInAnyOrder("DLX-TWN", "STD-DBL");
    }

    @Test
    void save_동일_객실타입코드_중복저장시_예외_발생() {
        // given
        supplierRoomTypeMappingRepository.saveAndFlush(
                TestEntityFixture.createRoomTypeMapping(supplier, "A-10023", "DLX-TWN", roomType));

        // when & then
        assertThatThrownBy(() -> supplierRoomTypeMappingRepository.saveAndFlush(
                TestEntityFixture.createRoomTypeMapping(supplier, "A-10023", "DLX-TWN", roomType)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
