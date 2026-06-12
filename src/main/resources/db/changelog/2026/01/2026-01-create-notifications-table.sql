-- Liquibase formatted sql
-- changeset notification:2026-01-create-notifications-table

-- Create sequence for notifications
CREATE SEQUENCE IF NOT EXISTS notification_seq
    START WITH 1
    INCREMENT BY 50;

-- Create notifications table
CREATE TABLE IF NOT EXISTS public.notifications (
    -- Primary Key (matches existing entity patterns)
    id BIGINT PRIMARY KEY DEFAULT nextval('notification_seq'),

    -- Optimistic locking (managed by JPA @Version)
    version BIGINT NOT NULL DEFAULT 0,

    -- Recipient (foreign key to user table)
    user_id BIGINT NOT NULL,

    -- Classification
    type VARCHAR(50) NOT NULL,
    category VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,

    -- Content (translated at creation time, stored in user's language)
    title VARCHAR(200) NOT NULL,
    message TEXT,
    action_url VARCHAR(500),
    action_label VARCHAR(100),

    -- Translation metadata (for debugging/re-translation if needed)
    translation_key VARCHAR(100),
    language_code VARCHAR(10) DEFAULT 'en',

    -- Context Snapshot (JSONB - frozen data from creation moment)
    -- This ensures notification displays correctly even if referenced entities change/delete
    snapshot JSONB,

    -- Relationship IDs (for querying notifications by context)
    applied_opportunity_id BIGINT,
    partnership_opportunity_id BIGINT,
    influencer_id BIGINT,
    company_id BIGINT,
    support_ticket_id BIGINT,

    -- Workflow tracking (for grouping related notifications)
    group_key VARCHAR(100),
    workflow_step VARCHAR(50),

    -- Read status
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP,

    -- Email delivery tracking
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent_at TIMESTAMP,
    email_retry_count INTEGER NOT NULL DEFAULT 0,
    email_error VARCHAR(500),

    -- Lifecycle
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id)
        REFERENCES public."user"(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_notification_type CHECK (type IN (
        -- Partnership workflow (12 types)
        'APPLICATION_RECEIVED',
        'APPLICATION_ACCEPTED',
        'APPLICATION_REJECTED',
        'OFFER_ACCEPTED',
        'OFFER_REJECTED',
        'CONTENT_SUBMITTED',
        'CONTENT_APPROVED',
        'CONTENT_REJECTED',
        'CONTENT_POSTED',
        'POST_VERIFIED',
        'POST_REJECTED',
        'COLLABORATION_COMPLETE',
        -- Account (3 types)
        'ACCOUNT_ACTIVATED',
        'ACCOUNT_SUSPENDED',
        'ACCOUNT_BANNED',
        -- Support (3 types)
        'TICKET_RESPONSE',
        'TICKET_RESOLVED',
        'TICKET_CLOSED'
    )),

    CONSTRAINT chk_notification_category CHECK (category IN (
        'PARTNERSHIP',
        'ACCOUNT',
        'SUPPORT',
        'SYSTEM'
    )),

    CONSTRAINT chk_notification_priority CHECK (priority IN (
        'LOW',
        'MEDIUM',
        'HIGH',
        'CRITICAL'
    ))
);

-- Add comment for documentation
COMMENT ON TABLE public.notifications IS 'User notifications for partnership workflow, account, and support events';
COMMENT ON COLUMN public.notifications.snapshot IS 'JSONB snapshot of actor/campaign data frozen at notification creation time';
COMMENT ON COLUMN public.notifications.email_enabled IS 'Whether email should be sent (based on user preferences at creation time)';
COMMENT ON COLUMN public.notifications.email_retry_count IS 'Number of failed email send attempts (max 3 before giving up)';

-- rollback DROP TABLE IF EXISTS public.notifications CASCADE;
-- rollback DROP SEQUENCE IF EXISTS notification_seq;