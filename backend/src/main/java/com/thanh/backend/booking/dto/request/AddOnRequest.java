package com.thanh.backend.booking.dto.request;

import jakarta.validation.constraints.Max;
import com.thanh.backend.booking.enums.AddOnType;
import jakarta.validation.constraints.NotNull;
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
public class AddOnRequest {

    @NotNull(message = "Add-on type is required")
    private AddOnType serviceType;

    @Max(value = 10, message = "Add-on quantity must not exceed 10")
    @Builder.Default
    private Integer quantity = 1;
}
