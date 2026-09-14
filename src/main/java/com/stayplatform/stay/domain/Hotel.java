package com.stayplatform.stay.domain;

import com.stayplatform.global.domain.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hotel extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    private Hotel(String name) {
        this.name = name;
    }

    public static Hotel create(String name) {
        return new Hotel(name);
    }

    public void updateName(String name) {
        this.name = name;
    }
}
