package com.thanh.backend.booking.dto.response;

import com.thanh.backend.booking.enums.AddOnType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOnResponse {
    private Long id;
    private AddOnType serviceType;
    private String description;
    private BigDecimal price;
    private Integer quantity;
}
