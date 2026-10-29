package com.thanh.backend.flight.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Data
@ConfigurationProperties("app.demo-flights")
public class DemoFlightProperties {
    private boolean enabled;
    private String allowedJdbcUrl = "";
    private int windowDays = 30;
    private long intervalMs = 3600000;
    private int maxFlightsPerDay = 12;
    private int maxWindowSeats = 90000;
    private List<Schedule> schedules = new ArrayList<>();
    @Data public static class Schedule {
        private LocalDate validFrom;
        private List<Airline> airlines = new ArrayList<>();
    }
    @Data public static class Airline {
        private String code;
        private List<String> aircraft = new ArrayList<>();
        private boolean fallback = true;
        private List<Route> routes = new ArrayList<>();
    }
    @Data public static class Route {
        private String outboundSlot;
        private String returnSlot;
        private String origin;
        private String destination;
        private LocalTime departure;
        private int durationMinutes;
        private int turnaroundMinutes = 60;
        private BigDecimal price;
    }
}
