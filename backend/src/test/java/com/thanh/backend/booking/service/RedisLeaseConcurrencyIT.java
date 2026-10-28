package com.thanh.backend.booking.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.UserRole;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.ContactInfoRequest;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.request.SeatSelectionRequest;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.dto.response.HoldSeatsResponse;
import com.thanh.backend.booking.enums.DocumentType;
import com.thanh.backend.booking.enums.Gender;
import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.booking.enums.TripType;
import com.thanh.backend.booking.infrastructure.redis.SeatHoldLeaseStore;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.thanh.backend.flight.repository.FlightSeatRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Import(RedisLeaseConcurrencyIT.RedisTimingConfiguration.class)
class RedisLeaseConcurrencyIT extends AbstractIntegrationTest {
    @Autowired private BookingService bookings;
    @Autowired private UserRepository users;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RedisGate gate;
    @Autowired private SeatHoldLeaseStore leases;
    @Autowired private FlightSeatRepository seats;
    private User alice;
    private User bob;
    private Long seatId;
    private Long flightId;

    @BeforeEach
    void fixtures() {
        gate.handler = event -> { };
        alice = user("alice", "0910000001");
        bob = user("bob", "0910000002");
        Long origin = airport("F3A");
        Long dest = airport("F3B");
        Long airline = jdbcTemplate.queryForObject(
                "INSERT INTO airlines(code, name) VALUES ('F3', 'F03 Airline') RETURNING id", Long.class);
        Long aircraft = jdbcTemplate.queryForObject("""
                INSERT INTO aircrafts(airline_code_id, model, total_seats, economy_seats, registration_no)
                VALUES (?, 'F03 Aircraft', 1, 1, 'F03-AIRCRAFT') RETURNING id
                """, Long.class, airline);
        flightId = jdbcTemplate.queryForObject("""
                INSERT INTO flights(flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id,
                                    departure_time, arrival_time, base_price, source)
                VALUES ('F301', ?, ?, ?, ?, clock_timestamp() + INTERVAL '2 days',
                        clock_timestamp() + INTERVAL '2 days 2 hours', 1500000, 'MANUAL') RETURNING id
                """, Long.class, airline, aircraft, origin, dest);
        seatId = jdbcTemplate.queryForObject("""
                INSERT INTO flight_seats(flight_id, seat_no, cabin_class, price)
                VALUES (?, '1A', 'ECONOMY', 1500000) RETURNING id
                """, Long.class, flightId);
    }

