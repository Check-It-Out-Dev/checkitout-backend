-- liquibase formatted sql
-- changeset system:add-newest-consents-accepted

-- Add newest_consents_accepted column to user table.
-- Defaults to false so existing users enter the re-consent flow on first login after deployment.
-- The 38-day grace period starts from the legal_document.published_at date.

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS newest_consents_accepted BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN "user".newest_consents_accepted IS
    'True if the user has accepted the latest version of all required legal documents. Set to false by migration when new document versions are introduced.';

-- rollback ALTER TABLE "user" DROP COLUMN IF EXISTS newest_consents_accepted;
