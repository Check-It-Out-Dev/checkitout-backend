-- Liquibase formatted SQL
-- changeset norbert:add-version-to-partnership-opportunity-table

-- Add JPA @Version column to partnership_opportunity table for optimistic locking protection
-- Prevents data loss when multiple users concurrently modify the same partnership opportunity
ALTER TABLE partnership_opportunity 
ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Rollback
--rollback ALTER TABLE partnership_opportunity DROP COLUMN version;
