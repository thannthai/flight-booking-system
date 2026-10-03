package com.thanh.backend.payment.service;

import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

public interface PayOSGatewayService {

    CreatePaymentLinkResponse createPaymentLink(CreatePaymentLinkRequest request);

    PaymentLink getPaymentLinkInformation(Long orderCode);

    PaymentLink cancelPaymentLink(Long orderCode, String cancellationReason);

    void cancelPaymentLinkAsync(Long orderCode, String cancellationReason);

    WebhookData verifyWebhook(Webhook webhook);
}
