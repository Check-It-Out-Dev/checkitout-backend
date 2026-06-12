-- liquibase formatted sql
-- changeset system:replace-pcd-with-subscription-consent

-- Remove PROFESSIONAL_CHARACTER_DECLARATION (never deployed to prod, wrong legal approach).
-- Replace with SUBSCRIPTION_ACTIVATION_CONSENT for subscription purchase consent flow.

-- Step 1: Delete old PCD seed data (safe even if rows don't exist)
DELETE FROM legal_document WHERE type = 'PROFESSIONAL_CHARACTER_DECLARATION';

-- Step 2: Update CHECK constraint — remove PCD, add SUBSCRIPTION_ACTIVATION_CONSENT
ALTER TABLE legal_document DROP CONSTRAINT IF EXISTS legal_document_type_check;
ALTER TABLE legal_document ADD CONSTRAINT legal_document_type_check
    CHECK (type IN ('COOKIE_POLICY', 'TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SUBSCRIPTION_ACTIVATION_CONSENT'));

-- Step 3: Seed SUBSCRIPTION_ACTIVATION_CONSENT documents (v1, EN + PL) with real SHA-256 hashes
INSERT INTO legal_document (id, type, language, version, content_hash, document_url, published_at) VALUES
    (nextval('legal_document_seq'), 'SUBSCRIPTION_ACTIVATION_CONSENT', 'en', 1,
     '18f558a54e467874cfadfec6e977aebb8bb6a3445a74cab31ac66748f68a2873',
     '/assets/docs/consent_subscription_activation_checkitout.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'SUBSCRIPTION_ACTIVATION_CONSENT', 'pl', 1,
     '4206ef0ec55790a79f6e84d6bbefbef78720dd927ac323981031fb60e00c1da7',
     '/assets/docs/zgoda_aktywacja_subskrypcji_checkitout.pdf',
     CURRENT_TIMESTAMP);

-- rollback DELETE FROM legal_document WHERE type = 'SUBSCRIPTION_ACTIVATION_CONSENT' AND version = 1;
-- rollback ALTER TABLE legal_document DROP CONSTRAINT IF EXISTS legal_document_type_check;
-- rollback ALTER TABLE legal_document ADD CONSTRAINT legal_document_type_check CHECK (type IN ('COOKIE_POLICY', 'TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'PROFESSIONAL_CHARACTER_DECLARATION'));
