package com.thanh.backend.booking.mapper;

import com.thanh.backend.booking.dto.response.AddOnResponse;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.dto.response.PassengerResponse;
import com.thanh.backend.booking.dto.response.TicketResponse;
import com.thanh.backend.booking.entity.AddOnService;
import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.BookingCancellationReason;
import com.thanh.backend.booking.entity.Passenger;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.flight.dto.response.FlightSeatResponse;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.mapper.FlightMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingMapper {

    private final FlightMapper flightMapper;

    public PassengerResponse toPassengerResponse(Passenger passenger) {
        if (passenger == null) {
            return null;
        }
        return PassengerResponse.builder()
                .id(passenger.getId())
                .fullName(passenger.getFullName())
                .dateOfBirth(passenger.getDateOfBirth())
                .gender(passenger.getGender())
                .nationality(passenger.getNationality())
                .guardianId(passenger.getGuardian() != null ? passenger.getGuardian().getId() : null)
                .build();
    }

    public FlightSeatResponse toSeatResponse(FlightSeat flightSeat) {
        if (flightSeat == null) {
            return null;
        }
        return FlightSeatResponse.builder()
                .id(flightSeat.getId())
                .seatNo(flightSeat.getSeatNo())
                .cabinClass(flightSeat.getCabinClass())
                .price(flightSeat.getPrice())
                .status(flightSeat.getStatus())
                .build();
    }

    public TicketResponse toTicketResponse(Ticket ticket, List<AddOnResponse> addOns) {
        if (ticket == null && addOns == null) {
            return null;
        }
        TicketResponse.TicketResponseBuilder builder = TicketResponse.builder();
        if (ticket != null) {
            builder.id(ticket.getId())
                    .ticketNo(ticket.getTicketNo())
                    .passengerType(ticket.getPassengerType())
                    .price(ticket.getPrice())
                    .status(ticket.getStatus())
                    .issuedAt(ticket.getIssuedAt())
                    .seat(toSeatResponse(ticket.getFlightSeat()));

            if (ticket.getBookingLeg() != null && ticket.getBookingLeg().getFlight() != null) {
                builder.flight(flightMapper.toResponse(ticket.getBookingLeg().getFlight()));
            }

            if (ticket.getPassenger() != null) {
                builder.passengerId(ticket.getPassenger().getId())
                        .passengerName(ticket.getPassenger().getFullName())
                        .passenger(toPassengerResponse(ticket.getPassenger()));
            }
        }
        if (addOns != null) {
            builder.addOns(new ArrayList<>(addOns));
        }
        return builder.build();
    }

    public AddOnResponse toAddOnResponse(AddOnService addOnService) {
        if (addOnService == null) {
            return null;
        }
        return AddOnResponse.builder()
                .id(addOnService.getId())
                .serviceType(addOnService.getServiceType())
                .description(addOnService.getDescription())
                .price(addOnService.getPrice())
                .quantity(addOnService.getQuantity())
                .build();
    }

    public BookingResponse toResponse(Booking booking, List<PassengerResponse> passengers, List<TicketResponse> tickets) {
        if (booking == null) {
            return null;
        }
        return BookingResponse.builder()
                .id(booking.getId())
                .pnr(booking.getPnr())
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .contactPhone(booking.getContactPhone())
                .tripType(booking.getTripType())
                .baseAmount(booking.getBaseAmount())
                .taxAmount(booking.getTaxAmount())
                .discountAmount(booking.getDiscountAmount())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .cancellationReason(cancellationReason(booking))
                .paymentDeadline(booking.getPaymentDeadline())
                .createdAt(booking.getCreatedAt())
                .passengers(passengers != null ? new ArrayList<>(passengers) : null)
                .tickets(tickets != null ? new ArrayList<>(tickets) : null)
                .build();
    }
    private BookingCancellationReason cancellationReason(Booking booking) {
        if (booking.getStatus() != BookingStatus.CANCELLED) return null;
        // An authenticated actor takes precedence over free-text cancellation input.
        if (booking.getCancelledBy() != null) return BookingCancellationReason.USER_CANCELLED;
        if ("Payment timeout (deadline exceeded)".equals(booking.getCancelReason())) {
            return BookingCancellationReason.PAYMENT_TIMEOUT;
        }
        return BookingCancellationReason.OTHER;
    }

}
