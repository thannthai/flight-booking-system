-- Remove reminder_sent column from booking table
ALTER TABLE booking DROP COLUMN IF EXISTS reminder_sent;

-- Update check constraint on notifications table to include FLIGHT_REMINDER
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('BOOKING_CONFIRM', 'TICKET_ISSUED', 'PAYMENT_REMINDER', 'FLIGHT_REMINDER', 'FLIGHT_DELAYED', 'FLIGHT_CANCELLED', 'REFUND_CONFIRMATION'));

-- Update check constraint on passenger_documents table to use CITIZEN_ID and PASSPORT
ALTER TABLE passenger_documents DROP CONSTRAINT IF EXISTS passenger_documents_identity_type_check;
ALTER TABLE passenger_documents ADD CONSTRAINT passenger_documents_identity_type_check
    CHECK (identity_type IN ('CITIZEN_ID', 'PASSPORT'));
