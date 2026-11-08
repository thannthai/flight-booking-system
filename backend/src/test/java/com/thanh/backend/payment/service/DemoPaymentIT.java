package com.thanh.backend.payment.service;
import com.thanh.backend.payment.enums.PaymentStatus;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.service.BookingService;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.payment.dto.response.PaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.Advised;
import org.aopalliance.intercept.MethodInterceptor;
import vn.payos.model.v2.paymentRequests.*;
import vn.payos.model.webhooks.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.awaitility.Awaitility.await;
@org.springframework.test.context.ActiveProfiles({"test","demo"})
class DemoPaymentIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired BookingService bookings;
    @Autowired PaymentService payments;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookingRepository;
    @MockitoBean PayOSGatewayService gateway;
    @Autowired com.thanh.backend.payment.config.DemoPaymentConfiguration demo;
    @Autowired DemoExperienceService experience;
    @Autowired com.thanh.backend.payment.scheduler.BookingPaymentCleanupScheduler cleanup;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    long flightId,uid;
    int serial;
    User user, bob;

    @BeforeEach
    void fixture() {
        demo.setMode("DEMO"); demo.setAllowedJdbcUrl(httpEnvironmentUrl());
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
        db.update("UPDATE users SET demo_alias='A' WHERE id=?",uid);
        db.update("UPDATE users SET demo_alias='B' WHERE id=?",bobId);
        user.setDemoAlias("A");bob.setDemoAlias("B");
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

    @org.junit.jupiter.api.AfterEach void resetMode() { demo.setMode("PAYOS"); demo.setAccountsEnabled(false); }
    @Test void createAndConcurrentConfirmAreIdempotentAndIssueTickets() throws Exception {
        var booking=book(user,seat("1A"));
        var payment=payments.createPaymentLink(booking.getId(),uid);
        assertEquals("DEMO",payments.getPaymentStatusByBookingId(booking.getId(),uid).getPayment().getProvider());
        assertEquals("DEMO",payment.getProvider()); assertNull(payment.getPaymentUrl()); assertNull(payment.getQrCode());
        assertEquals(payment.getId(),payments.createPaymentLink(booking.getId(),uid).getId());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return payments.confirmDemoPayment(payment.getId(),uid);});
            var b=pool.submit(()->{start.await();return payments.confirmDemoPayment(payment.getId(),uid);});
            start.countDown();
            assertEquals(PaymentStatus.SUCCESS,a.get(15,TimeUnit.SECONDS).getStatus());
            assertEquals(PaymentStatus.SUCCESS,b.get(15,TimeUnit.SECONDS).getStatus());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
        assertEquals("COMPLETED",db.queryForObject("SELECT status FROM booking WHERE id=?",String.class,booking.getId()));
        assertEquals("ISSUED",db.queryForObject("SELECT status FROM tickets WHERE booking_id=?",String.class,booking.getId()));
        var before=db.queryForMap("SELECT * FROM payments WHERE id=?",payment.getId());
        payments.confirmDemoPayment(payment.getId(),uid);
        assertEquals(before,db.queryForMap("SELECT * FROM payments WHERE id=?",payment.getId()));
        verify(gateway,never()).createPaymentLink(any());verify(gateway,never()).getPaymentLinkInformation(any());
    }
    @Test void ownershipDeadlineAndRealProviderAreEnforced() throws Exception {
        var b=book(user,seat("1A"));var p=payments.createPaymentLink(b.getId(),uid);
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(p.getId(),bob.getId()));
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.processWebhookPayment(
                Webhook.builder().success(true).code("00").desc("Test").signature("TEST-VERIFIED-BY-MOCK")
                        .data(WebhookData.builder().orderCode(p.getOrderCode()).amount(p.getAmount().longValue())
                                .reference("TEST").description("Test").accountNumber("000000000").paymentLinkId("TEST")
                                .desc("Test").transactionDateTime("2026-11-03 10:00:00").code("00").currency("VND").build()).build()));
        db.update("UPDATE payments SET provider='PAYOS' WHERE id=?",p.getId());
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(p.getId(),uid));
        db.update("UPDATE payments SET provider='DEMO' WHERE id=?",p.getId());
        db.update("UPDATE booking SET payment_deadline=now()-interval '1 minute' WHERE id=?",b.getId());
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(p.getId(),uid));
        assertEquals("PENDING",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
        demo.setMode("PAYOS");assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(p.getId(),uid));
    }
    @Test void cancelledBookingCannotIssueOrReleaseAnotherBooking() throws Exception {
        long s=seat("1A");var a=book(user,s);var p=payments.createPaymentLink(a.getId(),uid);
        payments.cancelPayment(p.getId(),uid,"demo cancellation");
        var b=book(bob,s);
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(p.getId(),uid));
        assertEquals("BOOKED",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
        assertEquals("PENDING",db.queryForObject("SELECT status FROM booking WHERE id=?",String.class,b.getId()));
        var second=payments.createPaymentLink(b.getId(),bob.getId());
        bookings.cancelBooking(bob,b.getId());
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(second.getId(),bob.getId()));
        verify(gateway,never()).cancelPaymentLinkAsync(any(),any());
    }
    @Test void guardsBlockWritesAndAccountsAreInsertOnlyUsers() throws Exception {
        demo.setAllowedJdbcUrl("wrong");assertThrows(IllegalStateException.class,()->payments.createPaymentLink(1L,uid));
        demo.setAllowedJdbcUrl(httpEnvironmentUrl());
        db.update("UPDATE users SET demo_alias=null");
        demo.setAccountsEnabled(true);demo.setAccountPassword("test-only-demo-password");
        experience.run(null);experience.run(null);
        assertEquals(1,db.queryForObject("SELECT count(*) FROM users WHERE demo_alias IS NOT NULL AND role='ROLE_USER'",Integer.class));
        assertEquals(3,db.queryForObject("SELECT count(*) FROM users",Integer.class));
        assertEquals(200,http.getForEntity("/api/v1/demo/info",String.class).getStatusCode().value());
        assertEquals(200,http.postForEntity("/api/v1/demo/login/A",null,String.class).getStatusCode().value());
        assertTrue(http.postForEntity("/api/v1/demo/login/B",null,String.class).getStatusCode().is4xxClientError());
        assertTrue(http.postForEntity("/api/v1/payments/1/demo-confirm",null,String.class).getStatusCode().is4xxClientError());
        db.update("UPDATE users SET role='ROLE_ADMIN' WHERE demo_alias='A'");
        assertTrue(http.postForEntity("/api/v1/demo/login/A",null,String.class).getStatusCode().is4xxClientError());
        demo.setMode("PAYOS");assertTrue(http.postForEntity("/api/v1/demo/login/A",null,String.class).getStatusCode().is4xxClientError());
    }
    @Test void cancelAndConfirmSerializeToOneTerminalOutcome() throws Exception {
        long seat=seat("1A"); var booking=book(user,seat);var p=payments.createPaymentLink(booking.getId(),uid);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var confirm=pool.submit(()->{start.await();try {payments.confirmDemoPayment(p.getId(),uid);} catch(com.thanh.backend.common.exception.AppException expected) {} return true;});
            var cancel=pool.submit(()->{start.await();try {payments.cancelPayment(p.getId(),uid,"race");} catch(com.thanh.backend.common.exception.AppException expected) {} return true;});
            start.countDown();confirm.get(15,TimeUnit.SECONDS);cancel.get(15,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
        String state=db.queryForObject("SELECT status FROM booking WHERE id=?",String.class,booking.getId());
        assertTrue(state.equals("COMPLETED") || state.equals("CANCELLED"));
        assertEquals(state.equals("COMPLETED")?"SUCCESS":"CANCELLED",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
        assertEquals(state.equals("COMPLETED")?"ISSUED":"CANCELLED",db.queryForObject("SELECT status FROM tickets WHERE booking_id=?",String.class,booking.getId()));
        assertEquals(state.equals("COMPLETED")?"BOOKED":"AVAILABLE",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,seat));
        verify(gateway,never()).cancelPaymentLinkAsync(any(),any());
    }
    @Test void ordinaryAccountsUseRealGatewayEvenWithDemoExperienceEnabled() throws Exception {
        long normalId=insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('normal@example.invalid','dummy','Ordinary','0900000099','ROLE_USER')");
        User normal=users.findById(normalId).orElseThrow();
        var b=book(normal,seat("1A"));
        var p=payments.createPaymentLink(b.getId(),normalId);
        assertEquals("PAYOS",p.getProvider());
        assertNotNull(p.getQrCode());assertNotNull(p.getPaymentUrl());
        assertEquals("000000", p.getBankBin());
        assertEquals("000000000", p.getAccountNumber());
        assertEquals("TEST", p.getAccountName());
        assertEquals(p.getAmount().longValueExact(), p.getTransferAmount().longValueExact());
        assertEquals("000000000", db.queryForObject("SELECT account_number FROM payments WHERE id=?", String.class, p.getId()));
        assertEquals(p.getTransferDescription(), payments.getPaymentStatusByBookingId(b.getId(), normalId).getPayment().getTransferDescription());
        assertThrows(com.thanh.backend.common.exception.AppException.class, () -> payments.getPaymentStatusByBookingId(b.getId(), uid));

        assertEquals(p.getId(),payments.createPaymentLink(b.getId(),normalId).getId());
        verify(gateway,times(1)).createPaymentLink(any());
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(p.getId(),normalId));
        // A legacy globally-simulated record must not grant demo privileges to an ordinary user.
        db.update("UPDATE payments SET provider='DEMO' WHERE id=?",p.getId());
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.confirmDemoPayment(p.getId(),normalId));
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.createPaymentLink(b.getId(),normalId));
        assertEquals("PENDING",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
    }
    @Test void demoAccountNeverFallsBackToRealPaymentWhenFeatureIsOff() throws Exception {
        var b=book(user,seat("1A"));
        demo.setMode("PAYOS");
        assertThrows(com.thanh.backend.common.exception.AppException.class,()->payments.createPaymentLink(b.getId(),uid));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM payments WHERE booking_id=?",Integer.class,b.getId()));
        verify(gateway,never()).createPaymentLink(any());
    }
    @Test void bookingResponseDistinguishesTimeoutFromOwnerCancellation() throws Exception {
        long firstSeat = seat("1A");
        var timedOut = book(user, firstSeat);
        var payment = payments.createPaymentLink(timedOut.getId(), uid);
        db.update("UPDATE booking SET payment_deadline=now()-interval '30 seconds' WHERE id=?", timedOut.getId());
        var awaitingCleanup = bookings.getBookingById(user, timedOut.getId());
        assertEquals("PENDING", awaitingCleanup.getStatus().name());
        assertNull(awaitingCleanup.getCancellationReason());
        db.update("UPDATE booking SET payment_deadline=now()-interval '3 minutes' WHERE id=?", timedOut.getId());
        cleanup.cleanupExpiredBookings();
        var expired = bookings.getBookingById(user, timedOut.getId());
        assertEquals("CANCELLED", expired.getStatus().name());
        assertEquals("PAYMENT_TIMEOUT", json.valueToTree(expired).get("cancellationReason").asText());
        assertEquals("EXPIRED", db.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.getId()));
        assertEquals("AVAILABLE", db.queryForObject("SELECT status FROM flight_seats WHERE id=?", String.class, firstSeat));
        assertThrows(AppException.class, () -> bookings.getBookingById(bob, timedOut.getId()));
        var manual = book(user, seat("1B"));
        var manualPayment = payments.createPaymentLink(manual.getId(), uid);
        // A caller-supplied reason must never masquerade as system expiry.
        payments.cancelPayment(manualPayment.getId(), uid, "Payment timeout (deadline exceeded)");
        assertEquals("USER_CANCELLED", bookings.getBookingById(user, manual.getId()).getCancellationReason().name());
        verify(gateway, never()).cancelPaymentLinkAsync(any(), any());
    }
    String httpEnvironmentUrl() {return ((org.springframework.core.env.Environment)org.springframework.test.util.ReflectionTestUtils.getField(demo,"environment")).getProperty("spring.datasource.url");}
}
