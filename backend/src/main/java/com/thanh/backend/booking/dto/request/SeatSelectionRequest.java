package com.thanh.backend.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatSelectionRequest {

    @NotNull(message = "Flight seat ID is required")
    private Long flightSeatId;

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotNull(message = "Passenger index is required")
    private Integer passengerIndex;

    @Valid
    @Size(max = 10, message = "At most 10 add-ons per seat")
    private List<AddOnRequest> addOns;
}
