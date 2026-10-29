package com.thanh.backend.flight.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository
public class DemoFlightRepository {
    private final JdbcTemplate db;
    public DemoFlightRepository(JdbcTemplate db) { this.db = db; }
    public Long airline(String code) {
        var ids = db.queryForList("SELECT id FROM airlines WHERE code=?", Long.class, code);
        return ids.isEmpty() ? null : ids.getFirst();
    }
    public record Airport(long id, ZoneId zone) { }
    public Airport airport(String code) {
        return db.query("SELECT id,timezone FROM airports WHERE iata_code=?", (rs,n) ->
                new Airport(rs.getLong(1), ZoneId.of(rs.getString(2))), code).stream().findFirst().orElseThrow();
    }
    public void serializeBudget() {
        // Transaction-scoped, shared by generator instances; never relies on a pooled session lock.
        db.query("SELECT pg_advisory_xact_lock(724193)", rs -> { return null; });
    }
    public boolean exists(String number) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM flights WHERE flight_no=?)", Boolean.class, number));
    }
    public boolean overlaps(long aircraft, Instant departure, Instant arrival, String outboundNo, String returnNo) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM flights WHERE aircraft_id=? AND status <> 'CANCELLED'
                 AND departure_time < ? AND arrival_time > ? AND flight_no NOT IN (?,?))
                """, Boolean.class, aircraft, OffsetDateTime.ofInstant(arrival.plusSeconds(3600), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(departure.minusSeconds(3600), ZoneOffset.UTC), outboundNo, returnNo));
    }
    public void fallback(long airline, String registration) {
        db.update("""
                INSERT INTO aircrafts(airline_code_id,model,total_seats,business_seats,economy_seats,registration_no)
                VALUES (?,'DEMO regional layout',180,24,156,?) ON CONFLICT (registration_no) DO NOTHING
                """, airline, registration);
    }
    public boolean budget(LocalDate day, LocalDate today, int flights, int seats, int capacity) {
        var start = today.atStartOfDay(DemoFlightTemplates.ZONE).toOffsetDateTime();
        var end = today.plusDays(30).atStartOfDay(DemoFlightTemplates.ZONE).toOffsetDateTime();
        int usedSeats = db.queryForObject("""
                SELECT count(*) FROM flight_seats s JOIN flights f ON s.flight_id=f.id
                WHERE f.source='DEMO' AND f.departure_time>=? AND f.departure_time<?
                """, Integer.class, start, end);
        int usedFlights = db.queryForObject("""
                SELECT count(*) FROM flights WHERE source='DEMO'
                AND departure_time>=? AND departure_time<?
                """, Integer.class, day.atStartOfDay(DemoFlightTemplates.ZONE).toOffsetDateTime(),
                day.plusDays(1).atStartOfDay(DemoFlightTemplates.ZONE).toOffsetDateTime());
        return usedFlights < flights && usedSeats + capacity <= seats;
    }
    public Long insert(String no, long airline, long aircraft, long origin, long dest,
                       Instant departure, Instant arrival, java.math.BigDecimal price) {
        return db.query("""
                INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,
                 departure_time,arrival_time,status,base_price,source)
                VALUES (?,?,?,?,?,?,?,'SCHEDULED',?,'DEMO')
                ON CONFLICT (flight_no) WHERE source='DEMO' DO NOTHING RETURNING id
                """, (rs,n) -> rs.getLong(1), no, airline, aircraft, origin, dest,
                OffsetDateTime.ofInstant(departure,ZoneOffset.UTC), OffsetDateTime.ofInstant(arrival,ZoneOffset.UTC), price)
                .stream().findFirst().orElse(null);
    }
    public void seats(long flightId, List<com.thanh.backend.flight.entity.FlightSeat> seats) {
        db.batchUpdate("INSERT INTO flight_seats(flight_id,seat_no,cabin_class,status,price) VALUES (?,?,?,'AVAILABLE',?)",
                seats, 200, (ps, seat) -> {
                    ps.setLong(1,flightId); ps.setString(2,seat.getSeatNo());
                    ps.setString(3,seat.getCabinClass().name()); ps.setBigDecimal(4,seat.getPrice());
                });
    }
}
