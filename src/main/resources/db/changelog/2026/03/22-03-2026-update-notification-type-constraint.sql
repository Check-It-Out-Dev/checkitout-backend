-- liquibase formatted sql
-- changeset system:update-notification-type-constraint

-- ============================================================================
-- Update CHECK constraint on notifications.type to include:
-- - 2 ADMIN_* types (added in 22-03-2026-admin-notification-translations.sql but constraint was missed)
-- - 10 SUBSCRIPTION_* types (new for Stripe payment gateway)
-- ============================================================================

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notification_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notification_type CHECK (type IN (
    -- Partnership (12)
    'APPLICATION_RECEIVED', 'APPLICATION_ACCEPTED', 'APPLICATION_REJECTED',
    'OFFER_ACCEPTED', 'OFFER_REJECTED',
    'CONTENT_SUBMITTED', 'CONTENT_APPROVED', 'CONTENT_REJECTED', 'CONTENT_POSTED',
    'POST_VERIFIED', 'POST_REJECTED', 'COLLABORATION_COMPLETE',
    -- Account (3)
    'ACCOUNT_ACTIVATED', 'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED',
    -- Admin (2)
    'ADMIN_NEW_USER_REGISTERED', 'ADMIN_ACCOUNT_ACTIVATED',
    -- Subscription (10)
    'SUBSCRIPTION_TRIAL_ENDING', 'SUBSCRIPTION_TRIAL_EXPIRED',
    'SUBSCRIPTION_PAYMENT_FAILED', 'SUBSCRIPTION_PAYMENT_RECOVERED',
    'SUBSCRIPTION_PAYMENT_EXHAUSTED',
    'SUBSCRIPTION_UPGRADED', 'SUBSCRIPTION_DOWNGRADE_SCHEDULED',
    'SUBSCRIPTION_DOWNGRADED',
    'SUBSCRIPTION_SUSPENDED', 'SUBSCRIPTION_REACTIVATED',
    -- Support (3)
    'TICKET_RESPONSE', 'TICKET_RESOLVED', 'TICKET_CLOSED'
));

-- rollback ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notification_type;
-- rollback ALTER TABLE notifications ADD CONSTRAINT chk_notification_type CHECK (type IN ('APPLICATION_RECEIVED','APPLICATION_ACCEPTED','APPLICATION_REJECTED','OFFER_ACCEPTED','OFFER_REJECTED','CONTENT_SUBMITTED','CONTENT_APPROVED','CONTENT_REJECTED','CONTENT_POSTED','POST_VERIFIED','POST_REJECTED','COLLABORATION_COMPLETE','ACCOUNT_ACTIVATED','ACCOUNT_SUSPENDED','ACCOUNT_BANNED','TICKET_RESPONSE','TICKET_RESOLVED','TICKET_CLOSED'));
