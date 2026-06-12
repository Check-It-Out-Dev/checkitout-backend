-- liquibase formatted sql
-- changeset system:email-case-insensitive-unique

-- Drop the existing case-sensitive unique constraint from the table definition
ALTER TABLE "user" DROP CONSTRAINT IF EXISTS user_email_key;

-- Create a case-insensitive unique index
CREATE UNIQUE INDEX idx_user_email_unique_lower ON "user"(LOWER(email));
