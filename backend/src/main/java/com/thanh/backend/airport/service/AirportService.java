package com.thanh.backend.airport.service;

import com.thanh.backend.airport.dto.request.AirportRequest;
import com.thanh.backend.airport.dto.response.AirportResponse;
import com.thanh.backend.airport.entity.Airport;
import com.thanh.backend.airport.mapper.AirportMapper;
import com.thanh.backend.airport.repository.AirportRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AirportService {

    private final AirportRepository airportRepository;
    private final FlightRepository flightRepository;
    private final AirportMapper airportMapper;

    /**
     * Lấy danh sách sân bay kèm phân trang và tìm kiếm theo code/name/city/country.
     */
    @Transactional(readOnly = true)
    public Page<AirportResponse> getAirports(String search, Pageable pageable) {
        String sanitizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        return airportRepository.searchAirports(sanitizedSearch, pageable)
                .map(airportMapper::toResponse);
    }

    /**
     * Lấy thông tin chi tiết một sân bay theo ID.
     */
    @Transactional(readOnly = true)
    public AirportResponse getAirportById(Long id) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRPORT_NOT_FOUND));
        return airportMapper.toResponse(airport);
    }

    /**
     * Tạo mới một sân bay.
     * Validate tránh trùng lặp mã sân bay (iataCode).
     */
    @Transactional
    public AirportResponse createAirport(AirportRequest request) {
        if (airportRepository.existsByIataCode(request.getIataCode())) {
            throw new AppException(ErrorCode.AIRPORT_CODE_EXISTED);
        }

        Airport airport = airportMapper.toEntity(request);
        Airport saved = airportRepository.save(airport);
        return airportMapper.toResponse(saved);
    }

    /**
     * Cập nhật thông tin sân bay.
     * Validate xem iataCode cập nhật có bị trùng với một sân bay khác hay không.
     */
    @Transactional
    public AirportResponse updateAirport(Long id, AirportRequest request) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRPORT_NOT_FOUND));

        if (!airport.getIataCode().equalsIgnoreCase(request.getIataCode())
                && airportRepository.existsByIataCode(request.getIataCode())) {
            throw new AppException(ErrorCode.AIRPORT_CODE_EXISTED);
        }

        airportMapper.updateEntityFromRequest(request, airport);
        Airport updated = airportRepository.save(airport);
        return airportMapper.toResponse(updated);
    }

    /**
     * Xóa sân bay theo ID.
     * Validate không cho phép xóa nếu sân bay đang được tham chiếu bởi chuyến bay
     * nào đó.
     */
    @Transactional
    public void deleteAirport(Long id) {
        Airport airport = airportRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRPORT_NOT_FOUND));

        if (flightRepository.existsByOriginIdOrDestId(id, id)) {
            throw new AppException(ErrorCode.AIRPORT_HAS_ACTIVE_FLIGHTS);
        }

        airportRepository.delete(airport);
    }
}
