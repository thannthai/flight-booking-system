package com.thanh.backend.flight.demo;

import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.*;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoFlightProperties.class)
public class DemoFlightConfiguration {
    private final DemoFlightProperties properties;
    private final Environment environment;
    public DemoFlightConfiguration(DemoFlightProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }
    @Bean("demoFlightClock") public Clock demoFlightClock() { return Clock.systemUTC(); }
    // Called before startup orchestration and again at every writer boundary.
    public void assertSafe() {
        if (!properties.isEnabled()
                || !environment.acceptsProfiles(Profiles.of("demo | dev"))
                || environment.acceptsProfiles(Profiles.of("prod | production"))
                || properties.getAllowedJdbcUrl().isBlank()
                || !properties.getAllowedJdbcUrl().equals(environment.getProperty("spring.datasource.url"))) {
            throw new IllegalStateException("Demo generator disabled or target environment not allowed");
        }
        if (properties.getWindowDays() != 30 || properties.getMaxFlightsPerDay() < 2
                || properties.getMaxFlightsPerDay() > 10 || properties.getMaxWindowSeats() < 180
                || properties.getMaxWindowSeats() > 90000 || properties.getIntervalMs() < 60000) {
            throw new IllegalStateException("Unsafe demo generation limits");
        }
    }
}
