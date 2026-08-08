package com.thanh.backend.flight.repository;

import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {

    List<FlightSeat> findByFlightId(Long flightId);

    List<FlightSeat> findByFlightIdAndStatus(Long flightId, SeatStatus status);
}
