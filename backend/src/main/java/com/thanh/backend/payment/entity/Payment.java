package com.thanh.backend.payment.entity;

import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.common.entity.BaseEntity;
import com.thanh.backend.payment.enums.PaymentStatus;
import com.thanh.backend.payment.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "order_code", unique = true)
    private Long orderCode;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String provider = "PAYOS";

    @Column(name = "provider_txn_id", length = 255, unique = true)
    private String providerTxnId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 10, nullable = false)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 30, nullable = false)
    @Builder.Default
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "callback_data", columnDefinition = "TEXT")
    private String callbackData;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;
}
