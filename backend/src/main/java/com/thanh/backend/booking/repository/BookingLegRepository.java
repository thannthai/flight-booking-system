package com.thanh.backend.booking.repository;

import com.thanh.backend.booking.entity.BookingLeg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingLegRepository extends JpaRepository<BookingLeg, Long> {
    List<BookingLeg> findByBookingId(Long bookingId);
}
