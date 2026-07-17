package com.thanh.backend.airline.controller;

import com.thanh.backend.airline.dto.response.AirlineResponse;
import com.thanh.backend.airline.service.AirlineService;
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
@RequestMapping("/api/v1/airlines")
@RequiredArgsConstructor
@Tag(name = "Airline Public", description = "Public Airline APIs")
public class AirlineController {

    private final AirlineService airlineService;

    /**
     * Lấy danh sách hãng hàng không với phân trang và tìm kiếm theo code/name.
     * Mặc định sắp xếp theo tên hãng tăng dần (name, asc).
     */
    @GetMapping
    @Operation(summary = "Get list of airlines with pagination and keyword search")
    public ApiResponse<PageResponse<AirlineResponse>> getAirlines(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(sort = "name") Pageable pageable
    ) {
        Page<AirlineResponse> airlines = airlineService.getAirlines(search, pageable);
        return ApiResponse.success("Airlines retrieved successfully", PageResponse.of(airlines));
    }

    /**
     * Lấy thông tin chi tiết một hãng hàng không bằng ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information of an airline by ID")
    public ApiResponse<AirlineResponse> getAirlineById(@PathVariable Long id) {
        AirlineResponse airline = airlineService.getAirlineById(id);
        return ApiResponse.success("Airline retrieved successfully", airline);
    }
}
