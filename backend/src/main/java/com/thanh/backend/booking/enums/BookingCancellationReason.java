package com.thanh.backend.booking.enums;

/** Public cancellation classification; persisted booking status remains unchanged. */
public enum BookingCancellationReason {
    PAYMENT_TIMEOUT,
    USER_CANCELLED,
    OTHER
}
