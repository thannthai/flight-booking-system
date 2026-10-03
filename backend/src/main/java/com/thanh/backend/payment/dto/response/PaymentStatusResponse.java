package com.thanh.backend.payment.dto.response;

import com.thanh.backend.booking.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusResponse {
    private Long bookingId;
    private BookingStatus bookingStatus;
    private String paymentStatus;
    private PaymentResponse payment;
}
