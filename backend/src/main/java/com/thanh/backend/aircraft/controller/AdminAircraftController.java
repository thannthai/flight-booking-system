package com.thanh.backend.aircraft.controller;

import com.thanh.backend.aircraft.dto.request.AircraftRequest;
import com.thanh.backend.aircraft.dto.response.AircraftResponse;
import com.thanh.backend.aircraft.service.AircraftService;
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
@RequestMapping("/api/v1/admin/aircraft")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Aircraft Admin", description = "Admin Aircraft management APIs")
public class AdminAircraftController {

    private final AircraftService aircraftService;

    /**
     * Tạo mới thông tin máy bay.
     * Chỉ dành cho vai trò ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) 
    @Operation(summary = "Create a new aircraft")
    public ApiResponse<AircraftResponse> createAircraft(@Valid @RequestBody AircraftRequest request) {
        AircraftResponse response = aircraftService.createAircraft(request);
        return ApiResponse.success("Aircraft created successfully", response);
    }

    /**
     * Cập nhật thông tin máy bay theo ID.
     * Chỉ dành cho vai trò ADMIN.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing aircraft")
    public ApiResponse<AircraftResponse> updateAircraft(
            @PathVariable Long id,
            @Valid @RequestBody AircraftRequest request
    ) {
        AircraftResponse response = aircraftService.updateAircraft(id, request);
        return ApiResponse.success("Aircraft updated successfully", response);
    }

    /**
     * Xóa máy bay theo ID.
     * Chỉ dành cho vai trò ADMIN.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an aircraft")
    public ApiResponse<Void> deleteAircraft(@PathVariable Long id) {
        aircraftService.deleteAircraft(id);
        return ApiResponse.success("Aircraft deleted successfully", null);
    }
}
