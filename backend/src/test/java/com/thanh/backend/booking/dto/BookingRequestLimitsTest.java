package com.thanh.backend.booking.dto;

import com.thanh.backend.booking.dto.request.AddOnRequest;
import com.thanh.backend.booking.dto.request.ContactInfoRequest;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.request.SeatSelectionRequest;
import com.thanh.backend.booking.enums.AddOnType;
import com.thanh.backend.booking.enums.Gender;
import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.booking.enums.TripType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BookingRequestLimitsTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static String repeat(int length) {
        return "x".repeat(length);
    }

    private PassengerRequest passenger() {
        return PassengerRequest.builder().fullName("Test Passenger").dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE).passengerType(PassengerType.ADULT).build();
    }

    private SeatSelectionRequest selection(List<AddOnRequest> addOns) {
        return SeatSelectionRequest.builder().flightSeatId(1L).flightId(1L).passengerIndex(0).addOns(addOns).build();
    }

    private CreateBookingRequest booking(List<Long> flights, List<PassengerRequest> passengers, List<SeatSelectionRequest> seats) {
        return CreateBookingRequest.builder().tripType(TripType.ONE_WAY).flightIds(flights)
                .contactInfo(ContactInfoRequest.builder().fullName("Contact").email("contact@example.invalid").phone("0900000000").build())
                .passengers(passengers).seatSelections(seats).build();
    }

    private String violations(Object request) {
        return validator.validate(request).stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted().collect(Collectors.joining("; "));
    }

    @Test
    void realisticBookingPassesValidation() {
        var request = booking(List.of(1L, 2L), Collections.nCopies(18, passenger()),
                Collections.nCopies(36, selection(List.of(new AddOnRequest(AddOnType.BAGGAGE, 2)))));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void addOnQuantityIsBounded() {
        assertThat(violations(new AddOnRequest(AddOnType.BAGGAGE, 11))).contains("quantity");
        assertThat(violations(new AddOnRequest(AddOnType.BAGGAGE, Integer.MAX_VALUE))).contains("quantity");
        assertThat(validator.validate(new AddOnRequest(AddOnType.BAGGAGE, 10))).isEmpty();
        assertThat(validator.validate(new AddOnRequest(AddOnType.MEAL, null))).isEmpty();
    }

    @Test
    void listsAreBounded() {
        var flights = booking(List.of(1L, 2L, 3L), List.of(passenger()), List.of(selection(null)));
        assertThat(violations(flights)).contains("flightIds");
        var passengers = booking(List.of(1L), Collections.nCopies(21, passenger()), List.of(selection(null)));
        assertThat(violations(passengers)).contains("passengers");
        var seats = booking(List.of(1L), List.of(passenger()), Collections.nCopies(41, selection(null)));
        assertThat(violations(seats)).contains("seatSelections");
        var addOns = booking(List.of(1L), List.of(passenger()),
                List.of(selection(Collections.nCopies(11, new AddOnRequest(AddOnType.MEAL, 1)))));
        assertThat(violations(addOns)).contains("addOns");
        assertThat(violations(HoldSeatsRequest.builder().seatIds(Collections.nCopies(41, 1L)).build())).contains("seatIds");
    }

    @Test
    void textFieldsMatchDatabaseColumnLengths() {
        var contact = ContactInfoRequest.builder().fullName(repeat(256)).email(repeat(250) + "@a.com").phone(repeat(21)).build();
        assertThat(violations(contact)).contains("fullName", "email", "phone");
        var longPassenger = passenger();
        longPassenger.setFullName(repeat(256));
        longPassenger.setNationality(repeat(101));
        longPassenger.setIdentityNumber(repeat(51));
        assertThat(violations(longPassenger)).contains("fullName", "nationality", "identityNumber");
        var maximal = passenger();
        maximal.setFullName(repeat(255));
        maximal.setNationality(repeat(100));
        maximal.setIdentityNumber(repeat(50));
        assertThat(validator.validate(maximal)).isEmpty();
    }
}
