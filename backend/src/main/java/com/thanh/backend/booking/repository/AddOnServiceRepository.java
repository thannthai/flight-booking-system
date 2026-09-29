package com.thanh.backend.booking.repository;

import com.thanh.backend.booking.entity.AddOnService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddOnServiceRepository extends JpaRepository<AddOnService, Long> {
    List<AddOnService> findByTicketId(Long ticketId);

    List<AddOnService> findByTicketIdIn(List<Long> ticketIds);
}
