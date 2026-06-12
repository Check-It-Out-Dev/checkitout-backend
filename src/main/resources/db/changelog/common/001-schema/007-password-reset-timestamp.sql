-- liquibase formatted sql

-- changeset norbert:add-password-reset-timestamp
-- comment: Add password_reset_sent_at column to users table for duplicate request prevention (HIGH-001 fix)

ALTER TABLE "user" ADD COLUMN password_reset_sent_at TIMESTAMP;

-- Create index for performance on cooldown check queries
CREATE INDEX idx_user_password_reset_sent_at ON "user"(password_reset_sent_at)
    WHERE password_reset_sent_at IS NOT NULL;

-- rollback ALTER TABLE "user" DROP COLUMN password_reset_sent_at;
-- rollback DROP INDEX idx_user_password_reset_sent_at;
