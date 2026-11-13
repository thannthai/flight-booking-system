package com.thanh.backend.payment.scheduler;

import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import com.thanh.backend.payment.entity.Payment;
import com.thanh.backend.payment.enums.PaymentStatus;
import com.thanh.backend.payment.repository.PaymentRepository;
import com.thanh.backend.payment.service.PayOSGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentCleanupScheduler {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final PayOSGatewayService payOSGatewayService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Chạy mỗi 60 giây để quét các Booking PENDING đã quá hạn thanh toán + 90 giây grace period.
     */
    @Scheduled(cron = "0 * * * * *")
    public void cleanupExpiredBookings() {
        OffsetDateTime cutoffTime = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(90);

        List<Long> expiredBookingIds = bookingRepository.findExpiredPendingBookingIds(
                cutoffTime,
                PageRequest.of(0, 50)
        );

        if (expiredBookingIds.isEmpty()) {
            return;
        }

        log.info("Found {} expired booking(s) to cleanup (cutoff: {})", expiredBookingIds.size(), cutoffTime);

        for (Long bookingId : expiredBookingIds) {
            try {
                expireSingleBooking(bookingId, cutoffTime);
            } catch (Exception e) {
                log.error("Failed to expire booking id: {}, error: {}", bookingId, e.getMessage(), e);
            }
        }
    }

    private void expireSingleBooking(Long bookingId, OffsetDateTime cutoffTime) {
        Long orderCodeToCancel = transactionTemplate.execute(status -> {
            // Lock theo thứ tự toàn cục: Booking -> Payment
            Optional<Booking> bookingOpt = bookingRepository.findByIdForUpdate(bookingId);
            if (bookingOpt.isEmpty()) {
                return null;
            }

            Booking booking = bookingOpt.get();

            // Double check điều kiện
            if (booking.getStatus() != BookingStatus.PENDING || booking.getPaymentDeadline().isAfter(cutoffTime)) {
                return null;
            }

            // Lock Payment liên kết nếu có
            Optional<Payment> paymentOpt = paymentRepository.findByBookingIdAndStatus(bookingId, PaymentStatus.PENDING);
            Long orderCode = null;

            if (paymentOpt.isPresent()) {
                Payment payment = paymentRepository.findByIdForUpdate(paymentOpt.get().getId()).orElse(null);
                if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.EXPIRED);
                    paymentRepository.save(payment);
                    if ("PAYOS".equals(payment.getProvider())) orderCode = payment.getOrderCode();
                }
            }

            // Cập nhật Booking -> CANCELLED
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelReason("Payment timeout (deadline exceeded)");
            booking.setCancelledAt(now);
            bookingRepository.save(booking);

            // Giải phóng vé và ghế
            List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
            for (Ticket ticket : tickets) {
                // Conditional release (same as user cancellation): never frees a seat that
                // another active booking owns. Must run before the ticket leaves PENDING.
                if (ticket.getStatus() == TicketStatus.PENDING && ticket.getFlightSeat() != null) {
                    flightSeatRepository.releaseBookedSeatForCancellation(ticket.getFlightSeat().getId(), bookingId);
                }
                ticket.setStatus(TicketStatus.CANCELLED);
                ticketRepository.save(ticket);
            }

            log.info("Successfully expired and cancelled booking id: {}, released {} ticket/seat(s)",
                    bookingId, tickets.size());

            return orderCode;
        });

        // Sau khi commit DB, gửi request hủy link trên payOS (nếu có orderCode)
        if (orderCodeToCancel != null) {
            payOSGatewayService.cancelPaymentLinkAsync(orderCodeToCancel, "Booking payment timeout");
        }
    }
}
