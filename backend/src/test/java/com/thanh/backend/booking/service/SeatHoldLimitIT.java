package com.thanh.backend.booking.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One customer cannot lock up inventory: live holds per user are capped (20 by default). */
class SeatHoldLimitIT extends AbstractIntegrationTest {
    @Autowired BookingService bookings;
    @Autowired UserRepository users;

    List<Long> seats = new ArrayList<>();
    User alice, bob;

    @BeforeEach
    void fixture() {
        alice = users.findById(insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('alice@example.invalid','dummy','Alice','0900000600','ROLE_USER')")).orElseThrow();
        bob = users.findById(insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('bob@example.invalid','dummy','Bob','0900000601','ROLE_USER')")).orElseThrow();
        jdbcTemplate.update("INSERT INTO airports(iata_code,name,city,country) VALUES ('HLA','Origin','Origin','Vietnam'),('HLB','Destination','Destination','Vietnam')");
        long airline = insert("INSERT INTO airlines(code,name) VALUES ('HL','Hold Limit')");
        long aircraft = insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'HL',30,30,'HL-001')", airline);
        long flight = insert("INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,departure_time,arrival_time,base_price,source) VALUES ('HL001',?,?,(SELECT id FROM airports WHERE iata_code='HLA'),(SELECT id FROM airports WHERE iata_code='HLB'),now()+interval '2 days',now()+interval '2 days 2 hours',100000,'MANUAL')", airline, aircraft);
        for (int i = 1; i <= 22; i++) {
            seats.add(insert("INSERT INTO flight_seats(flight_id,seat_no,cabin_class,status,price) VALUES (?,?,'ECONOMY','AVAILABLE',100000)", flight, (i + 1) / 2 + "" + "AB".charAt(i % 2)));
        }
    }

    long insert(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    void hold(User user, List<Long> ids) {
        bookings.holdSeats(user, HoldSeatsRequest.builder().seatIds(ids).build());
    }

    String status(long seat) {
        return jdbcTemplate.queryForObject("SELECT status FROM flight_seats WHERE id = ?", String.class, seat);
    }

    @Test
    void holdsBeyondTheLimitAreRejectedAcrossRequestsAndOtherUsersAreUnaffected() {
        hold(alice, seats.subList(0, 10));
        hold(alice, seats.subList(10, 20));

        assertThatThrownBy(() -> hold(alice, List.of(seats.get(20))))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_HOLD_LIMIT_EXCEEDED));
        assertThat(status(seats.get(20))).isEqualTo("AVAILABLE");

        hold(bob, List.of(seats.get(20)));
        assertThat(status(seats.get(20))).isEqualTo("HELD");
    }

    @Test
    void oneRequestAboveTheLimitIsRejectedAsAWhole() {
        assertThatThrownBy(() -> hold(alice, seats.subList(0, 21)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_HOLD_LIMIT_EXCEEDED));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flight_seats WHERE status = 'HELD'", Integer.class)).isZero();
    }

    @Test
    void retryingTheSameHoldAtTheLimitDoesNotCountTwice() {
        hold(alice, seats.subList(0, 20));
        hold(alice, seats.subList(0, 10));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flight_seats WHERE status = 'HELD' AND held_by_user = ?", Integer.class, alice.getId())).isEqualTo(20);
    }

    @Test
    void expiredHoldsNoLongerCountTowardsTheLimit() {
        hold(alice, seats.subList(0, 20));
        jdbcTemplate.update("UPDATE flight_seats SET held_until = now() - interval '1 minute' WHERE held_by_user = ?", alice.getId());

        hold(alice, List.of(seats.get(20)));
        assertThat(status(seats.get(20))).isEqualTo("HELD");
    }
}
