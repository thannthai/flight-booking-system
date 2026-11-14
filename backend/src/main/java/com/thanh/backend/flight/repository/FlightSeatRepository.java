package com.thanh.backend.flight.repository;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {

    List<FlightSeat> findByFlightId(Long flightId);

    /** Seats this user holds right now, other than the ones being (re)held by the current request. */
    @Query("""
            SELECT COUNT(s) FROM FlightSeat s
            WHERE s.status = com.thanh.backend.flight.enums.SeatStatus.HELD
              AND s.heldByUser.id = :userId
              AND s.heldUntil > cast(function('clock_timestamp') as OffsetDateTime)
              AND s.id NOT IN :excludedSeatIds
            """)
    long countLiveHoldsByUserExcluding(@Param("userId") Long userId, @Param("excludedSeatIds") java.util.Collection<Long> excludedSeatIds);

    @Query("""
            SELECT COUNT(s) > 0 FROM FlightSeat s
            WHERE s.flight.id = :flightId
              AND s.status = com.thanh.backend.flight.enums.SeatStatus.HELD
              AND s.heldUntil > cast(function('clock_timestamp') as OffsetDateTime)
            """)
    boolean existsLiveHoldByFlightId(@Param("flightId") Long flightId);

    List<FlightSeat> findByFlightIdAndStatus(Long flightId, SeatStatus status);

    @Modifying
    @Query("""
            UPDATE FlightSeat s
            SET s.status = com.thanh.backend.flight.enums.SeatStatus.HELD,
                s.heldByUser = :user,
                s.heldUntil = :expiresAt,
                s.updatedAt = cast(function('clock_timestamp') as OffsetDateTime)
            WHERE s.id = :seatId
              AND :expiresAt > cast(function('clock_timestamp') as OffsetDateTime)
              AND (s.status = com.thanh.backend.flight.enums.SeatStatus.AVAILABLE
                   OR (s.status = com.thanh.backend.flight.enums.SeatStatus.HELD
                       AND (s.heldUntil < cast(function('clock_timestamp') as OffsetDateTime)
                            OR (s.heldByUser = :user AND s.heldUntil = :expiresAt))))
            """)
    int acquireHoldIfAvailable(
            @Param("seatId") Long seatId,
            @Param("user") User user,
            @Param("expiresAt") OffsetDateTime expiresAt);

    // PostgreSQL CURRENT_TIMESTAMP is fixed at transaction start; expiry needs
    // database wall-clock time.
    @Modifying
    @Query("""
                UPDATE FlightSeat s
                SET s.status = :newStatus,
                    s.heldByUser = null,
                    s.heldUntil = null
                WHERE s.id = :seatId
                  AND s.status = :expectedStatus
                  AND s.heldByUser.id = :userId
                  AND s.heldUntil > cast(function('clock_timestamp') as OffsetDateTime)
            """)
    int claimHeldSeatForBooking(
            @Param("seatId") Long seatId,
            @Param("expectedStatus") SeatStatus expectedStatus,
            @Param("newStatus") SeatStatus newStatus,
            @Param("userId") Long userId);

    // Reconciliation must serialize with DB writers and retain any live hold.
    // NOWAIT makes cleanup best effort instead of blocking a competing request.
    @Query(value = """
            SELECT status = 'HELD' AND held_until > clock_timestamp()
            FROM flight_seats WHERE id = :seatId FOR UPDATE NOWAIT
            """, nativeQuery = true)
    Boolean hasLiveHoldForLeaseCleanup(@Param("seatId") Long seatId);
    // Release only the cancelling booking's pending allocation. Never free a
    // newer booking's seat or mutate a HELD lease (including Redis generations).
    @Modifying
    @Query(value = """
            UPDATE flight_seats s SET status = 'AVAILABLE', held_until = NULL, held_by_user = NULL
            WHERE s.id = :seatId AND s.status = 'BOOKED'
              AND EXISTS (SELECT 1 FROM tickets t WHERE t.flight_seat_id = s.id
                          AND t.booking_id = :bookingId AND t.status = 'PENDING')
              AND NOT EXISTS (SELECT 1 FROM tickets t JOIN booking b ON b.id = t.booking_id
                              WHERE t.flight_seat_id = s.id AND t.booking_id <> :bookingId
                                AND t.status IN ('PENDING', 'ISSUED')
                                AND b.status IN ('PENDING', 'COMPLETED'))
            """, nativeQuery = true)
    int releaseBookedSeatForCancellation(@Param("seatId") Long seatId, @Param("bookingId") Long bookingId);
}
