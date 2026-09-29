package com.thanh.backend.booking.repository;

import com.thanh.backend.booking.entity.PassengerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PassengerDocumentRepository extends JpaRepository<PassengerDocument, Long> {
    List<PassengerDocument> findByPassengerId(Long passengerId);
}
