ALTER TABLE payments
    ADD COLUMN bank_bin VARCHAR(255),
    ADD COLUMN account_number VARCHAR(255),
    ADD COLUMN account_name VARCHAR(255),
    ADD COLUMN transfer_description TEXT,
    ADD COLUMN transfer_amount NUMERIC(12, 2);
