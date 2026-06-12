-- changeset notification:2026-01-notifications-indexes

-- Index 1: User's unread notifications (most common query - bell icon count + list)
-- Covers: GET /notifications/unread/count, GET /notifications
CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id, is_read, created_at DESC)
    WHERE is_archived = FALSE;

-- Index 2: Email queue processing (cron job query)
-- Covers: findPendingEmails() - notifications needing email delivery
CREATE INDEX idx_notifications_email_queue
    ON notifications(created_at)
    WHERE email_enabled = TRUE
      AND email_sent = FALSE
      AND email_retry_count < 3;

-- Index 3: Group notifications by workflow (for showing related notifications)
-- Covers: Finding all notifications for a specific collaboration
CREATE INDEX idx_notifications_group
    ON notifications(user_id, group_key, created_at DESC)
    WHERE group_key IS NOT NULL;

-- Index 4: JSONB snapshot search (for advanced filtering)
CREATE INDEX idx_notifications_snapshot
    ON notifications USING gin(snapshot);

-- Index 5: Applied opportunity lookup
CREATE INDEX idx_notifications_applied_opportunity
    ON notifications(applied_opportunity_id)
    WHERE applied_opportunity_id IS NOT NULL;

-- rollback DROP INDEX IF EXISTS idx_notifications_user_unread;
-- rollback DROP INDEX IF EXISTS idx_notifications_email_queue;
-- rollback DROP INDEX IF EXISTS idx_notifications_group;
-- rollback DROP INDEX IF EXISTS idx_notifications_snapshot;
-- rollback DROP INDEX IF EXISTS idx_notifications_applied_opportunity;