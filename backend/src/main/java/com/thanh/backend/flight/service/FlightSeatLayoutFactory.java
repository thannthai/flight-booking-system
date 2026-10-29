package com.thanh.backend.flight.service;

import com.thanh.backend.flight.entity.Flight;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.*;
import com.thanh.backend.aircraft.entity.Aircraft;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class FlightSeatLayoutFactory {
    private static final String[] BUSINESS_COLUMNS = {"A", "C", "D", "F"};
    private static final String[] ECONOMY_COLUMNS = {"A", "B", "C", "D", "E", "F"};
    public List<FlightSeat> create(Flight flight, Aircraft aircraft, BigDecimal basePrice) {
        List<FlightSeat> seats = new ArrayList<>();

        int businessCount = aircraft.getBusinessSeats() != null ? aircraft.getBusinessSeats() : 0;
        BigDecimal businessPrice = basePrice.multiply(new BigDecimal("2.5"));

        int row = 1;
        int colIndex = 0;

        for (int i = 0; i < businessCount; i++) {
            seats.add(FlightSeat.builder()
                    .flight(flight)
                    .seatNo(row + BUSINESS_COLUMNS[colIndex])
                    .cabinClass(CabinClass.BUSINESS)
                    .price(businessPrice)
                    .status(SeatStatus.AVAILABLE)
                    .build());

            colIndex++;
            if (colIndex >= BUSINESS_COLUMNS.length) {
                colIndex = 0;
                row++;
            }
        }

        if (colIndex > 0) {
            row++;
        }

        int economyCount = aircraft.getEconomySeats() != null ? aircraft.getEconomySeats() : 0;
        colIndex = 0;

        for (int i = 0; i < economyCount; i++) {
            seats.add(FlightSeat.builder()
                    .flight(flight)
                    .seatNo(row + ECONOMY_COLUMNS[colIndex])
                    .cabinClass(CabinClass.ECONOMY)
                    .price(basePrice)
                    .status(SeatStatus.AVAILABLE)
                    .build());

            colIndex++;
            if (colIndex >= ECONOMY_COLUMNS.length) {
                colIndex = 0;
                row++;
            }
        }

        return seats;
    }

}
