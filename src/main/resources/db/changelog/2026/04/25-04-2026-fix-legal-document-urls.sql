-- liquibase formatted sql
-- changeset system:fix-legal-document-urls

-- Repoint legal_document.document_url at the actual files served by the FE under
-- src/assets/docs (no GCS bucket exists). The hashes already on these rows match
-- the on-disk SHA-256, so no content is changing — only the URL pointer.
-- Filenames mirror what the FE bundles: terms_conditions_*.pdf (not terms_of_service_*),
-- uppercase _PL/_EN suffix.

-- v1 and v2 of COOKIE/PRIVACY/TERMS share identical content (the v1 hashes were
-- backfilled from the v2 PDFs in 10-03-2026-update-v1-document-hashes.sql), so
-- both versions point at the same on-disk files. We update by (type, language)
-- which covers BOTH versions in one statement per row.
UPDATE legal_document SET document_url = '/assets/docs/cookie_policy_v2_PL.pdf'
    WHERE type = 'COOKIE_POLICY' AND language = 'pl';
UPDATE legal_document SET document_url = '/assets/docs/cookie_policy_v2_EN.pdf'
    WHERE type = 'COOKIE_POLICY' AND language = 'en';

UPDATE legal_document SET document_url = '/assets/docs/privacy_policy_v2_PL.pdf'
    WHERE type = 'PRIVACY_POLICY' AND language = 'pl';
UPDATE legal_document SET document_url = '/assets/docs/privacy_policy_v2_EN.pdf'
    WHERE type = 'PRIVACY_POLICY' AND language = 'en';

UPDATE legal_document SET document_url = '/assets/docs/terms_conditions_v2_PL.pdf'
    WHERE type = 'TERMS_OF_SERVICE' AND language = 'pl';
UPDATE legal_document SET document_url = '/assets/docs/terms_conditions_v2_EN.pdf'
    WHERE type = 'TERMS_OF_SERVICE' AND language = 'en';

-- Extend the type CHECK constraint to allow DATA_RETENTION_POLICY.
ALTER TABLE legal_document DROP CONSTRAINT IF EXISTS legal_document_type_check;
ALTER TABLE legal_document ADD CONSTRAINT legal_document_type_check
    CHECK (type IN ('COOKIE_POLICY', 'TERMS_OF_SERVICE', 'PRIVACY_POLICY',
                    'SUBSCRIPTION_ACTIVATION_CONSENT', 'DATA_RETENTION_POLICY'));

-- Seed DATA_RETENTION_POLICY (v2, EN + PL) with real SHA-256 hashes of the
-- PDFs already shipped in checkItOut-fe/src/assets/docs.
INSERT INTO legal_document (id, type, language, version, content_hash, document_url, published_at) VALUES
    (nextval('legal_document_seq'), 'DATA_RETENTION_POLICY', 'pl', 2,
     '8fa2ac914ebe0efed0eec71eabfa92a186ccbcb6411519c786d6aadcaaa1189e',
     '/assets/docs/data_retention_policy_v2_PL.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'DATA_RETENTION_POLICY', 'en', 2,
     'd094e4da829a2dd047c30f6e3e54ab2785489aef3e5959f79fa09e7848437a5a',
     '/assets/docs/data_retention_policy_v2_EN.pdf',
     CURRENT_TIMESTAMP);

-- rollback DELETE FROM legal_document WHERE type = 'DATA_RETENTION_POLICY';
-- rollback ALTER TABLE legal_document DROP CONSTRAINT IF EXISTS legal_document_type_check;
-- rollback ALTER TABLE legal_document ADD CONSTRAINT legal_document_type_check CHECK (type IN ('COOKIE_POLICY', 'TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SUBSCRIPTION_ACTIVATION_CONSENT'));
-- rollback UPDATE legal_document SET document_url = 'https://storage.googleapis.com/checkitout-legal-docs/cookie_policy_v2_pl.pdf' WHERE type = 'COOKIE_POLICY' AND language = 'pl' AND version = 2;
-- rollback UPDATE legal_document SET document_url = 'https://storage.googleapis.com/checkitout-legal-docs/cookie_policy_v2_en.pdf' WHERE type = 'COOKIE_POLICY' AND language = 'en' AND version = 2;
-- rollback UPDATE legal_document SET document_url = 'https://storage.googleapis.com/checkitout-legal-docs/privacy_policy_v2_pl.pdf' WHERE type = 'PRIVACY_POLICY' AND language = 'pl' AND version = 2;
-- rollback UPDATE legal_document SET document_url = 'https://storage.googleapis.com/checkitout-legal-docs/privacy_policy_v2_en.pdf' WHERE type = 'PRIVACY_POLICY' AND language = 'en' AND version = 2;
-- rollback UPDATE legal_document SET document_url = 'https://storage.googleapis.com/checkitout-legal-docs/terms_of_service_v2_pl.pdf' WHERE type = 'TERMS_OF_SERVICE' AND language = 'pl' AND version = 2;
-- rollback UPDATE legal_document SET document_url = 'https://storage.googleapis.com/checkitout-legal-docs/terms_of_service_v2_en.pdf' WHERE type = 'TERMS_OF_SERVICE' AND language = 'en' AND version = 2;
