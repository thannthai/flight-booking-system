package com.thanh.backend.booking.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
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
public class HoldSeatsRequest {

    @NotEmpty(message = "Seat selection list cannot be empty")
    @Size(max = 40, message = "At most 40 seats can be held at once")
    private List<Long> seatIds;
}
