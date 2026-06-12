-- liquibase formatted sql
-- changeset system:seed-legal-documents-v1

-- Seed initial legal document metadata (version 1).
-- Document URLs point to the public GCS bucket where PDFs are hosted.
-- content_hash values must be updated with actual SHA-256 hashes of the deployed PDFs.
-- published_at is set to the deployment date to start the 38-day grace period for existing users.

INSERT INTO legal_document (id, type, language, version, content_hash, document_url, published_at) VALUES
    (nextval('legal_document_seq'), 'COOKIE_POLICY', 'pl', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/cookie_policy_v1_pl.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'COOKIE_POLICY', 'en', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/cookie_policy_v1_en.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'TERMS_OF_SERVICE', 'pl', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/terms_of_service_v1_pl.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'TERMS_OF_SERVICE', 'en', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/terms_of_service_v1_en.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'PRIVACY_POLICY', 'pl', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/privacy_policy_v1_pl.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'PRIVACY_POLICY', 'en', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/privacy_policy_v1_en.pdf',
     CURRENT_TIMESTAMP);

-- rollback DELETE FROM legal_document WHERE version = 1;
