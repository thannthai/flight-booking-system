package com.thanh.backend.booking.dto.request;

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

    @Builder.Default
    private Integer quantity = 1;
}
