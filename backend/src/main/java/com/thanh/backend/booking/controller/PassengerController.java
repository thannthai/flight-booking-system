package com.thanh.backend.booking.controller;

import com.thanh.backend.auth.security.UserPrincipal;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.response.PassengerResponse;
import com.thanh.backend.booking.service.PassengerService;
import com.thanh.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/passengers")
@RequiredArgsConstructor
@Tag(name = "Passenger Directory", description = "Passenger Directory Management APIs")
public class PassengerController {

    private final PassengerService passengerService;

    /**
     * Lấy danh sách hành khách đã lưu trong danh bạ của tôi.
     */
    @GetMapping("/my")
    @Operation(summary = "Get current user's saved passenger directory")
    public ApiResponse<List<PassengerResponse>> getMyPassengers(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<PassengerResponse> response = passengerService.getMyPassengers(principal.getUser());
        return ApiResponse.success("Passenger directory retrieved successfully", response);
    }

    /**
     * Thêm hành khách mới vào danh bạ.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new passenger to personal directory")
    public ApiResponse<PassengerResponse> createPassenger(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PassengerRequest request
    ) {
        PassengerResponse response = passengerService.createPassenger(principal.getUser(), request);
        return ApiResponse.success("Passenger added to directory successfully", response);
    }

    /**
     * Cập nhật thông tin hành khách trong danh bạ.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update saved passenger details in directory")
    public ApiResponse<PassengerResponse> updatePassenger(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PassengerRequest request
    ) {
        PassengerResponse response = passengerService.updatePassenger(principal.getUser(), id, request);
        return ApiResponse.success("Passenger updated successfully", response);
    }

    /**
     * Xóa hành khách khỏi danh bạ cá nhân.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a passenger from personal directory")
    public ApiResponse<Void> deletePassenger(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        passengerService.deletePassenger(principal.getUser(), id);
        return ApiResponse.success("Passenger deleted from directory successfully", null);
    }
}
