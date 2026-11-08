package com.thanh.backend.payment.scheduler;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.SeatStatus;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import com.thanh.backend.payment.entity.Payment;
import com.thanh.backend.payment.enums.PaymentStatus;
import com.thanh.backend.payment.repository.PaymentRepository;
import com.thanh.backend.payment.service.PayOSGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingPaymentCleanupSchedulerTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private FlightSeatRepository flightSeatRepository;
    @Mock
    private PayOSGatewayService payOSGatewayService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private BookingPaymentCleanupScheduler scheduler;

    private User testUser;
    private Booking testBooking;
    private Payment testPayment;
    private FlightSeat testSeat;
    private Ticket testTicket;

    @BeforeEach
    void setUp() {
        scheduler = new BookingPaymentCleanupScheduler(
                bookingRepository,
                paymentRepository,
                ticketRepository,
                flightSeatRepository,
                payOSGatewayService,
                transactionTemplate
        );

        testUser = User.builder().email("user@example.com").build();
        ReflectionTestUtils.setField(testUser, "id", 100L);

        testBooking = Booking.builder()
                .pnr("XYZ999")
                .user(testUser)
                .totalAmount(new BigDecimal("2000000"))
                .status(BookingStatus.PENDING)
                .paymentDeadline(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2)) // Expired
                .build();
        ReflectionTestUtils.setField(testBooking, "id", 1L);

        testPayment = Payment.builder().provider("PAYOS")
                .booking(testBooking)
                .orderCode(1000000002L)
                .status(PaymentStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(testPayment, "id", 20L);

        testSeat = FlightSeat.builder()
                .status(SeatStatus.BOOKED)
                .heldUntil(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .heldByUser(testUser)
                .build();
        ReflectionTestUtils.setField(testSeat, "id", 88L);

        testTicket = Ticket.builder()
                .booking(testBooking)
                .flightSeat(testSeat)
                .status(TicketStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(testTicket, "id", 60L);

        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        }).when(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("Scheduler cleans up expired booking and releases seats")
    void cleanupExpiredBookings_Success() {
        when(bookingRepository.findExpiredPendingBookingIds(any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findByBookingIdAndStatus(1L, PaymentStatus.PENDING)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(testPayment));
        when(ticketRepository.findByBookingId(1L)).thenReturn(List.of(testTicket));

        scheduler.cleanupExpiredBookings();

        assertEquals(BookingStatus.CANCELLED, testBooking.getStatus());
        assertEquals("Payment timeout (deadline exceeded)", testBooking.getCancelReason());
        assertEquals(PaymentStatus.EXPIRED, testPayment.getStatus());
        assertEquals(TicketStatus.CANCELLED, testTicket.getStatus());
        assertEquals(SeatStatus.AVAILABLE, testSeat.getStatus());
        assertNull(testSeat.getHeldUntil());
        assertNull(testSeat.getHeldByUser());

        verify(flightSeatRepository).save(testSeat);
        verify(payOSGatewayService).cancelPaymentLinkAsync(eq(1000000002L), eq("Booking payment timeout"));
    }
}
