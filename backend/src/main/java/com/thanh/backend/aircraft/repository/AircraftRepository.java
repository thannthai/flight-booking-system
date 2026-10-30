package com.thanh.backend.aircraft.repository;

import com.thanh.backend.aircraft.entity.Aircraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AircraftRepository extends JpaRepository<Aircraft, Long> {

    Optional<Aircraft> findByRegistrationNo(String registrationNo);

    boolean existsByRegistrationNo(String registrationNo);

    @Query(value = """
            SELECT a.* FROM aircrafts a
            JOIN airlines al ON a.airline_code_id = al.id WHERE
            (cast(:search as varchar) IS NULL OR
            unaccent(LOWER(a.model)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(a.registration_no)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(al.name)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(al.code)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))))
            """,
            countQuery = """
            SELECT COUNT(*) FROM aircrafts a
            JOIN airlines al ON a.airline_code_id = al.id WHERE
            (cast(:search as varchar) IS NULL OR
            unaccent(LOWER(a.model)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(a.registration_no)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(al.name)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))) OR
            unaccent(LOWER(al.code)) LIKE unaccent(LOWER(CONCAT('%', cast(:search as varchar), '%'))))
            """,
            nativeQuery = true)
    Page<Aircraft> searchAircrafts(@Param("search") String search, Pageable pageable);
}
