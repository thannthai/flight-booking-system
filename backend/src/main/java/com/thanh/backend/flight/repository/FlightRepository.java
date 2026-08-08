package com.thanh.backend.flight.repository;

import com.thanh.backend.flight.entity.Flight;
import com.thanh.backend.flight.enums.FlightStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNo(String flightNo);

    boolean existsByFlightNo(String flightNo);

    boolean existsByAircraftId(Long aircraftId);

    boolean existsByAirlineId(Long airlineId);

    boolean existsByOriginIdOrDestId(Long originId, Long destId);

    @Query(value = """
            SELECT f FROM Flight f
            JOIN FETCH f.airline
            JOIN FETCH f.aircraft
            JOIN FETCH f.origin
            JOIN FETCH f.dest
            WHERE (:origin IS NULL OR LOWER(f.origin.iataCode) = LOWER(:origin) OR LOWER(f.origin.city) = LOWER(:origin))
              AND (:destination IS NULL OR LOWER(f.dest.iataCode) = LOWER(:destination) OR LOWER(f.dest.city) = LOWER(:destination))
              AND (cast(:startOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime >= :startOfDay)
              AND (cast(:endOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime <= :endOfDay)
              AND (:status IS NULL OR f.status = :status)
            """,
            countQuery = """
            SELECT COUNT(f) FROM Flight f
            WHERE (:origin IS NULL OR LOWER(f.origin.iataCode) = LOWER(:origin) OR LOWER(f.origin.city) = LOWER(:origin))
              AND (:destination IS NULL OR LOWER(f.dest.iataCode) = LOWER(:destination) OR LOWER(f.dest.city) = LOWER(:destination))
              AND (cast(:startOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime >= :startOfDay)
              AND (cast(:endOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime <= :endOfDay)
              AND (:status IS NULL OR f.status = :status)
            """)
    Page<Flight> searchFlights(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay,
            @Param("status") FlightStatus status,
            Pageable pageable
    );
}
