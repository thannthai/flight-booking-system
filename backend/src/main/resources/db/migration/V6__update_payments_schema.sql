-- ============================================================================
-- V6: Update payments and booking schema for payOS integration
-- ============================================================================

-- 1. Add payment_deadline to booking as Single Source of Truth
ALTER TABLE booking ADD COLUMN IF NOT EXISTS payment_deadline TIMESTAMPTZ;

-- Backfill existing bookings safely
UPDATE booking 
SET payment_deadline = created_at + INTERVAL '15 minutes' 
WHERE payment_deadline IS NULL;

ALTER TABLE booking ALTER COLUMN payment_deadline SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_booking_status_deadline 
ON booking(status, payment_deadline) 
WHERE status = 'PENDING';

-- 2. Create Sequence for payment order_code (starting from 1000000001)
CREATE SEQUENCE IF NOT EXISTS payment_order_code_seq START WITH 1000000001;

-- 3. Update payments table structure
ALTER TABLE payments ADD COLUMN IF NOT EXISTS order_code BIGINT UNIQUE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_url TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS qr_code TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS expired_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS callback_data TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_status VARCHAR(30) DEFAULT 'NONE';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_txn_id VARCHAR(255);

-- Extend column lengths if needed
ALTER TABLE payments ALTER COLUMN provider TYPE VARCHAR(50);
ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(50);

-- Set default nextval for order_code
ALTER TABLE payments ALTER COLUMN order_code SET DEFAULT nextval('payment_order_code_seq');

-- Update check constraints
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_provider_check;
ALTER TABLE payments ADD CONSTRAINT payments_provider_check 
CHECK (provider IN ('PAYOS', 'VIETQR', 'STRIPE', 'VNPAY', 'SEPAY'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check 
CHECK (status IN ('PENDING', 'SUCCESS', 'CANCELLED', 'EXPIRED', 'PAID_AFTER_TERMINAL'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_refund_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_refund_status_check 
CHECK (refund_status IN ('NONE', 'REFUND_REQUIRED', 'REFUNDED'));

-- 4. Partial Unique Index: Only at most 1 active PENDING payment per booking
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_single_pending_per_booking 
ON payments (booking_id) 
WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_payments_order_code ON payments(order_code);
