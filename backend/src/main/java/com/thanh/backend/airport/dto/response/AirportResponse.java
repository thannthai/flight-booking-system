package com.thanh.backend.airport.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirportResponse {
    private Long id;
    private String iataCode;
    private String name;
    private String city;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
