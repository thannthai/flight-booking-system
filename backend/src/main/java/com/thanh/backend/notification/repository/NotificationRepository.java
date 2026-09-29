package com.thanh.backend.notification.repository;

import com.thanh.backend.notification.entity.Notification;
import com.thanh.backend.notification.enums.NotificationStatus;
import com.thanh.backend.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByBookingIdAndTypeAndStatus(Long bookingId, NotificationType type, NotificationStatus status);

    List<Notification> findByBookingId(Long bookingId);

    List<Notification> findByUserId(Long userId);
}
