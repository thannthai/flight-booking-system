package com.thanh.backend.aircraft.entity;

import com.thanh.backend.airline.entity.Airline;
import com.thanh.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "aircrafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aircraft extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_code_id", nullable = false)
    private Airline airline;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "business_seats", nullable = false)
    @Builder.Default
    private Integer businessSeats = 0;

    @Column(name = "economy_seats", nullable = false)
    @Builder.Default
    private Integer economySeats = 0;

    @Column(name = "registration_no", unique = true, nullable = false, length = 20)
    private String registrationNo;
}
