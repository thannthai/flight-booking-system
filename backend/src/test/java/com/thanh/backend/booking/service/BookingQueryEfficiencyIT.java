package com.thanh.backend.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Booking history must not issue per-booking/per-ticket queries. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BookingQueryEfficiencyIT extends AbstractIntegrationTest {
    @Autowired BookingService bookings;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;

    long flightId;
    int serial;
    User user;

    @BeforeEach
    void fixture() {
        long uid = insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('history@example.invalid','dummy','History','0900000100','ROLE_USER')");
        user = users.findById(uid).orElseThrow();
        jdbcTemplate.update("INSERT INTO airports(iata_code,name,city,country) VALUES ('QEA','Origin','Origin','Vietnam'),('QEB','Destination','Destination','Vietnam')");
        long airline = insert("INSERT INTO airlines(code,name) VALUES ('QE','Query')");
        long aircraft = insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Query',30,30,'QUERY-001')", airline);
        flightId = insert("INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,departure_time,arrival_time,base_price,source) VALUES ('QE001',?,?,(SELECT id FROM airports WHERE iata_code='QEA'),(SELECT id FROM airports WHERE iata_code='QEB'),now()+interval '2 days',now()+interval '2 days 2 hours',100000,'MANUAL')", airline, aircraft);
    }

    long insert(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    void book() throws Exception {
        long seat = insert("INSERT INTO flight_seats(flight_id,seat_no,cabin_class,status,price) VALUES (?,?,'ECONOMY','AVAILABLE',100000)", flightId, "S" + (++serial));
        bookings.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seat)).build());
        String request = """
                {"tripType":"ONE_WAY","flightIds":[%d],"contactInfo":{"fullName":"History","email":"history@example.invalid","phone":"0900000100"},"passengers":[{"fullName":"History Passenger","dateOfBirth":"1990-01-01","gender":"MALE","passengerType":"ADULT","identityType":"PASSPORT","identityNumber":"QUERY%d"}],"seatSelections":[{"flightSeatId":%d,"flightId":%d,"passengerIndex":0,"addOns":[{"serviceType":"MEAL","quantity":1}]}]}
                """.formatted(flightId, serial, seat, flightId);
        bookings.createBooking(user, json.readValue(request, CreateBookingRequest.class));
    }

    long statementsForHistoryPage() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var page = bookings.getMyBookings(user, PageRequest.of(0, 20));
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(booking -> {
            assertThat(booking.getTickets()).hasSize(1);
            assertThat(booking.getTickets().getFirst().getFlight().getOrigin()).isNotNull();
            assertThat(booking.getTickets().getFirst().getAddOns()).hasSize(1);
        });
        return statistics.getPrepareStatementCount();
    }

    @Test
    void historyQueryCountDoesNotGrowWithNumberOfBookings() throws Exception {
        for (int i = 0; i < 2; i++) book();
        long withTwo = statementsForHistoryPage();
        for (int i = 0; i < 6; i++) book();
        long withEight = statementsForHistoryPage();
        assertThat(withEight).as("statements for 2 vs 8 bookings").isEqualTo(withTwo);
    }

    @Test
    void requestedPageSizeIsCappedAtTheConfiguredMaximum() throws Exception {
        var response = http.getForEntity("/api/v1/flights/search?origin=QEA&destination=QEB&size=100000", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        int pageSize = json.readTree(response.getBody()).path("data").path("pageSize").asInt();
        assertThat(pageSize).isEqualTo(100);
    }
}
