package com.stayplatform.stay.application.required;

import com.stayplatform.stay.domain.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
}
