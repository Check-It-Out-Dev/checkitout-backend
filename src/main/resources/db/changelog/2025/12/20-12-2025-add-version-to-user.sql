-- Liquibase formatted SQL
-- changeset norbert:add-version-to-user-table

-- Add JPA @Version column to user table for optimistic locking protection
-- Prevents data loss from concurrent user updates by admins or user self-edits
ALTER TABLE "user" 
ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Rollback
--rollback ALTER TABLE "user" DROP COLUMN version;
