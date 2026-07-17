package com.thanh.backend.airport.controller;

import com.thanh.backend.airport.dto.response.AirportResponse;
import com.thanh.backend.airport.service.AirportService;
import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/airports")
@RequiredArgsConstructor
@Tag(name = "Airport Public", description = "Public Airport APIs")
public class AirportController {

    private final AirportService airportService;

    /**
     * Lấy danh sách sân bay với phân trang và tìm kiếm theo code/name/city/country.
     * Mặc định sắp xếp theo tên sân bay tăng dần (name, asc).
     */
    @GetMapping
    @Operation(summary = "Get list of airports with pagination and keyword search")
    public ApiResponse<PageResponse<AirportResponse>> getAirports(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(sort = "name") Pageable pageable) {
        Page<AirportResponse> airports = airportService.getAirports(search, pageable);
        return ApiResponse.success("Airports retrieved successfully", PageResponse.of(airports));
    }

    /**
     * Lấy thông tin chi tiết một sân bay bằng ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information of an airport by ID")
    public ApiResponse<AirportResponse> getAirportById(@PathVariable Long id) {
        AirportResponse airport = airportService.getAirportById(id);
        return ApiResponse.success("Airport retrieved successfully", airport);
    }
}
