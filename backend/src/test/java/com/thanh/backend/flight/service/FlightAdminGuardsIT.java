package com.thanh.backend.flight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.service.BookingService;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.dto.request.FlightRequest;
import com.thanh.backend.flight.dto.request.UpdateFlightStatusRequest;
import com.thanh.backend.flight.enums.FlightSource;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.payment.service.PayOSGatewayService;
import com.thanh.backend.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Admin edits must not change or remove what customers already bought, and cancelled flights cannot be paid. */
class FlightAdminGuardsIT extends AbstractIntegrationTest {
    @Autowired FlightService flights;
    @Autowired BookingService bookings;
    @Autowired PaymentService payments;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @MockitoBean PayOSGatewayService gateway;

    long airline, smallAircraft, largeAircraft, airportA, airportB, airportC;
    int serial;
    User customer;

    @BeforeEach
    void fixture() {
        customer = users.findById(insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('guard@example.invalid','dummy','Guard','0900000700','ROLE_USER')")).orElseThrow();
        airportA = insert("INSERT INTO airports(iata_code,name,city,country) VALUES ('GDA','A','A','Vietnam')");
        airportB = insert("INSERT INTO airports(iata_code,name,city,country) VALUES ('GDB','B','B','Vietnam')");
        airportC = insert("INSERT INTO airports(iata_code,name,city,country) VALUES ('GDC','C','C','Vietnam')");
        airline = insert("INSERT INTO airlines(code,name) VALUES ('GD','Guard Air')");
        smallAircraft = insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Small',6,6,'GD-SMALL')", airline);
        largeAircraft = insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Large',12,12,'GD-LARGE')", airline);
    }

    long insert(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    FlightRequest request(String flightNo, long aircraft, long origin, long dest, OffsetDateTime departure) {
        return FlightRequest.builder().flightNo(flightNo).airlineId(airline).aircraftId(aircraft)
                .originAirportId(origin).destAirportId(dest).departureTime(departure).arrivalTime(departure.plusHours(2))
                .status(FlightStatus.SCHEDULED).basePrice(new BigDecimal("100000")).source(FlightSource.MANUAL).build();
    }

    long createFlight() {
        return flights.createFlight(request("GD" + (++serial), smallAircraft, airportA, airportB, OffsetDateTime.now().plusDays(3))).getId();
    }

    long seatOf(long flightId) {
        return jdbcTemplate.queryForObject("SELECT id FROM flight_seats WHERE flight_id = ? ORDER BY id LIMIT 1", Long.class, flightId);
    }

    int seatCount(long flightId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM flight_seats WHERE flight_id = ?", Integer.class, flightId);
    }

    void holdOneSeat(long flightId) {
        bookings.holdSeats(customer, HoldSeatsRequest.builder().seatIds(List.of(seatOf(flightId))).build());
    }

    long bookOneSeat(long flightId) throws Exception {
        long seat = seatOf(flightId);
        bookings.holdSeats(customer, HoldSeatsRequest.builder().seatIds(List.of(seat)).build());
        String body = """
                {"tripType":"ONE_WAY","flightIds":[%d],"contactInfo":{"fullName":"Guard","email":"guard@example.invalid","phone":"0900000700"},"passengers":[{"fullName":"Guard Passenger","dateOfBirth":"1990-01-01","gender":"MALE","passengerType":"ADULT","identityType":"PASSPORT","identityNumber":"GUARD%d"}],"seatSelections":[{"flightSeatId":%d,"flightId":%d,"passengerIndex":0}]}
                """.formatted(flightId, ++serial, seat, flightId);
        return bookings.createBooking(customer, json.readValue(body, CreateBookingRequest.class)).getId();
    }

    void expireHolds() {
        jdbcTemplate.update("UPDATE flight_seats SET held_until = now() - interval '1 minute' WHERE status = 'HELD'");
    }

    static void assertCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void aFlightWithBookingsCannotBeDeleted() throws Exception {
        long flight = createFlight();
        bookOneSeat(flight);

        assertThatThrownBy(() -> flights.deleteFlight(flight)).satisfies(t -> assertCode(t, ErrorCode.FLIGHT_HAS_BOOKINGS));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flights WHERE id = ?", Integer.class, flight)).isEqualTo(1);
        assertThat(seatCount(flight)).isEqualTo(6);
    }

    @Test
    void aFlightWithLiveHoldsCannotBeDeletedButCanOnceTheyExpire() {
        long flight = createFlight();
        holdOneSeat(flight);

        assertThatThrownBy(() -> flights.deleteFlight(flight)).satisfies(t -> assertCode(t, ErrorCode.FLIGHT_SEATS_IN_USE));
        assertThat(seatCount(flight)).isEqualTo(6);

        expireHolds();
        flights.deleteFlight(flight);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flights WHERE id = ?", Integer.class, flight)).isZero();
        assertThat(seatCount(flight)).isZero();
    }

    @Test
    void aFlightWithBookingsKeepsItsAircraftAirlineAndRouteButMayBeRescheduled() throws Exception {
        long flight = createFlight();
        bookOneSeat(flight);
        OffsetDateTime departure = OffsetDateTime.now().plusDays(3);

        assertThatThrownBy(() -> flights.updateFlight(flight, request("GD1", largeAircraft, airportA, airportB, departure)))
                .satisfies(t -> assertCode(t, ErrorCode.FLIGHT_HAS_BOOKINGS));
        assertThatThrownBy(() -> flights.updateFlight(flight, request("GD1", smallAircraft, airportA, airportC, departure)))
                .satisfies(t -> assertCode(t, ErrorCode.FLIGHT_HAS_BOOKINGS));
        assertThat(seatCount(flight)).isEqualTo(6);

        OffsetDateTime later = departure.plusHours(5);
        flights.updateFlight(flight, request("GD1", smallAircraft, airportA, airportB, later));
        assertThat(flights.getFlightById(flight).getDepartureTime().toInstant()).isEqualTo(later.toInstant());
    }

    @Test
    void changingTheAircraftOfAnUnbookedFlightRebuildsItsSeatMap() {
        long flight = createFlight();
        assertThat(seatCount(flight)).isEqualTo(6);

        holdOneSeat(flight);
        OffsetDateTime departure = OffsetDateTime.now().plusDays(3);
        assertThatThrownBy(() -> flights.updateFlight(flight, request("GD1", largeAircraft, airportA, airportB, departure)))
                .satisfies(t -> assertCode(t, ErrorCode.FLIGHT_SEATS_IN_USE));
        assertThat(seatCount(flight)).isEqualTo(6);

        expireHolds();
        flights.updateFlight(flight, request("GD1", largeAircraft, airportA, airportB, departure));
        assertThat(seatCount(flight)).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT seat_no) FROM flight_seats WHERE flight_id = ?", Integer.class, flight)).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flight_seats WHERE flight_id = ? AND status <> 'AVAILABLE'", Integer.class, flight)).isZero();
    }

    @Test
    void aBookingOnACancelledFlightCannotStartPayment() throws Exception {
        long flight = createFlight();
        long booking = bookOneSeat(flight);
        flights.updateFlightStatus(flight, UpdateFlightStatusRequest.builder().status(FlightStatus.CANCELLED).build());

        assertThatThrownBy(() -> payments.createPaymentLink(booking, customer.getId()))
                .satisfies(t -> assertCode(t, ErrorCode.FLIGHT_NOT_BOOKABLE));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payments WHERE booking_id = ?", Integer.class, booking)).isZero();
    }
}
