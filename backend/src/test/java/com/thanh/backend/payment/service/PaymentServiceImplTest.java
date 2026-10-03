package com.thanh.backend.payment.service;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.SeatStatus;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import com.thanh.backend.payment.config.PayOSProperties;
import com.thanh.backend.payment.dto.response.PaymentResponse;
import com.thanh.backend.payment.dto.response.PaymentStatusResponse;
import com.thanh.backend.payment.entity.Payment;
import com.thanh.backend.payment.enums.PaymentStatus;
import com.thanh.backend.payment.enums.RefundStatus;
import com.thanh.backend.payment.event.PaymentSuccessEvent;
import com.thanh.backend.payment.mapper.PaymentMapper;
import com.thanh.backend.payment.repository.PaymentRepository;
import com.thanh.backend.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

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
    private PayOSProperties payOSProperties;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PaymentServiceImpl paymentService;

    private User testUser;
    private Booking testBooking;
    private OffsetDateTime futureDeadline;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                bookingRepository,
                paymentRepository,
                ticketRepository,
                flightSeatRepository,
                payOSGatewayService,
                payOSProperties,
                paymentMapper,
                eventPublisher,
                transactionTemplate
        );

        testUser = User.builder().email("user@example.com").build();
        ReflectionTestUtils.setField(testUser, "id", 100L);

        futureDeadline = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15);
        testBooking = Booking.builder()
                .pnr("ABC123")
                .user(testUser)
                .totalAmount(new BigDecimal("1500000"))
                .status(BookingStatus.PENDING)
                .paymentDeadline(futureDeadline)
                .build();
        ReflectionTestUtils.setField(testBooking, "id", 1L);

        // Stub transactionTemplate to execute callback directly
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        }).when(transactionTemplate).execute(any());

        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("Create Payment Link - Happy Path 3-Phase")
    void createPaymentLink_Success() {
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findByBookingIdAndStatus(1L, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.getNextOrderCode()).thenReturn(1000000001L);

        Payment initialPayment = Payment.builder()
                .booking(testBooking)
                .orderCode(1000000001L)
                .amount(testBooking.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(initialPayment, "id", 10L);

        when(paymentRepository.save(any(Payment.class))).thenReturn(initialPayment);
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(initialPayment));

        when(payOSProperties.getReturnUrl()).thenReturn("http://localhost:5173/success");
        when(payOSProperties.getCancelUrl()).thenReturn("http://localhost:5173/cancel");

        CreatePaymentLinkResponse payosResponse = CreatePaymentLinkResponse.builder()
                .bin("970415")
                .accountNumber("123456789")
                .accountName("CONG TY DU LICH")
                .amount(1500000L)
                .description("Thanh toan ve ABC123")
                .orderCode(1000000001L)
                .currency("VND")
                .paymentLinkId("link_123")
                .status(PaymentLinkStatus.PENDING)
                .checkoutUrl("https://pay.payos.vn/web/123")
                .qrCode("00020101...")
                .build();
        when(payOSGatewayService.createPaymentLink(any(CreatePaymentLinkRequest.class))).thenReturn(payosResponse);

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .id(10L)
                .bookingId(1L)
                .orderCode(1000000001L)
                .status(PaymentStatus.PENDING)
                .paymentUrl("https://pay.payos.vn/web/123")
                .build();
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(expectedResponse);

        PaymentResponse actualResponse = paymentService.createPaymentLink(1L, 100L);

        assertNotNull(actualResponse);
        assertEquals("https://pay.payos.vn/web/123", actualResponse.getPaymentUrl());
        verify(payOSGatewayService).createPaymentLink(any(CreatePaymentLinkRequest.class));
    }

    @Test
    @DisplayName("Create Payment Link - Idempotency Reuses existing URL")
    void createPaymentLink_ReuseExistingUrl() {
        Payment existingPayment = Payment.builder()
                .booking(testBooking)
                .orderCode(1000000001L)
                .status(PaymentStatus.PENDING)
                .paymentUrl("https://pay.payos.vn/web/existing")
                .build();
        ReflectionTestUtils.setField(existingPayment, "id", 10L);

        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findByBookingIdAndStatus(1L, PaymentStatus.PENDING))
                .thenReturn(Optional.of(existingPayment));

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .id(10L)
                .paymentUrl("https://pay.payos.vn/web/existing")
                .build();
        when(paymentMapper.toResponse(existingPayment)).thenReturn(expectedResponse);

        PaymentResponse actualResponse = paymentService.createPaymentLink(1L, 100L);

        assertEquals("https://pay.payos.vn/web/existing", actualResponse.getPaymentUrl());
        verify(payOSGatewayService, never()).createPaymentLink(any());
    }

    @Test
    @DisplayName("Create Payment Link - Throws UNAUTHORIZED_BOOKING_ACCESS on IDOR")
    void createPaymentLink_IdorViolation() {
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.createPaymentLink(1L, 999L)); // Another user

        assertEquals(ErrorCode.UNAUTHORIZED_BOOKING_ACCESS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Create Payment Link - Throws BOOKING_PAYMENT_EXPIRED when deadline passed")
    void createPaymentLink_DeadlineExpired() {
        testBooking.setPaymentDeadline(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));

        AppException ex = assertThrows(AppException.class, () ->
                paymentService.createPaymentLink(1L, 100L));

        assertEquals(ErrorCode.BOOKING_PAYMENT_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get Payment Status - Returns NOT_CREATED when payment does not exist")
    void getPaymentStatus_NotCreated() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        PaymentStatusResponse response = paymentService.getPaymentStatusByBookingId(1L, 100L);

        assertNotNull(response);
        assertEquals(1L, response.getBookingId());
        assertEquals("NOT_CREATED", response.getPaymentStatus());
        assertNull(response.getPayment());
    }

    @Test
    @DisplayName("Webhook - Valid on-time payment moves to SUCCESS and COMPLETED")
    void processWebhook_ValidOnTime() {
        String validTransTime = OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMinutes(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        WebhookData data = WebhookData.builder()
                .orderCode(1000000001L)
                .amount(1500000L)
                .description("Thanh toan ABC123")
                .accountNumber("123456789")
                .reference("BANK_REF_123")
                .transactionDateTime(validTransTime)
                .currency("VND")
                .paymentLinkId("link_123")
                .code("00")
                .desc("Thanh toan thanh cong")
                .build();
        Webhook webhook = Webhook.builder()
                .code("00")
                .desc("Success")
                .success(true)
                .signature("dummy_signature")
                .data(data)
                .build();

        Payment payment = Payment.builder()
                .booking(testBooking)
                .orderCode(1000000001L)
                .status(PaymentStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(payment, "id", 10L);

        Ticket ticket = Ticket.builder()
                .booking(testBooking)
                .status(TicketStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(ticket, "id", 50L);

        when(payOSGatewayService.verifyWebhook(webhook)).thenReturn(data);
        when(paymentRepository.findByOrderCode(1000000001L)).thenReturn(Optional.of(payment));
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(ticketRepository.findByBookingId(1L)).thenReturn(List.of(ticket));

        paymentService.processWebhookPayment(webhook);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(BookingStatus.COMPLETED, testBooking.getStatus());
        assertEquals(TicketStatus.ISSUED, ticket.getStatus());
        verify(eventPublisher).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("Webhook - Late payment moves to PAID_AFTER_TERMINAL and REFUND_REQUIRED")
    void processWebhook_LatePayment() {
        testBooking.setPaymentDeadline(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));

        String lateTransTime = OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        WebhookData data = WebhookData.builder()
                .orderCode(1000000001L)
                .amount(1500000L)
                .description("Thanh toan ABC123")
                .accountNumber("123456789")
                .reference("BANK_REF_999")
                .transactionDateTime(lateTransTime)
                .currency("VND")
                .paymentLinkId("link_123")
                .code("00")
                .desc("Thanh toan thanh cong")
                .build();
        Webhook webhook = Webhook.builder()
                .code("00")
                .desc("Success")
                .success(true)
                .signature("dummy_signature")
                .data(data)
                .build();

        Payment payment = Payment.builder()
                .booking(testBooking)
                .orderCode(1000000001L)
                .status(PaymentStatus.PENDING)
                .refundStatus(RefundStatus.NONE)
                .build();
        ReflectionTestUtils.setField(payment, "id", 10L);

        when(payOSGatewayService.verifyWebhook(webhook)).thenReturn(data);
        when(paymentRepository.findByOrderCode(1000000001L)).thenReturn(Optional.of(payment));
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));

        paymentService.processWebhookPayment(webhook);

        assertEquals(PaymentStatus.PAID_AFTER_TERMINAL, payment.getStatus());
        assertEquals(RefundStatus.REFUND_REQUIRED, payment.getRefundStatus());
        assertEquals(BookingStatus.PENDING, testBooking.getStatus()); // Does NOT change booking to COMPLETED
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("User Cancel - Cancels Payment, Booking, and releases FlightSeats")
    void cancelPayment_Success() {
        Payment payment = Payment.builder()
                .booking(testBooking)
                .orderCode(1000000001L)
                .status(PaymentStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(payment, "id", 10L);

        FlightSeat seat = FlightSeat.builder()
                .status(SeatStatus.BOOKED)
                .heldUntil(OffsetDateTime.now(ZoneOffset.UTC))
                .heldByUser(testUser)
                .build();
        ReflectionTestUtils.setField(seat, "id", 77L);

        Ticket ticket = Ticket.builder()
                .booking(testBooking)
                .flightSeat(seat)
                .status(TicketStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(ticket, "id", 50L);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBooking));
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(ticketRepository.findByBookingId(1L)).thenReturn(List.of(ticket));

        PaymentResponse expectedResponse = PaymentResponse.builder()
                .id(10L)
                .status(PaymentStatus.CANCELLED)
                .build();
        when(paymentMapper.toResponse(payment)).thenReturn(expectedResponse);

        PaymentResponse actualResponse = paymentService.cancelPayment(10L, 100L, "Changed travel plans");

        assertEquals(PaymentStatus.CANCELLED, actualResponse.getStatus());
        assertEquals(BookingStatus.CANCELLED, testBooking.getStatus());
        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        assertNull(seat.getHeldUntil());
        assertNull(seat.getHeldByUser());

        verify(flightSeatRepository).save(seat);
        verify(payOSGatewayService).cancelPaymentLinkAsync(1000000001L, "User cancelled payment");
    }
}
