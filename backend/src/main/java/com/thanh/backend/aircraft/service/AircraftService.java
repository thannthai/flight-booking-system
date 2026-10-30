package com.thanh.backend.aircraft.service;

import com.thanh.backend.aircraft.dto.request.AircraftRequest;
import com.thanh.backend.aircraft.dto.response.AircraftResponse;
import com.thanh.backend.aircraft.entity.Aircraft;
import com.thanh.backend.aircraft.mapper.AircraftMapper;
import com.thanh.backend.aircraft.repository.AircraftRepository;
import com.thanh.backend.airline.entity.Airline;
import com.thanh.backend.airline.repository.AirlineRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AircraftService {

    private final AircraftRepository aircraftRepository;
    private final AirlineRepository airlineRepository;
    private final FlightRepository flightRepository;
    private final AircraftMapper aircraftMapper;

    /**
     * Lấy danh sách máy bay kèm phân trang và tìm kiếm theo model/registrationNo/airline.
     */
    @Transactional(readOnly = true)
    public Page<AircraftResponse> getAircrafts(String search, Pageable pageable) {
        String sanitizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        // Native SQL sorting uses column names, while the API uses entity properties.
        Sort sqlSort = Sort.by(pageable.getSort().stream()
                .map(order -> "registrationNo".equals(order.getProperty())
                        ? order.withProperty("registration_no") : order)
                .toList());
        Pageable sqlPageable = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sqlSort)
                : Pageable.unpaged(sqlSort);
        return aircraftRepository.searchAircrafts(sanitizedSearch, sqlPageable)
                .map(aircraftMapper::toResponse);
    }

    /**
     * Lấy thông tin chi tiết một máy bay theo ID.
     */
    @Transactional(readOnly = true)
    public AircraftResponse getAircraftById(Long id) {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRCRAFT_NOT_FOUND));
        return aircraftMapper.toResponse(aircraft);
    }

    /**
     * Tạo mới thông tin máy bay.
     * Validate:
     * 1. Tổng số ghế businessSeats + economySeats phải bằng totalSeats.
     * 2. Hãng hàng không (airlineId) phải tồn tại.
     * 3. Số đăng ký máy bay (registrationNo) không được trùng lặp.
     */
    @Transactional
    public AircraftResponse createAircraft(AircraftRequest request) {
        validateSeatConfiguration(request);

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRLINE_NOT_FOUND));

        if (aircraftRepository.existsByRegistrationNo(request.getRegistrationNo())) {
            throw new AppException(ErrorCode.AIRCRAFT_REGISTRATION_NO_EXISTED);
        }

        Aircraft aircraft = aircraftMapper.toEntity(request);
        aircraft.setAirline(airline);

        Aircraft saved = aircraftRepository.save(aircraft);
        return aircraftMapper.toResponse(saved);
    }

    /**
     * Cập nhật thông tin máy bay theo ID.
     * Validate:
     * 1. Máy bay phải tồn tại.
     * 2. Tổng số ghế businessSeats + economySeats phải bằng totalSeats.
     * 3. Hãng hàng không (airlineId) phải tồn tại.
     * 4. Số đăng ký mới không bị trùng với máy bay khác.
     */
    @Transactional
    public AircraftResponse updateAircraft(Long id, AircraftRequest request) {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRCRAFT_NOT_FOUND));

        validateSeatConfiguration(request);

        Airline airline = airlineRepository.findById(request.getAirlineId())
                .orElseThrow(() -> new AppException(ErrorCode.AIRLINE_NOT_FOUND));

        if (!aircraft.getRegistrationNo().equalsIgnoreCase(request.getRegistrationNo())
                && aircraftRepository.existsByRegistrationNo(request.getRegistrationNo())) {
            throw new AppException(ErrorCode.AIRCRAFT_REGISTRATION_NO_EXISTED);
        }

        aircraftMapper.updateEntityFromRequest(request, aircraft);
        aircraft.setAirline(airline);

        Aircraft updated = aircraftRepository.save(aircraft);
        return aircraftMapper.toResponse(updated);
    }

    /**
     * Xóa máy bay theo ID.
     * Validate: Không được phép xóa nếu máy bay đang gắn liền với các chuyến bay trong hệ thống.
     */
    @Transactional
    public void deleteAircraft(Long id) {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRCRAFT_NOT_FOUND));

        if (flightRepository.existsByAircraftId(id)) {
            throw new AppException(ErrorCode.AIRCRAFT_HAS_ACTIVE_FLIGHTS);
        }

        aircraftRepository.delete(aircraft);
    }

    /**
     * Kiểm tra quy tắc nghiệp vụ ghế máy bay: Tổng ghế business + economy phải bằng totalSeats.
     */
    private void validateSeatConfiguration(AircraftRequest request) {
        int business = request.getBusinessSeats() != null ? request.getBusinessSeats() : 0;
        int economy = request.getEconomySeats() != null ? request.getEconomySeats() : 0;
        int total = request.getTotalSeats() != null ? request.getTotalSeats() : 0;

        if (business + economy != total) {
            throw new AppException(ErrorCode.INVALID_AIRCRAFT_SEATS);
        }
    }
}
