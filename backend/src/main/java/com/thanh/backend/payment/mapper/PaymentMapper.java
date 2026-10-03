package com.thanh.backend.payment.mapper;

import com.thanh.backend.payment.dto.response.PaymentResponse;
import com.thanh.backend.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @org.mapstruct.Builder(disableBuilder = true)
)
public interface PaymentMapper {

    @Mapping(target = "bookingId", source = "booking.id")
    PaymentResponse toResponse(Payment payment);
}
