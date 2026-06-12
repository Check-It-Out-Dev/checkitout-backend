--liquibase formatted sql
--changeset system:add-display-order-to-consent-definition

ALTER TABLE consent_definition ADD COLUMN IF NOT EXISTS display_order INT NOT NULL DEFAULT 0;

UPDATE consent_definition SET display_order = 1 WHERE consent_type = 'COOKIES';
UPDATE consent_definition SET display_order = 2 WHERE consent_type = 'ANALYTICS';
UPDATE consent_definition SET display_order = 3 WHERE consent_type = 'MARKETING';
