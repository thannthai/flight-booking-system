package com.thanh.backend.payment.dto.response;

import com.thanh.backend.payment.enums.PaymentStatus;
import com.thanh.backend.payment.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long id;
    private Long bookingId;
    private Long orderCode;
    private String provider;
    private String providerTxnId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private RefundStatus refundStatus;
    private String paymentUrl;
    private String qrCode;
    private String bankBin;
    private String accountNumber;
    private String accountName;
    private String transferDescription;
    private BigDecimal transferAmount;

    private OffsetDateTime expiredAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
}
