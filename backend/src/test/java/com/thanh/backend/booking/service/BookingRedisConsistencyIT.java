package com.thanh.backend.booking.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.UserRole;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.*;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.dto.response.HoldSeatsResponse;
import com.thanh.backend.booking.enums.*;
import com.thanh.backend.booking.infrastructure.redis.SeatHoldLeaseStore;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Import(BookingRedisConsistencyIT.TimingConfiguration.class)
class BookingRedisConsistencyIT extends AbstractIntegrationTest {
    @Autowired private BookingService bookings;
    @Autowired private UserRepository users;
    @Autowired private FlightSeatRepository seats;
    @Autowired private StringRedisTemplate redis;
    @Autowired private Gate gate;
    @Autowired private TicketRepository tickets;
    @Autowired private SeatHoldLeaseStore leaseStore;
    @Autowired private ApplicationContext applicationContext;
    private User alice;
    private User bob;
    private long flightId;
    private long firstSeat;
    private long secondSeat;

    @BeforeEach
    void fixtures() {
        gate.handler = event -> { };
        alice = user("alice", "0940000001");
        bob = user("bob", "0940000002");
        Long origin = airport("F4A");
        Long dest = airport("F4B");
        Long airline = jdbcTemplate.queryForObject(
                "INSERT INTO airlines(code, name) VALUES ('F4', 'F04 Airline') RETURNING id", Long.class);
        Long aircraft = jdbcTemplate.queryForObject("""
                INSERT INTO aircrafts(airline_code_id, model, total_seats, economy_seats, registration_no)
                VALUES (?, 'F04 Aircraft', 2, 2, 'F04-AIRCRAFT') RETURNING id
                """, Long.class, airline);
        flightId = jdbcTemplate.queryForObject("""
                INSERT INTO flights(flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id,
                                    departure_time, arrival_time, base_price, source)
                VALUES ('F401', ?, ?, ?, ?, clock_timestamp() + INTERVAL '2 days',
                        clock_timestamp() + INTERVAL '2 days 2 hours', 1500000, 'MANUAL') RETURNING id
                """, Long.class, airline, aircraft, origin, dest);
        firstSeat = seat("1A");
        secondSeat = seat("1B");
    }

    @AfterEach
    void restoreFaults() {
        drainSeatHoldCleanup();
        gate.handler = event -> { };
        allowDelete(true);
        allowGet(true);
        for (String table : List.of("booking", "flight_seats", "tickets")) {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS f04_failure ON " + table);
        }
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS f04_failure()");
    }

    @Test
    void multiSeatLateSqlFailure_PreservesAllLeasesAndRollsBackEntireGraph() {
        hold(alice, firstSeat, secondSeat);
        Map<Long, LeaseState> original = leases();
        jdbcTemplate.execute("""
                CREATE FUNCTION f04_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.flight_seat_id = %d THEN
                        RAISE EXCEPTION 'F04 deliberate second-ticket failure' USING ERRCODE = '23514';
                    END IF;
                    RETURN NEW;
                END $$
                """.formatted(secondSeat));
        jdbcTemplate.execute("CREATE TRIGGER f04_failure BEFORE INSERT ON tickets FOR EACH ROW EXECUTE FUNCTION f04_failure()");
        Outcome<BookingResponse> result = capture(() -> bookings.createBooking(alice, request()));
        assertAll(() -> assertSqlFailure(result, "23514"), this::assertNoGraph,
                () -> assertHeld(firstSeat, alice), () -> assertHeld(secondSeat, alice),
                () -> assertLeasesUnchanged(original));
    }

