package com.stayplatform.stay.domain;

import com.stayplatform.global.domain.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomType extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int maxOccupancy;

    private RoomType(Hotel hotel, String name, int maxOccupancy) {
        this.hotel = hotel;
        this.name = name;
        this.maxOccupancy = maxOccupancy;
    }

    public static RoomType create(Hotel hotel, String name, int maxOccupancy) {
        return new RoomType(hotel, name, maxOccupancy);
    }
}
