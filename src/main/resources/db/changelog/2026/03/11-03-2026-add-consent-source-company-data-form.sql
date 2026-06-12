-- liquibase formatted sql
-- changeset system:add-consent-source-company-data-form

-- ============================================================================
-- Add COMPANY_DATA_FORM as a valid consent source for professional character
-- declaration consents collected during the NIP verification flow.
-- ============================================================================

ALTER TABLE consent_record DROP CONSTRAINT IF EXISTS consent_record_source_check;
ALTER TABLE consent_record ADD CONSTRAINT consent_record_source_check CHECK (source IN (
    'REGISTRATION', 'OAUTH_REGISTRATION', 'SOCIAL_REGISTRATION',
    'LOGIN_PROMPT', 'COOKIE_BANNER', 'SETTINGS', 'ACCOUNT_DELETION',
    'COMPANY_DATA_FORM'
));

COMMENT ON CONSTRAINT consent_record_source_check ON consent_record IS 'Valid consent sources including COMPANY_DATA_FORM for JDG professional character declaration';

-- rollback ALTER TABLE consent_record DROP CONSTRAINT IF EXISTS consent_record_source_check;
-- rollback ALTER TABLE consent_record ADD CONSTRAINT consent_record_source_check CHECK (source IN ('REGISTRATION', 'OAUTH_REGISTRATION', 'SOCIAL_REGISTRATION', 'LOGIN_PROMPT', 'COOKIE_BANNER', 'SETTINGS', 'ACCOUNT_DELETION'));
