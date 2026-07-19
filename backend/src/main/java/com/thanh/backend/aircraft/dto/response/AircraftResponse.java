package com.thanh.backend.aircraft.dto.response;

import com.thanh.backend.airline.dto.response.AirlineResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AircraftResponse {
    private Long id;
    private AirlineResponse airline;
    private String model;
    private Integer totalSeats;
    private Integer businessSeats;
    private Integer economySeats;
    private String registrationNo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
