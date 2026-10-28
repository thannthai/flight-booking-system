package com.thanh.backend.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldSeatsResponse {
    private List<Long> heldSeats;
    // Earliest deadline in a batch; reusing a live hold never moves it forward.
    private OffsetDateTime expiresAt;
    // Remaining time, rounded up to seconds (maximum configured hold TTL).
    private long ttlSeconds;
}
