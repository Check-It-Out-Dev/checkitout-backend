-- changeset notification:2025-01-userpreferences-notification-fields

-- Add granular notification category preferences
ALTER TABLE public.user_preferences
    ADD COLUMN IF NOT EXISTS notification_partnership_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notification_support_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notification_system_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notification_email_partnership_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notification_email_support_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Add comments for documentation
COMMENT ON COLUMN public.user_preferences.notification_partnership_enabled
    IS 'Enable/disable all partnership workflow notifications';
COMMENT ON COLUMN public.user_preferences.notification_support_enabled
    IS 'Enable/disable all support ticket notifications';
COMMENT ON COLUMN public.user_preferences.notification_system_enabled
    IS 'Enable/disable all system notifications';
COMMENT ON COLUMN public.user_preferences.notification_email_partnership_enabled
    IS 'Enable/disable email partnership workflow notifications';
COMMENT ON COLUMN public.user_preferences.notification_email_support_enabled
    IS 'Enable/disable email support ticket notifications';

-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS notification_partnership_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS notification_support_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS notification_system_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS notification_email_partnership_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS notification_email_support_enabled;
