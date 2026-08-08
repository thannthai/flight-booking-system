package com.thanh.backend.flight.controller;

import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.common.response.PageResponse;
import com.thanh.backend.flight.dto.response.FlightResponse;
import com.thanh.backend.flight.dto.response.FlightSeatMapResponse;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.flight.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
@Tag(name = "Flight Public", description = "Public Flight search and detail APIs")
public class FlightController {

    private final FlightService flightService;

    /**
     * Tìm kiếm chuyến bay công khai cho khách hàng mua vé có phân trang.
     * Mặc định chỉ trả về các chuyến bay đang mở bán (status = SCHEDULED).
     */
    @GetMapping("/search")
    @Operation(summary = "Search scheduled flights for ticket booking")
    public ApiResponse<PageResponse<FlightResponse>> searchFlights(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @ParameterObject @PageableDefault(sort = "departureTime") Pageable pageable
    ) {
        Page<FlightResponse> flights = flightService.searchFlights(
                origin,
                destination,
                departureDate,
                FlightStatus.SCHEDULED,
                pageable
        );
        return ApiResponse.success("Flights retrieved successfully", PageResponse.of(flights));
    }

    /**
     * Lấy thông tin chi tiết một chuyến bay theo ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information of a flight by ID")
    public ApiResponse<FlightResponse> getFlightById(@PathVariable Long id) {
        FlightResponse flight = flightService.getFlightById(id);
        return ApiResponse.success("Flight retrieved successfully", flight);
    }

    /**
     * Lấy danh sách sơ đồ ghế của chuyến bay kèm trạng thái ghế cho màn hình chọn chỗ.
     */
    @GetMapping("/{id}/seats")
    @Operation(summary = "Get seat map and availability for a flight")
    public ApiResponse<FlightSeatMapResponse> getSeatsByFlightId(@PathVariable Long id) {
        FlightSeatMapResponse seatMap = flightService.getSeatsByFlightId(id);
        return ApiResponse.success("Flight seats retrieved successfully", seatMap);
    }
}
