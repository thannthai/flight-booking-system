package com.thanh.backend.payment.service.impl;

import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.payment.service.PayOSGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOSGatewayServiceImpl implements PayOSGatewayService {

    private final PayOS payOS;

    @Override
    public CreatePaymentLinkResponse createPaymentLink(CreatePaymentLinkRequest request) {
        try {
            log.info("Creating payOS payment link for orderCode: {}", request.getOrderCode());
            return payOS.paymentRequests().create(request);
        } catch (Exception e) {
            log.error("Failed to create payOS payment link for orderCode: {}, error: {}",
                    request.getOrderCode(), e.getMessage(), e);
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    @Override
    public PaymentLink getPaymentLinkInformation(Long orderCode) {
        try {
            log.info("Querying payOS payment link info for orderCode: {}", orderCode);
            return payOS.paymentRequests().get(orderCode);
        } catch (Exception e) {
            log.warn("Failed to get payOS payment link info for orderCode: {}, error: {}",
                    orderCode, e.getMessage());
            return null;
        }
    }

    @Override
    public PaymentLink cancelPaymentLink(Long orderCode, String cancellationReason) {
        try {
            log.info("Cancelling payOS payment link for orderCode: {}, reason: {}", orderCode, cancellationReason);
            return payOS.paymentRequests().cancel(orderCode, cancellationReason);
        } catch (Exception e) {
            log.warn("Failed to cancel payOS payment link for orderCode: {}, error: {}",
                    orderCode, e.getMessage());
            return null;
        }
    }

    @Override
    @Async
    public void cancelPaymentLinkAsync(Long orderCode, String cancellationReason) {
        cancelPaymentLink(orderCode, cancellationReason);
    }

    @Override
    public WebhookData verifyWebhook(Webhook webhook) {
        try {
            return payOS.webhooks().verify(webhook);
        } catch (Exception e) {
            log.error("payOS webhook signature verification failed: {}", e.getMessage());
            throw new AppException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
    }
}
