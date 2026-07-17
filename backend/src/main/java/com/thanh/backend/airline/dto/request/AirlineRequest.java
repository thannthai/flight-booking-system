package com.thanh.backend.airline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlineRequest {

    @NotBlank(message = "Airline code is required")
    @Size(max = 10, message = "Airline code must not exceed 10 characters")
    private String code;

    @NotBlank(message = "Airline name is required")
    @Size(max = 255, message = "Airline name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Logo URL must not exceed 500 characters")
    private String logoUrl;

    @NotNull(message = "Free baggage status is required")
    private Boolean freeBaggage;

    @NotNull(message = "Extra baggage fee is required")
    @PositiveOrZero(message = "Extra baggage fee must be zero or positive")
    private BigDecimal extraBaggageFee;

    public String getCode() {
        return code != null ? code.strip().toUpperCase() : null;
    }

    public String getName() {
        return name != null ? name.strip() : null;
    }

    public String getLogoUrl() {
        return logoUrl != null ? logoUrl.strip() : null;
    }
}
