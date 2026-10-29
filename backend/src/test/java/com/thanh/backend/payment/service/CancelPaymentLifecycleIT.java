package com.thanh.backend.payment.service;

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
class CancelPaymentLifecycleIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired BookingService bookings;
    @Autowired PaymentService payments;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookingRepository;
    @MockitoBean PayOSGatewayService gateway;
    long flightId,uid;
    int serial;
    User user, bob;

    @BeforeEach
    void fixture() {
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

    @Test
    void cancelledBookingCannotBeReopenedByOldWebhookAfterRebooking() throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        bookings.cancelBooking(user,a.getId());
        var b=book(bob,s);
        payments.processWebhookPayment(hook(p));
        assertEquals("CANCELLED",state(a.getId()));
        assertEquals("PENDING",state(b.getId()));
        assertEquals(1,active(s));
        assertEquals("CANCELLED",db.queryForObject("SELECT status FROM tickets WHERE booking_id=?",String.class,a.getId()));
        assertEquals("PAID_AFTER_TERMINAL",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
        assertEquals("REFUND_REQUIRED",db.queryForObject("SELECT refund_status FROM payments WHERE id=?",String.class,p.getId()));
        assertEquals("BOOKED",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
    }

    @Test
    void oldPaymentCancellationCannotReleaseNewBookingSeat() throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        bookings.cancelBooking(user,a.getId());
        var b=book(bob,s);
        payments.cancelPayment(p.getId(),uid,"retry cancellation");
        assertEquals("BOOKED",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
        assertEquals("PENDING",state(b.getId()));
        assertEquals(1,active(s));
        assertEquals("CANCELLED",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
    }

    @Test
    void legacyPendingPaymentCancellationDoesNotReleaseBobsHold() throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        bookings.cancelBooking(user,a.getId());
        db.update("UPDATE payments SET status='PENDING' WHERE id=?",p.getId());
        bookings.holdSeats(bob,HoldSeatsRequest.builder().seatIds(List.of(s)).build());
        String lease=redis.opsForValue().get("hold:seat:"+s);
        assertNotNull(lease);
        payments.cancelPayment(p.getId(),uid,"legacy cancellation");
        assertEquals(lease,redis.opsForValue().get("hold:seat:"+s));
        assertEquals("HELD",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
        assertEquals(bob.getId(),db.queryForObject("SELECT held_by_user FROM flight_seats WHERE id=?",Long.class,s));
        assertEquals("CANCELLED",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
    }

    @Test
    void cancellationRequiresOwnerAndDoesNotChangePersistedState() throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        User other=User.builder().role(com.thanh.backend.auth.enums.UserRole.ROLE_USER).build();
        org.springframework.test.util.ReflectionTestUtils.setField(other,"id",uid+1000);
        assertThrows(AppException.class,()->bookings.cancelBooking(other,a.getId()));
        assertThrows(AppException.class,()->payments.cancelPayment(p.getId(),other.getId(),"forbidden"));
        assertEquals("PENDING",state(a.getId()));
        assertEquals("PENDING",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
        assertEquals("BOOKED",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
        verify(gateway,never()).cancelPaymentLinkAsync(anyLong(),anyString());
    }

    @ParameterizedTest
    @CsvSource( {
        "false,false","false,true","true,false","true,true"
    })
    void cancellationAndWebhookSerializeOnBooking(boolean cancelPayment,boolean webhookFirst) throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        Webhook webhook=hook(p);
        Advised proxy=(Advised)bookingRepository;
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger winnerPid=new AtomicInteger();
        MethodInterceptor barrier=inv-> {
            Object out=inv.proceed();
            if(inv.getMethod().getName().equals("findByIdForUpdate")&&Thread.currentThread().getName().equals("lifecycle-winner")) {
                winnerPid.set(db.queryForObject("SELECT pg_backend_pid()",Integer.class));
                locked.countDown();
                assertTrue(release.await(10,TimeUnit.SECONDS));
            }
            return out;
        };
        Runnable cancel=()-> {
            if(cancelPayment)payments.cancelPayment(p.getId(),uid,"concurrent cancellation");
            else bookings.cancelBooking(user,a.getId());
        };
        Runnable notify=()->payments.processWebhookPayment(webhook);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        proxy.addAdvice(0,barrier);
        try {
            Future<Throwable> first=workers.submit(()-> {
                Thread.currentThread().setName("lifecycle-winner");
                return outcome(webhookFirst?notify:cancel);
            });
            assertTrue(locked.await(10,TimeUnit.SECONDS));
            assertNotEquals(winnerPid.get(),db.queryForObject("SELECT pg_backend_pid()",Integer.class));
            Future<Throwable> second=workers.submit(()-> {
                Thread.currentThread().setName("lifecycle-loser");
                return outcome(webhookFirst?cancel:notify);
            });
            await().atMost(Duration.ofSeconds(5)).until(()->Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))",Boolean.class,winnerPid.get())));
            release.countDown();
            assertNull(first.get(10,TimeUnit.SECONDS));
            Throwable error=second.get(10,TimeUnit.SECONDS);
            if(webhookFirst)assertInstanceOf(AppException.class,error);
            else assertNull(error);
            assertEquals(webhookFirst?"COMPLETED":"CANCELLED",state(a.getId()));
            assertEquals(webhookFirst?"SUCCESS":"PAID_AFTER_TERMINAL",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
            assertEquals(webhookFirst?"ISSUED":"CANCELLED",db.queryForObject("SELECT status FROM tickets WHERE booking_id=?",String.class,a.getId()));
            assertEquals(webhookFirst?"BOOKED":"AVAILABLE",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
            assertEquals(webhookFirst?1:0,active(s));
        }
        finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));
            proxy.removeAdvice(barrier);
        }
    }

    @Test
    void releaseGuardPreservesAnotherActiveBookingEvenWithStalePendingTicket() throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        bookings.cancelBooking(user,a.getId());
        var b=book(bob,s);
        // Legacy inconsistent state must not authorize releasing Bob's allocation.
        db.update("UPDATE booking SET status='PENDING' WHERE id=?",a.getId());
        db.update("UPDATE tickets SET status='PENDING' WHERE booking_id=?",a.getId());
        db.update("UPDATE payments SET status='PENDING' WHERE id=?",p.getId());
        payments.cancelPayment(p.getId(),uid,"stale allocation");
        assertEquals("CANCELLED",state(a.getId()));
        assertEquals("PENDING",state(b.getId()));
        assertEquals("BOOKED",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
        assertEquals(1,active(s));
    }

    @Test
    void commitFailureRollsBackCancellationAndDoesNotCancelProvider() throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        db.execute("CREATE FUNCTION reject_cancel() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='CANCELLED' THEN RAISE EXCEPTION 'test commit rejection' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        db.execute("CREATE CONSTRAINT TRIGGER reject_cancel AFTER UPDATE ON booking DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_cancel()");
        try {
            assertThrows(RuntimeException.class,()->bookings.cancelBooking(user,a.getId()));
            assertEquals("PENDING",state(a.getId()));
            assertEquals("PENDING",db.queryForObject("SELECT status FROM payments WHERE id=?",String.class,p.getId()));
            assertEquals("PENDING",db.queryForObject("SELECT status FROM tickets WHERE booking_id=?",String.class,a.getId()));
            assertEquals("BOOKED",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
            verify(gateway,never()).cancelPaymentLinkAsync(anyLong(),anyString());
        }
        finally {
            db.execute("DROP TRIGGER reject_cancel ON booking");
            db.execute("DROP FUNCTION reject_cancel()");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans= {
        false,true
    })
    void providerSubmissionFailureOccursAfterCommitAndDoesNotUndoCancellation(boolean byPayment) throws Exception {
        long s=seat("1A");
        var a=book(user,s);
        var p=payments.createPaymentLink(a.getId(),uid);
        doAnswer(i-> {
            try(var connection=dataSource.getConnection();
            var statement=connection.prepareStatement("SELECT status FROM payments WHERE order_code=?")) {
                statement.setLong(1,i.getArgument(0));
                try(var rows=statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("CANCELLED",rows.getString(1));
                }
            }
            throw new org.springframework.core.task.TaskRejectedException("test executor rejection");
        }).when(gateway).cancelPaymentLinkAsync(anyLong(),anyString());
        if(byPayment)assertDoesNotThrow(()->payments.cancelPayment(p.getId(),uid,"cancel"));
        else assertDoesNotThrow(()->bookings.cancelBooking(user,a.getId()));
        assertEquals("CANCELLED",state(a.getId()));
        assertEquals("AVAILABLE",db.queryForObject("SELECT status FROM flight_seats WHERE id=?",String.class,s));
        verify(gateway).cancelPaymentLinkAsync(eq(p.getOrderCode()),anyString());
    }
    private Throwable outcome(Runnable action) {
        try {
            action.run();
            return null;
        }
        catch(Throwable failure) {
            return failure;
        }
    }
}
