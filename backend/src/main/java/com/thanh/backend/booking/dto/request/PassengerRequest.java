package com.thanh.backend.booking.dto.request;

import com.thanh.backend.booking.enums.DocumentType;
import com.thanh.backend.booking.enums.Gender;
import com.thanh.backend.booking.enums.PassengerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerRequest {

    @NotBlank(message = "Passenger full name is required")
    private String fullName;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    private Gender gender;

    private String nationality;

    @NotNull(message = "Passenger type is required")
    private PassengerType passengerType;

    private Integer guardianPassengerIndex;

    private DocumentType identityType;
    private String identityNumber;
    private LocalDate identityExpiry;
}
