package com.thanh.backend.airport.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class AirportRequest {

    @NotBlank(message = "IATA code is required")
    @Size(min = 3, max = 10, message = "IATA code must be between 3 and 10 characters")
    private String iataCode;

    @NotBlank(message = "Airport name is required")
    @Size(max = 255, message = "Airport name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    @NotBlank(message = "Timezone is required")
    @Size(max = 50, message = "Timezone must not exceed 50 characters")
    private String timezone;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    public String getIataCode() {
        return iataCode != null ? iataCode.strip().toUpperCase() : null;
    }

    public String getName() {
        return name != null ? name.strip() : null;
    }

    public String getCity() {
        return city != null ? city.strip() : null;
    }

    public String getCountry() {
        return country != null ? country.strip() : null;
    }

    public String getTimezone() {
        return timezone != null ? timezone.strip() : null;
    }
}
