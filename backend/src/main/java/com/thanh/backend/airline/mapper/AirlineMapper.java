package com.thanh.backend.airline.mapper;

import com.thanh.backend.airline.dto.request.AirlineRequest;
import com.thanh.backend.airline.dto.response.AirlineResponse;
import com.thanh.backend.airline.entity.Airline;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AirlineMapper {

    Airline toEntity(AirlineRequest request);

    AirlineResponse toResponse(Airline airline);

    void updateEntityFromRequest(AirlineRequest request, @MappingTarget Airline airline);
}