    @Test
    void multiSeatSecondClaimExpires_PreservesFirstLeaseAndRollsBackGraph() throws Exception {
        hold(alice, firstSeat, secondSeat);
        LeaseState original = lease(firstSeat);
        CountDownLatch secondClaim = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        MethodInterceptor pause = invocation -> {
            if (invocation.getMethod().getName().equals("claimHeldSeatForBooking")
                    && invocation.getArguments()[0].equals(secondSeat)) {
                secondClaim.countDown();
                boundedWait(resume);
            }
            return invocation.proceed();
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        proxy.addAdvice(0, pause);
        ExecutorService executor = worker("f04-second-claim");
        try {
            Future<Outcome<BookingResponse>> future = executor.submit(() -> capture(() -> bookings.createBooking(alice, request())));
            assertTrue(secondClaim.await(10, TimeUnit.SECONDS));
            assertEquals(0, committedCount("booking"));
            jdbcTemplate.update("UPDATE flight_seats SET held_until = clock_timestamp() + INTERVAL '1 second' WHERE id = ?", secondSeat);
            assertTrue(Boolean.TRUE.equals(redis.expire(key(secondSeat), Duration.ofSeconds(1))));
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key(secondSeat)))
                    && Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT held_until < clock_timestamp() FROM flight_seats WHERE id = ?", Boolean.class, secondSeat)));
            resume.countDown();
            Outcome<BookingResponse> result = future.get(10, TimeUnit.SECONDS);
            assertAll(() -> assertError(result, ErrorCode.SEAT_NOT_AVAILABLE), this::assertNoGraph,
                    () -> assertLeaseUnchanged(firstSeat, original), () -> assertHeld(firstSeat, alice),
                    () -> assertHeld(secondSeat, alice), () -> assertNull(redis.opsForValue().get(key(secondSeat))));
        } finally {
            resume.countDown();
            try { stop(executor); } finally { proxy.removeAdvice(pause); }
        }
    }

    @Test
    void bookingActualCommitFailure_DoesNotReturnSuccessOrConsumeLeases() {
        hold(alice, firstSeat, secondSeat);
        Map<Long, LeaseState> original = leases();
        deferredFailure("booking", "INSERT");
        Outcome<BookingResponse> result = capture(() -> bookings.createBooking(alice, request()));
        assertAll(() -> assertSqlFailure(result, "23514"), this::assertNoGraph,
                () -> assertLeasesUnchanged(original), () -> assertHeld(firstSeat, alice), () -> assertHeld(secondSeat, alice));
    }

    @Test
    void holdActualCommitFailure_CleansNewRedisLeaseAfterRollback() {
        deferredFailure("flight_seats", "UPDATE");
        Outcome<HoldSeatsResponse> result = capture(() -> hold(alice, firstSeat, secondSeat));
        assertAll(() -> assertSqlFailure(result, "23514"), this::assertNoGraph,
                () -> assertEquals("AVAILABLE", state(firstSeat).get("status")),
                () -> assertEquals("AVAILABLE", state(secondSeat).get("status")),
                () -> assertTrue(redis.opsForValue().get(key(firstSeat)) == null, "New first lease must be absent"),
                () -> assertTrue(redis.opsForValue().get(key(secondSeat)) == null, "New second lease must be absent"));
    }

    @Test
    void redisDeleteDenied_AfterCommitStillReturnsCommittedBookingAndAttemptsEveryLease() {
        hold(alice, firstSeat, secondSeat);
        Map<Long, LeaseState> original = leases();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger visibleAtDelete = new AtomicInteger(-1);
        gate.handler = event -> {
            if (event.before() && event.script().contains("'DEL'")) {
                attempts.incrementAndGet();
                visibleAtDelete.set(committedCount("booking"));
            }
        };
        allowDelete(false); // Real Redis ACL: Lua DEL fails; reads/TTL commands still work.
        Outcome<BookingResponse> result = capture(() -> bookings.createBooking(alice, request()));
        assertAll(() -> assertNull(result.error(), "Redis cleanup must not turn a committed booking into an API failure"),
                () -> assertNotNull(result.value()), () -> assertEquals(1, committedCount("booking")),
                () -> assertEquals(1, visibleAtDelete.get(), "Cleanup must run only after durable DB commit"),
                () -> assertEquals(2, attempts.get(), "One failed cleanup must not prevent the next attempt"),
                () -> assertGraph(1, 1, 2, 2, 2, 2), () -> assertEquals("BOOKED", state(firstSeat).get("status")),
                () -> assertEquals("BOOKED", state(secondSeat).get("status")),
                () -> assertNull(state(firstSeat).get("held_until")),
                () -> assertNull(state(secondSeat).get("held_until")),
                () -> original.forEach(this::assertRedisLeaseUnchanged));
    }

    @Test
    void acquireReplyLostAfterRealSet_RollbackCleansCandidateDespiteMissingResult() {
        AtomicBoolean once = new AtomicBoolean();
        gate.handler = event -> {
            if (!event.before() && event.script().contains("'SET'") && event.result() instanceof Long value
                    && value > 0 && once.compareAndSet(false, true)) {
                assertNotNull(redis.opsForValue().get(event.key())); // The real Redis write has happened.
                throw new RedisConnectionFailureException("F04 injected lost acquisition response");
            }
        };
        Outcome<HoldSeatsResponse> result = capture(() -> hold(alice, firstSeat));
        assertAll(() -> assertInstanceOf(RedisConnectionFailureException.class, result.error()),
                () -> assertNull(result.value()), () -> assertEquals("AVAILABLE", state(firstSeat).get("status")),
                () -> assertTrue(redis.opsForValue().get(key(firstSeat)) == null, "Unacknowledged candidate must be absent"), this::assertNoGraph);
    }

    @Test
    void failedOriginalHold_CannotDeleteSameLeaseCommittedByConcurrentRetry() throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean once = new AtomicBoolean();
        gate.handler = event -> {
            if (!event.before() && Thread.currentThread().getName().equals("f04-original")
                    && event.script().contains("'SET'") && event.result() instanceof Long value && value > 0
                    && once.compareAndSet(false, true)) {
                acquired.countDown();
                boundedWait(resume);
            }
        };
        ExecutorService executor = worker("f04-original");
        try {
            Future<Outcome<HoldSeatsResponse>> original = executor.submit(() -> capture(() -> hold(alice, firstSeat, -1L)));
            assertTrue(acquired.await(10, TimeUnit.SECONDS));
            LeaseState identity = lease(firstSeat);
            HoldSeatsResponse retry = hold(alice, firstSeat);
            assertEquals(identity.deadline(), retry.getExpiresAt().toInstant().toEpochMilli());
            resume.countDown();
            Outcome<HoldSeatsResponse> result = original.get(10, TimeUnit.SECONDS);
            assertAll(() -> assertError(result, ErrorCode.SEAT_NOT_AVAILABLE),
                    () -> assertHeld(firstSeat, alice), () -> assertLeaseUnchanged(firstSeat, identity), this::assertNoGraph);
        } finally { resume.countDown(); stop(executor); }
    }

    @Test
    void rollbackCleanupRedisFailure_DoesNotMaskBusinessErrorOrRestartDeadlineOnRetry() {
        AtomicBoolean once = new AtomicBoolean();
        gate.handler = event -> {
            if (!event.before() && event.script().contains("'SET'") && event.result() instanceof Long value
                    && value > 0 && once.compareAndSet(false, true)) allowDelete(false);
        };
        Outcome<HoldSeatsResponse> result = capture(() -> hold(alice, firstSeat, -1L));
        LeaseState orphan = lease(firstSeat);
        assertAll(() -> assertError(result, ErrorCode.SEAT_NOT_AVAILABLE),
                () -> assertEquals("AVAILABLE", state(firstSeat).get("status")),
                () -> assertNotNull(orphan.value()), () -> assertTrue(orphan.deadline() > 0), this::assertNoGraph);
        allowDelete(true);
        gate.handler = event -> { };
        HoldSeatsResponse retry = hold(alice, firstSeat);
        assertEquals(orphan.deadline(), retry.getExpiresAt().toInstant().toEpochMilli());
        assertLeaseUnchanged(firstSeat, orphan);
        assertHeld(firstSeat, alice);
    }

    @ParameterizedTest(name = "Late post-commit cleanup preserves reacquisition; same user={0}")
    @ValueSource(booleans = {false, true})
    void postCommitCleanup_CannotDeleteReacquiredGenerationAfterCancellation(boolean sameUser) throws Exception {
        hold(alice, firstSeat, secondSeat);
        Map<Long, LeaseState> originals = leases();
        CountDownLatch cleanup = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean once = new AtomicBoolean();
        gate.handler = event -> {
            if (event.before() && Thread.currentThread().getName().equals("f04-postcommit")
                    && event.script().contains("'DEL'") && once.compareAndSet(false, true)) {
                assertEquals(1, committedCount("booking"));
                cleanup.countDown();
                boundedWait(resume);
            }
        };
        ExecutorService executor = worker("f04-postcommit");
        try {
            Future<Outcome<BookingResponse>> future = executor.submit(() -> capture(() -> bookings.createBooking(alice, request())));
            assertTrue(cleanup.await(10, TimeUnit.SECONDS));
            long bookingId = jdbcTemplate.queryForObject("SELECT id FROM booking", Long.class);
            bookings.cancelBooking(alice, bookingId);
            for (long id : List.of(firstSeat, secondSeat)) assertTrue(Boolean.TRUE.equals(redis.expire(key(id), Duration.ofSeconds(1))));
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key(firstSeat)))
                    && Boolean.FALSE.equals(redis.hasKey(key(secondSeat))));
            User nextOwner = sameUser ? alice : bob;
            hold(nextOwner, firstSeat, secondSeat);
            Map<Long, LeaseState> next = leases();
            for (long id : next.keySet()) assertNotEquals(originals.get(id).value(), next.get(id).value());
            resume.countDown();
            Outcome<BookingResponse> result = future.get(10, TimeUnit.SECONDS);
            assertAll(() -> assertNull(result.error()), () -> assertNotNull(result.value()),
                    () -> assertLeasesUnchanged(next), () -> assertHeld(firstSeat, nextOwner), () -> assertHeld(secondSeat, nextOwner),
                    () -> assertEquals("CANCELLED", jdbcTemplate.queryForObject("SELECT status FROM booking", String.class)));
        } finally { resume.countDown(); stop(executor); }
    }

    @Test
    void serviceDoesNotReturnBookingSuccessBeforeActualCommit() throws Exception {
        hold(alice, firstSeat, secondSeat);
        Map<Long, LeaseState> original = leases();
        CountDownLatch beforeCommit = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicInteger savedTickets = new AtomicInteger();
        MethodInterceptor observe = invocation -> {
            Object actual = invocation.proceed();
            if (invocation.getMethod().getName().equals("save") && savedTickets.incrementAndGet() == 2) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { beforeCommit.countDown(); boundedWait(resume); }
                });
            }
            return actual;
        };
        Advised proxy = assertInstanceOf(Advised.class, tickets);
        proxy.addAdvice(0, observe);
        ExecutorService executor = worker("f04-beforecommit");
        try {
            Future<Outcome<BookingResponse>> future = executor.submit(() -> capture(() -> bookings.createBooking(alice, request())));
            assertTrue(beforeCommit.await(10, TimeUnit.SECONDS));
            assertFalse(future.isDone());
            assertNoGraph(); // Independent connection cannot see the uncommitted graph.
            original.forEach(this::assertRedisLeaseUnchanged);
            resume.countDown();
            Outcome<BookingResponse> result = future.get(10, TimeUnit.SECONDS);
            assertNull(result.error());
            assertNotNull(result.value());
            assertGraph(1, 1, 2, 2, 2, 2);
            assertTrue(redis.opsForValue().get(key(firstSeat)) == null);
            assertTrue(redis.opsForValue().get(key(secondSeat)) == null);
        } finally {
            resume.countDown();
            try { stop(executor); } finally { proxy.removeAdvice(observe); }
        }
    }

    @Test
    void redisReadFailureAtBeforeCommit_RollsBackAllSeatsAndPreservesLeases() {
        hold(alice, firstSeat, secondSeat);
        Map<Long, LeaseState> original = leases();
        AtomicInteger checks = new AtomicInteger();
        gate.handler = event -> {
            if (event.before() && event.script().contains("'PEXPIRETIME'") && !event.script().contains("'SET'")
                    && checks.incrementAndGet() == 3) allowGet(false); // Two post-claim checks precede beforeCommit.
        };
        Outcome<BookingResponse> result;
        try { result = capture(() -> bookings.createBooking(alice, request())); }
        finally { allowGet(true); gate.handler = event -> { }; }
        assertAll(() -> assertNotNull(result.error()), () -> assertNull(result.value()),
                () -> assertEquals(3, checks.get()), this::assertNoGraph,
                () -> assertLeasesUnchanged(original), () -> assertHeld(firstSeat, alice), () -> assertHeld(secondSeat, alice));
    }

    @Test
    void rollbackCleanupDoesNotWaitForConcurrentRetryRowLock() throws Exception {
        CountDownLatch acquisition = new CountDownLatch(1);
        CountDownLatch failOriginal = new CountDownLatch(1);
        CountDownLatch retryClaimed = new CountDownLatch(1);
        CountDownLatch commitRetry = new CountDownLatch(1);
        gate.handler = event -> {
            if (!event.before() && Thread.currentThread().getName().equals("f04-lost-original")
                    && event.script().contains("'SET'") && event.result() instanceof Long value && value > 0) {
                acquisition.countDown();
                boundedWait(failOriginal);
                throw new RedisConnectionFailureException("F04 injected reply loss while retry is in flight");
            }
        };
        AtomicInteger originalPid = new AtomicInteger();
        AtomicInteger retryPid = new AtomicInteger();
        AtomicReference<String> cleanupSqlState = new AtomicReference<>();
        MethodInterceptor pause = invocation -> {
            if (invocation.getMethod().getName().equals("hasLiveHoldForLeaseCleanup")) {
                originalPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
            }
            Object actual;
            try { actual = invocation.proceed(); }
            catch (RuntimeException failure) {
                Throwable cause = failure;
                while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
                if (cause instanceof SQLException sql) cleanupSqlState.set(sql.getSQLState());
                throw failure;
            }
            if (Thread.currentThread().getName().equals("f04-locked-retry")
                    && invocation.getMethod().getName().equals("acquireHoldIfAvailable")) {
                assertEquals(1, actual);
                retryPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                retryClaimed.countDown(); boundedWait(commitRetry);
            }
            return actual;
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        proxy.addAdvice(0, pause);
        ExecutorService oldWorker = worker("f04-lost-original");
        ExecutorService retryWorker = worker("f04-locked-retry");
        try {
            Future<Outcome<HoldSeatsResponse>> old = oldWorker.submit(() -> capture(() -> hold(alice, firstSeat)));
            assertTrue(acquisition.await(10, TimeUnit.SECONDS));
            LeaseState identity = lease(firstSeat);
            Future<Outcome<HoldSeatsResponse>> retry = retryWorker.submit(() -> capture(() -> hold(alice, firstSeat)));
            assertTrue(retryClaimed.await(10, TimeUnit.SECONDS));
            failOriginal.countDown();
            Outcome<HoldSeatsResponse> failed = old.get(5, TimeUnit.SECONDS); // NOWAIT, while retry still holds its row lock.
            assertInstanceOf(RedisConnectionFailureException.class, failed.error());
            assertFalse(retry.isDone());
            assertEquals("55P03", cleanupSqlState.get(), "Real PostgreSQL NOWAIT lock rejection");
            assertTrue(originalPid.get() > 0);
            assertNotEquals(originalPid.get(), retryPid.get());
            assertRedisLeaseUnchanged(firstSeat, identity);
            commitRetry.countDown();
            Outcome<HoldSeatsResponse> success = retry.get(10, TimeUnit.SECONDS);
            assertNull(success.error()); assertNotNull(success.value());
            assertLeaseUnchanged(firstSeat, identity); assertHeld(firstSeat, alice);
            assertTrue(retryPid.get() > 0);
        } finally {
            failOriginal.countDown(); commitRetry.countDown();
            try { stop(oldWorker); stop(retryWorker); } finally { proxy.removeAdvice(pause); }
        }
    }

    @Test
    void crashStateBeforeHoldCommit_OrphanExpiresWithoutExtendingDeadline() throws Exception {
        // Reconstruct crash state using real Redis + an independent DB connection that closes without commit.
        // No process/SIGKILL test: the transaction callbacks are deliberately absent from this primitive setup.
        var orphan = leaseStore.acquireOrReuse(firstSeat, alice.getId(), Duration.ofSeconds(2), null).orElseThrow();
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("UPDATE flight_seats SET status='HELD', held_by_user=?, held_until=? WHERE id=?")) {
                statement.setLong(1, alice.getId()); statement.setObject(2, orphan.expiresAt()); statement.setLong(3, firstSeat);
                assertEquals(1, statement.executeUpdate());
            }
            connection.rollback(); // PostgreSQL outcome after an uncommitted process disconnect.
        }
        assertEquals("AVAILABLE", state(firstSeat).get("status"));
        assertEquals(orphan.expiresAt().toInstant().toEpochMilli(), lease(firstSeat).deadline());
        AppException blocked = assertThrows(AppException.class, () -> hold(bob, firstSeat));
        assertEquals(ErrorCode.SEAT_HELD, blocked.getErrorCode());
        assertEquals(orphan.expiresAt().toInstant().toEpochMilli(), lease(firstSeat).deadline());
        await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key(firstSeat))));
        HoldSeatsResponse next = hold(bob, firstSeat);
        assertTrue(next.getExpiresAt().isAfter(orphan.expiresAt()));
        assertHeld(firstSeat, bob); assertNoGraph();
    }

    @Test
    void postCommitOrphanExpiry_CannotReopenBookedSeatOrCreateSecondBooking() {
        hold(alice, firstSeat, secondSeat);
        allowDelete(false); // A committed booking whose cleanup failed has the same state as a lost callback after crash.
        Outcome<BookingResponse> result;
        try { result = capture(() -> bookings.createBooking(alice, request())); }
        finally { allowDelete(true); }
        assertNull(result.error()); assertNotNull(result.value());
        assertGraph(1, 1, 2, 2, 2, 2);
        assertEquals(ErrorCode.SEAT_HELD, assertThrows(AppException.class, () -> hold(bob, firstSeat)).getErrorCode());
        for (long id : List.of(firstSeat, secondSeat)) assertTrue(Boolean.TRUE.equals(redis.expire(key(id), Duration.ofSeconds(1))));
        await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key(firstSeat)))
                && Boolean.FALSE.equals(redis.hasKey(key(secondSeat))));
        assertEquals(ErrorCode.SEAT_HELD, assertThrows(AppException.class, () -> hold(bob, firstSeat)).getErrorCode());
        assertEquals(ErrorCode.SEAT_NOT_AVAILABLE, assertThrows(AppException.class, () -> bookings.createBooking(alice,
                requestWithDifferentDocuments())).getErrorCode());
        assertGraph(1, 1, 2, 2, 2, 2);
        assertEquals("BOOKED", state(firstSeat).get("status"));
        assertEquals("BOOKED", state(secondSeat).get("status"));
    }

    private CreateBookingRequest requestWithDifferentDocuments() {
        var request = request();
        request.getPassengers().getFirst().setIdentityNumber("F04-RETRY-ONE");
        request.getPassengers().get(1).setIdentityNumber("F04-RETRY-TWO");
        return request;
    }

    @Test
    void fullCleanupQueue_PreservesBusinessErrorAndOriginalExpiry() throws Exception {
        assertNotSame(seatHoldCleanupExecutor, applicationContext.getBean("applicationTaskExecutor"),
                "Payment/email async execution must retain its own default executor");
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        seatHoldCleanupExecutor.execute(() -> { running.countDown(); boundedWait(resume); });
        assertTrue(running.await(10, TimeUnit.SECONDS));
        var queue = seatHoldCleanupExecutor.getThreadPoolExecutor().getQueue();
        try {
            while (queue.remainingCapacity() > 0) seatHoldCleanupExecutor.execute(() -> { });
            AppException failure = assertThrows(AppException.class, () -> hold(alice, firstSeat, -1L));
            assertEquals(ErrorCode.SEAT_NOT_AVAILABLE, failure.getErrorCode());
            LeaseState orphan = lease(firstSeat);
            assertNotNull(orphan.value());
            assertTrue(orphan.deadline() > System.currentTimeMillis());
            assertTrue(orphan.deadline() <= System.currentTimeMillis() + 900_000);
            assertEquals("AVAILABLE", state(firstSeat).get("status"));
            assertNoGraph();
        } finally {
            resume.countDown();
            await().atMost(Duration.ofSeconds(10)).until(queue::isEmpty);
            drainSeatHoldCleanup();
        }
    }

    private void deferredFailure(String table, String event) {
        jdbcTemplate.execute("""
                CREATE FUNCTION f04_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'F04 deliberate commit-time failure' USING ERRCODE = '23514'; END $$
                """);
        jdbcTemplate.execute("CREATE CONSTRAINT TRIGGER f04_failure AFTER " + event + " ON " + table
                + " DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION f04_failure()");
    }

    private void allowDelete(boolean allow) {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.execute("ACL", bytes("SETUSER"), bytes("default"), bytes(allow ? "+del" : "-del"));
        }
    }
    private void allowGet(boolean allow) {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.execute("ACL", bytes("SETUSER"), bytes("default"), bytes(allow ? "+get" : "-get"));
        }
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private HoldSeatsResponse hold(User actor, Long... ids) {
        return bookings.holdSeats(actor, HoldSeatsRequest.builder().seatIds(List.of(ids)).build());
    }
    private CreateBookingRequest request() {
        return CreateBookingRequest.builder().tripType(TripType.ONE_WAY).flightIds(List.of(flightId))
                .contactInfo(ContactInfoRequest.builder().fullName("F04 Contact").email("f04@test.com").phone("0940000000").build())
                .passengers(List.of(passenger("ONE"), passenger("TWO")))
                .seatSelections(List.of(selection(firstSeat, 0), selection(secondSeat, 1))).build();
    }
    private PassengerRequest passenger(String document) {
        return PassengerRequest.builder().fullName("F04 Passenger " + document).dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE).nationality("VNM").passengerType(PassengerType.ADULT).identityType(DocumentType.PASSPORT)
                .identityNumber("F04-" + document).identityExpiry(LocalDate.of(2035, 1, 1)).build();
    }
    private SeatSelectionRequest selection(long seat, int index) {
        return SeatSelectionRequest.builder().flightId(flightId).flightSeatId(seat).passengerIndex(index)
                .addOns(List.of(AddOnRequest.builder().serviceType(AddOnType.MEAL).quantity(1).build())).build();
    }
    private User user(String name, String phone) {
        return users.save(User.builder().email("f04-" + name + "@test.com").passwordHash("test-only")
                .fullName("F04 " + name).phone(phone).role(UserRole.ROLE_USER).build());
    }
    private Long airport(String code) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO airports(iata_code, name, city, country, timezone)
                VALUES (?, 'F04 Airport', 'Test City', 'Vietnam', 'UTC') RETURNING id
                """, Long.class, code);
    }
    private long seat(String number) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO flight_seats(flight_id, seat_no, cabin_class, price)
                VALUES (?, ?, 'ECONOMY', 1500000) RETURNING id
                """, Long.class, flightId, number);
    }
    private Map<String, Object> state(long id) {
        return jdbcTemplate.queryForMap("SELECT status, held_by_user, held_until FROM flight_seats WHERE id = ?", id);
    }
    private void assertHeld(long id, User owner) {
        var state = state(id);
        assertEquals("HELD", state.get("status"));
        assertEquals(owner.getId(), state.get("held_by_user"));
        assertNotNull(state.get("held_until"));
    }
    private int committedCount(String table) {
        // Always a separate connection, including inside callbacks on the service thread.
        try (var connection = jdbcTemplate.getDataSource().getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(result.next());
            return result.getInt(1);
        } catch (SQLException error) { throw new AssertionError(error); }
    }
    private void assertNoGraph() { assertGraph(0, 0, 0, 0, 0, 0); }
    private void assertGraph(int... expected) {
        var tables = List.of("booking", "booking_legs", "passengers", "passenger_documents", "tickets", "add_on_services");
        for (int i = 0; i < tables.size(); i++) assertEquals(expected[i], committedCount(tables.get(i)), tables.get(i));
    }
    private record LeaseState(String value, long deadline) {
        @Override public String toString() { return "LeaseState[value=" + (value == null ? "absent" : "redacted") + ", deadline=" + deadline + "]"; }
    }
    private LeaseState lease(long id) {
        Long deadline = redis.execute(new DefaultRedisScript<>("return redis.call('PEXPIRETIME', KEYS[1])", Long.class), List.of(key(id)));
        return new LeaseState(redis.opsForValue().get(key(id)), deadline);
    }
    private Map<Long, LeaseState> leases() { return Map.of(firstSeat, lease(firstSeat), secondSeat, lease(secondSeat)); }
    private void assertLeasesUnchanged(Map<Long, LeaseState> expected) { expected.forEach(this::assertLeaseUnchanged); }
    private void assertLeaseUnchanged(long id, LeaseState expected) {
        assertRedisLeaseUnchanged(id, expected);
        assertEquals(expected.deadline(), jdbcTemplate.queryForObject(
                "SELECT held_until FROM flight_seats WHERE id = ?", OffsetDateTime.class, id).toInstant().toEpochMilli());
    }
    private void assertRedisLeaseUnchanged(long id, LeaseState expected) {
        assertEquals(expected, lease(id), "Lease identity and absolute expiry must remain unchanged");
        assertTrue(redis.getExpire(key(id), TimeUnit.MILLISECONDS) > 0);
    }
    private static void assertSqlFailure(Outcome<?> result, String state) {
        assertNull(result.value());
        assertNotNull(result.error());
        Throwable error = result.error();
        while (error != null && !(error instanceof SQLException)) error = error.getCause();
        assertEquals(state, assertInstanceOf(SQLException.class, error, () -> "Unexpected error: " + result.error()).getSQLState());
    }
    private static void assertError(Outcome<?> result, ErrorCode expected) {
        assertNull(result.value());
        assertEquals(expected, assertInstanceOf(AppException.class, result.error()).getErrorCode());
    }
    private static String key(long id) { return "hold:seat:" + id; }
    private static ExecutorService worker(String name) { return Executors.newSingleThreadExecutor(task -> new Thread(task, name)); }
    private static void boundedWait(CountDownLatch latch) {
        try { assertTrue(latch.await(30, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
    private record Outcome<T>(T value, Throwable error) { }
    private <T> Outcome<T> capture(Callable<T> call) {
        Outcome<T> result;
        try { result = new Outcome<>(call.call(), null); }
        catch (Throwable error) { result = new Outcome<>(null, error); }
        // Observe the original method result, then wait outside its transaction for cleanup.
        drainSeatHoldCleanup();
        return result;
    }
    record Event(boolean before, String script, String key, Object result) { }
    static class Gate { volatile Consumer<Event> handler = event -> { }; }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimingConfiguration {
        @Bean static Gate f04Gate() { return new Gate(); }
        @Bean static BeanPostProcessor f04RedisTiming(Gate gate) {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof StringRedisTemplate)) return bean;
                    ProxyFactory factory = new ProxyFactory(bean);
                    factory.setProxyTargetClass(true);
                    factory.addAdvice((MethodInterceptor) invocation -> {
                        if (invocation.getMethod().getName().equals("execute")
                                && invocation.getArguments()[0] instanceof RedisScript<?> script) {
                            String key = ((List<?>) invocation.getArguments()[1]).getFirst().toString();
                            gate.handler.accept(new Event(true, script.getScriptAsString(), key, null));
                            Object actual = invocation.proceed();
                            gate.handler.accept(new Event(false, script.getScriptAsString(), key, actual));
                            return actual;
                        }
                        return invocation.proceed();
                    });
                    return factory.getProxy();
                }
            };
        }
    }
}
