-- ====================================================================
-- Migration V7: Add unaccent extension & unique constraints for flights and seats
-- Description:
-- 1. Enable 'unaccent' extension for accent-insensitive search
-- 2. Ensure no duplicate seat numbers exist on the same flight
-- 3. Ensure no duplicate flights exist with the same flight number and departure time
-- ====================================================================

-- 1. Extension unaccent for accent-insensitive search (airports, airlines, flights)
CREATE EXTENSION IF NOT EXISTS unaccent;

-- 2. Ensure each flight has strictly unique seat numbers (e.g. no duplicate '12A' on the same flight)
CREATE UNIQUE INDEX IF NOT EXISTS uq_flight_seats_flight_seat
    ON flight_seats (flight_id, seat_no);

-- 3. Ensure a flight number cannot depart multiple times at the exact same timestamp
CREATE UNIQUE INDEX IF NOT EXISTS uq_flights_flight_no_dep_time
    ON flights (flight_no, departure_time);
