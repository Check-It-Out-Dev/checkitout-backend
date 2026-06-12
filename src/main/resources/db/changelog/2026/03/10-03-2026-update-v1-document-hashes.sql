-- liquibase formatted sql
-- changeset system:update-v1-document-hashes

-- Replace placeholder hashes in v1 legal documents with actual SHA-256 hashes
-- computed from the deployed PDF files.

UPDATE legal_document SET content_hash = 'a911b2585881f2e5e00e33f1b55dc2fc44790c92cbb3ba98b3607bf4d8f66403'
WHERE type = 'COOKIE_POLICY' AND language = 'pl' AND version = 1;

UPDATE legal_document SET content_hash = '99dc546935ed15138b083738a3553f732d386889e80a93cae8acf5176c754f3b'
WHERE type = 'COOKIE_POLICY' AND language = 'en' AND version = 1;

UPDATE legal_document SET content_hash = '9a3e930281b3c5f933f6c133334c2bd44c32d23b0d7436264587282bbfa7589f'
WHERE type = 'TERMS_OF_SERVICE' AND language = 'pl' AND version = 1;

UPDATE legal_document SET content_hash = '420e6c9f4036b909aa1bd67f00f47860f1e1b333b88854c4573822b532c2cd0a'
WHERE type = 'TERMS_OF_SERVICE' AND language = 'en' AND version = 1;

UPDATE legal_document SET content_hash = 'a353c3b61235badc3632cfe1a9d51b0cf46906765d4c46d0b0181cdf02a03bf4'
WHERE type = 'PRIVACY_POLICY' AND language = 'pl' AND version = 1;

UPDATE legal_document SET content_hash = '5afc638dd6cad82b0c0474a7f5defc711d04ecfccbfdc75a1fb417ee9c8a8bf2'
WHERE type = 'PRIVACY_POLICY' AND language = 'en' AND version = 1;

-- rollback UPDATE legal_document SET content_hash = 'PLACEHOLDER_HASH_UPDATE_BEFORE_DEPLOY' WHERE version = 1;
