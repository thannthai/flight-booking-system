ALTER TABLE flights DROP CONSTRAINT flights_source_check;
ALTER TABLE flights ADD CONSTRAINT flights_source_check CHECK (source IN ('MANUAL', 'CRAWLER', 'DEMO'));
CREATE UNIQUE INDEX uq_demo_flight_no ON flights(flight_no) WHERE source = 'DEMO';
