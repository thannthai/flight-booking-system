package com.thanh.backend.payment.repository;

import com.thanh.backend.payment.entity.Payment;
import com.thanh.backend.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByOrderCode(Long orderCode);

    Optional<Payment> findByBookingIdAndStatus(@Param("bookingId") Long bookingId, @Param("status") PaymentStatus status);

    Optional<Payment> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

    @Query(value = "SELECT nextval('payment_order_code_seq')", nativeQuery = true)
    Long getNextOrderCode();
}
