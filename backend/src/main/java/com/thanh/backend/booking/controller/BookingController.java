package com.thanh.backend.booking.controller;

import com.thanh.backend.auth.security.UserPrincipal;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.dto.response.HoldSeatsResponse;
import com.thanh.backend.booking.service.BookingService;
import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Core", description = "Booking and Seat Holding APIs")
public class BookingController {

    private final BookingService bookingService;

    /**
     * Giữ ghế trong 15 phút (Redis Hold + Database Atomic Lock).
     */
    @PostMapping("/hold-seats")
    @Operation(summary = "Hold flight seats for 15 minutes in Redis and Database")
    public ApiResponse<HoldSeatsResponse> holdSeats(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody HoldSeatsRequest request
    ) {
        HoldSeatsResponse response = bookingService.holdSeats(principal.getUser(), request);
        return ApiResponse.success("Seats held successfully for 15 minutes", response);
    }

    /**
     * Tạo mới đơn đặt vé (Sinh mã PNR 6 ký tự).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new flight booking with 6-character PNR")
    public ApiResponse<BookingResponse> createBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse response = bookingService.createBooking(principal.getUser(), request);
        return ApiResponse.success("Booking created successfully with PNR: " + response.getPnr(), response);
    }

    /**
     * Lấy thông tin chi tiết đơn đặt vé theo ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information of a booking by ID")
    public ApiResponse<BookingResponse> getBookingById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        BookingResponse response = bookingService.getBookingById(principal.getUser(), id);
        return ApiResponse.success("Booking details retrieved successfully", response);
    }

    /**
     * Lấy danh sách đơn đặt vé của người dùng hiện tại (kèm phân trang).
     */
    @GetMapping("/my")
    @Operation(summary = "Get current user's booking history with pagination")
    public ApiResponse<PageResponse<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        Page<BookingResponse> bookings = bookingService.getMyBookings(principal.getUser(), pageable);
        return ApiResponse.success("User bookings retrieved successfully", PageResponse.of(bookings));
    }

    /**
     * Hủy đơn đặt vé đang chờ thanh toán (PENDING).
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending booking and release held seats")
    public ApiResponse<Void> cancelBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        bookingService.cancelBooking(principal.getUser(), id);
        return ApiResponse.success("Booking cancelled successfully and seats released", null);
    }
}
