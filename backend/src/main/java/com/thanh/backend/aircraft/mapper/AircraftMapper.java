package com.thanh.backend.aircraft.mapper;

import com.thanh.backend.aircraft.dto.request.AircraftRequest;
import com.thanh.backend.aircraft.dto.response.AircraftResponse;
import com.thanh.backend.aircraft.entity.Aircraft;
import com.thanh.backend.airline.mapper.AirlineMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = {AirlineMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AircraftMapper {

    @Mapping(target = "airline", ignore = true)
    Aircraft toEntity(AircraftRequest request);

    AircraftResponse toResponse(Aircraft aircraft);

    @Mapping(target = "airline", ignore = true)
    void updateEntityFromRequest(AircraftRequest request, @MappingTarget Aircraft aircraft);
}
