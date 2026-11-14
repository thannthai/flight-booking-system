package com.thanh.backend.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.enums.DocumentType;
import com.thanh.backend.booking.enums.Gender;
import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The passenger directory holds only entries a customer saved on purpose. Passengers created by a booking
 * belong to that booking's tickets, so repeat bookings do not fill the directory and editing a directory
 * entry can never rewrite who an issued ticket belongs to.
 */
class PassengerDirectoryIT extends AbstractIntegrationTest {
    @Autowired BookingService bookings;
    @Autowired PassengerService passengers;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    long airline, aircraft;
    int serial;
    User alice, bob;

    @BeforeEach
    void fixture() {
        alice = users.findById(insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('alice@example.invalid','dummy','Alice','0900000500','ROLE_USER')")).orElseThrow();
        bob = users.findById(insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('bob@example.invalid','dummy','Bob','0900000501','ROLE_USER')")).orElseThrow();
        jdbcTemplate.update("INSERT INTO airports(iata_code,name,city,country) VALUES ('DRA','Origin','Origin','Vietnam'),('DRB','Destination','Destination','Vietnam')");
        airline = insert("INSERT INTO airlines(code,name) VALUES ('DR','Directory')");
        aircraft = insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Dir',6,6,'DIR-001')", airline);
    }

    long insert(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    /** Books one seat on a new flight for the same real person and returns the booking id. */
    long bookSamePerson(User owner) throws Exception {
        long flight = insert("INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,departure_time,arrival_time,base_price,source) VALUES (?,?,?,(SELECT id FROM airports WHERE iata_code='DRA'),(SELECT id FROM airports WHERE iata_code='DRB'),now()+interval '2 days',now()+interval '2 days 2 hours',100000,'MANUAL')", "DR" + (++serial), airline, aircraft);
        long seat = insert("INSERT INTO flight_seats(flight_id,seat_no,cabin_class,status,price) VALUES (?,'1A','ECONOMY','AVAILABLE',100000)", flight);
        bookings.holdSeats(owner, HoldSeatsRequest.builder().seatIds(List.of(seat)).build());
        String request = """
                {"tripType":"ONE_WAY","flightIds":[%d],"contactInfo":{"fullName":"Alice","email":"alice@example.invalid","phone":"0900000500"},"passengers":[{"fullName":"Same Person","dateOfBirth":"1990-01-01","gender":"MALE","passengerType":"ADULT","identityType":"PASSPORT","identityNumber":"SAME123"}],"seatSelections":[{"flightSeatId":%d,"flightId":%d,"passengerIndex":0}]}
                """.formatted(flight, seat, flight);
        return bookings.createBooking(owner, json.readValue(request, CreateBookingRequest.class)).getId();
    }

    PassengerRequest request(String name, String document) {
        return PassengerRequest.builder().fullName(name).dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
                .passengerType(PassengerType.ADULT).nationality("Vietnam")
                .identityType(document == null ? null : DocumentType.PASSPORT).identityNumber(document).build();
    }

    long ticketPassengerId(long bookingId) {
        return jdbcTemplate.queryForObject("SELECT passenger_id FROM tickets WHERE booking_id = ?", Long.class, bookingId);
    }

    String passengerName(long passengerId) {
        return jdbcTemplate.queryForObject("SELECT full_name FROM passengers WHERE id = ?", String.class, passengerId);
    }

    @Test
    void repeatBookingsDoNotFillTheDirectory() throws Exception {
        long first = bookSamePerson(alice);
        long second = bookSamePerson(alice);

        assertThat(passengers.getMyPassengers(alice)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM passengers WHERE user_id IS NOT NULL", Integer.class)).isZero();
        // Tickets still point at real passenger rows.
        assertThat(passengerName(ticketPassengerId(first))).isEqualTo("Same Person");
        assertThat(passengerName(ticketPassengerId(second))).isEqualTo("Same Person");

        passengers.createPassenger(alice, request("Same Person", "SAME123"));
        assertThat(passengers.getMyPassengers(alice)).hasSize(1);
    }

    @Test
    void editingADirectoryEntryNeverChangesAnIssuedTicketHolder() throws Exception {
        long booking = bookSamePerson(alice);
        var saved = passengers.createPassenger(alice, request("Same Person", "SAME123"));

        passengers.updatePassenger(alice, saved.getId(), request("Renamed Person", "SAME123"));

        assertThat(passengerName(ticketPassengerId(booking))).isEqualTo("Same Person");
        assertThat(passengers.getMyPassengers(alice)).extracting("fullName").containsExactly("Renamed Person");
    }

    @Test
    void legacyRowSharedWithTicketsIsCopiedOnEditSoTheTicketKeepsItsHolder() throws Exception {
        long booking = bookSamePerson(alice);
        long legacy = ticketPassengerId(booking);
        // Data written before this rule: the ticket's passenger row also sits in the user's directory.
        jdbcTemplate.update("UPDATE passengers SET user_id = ? WHERE id = ?", alice.getId(), legacy);
        assertThat(passengers.getMyPassengers(alice)).hasSize(1);

        // The edit omits the document: the new entry keeps the one it was copied from.
        var edited = passengers.updatePassenger(alice, legacy, request("Corrected Name", null));

        assertThat(passengerName(legacy)).isEqualTo("Same Person");
        assertThat(ticketPassengerId(booking)).isEqualTo(legacy);
        assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM passengers WHERE id = ?", Long.class, legacy)).isNull();
        assertThat(edited.getId()).isNotEqualTo(legacy);
        var directory = passengers.getMyPassengers(alice);
        assertThat(directory).hasSize(1);
        assertThat(directory.getFirst().getFullName()).isEqualTo("Corrected Name");
        assertThat(directory.getFirst().getIdentityNumber()).isEqualTo("SAME123");
    }

    @Test
    void passengersWithoutAnOwnerOrOfAnotherUserAreForbiddenNotAServerError() throws Exception {
        long booking = bookSamePerson(alice);
        long ownerless = ticketPassengerId(booking);
        assertThatThrownBy(() -> passengers.updatePassenger(alice, ownerless, request("Anyone", null)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        var entry = passengers.createPassenger(alice, request("Alice Entry", "ALICE1"));
        assertThatThrownBy(() -> passengers.updatePassenger(bob, entry.getId(), request("Hijacked", null)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(passengerName(entry.getId())).isEqualTo("Alice Entry");
    }
}
