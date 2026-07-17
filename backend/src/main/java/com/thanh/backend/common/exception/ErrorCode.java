package com.thanh.backend.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND(404, "User not found"),
    EMAIL_EXISTED(409, "Email already exists"),
    PHONE_EXISTED(409, "Phone number already exists"),
    INVALID_CREDENTIALS(401, "Invalid email or password"),
    TOKEN_EXPIRED(401, "Token expired"),
    TOKEN_INVALID(401, "Token invalid"),
    TOKEN_BLACKLISTED(401, "Token has been revoked"),
    REFRESH_TOKEN_INVALID(401, "Refresh token invalid or expired"),
    SEAT_NOT_AVAILABLE(409, "Seat is not available"),
    SEAT_HELD(409, "Seat is currently held by another user"),
    BOOKING_NOT_FOUND(404, "Booking not found"),
    FLIGHT_NOT_FOUND(404, "Flight not found"),
    PAYMENT_ALREADY_PROCESSED(409, "Payment already processed"),
    VOUCHER_EXPIRED(400, "Voucher has expired"),
    VOUCHER_EXCEEDED(409, "Voucher usage limit exceeded"),
    FORBIDDEN(403, "Access denied"),
    AIRLINE_NOT_FOUND(404, "Airline not found"),
    AIRLINE_CODE_EXISTED(409, "Airline code already exists"),
    AIRLINE_HAS_ACTIVE_FLIGHTS(400, "Airline has active flights"),
    INTERNAL_ERROR(500, "Internal server error");

    private final int httpStatus;
    private final String message;

    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
