-- liquibase formatted sql
-- changeset system:seed-legal-documents-v2

-- Seed version 2 of legal documents.
-- Document URLs point to the public GCS bucket where v2 PDFs are hosted.
-- content_hash values are SHA-256 hashes of the deployed v2 PDFs.
-- published_at is set to the deployment date to start the 38-day grace period for existing users.

INSERT INTO legal_document (id, type, language, version, content_hash, document_url, published_at) VALUES
    (nextval('legal_document_seq'), 'COOKIE_POLICY', 'pl', 2,
     'a911b2585881f2e5e00e33f1b55dc2fc44790c92cbb3ba98b3607bf4d8f66403',
     'https://storage.googleapis.com/checkitout-legal-docs/cookie_policy_v2_pl.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'COOKIE_POLICY', 'en', 2,
     '99dc546935ed15138b083738a3553f732d386889e80a93cae8acf5176c754f3b',
     'https://storage.googleapis.com/checkitout-legal-docs/cookie_policy_v2_en.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'TERMS_OF_SERVICE', 'pl', 2,
     '9a3e930281b3c5f933f6c133334c2bd44c32d23b0d7436264587282bbfa7589f',
     'https://storage.googleapis.com/checkitout-legal-docs/terms_of_service_v2_pl.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'TERMS_OF_SERVICE', 'en', 2,
     '420e6c9f4036b909aa1bd67f00f47860f1e1b333b88854c4573822b532c2cd0a',
     'https://storage.googleapis.com/checkitout-legal-docs/terms_of_service_v2_en.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'PRIVACY_POLICY', 'pl', 2,
     'a353c3b61235badc3632cfe1a9d51b0cf46906765d4c46d0b0181cdf02a03bf4',
     'https://storage.googleapis.com/checkitout-legal-docs/privacy_policy_v2_pl.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'PRIVACY_POLICY', 'en', 2,
     '5afc638dd6cad82b0c0474a7f5defc711d04ecfccbfdc75a1fb417ee9c8a8bf2',
     'https://storage.googleapis.com/checkitout-legal-docs/privacy_policy_v2_en.pdf',
     CURRENT_TIMESTAMP);

-- rollback DELETE FROM legal_document WHERE version = 2;
