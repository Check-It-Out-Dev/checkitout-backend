-- liquibase formatted sql
-- changeset system:add-blocked-terms-status-to-constraint

-- Add BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS status to the account_status CHECK constraint.
-- Applied automatically by daily cron after the 38-day grace period expires
-- for users who have not accepted updated legal documents.

-- Drop the existing constraint
ALTER TABLE "user" DROP CONSTRAINT IF EXISTS user_account_status_check;

-- Recreate with BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS status included
ALTER TABLE "user" ADD CONSTRAINT user_account_status_check
    CHECK (account_status IN (
        'INACTIVE', 'IN_VALIDATION', 'ACTIVE', 'BANNED',
        'BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS',
        'TO_BE_DELETED', 'DELETED'
    ));

COMMENT ON CONSTRAINT user_account_status_check ON "user" IS
    'Valid account statuses: INACTIVE, IN_VALIDATION, ACTIVE, BANNED, BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS (consent enforcement), TO_BE_DELETED, DELETED';

-- rollback ALTER TABLE "user" DROP CONSTRAINT IF EXISTS user_account_status_check; ALTER TABLE "user" ADD CONSTRAINT user_account_status_check CHECK (account_status IN ('INACTIVE', 'IN_VALIDATION', 'ACTIVE', 'BANNED', 'TO_BE_DELETED', 'DELETED'));
