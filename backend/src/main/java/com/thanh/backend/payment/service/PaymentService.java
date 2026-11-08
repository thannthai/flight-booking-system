package com.thanh.backend.payment.service;

import com.thanh.backend.payment.dto.response.PaymentResponse;
import com.thanh.backend.payment.dto.response.PaymentStatusResponse;
import vn.payos.model.webhooks.Webhook;

public interface PaymentService {

    PaymentResponse createPaymentLink(Long bookingId, Long currentUserId);

    PaymentStatusResponse getPaymentStatusByBookingId(Long bookingId, Long currentUserId);

    PaymentResponse cancelPayment(Long paymentId, Long currentUserId, String cancelReason);

    PaymentResponse confirmDemoPayment(Long paymentId, Long currentUserId);

    void processWebhookPayment(Webhook webhook);
}
