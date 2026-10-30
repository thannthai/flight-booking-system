package com.thanh.backend.aircraft.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.aircraft.dto.response.AircraftResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AircraftSearchIT extends AbstractIntegrationTest {

    @Autowired
    private AircraftService aircraftService;

    @BeforeEach
    void seedAircrafts() {
        Long firstAirline = jdbcTemplate.queryForObject(
                "INSERT INTO airlines(code, name) VALUES ('AX', 'Hãng Ánh Dương') RETURNING id", Long.class);
        Long secondAirline = jdbcTemplate.queryForObject(
                "INSERT INTO airlines(code, name) VALUES ('BY', 'Blue Sky') RETURNING id", Long.class);
        insertAircraft(firstAirline, "Airbus A321", "AX-002");
        insertAircraft(firstAirline, "Airbus A320", "AX-001");
        insertAircraft(secondAirline, "Boeing 787", "BY-001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void noFilterReturnsAllAircraftsWithAccuratePagination(String search) {
        // Full first page forces Spring Data to execute the explicit countQuery.
        var page = aircraftService.getAircrafts(search, PageRequest.of(0, 1, Sort.by("id")));

        assertEquals(3, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(1, page.getNumberOfElements());
        assertEquals("AX-002", page.getContent().getFirst().getRegistrationNo());
        assertEquals("AX", page.getContent().getFirst().getAirline().getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"  AIRBUS  ", "ax-00", "hang anh duong", "aX"})
    void filterMatchesModelRegistrationAirlineNameOrCode(String search) {
        var page = aircraftService.getAircrafts(search, PageRequest.of(0, 1, Sort.by("id")));

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals("AX-002", page.getContent().getFirst().getRegistrationNo());
        assertEquals("Hãng Ánh Dương", page.getContent().getFirst().getAirline().getName());

        var nextPage = aircraftService.getAircrafts(search, PageRequest.of(1, 1, Sort.by("id")));
        assertEquals(2, nextPage.getTotalElements());
        assertEquals("AX-001", nextPage.getContent().getFirst().getRegistrationNo());
        assertTrue(nextPage.isLast());
    }

    @Test
    void unmatchedFilterReturnsEmptyPage() {
        var page = aircraftService.getAircrafts("not-an-aircraft", PageRequest.of(0, 1));

        assertTrue(page.isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void controllerDefaultRegistrationSortUsesMappedColumn() {
        var page = aircraftService.getAircrafts(null, PageRequest.of(0, 1, Sort.by("registrationNo")));

        assertEquals(3, page.getTotalElements());
        assertEquals("AX-001", page.getContent().getFirst().getRegistrationNo());
    }

    @Test
    void filteredRegistrationSortPreservesDescendingOrderAndPagination() {
        var page = aircraftService.getAircrafts("AX", PageRequest.of(1, 1, Sort.by("registrationNo").descending()));

        assertEquals(2, page.getTotalElements());
        assertEquals("AX-001", page.getContent().getFirst().getRegistrationNo());
        assertTrue(page.isLast());
    }

    @Test
    void unpagedRegistrationSortReturnsAllMatchingAircrafts() {
        var page = aircraftService.getAircrafts(null, Pageable.unpaged(Sort.by("registrationNo")));

        assertEquals(java.util.List.of("AX-001", "AX-002", "BY-001"),
                page.map(AircraftResponse::getRegistrationNo).getContent());
    }

    private void insertAircraft(Long airlineId, String model, String registrationNo) {
        jdbcTemplate.update("""
                INSERT INTO aircrafts(airline_code_id, model, total_seats, business_seats, economy_seats, registration_no)
                VALUES (?, ?, 180, 24, 156, ?)
                """, airlineId, model, registrationNo);
    }
}
