--liquibase formatted sql
--changeset owasp-p2:add-version-to-applied-opportunity-content
ALTER TABLE applied_opportunity_content ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
