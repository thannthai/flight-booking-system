-- Preserve historical data: do not silently relabel an existing CRAWLER flight.
-- If such rows exist, their provenance must be classified explicitly before upgrade.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM flights WHERE source = 'CRAWLER') THEN
        RAISE EXCEPTION 'Cannot remove CRAWLER source while legacy CRAWLER flights exist; explicit data classification is required';
    END IF;
END $$;

ALTER TABLE flights DROP CONSTRAINT flights_source_check;
ALTER TABLE flights ADD CONSTRAINT flights_source_check
    CHECK (source IN ('MANUAL', 'DEMO'));
