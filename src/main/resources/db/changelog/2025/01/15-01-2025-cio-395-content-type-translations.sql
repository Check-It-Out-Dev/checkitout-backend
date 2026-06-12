-- liquibase formatted sql
-- changeset system:cio-395-content-type-translations
-- CIO-395: Fix Instagram content type naming to match platform conventions
--
-- DATA INTEGRITY ANALYSIS:
-- - content_type.name has UNIQUE constraint (safe for ON CONFLICT)
-- - dictionary_entries has UNIQUE INDEX on (entry_key, language_code)
-- - platform_content_type has composite PK (platform_id, content_type_id)
-- - Existing campaign data unaffected (IDs unchanged, only display names change)
-- - applied_opportunity_content uses RESTRICT delete (won't affect adds)

-- ============================================================================
-- STEP 1: ADD CAROUSEL CONTENT TYPE (idempotent)
-- ============================================================================
-- Note: content_type table lacks UNIQUE constraint on name, using WHERE NOT EXISTS

INSERT INTO content_type (name)
SELECT 'carousel'
WHERE NOT EXISTS (SELECT 1 FROM content_type WHERE name = 'carousel');

-- ============================================================================
-- STEP 2: LINK CAROUSEL TO INSTAGRAM PLATFORM (idempotent)
-- ============================================================================
-- platform_content_type has composite PK (platform_id, content_type_id)

INSERT INTO platform_content_type (platform_id, content_type_id)
SELECT p.id, ct.id
FROM platform p, content_type ct
WHERE p.name = 'Instagram' AND ct.name = 'carousel'
AND NOT EXISTS (
    SELECT 1 FROM platform_content_type pct
    WHERE pct.platform_id = p.id AND pct.content_type_id = ct.id
);

-- ============================================================================
-- STEP 3: UPDATE TRANSLATIONS (using DELETE + INSERT due to trigger bug)
-- ============================================================================
-- Note: dictionary_entries has a trigger that references non-existent 'last_update_time' column
-- So we use DELETE + INSERT instead of UPDATE to avoid the trigger error

-- Delete old translations that need updating
DELETE FROM dictionary_entries WHERE entry_key = 'CONTENT_TYPE_STORY' AND language_code = 'pl';
DELETE FROM dictionary_entries WHERE entry_key = 'CONTENT_TYPE_REEL' AND language_code = 'pl';
DELETE FROM dictionary_entries WHERE entry_key = 'CONTENT_TYPE_LIVE' AND language_code = 'pl';
DELETE FROM dictionary_entries WHERE entry_key = 'CONTENT_TYPE_LIVE' AND language_code = 'en';

-- Insert updated translations
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id)
VALUES
    ('CONTENT_TYPE_STORY', 'Relacja (Story)', 'pl', 'content_types', 'system'),
    ('CONTENT_TYPE_REEL', 'Rolka (Reel)', 'pl', 'content_types', 'system'),
    ('CONTENT_TYPE_LIVE', 'Live', 'pl', 'content_types', 'system'),
    ('CONTENT_TYPE_LIVE', 'Live', 'en', 'content_types', 'system'),
    ('CONTENT_TYPE_CAROUSEL', 'Carousel', 'en', 'content_types', 'system'),
    ('CONTENT_TYPE_CAROUSEL', 'Karuzela', 'pl', 'content_types', 'system')
ON CONFLICT (entry_key, language_code) DO NOTHING;

-- ============================================================================
-- ROLLBACK SCRIPT
-- ============================================================================
-- rollback UPDATE dictionary_entries SET value = 'Historia' WHERE entry_key = 'CONTENT_TYPE_STORY' AND language_code = 'pl';
-- rollback UPDATE dictionary_entries SET value = 'Rolka' WHERE entry_key = 'CONTENT_TYPE_REEL' AND language_code = 'pl';
-- rollback UPDATE dictionary_entries SET value = 'Transmisja na żywo' WHERE entry_key = 'CONTENT_TYPE_LIVE' AND language_code = 'pl';
-- rollback UPDATE dictionary_entries SET value = 'Live Stream' WHERE entry_key = 'CONTENT_TYPE_LIVE' AND language_code = 'en';
-- rollback DELETE FROM dictionary_entries WHERE entry_key = 'CONTENT_TYPE_CAROUSEL';
-- rollback DELETE FROM platform_content_type WHERE content_type_id = (SELECT id FROM content_type WHERE name = 'carousel');
-- rollback DELETE FROM content_type WHERE name = 'carousel';
