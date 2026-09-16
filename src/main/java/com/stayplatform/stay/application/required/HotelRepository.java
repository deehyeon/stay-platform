package com.stayplatform.stay.application.required;

import com.stayplatform.stay.domain.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
}
