-- liquibase formatted sql
-- changeset system:add-email-verification-tracking

-- Add email verification columns to user table
ALTER TABLE "user"
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN email_verification_sent_at TIMESTAMP,
ADD COLUMN email_verified_at TIMESTAMP;

-- Add column comments for documentation
COMMENT ON COLUMN "user".email_verified IS 'Email verification status synced from Firebase during login';
COMMENT ON COLUMN "user".email_verification_sent_at IS 'Last verification email sent timestamp for rate limiting';
COMMENT ON COLUMN "user".email_verified_at IS 'Timestamp when email was verified (audit trail)';

-- rollback CREATE TABLE IF NOT EXISTS email_verification_backup AS SELECT id, email_verified, email_verification_sent_at, email_verified_at FROM "user" WHERE email_verified = TRUE OR email_verified_at IS NOT NULL;
-- rollback ALTER TABLE "user" DROP COLUMN email_verified, DROP COLUMN email_verification_sent_at, DROP COLUMN email_verified_at;

-- changeset system:add-email-verification-index runInTransaction:false
-- Non-blocking index creation (CONCURRENTLY prevents table locking)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_email_verified ON "user"(email_verified);

-- rollback DROP INDEX IF EXISTS idx_user_email_verified;
