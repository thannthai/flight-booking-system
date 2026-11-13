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
            WHERE (:origin IS NULL OR FUNCTION('unaccent', LOWER(f.origin.iataCode)) = FUNCTION('unaccent', LOWER(cast(:origin as string))) OR FUNCTION('unaccent', LOWER(f.origin.city)) = FUNCTION('unaccent', LOWER(cast(:origin as string))))
              AND (:destination IS NULL OR FUNCTION('unaccent', LOWER(f.dest.iataCode)) = FUNCTION('unaccent', LOWER(cast(:destination as string))) OR FUNCTION('unaccent', LOWER(f.dest.city)) = FUNCTION('unaccent', LOWER(cast(:destination as string))))
              AND (cast(:startOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime >= :startOfDay)
              AND (cast(:endOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime <= :endOfDay)
              AND (:status IS NULL OR f.status = :status)
              AND (:upcomingOnly = false OR f.departureTime > cast(function('clock_timestamp') as OffsetDateTime))
            """,
            countQuery = """
            SELECT COUNT(f) FROM Flight f
            WHERE (:origin IS NULL OR FUNCTION('unaccent', LOWER(f.origin.iataCode)) = FUNCTION('unaccent', LOWER(cast(:origin as string))) OR FUNCTION('unaccent', LOWER(f.origin.city)) = FUNCTION('unaccent', LOWER(cast(:origin as string))))
              AND (:destination IS NULL OR FUNCTION('unaccent', LOWER(f.dest.iataCode)) = FUNCTION('unaccent', LOWER(cast(:destination as string))) OR FUNCTION('unaccent', LOWER(f.dest.city)) = FUNCTION('unaccent', LOWER(cast(:destination as string))))
              AND (cast(:startOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime >= :startOfDay)
              AND (cast(:endOfDay as java.time.OffsetDateTime) IS NULL OR f.departureTime <= :endOfDay)
              AND (:status IS NULL OR f.status = :status)
              AND (:upcomingOnly = false OR f.departureTime > cast(function('clock_timestamp') as OffsetDateTime))
            """)
    Page<Flight> searchFlights(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("startOfDay") OffsetDateTime startOfDay,
            @Param("endOfDay") OffsetDateTime endOfDay,
            @Param("status") FlightStatus status,
            @Param("upcomingOnly") boolean upcomingOnly,
            Pageable pageable
    );
}
