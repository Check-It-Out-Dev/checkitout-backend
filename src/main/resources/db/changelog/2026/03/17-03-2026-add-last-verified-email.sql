-- liquibase formatted sql

-- changeset system:add-last-verified-email
-- comment: Track last verified email for secure step-up code delivery.
-- Step-up codes are always sent to this address, not the current (possibly unverified) email.
ALTER TABLE "user" ADD COLUMN last_verified_email VARCHAR(255);

-- Backfill: users with verified email have a proven address
UPDATE "user" SET last_verified_email = email WHERE email_verified = TRUE AND email IS NOT NULL;

-- rollback ALTER TABLE "user" DROP COLUMN last_verified_email;
