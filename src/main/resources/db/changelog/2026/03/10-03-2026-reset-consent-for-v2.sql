-- liquibase formatted sql
-- changeset system:reset-consent-for-v2

-- Reset consent flag for all existing users so they must re-accept v2 documents.
-- The 38-day grace period starts from the v2 documents' published_at timestamp.
UPDATE "user" SET newest_consents_accepted = false WHERE newest_consents_accepted = true;

-- rollback SELECT 1;
