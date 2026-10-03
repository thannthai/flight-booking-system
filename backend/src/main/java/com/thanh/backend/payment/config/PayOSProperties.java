package com.thanh.backend.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payos")
public class PayOSProperties {
    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String returnUrl;
    private String cancelUrl;
}
