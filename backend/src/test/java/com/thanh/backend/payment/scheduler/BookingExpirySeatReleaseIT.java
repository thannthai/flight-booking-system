package com.thanh.backend.payment.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Expiry cleanup against real PostgreSQL: seat release is conditional on the expiring booking's ownership. */
class BookingExpirySeatReleaseIT extends AbstractIntegrationTest {
    @Autowired BookingPaymentCleanupScheduler scheduler;
    @Autowired BookingService bookings;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    long flightId;
    long seatId;
    long expiringBookingId;
    User user;

    @BeforeEach
    void fixture() throws Exception {
        long uid = insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('expiry@example.invalid','dummy','Expiry','0900000200','ROLE_USER')");
        user = users.findById(uid).orElseThrow();
        jdbcTemplate.update("INSERT INTO airports(iata_code,name,city,country) VALUES ('EXA','Origin','Origin','Vietnam'),('EXB','Destination','Destination','Vietnam')");
        long airline = insert("INSERT INTO airlines(code,name) VALUES ('EX','Expiry')");
        long aircraft = insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Expiry',6,6,'EXPIRY-001')", airline);
        flightId = insert("INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,departure_time,arrival_time,base_price,source) VALUES ('EX001',?,?,(SELECT id FROM airports WHERE iata_code='EXA'),(SELECT id FROM airports WHERE iata_code='EXB'),now()+interval '2 days',now()+interval '2 days 2 hours',100000,'MANUAL')", airline, aircraft);
        seatId = insert("INSERT INTO flight_seats(flight_id,seat_no,cabin_class,status,price) VALUES (?,'1A','ECONOMY','AVAILABLE',100000)", flightId);
        bookings.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seatId)).build());
        String request = """
                {"tripType":"ONE_WAY","flightIds":[%d],"contactInfo":{"fullName":"Expiry","email":"expiry@example.invalid","phone":"0900000200"},"passengers":[{"fullName":"Expiry Passenger","dateOfBirth":"1990-01-01","gender":"MALE","passengerType":"ADULT","identityType":"PASSPORT","identityNumber":"EXPIRY1"}],"seatSelections":[{"flightSeatId":%d,"flightId":%d,"passengerIndex":0}]}
                """.formatted(flightId, seatId, flightId);
        expiringBookingId = bookings.createBooking(user, json.readValue(request, CreateBookingRequest.class)).getId();
        // Past the deadline plus the scheduler's 90 second grace period.
        jdbcTemplate.update("UPDATE booking SET payment_deadline = now() - interval '10 minutes' WHERE id = ?", expiringBookingId);
    }

    long insert(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    String seatStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM flight_seats WHERE id = ?", String.class, seatId);
    }

    @Test
    void expiredBookingReleasesItsSeatAndCancelsItsTickets() {
        scheduler.cleanupExpiredBookings();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, expiringBookingId)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tickets WHERE booking_id = ?", String.class, expiringBookingId)).isEqualTo("CANCELLED");
        assertThat(seatStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void expiredBookingDoesNotReleaseSeatOwnedByAnotherActiveBooking() {
        // Inconsistent legacy data: a second live booking also references the same seat.
        long otherBooking = insert("""
                INSERT INTO booking(pnr,user_id,trip_type,base_amount,tax_amount,discount_amount,total_amount,status,payment_deadline)
                SELECT 'OWNER1',user_id,trip_type,base_amount,tax_amount,discount_amount,total_amount,'PENDING',now() + interval '10 minutes'
                FROM booking WHERE id = ?
                """, expiringBookingId);
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_no,booking_id,booking_leg_id,passenger_id,flight_seat_id,passenger_type,price,status)
                SELECT 'EX-OWNER1-1-01',?,booking_leg_id,passenger_id,flight_seat_id,passenger_type,price,'PENDING'
                FROM tickets WHERE booking_id = ?
                """, otherBooking, expiringBookingId);

        scheduler.cleanupExpiredBookings();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, expiringBookingId)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, otherBooking)).isEqualTo("PENDING");
        assertThat(seatStatus()).as("seat of the still-active booking").isEqualTo("BOOKED");
    }
}
