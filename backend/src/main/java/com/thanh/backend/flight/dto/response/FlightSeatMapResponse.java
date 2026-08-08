package com.thanh.backend.flight.dto.response;

import com.thanh.backend.aircraft.dto.response.AircraftResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSeatMapResponse {
    private Long flightId;
    private String flightNo;
    private AircraftResponse aircraft;
    private List<FlightSeatResponse> seats;
}
