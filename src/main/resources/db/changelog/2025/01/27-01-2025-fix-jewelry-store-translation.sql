-- liquibase formatted sql
-- changeset system:fix-polish-jewelry-store-translation
-- Add missing normalized key for jewelry store translation
-- The TranslationService normalizes Polish characters (ż -> Z, ą -> A)
-- but the existing key has Polish characters, so we need both versions
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
    -- Normalized key (without Polish characters) - this is what TranslationService generates
    ('SERVICE_TYPE_SKLEP_Z_BIZUTERIA', 'Jewelry Store', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_Z_BIZUTERIA', 'Sklep z biżuterią', 'pl', 'service_types', 'system'),
    
    -- Also add description for normalized key
    ('SERVICE_TYPE_SKLEP_Z_BIZUTERIA_DESC', 'Retail store with jewelry and watches', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_Z_BIZUTERIA_DESC', 'Sklep detaliczny z biżuterią i zegarkami', 'pl', 'service_types', 'system')
ON CONFLICT (entry_key, language_code) DO NOTHING;

-- rollback DELETE FROM dictionary_entries WHERE entry_key IN ('SERVICE_TYPE_SKLEP_Z_BIZUTERIA', 'SERVICE_TYPE_SKLEP_Z_BIZUTERIA_DESC');
