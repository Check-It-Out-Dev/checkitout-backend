-- liquibase formatted sql
-- changeset system:deactivate-all-company-users

-- ============================================================================
-- COMPANY USER DEACTIVATION: Reset all existing company accounts to IN_VALIDATION.
-- This forces all company users through the new NIP verification flow.
-- Token version increment invalidates existing sessions (forces re-login).
-- Excludes DELETED and TO_BE_DELETED users.
-- ============================================================================

UPDATE "user"
SET account_status = 'IN_VALIDATION',
    token_version = token_version + 1,
    last_update_time = CURRENT_TIMESTAMP
WHERE user_type = 'COMPANY'
  AND account_status NOT IN ('DELETED', 'TO_BE_DELETED');

-- rollback UPDATE "user" SET account_status = 'ACTIVE', token_version = token_version + 1 WHERE user_type = 'COMPANY' AND account_status = 'IN_VALIDATION';
