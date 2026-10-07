-- Migration V8: Allow null user_id on passengers table
-- Allows detaching passengers from user's directory without breaking foreign key references in tickets table.
ALTER TABLE passengers ALTER COLUMN user_id DROP NOT NULL;
