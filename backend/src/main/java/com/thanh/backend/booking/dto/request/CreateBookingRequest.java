package com.thanh.backend.booking.dto.request;

import jakarta.validation.constraints.Size;
import com.thanh.backend.booking.enums.TripType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class CreateBookingRequest {

    @NotNull(message = "Trip type is required")
    private TripType tripType;

    @NotEmpty(message = "Flight IDs cannot be empty")
    @Size(max = 2, message = "A booking has at most 2 flights")
    private List<Long> flightIds;

    @NotNull(message = "Contact info is required")
    @Valid
    private ContactInfoRequest contactInfo;

    @NotEmpty(message = "Passenger list cannot be empty")
    @Size(max = 20, message = "A booking has at most 20 passengers")
    @Valid
    private List<PassengerRequest> passengers;

    @NotEmpty(message = "Seat selections cannot be empty")
    @Size(max = 40, message = "A booking has at most 40 seat selections")
    @Valid
    private List<SeatSelectionRequest> seatSelections;

    private Long voucherId;
}
