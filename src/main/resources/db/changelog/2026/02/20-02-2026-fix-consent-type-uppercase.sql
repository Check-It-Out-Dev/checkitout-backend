-- liquibase formatted sql
-- changeset system:fix-consent-type-uppercase
-- Fix consent_type values to UPPERCASE to match ConsentController regex validation (^[A-Z_]+$)

UPDATE public.consent_definition SET consent_type = UPPER(consent_type)
WHERE consent_type <> UPPER(consent_type);

-- rollback UPDATE public.consent_definition SET consent_type = LOWER(consent_type) WHERE consent_type IN ('MARKETING', 'ANALYTICS', 'COOKIES');
