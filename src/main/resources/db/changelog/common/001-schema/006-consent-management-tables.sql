-- ============================================================================
-- CONSENT MANAGEMENT TABLES
-- ============================================================================
-- Simple GDPR-compliant consent management system
-- Tracks consent definitions, versions, and user consent events
-- ============================================================================

-- liquibase formatted sql
-- changeset system:consent-management-tables

-- Consent definitions table - types of consent (marketing, analytics, etc.)
CREATE TABLE IF NOT EXISTS public.consent_definition (
    id BIGINT PRIMARY KEY DEFAULT nextval('consent_definition_seq'),
    consent_type VARCHAR(100) NOT NULL UNIQUE, -- 'marketing', 'analytics', 'cookies', etc.
    name VARCHAR(255) NOT NULL,
    description TEXT,
    regulation_reference VARCHAR(100), -- GDPR Article 6(1)(a), etc.
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Consent versions table - versions of consent text/policies
CREATE TABLE IF NOT EXISTS public.consent_version (
    id BIGINT PRIMARY KEY DEFAULT nextval('consent_version_seq'),
    consent_definition_id BIGINT NOT NULL REFERENCES consent_definition(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    consent_text TEXT NOT NULL,
    policy_url VARCHAR(500),
    effective_from TIMESTAMP NOT NULL,
    effective_until TIMESTAMP, -- NULL if current version
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255),
    UNIQUE (consent_definition_id, version)
);

-- User consents table - event log of user consent actions
CREATE TABLE IF NOT EXISTS public.user_consent (
    id BIGINT PRIMARY KEY DEFAULT nextval('user_consent_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    consent_version_id BIGINT NOT NULL REFERENCES consent_version(id) ON DELETE RESTRICT,
    action VARCHAR(20) NOT NULL CHECK (action IN ('GRANTED', 'WITHDRAWN', 'UPDATED')),
    consent_given BOOLEAN NOT NULL,
    ip_address INET,
    user_agent TEXT,
    collection_method VARCHAR(100) DEFAULT 'web_form', -- 'web_form', 'api', 'import', etc.
    legal_basis VARCHAR(100) DEFAULT 'consent', -- For GDPR: 'consent', 'legitimate_interest', etc.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Create index for user consent lookups
CREATE INDEX IF NOT EXISTS idx_user_consent_lookup ON public.user_consent (user_id, consent_version_id, created_at DESC);

-- Current consent status view - materialized for performance
CREATE TABLE IF NOT EXISTS public.user_current_consent (
    user_id BIGINT NOT NULL,
    consent_definition_id BIGINT NOT NULL,
    consent_version_id BIGINT NOT NULL,
    consent_given BOOLEAN NOT NULL,
    granted_at TIMESTAMP,
    withdrawn_at TIMESTAMP,
    last_updated TIMESTAMP NOT NULL,
    
    PRIMARY KEY (user_id, consent_definition_id),
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    FOREIGN KEY (consent_definition_id) REFERENCES consent_definition(id) ON DELETE CASCADE,
    FOREIGN KEY (consent_version_id) REFERENCES consent_version(id) ON DELETE RESTRICT
);

-- Comments
COMMENT ON TABLE consent_definition IS 'Defines types of consent that can be requested from users';
COMMENT ON TABLE consent_version IS 'Stores different versions of consent text and policies';
COMMENT ON TABLE user_consent IS 'Immutable event log of all user consent actions';
COMMENT ON TABLE user_current_consent IS 'Current consent status for efficient querying';

-- rollback DROP TABLE IF EXISTS user_current_consent CASCADE;
-- rollback DROP TABLE IF EXISTS user_consent CASCADE;
-- rollback DROP TABLE IF EXISTS consent_version CASCADE;
-- rollback DROP TABLE IF EXISTS consent_definition CASCADE;
