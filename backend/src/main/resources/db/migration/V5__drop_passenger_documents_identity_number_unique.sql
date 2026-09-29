-- Migration V5: Drop global unique constraint on identity_number in passenger_documents table
-- Allows different users to save the same passenger document in their personal directory.
ALTER TABLE passenger_documents DROP CONSTRAINT IF EXISTS passenger_documents_identity_number_key;
