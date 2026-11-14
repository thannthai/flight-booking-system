package com.thanh.backend.payment.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.service.BookingService;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.payment.dto.response.PaymentResponse;
import com.thanh.backend.payment.event.PaymentSuccessEvent;
import com.thanh.backend.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.payos.model.v2.paymentRequests.*;
import vn.payos.model.webhooks.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Import(WebhookIdempotencyIT.EventConfiguration.class)
class WebhookIdempotencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired BookingService bookings;
    @Autowired PaymentService payments;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PaymentRepository paymentRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired Events events;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @LocalServerPort int port;
    @MockitoBean PayOSGatewayService gateway;
    long flightId, uid;
    int serial;
    User user, bob;

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfiguration {
        @Bean Events webhookEvents(JdbcTemplate db) { return new Events(db); }
    }
    static class Events {
        final Map<Long, Integer> counts = new ConcurrentHashMap<>();
        final JdbcTemplate db;
        Events(JdbcTemplate db) { this.db = db; }
        @EventListener
        public void success(PaymentSuccessEvent event) {
            // No ambient payment transaction here: publication follows execute/commit.
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("SUCCESS", db.queryForObject("SELECT status FROM payments WHERE id=?", String.class, event.getPaymentId()));
            counts.merge(event.getBookingId(), 1, Integer::sum);
        }
    }
    @BeforeEach
    void fixture() {
        events.counts.clear();
        when(gateway.verifyWebhook(any())).thenAnswer(i->((Webhook)i.getArgument(0)).getData());
        when(gateway.createPaymentLink(any())).thenAnswer(i-> {
            CreatePaymentLinkRequest r=i.getArgument(0);
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return CreatePaymentLinkResponse.builder().bin("000000").accountNumber("000000000").accountName("TEST").amount(r.getAmount()).description(r.getDescription()).orderCode(r.getOrderCode()).currency("VND").paymentLinkId("TEST"+r.getOrderCode()).status(PaymentLinkStatus.PENDING).checkoutUrl("https://example.invalid/pay").qrCode("TEST").build();
        });
        uid=insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('audit@example.invalid','dummy','Audit','0900000000','ROLE_USER')");
        user=users.findById(uid).orElseThrow();
        db.update("INSERT INTO airports(iata_code,name,city,country) VALUES ('CTA','Origin','Origin','Vietnam'),('CTB','Destination','Destination','Vietnam')");
        long bobId=insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('bob@example.invalid','dummy','Bob','0900000001','ROLE_USER')");
        bob=users.findById(bobId).orElseThrow();
        long al=insert("INSERT INTO airlines(code,name) VALUES ('AU','Audit')");
        long ac=insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Audit',6,6,'AUDIT-001')",al);
        flightId=insert("INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,departure_time,arrival_time,base_price,source) VALUES ('AU001',?,?,(SELECT id FROM airports WHERE iata_code='CTA'),(SELECT id FROM airports WHERE iata_code='CTB'),now()+interval '2 days',now()+interval '2 days 2 hours',100000,'MANUAL')",al,ac);
    }
    long insert(String sql,Object... args) {
        return db.queryForObject(sql+" RETURNING id",Long.class,args);
    }
    long seat(String no) {
        return insert("INSERT INTO flight_seats(flight_id,seat_no,cabin_class,status,price) VALUES (?,?,'ECONOMY','AVAILABLE',100000)",flightId,no);
    }
    BookingResponse book(User u,long s) throws Exception {
        bookings.holdSeats(u,HoldSeatsRequest.builder().seatIds(List.of(s)).build());
        String request="""
     {"tripType":"ONE_WAY","flightIds":[%d],"contactInfo":{"fullName":"Audit","email":"audit@example.invalid","phone":"0900000000"},"passengers":[{"fullName":"Audit Passenger","dateOfBirth":"1990-01-01","gender":"MALE","passengerType":"ADULT","identityType":"PASSPORT","identityNumber":"AUDIT%d"}],"seatSelections":[{"flightSeatId":%d,"flightId":%d,"passengerIndex":0}]}
      """.formatted(flightId,++serial,s,flightId);
        var parsed=json.readValue(request,CreateBookingRequest.class);
        return bookings.createBooking(u,parsed);
    }
    Webhook hook(PaymentResponse p) {
        return Webhook.builder().success(true).code("00").desc("AUDIT SUCCESS").signature("AUDIT-STUB-VERIFICATION")
        .data(WebhookData.builder().orderCode(p.getOrderCode()).amount(p.getAmount().longValue())
        .reference("AUDIT-"+p.getOrderCode()).description("AUDIT").accountNumber("000000000").paymentLinkId("AUDIT-"+p.getOrderCode()).desc("AUDIT SUCCESS").transactionDateTime(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).code("00").currency("VND").build()).build();
    }
    String state(long b) {
        return db.queryForObject("SELECT status FROM booking WHERE id=?",String.class,b);
    }
    int active(long s) {
        return db.queryForObject("SELECT count(*) FROM tickets t JOIN booking b ON b.id=t.booking_id WHERE t.flight_seat_id=? AND b.status IN ('PENDING','COMPLETED') AND t.status IN ('PENDING','ISSUED')",Integer.class,s);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentHttpDuplicatesUseFreshLockedStateAndPublishOnce(boolean cancelled) throws Exception {
        long seat = seat("1A");
        var booking = book(user, seat);
        var payment = payments.createPaymentLink(booking.getId(), uid);
        if (cancelled) payments.cancelPayment(payment.getId(), uid, "cancel");
        db.execute("CREATE TABLE webhook_writes(payment_id bigint)");
        db.execute("CREATE FUNCTION track_webhook() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN INSERT INTO webhook_writes VALUES (NEW.id); RETURN NEW; END $$");
        db.execute("CREATE TRIGGER track_webhook AFTER UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION track_webhook()");
        String expectedBefore = cancelled ? "CANCELLED" : "PENDING";
        String payload = json.writeValueAsString(hook(payment));
        CountDownLatch read = new CountDownLatch(2), release = new CountDownLatch(1);
        CountDownLatch locked = new CountDownLatch(1), finish = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger winnerPid = new java.util.concurrent.atomic.AtomicInteger();
        Set<Integer> pids = ConcurrentHashMap.newKeySet();
        Advised proxy = (Advised) paymentRepository;
        MethodInterceptor barrier = invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("findByOrderCode")) {
                assertTrue(entityManager.contains(((Optional<?>) result).orElseThrow()), "HTTP snapshot must be managed by OSIV");
                // Both HTTP persistence contexts hold a PENDING snapshot before either locks.
                assertEquals(expectedBefore, ((Optional<com.thanh.backend.payment.entity.Payment>) result).orElseThrow().getStatus().name());
                read.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return result;
        };
        Advised bookingProxy = (Advised) bookingRepository;
        MethodInterceptor locking = invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("findByIdForUpdate")) {
                int pid = db.queryForObject("SELECT pg_backend_pid()", Integer.class);
                pids.add(pid);
                if (winnerPid.compareAndSet(0, pid)) {
                    locked.countDown();
                    assertTrue(finish.await(10, TimeUnit.SECONDS));
                }
            }
            return result;
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        proxy.addAdvice(0, barrier);
        bookingProxy.addAdvice(0, locking);
        try {
            Callable<Integer> send = () -> HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/payments/webhook"))
                            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
            var first = workers.submit(send);
            var second = workers.submit(send);
            assertTrue(read.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> Boolean.TRUE.equals(
                    db.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))", Boolean.class, winnerPid.get())));
            finish.countDown();
            assertEquals(200, first.get(20, TimeUnit.SECONDS));
            assertEquals(200, second.get(20, TimeUnit.SECONDS));
            assertEquals(2, pids.size());
            assertEquals(1, db.queryForObject("SELECT count(*) FROM webhook_writes WHERE payment_id=?", Integer.class, payment.getId()));
            assertEquals(cancelled ? 0 : 1, events.counts.getOrDefault(booking.getId(), 0));
            assertEquals(cancelled ? "PAID_AFTER_TERMINAL" : "SUCCESS", db.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.getId()));
            assertEquals(cancelled ? "REFUND_REQUIRED" : "NONE", db.queryForObject("SELECT refund_status FROM payments WHERE id=?", String.class, payment.getId()));
            assertEquals(cancelled ? "CANCELLED" : "COMPLETED", state(booking.getId()));
            assertEquals(cancelled ? 0 : 1, active(seat));
            assertEquals(cancelled ? 0 : 1, db.queryForObject("SELECT count(*) FROM tickets WHERE booking_id=? AND status='ISSUED'", Integer.class, booking.getId()));
            var before = snapshot(payment.getId());
            proxy.removeAdvice(barrier);
            payments.processWebhookPayment(hook(payment));
            assertEquals(before, snapshot(payment.getId()));
            assertEquals(cancelled ? 0 : 1, events.counts.getOrDefault(booking.getId(), 0));
        } finally {
            release.countDown();
            finish.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            proxy.removeAdvice(barrier);
            bookingProxy.removeAdvice(locking);
            db.execute("DROP TRIGGER track_webhook ON payments");
            db.execute("DROP FUNCTION track_webhook()");
            db.execute("DROP TABLE webhook_writes");
        }
    }

    @ParameterizedTest
    @CsvSource({"REFUND_REQUIRED", "REFUNDED"})
    void repeatedLateCallbackPreservesRecordedPaymentAndRefund(String refund) throws Exception {
        long seat = seat("1A");
        var booking = book(user, seat);
        var payment = payments.createPaymentLink(booking.getId(), uid);
        payments.cancelPayment(payment.getId(), uid, "cancel");
        Webhook callback = hook(payment);
        payments.processWebhookPayment(callback);
        db.update("UPDATE payments SET refund_status=? WHERE id=?", refund, payment.getId());
        var before = snapshot(payment.getId());
        // Provider delivery may repeat/change metadata; recorded terminal result stays immutable.
        callback.getData().setReference("REPLAY-REFERENCE");
        payments.processWebhookPayment(callback);
        assertEquals(before, snapshot(payment.getId()));
        assertEquals("CANCELLED", state(booking.getId()));
        assertEquals("AVAILABLE", db.queryForObject("SELECT status FROM flight_seats WHERE id=?", String.class, seat));
        assertEquals("CANCELLED", db.queryForObject("SELECT status FROM tickets WHERE booking_id=?", String.class, booking.getId()));
        assertEquals(0, events.counts.getOrDefault(booking.getId(), 0));
    }

    @ParameterizedTest
    @CsvSource({"100000.49,100000", "100000.50,100001", "100000.55,100001", "100000.00,100000"})
    void callbackUsesExactlyTheIntegerVndAmountSentToProvider(String amount, long expected) throws Exception {
        long seat = seat("1A");
        var booking = book(user, seat);
        // Supported decimal pricing snapshot; retain it, round only at provider boundary.
        db.update("UPDATE booking SET total_amount=? WHERE id=?", new BigDecimal(amount), booking.getId());
        var payment = payments.createPaymentLink(booking.getId(), uid);
        var request = org.mockito.ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(gateway).createPaymentLink(request.capture());
        assertEquals(expected, request.getValue().getAmount());
        Webhook callback = hook(payment);
        callback.getData().setAmount(expected);
        payments.processWebhookPayment(callback);
        assertEquals("SUCCESS", db.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.getId()));
        assertEquals("NONE", db.queryForObject("SELECT refund_status FROM payments WHERE id=?", String.class, payment.getId()));
        assertEquals("COMPLETED", state(booking.getId()));
        assertEquals(1, events.counts.get(booking.getId()));
    }


    @ParameterizedTest
    @CsvSource({"CANCELLED", "EXPIRED"})
    void firstReceiptAfterTerminalStateRequiresRefundWithoutIssuingTickets(String status) throws Exception {
        long seat = seat("1A");
        var booking = book(user, seat);
        var payment = payments.createPaymentLink(booking.getId(), uid);
        payments.cancelPayment(payment.getId(), uid, "cancel");
        if (status.equals("EXPIRED")) {
            // Scheduler-equivalent persisted payment state; no new refund workflow.
            db.update("UPDATE payments SET status='EXPIRED' WHERE id=?", payment.getId());
        }
        payments.processWebhookPayment(hook(payment));
        assertEquals("PAID_AFTER_TERMINAL", db.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.getId()));
        assertEquals("REFUND_REQUIRED", db.queryForObject("SELECT refund_status FROM payments WHERE id=?", String.class, payment.getId()));
        assertEquals("CANCELLED", state(booking.getId()));
        assertEquals("CANCELLED", db.queryForObject("SELECT status FROM tickets WHERE booking_id=?", String.class, booking.getId()));
        assertEquals("AVAILABLE", db.queryForObject("SELECT status FROM flight_seats WHERE id=?", String.class, seat));
        assertEquals(0, events.counts.getOrDefault(booking.getId(), 0));
    }

    @ParameterizedTest
    @CsvSource({"NONE", "REFUND_REQUIRED", "REFUNDED"})
    void terminalReceiptIsImmutableIncludingLegacyRefundCombinations(String refund) throws Exception {
        long seat = seat("1A");
        var booking = book(user, seat);
        var payment = payments.createPaymentLink(booking.getId(), uid);
        payments.processWebhookPayment(hook(payment));
        // Persisted refund markers are treated as authoritative; this is fixture
        // setup, not an implementation of a refund endpoint/provider operation.
        db.update("UPDATE payments SET refund_status=? WHERE id=?", refund, payment.getId());
        var before = snapshot(payment.getId());
        Webhook replay = hook(payment);
        replay.getData().setReference("DIFFERENT-REPLAY");
        replay.getData().setAmount(1L);
        payments.processWebhookPayment(replay);
        assertEquals(before, snapshot(payment.getId()));
        assertEquals("COMPLETED", state(booking.getId()));
        assertEquals(1, events.counts.get(booking.getId()));
    }

    @Test
    void wrongAmountCannotCompleteBookingAndRepeatedCallbackCannotRewriteReceipt() throws Exception {
        long seat = seat("1A");
        var booking = book(user, seat);
        var payment = payments.createPaymentLink(booking.getId(), uid);
        Webhook callback = hook(payment);
        callback.getData().setAmount(1L);
        payments.processWebhookPayment(callback);
        assertEquals("PAID_AFTER_TERMINAL", db.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.getId()));
        assertEquals("REFUND_REQUIRED", db.queryForObject("SELECT refund_status FROM payments WHERE id=?", String.class, payment.getId()));
        assertEquals("PENDING", state(booking.getId()));
        assertEquals("PENDING", db.queryForObject("SELECT status FROM tickets WHERE booking_id=?", String.class, booking.getId()));
        var before = snapshot(payment.getId());
        payments.processWebhookPayment(hook(payment));
        assertEquals(before, snapshot(payment.getId()));
        assertEquals(0, events.counts.getOrDefault(booking.getId(), 0));
    }

    @Test
    void commitRejectionRollsBackSuccessAndPublishesNoEvent() throws Exception {
        long seat = seat("1A");
        var booking = book(user, seat);
        var payment = payments.createPaymentLink(booking.getId(), uid);
        db.execute("CREATE FUNCTION reject_webhook() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='SUCCESS' THEN RAISE EXCEPTION 'test commit rejection' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        db.execute("CREATE CONSTRAINT TRIGGER reject_webhook AFTER UPDATE ON payments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_webhook()");
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, () -> payments.processWebhookPayment(hook(payment)));
            Throwable sql = failure;
            while (sql != null && !(sql instanceof java.sql.SQLException)) sql = sql.getCause();
            assertNotNull(sql);
            assertEquals("23514", ((java.sql.SQLException) sql).getSQLState());
            assertEquals("PENDING", db.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.getId()));
            assertEquals("PENDING", state(booking.getId()));
            assertEquals("PENDING", db.queryForObject("SELECT status FROM tickets WHERE booking_id=?", String.class, booking.getId()));
            assertEquals(0, events.counts.getOrDefault(booking.getId(), 0));
        } finally {
            db.execute("DROP TRIGGER reject_webhook ON payments");
            db.execute("DROP FUNCTION reject_webhook()");
        }
        payments.processWebhookPayment(hook(payment));
        assertEquals(1, events.counts.get(booking.getId()));
    }

    private Map<String, Object> snapshot(long paymentId) {
        return db.queryForMap("SELECT status, refund_status, provider_txn_id, paid_at, callback_data, updated_at FROM payments WHERE id=?", paymentId);
    }
}
