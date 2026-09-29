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
    private OffsetDateTime expiresAt;
    private long ttlSeconds;
}
