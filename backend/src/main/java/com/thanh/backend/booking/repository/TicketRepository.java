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
