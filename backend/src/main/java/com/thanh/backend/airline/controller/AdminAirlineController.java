package com.thanh.backend.airline.controller;

import com.thanh.backend.airline.dto.request.AirlineRequest;
import com.thanh.backend.airline.dto.response.AirlineResponse;
import com.thanh.backend.airline.service.AirlineService;
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
@RequestMapping("/api/v1/admin/airlines")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Airline Admin", description = "Admin Airline management APIs") // dùng để định nghĩa tên nhóm và mô tả cho toàn bộ các API nằm trong Controller trên giao diện Swagger UI
public class AdminAirlineController {

    private final AirlineService airlineService;

    /**
     * Tạo mới hãng hàng không.
     * Chỉ người dùng có vai trò ADMIN mới được phép truy cập.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new airline")    // để Swagger UI giải thích chi tiết API này dùng để làm gì
    public ApiResponse<AirlineResponse> createAirline(@Valid @RequestBody AirlineRequest request) {
        AirlineResponse response = airlineService.createAirline(request);
        return ApiResponse.success("Airline created successfully", response);
    }

    /**
     * Cập nhật thông tin hãng hàng không theo ID.
     * Chỉ người dùng có vai trò ADMIN mới được phép truy cập.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing airline")
    public ApiResponse<AirlineResponse> updateAirline(
            @PathVariable Long id,
            @Valid @RequestBody AirlineRequest request
    ) {
        AirlineResponse response = airlineService.updateAirline(id, request);
        return ApiResponse.success("Airline updated successfully", response);
    }

    /**
     * Xóa hãng hàng không theo ID.
     * Chỉ người dùng có vai trò ADMIN mới được phép truy cập.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an airline")
    public ApiResponse<Void> deleteAirline(@PathVariable Long id) {
        airlineService.deleteAirline(id);
        return ApiResponse.success("Airline deleted successfully", null);
    }
}
