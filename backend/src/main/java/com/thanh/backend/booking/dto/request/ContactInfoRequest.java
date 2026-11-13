package com.thanh.backend.booking.dto.request;

import jakarta.validation.constraints.Size;
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
    @Size(max = 255, message = "Contact full name is too long")
    private String fullName;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email format is invalid")
    @Size(max = 255, message = "Contact email is too long")
    private String email;

    @NotBlank(message = "Contact phone is required")
    @Size(max = 20, message = "Contact phone is too long")
    private String phone;
}
