package com.thanh.backend.booking.mapper;

import com.thanh.backend.booking.dto.response.AddOnResponse;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.dto.response.PassengerResponse;
import com.thanh.backend.booking.dto.response.TicketResponse;
import com.thanh.backend.booking.entity.AddOnService;
import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.Passenger;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.flight.dto.response.FlightSeatResponse;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.mapper.FlightMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {FlightMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @org.mapstruct.Builder(disableBuilder = true)
)
public interface BookingMapper {

    @Mapping(target = "guardianId", source = "passenger.guardian.id")
    PassengerResponse toPassengerResponse(Passenger passenger);

    FlightSeatResponse toSeatResponse(FlightSeat flightSeat);

    @Mapping(target = "flight", source = "ticket.bookingLeg.flight")
    @Mapping(target = "seat", source = "ticket.flightSeat")
    @Mapping(target = "addOns", source = "addOns")
    TicketResponse toTicketResponse(Ticket ticket, List<AddOnResponse> addOns);

    AddOnResponse toAddOnResponse(AddOnService addOnService);

    @Mapping(target = "passengers", source = "passengers")
    @Mapping(target = "tickets", source = "tickets")
    BookingResponse toResponse(Booking booking, List<PassengerResponse> passengers, List<TicketResponse> tickets);
}
