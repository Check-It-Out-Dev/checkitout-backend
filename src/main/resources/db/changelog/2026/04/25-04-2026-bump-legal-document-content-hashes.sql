-- liquibase formatted sql
-- changeset system:bump-legal-document-content-hashes

-- Bump content_hash on every legal_document row to the SHA-256 of the
-- regenerated PDFs in checkItOut-fe/src/assets/docs. The PDFs were rebuilt
-- from per-doc Python scripts under checkItOut-fe/legal-docs-src/ (ReportLab
-- Platypus, Calibri 11pt) with the internal version label changed from
-- "Version 1.1" / "Wersja 1.1" to "Version 2.0" / "Wersja 2.0" — the hash on
-- disk therefore no longer matches what the DB held, and ConsentEnforcement
-- would (correctly) flag every existing row as drifted until we update.
--
-- v1 rows were backfilled with the v2 hash in
-- 10-03-2026-update-v1-document-hashes.sql because v1 and v2 of
-- COOKIE/PRIVACY/TERMS share identical content; we apply the same logic here
-- and update both versions per (type, language). DATA_RETENTION_POLICY only
-- exists at version 2 (seeded in 25-04-2026-fix-legal-document-urls.sql).

-- COOKIE_POLICY (v1 + v2 share content, both bumped)
UPDATE legal_document SET content_hash = 'cdd96263740d047238216aeb24ee8a40b38f8a4aad121971e4b42d896550b93e'
    WHERE type = 'COOKIE_POLICY' AND language = 'pl' AND version = 2;
UPDATE legal_document SET content_hash = 'cdd96263740d047238216aeb24ee8a40b38f8a4aad121971e4b42d896550b93e'
    WHERE type = 'COOKIE_POLICY' AND language = 'pl' AND version = 1;
UPDATE legal_document SET content_hash = '083b6b9ce00cce6b4ef957563e17641be1fca8e9f1c49b918f1b1b3521ece57a'
    WHERE type = 'COOKIE_POLICY' AND language = 'en' AND version = 2;
UPDATE legal_document SET content_hash = '083b6b9ce00cce6b4ef957563e17641be1fca8e9f1c49b918f1b1b3521ece57a'
    WHERE type = 'COOKIE_POLICY' AND language = 'en' AND version = 1;

-- PRIVACY_POLICY (v1 + v2 share content, both bumped)
UPDATE legal_document SET content_hash = '6bcf58d36314095ad6bfd39dabfa622a132a9572d0ba45ae57f28f48220aea95'
    WHERE type = 'PRIVACY_POLICY' AND language = 'pl' AND version = 2;
UPDATE legal_document SET content_hash = '6bcf58d36314095ad6bfd39dabfa622a132a9572d0ba45ae57f28f48220aea95'
    WHERE type = 'PRIVACY_POLICY' AND language = 'pl' AND version = 1;
UPDATE legal_document SET content_hash = 'f1e6859b6e9a83bbf639744372b74d547178a0d4e1553cafe8ece3b6b6fae79d'
    WHERE type = 'PRIVACY_POLICY' AND language = 'en' AND version = 2;
UPDATE legal_document SET content_hash = 'f1e6859b6e9a83bbf639744372b74d547178a0d4e1553cafe8ece3b6b6fae79d'
    WHERE type = 'PRIVACY_POLICY' AND language = 'en' AND version = 1;

-- TERMS_OF_SERVICE (v1 + v2 share content, both bumped)
UPDATE legal_document SET content_hash = '87aca6e7b8d3e563ab84f7ce9b3cf8850b6bc8764eba8ef0af5f396b13deac7a'
    WHERE type = 'TERMS_OF_SERVICE' AND language = 'pl' AND version = 2;
UPDATE legal_document SET content_hash = '87aca6e7b8d3e563ab84f7ce9b3cf8850b6bc8764eba8ef0af5f396b13deac7a'
    WHERE type = 'TERMS_OF_SERVICE' AND language = 'pl' AND version = 1;
UPDATE legal_document SET content_hash = 'b5ebb2a66c0916e9a470f121553e6e6b13715fb62b478cec9cfe23cc18f18cc5'
    WHERE type = 'TERMS_OF_SERVICE' AND language = 'en' AND version = 2;
UPDATE legal_document SET content_hash = 'b5ebb2a66c0916e9a470f121553e6e6b13715fb62b478cec9cfe23cc18f18cc5'
    WHERE type = 'TERMS_OF_SERVICE' AND language = 'en' AND version = 1;

-- DATA_RETENTION_POLICY (only v2 exists; we still issue a v1 UPDATE for
-- symmetry/idempotence — it is a no-op when no v1 row is present)
UPDATE legal_document SET content_hash = '5da2a1aec7990286bc514e9b390a880d72456af8cad65e212a42eb56f1dd7b31'
    WHERE type = 'DATA_RETENTION_POLICY' AND language = 'pl' AND version = 2;