    @ParameterizedTest(name = "Late cleanup preserves new lease; same user ABA={0}")
    @ValueSource(booleans = {false, true})
    void staleCleanup_MustNotDeleteReacquiredLease(boolean sameUser) throws Exception {
        User nextOwner = sameUser ? alice : bob;
        CountDownLatch cleanupReady = new CountDownLatch(1);
        CountDownLatch resumeCleanup = new CountDownLatch(1);
        CountDownLatch newLeaseAcquired = new CountDownLatch(1);
        AtomicBoolean once = new AtomicBoolean();
        AtomicInteger oldPid = new AtomicInteger();
        AtomicInteger newPid = new AtomicInteger();
        gate.handler = event -> {
            if (!event.key().equals(key())) return;
            if (event.operation().equals("compareDelete") && once.compareAndSet(false, true)) {
                oldPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                cleanupReady.countDown();
                boundedWait(resumeCleanup);
            }
            if (Thread.currentThread().getName().equals("f03-new-holder")
                    && event.operation().equals("setIfAbsent") && Boolean.TRUE.equals(event.result())) {
                newPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                newLeaseAcquired.countDown();
            }
        };
        ExecutorService oldWorker = worker("f03-cleanup");
        ExecutorService newWorker = worker("f03-new-holder");
        try {
            // Seat1 DB update is real but uncommitted; missing seat2 causes actual batch exception cleanup.
            Future<Outcome<HoldSeatsResponse>> oldRequest = oldWorker.submit(() -> capture(() -> bookings.holdSeats(
                    alice, HoldSeatsRequest.builder().seatIds(List.of(seatId, -1L)).build())));
            assertTrue(cleanupReady.await(10, TimeUnit.SECONDS));
            String oldValue = redis.opsForValue().get(key());
            assertNotNull(oldValue);
            expireRedisLease();

            Future<Outcome<HoldSeatsResponse>> nextRequest = newWorker.submit(() -> capture(() -> hold(nextOwner)));
            assertTrue(newLeaseAcquired.await(10, TimeUnit.SECONDS));
            String newValue = redis.opsForValue().get(key());
            assertNotNull(newValue);
            assertNotEquals(oldPid.get(), newPid.get());
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT wait_event_type = 'Lock' AND ? = ANY(pg_blocking_pids(pid))
                    FROM pg_stat_activity WHERE pid = ?
                    """, Boolean.class, oldPid.get(), newPid.get())));
            resumeCleanup.countDown();
            assertError(oldRequest.get(10, TimeUnit.SECONDS), ErrorCode.SEAT_NOT_AVAILABLE);
            assertSuccess(nextRequest.get(10, TimeUnit.SECONDS));
            System.out.println("F03 cleanup sameUser=" + sameUser + ": lease preserved="
                    + newValue.equals(redis.opsForValue().get(key())));
            assertAll(
                    () -> assertNotEquals(oldValue, newValue, "Every reacquisition needs a new lease identity"),
                    () -> assertEquals(newValue, redis.opsForValue().get(key()), "Late cleanup must preserve new lease"),
                    () -> assertTrue(redis.getExpire(key(), TimeUnit.MILLISECONDS) > 0),
                    () -> assertHeldBy(nextOwner), this::assertNoBookingGraph);
        } finally {
            resumeCleanup.countDown();
            stop(oldWorker, newWorker);
            gate.handler = event -> { };
        }
    }

    @ParameterizedTest(name = "Late renew must not extend new lease; same user ABA={0}")
    @ValueSource(booleans = {false, true})
    void staleRenew_MustNotExtendOrOverwriteReacquiredLease(boolean sameUser) throws Exception {
        hold(alice);
        User nextOwner = sameUser ? alice : bob;
        CountDownLatch readOldLease = new CountDownLatch(1);
        CountDownLatch resumeRenew = new CountDownLatch(1);
        pauseRenewAfterRead(readOldLease, resumeRenew);
        ExecutorService executor = worker("f03-renew");
        try {
            Future<Outcome<HoldSeatsResponse>> oldRequest = executor.submit(() -> capture(() -> hold(alice)));
            assertTrue(readOldLease.await(10, TimeUnit.SECONDS));
            String oldValue = redis.opsForValue().get(key());
            jdbcTemplate.update("""
                    UPDATE flight_seats SET held_until = clock_timestamp() + INTERVAL '1 second' WHERE id = ?
                    """, seatId);
            expireRedisLease();
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT held_until < clock_timestamp() FROM flight_seats WHERE id = ?", Boolean.class, seatId)));
            hold(nextOwner);
            String newValue = redis.opsForValue().get(key());
            Map<String, Object> newDbHold = seatState();
            // Short TTL is a test probe only, making an unauthorized EXPIRE(900s) unambiguous.
            assertEquals(Boolean.TRUE, redis.expire(key(), Duration.ofSeconds(30)));
            long ttlBefore = redis.getExpire(key(), TimeUnit.MILLISECONDS);
            resumeRenew.countDown();
            Outcome<HoldSeatsResponse> result = oldRequest.get(10, TimeUnit.SECONDS);
            long ttlAfter = redis.getExpire(key(), TimeUnit.MILLISECONDS);
            System.out.println("F03 renew sameUser=" + sameUser + ": TTL before=" + ttlBefore + ", after=" + ttlAfter);
            assertAll(
                    () -> assertError(result, ErrorCode.SEAT_HELD),
                    () -> assertNotEquals(oldValue, newValue),
                    () -> assertEquals(newValue, redis.opsForValue().get(key())),
                    () -> assertTrue(ttlAfter > 0 && ttlAfter <= ttlBefore, "Old request must not extend new TTL"),
                    () -> assertEquals(newDbHold, seatState(), "Stale renewal must not overwrite new DB hold"),
                    () -> assertHeldBy(nextOwner), this::assertNoBookingGraph);
        } finally {
            resumeRenew.countDown();
            stop(executor);
            gate.handler = event -> { };
        }
    }

    @Test
    void renewalWhoseKeyExpiresAfterRead_MustFailWithoutResurrectingKey() throws Exception {
        hold(alice);
        Map<String, Object> originalDbHold = seatState();
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseRenewAfterRead(read, resume);
        ExecutorService executor = worker("f03-renew");
        try {
            Future<Outcome<HoldSeatsResponse>> request = executor.submit(() -> capture(() -> hold(alice)));
            assertTrue(read.await(10, TimeUnit.SECONDS));
            expireRedisLease();
            resume.countDown();
            Outcome<HoldSeatsResponse> result = request.get(10, TimeUnit.SECONDS);
            assertAll(() -> assertError(result, ErrorCode.SEAT_HELD),
                    () -> assertNull(redis.opsForValue().get(key())),
                    () -> assertEquals(originalDbHold, seatState()), this::assertNoBookingGraph);
        } finally {
            resume.countDown();
            stop(executor);
            gate.handler = event -> { };
        }
    }

    @Test
    void newAcquisitionHasUniqueToken_ReuseRetainsToken_ReacquisitionChangesToken() {
        var first = leases.acquireOrReuse(seatId, alice.getId(), Duration.ofSeconds(30), null).orElseThrow().lease();
        String firstValue = redis.opsForValue().get(key());
        SeatHoldLeaseAssertions.assertLeaseOwnedBy(firstValue, alice.getId());
        assertTrue(leases.readDeadline(first, alice.getId()).isPresent());
        assertEquals(first, leases.acquireOrReuse(seatId, alice.getId(), Duration.ofSeconds(30), null).orElseThrow().lease());
        assertEquals(firstValue, redis.opsForValue().get(key()));
        expireRedisLease();
        var second = leases.acquireOrReuse(seatId, alice.getId(), Duration.ofSeconds(30), null).orElseThrow().lease();
        String secondValue = redis.opsForValue().get(key());
        assertNotEquals(first.token(), second.token());
        assertNotEquals(firstValue, secondValue);
        long ttlBefore = redis.getExpire(key(), TimeUnit.MILLISECONDS);
        assertFalse(leases.release(first, alice.getId()));
        assertFalse(leases.readDeadline(first, alice.getId()).isPresent());
        assertEquals(secondValue, redis.opsForValue().get(key()));
        assertTrue(redis.getExpire(key(), TimeUnit.MILLISECONDS) <= ttlBefore);
    }

    @Test
    void wrongAuthenticatedActorOrToken_CannotReleaseOrReadDeadline() {
        var lease = leases.acquireOrReuse(seatId, bob.getId(), Duration.ofSeconds(30), null).orElseThrow().lease();
        String value = redis.opsForValue().get(key());
        long before = redis.getExpire(key(), TimeUnit.MILLISECONDS);
        assertTrue(leases.readOwned(seatId, alice.getId()).isEmpty());
        assertFalse(leases.release(lease, alice.getId()));
        assertFalse(leases.readDeadline(lease, alice.getId()).isPresent());
        var forged = new SeatHoldLeaseStore.Lease(seatId, bob.getId(), UUID.randomUUID());
        assertFalse(leases.release(forged, bob.getId()));
        assertFalse(leases.readDeadline(forged, bob.getId()).isPresent());
        assertEquals(value, redis.opsForValue().get(key()));
        assertTrue(redis.getExpire(key(), TimeUnit.MILLISECONDS) <= before);
        assertEquals("AVAILABLE", seatState().get("status")); // Redis identity alone never changes PostgreSQL.
        assertNoBookingGraph();
    }

    @Test
    void missingOrExpiredLease_ReleaseAndReadNeverResurrectOrMutateNewKey() {
        var first = leases.acquireOrReuse(seatId, alice.getId(), Duration.ofSeconds(30), null).orElseThrow().lease();
        assertTrue(leases.release(first, alice.getId()));
        assertFalse(leases.release(first, alice.getId()));
        assertFalse(leases.readDeadline(first, alice.getId()).isPresent());
        assertNull(redis.opsForValue().get(key()));
        var expired = leases.acquireOrReuse(seatId, alice.getId(), Duration.ofSeconds(30), null).orElseThrow().lease();
        expireRedisLease();
        assertFalse(leases.release(expired, alice.getId()));
        assertFalse(leases.readDeadline(expired, alice.getId()).isPresent());
        assertEquals(-2L, redis.getExpire(key(), TimeUnit.MILLISECONDS));
        var current = leases.acquireOrReuse(seatId, bob.getId(), Duration.ofSeconds(30), null).orElseThrow().lease();
        assertFalse(leases.release(expired, alice.getId()));
        assertFalse(leases.readDeadline(expired, alice.getId()).isPresent());
        assertEquals(current, leases.readOwned(seatId, bob.getId()).orElseThrow());
    }

    @Test
    void legacyAndMalformedValues_FailClosedWithoutMutation() {
        for (String value : List.of(alice.getId().toString(), alice.getId() + ":invalid-token", "invalid-owner")) {
            redis.opsForValue().set(key(), value, Duration.ofSeconds(30));
            long before = redis.getExpire(key(), TimeUnit.MILLISECONDS);
            assertTrue(leases.readOwned(seatId, alice.getId()).isEmpty());
            assertTrue(leases.acquireOrReuse(seatId, alice.getId(), Duration.ofSeconds(900), null).isEmpty());
            assertFalse(leases.release(new SeatHoldLeaseStore.Lease(seatId, alice.getId(), UUID.randomUUID()), alice.getId()));
            assertEquals(value, redis.opsForValue().get(key()));
            assertTrue(redis.getExpire(key(), TimeUnit.MILLISECONDS) <= before);
        }
    }

    @Test
    void bookingMustConsumeValidatedLease_NotNewSameUserLease_AndRollBackOnMismatch() throws Exception {
        hold(alice);
        CountDownLatch consuming = new CountDownLatch(1);
        CountDownLatch resumeConsume = new CountDownLatch(1);
        CountDownLatch reacquired = new CountDownLatch(1);
        AtomicBoolean once = new AtomicBoolean();
        gate.handler = event -> {
            if (!event.key().equals(key())) return;
            if (Thread.currentThread().getName().equals("f03-consume")
                    && event.operation().equals("compareDeadline") && once.compareAndSet(false, true)) {
                consuming.countDown();
                boundedWait(resumeConsume);
            }
            if (Thread.currentThread().getName().equals("f03-new-holder")
                    && event.operation().equals("setIfAbsent") && Boolean.TRUE.equals(event.result())) {
                reacquired.countDown();
            }
        };
        ExecutorService oldWorker = worker("f03-consume");
        ExecutorService newWorker = worker("f03-new-holder");
        try {
            Future<Outcome<BookingResponse>> oldBooking = oldWorker.submit(() -> capture(
                    () -> bookings.createBooking(alice, bookingRequest())));
            assertTrue(consuming.await(10, TimeUnit.SECONDS));
            String oldValue = redis.opsForValue().get(key());
            expireRedisLease();
            Future<Outcome<HoldSeatsResponse>> newHold = newWorker.submit(() -> capture(() -> hold(alice)));
            assertTrue(reacquired.await(10, TimeUnit.SECONDS));
            String newValue = redis.opsForValue().get(key());
            assertNotEquals(oldValue, newValue);
            resumeConsume.countDown();
            assertError(oldBooking.get(10, TimeUnit.SECONDS), ErrorCode.SEAT_NOT_AVAILABLE);
            assertSuccess(newHold.get(10, TimeUnit.SECONDS));
            assertEquals(newValue, redis.opsForValue().get(key()));
            assertHeldBy(alice);
            assertNoBookingGraph();
        } finally {
            resumeConsume.countDown();
            stop(oldWorker, newWorker);
            gate.handler = event -> { };
        }
    }

    @Test
    void cancellationHasNoAuthorityToDeleteLeaseOfNewHolder() throws Exception {
        CountDownLatch oldRead = new CountDownLatch(1);
        CountDownLatch resumeRead = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch resumeAcquisition = new CountDownLatch(1);
        AtomicBoolean readOnce = new AtomicBoolean();
        MethodInterceptor pauseRead = invocation -> {
            Object value = invocation.proceed();
            if (Thread.currentThread().getName().equals("f03-cancel-holder")
                    && invocation.getMethod().getName().equals("findById")
                    && invocation.getArguments()[0].equals(seatId) && readOnce.compareAndSet(false, true)) {
                oldRead.countDown();
                boundedWait(resumeRead);
            }
            return value;
        };
        gate.handler = event -> {
            if (Thread.currentThread().getName().equals("f03-cancel-holder") && event.key().equals(key())
                    && event.operation().equals("setIfAbsent") && Boolean.TRUE.equals(event.result())) {
                acquired.countDown();
                boundedWait(resumeAcquisition);
            }
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        proxy.addAdvice(0, pauseRead);
        ExecutorService executor = worker("f03-cancel-holder");
        try {
            Future<Outcome<HoldSeatsResponse>> newHold = executor.submit(() -> capture(() -> hold(bob)));
            assertTrue(oldRead.await(10, TimeUnit.SECONDS));
            hold(alice);
            BookingResponse booking = bookings.createBooking(alice, bookingRequest());
            assertNull(redis.opsForValue().get(key()), "Successful booking must consume its original lease");
            resumeRead.countDown();
            assertTrue(acquired.await(10, TimeUnit.SECONDS));
            String newValue = redis.opsForValue().get(key());
            SeatHoldLeaseAssertions.assertLeaseOwnedBy(newValue, bob.getId());
            bookings.cancelBooking(alice, booking.getId());
            assertEquals(newValue, redis.opsForValue().get(key()));
            resumeAcquisition.countDown();
            assertSuccess(newHold.get(10, TimeUnit.SECONDS));
            assertHeldBy(bob);
            assertEquals(newValue, redis.opsForValue().get(key()));
            assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                    "SELECT status FROM booking WHERE id = ?", String.class, booking.getId()));
            assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                    "SELECT status FROM tickets WHERE booking_id = ?", String.class, booking.getId()));
        } finally {
            resumeRead.countDown();
            resumeAcquisition.countDown();
            try { stop(executor); }
            finally { proxy.removeAdvice(pauseRead); gate.handler = event -> { }; }
        }
    }

    @Test
    void fixedDeadline_initialAcquireUsesExactlyOneDeadline() {
        Instant before = redisTime();
        HoldSeatsResponse response = hold(alice);
        Instant after = redisTime();
        Instant deadline = response.getExpiresAt().toInstant();
        assertAll(
                () -> assertEquals(deadline, dbDeadline()),
                () -> assertEquals(deadline.toEpochMilli(), redisDeadline()),
                () -> assertEquals(0, deadline.getNano() % 1_000_000, "Redis millisecond precision"),
                () -> assertFalse(deadline.isBefore(before.plusSeconds(900))),
                () -> assertFalse(deadline.isAfter(after.plusSeconds(900))),
                () -> assertEquals(900, response.getTtlSeconds()));
    }

    @Test
    void fixedDeadline_retryPreservesDeadlineIdentityAndRemainingTtl() {
        hold(alice);
        Instant deadline = shortenHold(Duration.ofSeconds(60));
        String identity = redis.opsForValue().get(key());
        long ttlBefore = redis.getExpire(key(), TimeUnit.MILLISECONDS);
        HoldSeatsResponse response = hold(alice);
        assertAll(
                () -> assertEquals(deadline, response.getExpiresAt().toInstant()),
                () -> assertEquals(deadline, dbDeadline()),
                () -> assertEquals(deadline.toEpochMilli(), redisDeadline()),
                () -> assertEquals(identity, redis.opsForValue().get(key())),
                () -> assertTrue(redis.getExpire(key(), TimeUnit.MILLISECONDS) <= ttlBefore),
                () -> assertTrue(response.getTtlSeconds() > 0 && response.getTtlSeconds() <= 60),
                this::assertNoBookingGraph);
    }

    @Test
    void fixedDeadline_failedRetryBatchPreservesExistingLease() {
        HoldSeatsResponse first = hold(alice);
        String identity = redis.opsForValue().get(key());
        long deadline = redisDeadline();
        AppException error = assertThrows(AppException.class, () -> bookings.holdSeats(alice,
                HoldSeatsRequest.builder().seatIds(List.of(seatId, -1L)).build()));
        assertAll(
                () -> assertEquals(ErrorCode.SEAT_NOT_AVAILABLE, error.getErrorCode()),
                () -> assertEquals(identity, redis.opsForValue().get(key())),
                () -> assertEquals(deadline, redisDeadline()),
                () -> assertEquals(first.getExpiresAt().toInstant(), dbDeadline()),
                this::assertNoBookingGraph);
    }

    @Test
    void fixedDeadline_mixedBatchReturnsEarliestDeadlineWithoutExtendingOldSeat() {
        hold(alice);
        Instant deadline = shortenHold(Duration.ofSeconds(60));
        Long secondSeat = jdbcTemplate.queryForObject("""
                INSERT INTO flight_seats(flight_id, seat_no, cabin_class, price)
                VALUES (?, '1B', 'ECONOMY', 1500000) RETURNING id
                """, Long.class, flightId);
        HoldSeatsResponse response = bookings.holdSeats(alice,
                HoldSeatsRequest.builder().seatIds(List.of(secondSeat, seatId)).build());
        Instant secondDeadline = jdbcTemplate.queryForObject(
                "SELECT held_until FROM flight_seats WHERE id = ?", OffsetDateTime.class, secondSeat).toInstant();
        assertAll(
                () -> assertEquals(List.of(secondSeat, seatId), response.getHeldSeats()),
                () -> assertEquals(deadline, response.getExpiresAt().toInstant()),
                () -> assertEquals(deadline, dbDeadline()),
                () -> assertEquals(deadline.toEpochMilli(), redisDeadline()),
                () -> assertTrue(secondDeadline.isAfter(deadline)),
                () -> assertEquals(secondDeadline.toEpochMilli(), redisDeadline(secondSeat)),
                () -> assertTrue(response.getTtlSeconds() > 0 && response.getTtlSeconds() <= 60));
    }

    @Test
    void fixedDeadline_missingRedisDoesNotExtendPersistedLiveHold() {
        hold(alice);
        Instant deadline = shortenHold(Duration.ofSeconds(60));
        String oldIdentity = redis.opsForValue().get(key());
        assertTrue(Boolean.TRUE.equals(redis.delete(key()))); // Simulate lost Redis key, DB still live.
        HoldSeatsResponse response = hold(alice);
        assertAll(
                () -> assertEquals(deadline, response.getExpiresAt().toInstant()),
                () -> assertEquals(deadline, dbDeadline()),
                () -> assertEquals(deadline.toEpochMilli(), redisDeadline()),
                () -> assertNotEquals(oldIdentity, redis.opsForValue().get(key())));
    }

    @Test
    void fixedDeadline_dbLockOutlivesLeaseRejectsRetry() throws Exception {
        hold(alice);
        Instant deadline = shortenHold(Duration.ofSeconds(3));
        AtomicInteger retryPid = new AtomicInteger();
        CountDownLatch enteringCas = new CountDownLatch(1);
        MethodInterceptor observe = invocation -> {
            if (Thread.currentThread().getName().equals("fixed-deadline-waiter")
                    && invocation.getMethod().getName().equals("acquireHoldIfAvailable")) {
                retryPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                enteringCas.countDown();
            }
            return invocation.proceed();
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        ExecutorService executor = worker("fixed-deadline-waiter");
        proxy.addAdvice(0, observe);
        try (Connection blocker = jdbcTemplate.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            int blockerPid;
            try (var statement = blocker.createStatement(); var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                assertTrue(result.next());
                blockerPid = result.getInt(1);
            }
            try (var statement = blocker.prepareStatement("SELECT id FROM flight_seats WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, seatId);
                try (var result = statement.executeQuery()) { assertTrue(result.next()); }
            }
            Future<Outcome<HoldSeatsResponse>> retry = executor.submit(() -> capture(() -> hold(alice)));
            assertTrue(enteringCas.await(10, TimeUnit.SECONDS));
            assertNotEquals(blockerPid, retryPid.get());
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT wait_event_type = 'Lock' AND ? = ANY(pg_blocking_pids(pid))
                    FROM pg_stat_activity WHERE pid = ?
                    """, Boolean.class, blockerPid, retryPid.get())));
            // Wait on real wall clocks. No Redis/DB mutation after the retry has started.
            await().atMost(Duration.ofSeconds(10)).until(() -> Instant.now().isAfter(deadline));
            blocker.commit();
            Outcome<HoldSeatsResponse> result = retry.get(10, TimeUnit.SECONDS);
            assertAll(() -> assertError(result, ErrorCode.SEAT_HELD),
                    () -> assertEquals(deadline, dbDeadline()),
                    () -> assertNull(redis.opsForValue().get(key())), this::assertNoBookingGraph);
        } finally {
            try { stop(executor); }
            finally { proxy.removeAdvice(observe); }
        }
    }

    @Test
    void fixedDeadline_concurrentSameLeaseRetriesPreserveDeadline() throws Exception {
        hold(alice);
        Instant deadline = shortenHold(Duration.ofSeconds(60));
        String identity = redis.opsForValue().get(key());
        CountDownLatch atCas = new CountDownLatch(2);
        CountDownLatch resume = new CountDownLatch(1);
        var pids = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        MethodInterceptor pause = invocation -> {
            if (Thread.currentThread().getName().startsWith("fixed-deadline-retry")
                    && invocation.getMethod().getName().equals("acquireHoldIfAvailable")) {
                pids.add(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                atCas.countDown();
                boundedWait(resume);
            }
            return invocation.proceed();
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        proxy.addAdvice(0, pause);
        ExecutorService first = worker("fixed-deadline-retry-1");
        ExecutorService second = worker("fixed-deadline-retry-2");
        try {
            Future<Outcome<HoldSeatsResponse>> a = first.submit(() -> capture(() -> hold(alice)));
            Future<Outcome<HoldSeatsResponse>> b = second.submit(() -> capture(() -> hold(alice)));
            assertTrue(atCas.await(10, TimeUnit.SECONDS));
            assertEquals(2, pids.size());
            resume.countDown();
            var resultA = a.get(10, TimeUnit.SECONDS);
            var resultB = b.get(10, TimeUnit.SECONDS);
            assertSuccess(resultA);
            assertSuccess(resultB);
            assertAll(() -> assertEquals(deadline, resultA.value().getExpiresAt().toInstant()),
                    () -> assertEquals(deadline, resultB.value().getExpiresAt().toInstant()),
                    () -> assertEquals(deadline, dbDeadline()),
                    () -> assertEquals(deadline.toEpochMilli(), redisDeadline()),
                    () -> assertEquals(identity, redis.opsForValue().get(key())));
        } finally {
            resume.countDown();
            try { stop(first, second); }
            finally { proxy.removeAdvice(pause); }
        }
    }

    @Test
    void fixedDeadline_retryPausedBeforeCasCannotOverwriteNewSameUserGeneration() throws Exception {
        hold(alice);
        CountDownLatch atCas = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        MethodInterceptor pause = invocation -> {
            if (Thread.currentThread().getName().equals("fixed-deadline-old-retry")
                    && invocation.getMethod().getName().equals("acquireHoldIfAvailable")) {
                atCas.countDown();
                boundedWait(resume);
            }
            return invocation.proceed();
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        proxy.addAdvice(0, pause);
        ExecutorService executor = worker("fixed-deadline-old-retry");
        try {
            Future<Outcome<HoldSeatsResponse>> retry = executor.submit(() -> capture(() -> hold(alice)));
            assertTrue(atCas.await(10, TimeUnit.SECONDS));
            shortenHold(Duration.ofSeconds(1));
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key()))
                    && Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT held_until < clock_timestamp() FROM flight_seats WHERE id = ?", Boolean.class, seatId)));
            HoldSeatsResponse next = hold(alice);
            String identity = redis.opsForValue().get(key());
            Map<String, Object> state = seatState();
            resume.countDown();
            Outcome<HoldSeatsResponse> result = retry.get(10, TimeUnit.SECONDS);
            assertAll(() -> assertError(result, ErrorCode.SEAT_HELD),
                    () -> assertEquals(state, seatState()),
                    () -> assertEquals(identity, redis.opsForValue().get(key())),
                    () -> assertEquals(next.getExpiresAt().toInstant().toEpochMilli(), redisDeadline()),
                    this::assertNoBookingGraph);
        } finally {
            resume.countDown();
            try { stop(executor); }
            finally { proxy.removeAdvice(pause); }
        }
    }

    @Test
    void fixedDeadline_retryAfterExpiryStartsNewLease_PaymentGetsSeparateFifteenMinutes() {
        hold(alice);
        String oldIdentity = redis.opsForValue().get(key());
        Instant oldDeadline = shortenHold(Duration.ofSeconds(1));
        await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key()))
                && Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT held_until < clock_timestamp() FROM flight_seats WHERE id = ?", Boolean.class, seatId)));
        Instant beforeReacquire = redisTime();
        HoldSeatsResponse next = hold(alice);
        Instant afterReacquire = redisTime();
        assertNotEquals(oldIdentity, redis.opsForValue().get(key()));
        assertTrue(next.getExpiresAt().toInstant().isAfter(oldDeadline));
        assertFalse(next.getExpiresAt().toInstant().isBefore(beforeReacquire.plusSeconds(900)));
        assertFalse(next.getExpiresAt().toInstant().isAfter(afterReacquire.plusSeconds(900)));
        assertEquals(next.getExpiresAt().toInstant(), dbDeadline());
        assertEquals(next.getExpiresAt().toInstant().toEpochMilli(), redisDeadline());

        Instant holdDeadline = shortenHold(Duration.ofSeconds(60));
        assertEquals(holdDeadline, hold(alice).getExpiresAt().toInstant());
        Instant beforeBooking = Instant.now();
        BookingResponse booking = bookings.createBooking(alice, bookingRequest());
        Instant afterBooking = Instant.now();
        Instant paymentDeadline = jdbcTemplate.queryForObject(
                "SELECT payment_deadline FROM booking WHERE id = ?", OffsetDateTime.class, booking.getId()).toInstant();
        assertAll(
                () -> assertFalse(paymentDeadline.isBefore(beforeBooking.plusSeconds(900).minusMillis(1))),
                () -> assertFalse(paymentDeadline.isAfter(afterBooking.plusSeconds(900))),
                () -> assertTrue(paymentDeadline.isAfter(holdDeadline.plusSeconds(800))),
                () -> assertEquals("BOOKED", seatState().get("status")),
                () -> assertNull(seatState().get("held_until")),
                () -> assertNull(redis.opsForValue().get(key())),
                () -> assertEquals(1L, jdbcTemplate.queryForObject("SELECT count(*) FROM booking", Long.class)),
                () -> assertEquals(1L, jdbcTemplate.queryForObject("SELECT count(*) FROM tickets", Long.class)));
    }

    @Test
    void fixedDeadline_inconsistentLiveDeadlinesFailClosedWithoutMutation() {
        hold(alice);
        Map<String, Object> before = seatState();
        String identity = redis.opsForValue().get(key());
        // Model a pre-existing desynchronization: never silently extend either deadline to repair it.
        assertTrue(Boolean.TRUE.equals(redis.expire(key(), Duration.ofSeconds(30))));
        long deadline = redisDeadline();
        AppException error = assertThrows(AppException.class, () -> hold(alice));
        assertAll(() -> assertEquals(ErrorCode.SEAT_HELD, error.getErrorCode()),
                () -> assertEquals(before, seatState()),
                () -> assertEquals(identity, redis.opsForValue().get(key())),
                () -> assertEquals(deadline, redisDeadline()), this::assertNoBookingGraph);
    }

    @Test
    void fixedDeadline_oldRetryCannotAdoptNewTokenEvenWithIdenticalDeadline() throws Exception {
        HoldSeatsResponse first = hold(alice);
        String oldIdentity = redis.opsForValue().get(key());
        CountDownLatch atCas = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicInteger affectedRows = new AtomicInteger(-1);
        MethodInterceptor pause = invocation -> {
            if (Thread.currentThread().getName().equals("fixed-deadline-aba")
                    && invocation.getMethod().getName().equals("acquireHoldIfAvailable")) {
                atCas.countDown();
                boundedWait(resume);
                Object actual = invocation.proceed();
                affectedRows.set((Integer) actual);
                return actual;
            }
            return invocation.proceed();
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        proxy.addAdvice(0, pause);
        ExecutorService executor = worker("fixed-deadline-aba");
        try {
            Future<Outcome<HoldSeatsResponse>> retry = executor.submit(() -> capture(() -> hold(alice)));
            assertTrue(atCas.await(10, TimeUnit.SECONDS));
            expireRedisLease(); // DB deadline remains live; the next generation must preserve it.
            HoldSeatsResponse next = hold(alice);
            String identity = redis.opsForValue().get(key());
            Map<String, Object> state = seatState();
            assertNotEquals(oldIdentity, identity);
            assertEquals(first.getExpiresAt(), next.getExpiresAt());
            resume.countDown();
            Outcome<HoldSeatsResponse> result = retry.get(10, TimeUnit.SECONDS);
            assertAll(
                    () -> assertEquals(1, affectedRows.get(), "Real CAS accepts same owner/deadline; final token guard is required"),
                    () -> assertError(result, ErrorCode.SEAT_HELD),
                    () -> assertEquals(state, seatState()),
                    () -> assertEquals(identity, redis.opsForValue().get(key())),
                    () -> assertEquals(next.getExpiresAt().toInstant().toEpochMilli(), redisDeadline()),
                    this::assertNoBookingGraph);
        } finally {
            resume.countDown();
            try { stop(executor); }
            finally { proxy.removeAdvice(pause); }
        }
    }

    private Instant redisTime() {
        // Acquisition deadlines use the Redis server clock. Docker and the JVM
        // need not share a sub-millisecond clock offset, even on the same host.
        Long epochMillis = redis.execute(new DefaultRedisScript<>("""
                local time = redis.call('TIME')
                return time[1] * 1000 + math.floor(time[2] / 1000)
                """, Long.class), List.of());
        return Instant.ofEpochMilli(epochMillis);
    }

    private Instant dbDeadline() {
        return jdbcTemplate.queryForObject("SELECT held_until FROM flight_seats WHERE id = ?",
                OffsetDateTime.class, seatId).toInstant();
    }

    private long redisDeadline() { return redisDeadline(seatId); }

    private long redisDeadline(long id) {
        return redis.execute(new DefaultRedisScript<>("return redis.call('PEXPIRETIME', KEYS[1])", Long.class),
                List.of("hold:seat:" + id));
    }

    private Instant shortenHold(Duration remaining) {
        Instant deadline = Instant.ofEpochMilli(Instant.now().plus(remaining).toEpochMilli());
        jdbcTemplate.update("UPDATE flight_seats SET held_until = ? WHERE id = ?",
                deadline.atOffset(ZoneOffset.UTC), seatId);
        assertTrue(Boolean.TRUE.equals(redis.expireAt(key(), deadline)));
        assertEquals(deadline, dbDeadline());
        assertEquals(deadline.toEpochMilli(), redisDeadline());
        return deadline;
    }

    private CreateBookingRequest bookingRequest() {
        return CreateBookingRequest.builder().tripType(TripType.ONE_WAY).flightIds(List.of(flightId))
                .contactInfo(ContactInfoRequest.builder().fullName("F03 Contact")
                        .email("f03@test.com").phone("0910000000").build())
                .passengers(List.of(PassengerRequest.builder().fullName("F03 Passenger")
                        .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE).nationality("VNM")
                        .passengerType(PassengerType.ADULT).identityType(DocumentType.PASSPORT)
                        .identityNumber("F03-DOCUMENT").identityExpiry(LocalDate.of(2035, 1, 1)).build()))
                .seatSelections(List.of(SeatSelectionRequest.builder().flightId(flightId).flightSeatId(seatId)
                        .passengerIndex(0).build())).build();
    }

    private void pauseRenewAfterRead(CountDownLatch read, CountDownLatch resume) {
        AtomicBoolean once = new AtomicBoolean();
        gate.handler = event -> {
            if (Thread.currentThread().getName().equals("f03-renew") && event.key().equals(key())
                    && event.operation().equals("get") && event.result() != null && once.compareAndSet(false, true)) {
                read.countDown();
                boundedWait(resume);
            }
        };
    }

    private void expireRedisLease() {
        assertEquals(Boolean.TRUE, redis.expire(key(), Duration.ofSeconds(1)));
        await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key())));
    }

    private Map<String, Object> seatState() {
        return jdbcTemplate.queryForMap("SELECT status, held_by_user, held_until FROM flight_seats WHERE id = ?", seatId);
    }

    private void assertHeldBy(User owner) {
        Map<String, Object> state = seatState();
        assertEquals("HELD", state.get("status"));
        assertEquals(owner.getId(), state.get("held_by_user"));
    }

    private void assertNoBookingGraph() {
        for (String table : List.of("booking", "booking_legs", "passengers", "passenger_documents", "tickets", "add_on_services")) {
            assertEquals(0L, jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class), table);
        }
    }

    private HoldSeatsResponse hold(User user) {
        return bookings.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seatId)).build());
    }

    private User user(String name, String phone) {
        return users.save(User.builder().email("f03-" + name + "@test.com").passwordHash("test-only")
                .fullName("F03 " + name).phone(phone).role(UserRole.ROLE_USER).build());
    }

    private Long airport(String code) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO airports(iata_code, name, city, country, timezone)
                VALUES (?, 'F03 Airport', 'Test City', 'Vietnam', 'UTC') RETURNING id
                """, Long.class, code);
    }

    private String key() { return "hold:seat:" + seatId; }
    private static ExecutorService worker(String name) {
        return Executors.newSingleThreadExecutor(task -> new Thread(task, name));
    }
    private static void boundedWait(CountDownLatch latch) {
        try { assertTrue(latch.await(30, TimeUnit.SECONDS), "Worker must be resumed"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private static void stop(ExecutorService... executors) throws InterruptedException {
        for (ExecutorService executor : executors) executor.shutdownNow();
        for (ExecutorService executor : executors) assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
    private record Outcome<T>(T value, Throwable error) { }
    private static <T> Outcome<T> capture(Callable<T> call) {
        try { return new Outcome<>(call.call(), null); }
        catch (Throwable error) { return new Outcome<>(null, error); }
    }
    private static void assertSuccess(Outcome<?> result) {
        assertNull(result.error(), () -> "Unexpected failure: " + result.error());
        assertNotNull(result.value());
    }
    private static void assertError(Outcome<?> result, ErrorCode expected) {
        assertEquals(expected, assertInstanceOf(AppException.class, result.error(),
                () -> "Unexpected outcome: " + result).getErrorCode());
        assertNull(result.value());
    }

    record RedisEvent(String operation, String key, Object result) { }
    static class RedisGate { volatile Consumer<RedisEvent> handler = event -> { }; }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisTimingConfiguration {
        @Bean static RedisGate redisGate() { return new RedisGate(); }

        @Bean
        static BeanPostProcessor redisTiming(RedisGate gate) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof StringRedisTemplate)) return bean;
                    ProxyFactory template = new ProxyFactory(bean);
                    template.setProxyTargetClass(true);
                    template.addAdvice((MethodInterceptor) invocation -> {
                        if (invocation.getMethod().getName().equals("execute")
                                && invocation.getArguments()[0] instanceof RedisScript<?> script
                                && script.getScriptAsString().contains("'PEXPIRETIME'")
                                && !script.getScriptAsString().contains("'SET'")) {
                            var keys = (List<?>) invocation.getArguments()[1];
                            gate.handler.accept(new RedisEvent("compareDeadline", keys.get(0).toString(), null));
                        }
                        if (invocation.getMethod().getName().equals("execute")
                                && invocation.getArguments()[0] instanceof RedisScript<?> script
                                && script.getScriptAsString().contains("'DEL'")) {
                            var keys = (List<?>) invocation.getArguments()[1];
                            gate.handler.accept(new RedisEvent("compareDelete", keys.get(0).toString(), null));
                        }
                        Object actual = invocation.proceed();
                        if (invocation.getMethod().getName().equals("execute")
                                && invocation.getArguments()[0] instanceof RedisScript<?> script
                                && script.getScriptAsString().contains("'SET'")
                                && actual instanceof Long deadline && deadline > 0) {
                            var keys = (List<?>) invocation.getArguments()[1];
                            gate.handler.accept(new RedisEvent("setIfAbsent", keys.get(0).toString(), true));
                        }
                        if (!invocation.getMethod().getName().equals("opsForValue")) return actual;
                        ProxyFactory values = new ProxyFactory(actual);
                        values.addAdvice((MethodInterceptor) valueCall -> {
                            Object result = valueCall.proceed(); // Never fake or replace a Redis result.
                            if (valueCall.getArguments().length > 0) {
                                gate.handler.accept(new RedisEvent(valueCall.getMethod().getName(),
                                        valueCall.getArguments()[0].toString(), result));
                            }
                            return result;
                        });
                        return values.getProxy();
                    });
                    return template.getProxy();
                }
            };
        }
    }
}
