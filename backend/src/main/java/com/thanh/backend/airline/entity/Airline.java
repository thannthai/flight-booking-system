package com.thanh.backend.airline.entity;

import com.thanh.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "airlines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Airline extends BaseEntity {

    @Column(unique = true, nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String logoUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean freeBaggage = false;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal extraBaggageFee = BigDecimal.ZERO;
}
