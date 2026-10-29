package com.thanh.backend.payment.service.impl;

import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
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
import com.thanh.backend.payment.service.PayOSGatewayService;
import com.thanh.backend.payment.service.PaymentService;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final PayOSGatewayService payOSGatewayService;
    private final PayOSProperties payOSProperties;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static final DateTimeFormatter PAYOS_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private record Phase1Result(
            Payment payment,
            Booking booking,
            boolean isReconcile,
            boolean isReuse
    ) {}

    private record CreatePaymentResult(
            String checkoutUrl,
            String qrCode
    ) {}

    @Override
    public PaymentResponse createPaymentLink(Long bookingId, Long currentUserId) {
        // =========================================================================
        // Phase 1: Chuẩn bị Payment Intent trong DB transaction ngắn (~5ms)
        // =========================================================================
        Phase1Result phase1 = transactionTemplate.execute(status -> {
            // 1. Pessimistic lock Booking (Thứ tự khóa: Booking -> Payment)
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

            entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);

            // 2. Kiểm tra quyền sở hữu (IDOR)
            if (!booking.getUser().getId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED_BOOKING_ACCESS);
            }

            // 3. Kiểm tra trạng thái Booking và thời hạn thanh toán
            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new AppException(ErrorCode.BOOKING_STATUS_INVALID);
            }
            if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(booking.getPaymentDeadline())) {
                throw new AppException(ErrorCode.BOOKING_PAYMENT_EXPIRED);
            }

            // 4. Kiểm tra xem đã có Payment PENDING nào chưa (nhờ Partial Unique Index)
            Optional<Payment> activePaymentOpt = paymentRepository.findByBookingIdAndStatus(bookingId, PaymentStatus.PENDING);
            if (activePaymentOpt.isPresent()) {
                Payment activePayment = activePaymentOpt.get();
                if (activePayment.getPaymentUrl() != null) {
                    // Đã có link và còn hạn -> Tái sử dụng (Idempotency)
                    return new Phase1Result(activePayment, booking, false, true);
                }
                // Đã có bản ghi PENDING nhưng chưa kịp attach URL -> Đối soát (Reconcile)
                return new Phase1Result(activePayment, booking, true, false);
            }

            // 5. Sinh orderCode từ Sequence
            Long orderCode = paymentRepository.getNextOrderCode();

            // 6. Lưu Payment PENDING mới (paymentUrl = null)
            Payment payment = Payment.builder()
                    .booking(booking)
                    .orderCode(orderCode)
                    .amount(booking.getTotalAmount())
                    .currency("VND")
                    .provider("PAYOS")
                    .status(PaymentStatus.PENDING)
                    .refundStatus(RefundStatus.NONE)
                    .expiredAt(booking.getPaymentDeadline())
                    .build();
            Payment saved = paymentRepository.save(payment);

            return new Phase1Result(saved, booking, false, false);
        });

        // Chốt chặn an toàn: Đảm bảo bước chuẩn bị bắt buộc phải trả về dữ liệu hợp lệ thì mới được phép đi tiếp sang bước 2.
        if (phase1 == null) {
            throw new AppException(ErrorCode.INTERNAL_ERROR);
        }

        // Nếu đã có URL tái sử dụng được, trả về luôn
        if (phase1.isReuse()) {
            return paymentMapper.toResponse(phase1.payment());
        }

        // =========================================================================
        // Phase 2: Gọi cổng thanh toán payOS NGOÀI DB Transaction (500ms - 2s)
        // =========================================================================
        CreatePaymentResult result = callPayOSGateway(phase1);

        // =========================================================================
        // Phase 3: Cập nhật URL & QR trong DB transaction ngắn (~5ms)
        // =========================================================================
        return transactionTemplate.execute(status -> {
            // Lock theo đúng thứ tự toàn cục: Booking -> Payment
            Booking booking = bookingRepository.findByIdForUpdate(phase1.booking().getId()).orElseThrow();
            entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
            Payment payment = paymentRepository.findByIdForUpdate(phase1.payment().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

            entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setPaymentUrl(result.checkoutUrl());
                payment.setQrCode(result.qrCode());
                paymentRepository.save(payment);
            } else if (payment.getStatus() == PaymentStatus.SUCCESS) {
                // Webhook đã xử lý xong trước khi Phase 3 commit -> Giữ nguyên, không ghi đè URL
                log.info("Payment {} already SUCCESS via webhook before Phase 3 attached URL", payment.getId());
            } else if (payment.getStatus() == PaymentStatus.CANCELLED || payment.getStatus() == PaymentStatus.EXPIRED) {
                // Payment đã bị hủy hoặc timeout trong lúc gọi external HTTP -> Không lưu URL, gọi hủy link trên payOS
                payOSGatewayService.cancelPaymentLinkAsync(payment.getOrderCode(), "Late attachment after terminal state");
            }

            return paymentMapper.toResponse(payment);
        });
    }

    private CreatePaymentResult callPayOSGateway(Phase1Result phase1) {
        long expiredAtEpoch = phase1.booking().getPaymentDeadline().toEpochSecond();

        if (phase1.isReconcile()) {
            PaymentLink existingLink = payOSGatewayService.getPaymentLinkInformation(phase1.payment().getOrderCode());
            if (existingLink != null) {
                // Reconciliation Guard: Kiểm tra link đã hết hạn hoặc bị hủy chưa
                if (existingLink.getStatus() == PaymentLinkStatus.EXPIRED
                        || existingLink.getStatus() == PaymentLinkStatus.CANCELLED) {
                    throw new AppException(ErrorCode.PAYMENT_LINK_EXPIRED);
                }
            }
        }

        long safeAmount = payableVnd(phase1.payment().getAmount());

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(phase1.payment().getOrderCode())
                .amount(safeAmount)
                .description("Thanh toan ve " + phase1.booking().getPnr())
                .returnUrl(payOSProperties.getReturnUrl() + "?bookingId=" + phase1.booking().getId())
                .cancelUrl(payOSProperties.getCancelUrl() + "?bookingId=" + phase1.booking().getId())
                .expiredAt(expiredAtEpoch)
                .build();

        CreatePaymentLinkResponse response = payOSGatewayService.createPaymentLink(request);
        return new CreatePaymentResult(response.getCheckoutUrl(), response.getQrCode());
    }

    @Override
    public PaymentStatusResponse getPaymentStatusByBookingId(Long bookingId, Long currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        if (!booking.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_BOOKING_ACCESS);
        }

        Optional<Payment> paymentOpt = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId);
        if (paymentOpt.isEmpty()) {
            return PaymentStatusResponse.builder()
                    .bookingId(booking.getId())
                    .bookingStatus(booking.getStatus())
                    .paymentStatus("NOT_CREATED")
                    .payment(null)
                    .build();
        }

        Payment payment = paymentOpt.get();
        return PaymentStatusResponse.builder()
                .bookingId(booking.getId())
                .bookingStatus(booking.getStatus())
                .paymentStatus(payment.getStatus().name())
                .payment(paymentMapper.toResponse(payment))
                .build();
    }

    @Override
    public PaymentResponse cancelPayment(Long paymentId, Long currentUserId, String cancelReason) {
        Payment cancelledPayment = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

            // Lock theo đúng thứ tự: Booking -> Payment
            Booking booking = bookingRepository.findByIdForUpdate(payment.getBooking().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
            Payment lockedPayment = paymentRepository.findByIdForUpdate(payment.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
            entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(lockedPayment, LockModeType.PESSIMISTIC_WRITE);

            // Kiểm tra IDOR
            if (!booking.getUser().getId().equals(currentUserId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED_BOOKING_ACCESS);
            }

            // Repeated/legacy cancellation must never revisit released ticket allocations.
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                if (lockedPayment.getStatus() == PaymentStatus.PENDING) {
                    lockedPayment.setStatus(PaymentStatus.CANCELLED);
                }
                return lockedPayment;
            }
            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new AppException(ErrorCode.BOOKING_STATUS_INVALID);
            }
            // Chỉ cho phép hủy khi đang PENDING
            if (lockedPayment.getStatus() != PaymentStatus.PENDING) {
                throw new AppException(ErrorCode.PAYMENT_STATUS_INVALID);
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            // Cập nhật trạng thái Payment & Booking
            lockedPayment.setStatus(PaymentStatus.CANCELLED);
            paymentRepository.save(lockedPayment);

            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelReason(cancelReason != null ? cancelReason : "User cancelled payment");
            booking.setCancelledAt(now);
            booking.setCancelledBy(currentUserId);
            bookingRepository.save(booking);

            // Giải phóng vé và ghế
            List<Ticket> tickets = ticketRepository.findByBookingId(booking.getId());
            for (Ticket ticket : tickets) {
                if (ticket.getStatus() == TicketStatus.PENDING && ticket.getFlightSeat() != null) {
                    flightSeatRepository.releaseBookedSeatForCancellation(ticket.getFlightSeat().getId(), booking.getId());
                }
                ticket.setStatus(TicketStatus.CANCELLED);
                ticketRepository.save(ticket);
            }

            return lockedPayment;
        });

        if (cancelledPayment != null && cancelledPayment.getStatus() == PaymentStatus.CANCELLED) {
            // Sau khi commit DB, gửi request hủy link sang payOS bất đồng bộ
            try {
                payOSGatewayService.cancelPaymentLinkAsync(cancelledPayment.getOrderCode(), "User cancelled payment");
            } catch (RuntimeException error) {
                log.warn("Provider cancellation deferred, paymentId={}", cancelledPayment.getId());
            }
        }

        return paymentMapper.toResponse(cancelledPayment);
    }

    @Override
    public void processWebhookPayment(Webhook webhook) {
        // 1. Xác thực chữ ký Webhook ngoài DB transaction (fail-fast, bảo mật)
        WebhookData data = payOSGatewayService.verifyWebhook(webhook);

        // 2. Bỏ qua webhook test mẫu từ dashboard của payOS
        if (data.getOrderCode() != null && data.getOrderCode() == 123456L) {
            log.info("Received payOS test webhook successfully");
            return;
        }

        // 3. Tìm Payment theo orderCode (không lock, kiểm tra tồn tại)
        Optional<Payment> paymentOpt = paymentRepository.findByOrderCode(data.getOrderCode());
        if (paymentOpt.isEmpty()) {
            log.warn("Webhook received for unknown orderCode: {}. Acknowledged 200.", data.getOrderCode());
            return;
        }

        Payment payment = paymentOpt.get();

        // 4. Xử lý cập nhật trạng thái trong DB Transaction ngắn
        // Dùng array 1 phần tử để capture event từ lambda (effectively-final workaround)
        PaymentSuccessEvent[] successEventHolder = new PaymentSuccessEvent[1];

        transactionTemplate.executeWithoutResult(status -> {
            // Lock theo đúng thứ tự: Booking -> Payment
            Booking booking = bookingRepository.findByIdForUpdate(payment.getBooking().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
            Payment lockedPayment = paymentRepository.findByIdForUpdate(payment.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
            entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(lockedPayment, LockModeType.PESSIMISTIC_WRITE);

            // A recorded receipt/refund decision is terminal for webhook processing.
            // Replays must preserve the original reference, paidAt and refund result.
            // CANCELLED/EXPIRED with NONE may still record their first late receipt.
            if (lockedPayment.getStatus() == PaymentStatus.SUCCESS
                    || lockedPayment.getStatus() == PaymentStatus.PAID_AFTER_TERMINAL
                    || lockedPayment.getRefundStatus() != RefundStatus.NONE) {
                log.info("Payment {} already has a recorded receipt/refund result; ignoring webhook", lockedPayment.getId());
                return;
            }

            OffsetDateTime transTime = parseTransactionDateTime(data.getTransactionDateTime());

            // Quy tắc Strict: Thanh toán chỉ hợp lệ nếu transTime <= booking.paymentDeadline
            boolean isOnTime = !transTime.isAfter(booking.getPaymentDeadline());
            
            // XÁC MINH SỐ TIỀN: Bắt buộc số tiền chuyển khoản thực tế phải == số tiền cần thanh toán
            boolean isAmountValid = data.getAmount() == payableVnd(lockedPayment.getAmount());

            if (booking.getStatus() == BookingStatus.PENDING
                    && lockedPayment.getStatus() == PaymentStatus.PENDING && isOnTime && isAmountValid) {
                // Thanh toán thành công đúng hạn
                lockedPayment.setStatus(PaymentStatus.SUCCESS);
                lockedPayment.setProviderTxnId(data.getReference());
                lockedPayment.setPaidAt(transTime);
                lockedPayment.setCallbackData(webhook.getData() != null ? webhook.getData().toString() : null);
                paymentRepository.save(lockedPayment);

                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);

                // Xuất vé
                List<Ticket> tickets = ticketRepository.findByBookingId(booking.getId());
                for (Ticket ticket : tickets) {
                    ticket.setStatus(TicketStatus.ISSUED);
                    ticket.setIssuedAt(transTime);
                    ticketRepository.save(ticket);
                }

                // Chuẩn bị event để publish SAU KHI commit (tránh gửi email trước khi DB commit thành công)
                successEventHolder[0] = new PaymentSuccessEvent(this, lockedPayment.getId(), booking.getId());
                log.info("Payment SUCCESS for orderCode: {}, bookingId: {}, PNR: {}",
                        data.getOrderCode(), booking.getId(), booking.getPnr());
            } else {
                // Thanh toán trễ (transTime > paymentDeadline HOẶC đơn đã CANCELLED / EXPIRED)
                log.warn("Late payment received for orderCode: {}, bookingId: {}, transTime: {}, deadline: {}, status: {}",
                        data.getOrderCode(), booking.getId(), transTime, booking.getPaymentDeadline(), lockedPayment.getStatus());

                lockedPayment.setStatus(PaymentStatus.PAID_AFTER_TERMINAL);
                lockedPayment.setRefundStatus(RefundStatus.REFUND_REQUIRED);
                lockedPayment.setProviderTxnId(data.getReference());
                lockedPayment.setPaidAt(transTime);
                lockedPayment.setCallbackData(webhook.getData() != null ? webhook.getData().toString() : null);
                paymentRepository.save(lockedPayment);

                // Không đổi trạng thái Booking, không cấp lại ghế đã giải phóng
            }
        });

        // Publish event SAU KHI transaction đã commit thành công
        // Listener phải dùng @Async để không block webhook response thread
        if (successEventHolder[0] != null) {
            eventPublisher.publishEvent(successEventHolder[0]);
        }
    }

    // payOS accepts integer VND. Preserve the existing outbound HALF_UP contract
    // for both new and legacy decimal payment snapshots; do not change fare totals.
    private static long payableVnd(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private OffsetDateTime parseTransactionDateTime(String transactionDateTimeStr) {
        if (transactionDateTimeStr == null || transactionDateTimeStr.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(transactionDateTimeStr, PAYOS_DATE_FORMATTER);
            return localDateTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse transactionDateTime '{}', falling back to UTC now: {}",
                    transactionDateTimeStr, e.getMessage());
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
