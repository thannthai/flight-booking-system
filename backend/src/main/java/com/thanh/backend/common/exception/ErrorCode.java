package com.thanh.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    EMAIL_EXISTED(HttpStatus.CONFLICT, "Email already exists"),
    PHONE_EXISTED(HttpStatus.CONFLICT, "Phone number already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token invalid"),
    TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "Token has been revoked"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token invalid or expired"),
    SEAT_NOT_AVAILABLE(HttpStatus.CONFLICT, "Seat is not available"),
    SEAT_HELD(HttpStatus.CONFLICT, "Seat is currently held by another user"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking not found"),
    FLIGHT_NOT_FOUND(HttpStatus.NOT_FOUND, "Flight not found"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "Payment already processed"),
    VOUCHER_EXPIRED(HttpStatus.BAD_REQUEST, "Voucher has expired"),
    VOUCHER_EXCEEDED(HttpStatus.CONFLICT, "Voucher usage limit exceeded"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    AIRLINE_NOT_FOUND(HttpStatus.NOT_FOUND, "Airline not found"),
    AIRLINE_CODE_EXISTED(HttpStatus.CONFLICT, "Airline code already exists"),
    AIRLINE_HAS_ACTIVE_FLIGHTS(HttpStatus.BAD_REQUEST, "Airline has active flights"),
    AIRPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Airport not found"),
    AIRPORT_CODE_EXISTED(HttpStatus.CONFLICT, "Airport code already exists"),
    AIRPORT_HAS_ACTIVE_FLIGHTS(HttpStatus.BAD_REQUEST, "Airport has active flights"),
    AIRCRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "Aircraft not found"),
    AIRCRAFT_REGISTRATION_NO_EXISTED(HttpStatus.CONFLICT, "Aircraft registration number already exists"),
    AIRCRAFT_HAS_ACTIVE_FLIGHTS(HttpStatus.BAD_REQUEST, "Aircraft has active flights"),
    INVALID_AIRCRAFT_SEATS(HttpStatus.BAD_REQUEST, "Business seats and economy seats total must equal total seats"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
