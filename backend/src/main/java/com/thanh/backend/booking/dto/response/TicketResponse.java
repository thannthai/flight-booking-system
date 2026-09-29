package com.thanh.backend.booking.dto.response;

import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.flight.dto.response.FlightResponse;
import com.thanh.backend.flight.dto.response.FlightSeatResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private String ticketNo;
    private PassengerType passengerType;
    private BigDecimal price;
    private TicketStatus status;
    private OffsetDateTime issuedAt;
    private FlightResponse flight;
    private FlightSeatResponse seat;
    private List<AddOnResponse> addOns;
}
