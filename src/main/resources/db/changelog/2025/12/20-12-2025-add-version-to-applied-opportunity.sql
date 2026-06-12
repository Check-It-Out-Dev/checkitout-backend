-- Liquibase formatted SQL
-- changeset norbert:add-version-to-applied-opportunity-table

-- Add JPA @Version column to applied_opportunity table for optimistic locking protection
-- Prevents data loss when influencer AND company concurrently update the same opportunity
ALTER TABLE applied_opportunity 
ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Rollback
--rollback ALTER TABLE applied_opportunity DROP COLUMN version;
