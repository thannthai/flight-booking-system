package com.thanh.backend.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Getter
@Setter
@Configuration
@ConfigurationProperties("app.payment")
public class DemoPaymentConfiguration {
    private String mode = "PAYOS";
    private String allowedJdbcUrl = "";
    private boolean accountsEnabled;
    private String accountPassword = "";
    private final Environment environment;
    public DemoPaymentConfiguration(Environment environment) { this.environment = environment; }
    public boolean isDemo() { return "DEMO".equals(mode); }
    public boolean isDemoAccount(com.thanh.backend.auth.entity.User user) {
        return "A".equals(user.getDemoAlias()) || "B".equals(user.getDemoAlias());
    }
    public boolean usesDemoPayment(com.thanh.backend.auth.entity.User user) {
        return isDemo() && user.getRole() == com.thanh.backend.auth.enums.UserRole.ROLE_USER
                && isDemoAccount(user);
    }
    public void assertDemo() {
        if (!isDemo() || !environment.acceptsProfiles(Profiles.of("demo | dev"))
                || environment.acceptsProfiles(Profiles.of("prod | production"))
                || allowedJdbcUrl.isBlank() || !allowedJdbcUrl.equals(environment.getProperty("spring.datasource.url")))
            throw new IllegalStateException("Demo payment target is not allowed");
    }
    @Bean ApplicationRunner paymentModeGuard() {
        return args -> {
            if (!"PAYOS".equals(mode) && !isDemo()) throw new IllegalStateException("Invalid payment mode");
            if (isDemo()) assertDemo();
            if (accountsEnabled && (!isDemo() || accountPassword.length() < 16))
                throw new IllegalStateException("Demo accounts require demo mode and a configured password of at least 16 characters");
        };
    }
}
