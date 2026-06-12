-- liquibase formatted sql
-- changeset system:add-token-version-column
-- Add token_version column to users table for immediate session invalidation
-- When admin changes user status (ban/unban/activate), this version is incremented
-- JwtAuthenticationFilter validates token version to detect stale sessions
-- Frontend detects mismatch and triggers silent refresh without re-login

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 1;

-- Add comment explaining the column purpose
COMMENT ON COLUMN "user".token_version IS 'Token version for immediate session invalidation. Incremented when account status changes to force token refresh.';

-- rollback ALTER TABLE "user" DROP COLUMN IF EXISTS token_version;
