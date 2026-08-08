package com.thanh.backend.flight.dto.request;

import com.thanh.backend.flight.enums.FlightStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFlightStatusRequest {

    @NotNull(message = "Flight status is required")
    private FlightStatus status;

    private OffsetDateTime newDepartureTime;
    private OffsetDateTime newArrivalTime;
}
