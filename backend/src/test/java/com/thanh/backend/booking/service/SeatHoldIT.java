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
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.response.HoldSeatsResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.thanh.backend.booking.service.SeatHoldLeaseAssertions.assertLeaseOwnedBy;

class SeatHoldIT extends AbstractIntegrationTest {

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
    private StringRedisTemplate redisTemplate;

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
                .basePrice(new BigDecimal("1500000"))
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
                .price(new BigDecimal("1500000"))
                .build());
    }

    @Test
    @DisplayName("Seat Hold - Basic Happy Path & TTL Decay (DB & Redis Consistency)")
    void holdSeat_Success_ValidTtlAndDbState() {
        FlightSeat seat = createTestSeat("12A");
        User user1 = createTestUser("alice");
        User user2 = createTestUser("bob");

        HoldSeatsRequest request = HoldSeatsRequest.builder()
                .seatIds(List.of(seat.getId()))
                .build();

        HoldSeatsResponse response = bookingService.holdSeats(user1, request);

        // 1. Verify Response
        assertNotNull(response);
        assertEquals(List.of(seat.getId()), response.getHeldSeats());
        assertEquals(900, response.getTtlSeconds());

        // 2. Verify Redis Key & Positive TTL bound (0 < ttl <= 900s)
        String redisKey = "hold:seat:" + seat.getId();
        String redisOwner = redisTemplate.opsForValue().get(redisKey);
        assertLeaseOwnedBy(redisOwner, user1.getId());

        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 900, "Redis TTL must be positive and <= 900, was: " + ttl);

        // 3. Verify Database State
        FlightSeat updatedSeat = flightSeatRepository.findById(seat.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, updatedSeat.getStatus());
        assertEquals(user1.getId(), updatedSeat.getHeldByUser().getId());
        assertNotNull(updatedSeat.getHeldUntil());
        assertTrue(updatedSeat.getHeldUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));

        // 4. Verify Subsequent hold by another user is rejected with SEAT_HELD
        AppException ex = assertThrows(AppException.class, () -> bookingService.holdSeats(user2, request));
        assertEquals(ErrorCode.SEAT_HELD, ex.getErrorCode());
    }

    @Test
    @DisplayName("Seat Hold - Batch Partial Failure preserves other user's hold and rolls back unacquired seat")
    void holdSeats_BatchPartialFailure_PreservesOtherUserHold() {
        FlightSeat seat1 = createTestSeat("14A");
        FlightSeat seat2 = createTestSeat("14B");

        User userB = createTestUser("bob");
        User userA = createTestUser("alice");

        // Step 1: User B successfully holds Seat 2
        HoldSeatsRequest reqB = HoldSeatsRequest.builder()
                .seatIds(List.of(seat2.getId()))
                .build();
        bookingService.holdSeats(userB, reqB);

        String redisKey2 = "hold:seat:" + seat2.getId();
        String originalLease = redisTemplate.opsForValue().get(redisKey2);
        assertLeaseOwnedBy(originalLease, userB.getId());

        // Step 2: User A attempts batch hold on [Seat 1 (Free), Seat 2 (Held by B)]
        HoldSeatsRequest reqA = HoldSeatsRequest.builder()
                .seatIds(List.of(seat1.getId(), seat2.getId()))
                .build();

        AppException ex = assertThrows(AppException.class, () -> bookingService.holdSeats(userA, reqA));
        drainSeatHoldCleanup();
        assertEquals(ErrorCode.SEAT_HELD, ex.getErrorCode());

        // Step 3: Invariant Assertions
        // Seat 1: must remain AVAILABLE, not held by User A, Redis key must not exist
        FlightSeat freshSeat1 = flightSeatRepository.findById(seat1.getId()).orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, freshSeat1.getStatus(), "Seat 1 must remain AVAILABLE after batch rollback");
        assertNull(freshSeat1.getHeldByUser());
        assertNull(redisTemplate.opsForValue().get("hold:seat:" + seat1.getId()), "Seat 1 Redis key must be empty");

        // Seat 2: must remain strictly HELD by User B, User A's rollback must not touch it!
        FlightSeat freshSeat2 = flightSeatRepository.findById(seat2.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, freshSeat2.getStatus(), "Seat 2 must remain HELD by User B");
        assertEquals(userB.getId(), freshSeat2.getHeldByUser().getId(), "Seat 2 must still belong to User B");
        assertEquals(originalLease, redisTemplate.opsForValue().get(redisKey2), "Seat 2 Redis lease identity must remain unchanged");
        Long ttl2 = redisTemplate.getExpire(redisKey2, TimeUnit.SECONDS);
        assertNotNull(ttl2);
        assertTrue(ttl2 > 0, "Seat 2 Redis TTL must remain positive");
    }

    @Test
    @DisplayName("Seat Hold - 20 Concurrent Threads with CountDownLatch: Strict Mutual Exclusion")
    void holdSeat_Concurrent20Threads_StrictMutualExclusion() throws InterruptedException {
        FlightSeat targetSeat = createTestSeat("15A");
        int threadCount = 20;

        List<User> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            users.add(createTestUser("contestant_" + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger seatHeldErrorCount = new AtomicInteger(0);
        AtomicReference<User> winnerRef = new AtomicReference<>();
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        HoldSeatsRequest request = HoldSeatsRequest.builder()
                .seatIds(List.of(targetSeat.getId()))
                .build();

        for (int i = 0; i < threadCount; i++) {
            final User user = users.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // Wait for all 20 threads to align at the starting line
                    boolean started = startLatch.await(10, TimeUnit.SECONDS);
                    if (!started) {
                        unexpectedErrors.add(new IllegalStateException("Timeout waiting for startLatch"));
                        return;
                    }

                    bookingService.holdSeats(user, request);
                    successCount.incrementAndGet();
                    winnerRef.set(user);
                } catch (AppException e) {
                    if (e.getErrorCode() == ErrorCode.SEAT_HELD) {
                        seatHeldErrorCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(e);
                    }
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                }
            });
        }

        // Wait until all 20 threads are ready, then fire simultaneously!
        boolean ready = readyLatch.await(10, TimeUnit.SECONDS);
        assertTrue(ready, "All 20 threads must be ready within 10 seconds");
        startLatch.countDown();

        executor.shutdown();
        boolean terminated = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertTrue(terminated, "Executor must terminate within 15 seconds");

        // Assertions
        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors encountered during concurrency: " + unexpectedErrors);
        assertEquals(1, successCount.get(), "Exactly 1 thread must acquire the seat hold");
        assertEquals(19, seatHeldErrorCount.get(), "Exactly 19 threads must be rejected with SEAT_HELD");

        User winner = winnerRef.get();
        assertNotNull(winner, "A winning user must be recorded");

        // Verify Redis reflects the winner
        String redisKey = "hold:seat:" + targetSeat.getId();
        assertLeaseOwnedBy(redisTemplate.opsForValue().get(redisKey), winner.getId());

        // Verify PostgreSQL reflects the winner
        FlightSeat finalSeat = flightSeatRepository.findById(targetSeat.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, finalSeat.getStatus());
        assertEquals(winner.getId(), finalSeat.getHeldByUser().getId());
        assertNotNull(finalSeat.getHeldUntil());
    }
}
