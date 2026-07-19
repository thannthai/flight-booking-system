package com.thanh.backend.aircraft.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AircraftRequest {

    @NotNull(message = "Airline ID is required")
    private Long airlineId;

    @NotBlank(message = "Model is required")
    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

    @NotNull(message = "Total seats is required")
    @Min(value = 1, message = "Total seats must be at least 1")
    private Integer totalSeats;

    @NotNull(message = "Business seats is required")
    @Min(value = 0, message = "Business seats cannot be negative")
    private Integer businessSeats;

    @NotNull(message = "Economy seats is required")
    @Min(value = 0, message = "Economy seats cannot be negative")
    private Integer economySeats;

    @NotBlank(message = "Registration number is required")
    @Size(max = 20, message = "Registration number must not exceed 20 characters")
    private String registrationNo;

    public String getModel() {
        return model != null ? model.strip() : null;
    }

    public String getRegistrationNo() {
        return registrationNo != null ? registrationNo.strip().toUpperCase() : null;
    }
}
