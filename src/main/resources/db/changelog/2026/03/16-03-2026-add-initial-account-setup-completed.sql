-- liquibase formatted sql
-- changeset system:add-initial-account-setup-completed

-- ============================================================================
-- ADD initial_account_setup_completed FLAG TO USER TABLE.
-- PostgreSQL is the source of truth for initial account setup state.
-- All users start at FALSE. Activation happens when emailVerified=true AND
-- profileComplete=true (checked at login sync and profile update).
-- ============================================================================

ALTER TABLE "user" ADD COLUMN initial_account_setup_completed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_user_initial_setup
    ON "user" (initial_account_setup_completed);

-- rollback ALTER TABLE "user" DROP COLUMN initial_account_setup_completed;
