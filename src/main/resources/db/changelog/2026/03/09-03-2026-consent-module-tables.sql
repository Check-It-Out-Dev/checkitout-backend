-- liquibase formatted sql
-- changeset system:consent-module-tables

-- ============================================================================
-- LEGAL CONSENT MODULE: Tables for mandatory legal document acceptance
-- Separate from existing consent_* tables (marketing/analytics)
-- ============================================================================

-- Sequences for Hibernate pooled-lo optimizer
CREATE SEQUENCE IF NOT EXISTS legal_document_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS consent_record_seq START WITH 1 INCREMENT BY 50 CACHE 50;

-- Legal document metadata table
CREATE TABLE IF NOT EXISTS public.legal_document (
    id BIGINT PRIMARY KEY DEFAULT nextval('legal_document_seq'),
    type VARCHAR(50) NOT NULL CHECK (type IN (
        'COOKIE_POLICY', 'TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'PROFESSIONAL_CHARACTER_DECLARATION'
    )),
    language VARCHAR(5) NOT NULL CHECK (language IN ('pl', 'en')),
    version INTEGER NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    document_url TEXT NOT NULL,
    published_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_legal_document_type_lang_version UNIQUE (type, language, version)
);

CREATE INDEX IF NOT EXISTS idx_legal_document_type_lang
    ON public.legal_document (type, language, version DESC);

COMMENT ON TABLE legal_document IS 'Stores metadata for each version of each legal document (ToS, Privacy Policy, Cookie Policy)';

-- Consent record table - every individual acceptance event
CREATE TABLE IF NOT EXISTS public.consent_record (
    id BIGINT PRIMARY KEY DEFAULT nextval('consent_record_seq'),
    user_id BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    document_id BIGINT NOT NULL REFERENCES legal_document(id) ON DELETE RESTRICT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_agent TEXT,
    ip_address INET,
    is_trusted BOOLEAN,
    consent_proof JSONB,
    source VARCHAR(30) NOT NULL CHECK (source IN (
        'REGISTRATION', 'OAUTH_REGISTRATION', 'SOCIAL_REGISTRATION',
        'LOGIN_PROMPT', 'COOKIE_BANNER', 'SETTINGS', 'ACCOUNT_DELETION'
    ))
);

-- Index for user consent lookups
CREATE INDEX IF NOT EXISTS idx_consent_record_user_document
    ON public.consent_record (user_id, document_id, timestamp DESC);

-- Partial index for weekly anonymous consent cleanup cron
CREATE INDEX IF NOT EXISTS idx_consent_record_anonymous_cleanup
    ON public.consent_record (timestamp)
    WHERE user_id IS NULL;

COMMENT ON TABLE consent_record IS 'Immutable event log of legal document acceptance events. user_id is NULL for anonymous cookie consents.';

-- rollback DROP TABLE IF EXISTS consent_record CASCADE;
-- rollback DROP TABLE IF EXISTS legal_document CASCADE;
-- rollback DROP SEQUENCE IF EXISTS consent_record_seq;
-- rollback DROP SEQUENCE IF EXISTS legal_document_seq;
