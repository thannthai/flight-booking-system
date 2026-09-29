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
    INVALID_CONTACT_INFO(HttpStatus.BAD_REQUEST, "Contact info is invalid"),
    INVALID_INFANT_GUARDIAN(HttpStatus.BAD_REQUEST, "Infant guardian is invalid. Each infant must have a valid adult guardian with identity document"),
    INVALID_SEAT_SELECTION(HttpStatus.BAD_REQUEST, "Seat selection is invalid for passenger type or flight"),
    INVALID_PASSENGER_DOCUMENT(HttpStatus.BAD_REQUEST, "Passenger identity document is invalid or missing"),
    INVALID_PASSENGER_AGE(HttpStatus.BAD_REQUEST, "Passenger age at departure time does not match passenger type"),
    INVALID_PASSENGER_INDEX(HttpStatus.BAD_REQUEST, "Passenger index is out of bounds"),
    INVALID_TRIP_TYPE(HttpStatus.BAD_REQUEST, "Trip type does not match flight selection count"),
    PASSENGER_NOT_FOUND(HttpStatus.NOT_FOUND, "Passenger not found"),
    IDENTITY_NUMBER_EXISTED(HttpStatus.CONFLICT, "Identity number already exists"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking not found"),
    UNAUTHORIZED_BOOKING_ACCESS(HttpStatus.FORBIDDEN, "You do not have permission to access this booking"),
    PASSENGER_ALREADY_BOOKED_ON_FLIGHT(HttpStatus.CONFLICT, "Passenger already has an active booking on this flight"),
    FLIGHT_NOT_FOUND(HttpStatus.NOT_FOUND, "Flight not found"),
    FLIGHT_NUMBER_EXISTED(HttpStatus.CONFLICT, "Flight number already exists"),
    FLIGHT_NOT_BOOKABLE(HttpStatus.BAD_REQUEST, "Flight is not available for booking"),
    INVALID_FLIGHT_AIRPORTS(HttpStatus.BAD_REQUEST, "Origin airport and destination airport must be different"),
    INVALID_FLIGHT_TIME(HttpStatus.BAD_REQUEST, "Arrival time must be after departure time"),
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
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Database constraint violation"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
