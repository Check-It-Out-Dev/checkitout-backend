-- liquibase formatted sql
-- changeset system:widen-account-status-column

-- Widen account_status column from VARCHAR(20) to VARCHAR(50).
-- Required because BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS (37 chars) exceeds VARCHAR(20).
-- The CHECK constraint was already updated in add-blocked-terms-status-to-constraint
-- but the column width was not.

ALTER TABLE "user" ALTER COLUMN account_status TYPE VARCHAR(50);

-- rollback ALTER TABLE "user" ALTER COLUMN account_status TYPE VARCHAR(20);
