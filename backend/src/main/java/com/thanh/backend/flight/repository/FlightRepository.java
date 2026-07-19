package com.thanh.backend.flight.repository;

import com.thanh.backend.flight.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {
    boolean existsByAirlineId(Long airlineId);

    boolean existsByAircraftId(Long aircraftId);

    boolean existsByOriginIdOrDestId(Long originId, Long destId);
}
