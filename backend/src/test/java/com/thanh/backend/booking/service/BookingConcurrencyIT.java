package com.thanh.backend.booking.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.aircraft.entity.Aircraft;
import com.thanh.backend.aircraft.repository.AircraftRepository;
import com.thanh.backend.airline.entity.Airline;
import com.thanh.backend.airline.repository.AirlineRepository;
import com.thanh.backend.airport.entity.Airport;
import com.thanh.backend.airport.repository.AirportRepository;
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
import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.BookingLeg;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.AddOnType;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.DocumentType;
import com.thanh.backend.booking.enums.Gender;
import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.booking.enums.TripType;
import com.thanh.backend.booking.repository.BookingLegRepository;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.entity.Flight;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.CabinClass;
import com.thanh.backend.flight.enums.FlightSource;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.flight.enums.SeatStatus;
import com.thanh.backend.flight.repository.FlightRepository;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static com.thanh.backend.booking.service.SeatHoldLeaseAssertions.assertLeaseOwnedBy;

class BookingConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private FlightSeatRepository flightSeatRepository;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private AirlineRepository airlineRepository;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockitoSpyBean
    private BookingLegRepository bookingLegRepository;

    private Flight testFlight;

    @BeforeEach
    void setUpTestData() {
        Airport origin = airportRepository.findByIataCode("SGN").orElseGet(() -> airportRepository.save(Airport.builder()
                .iataCode("SGN")
                .name("Tan Son Nhat")
                .city("Ho Chi Minh")
                .country("Vietnam")
                .timezone("Asia/Ho_Chi_Minh")
                .build()));

        Airport dest = airportRepository.findByIataCode("HAN").orElseGet(() -> airportRepository.save(Airport.builder()
                .iataCode("HAN")
                .name("Noi Bai")
                .city("Ha Noi")
                .country("Vietnam")
                .timezone("Asia/Ho_Chi_Minh")
                .build()));

        Airline airline = airlineRepository.findByCode("VN").orElseGet(() -> airlineRepository.save(Airline.builder()
                .code("VN")
                .name("Vietnam Airlines")
                .build()));

        String regNo = "VN-A" + String.format("%04d", Math.abs((int) (System.nanoTime() % 10000)));
        Aircraft aircraft = aircraftRepository.save(Aircraft.builder()
                .airline(airline)
                .model("Airbus A321")
                .registrationNo(regNo)
                .totalSeats(180)
                .economySeats(160)
                .businessSeats(20)
                .build());

        testFlight = flightRepository.save(Flight.builder()
                .flightNo("VN101")
                .airline(airline)
                .aircraft(aircraft)
                .origin(origin)
                .dest(dest)
                .departureTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2))
                .arrivalTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).plusHours(2))
                .status(FlightStatus.SCHEDULED)
                .basePrice(new BigDecimal("1500000.00"))
                .source(FlightSource.MANUAL)
                .build());
    }

    private User createTestUser(String emailPrefix) {
        return userRepository.save(User.builder()
                .email(emailPrefix + "_" + System.nanoTime() + "@test.com")
                .passwordHash("hashed_pwd")
                .fullName("User " + emailPrefix)
                .phone("09" + String.format("%08d", Math.abs((int) (System.nanoTime() % 100000000))))
                .role(UserRole.ROLE_USER)
                .build());
    }

    private FlightSeat createTestSeat(String seatNo) {
        return flightSeatRepository.save(FlightSeat.builder()
                .flight(testFlight)
                .seatNo(seatNo)
                .cabinClass(CabinClass.ECONOMY)
                .status(SeatStatus.AVAILABLE)
                .price(new BigDecimal("1500000.00"))
                .build());
    }

    @Test
    @DisplayName("Test 1 (Happy Path): User with valid hold creates booking successfully")
    void createBooking_HappyPath_CompletesBookingAndUpdatesSeatState() {
        // Given
        User alice = createTestUser("alice");
        FlightSeat seat = createTestSeat("12A");

        // 1. Alice holds the seat
        HoldSeatsResponse holdResponse = bookingService.holdSeats(alice, HoldSeatsRequest.builder()
                .seatIds(List.of(seat.getId()))
                .build());
        assertNotNull(holdResponse);
        assertEquals(1, holdResponse.getHeldSeats().size());

        CreateBookingRequest bookingRequest = CreateBookingRequest.builder()
                .tripType(TripType.ONE_WAY)
                .flightIds(List.of(testFlight.getId()))
                .contactInfo(ContactInfoRequest.builder()
                        .fullName("Alice Smith")
                        .email("alice@test.com")
                        .phone("0901234567")
                        .build())
                .passengers(List.of(PassengerRequest.builder()
                        .fullName("Alice Smith")
                        .dateOfBirth(LocalDate.of(1995, 5, 20))
                        .gender(Gender.FEMALE)
                        .nationality("VNM")
                        .passengerType(PassengerType.ADULT)
                        .identityType(DocumentType.CITIZEN_ID)
                        .identityNumber("079195000001")
                        .identityExpiry(LocalDate.of(2035, 5, 20))
                        .build()))
                .seatSelections(List.of(SeatSelectionRequest.builder()
                        .flightId(testFlight.getId())
                        .flightSeatId(seat.getId())
                        .passengerIndex(0)
                        .build()))
                .build();

        // When
        OffsetDateTime beforeCall = OffsetDateTime.now(ZoneOffset.UTC);
        BookingResponse response = bookingService.createBooking(alice, bookingRequest);
        OffsetDateTime afterCall = OffsetDateTime.now(ZoneOffset.UTC);

        // Then - Response Assertions
        assertNotNull(response);
        assertNotNull(response.getPnr());
        assertTrue(response.getPnr().matches("^[A-Z0-9]{6}$"), "PNR must be 6 uppercase alphanumeric characters");
        assertEquals(BookingStatus.PENDING, response.getStatus());

        // Then - Database Booking Assertions
        Booking savedBooking = bookingRepository.findByPnr(response.getPnr()).orElseThrow();
        assertEquals(BookingStatus.PENDING, savedBooking.getStatus());
        assertEquals(alice.getId(), savedBooking.getUser().getId());

        // Bounded Payment Deadline validation (now + 15m window +- 5s)
        OffsetDateTime minExpectedDeadline = beforeCall.plusMinutes(15).minusSeconds(5);
        OffsetDateTime maxExpectedDeadline = afterCall.plusMinutes(15).plusSeconds(5);
        assertTrue(!savedBooking.getPaymentDeadline().isBefore(minExpectedDeadline),
                "Payment deadline must be after " + minExpectedDeadline);
        assertTrue(!savedBooking.getPaymentDeadline().isAfter(maxExpectedDeadline),
                "Payment deadline must be before " + maxExpectedDeadline);

        // Financial precision check with BigDecimal compareTo
        assertEquals(0, new BigDecimal("1500000.00").compareTo(savedBooking.getBaseAmount()),
                "Base amount must equal 1,500,000.00");
        assertEquals(0, new BigDecimal("150000.00").compareTo(savedBooking.getTaxAmount()),
                "Tax amount (10%) must equal 150,000.00");
        assertEquals(0, new BigDecimal("0.00").compareTo(savedBooking.getDiscountAmount()),
                "Discount amount must equal 0.00");
        assertEquals(0, new BigDecimal("1650000.00").compareTo(savedBooking.getTotalAmount()),
                "Total amount must equal 1,650,000.00");

        // Then - Database Ticket Assertions
        List<Ticket> tickets = ticketRepository.findByBookingId(savedBooking.getId());
        assertEquals(1, tickets.size());
        Ticket ticket = tickets.get(0);
        assertEquals(TicketStatus.PENDING, ticket.getStatus());
        assertEquals(seat.getId(), ticket.getFlightSeat().getId());
        assertTrue(ticket.getTicketNo().contains(savedBooking.getPnr()));

        // Then - Database FlightSeat Assertions
        FlightSeat updatedSeat = flightSeatRepository.findById(seat.getId()).orElseThrow();
        assertEquals(SeatStatus.BOOKED, updatedSeat.getStatus(), "Seat status must transition to BOOKED");
        assertNull(updatedSeat.getHeldByUser(), "Seat held_by_user must be cleared");
        assertNull(updatedSeat.getHeldUntil(), "Seat held_until must be cleared");

        // Then - Redis Hold Consumption / Lock Invariant
        assertNull(redisTemplate.opsForValue().get("hold:seat:" + seat.getId()),
                "Redis hold key must be cleaned up upon successful booking creation");

        // A second user attempting to hold this seat must be rejected because DB seat is BOOKED
        User bob = createTestUser("bob");
        AppException exception = assertThrows(AppException.class, () ->
                bookingService.holdSeats(bob, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build()));
        assertEquals(ErrorCode.SEAT_HELD, exception.getErrorCode());
    }

    @Test
    @DisplayName("Test 2 (Race Condition Remediation): Check-then-act race is PREVENTED by atomic conditional update")
    void createBooking_CheckThenActRace_DeterministicReproduction() throws Exception {
        // Given
        User alice = createTestUser("alice_race");
        User bob = createTestUser("bob_race");
        FlightSeat seat = createTestSeat("14B");

        // 1. Alice holds the seat
        bookingService.holdSeats(alice, HoldSeatsRequest.builder()
                .seatIds(List.of(seat.getId()))
                .build());

        CreateBookingRequest aliceRequest = CreateBookingRequest.builder()
                .tripType(TripType.ONE_WAY)
                .flightIds(List.of(testFlight.getId()))
                .contactInfo(ContactInfoRequest.builder()
                        .fullName("Alice Race")
                        .email("alice_race@test.com")
                        .phone("0901234568")
                        .build())
                .passengers(List.of(PassengerRequest.builder()
                        .fullName("Alice Race")
                        .dateOfBirth(LocalDate.of(1992, 3, 15))
                        .gender(Gender.FEMALE)
                        .nationality("VNM")
                        .passengerType(PassengerType.ADULT)
                        .identityType(DocumentType.CITIZEN_ID)
                        .identityNumber("079192000002")
                        .identityExpiry(LocalDate.of(2032, 3, 15))
                        .build()))
                .seatSelections(List.of(SeatSelectionRequest.builder()
                        .flightId(testFlight.getId())
                        .flightSeatId(seat.getId())
                        .passengerIndex(0)
                        .build()))
                .build();

        // Latches to deterministically pause Alice right after bookingLegRepository.save
        // and before saveTicketsAndAddOns (which updates seat to BOOKED)
        CountDownLatch aliceLegSavedLatch = new CountDownLatch(1);
        CountDownLatch bobIntervenedLatch = new CountDownLatch(1);
        AtomicBoolean spyIntercepted = new AtomicBoolean(false);

        doAnswer(invocation -> {
            BookingLeg leg = invocation.getArgument(0);
            if (leg.getId() == null) {
                entityManager.persist(leg);
            } else {
                leg = entityManager.merge(leg);
            }
            if (spyIntercepted.compareAndSet(false, true)) {
                // Signal main thread that Alice has passed validation, saved booking & leg
                aliceLegSavedLatch.countDown();
                // Pause Alice until Bob intervenes
                assertTrue(bobIntervenedLatch.await(10, TimeUnit.SECONDS),
                        "Alice thread timed out waiting for Bob intervention");
            }
            return leg;
        }).when(bookingLegRepository).save(any(BookingLeg.class));

        // When - Start Alice's createBooking in a background thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<BookingResponse> aliceResponseRef = new AtomicReference<>();
        AtomicReference<Throwable> aliceErrorRef = new AtomicReference<>();

        try {
            Future<?> aliceFuture = executor.submit(() -> {
                try {
                    BookingResponse res = bookingService.createBooking(alice, aliceRequest);
                    aliceResponseRef.set(res);
                } catch (Throwable t) {
                    aliceErrorRef.set(t);
                }
            });

            boolean reached = aliceLegSavedLatch.await(5, TimeUnit.SECONDS);
            if (!reached && aliceErrorRef.get() != null) {
                System.err.println("ALICE ERROR BEFORE LATCH: ");
                aliceErrorRef.get().printStackTrace();
            }
            assertTrue(reached,
                    "Alice thread should have reached BookingLegRepository.save");

            // 3. Main thread simulates Alice's hold expiration:
            // Update DB held_until to the past (so holdSeats allows a new claim) and clear Alice's Redis key
            FlightSeat currentSeat = flightSeatRepository.findById(seat.getId()).orElseThrow();
            currentSeat.setHeldUntil(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10));
            flightSeatRepository.save(currentSeat);
            redisTemplate.delete("hold:seat:" + seat.getId());

            // 4. Bob intervenes: claims the expired seat via holdSeats (committed independently)
            HoldSeatsResponse bobHoldResponse = bookingService.holdSeats(bob, HoldSeatsRequest.builder()
                    .seatIds(List.of(seat.getId()))
                    .build());
            assertNotNull(bobHoldResponse);
            assertEquals(1, bobHoldResponse.getHeldSeats().size());

            // Verify Bob legitimately owns the hold in both PostgreSQL and Redis
            java.util.Map<String, Object> bobDbSeat = jdbcTemplate.queryForMap(
                    "SELECT status, held_by_user, held_until FROM flight_seats WHERE id = ?", seat.getId());
            assertEquals("HELD", bobDbSeat.get("status"));
            assertEquals(bob.getId(), ((Number) bobDbSeat.get("held_by_user")).longValue(),
                    "Seat in DB must now belong to Bob");
            String bobLease = redisTemplate.opsForValue().get("hold:seat:" + seat.getId());
            assertLeaseOwnedBy(bobLease, bob.getId());

            // 5. Release Alice to resume execution into saveTicketsAndAddOns
            bobIntervenedLatch.countDown();
            aliceFuture.get(10, TimeUnit.SECONDS);

            // 6. INSPECT REMEDIATION & INVARIANT OUTCOME:
            // Query PostgreSQL directly with JdbcTemplate to bypass any Hibernate 1st-level cache
            java.util.Map<String, Object> finalDbSeat = jdbcTemplate.queryForMap(
                    "SELECT status, held_by_user, held_until FROM flight_seats WHERE id = ?", seat.getId());
            String finalRedisLock = redisTemplate.opsForValue().get("hold:seat:" + seat.getId());

            System.out.println("=== RACE REMEDIATION VERIFICATION REPORT ===");
            System.out.println("Alice Error: " + aliceErrorRef.get());
            System.out.println("Alice Booking Response: " + (aliceResponseRef.get() != null ? aliceResponseRef.get().getPnr() : "NULL"));
            System.out.println("Final Seat Status in DB (raw JDBC): " + finalDbSeat.get("status"));
            System.out.println("Final Seat HeldByUser in DB (raw JDBC): " + finalDbSeat.get("held_by_user"));
            System.out.println("Final Seat HeldUntil in DB (raw JDBC): " + finalDbSeat.get("held_until"));
            System.out.println("Bob's Redis Lease Present: " + (finalRedisLock != null));
            System.out.println("===========================================");

            // 1. Alice must be REJECTED with SEAT_NOT_AVAILABLE
            assertNotNull(aliceErrorRef.get(), "Alice must fail because seat is no longer held by her");
            assertTrue(aliceErrorRef.get() instanceof AppException, "Error must be an AppException");
            assertEquals(ErrorCode.SEAT_NOT_AVAILABLE, ((AppException) aliceErrorRef.get()).getErrorCode(),
                    "Alice must receive SEAT_NOT_AVAILABLE error code");
            assertNull(aliceResponseRef.get(), "Alice booking response must be null");

            // 2. Bob's hold in PostgreSQL must remain strictly intact
            assertEquals("HELD", finalDbSeat.get("status"), "Seat status must remain HELD for Bob");
            assertEquals(bob.getId(), ((Number) finalDbSeat.get("held_by_user")).longValue(),
                    "Seat must still be held by Bob");
            assertNotNull(finalDbSeat.get("held_until"), "Seat held_until must remain intact for Bob");

            // 3. Bob's Redis key must remain strictly intact
            assertEquals(bobLease, finalRedisLock, "Bob's exact Redis lease must remain untouched");

            // 4. Complete Transaction Rollback verification: Zero partial records for Alice
            Integer aliceBookingCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM booking WHERE user_id = ?", Integer.class, alice.getId());
            assertEquals(0, aliceBookingCount, "Alice's booking must be completely rolled back in PostgreSQL");

            Integer aliceTicketCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM tickets WHERE flight_seat_id = ?", Integer.class, seat.getId());
            assertEquals(0, aliceTicketCount, "Zero tickets must be committed for this seat");

            Integer alicePassengerCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM passengers WHERE user_id = ?", Integer.class, alice.getId());
            assertEquals(0, alicePassengerCount, "Alice's passenger records must be completely rolled back");
        } finally {
            bobIntervenedLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("F01: A hold expiring after validation must be rejected at final claim and roll back")
    void createBooking_HoldExpiresAfterValidation_RejectsFinalClaimAndRollsBack() throws Exception {
        User alice = createTestUser("alice_expiry");
        FlightSeat seat = createTestSeat("16A");
        String redisKey = "hold:seat:" + seat.getId();
        bookingService.holdSeats(alice, HoldSeatsRequest.builder().seatIds(List.of(seat.getId())).build());

        CreateBookingRequest request = CreateBookingRequest.builder()
                .tripType(TripType.ONE_WAY)
                .flightIds(List.of(testFlight.getId()))
                .contactInfo(ContactInfoRequest.builder()
                        .fullName("Alice Expiry").email("alice_expiry@test.com").phone("0901234569").build())
                .passengers(List.of(PassengerRequest.builder()
                        .fullName("Alice Expiry").dateOfBirth(LocalDate.of(1992, 3, 15))
                        .gender(Gender.FEMALE).nationality("VNM").passengerType(PassengerType.ADULT)
                        .identityType(DocumentType.CITIZEN_ID).identityNumber("079192000003")
                        .identityExpiry(LocalDate.of(2035, 3, 15)).build()))
                .seatSelections(List.of(SeatSelectionRequest.builder()
                        .flightId(testFlight.getId()).flightSeatId(seat.getId()).passengerIndex(0)
                        .addOns(List.of(AddOnRequest.builder().serviceType(AddOnType.MEAL).quantity(1).build()))
                        .build()))
                .build();

        CountDownLatch beforeClaim = new CountDownLatch(1);
        CountDownLatch resumeClaim = new CountDownLatch(1);
        MethodInterceptor pauseBeforeClaim = invocation -> {
            if (invocation.getMethod().getName().equals("claimHeldSeatForBooking")
                    && invocation.getArguments()[0].equals(seat.getId())) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                // These inserts are real and uncommitted: validation and persistence have already run.
                for (String table : List.of("booking", "booking_legs", "passengers", "passenger_documents")) {
                    assertEquals(1L, jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class));
                }
                beforeClaim.countDown();
                assertTrue(resumeClaim.await(20, TimeUnit.SECONDS), "Timed out waiting for hold expiry");
                // A CURRENT_TIMESTAMP predicate would incorrectly still accept this expired hold.
                assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject("""
                        SELECT CURRENT_TIMESTAMP < held_until AND held_until <= clock_timestamp()
                        FROM flight_seats WHERE id = ?
                        """, Boolean.class, seat.getId()));
            }
            // Keep the real Spring Data query, transaction advice and affected-row result unchanged.
            return invocation.proceed();
        };

        Advised repositoryProxy = assertInstanceOf(Advised.class, flightSeatRepository);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        repositoryProxy.addAdvice(0, pauseBeforeClaim);
        try {
            Future<BookingResponse> aliceFuture = executor.submit(() -> bookingService.createBooking(alice, request));
            assertTrue(beforeClaim.await(10, TimeUnit.SECONDS), "Alice must reach final claim after validation");

            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertNoBookingRecordsCommitted(); // Independent connection cannot see Alice's uncommitted inserts.
            assertLeaseOwnedBy(redisTemplate.opsForValue().get(redisKey), alice.getId());
            assertTrue(redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS) > 0);
            assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject("""
                    SELECT status = 'HELD' AND held_by_user = ? AND held_until > clock_timestamp()
                    FROM flight_seats WHERE id = ?
                    """, Boolean.class, alice.getId(), seat.getId()));

            // Shorten the real lease only after the barrier, then let DB time and Redis TTL expire naturally.
            // No Bob, no stale repository return value, and no change to save/claim persistence behavior.
            assertEquals(1, jdbcTemplate.update("""
                    UPDATE flight_seats SET held_until = clock_timestamp() + INTERVAL '1 second' WHERE id = ?
                    """, seat.getId()));
            assertEquals(Boolean.TRUE, redisTemplate.expire(redisKey, Duration.ofSeconds(1)));
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(25)).until(() ->
                    Boolean.FALSE.equals(redisTemplate.hasKey(redisKey))
                            && Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                            SELECT held_until <= clock_timestamp() FROM flight_seats WHERE id = ?
                            """, Boolean.class, seat.getId())));

            resumeClaim.countDown();
            BookingResponse response = null;
            Throwable failure = null;
            try {
                response = aliceFuture.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failure = e.getCause();
            }

            BookingResponse actualResponse = response;
            Throwable actualFailure = failure;
            var dbSeat = jdbcTemplate.queryForMap(
                    "SELECT status, held_by_user, held_until FROM flight_seats WHERE id = ?", seat.getId());
            assertAll("Expired final claim must reject Alice without committing partial booking data",
                    () -> assertEquals(ErrorCode.SEAT_NOT_AVAILABLE,
                            assertInstanceOf(AppException.class, actualFailure,
                                    () -> "Unexpected booking failure: " + actualFailure).getErrorCode()),
                    () -> assertNull(actualResponse, "Expired hold must not produce a successful booking"),
                    this::assertNoBookingRecordsCommitted,
                    () -> assertEquals("HELD", dbSeat.get("status"), "Expired seat must not become BOOKED"),
                    () -> assertEquals(alice.getId(), dbSeat.get("held_by_user")),
                    () -> assertNotNull(dbSeat.get("held_until")),
                    () -> assertNull(redisTemplate.opsForValue().get(redisKey), "Redis lease must remain expired"));
        } finally {
            resumeClaim.countDown();
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Alice worker must stop before cleanup");
            } finally {
                repositoryProxy.removeAdvice(pauseBeforeClaim);
            }
        }
    }

    private void assertNoBookingRecordsCommitted() {
        assertAll(List.of("booking", "booking_legs", "passengers", "passenger_documents", "tickets", "add_on_services")
                .stream().map(table -> () -> assertEquals(0L,
                        jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class),
                        "No committed records expected in " + table)));
    }
}
