package com.thanh.backend.aircraft.controller;

import com.thanh.backend.aircraft.dto.response.AircraftResponse;
import com.thanh.backend.aircraft.service.AircraftService;
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
@RequestMapping("/api/v1/aircraft")
@RequiredArgsConstructor
@Tag(name = "Aircraft Public", description = "Public Aircraft APIs")
public class AircraftController {

    private final AircraftService aircraftService;

    /**
     * Lấy danh sách máy bay có phân trang và tìm kiếm theo từ khóa.
     * Mặc định sắp xếp theo số đăng ký (registrationNo).
     */
    @GetMapping
    @Operation(summary = "Get list of aircrafts with pagination and keyword search")
    public ApiResponse<PageResponse<AircraftResponse>> getAircrafts(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(sort = "registrationNo") Pageable pageable
    ) {
        Page<AircraftResponse> aircrafts = aircraftService.getAircrafts(search, pageable);
        return ApiResponse.success("Aircrafts retrieved successfully", PageResponse.of(aircrafts));
    }

    /**
     * Lấy thông tin chi tiết một máy bay bằng ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information of an aircraft by ID")
    public ApiResponse<AircraftResponse> getAircraftById(@PathVariable Long id) {
        AircraftResponse aircraft = aircraftService.getAircraftById(id);
        return ApiResponse.success("Aircraft retrieved successfully", aircraft);
    }
}