UPDATE legal_document SET content_hash = '5da2a1aec7990286bc514e9b390a880d72456af8cad65e212a42eb56f1dd7b31'
    WHERE type = 'DATA_RETENTION_POLICY' AND language = 'pl' AND version = 1;
UPDATE legal_document SET content_hash = '09dafec3232693fe1f8f0273867ab31f1390b28abfdc586cd945f1e16981c5d7'
    WHERE type = 'DATA_RETENTION_POLICY' AND language = 'en' AND version = 2;
UPDATE legal_document SET content_hash = '09dafec3232693fe1f8f0273867ab31f1390b28abfdc586cd945f1e16981c5d7'
    WHERE type = 'DATA_RETENTION_POLICY' AND language = 'en' AND version = 1;

-- Rollbacks restore to the pre-rebuild ("Version 1.1" PDFs) hashes that were
-- live before this migration ran (set by 10-03-2026-seed-legal-documents-v2.sql,
-- 10-03-2026-update-v1-document-hashes.sql and 25-04-2026-fix-legal-document-urls.sql).
-- rollback UPDATE legal_document SET content_hash = 'a911b2585881f2e5e00e33f1b55dc2fc44790c92cbb3ba98b3607bf4d8f66403' WHERE type = 'COOKIE_POLICY' AND language = 'pl' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = 'a911b2585881f2e5e00e33f1b55dc2fc44790c92cbb3ba98b3607bf4d8f66403' WHERE type = 'COOKIE_POLICY' AND language = 'pl' AND version = 1;
-- rollback UPDATE legal_document SET content_hash = '99dc546935ed15138b083738a3553f732d386889e80a93cae8acf5176c754f3b' WHERE type = 'COOKIE_POLICY' AND language = 'en' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = '99dc546935ed15138b083738a3553f732d386889e80a93cae8acf5176c754f3b' WHERE type = 'COOKIE_POLICY' AND language = 'en' AND version = 1;
-- rollback UPDATE legal_document SET content_hash = 'a353c3b61235badc3632cfe1a9d51b0cf46906765d4c46d0b0181cdf02a03bf4' WHERE type = 'PRIVACY_POLICY' AND language = 'pl' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = 'a353c3b61235badc3632cfe1a9d51b0cf46906765d4c46d0b0181cdf02a03bf4' WHERE type = 'PRIVACY_POLICY' AND language = 'pl' AND version = 1;
-- rollback UPDATE legal_document SET content_hash = '5afc638dd6cad82b0c0474a7f5defc711d04ecfccbfdc75a1fb417ee9c8a8bf2' WHERE type = 'PRIVACY_POLICY' AND language = 'en' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = '5afc638dd6cad82b0c0474a7f5defc711d04ecfccbfdc75a1fb417ee9c8a8bf2' WHERE type = 'PRIVACY_POLICY' AND language = 'en' AND version = 1;
-- rollback UPDATE legal_document SET content_hash = '9a3e930281b3c5f933f6c133334c2bd44c32d23b0d7436264587282bbfa7589f' WHERE type = 'TERMS_OF_SERVICE' AND language = 'pl' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = '9a3e930281b3c5f933f6c133334c2bd44c32d23b0d7436264587282bbfa7589f' WHERE type = 'TERMS_OF_SERVICE' AND language = 'pl' AND version = 1;
-- rollback UPDATE legal_document SET content_hash = '420e6c9f4036b909aa1bd67f00f47860f1e1b333b88854c4573822b532c2cd0a' WHERE type = 'TERMS_OF_SERVICE' AND language = 'en' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = '420e6c9f4036b909aa1bd67f00f47860f1e1b333b88854c4573822b532c2cd0a' WHERE type = 'TERMS_OF_SERVICE' AND language = 'en' AND version = 1;
-- rollback UPDATE legal_document SET content_hash = '8fa2ac914ebe0efed0eec71eabfa92a186ccbcb6411519c786d6aadcaaa1189e' WHERE type = 'DATA_RETENTION_POLICY' AND language = 'pl' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = '8fa2ac914ebe0efed0eec71eabfa92a186ccbcb6411519c786d6aadcaaa1189e' WHERE type = 'DATA_RETENTION_POLICY' AND language = 'pl' AND version = 1;
-- rollback UPDATE legal_document SET content_hash = 'd094e4da829a2dd047c30f6e3e54ab2785489aef3e5959f79fa09e7848437a5a' WHERE type = 'DATA_RETENTION_POLICY' AND language = 'en' AND version = 2;
-- rollback UPDATE legal_document SET content_hash = 'd094e4da829a2dd047c30f6e3e54ab2785489aef3e5959f79fa09e7848437a5a' WHERE type = 'DATA_RETENTION_POLICY' AND language = 'en' AND version = 1;
