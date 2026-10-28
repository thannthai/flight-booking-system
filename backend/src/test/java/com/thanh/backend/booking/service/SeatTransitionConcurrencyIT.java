package com.thanh.backend.booking.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.UserRole;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.booking.dto.request.AddOnRequest;
import com.thanh.backend.booking.dto.request.ContactInfoRequest;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.request.SeatSelectionRequest;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.dto.response.HoldSeatsResponse;
import com.thanh.backend.booking.enums.AddOnType;
import com.thanh.backend.booking.enums.DocumentType;
import com.thanh.backend.booking.enums.Gender;
import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.booking.enums.TripType;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.thanh.backend.booking.service.SeatHoldLeaseAssertions.assertLeaseOwnedBy;

// Test-only interceptors coordinate real Spring Data calls; no mock results or replacement persistence.
class SeatTransitionConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private FlightSeatRepository seats;
    @Autowired private UserRepository users;
    @Autowired private StringRedisTemplate redis;

    private User alice;
    private User bob;
    private Long flightId;
    private Long seatId;

    @BeforeEach
    void fixtures() {
        alice = user("alice", "0900000001");
        bob = user("bob", "0900000002");
        Long origin = airport("F2A");
        Long dest = airport("F2B");
        Long airline = jdbcTemplate.queryForObject("""
                INSERT INTO airlines(code, name) VALUES ('F2', 'F02 Test Airline') RETURNING id
                """, Long.class);
        Long aircraft = jdbcTemplate.queryForObject("""
                INSERT INTO aircrafts(airline_code_id, model, total_seats, economy_seats, registration_no)
                VALUES (?, 'F02 Test Aircraft', 1, 1, 'F02-AIRCRAFT') RETURNING id
                """, Long.class, airline);
        flightId = jdbcTemplate.queryForObject("""
                INSERT INTO flights(flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id,
                                    departure_time, arrival_time, base_price, source)
                VALUES ('F201', ?, ?, ?, ?, clock_timestamp() + INTERVAL '2 days',
                        clock_timestamp() + INTERVAL '2 days 2 hours', 1500000, 'MANUAL') RETURNING id
                """, Long.class, airline, aircraft, origin, dest);
        seatId = jdbcTemplate.queryForObject("""
                INSERT INTO flight_seats(flight_id, seat_no, cabin_class, price)
                VALUES (?, '1A', 'ECONOMY', 1500000) RETURNING id
                """, Long.class, flightId);
    }

    @ParameterizedTest(name = "Stale hold must not overwrite committed booking; expired initial hold={0}")
    @ValueSource(booleans = {false, true})
    void staleHoldRead_BookingCommitsBeforeHoldResumes_MustNotReopenSeat(boolean expiredInitialHold)
            throws Exception {
        if (expiredInitialHold) {
            hold(alice);
            // Expire the old lease before either competing transaction; Alice later obtains a NEW valid hold.
            jdbcTemplate.update("""
                    UPDATE flight_seats SET held_until = clock_timestamp() + INTERVAL '1 second' WHERE id = ?
                    """, seatId);
            assertEquals(Boolean.TRUE, redis.expire(key(), Duration.ofSeconds(1)));
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.FALSE.equals(redis.hasKey(key()))
                    && Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT held_until < clock_timestamp() FROM flight_seats WHERE id = ?
                    """, Boolean.class, seatId)));
        }

        try (PausedHold staleBob = new PausedHold()) {
            staleBob.awaitRead();
            assertNotEquals(staleBob.pid.get(), jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
            assertNull(redis.opsForValue().get(key()));

            hold(alice); // Commits a fresh, unexpired lease while Bob still holds only a stale entity.
            assertHeldBy(alice);
            BookingResponse firstBooking = bookingService.createBooking(alice, request("ALICE-F02"));
            assertNotNull(firstBooking);
            assertBooked(seatState());
            assertNull(redis.opsForValue().get(key()));
            assertGraphCount(1);
            assertEquals(1L, activeBookings());
            System.out.println("F02 Alice committed valid booking while Bob's read transaction is still paused");

            Outcome<HoldSeatsResponse> bobHold = staleBob.resumeAndGet();
            drainSeatHoldCleanup();
            Map<String, Object> afterStaleWriter = seatState();
            String leaseAfterStaleWriter = redis.opsForValue().get(key());
            // Exercise the actual public service again: RED can now produce a second active booking.
            Outcome<BookingResponse> bobBooking = capture(() -> bookingService.createBooking(bob, request("BOB-F02")));
            System.out.println("F02 expiredInitialHold=" + expiredInitialHold
                    + " afterStaleWriter=" + afterStaleWriter + " redisLeasePresent=" + (leaseAfterStaleWriter != null)
                    + " holdError=" + bobHold.error() + " bobBookingError=" + bobBooking.error()
                    + " activeBookings=" + activeBookings());

            assertAll("No stale BOOKED -> HELD transition or second active booking",
                    () -> assertError(bobHold, ErrorCode.SEAT_HELD),
                    () -> assertBooked(afterStaleWriter),
                    () -> assertNull(leaseAfterStaleWriter, "Rejected stale hold must not leave a Redis lease"),
                    () -> assertError(bobBooking, ErrorCode.SEAT_NOT_AVAILABLE),
                    () -> assertEquals(1L, activeBookings(), "Exactly one active booking may own the seat"),
                    () -> assertGraphCount(1),
                    () -> assertEquals(0L, jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM booking WHERE user_id = ?", Long.class, bob.getId())),
                    () -> assertEquals(0L, jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM passengers WHERE user_id = ?", Long.class, bob.getId())),
                    () -> assertBooked(seatState()),
                    () -> assertNull(redis.opsForValue().get(key())));
        }
    }

    @Test
    void staleHoldRead_AnotherLiveHoldCommits_RedisRejectsCompetingOwner() throws Exception {
        try (PausedHold staleBob = new PausedHold()) {
            staleBob.awaitRead();
            hold(alice);
            Outcome<HoldSeatsResponse> bobHold = staleBob.resumeAndGet();
            assertError(bobHold, ErrorCode.SEAT_HELD);
            assertHeldBy(alice);
            assertLeaseOwnedBy(redis.opsForValue().get(key()), alice.getId());
            assertTrue(redis.getExpire(key(), TimeUnit.MILLISECONDS) > 0);
            assertGraphCount(0);
            assertEquals(0L, activeBookings());
        }
    }

    @Test
    void sameOwnerCanReuseLiveHold_AndCreateBooking() {
        hold(alice);
        var previousDeadline = seatState().get("held_until");
        hold(alice);
        assertHeldBy(alice);
        assertEquals(previousDeadline, seatState().get("held_until"));
        assertLeaseOwnedBy(redis.opsForValue().get(key()), alice.getId());
        assertTrue(redis.getExpire(key(), TimeUnit.MILLISECONDS) > 0);
        assertNotNull(bookingService.createBooking(alice, request("RENEW-F02")));
        assertBooked(seatState());
        assertNull(redis.opsForValue().get(key()));
        assertGraphCount(1);
        assertEquals(1L, activeBookings());
    }

    @Test
    void competingBookings_SameLiveHold_SecondUpdateWaitsAndRechecksStatus() throws Exception {
        hold(alice);
        CountDownLatch winnerClaimed = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        CountDownLatch loserEnteringClaim = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger winnerPid = new AtomicInteger();
        AtomicInteger loserPid = new AtomicInteger();
        MethodInterceptor coordinateClaims = invocation -> {
            if (!invocation.getMethod().getName().equals("claimHeldSeatForBooking")) {
                return invocation.proceed();
            }
            int order = calls.incrementAndGet();
            int pid = transactionEvidence("booking claim " + order);
            if (order == 1) {
                winnerPid.set(pid);
                Object rows = invocation.proceed(); // Real UPDATE obtains the seat row lock.
                assertEquals(1, rows);
                winnerClaimed.countDown();
                assertTrue(releaseWinner.await(20, TimeUnit.SECONDS), "Winner must be released");
                return rows;
            }
            loserPid.set(pid);
            loserEnteringClaim.countDown();
            return invocation.proceed(); // Real UPDATE must block until the first transaction commits.
        };
        Advised proxy = assertInstanceOf(Advised.class, seats);
        proxy.addAdvice(0, coordinateClaims);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome<BookingResponse>> winner = executor.submit(() -> capture(
                    () -> bookingService.createBooking(alice, request("WINNER-F02"))));
            assertTrue(winnerClaimed.await(10, TimeUnit.SECONDS));
            Future<Outcome<BookingResponse>> loser = executor.submit(() -> capture(
                    () -> bookingService.createBooking(alice, request("LOSER-F02"))));
            assertTrue(loserEnteringClaim.await(10, TimeUnit.SECONDS));
            assertNotEquals(winnerPid.get(), loserPid.get());
            await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT wait_event_type = 'Lock' AND ? = ANY(pg_blocking_pids(pid))
                    FROM pg_stat_activity WHERE pid = ?
                    """, Boolean.class, winnerPid.get(), loserPid.get())));
            System.out.println("F02 actual blocked UPDATE: " + jdbcTemplate.queryForMap("""
                    SELECT pid, wait_event_type, wait_event, pg_blocking_pids(pid)::text AS blockers
                    FROM pg_stat_activity WHERE pid = ?
                    """, loserPid.get()));
            assertHeldBy(alice); // The winner's BOOKED update has not committed yet.
            assertLeaseOwnedBy(redis.opsForValue().get(key()), alice.getId());
            assertGraphCount(0);

            releaseWinner.countDown();
            assertSuccess(winner.get(10, TimeUnit.SECONDS));
            assertError(loser.get(10, TimeUnit.SECONDS), ErrorCode.SEAT_NOT_AVAILABLE);
            assertBooked(seatState());
            assertNull(redis.opsForValue().get(key()));
            assertGraphCount(1);
            assertEquals(1L, activeBookings());
        } finally {
            releaseWinner.countDown();
            try {
                stop(executor);
            } finally {
                proxy.removeAdvice(coordinateClaims);
            }
        }
    }

    private final class PausedHold implements AutoCloseable {
        private final CountDownLatch read = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private final AtomicInteger pid = new AtomicInteger();
        private final ExecutorService executor = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "f02-stale-holder"));
        private final Advised proxy = assertInstanceOf(Advised.class, seats);
        private final MethodInterceptor pause;
        private final Future<Outcome<HoldSeatsResponse>> result;

        private PausedHold() {
            AtomicBoolean once = new AtomicBoolean();
            pause = invocation -> {
                Object value = invocation.proceed(); // Preserve the real findById and managed entity.
                if (Thread.currentThread().getName().equals("f02-stale-holder")
                        && invocation.getMethod().getName().equals("findById")
                        && invocation.getArguments()[0].equals(seatId) && once.compareAndSet(false, true)) {
                    pid.set(transactionEvidence("Bob read before Redis acquisition"));
                    System.out.println("F02 Bob snapshot: " + seatState());
                    read.countDown();
                    assertTrue(resume.await(20, TimeUnit.SECONDS), "Bob must be released");
                }
                return value;
            };
            proxy.addAdvice(0, pause);
            result = executor.submit(() -> capture(() -> hold(bob)));
        }

        private void awaitRead() throws InterruptedException {
            assertTrue(read.await(10, TimeUnit.SECONDS), "Bob must read seat before the other transaction");
        }

        private Outcome<HoldSeatsResponse> resumeAndGet() throws Exception {
            resume.countDown();
            return result.get(10, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws InterruptedException {
            resume.countDown();
            try {
                stop(executor);
            } finally {
                proxy.removeAdvice(pause);
            }
        }
    }

    private int transactionEvidence(String label) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        Map<String, Object> evidence = jdbcTemplate.queryForMap("""
                SELECT pg_backend_pid() AS pid, txid_current() AS tx,
                       current_setting('transaction_isolation') AS isolation
                """);
        assertEquals("read committed", evidence.get("isolation"));
        System.out.println("F02 " + label + ": " + evidence);
        return ((Number) evidence.get("pid")).intValue();
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Workers must stop before database cleanup");
    }

    private HoldSeatsResponse hold(User user) {
        return bookingService.holdSeats(user, HoldSeatsRequest.builder().seatIds(List.of(seatId)).build());
    }

    private CreateBookingRequest request(String document) {
        return CreateBookingRequest.builder().tripType(TripType.ONE_WAY).flightIds(List.of(flightId))
                .contactInfo(ContactInfoRequest.builder()
                        .fullName("F02 Contact").email("f02@test.com").phone("0900000000").build())
                .passengers(List.of(PassengerRequest.builder().fullName("F02 Passenger")
                        .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE).nationality("VNM")
                        .passengerType(PassengerType.ADULT).identityType(DocumentType.PASSPORT)
                        .identityNumber(document).identityExpiry(LocalDate.of(2035, 1, 1)).build()))
                .seatSelections(List.of(SeatSelectionRequest.builder().flightId(flightId).flightSeatId(seatId)
                        .passengerIndex(0).addOns(List.of(AddOnRequest.builder()
                                .serviceType(AddOnType.MEAL).quantity(1).build())).build())).build();
    }

    private Map<String, Object> seatState() {
        return jdbcTemplate.queryForMap(
                "SELECT status, held_by_user, held_until FROM flight_seats WHERE id = ?", seatId);
    }

    private void assertHeldBy(User user) {
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject("""
                SELECT status = 'HELD' AND held_by_user = ? AND held_until > clock_timestamp()
                FROM flight_seats WHERE id = ?
                """, Boolean.class, user.getId(), seatId));
    }

    private static void assertBooked(Map<String, Object> state) {
        assertAll(() -> assertEquals("BOOKED", state.get("status")),
                () -> assertNull(state.get("held_by_user")), () -> assertNull(state.get("held_until")));
    }

    private long activeBookings() {
        return jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT b.id) FROM tickets t JOIN booking b ON b.id = t.booking_id
                WHERE t.flight_seat_id = ? AND b.status IN ('PENDING', 'COMPLETED')
                """, Long.class, seatId);
    }

    private void assertGraphCount(long count) {
        assertAll(List.of("booking", "booking_legs", "passengers", "passenger_documents", "tickets", "add_on_services")
                .stream().map(table -> () -> assertEquals(count,
                        jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class), table)));
    }

    private String key() { return "hold:seat:" + seatId; }

    private User user(String name, String phone) {
        return users.save(User.builder().email("f02-" + name + "@test.com").passwordHash("test-only")
                .fullName("F02 " + name).phone(phone).role(UserRole.ROLE_USER).build());
    }

    private Long airport(String code) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO airports(iata_code, name, city, country, timezone)
                VALUES (?, 'F02 Airport', 'Test City', 'Vietnam', 'UTC') RETURNING id
                """, Long.class, code);
    }

    private record Outcome<T>(T value, Throwable error) { }

    private static <T> Outcome<T> capture(Callable<T> call) {
        try { return new Outcome<>(call.call(), null); }
        catch (Throwable error) { return new Outcome<>(null, error); }
    }

    private static void assertSuccess(Outcome<?> outcome) {
        assertNull(outcome.error(), () -> "Unexpected failure: " + outcome.error());
        assertNotNull(outcome.value());
    }

    private static void assertError(Outcome<?> outcome, ErrorCode expected) {
        assertEquals(expected, assertInstanceOf(AppException.class, outcome.error(),
                () -> "Expected rejection; actual value=" + outcome.value() + ", error=" + outcome.error()).getErrorCode());
        assertNull(outcome.value());
    }
}
