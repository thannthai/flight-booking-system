package com.thanh.backend.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
@RequiredArgsConstructor
public class PayOSConfig {

    private final PayOSProperties payOSProperties;

    @Bean
    public PayOS payOS() {
        return new PayOS(
                payOSProperties.getClientId(),
                payOSProperties.getApiKey(),
                payOSProperties.getChecksumKey()
        );
    }
}
