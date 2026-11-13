package com.thanh.backend.booking.repository;

import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByBookingId(Long bookingId);
    boolean existsByPassengerId(Long passengerId);

    /** Read-only response path: loads everything BookingMapper touches in one statement. */
    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.bookingLeg bl
        JOIN FETCH bl.flight f
        JOIN FETCH f.airline
        JOIN FETCH f.aircraft
        JOIN FETCH f.origin
        JOIN FETCH f.dest
        JOIN FETCH t.passenger p
        LEFT JOIN FETCH p.guardian
        LEFT JOIN FETCH t.flightSeat
        WHERE t.booking.id IN :bookingIds
        ORDER BY t.id
    """)
    List<Ticket> findDetailedByBookingIdIn(@Param("bookingIds") List<Long> bookingIds);

    @Query("""
        SELECT COUNT(t) > 0
        FROM Ticket t
        JOIN t.booking b
        JOIN t.bookingLeg bl
        JOIN PassengerDocument pd ON pd.passenger = t.passenger
        WHERE bl.flight.id = :flightId
          AND pd.identityType = :identityType
          AND LOWER(TRIM(pd.identityNumber)) = LOWER(TRIM(:identityNumber))
          AND b.status IN (:activeStatuses)
    """)
    boolean existsActiveBookingForPassenger(
            @Param("flightId") Long flightId,
            @Param("identityType") DocumentType identityType,
            @Param("identityNumber") String identityNumber,
            @Param("activeStatuses") List<BookingStatus> activeStatuses
    );
}
