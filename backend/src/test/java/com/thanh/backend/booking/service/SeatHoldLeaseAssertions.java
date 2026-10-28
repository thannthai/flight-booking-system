package com.thanh.backend.booking.service;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SeatHoldLeaseAssertions {
    private SeatHoldLeaseAssertions() { }

    static void assertLeaseOwnedBy(String actual, Long userId) {
        assertNotNull(actual, "Redis lease must exist");
        String[] parts = actual.split(":", -1);
        assertEquals(2, parts.length, "Redis lease must contain owner and unique identity");
        assertEquals(userId.toString(), parts[0]);
        assertEquals(UUID.fromString(parts[1]).toString(), parts[1], "Lease token must be a canonical UUID");
    }
}
