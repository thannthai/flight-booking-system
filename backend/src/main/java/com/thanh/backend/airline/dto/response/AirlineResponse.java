package com.thanh.backend.airline.dto.response;

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
public class AirlineResponse {
    private Long id;
    private String code;
    private String name;
    private String logoUrl;
    private boolean freeBaggage;
    private BigDecimal extraBaggageFee;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
