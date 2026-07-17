package com.thanh.backend.airport.controller;

import com.thanh.backend.airport.dto.request.AirportRequest;
import com.thanh.backend.airport.dto.response.AirportResponse;
import com.thanh.backend.airport.service.AirportService;
import com.thanh.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/airports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Airport Admin", description = "Admin Airport management APIs")
public class AdminAirportController {

    private final AirportService airportService;

    /**
     * Tạo mới sân bay.
     * Chỉ người dùng có vai trò ADMIN mới được phép truy cập.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new airport")
    public ApiResponse<AirportResponse> createAirport(@Valid @RequestBody AirportRequest request) {
        AirportResponse response = airportService.createAirport(request);
        return ApiResponse.success("Airport created successfully", response);
    }

    /**
     * Cập nhật thông tin sân bay theo ID.
     * Chỉ người dùng có vai trò ADMIN mới được phép truy cập.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing airport")
    public ApiResponse<AirportResponse> updateAirport(
            @PathVariable Long id,
            @Valid @RequestBody AirportRequest request) {
        AirportResponse response = airportService.updateAirport(id, request);
        return ApiResponse.success("Airport updated successfully", response);
    }

    /**
     * Xóa sân bay theo ID.
     * Chỉ người dùng có vai trò ADMIN mới được phép truy cập.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an airport")
    public ApiResponse<Void> deleteAirport(@PathVariable Long id) {
        airportService.deleteAirport(id);
        return ApiResponse.success("Airport deleted successfully", null);
    }
}
