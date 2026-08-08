package com.thanh.backend.flight.dto.response;

import com.thanh.backend.flight.enums.CabinClass;
import com.thanh.backend.flight.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSeatResponse {
    private Long id;
    private String seatNo;
    private CabinClass cabinClass;
    private BigDecimal price;
    private SeatStatus status;
}
