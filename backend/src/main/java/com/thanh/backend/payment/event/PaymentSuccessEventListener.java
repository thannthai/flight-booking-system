package com.thanh.backend.payment.event;

import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.notification.entity.Notification;
import com.thanh.backend.notification.enums.NotificationStatus;
import com.thanh.backend.notification.enums.NotificationType;
import com.thanh.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessEventListener {

    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;

    @Async
    @EventListener
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        log.info("Processing PaymentSuccessEvent for bookingId: {}, paymentId: {}",
                event.getBookingId(), event.getPaymentId());

        try {
            Booking booking = bookingRepository.findById(event.getBookingId()).orElse(null);
            if (booking == null) {
                log.warn("Booking not found for PaymentSuccessEvent, bookingId: {}", event.getBookingId());
                return;
            }

            // Ghi nhận lịch sử thông báo xác nhận đặt chỗ thành công
            Notification notification = Notification.builder()
                    .booking(booking)
                    .user(booking.getUser())
                    .type(NotificationType.BOOKING_CONFIRM)
                    .recipientEmail(booking.getContactEmail())
                    .status(NotificationStatus.SENT)
                    .sentAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            notificationRepository.save(notification);
            log.info("Successfully recorded BOOKING_CONFIRM notification for PNR: {}", booking.getPnr());
        } catch (Exception e) {
            log.error("Failed to handle PaymentSuccessEvent for bookingId: {}, error: {}",
                    event.getBookingId(), e.getMessage(), e);
        }
    }
}
