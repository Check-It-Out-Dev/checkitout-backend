-- liquibase formatted sql
-- changeset system:add-share-phone-for-payments

-- CIO-373: Add GDPR consent field for sharing phone number at payment stage
ALTER TABLE public.user_preferences
ADD COLUMN IF NOT EXISTS share_phone_for_payments BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN user_preferences.share_phone_for_payments IS
    'GDPR consent to share phone number with other party at payment stage. Default TRUE for usability.';

-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS share_phone_for_payments;
