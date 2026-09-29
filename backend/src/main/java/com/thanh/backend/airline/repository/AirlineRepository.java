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

    @Query(value = """
            SELECT * FROM airlines WHERE
            (cast(:search as varchar) IS NULL OR
            unaccent(LOWER(name)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(code)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))))
            ORDER BY name
            """,
            countQuery = """
            SELECT COUNT(*) FROM airlines WHERE
            (cast(:search as varchar) IS NULL OR
            unaccent(LOWER(name)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(code)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))))
            """,
            nativeQuery = true)
    Page<Airline> searchAirlines(@Param("search") String search, Pageable pageable);
}
