package com.thanh.backend.booking.dto.response;

import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.TripType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private String pnr;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private TripType tripType;
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private OffsetDateTime paymentDeadline;
    private OffsetDateTime createdAt;
    private List<PassengerResponse> passengers;
    private List<TicketResponse> tickets;
}
