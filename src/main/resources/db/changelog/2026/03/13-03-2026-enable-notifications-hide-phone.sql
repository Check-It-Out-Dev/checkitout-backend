--liquibase formatted sql
--changeset system:enable-notifications-hide-phone
--comment: Enable all notifications by default for all users, hide phone numbers by default (user opt-in)

-- Step 1: Enable all notification toggles for existing users
UPDATE public.user_preferences SET notification_email_enabled = TRUE;
UPDATE public.user_preferences SET notification_partnership_enabled = TRUE;
UPDATE public.user_preferences SET notification_support_enabled = TRUE;
UPDATE public.user_preferences SET notification_system_enabled = TRUE;
UPDATE public.user_preferences SET notification_email_partnership_enabled = TRUE;
UPDATE public.user_preferences SET notification_email_support_enabled = TRUE;

-- Step 2: Change column defaults so new users also get notifications enabled
ALTER TABLE public.user_preferences ALTER COLUMN notification_email_enabled SET DEFAULT TRUE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_partnership_enabled SET DEFAULT TRUE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_support_enabled SET DEFAULT TRUE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_system_enabled SET DEFAULT TRUE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_email_partnership_enabled SET DEFAULT TRUE;
ALTER TABLE public.user_preferences ALTER COLUMN notification_email_support_enabled SET DEFAULT TRUE;

-- Step 3: Hide phone numbers for all existing users (was TRUE, now opt-in)
UPDATE public.user_preferences SET share_phone_for_payments = FALSE;

-- Step 4: Change column default so new users also have phone hidden
ALTER TABLE public.user_preferences ALTER COLUMN share_phone_for_payments SET DEFAULT FALSE;

-- rollback UPDATE public.user_preferences SET notification_email_enabled = FALSE;
-- rollback UPDATE public.user_preferences SET notification_partnership_enabled = FALSE;
-- rollback UPDATE public.user_preferences SET notification_support_enabled = FALSE;
-- rollback UPDATE public.user_preferences SET notification_system_enabled = FALSE;
-- rollback UPDATE public.user_preferences SET notification_email_partnership_enabled = FALSE;
-- rollback UPDATE public.user_preferences SET notification_email_support_enabled = FALSE;
-- rollback ALTER TABLE public.user_preferences ALTER COLUMN notification_email_enabled SET DEFAULT FALSE;
-- rollback ALTER TABLE public.user_preferences ALTER COLUMN notification_partnership_enabled SET DEFAULT FALSE;
-- rollback ALTER TABLE public.user_preferences ALTER COLUMN notification_support_enabled SET DEFAULT FALSE;
-- rollback ALTER TABLE public.user_preferences ALTER COLUMN notification_system_enabled SET DEFAULT FALSE;
-- rollback ALTER TABLE public.user_preferences ALTER COLUMN notification_email_partnership_enabled SET DEFAULT FALSE;
-- rollback ALTER TABLE public.user_preferences ALTER COLUMN notification_email_support_enabled SET DEFAULT FALSE;
-- rollback UPDATE public.user_preferences SET share_phone_for_payments = TRUE;
-- rollback ALTER TABLE public.user_preferences ALTER COLUMN share_phone_for_payments SET DEFAULT TRUE;
