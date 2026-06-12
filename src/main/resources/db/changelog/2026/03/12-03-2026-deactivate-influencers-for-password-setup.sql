-- liquibase formatted sql
-- changeset system:deactivate-influencers-for-password-setup

-- ============================================================================
-- INFLUENCER USER DEACTIVATION: Reset all existing influencer accounts to IN_VALIDATION.
-- This forces all influencer users through the new email verification + password setup flow.
-- Token version increment invalidates existing sessions (forces re-login).
-- Excludes DELETED and TO_BE_DELETED users.
-- ============================================================================

UPDATE "user"
SET account_status = 'IN_VALIDATION',
    email_verified = false,
    email_verified_at = NULL,
    token_version = token_version + 1,
    last_update_time = CURRENT_TIMESTAMP
WHERE user_type = 'INFLUENCER'
  AND account_status NOT IN ('DELETED', 'TO_BE_DELETED');

-- rollback UPDATE "user" SET account_status = 'ACTIVE', email_verified = true, email_verified_at = CURRENT_TIMESTAMP, token_version = token_version + 1, last_update_time = CURRENT_TIMESTAMP WHERE user_type = 'INFLUENCER' AND account_status = 'IN_VALIDATION';
