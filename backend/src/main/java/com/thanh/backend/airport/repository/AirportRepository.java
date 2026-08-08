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

    @Query("""
            SELECT a FROM Airport a WHERE
            (cast(:search as String) IS NULL OR
            LOWER(a.name) LIKE LOWER(CONCAT('%', cast(:search as String), '%')) OR
            LOWER(a.iataCode) LIKE LOWER(CONCAT('%', cast(:search as String), '%')) OR
            LOWER(a.city) LIKE LOWER(CONCAT('%', cast(:search as String), '%')) OR
            LOWER(a.country) LIKE LOWER(CONCAT('%', cast(:search as String), '%')))
            ORDER BY a.name
            """)
    Page<Airport> searchAirports(@Param("search") String search, Pageable pageable);
}
