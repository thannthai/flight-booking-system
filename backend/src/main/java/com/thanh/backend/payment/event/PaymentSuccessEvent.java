package com.thanh.backend.payment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentSuccessEvent extends ApplicationEvent {

    private final Long paymentId;
    private final Long bookingId;

    public PaymentSuccessEvent(Object source, Long paymentId, Long bookingId) {
        super(source);
        this.paymentId = paymentId;
        this.bookingId = bookingId;
    }
}
