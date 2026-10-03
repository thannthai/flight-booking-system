package com.thanh.backend.payment.controller;

import com.thanh.backend.auth.security.UserPrincipal;
import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.payment.dto.request.CancelPaymentRequest;
import com.thanh.backend.payment.dto.request.CreatePaymentLinkRequest;
import com.thanh.backend.payment.dto.response.PaymentResponse;
import com.thanh.backend.payment.dto.response.PaymentStatusResponse;
import com.thanh.backend.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "APIs for payOS payment link creation, status check, and cancellation")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-link")
    @Operation(summary = "Create payOS payment checkout link for a booking")
    public ApiResponse<PaymentResponse> createPaymentLink(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePaymentLinkRequest request
    ) {
        PaymentResponse response = paymentService.createPaymentLink(request.getBookingId(), principal.getUser().getId());
        return ApiResponse.success("Payment link created successfully", response);
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment status for a booking (returns NOT_CREATED if no payment exists yet)")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long bookingId
    ) {
        PaymentStatusResponse response = paymentService.getPaymentStatusByBookingId(bookingId, principal.getUser().getId());
        return ApiResponse.success("Payment status retrieved successfully", response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an active pending payment")
    public ApiResponse<PaymentResponse> cancelPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) CancelPaymentRequest request
    ) {
        String reason = (request != null) ? request.getReason() : null;
        PaymentResponse response = paymentService.cancelPayment(id, principal.getUser().getId(), reason);
        return ApiResponse.success("Payment cancelled successfully", response);
    }
}
