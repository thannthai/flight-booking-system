package com.thanh.backend.airline.repository;

import com.thanh.backend.airline.entity.Airline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AirlineRepository extends JpaRepository<Airline, Long> {

    Optional<Airline> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            SELECT a FROM Airline a WHERE
            (cast(:search as String) IS NULL OR
            LOWER(a.name) LIKE LOWER(CONCAT('%', cast(:search as String), '%')) OR
            LOWER(a.code) LIKE LOWER(CONCAT('%', cast(:search as String), '%')))
            ORDER BY a.name
            """)
    Page<Airline> searchAirlines(@Param("search") String search, Pageable pageable);
}
