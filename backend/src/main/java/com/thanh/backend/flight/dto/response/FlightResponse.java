package com.thanh.backend.flight.dto.response;

import com.thanh.backend.aircraft.dto.response.AircraftResponse;
import com.thanh.backend.airline.dto.response.AirlineResponse;
import com.thanh.backend.airport.dto.response.AirportResponse;
import com.thanh.backend.flight.enums.FlightSource;
import com.thanh.backend.flight.enums.FlightStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightResponse {
    private Long id;
    private String flightNo;
    private AirlineResponse airline;
    private AircraftResponse aircraft;
    private AirportResponse origin;
    private AirportResponse dest;
    private OffsetDateTime departureTime;
    private OffsetDateTime arrivalTime;
    private FlightStatus status;
    private BigDecimal basePrice;
    private FlightSource source;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
