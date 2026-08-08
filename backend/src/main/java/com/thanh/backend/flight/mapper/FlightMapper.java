package com.thanh.backend.flight.mapper;

import com.thanh.backend.aircraft.mapper.AircraftMapper;
import com.thanh.backend.airline.mapper.AirlineMapper;
import com.thanh.backend.airport.mapper.AirportMapper;
import com.thanh.backend.flight.dto.request.FlightRequest;
import com.thanh.backend.flight.dto.response.FlightResponse;
import com.thanh.backend.flight.entity.Flight;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        uses = {AirlineMapper.class, AircraftMapper.class, AirportMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface FlightMapper {

    @Mapping(target = "airline", ignore = true)
    @Mapping(target = "aircraft", ignore = true)
    @Mapping(target = "origin", ignore = true)
    @Mapping(target = "dest", ignore = true)
    Flight toEntity(FlightRequest request);

    FlightResponse toResponse(Flight flight);

    @Mapping(target = "airline", ignore = true)
    @Mapping(target = "aircraft", ignore = true)
    @Mapping(target = "origin", ignore = true)
    @Mapping(target = "dest", ignore = true)
    void updateEntityFromRequest(FlightRequest request, @MappingTarget Flight flight);
}
