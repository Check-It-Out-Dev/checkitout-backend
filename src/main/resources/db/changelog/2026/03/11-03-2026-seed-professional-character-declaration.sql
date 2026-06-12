-- liquibase formatted sql
-- changeset system:seed-professional-character-declaration-v1

-- ============================================================================
-- Seed PROFESSIONAL_CHARACTER_DECLARATION legal documents (version 1).
-- Required for JDG (sole proprietor) companies during NIP verification.
-- The type already exists in the legal_document CHECK constraint.
-- content_hash values must be updated with actual SHA-256 hashes of the deployed PDFs.
-- ============================================================================

INSERT INTO legal_document (id, type, language, version, content_hash, document_url, published_at) VALUES
    (nextval('legal_document_seq'), 'PROFESSIONAL_CHARACTER_DECLARATION', 'pl', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/professional_character_declaration_v1_pl.pdf',
     CURRENT_TIMESTAMP),
    (nextval('legal_document_seq'), 'PROFESSIONAL_CHARACTER_DECLARATION', 'en', 1,
     'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY',
     'https://storage.googleapis.com/checkitout-legal-docs/professional_character_declaration_v1_en.pdf',
     CURRENT_TIMESTAMP);

-- rollback DELETE FROM legal_document WHERE type = 'PROFESSIONAL_CHARACTER_DECLARATION' AND version = 1;
