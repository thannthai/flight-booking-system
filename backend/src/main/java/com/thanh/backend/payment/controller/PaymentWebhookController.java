package com.thanh.backend.payment.controller;

import com.thanh.backend.common.response.ApiResponse;
import com.thanh.backend.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Webhook", description = "Public Webhook endpoint for payOS payment notifications")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/webhook")
    @Operation(summary = "Handle payOS payment notification webhook")
    public ApiResponse<Map<String, Object>> handleWebhook(@RequestBody Webhook webhook) {
        log.info("Received payOS webhook request");
        paymentService.processWebhookPayment(webhook);
        return ApiResponse.success("Webhook processed successfully", Map.of("success", true));
    }
}
