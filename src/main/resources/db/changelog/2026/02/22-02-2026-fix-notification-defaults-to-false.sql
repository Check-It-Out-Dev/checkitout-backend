--liquibase formatted sql

--changeset migration:22-02-2026-fix-notification-defaults-to-false
--comment: Fix notification category defaults from TRUE to FALSE to match service behavior and avoid notification bombing for new users

-- Step 1: Change column defaults from TRUE to FALSE
ALTER TABLE public.user_preferences ALTER COLUMN notification_partnership_enabled SET DEFAULT FALSE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_support_enabled SET DEFAULT FALSE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_system_enabled SET DEFAULT FALSE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_email_partnership_enabled SET DEFAULT FALSE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_email_support_enabled SET DEFAULT FALSE;

-- Step 2: Update existing rows that were created with the wrong default
UPDATE public.user_preferences SET notification_partnership_enabled = FALSE WHERE notification_partnership_enabled = TRUE;
UPDATE public.user_preferences SET notification_support_enabled = FALSE WHERE notification_support_enabled = TRUE;
UPDATE public.user_preferences SET notification_system_enabled = FALSE WHERE notification_system_enabled = TRUE;
UPDATE public.user_preferences SET notification_email_partnership_enabled = FALSE WHERE notification_email_partnership_enabled = TRUE;
UPDATE public.user_preferences SET notification_email_support_enabled = FALSE WHERE notification_email_support_enabled = TRUE;
