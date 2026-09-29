package com.thanh.backend.booking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class ContactInfoRequest {

    @NotBlank(message = "Contact full name is required")
    private String fullName;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email format is invalid")
    private String email;

    @NotBlank(message = "Contact phone is required")
    private String phone;
}
