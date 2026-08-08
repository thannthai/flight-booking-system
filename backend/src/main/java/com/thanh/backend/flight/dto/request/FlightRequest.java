package com.thanh.backend.flight.dto.request;

import com.thanh.backend.flight.enums.FlightSource;
import com.thanh.backend.flight.enums.FlightStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightRequest {

    @NotBlank(message = "Flight number is required")
    @Size(max = 10, message = "Flight number must not exceed 10 characters")
    private String flightNo;

    @NotNull(message = "Airline ID is required")
    private Long airlineId;

    @NotNull(message = "Aircraft ID is required")
    private Long aircraftId;

    @NotNull(message = "Origin airport ID is required")
    private Long originAirportId;

    @NotNull(message = "Destination airport ID is required")
    private Long destAirportId;

    @NotNull(message = "Departure time is required")
    private OffsetDateTime departureTime;

    @NotNull(message = "Arrival time is required")
    private OffsetDateTime arrivalTime;

    private FlightStatus status;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    private BigDecimal basePrice;

    @NotNull(message = "Flight source is required")
    private FlightSource source;

    public String getFlightNo() {
        return flightNo != null ? flightNo.strip().toUpperCase() : null;
    }
}
