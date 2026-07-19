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

    @Query("""
            SELECT a FROM Aircraft a JOIN FETCH a.airline WHERE
            (:search IS NULL OR
            LOWER(a.model) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(a.registrationNo) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(a.airline.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(a.airline.code) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Aircraft> searchAircrafts(@Param("search") String search, Pageable pageable);
}
