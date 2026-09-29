package com.thanh.backend.airport.repository;

import com.thanh.backend.airport.entity.Airport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AirportRepository extends JpaRepository<Airport, Long> {

    Optional<Airport> findByIataCode(String iataCode);

    Optional<Airport> findByIataCodeIgnoreCase(String iataCode);

    Optional<Airport> findFirstByCityIgnoreCase(String city);

    boolean existsByIataCode(String iataCode);

    @Query(value = """
            SELECT * FROM airports WHERE
            (cast(:search as varchar) IS NULL OR
            unaccent(LOWER(name)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(iata_code)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(city)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(country)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))))
            ORDER BY name
            """,
            countQuery = """
            SELECT COUNT(*) FROM airports WHERE
            (cast(:search as varchar) IS NULL OR
            unaccent(LOWER(name)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(iata_code)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(city)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(country)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))))
            """,
            nativeQuery = true)
    Page<Airport> searchAirports(@Param("search") String search, Pageable pageable);
}
