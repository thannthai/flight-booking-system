package com.thanh.backend.flight.demo;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class DemoFlightTemplates {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public record Pair(DemoFlightProperties.Airline airline, DemoFlightProperties.Route route,
                       LocalDate date) {
        public String outboundNo() { return number(route.getOutboundSlot(), date); }
        public String returnNo() { return number(route.getReturnSlot(), date); }
    }
    private static String number(String slot, LocalDate date) {
        return "DM" + slot + date.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
    }
    public void validate(DemoFlightProperties p) {
        LocalDate previous = null;
        Map<String, String> identities = new HashMap<>();
        for (var schedule : p.getSchedules()) {
            if (schedule.getValidFrom() == null || previous != null && !schedule.getValidFrom().isAfter(previous)
                    || schedule.getAirlines().isEmpty() || schedule.getAirlines().size() > (p.getMaxFlightsPerDay()/2)*15)
                throw new IllegalArgumentException("Invalid demo schedule dates/roster");
            previous = schedule.getValidFrom();
            Set<String> codes = new HashSet<>();
            Set<String> slots = new HashSet<>();
            for (var airline : schedule.getAirlines()) {
                if (airline.getCode() == null || !airline.getCode().matches("[A-Z0-9]{1,10}")
                        || !codes.add(airline.getCode()) || airline.getRoutes().size() != 2)
                    throw new IllegalArgumentException("Invalid demo airline configuration");
                for (var r : airline.getRoutes()) {
                    if (r.getOrigin() == null || r.getDestination() == null || r.getOrigin().equals(r.getDestination())
                            || r.getDeparture() == null || r.getDurationMinutes() <= 0 || r.getDurationMinutes() > 720
                            || r.getTurnaroundMinutes() < 60 || r.getPrice() == null || r.getPrice().signum() <= 0
                            || r.getPrice().compareTo(new java.math.BigDecimal("100000000")) > 0)
                        throw new IllegalArgumentException("Invalid demo route configuration");
                    for (String slot : Arrays.asList(r.getOutboundSlot(), r.getReturnSlot())) {
                        if (slot == null || !slot.matches("[0-9A-Z]{2}") || !slots.add(slot)) throw new IllegalArgumentException("Invalid demo slot");
                        String owner = airline.getCode() + ":" + r.getOrigin() + ":" + r.getDestination() + ":" + (slot.equals(r.getOutboundSlot()) ? "OUT" : "RETURN");
                        String old = identities.putIfAbsent(slot, owner);
                        if (old != null && !old.equals(owner)) throw new IllegalArgumentException("Reused demo identity");
                    }
                    if (r.getOutboundSlot().equals(r.getReturnSlot())) throw new IllegalArgumentException("Duplicate demo slot");
                }
            }
        }
        if (p.getSchedules().isEmpty()) throw new IllegalArgumentException("Missing demo templates");
    }
    public List<Pair> pairs(DemoFlightProperties p, LocalDate date) {
        var schedule = p.getSchedules().stream().filter(s -> !s.getValidFrom().isAfter(date)).reduce((a,b) -> b).orElse(null);
        if (schedule == null) return List.of();
        var roster = schedule.getAirlines();
        // Route-major order gives each configured airline a turn before a second route.
        List<Pair> candidates = new ArrayList<>();
        for (int route = 0; route < 2; route++) {
            for (var airline : roster) candidates.add(new Pair(airline, airline.getRoutes().get(route), date));
        }
        int count = Math.min(p.getMaxFlightsPerDay() / 2, candidates.size());
        long day = java.time.temporal.ChronoUnit.DAYS.between(schedule.getValidFrom(), date);
        List<Pair> result = new ArrayList<>();
        // All routes run daily when budget permits; larger rosters rotate deterministically.
        long start = candidates.size() <= count ? 0 : day * count;
        for (int i = 0; i < count; i++) {
            result.add(candidates.get((int) Math.floorMod(start + i, candidates.size())));
        }
        return result;
    }
}
