package com.thanh.backend.airport.mapper;

import com.thanh.backend.airport.dto.request.AirportRequest;
import com.thanh.backend.airport.dto.response.AirportResponse;
import com.thanh.backend.airport.entity.Airport;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AirportMapper {

    Airport toEntity(AirportRequest request);

    AirportResponse toResponse(Airport airport);

    void updateEntityFromRequest(AirportRequest request, @MappingTarget Airport airport);
}
