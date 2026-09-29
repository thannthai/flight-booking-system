package com.thanh.backend.booking.entity;

import com.thanh.backend.booking.enums.LegDirection;
import com.thanh.backend.common.entity.BaseEntity;
import com.thanh.backend.flight.entity.Flight;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "booking_legs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingLeg extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "leg_order", nullable = false)
    private Integer legOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LegDirection direction;
}
