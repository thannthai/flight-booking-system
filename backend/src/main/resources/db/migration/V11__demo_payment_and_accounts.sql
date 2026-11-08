ALTER TABLE payments DROP CONSTRAINT payments_provider_check;
ALTER TABLE payments ADD CONSTRAINT payments_provider_check
    CHECK (provider IN ('PAYOS','VIETQR','STRIPE','VNPAY','SEPAY','DEMO'));
-- A marker prevents claiming an existing real user as a public demo account.
ALTER TABLE users ADD COLUMN demo_alias VARCHAR(1) UNIQUE;
ALTER TABLE users ADD CONSTRAINT users_demo_alias_check CHECK (demo_alias IN ('A','B'));
