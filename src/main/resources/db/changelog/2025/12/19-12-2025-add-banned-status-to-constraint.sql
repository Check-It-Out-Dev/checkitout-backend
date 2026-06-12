-- liquibase formatted sql
-- changeset system:add-banned-status-to-constraint

-- Add BANNED status to the account_status CHECK constraint
-- This is required for the ban/suspension feature to work properly

-- First, drop the existing constraint
ALTER TABLE "user" DROP CONSTRAINT IF EXISTS user_account_status_check;

-- Recreate with BANNED status included
ALTER TABLE "user" ADD CONSTRAINT user_account_status_check
    CHECK (account_status IN ('INACTIVE', 'IN_VALIDATION', 'ACTIVE', 'BANNED', 'TO_BE_DELETED', 'DELETED'));

COMMENT ON CONSTRAINT user_account_status_check ON "user" IS
    'Valid account statuses: INACTIVE (new/disabled), IN_VALIDATION (pending review), ACTIVE (fully functional), BANNED (suspended - can login but not perform actions), TO_BE_DELETED (marked for deletion), DELETED (permanently deleted)';

-- rollback ALTER TABLE "user" DROP CONSTRAINT IF EXISTS user_account_status_check; ALTER TABLE "user" ADD CONSTRAINT user_account_status_check CHECK (account_status IN ('INACTIVE', 'IN_VALIDATION', 'ACTIVE', 'TO_BE_DELETED', 'DELETED'));
