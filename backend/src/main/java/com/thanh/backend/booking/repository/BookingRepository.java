package com.thanh.backend.booking.repository;

import com.thanh.backend.booking.entity.Booking;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPnr(String pnr);

    boolean existsByPnr(String pnr);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    Page<Booking> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT b.id FROM Booking b WHERE b.status = 'PENDING' AND b.paymentDeadline <= :cutoffTime ORDER BY b.paymentDeadline ASC")
    List<Long> findExpiredPendingBookingIds(@Param("cutoffTime") OffsetDateTime cutoffTime, Pageable pageable);
}
