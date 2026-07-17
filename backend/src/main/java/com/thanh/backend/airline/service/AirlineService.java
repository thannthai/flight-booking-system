package com.thanh.backend.airline.service;

import com.thanh.backend.airline.dto.request.AirlineRequest;
import com.thanh.backend.airline.dto.response.AirlineResponse;
import com.thanh.backend.airline.entity.Airline;
import com.thanh.backend.airline.mapper.AirlineMapper;
import com.thanh.backend.airline.repository.AirlineRepository;
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
public class AirlineService {

    private final AirlineRepository airlineRepository;
    private final FlightRepository flightRepository;
    private final AirlineMapper airlineMapper;

    /**
     * Lấy danh sách hãng hàng không kèm phân trang và tìm kiếm theo code/name.
     */
    @Transactional(readOnly = true)
    public Page<AirlineResponse> getAirlines(String search, Pageable pageable) {
        String sanitizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        return airlineRepository.searchAirlines(sanitizedSearch, pageable)
                .map(airlineMapper::toResponse);
    }

    /**
     * Lấy thông tin chi tiết một hãng hàng không theo ID.
     */
    @Transactional(readOnly = true)
    public AirlineResponse getAirlineById(Long id) {
        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRLINE_NOT_FOUND));
        return airlineMapper.toResponse(airline);
    }

    /**
     * Tạo mới một hãng hàng không.
     * Validate tránh trùng lặp mã hãng hàng không (code).
     */
    @Transactional
    public AirlineResponse createAirline(AirlineRequest request) {
        if (airlineRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.AIRLINE_CODE_EXISTED);
        }

        Airline airline = airlineMapper.toEntity(request);
        Airline saved = airlineRepository.save(airline);
        return airlineMapper.toResponse(saved);
    }

    /**
     * Cập nhật thông tin hãng hàng không.
     * Validate xem code cập nhật có bị trùng với một hãng khác hay không.
     */
    @Transactional
    public AirlineResponse updateAirline(Long id, AirlineRequest request) {
        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRLINE_NOT_FOUND));

        if (!airline.getCode().equalsIgnoreCase(request.getCode()) && airlineRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.AIRLINE_CODE_EXISTED);
        }

        airlineMapper.updateEntityFromRequest(request, airline);
        Airline updated = airlineRepository.save(airline);
        return airlineMapper.toResponse(updated);
    }

    /**
     * Xóa hãng hàng không theo ID.
     * Validate không cho phép xóa nếu hãng đang có chuyến bay hoạt động (active flights).
     */
    @Transactional
    public void deleteAirline(Long id) {
        Airline airline = airlineRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AIRLINE_NOT_FOUND));

        if (flightRepository.existsByAirlineId(id)) {
            throw new AppException(ErrorCode.AIRLINE_HAS_ACTIVE_FLIGHTS);
        }

        airlineRepository.delete(airline);
    }
}
