package com.stayplatform.stay.domain;

import com.stayplatform.global.domain.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "suppliers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends AbstractEntity {

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private Supplier(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static Supplier create(String code, String name) {
        return new Supplier(code, name);
    }
}
