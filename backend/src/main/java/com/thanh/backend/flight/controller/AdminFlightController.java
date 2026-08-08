package com.thanh.backend.flight.controller;

import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.common.response.PageResponse;
import com.thanh.backend.flight.dto.request.FlightRequest;
import com.thanh.backend.flight.dto.request.UpdateFlightStatusRequest;
import com.thanh.backend.flight.dto.response.FlightResponse;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.flight.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/flights")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Flight Admin", description = "Admin Flight management APIs")
public class AdminFlightController {

    private final FlightService flightService;

    /**
     * Lấy danh sách chuyến bay cho Admin quản lý (kèm phân trang và bộ lọc).
     */
    @GetMapping
    @Operation(summary = "Get list of flights for Admin management")
    public ApiResponse<PageResponse<FlightResponse>> getFlightsForAdmin(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @RequestParam(required = false) FlightStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt") Pageable pageable) {
        Page<FlightResponse> flights = flightService.searchFlights(origin, destination, departureDate, status,
                pageable);
        return ApiResponse.success("Flights retrieved successfully for admin", PageResponse.of(flights));
    }

    /**
     * Tạo mới chuyến bay (Tự động sinh sơ đồ ghế).
     * Chỉ dành cho vai trò ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new flight and auto-generate seat layout")
    public ApiResponse<FlightResponse> createFlight(@Valid @RequestBody FlightRequest request) {
        FlightResponse response = flightService.createFlight(request);
        return ApiResponse.success("Flight created successfully", response);
    }

    /**
     * Cập nhật thông tin chuyến bay theo ID.
     * Chỉ dành cho vai trò ADMIN.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update flight details")
    public ApiResponse<FlightResponse> updateFlight(
            @PathVariable Long id,
            @Valid @RequestBody FlightRequest request) {
        FlightResponse response = flightService.updateFlight(id, request);
        return ApiResponse.success("Flight updated successfully", response);
    }

    /**
     * Cập nhật trạng thái và giờ cất/hạ cánh của chuyến bay.
     * Chỉ dành cho vai trò ADMIN.
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Update flight status and departure/arrival schedule")
    public ApiResponse<FlightResponse> updateFlightStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFlightStatusRequest request) {
        FlightResponse response = flightService.updateFlightStatus(id, request);
        return ApiResponse.success("Flight status updated successfully", response);
    }

    /**
     * Xóa chuyến bay theo ID.
     * Chỉ dành cho vai trò ADMIN.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a flight and its seats")
    public ApiResponse<Void> deleteFlight(@PathVariable Long id) {
        flightService.deleteFlight(id);
        return ApiResponse.success("Flight deleted successfully", null);
    }
}
