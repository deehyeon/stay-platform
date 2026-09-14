package com.stayplatform.stay.adapter.persistence;

import com.stayplatform.stay.domain.Hotel;
import com.stayplatform.stay.domain.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    List<RoomType> findAllByHotel(Hotel hotel);
}
