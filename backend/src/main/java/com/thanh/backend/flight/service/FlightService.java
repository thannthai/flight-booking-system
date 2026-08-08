package com.thanh.backend.flight.service;

import com.thanh.backend.aircraft.entity.Aircraft;
import com.thanh.backend.aircraft.mapper.AircraftMapper;
import com.thanh.backend.aircraft.repository.AircraftRepository;
import com.thanh.backend.airline.entity.Airline;
import com.thanh.backend.airline.repository.AirlineRepository;
import com.thanh.backend.airport.entity.Airport;
import com.thanh.backend.airport.repository.AirportRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.dto.request.FlightRequest;
import com.thanh.backend.flight.dto.request.UpdateFlightStatusRequest;
import com.thanh.backend.flight.dto.response.FlightResponse;
import com.thanh.backend.flight.dto.response.FlightSeatMapResponse;
import com.thanh.backend.flight.dto.response.FlightSeatResponse;
import com.thanh.backend.flight.entity.Flight;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.CabinClass;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.flight.enums.SeatStatus;
import com.thanh.backend.flight.mapper.FlightMapper;
import com.thanh.backend.flight.repository.FlightRepository;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlightService {

    // Giả định cấu hình sơ đồ ghế mặc định cho MVP:
    // Business layout (2-2): A, C | D, F
    // Economy layout (3-3): A, B, C | D, E, F
    private static final String[] BUSINESS_COLUMNS = {"A", "C", "D", "F"};
    private static final String[] ECONOMY_COLUMNS = {"A", "B", "C", "D", "E", "F"};

    private final FlightRepository flightRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final AirlineRepository airlineRepository;
    private final AircraftRepository aircraftRepository;
    private final AirportRepository airportRepository;
    private final FlightMapper flightMapper;
    private final AircraftMapper aircraftMapper;

    /**
     * Lấy danh sách chuyến bay kèm phân trang và tìm kiếm theo điểm đi, điểm đến, ngày và trạng thái.
     * Tự động xác định ZoneId từ sân bay cất cánh (origin) để quy đổi khoảng ngày cất cánh chuẩn xác theo múi giờ địa phương.
     */
    @Transactional(readOnly = true)
    public Page<FlightResponse> searchFlights(
            String origin,
            String destination,
            LocalDate departureDate,
            FlightStatus status,
            Pageable pageable
    ) {
        String sanitizedOrigin = StringUtils.hasText(origin) ? origin.trim() : null;
        String sanitizedDest = StringUtils.hasText(destination) ? destination.trim() : null;

        ZoneId zone = ZoneOffset.UTC;
        if (sanitizedOrigin != null) {
            Airport originAirport = airportRepository.findByIataCodeIgnoreCase(sanitizedOrigin)
                    .orElseGet(() -> airportRepository.findFirstByCityIgnoreCase(sanitizedOrigin).orElse(null));
            if (originAirport != null && StringUtils.hasText(originAirport.getTimezone())) {
                try {
                    zone = ZoneId.of(originAirport.getTimezone());
                } catch (Exception ignored) {
                }
            }
        }

        OffsetDateTime startOfDay = null;
        OffsetDateTime endOfDay = null;
        if (departureDate != null) {
            startOfDay = departureDate.atStartOfDay(zone).toOffsetDateTime();
            endOfDay = departureDate.atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();
        }

        return flightRepository.searchFlights(sanitizedOrigin, sanitizedDest, startOfDay, endOfDay, status, pageable)
                .map(flightMapper::toResponse);
    }

    /**
     * Lấy thông tin chi tiết chuyến bay theo ID.
     */
    @Transactional(readOnly = true)
    public FlightResponse getFlightById(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FLIGHT_NOT_FOUND));
        return flightMapper.toResponse(flight);
    }

    /**
     * Lấy danh sách sơ đồ ghế của chuyến bay kèm kiểm tra trạng thái hết hạn giữ ghế (Lazy Status Check).
     */
    @Transactional(readOnly = true)
    public FlightSeatMapResponse getSeatsByFlightId(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new AppException(ErrorCode.FLIGHT_NOT_FOUND));

        List<FlightSeat> seats = flightSeatRepository.findByFlightId(flightId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<FlightSeatResponse> seatResponses = seats.stream().map(seat -> {
            SeatStatus effectiveStatus = seat.getStatus();
            if (seat.getStatus() == SeatStatus.HELD && seat.getHeldUntil() != null && seat.getHeldUntil().isBefore(now)) {
                effectiveStatus = SeatStatus.AVAILABLE;
            }
            return FlightSeatResponse.builder()
                    .id(seat.getId())
                    .seatNo(seat.getSeatNo())
                    .cabinClass(seat.getCabinClass())
                    .price(seat.getPrice())
                    .status(effectiveStatus)
                    .build();
        }).toList();

        return FlightSeatMapResponse.builder()
                .flightId(flight.getId())
                .flightNo(flight.getFlightNo())
                .aircraft(aircraftMapper.toResponse(flight.getAircraft()))
                .seats(seatResponses)
                .build();
    }

    /**
     * Tạo mới chuyến bay và tự động sinh sơ đồ ghế (FlightSeat).
     */
    @Transactional
    public FlightResponse createFlight(FlightRequest request) {
        validateFlightRequest(request);

        if (flightRepository.existsByFlightNo(request.getFlightNo())) {
            throw new AppException(ErrorCode.FLIGHT_NUMBER_EXISTED);
        }

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRLINE_NOT_FOUND));

        Aircraft aircraft = aircraftRepository.findById(request.getAircraftId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRCRAFT_NOT_FOUND));

        Airport origin = airportRepository.findById(request.getOriginAirportId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRPORT_NOT_FOUND));

        Airport dest = airportRepository.findById(request.getDestAirportId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRPORT_NOT_FOUND));

        Flight flight = flightMapper.toEntity(request);
        flight.setAirline(airline);
        flight.setAircraft(aircraft);
        flight.setOrigin(origin);
        flight.setDest(dest);

        if (flight.getStatus() == null) {
            flight.setStatus(FlightStatus.SCHEDULED);
        }

        Flight savedFlight = flightRepository.save(flight);

        generateSeatsForFlight(savedFlight, aircraft, request.getBasePrice());

        return flightMapper.toResponse(savedFlight);
    }

    /**
     * Cập nhật thông tin chuyến bay theo ID.
     */
    @Transactional
    public FlightResponse updateFlight(Long id, FlightRequest request) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FLIGHT_NOT_FOUND));

        validateFlightRequest(request);

        if (!flight.getFlightNo().equalsIgnoreCase(request.getFlightNo())
                && flightRepository.existsByFlightNo(request.getFlightNo())) {
            throw new AppException(ErrorCode.FLIGHT_NUMBER_EXISTED);
        }

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRLINE_NOT_FOUND));

        Aircraft aircraft = aircraftRepository.findById(request.getAircraftId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRCRAFT_NOT_FOUND));

        Airport origin = airportRepository.findById(request.getOriginAirportId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRPORT_NOT_FOUND));

        Airport dest = airportRepository.findById(request.getDestAirportId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRPORT_NOT_FOUND));

        flightMapper.updateEntityFromRequest(request, flight);
        flight.setAirline(airline);
        flight.setAircraft(aircraft);
        flight.setOrigin(origin);
        flight.setDest(dest);

        if (request.getStatus() != null) {
            flight.setStatus(request.getStatus());
        }

        Flight updatedFlight = flightRepository.save(flight);
        return flightMapper.toResponse(updatedFlight);
    }

    /**
     * Cập nhật trạng thái và thời gian của chuyến bay.
     */
    @Transactional
    public FlightResponse updateFlightStatus(Long id, UpdateFlightStatusRequest request) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FLIGHT_NOT_FOUND));

        flight.setStatus(request.getStatus());

        if (request.getNewDepartureTime() != null) {
            flight.setDepartureTime(request.getNewDepartureTime());
        }
        if (request.getNewArrivalTime() != null) {
            flight.setArrivalTime(request.getNewArrivalTime());
        }

        if (!flight.getArrivalTime().isAfter(flight.getDepartureTime())) {
            throw new AppException(ErrorCode.INVALID_FLIGHT_TIME);
        }

        Flight updated = flightRepository.save(flight);
        return flightMapper.toResponse(updated);
    }

    /**
     * Xóa chuyến bay theo ID và các ghế liên quan.
     */
    @Transactional
    public void deleteFlight(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FLIGHT_NOT_FOUND));

        List<FlightSeat> seats = flightSeatRepository.findByFlightId(id);
        if (!seats.isEmpty()) {
            flightSeatRepository.deleteAll(seats);
        }

        flightRepository.delete(flight);
    }

    /**
     * Tự động khởi tạo sơ đồ ghế (FlightSeat) dựa trên cấu hình ghế của máy bay.
     * Cấu hình mặc định cho MVP:
     * - Khoang Business (2-2): Cột A, C | D, F (Hệ số giá: 2.5 x basePrice)
     * - Khoang Economy (3-3): Cột A, B, C | D, E, F (Hệ số giá: 1.0 x basePrice)
     */
    private void generateSeatsForFlight(Flight flight, Aircraft aircraft, BigDecimal basePrice) {
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

        flightSeatRepository.saveAll(seats);
    }

    /**
     * Validate nghiệp vụ cơ bản của FlightRequest: Sân bay đi và đến không trùng nhau, thời gian đến sau thời gian đi.
     */
    private void validateFlightRequest(FlightRequest request) {
        if (request.getOriginAirportId().equals(request.getDestAirportId())) {
            throw new AppException(ErrorCode.INVALID_FLIGHT_AIRPORTS);
        }

        if (!request.getArrivalTime().isAfter(request.getDepartureTime())) {
            throw new AppException(ErrorCode.INVALID_FLIGHT_TIME);
        }
    }
}
