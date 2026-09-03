# Notification System MVP - Database Schema

**Version:** 3.1
**Date:** January 2025

---

## Table of Contents
1. [Overview](#overview)
2. [Notification Table](#notification-table)
3. [UserPreferences Modifications](#userpreferences-modifications)
4. [Dictionary Entries (Translations)](#dictionary-entries-translations)
5. [Liquibase Changelog](#liquibase-changelog)
6. [Useful Queries](#useful-queries)

---

## Overview

### What We're Creating
- 1 new table: `notifications`
- 4 new columns in `user_preferences`
- 108 new dictionary entries (18 types × 3 fields × 2 languages)

### Naming Conventions (Match Existing Codebase)
- Table names: `snake_case` (e.g., `user_preferences`)
- Column names: `snake_case` (e.g., `created_time`)
- Sequences: `{table_name}_seq`
- Indexes: `idx_{table}_{columns}`

---

## Notification Table

### Complete CREATE TABLE Statement

```sql
-- Liquibase formatted sql
-- changeset notification:2025-01-create-notifications-table

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
```

### Performance Indexes

```sql
-- changeset notification:2025-01-notifications-indexes

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
```

---

## UserPreferences Modifications

### Existing Fields (DO NOT RECREATE)

The `user_preferences` table already has these notification-related fields:
```sql
-- ALREADY EXISTS - do not add again
notification_email_enabled BOOLEAN DEFAULT FALSE  -- Master email toggle
notification_push_enabled BOOLEAN DEFAULT FALSE   -- Master push toggle (future)
notification_sms_enabled BOOLEAN DEFAULT FALSE    -- Master SMS toggle (future)
communication_frequency VARCHAR(20) DEFAULT 'WEEKLY'  -- DAILY, WEEKLY, MONTHLY, NEVER
```

### New Fields to Add

```sql
-- changeset notification:2025-01-userpreferences-notification-fields

-- Add granular notification category preferences
ALTER TABLE public.user_preferences
    ADD COLUMN IF NOT EXISTS partnership_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS account_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS support_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS email_prefs_override JSONB DEFAULT '{}';

-- Add comments for documentation
COMMENT ON COLUMN public.user_preferences.partnership_notifications_enabled
    IS 'Enable/disable all partnership workflow notifications';
COMMENT ON COLUMN public.user_preferences.account_notifications_enabled
    IS 'Enable/disable all account status notifications';
COMMENT ON COLUMN public.user_preferences.support_notifications_enabled
    IS 'Enable/disable all support ticket notifications';
COMMENT ON COLUMN public.user_preferences.email_prefs_override
    IS 'JSONB map of NotificationType -> Boolean for per-type email overrides. Example: {"APPLICATION_RECEIVED": false}';

-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS partnership_notifications_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS account_notifications_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS support_notifications_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS email_prefs_override;
```

### email_prefs_override JSONB Structure

This field allows users to override email settings for specific notification types:

```json
{
    "APPLICATION_RECEIVED": false,
    "CONTENT_SUBMITTED": true,
    "COLLABORATION_COMPLETE": false
}
```

**Logic:**
1. If type exists in override → use override value
2. Else → use NotificationType's default emailDefault

---

## Dictionary Entries (Translations)

### Notification Type Translations (EN + PL)

Each notification type needs 3 dictionary entries:
- `NOTIFICATION_{TYPE}_TITLE` - Short title for notification
- `NOTIFICATION_{TYPE}_MESSAGE` - Message with placeholders
- `NOTIFICATION_{TYPE}_ACTION` - Action button label

**Placeholder syntax:** `{placeholder_name}` - replaced at runtime

```sql
-- changeset notification:2025-01-notification-translations

-- Clear existing notification translations (if any)
DELETE FROM dictionary_entries WHERE entry_key LIKE 'NOTIFICATION_%';

-- ============================================================
-- PARTNERSHIP NOTIFICATIONS
-- ============================================================

-- APPLICATION_RECEIVED (Company receives when influencer applies)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'New Application Received', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_MESSAGE', '{influencerName} has applied to your opportunity: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_ACTION', 'Review Application', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'Nowa aplikacja', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_MESSAGE', '{influencerName} aplikuje do Twojej oferty: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_ACTION', 'Przejrzyj aplikację', 'pl', 'notifications', 'system');

-- APPLICATION_ACCEPTED (Influencer receives when company accepts)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_ACCEPTED_TITLE', 'Application Accepted!', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_MESSAGE', '{companyName} has accepted your application for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_ACTION', 'View Offer', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_TITLE', 'Aplikacja zaakceptowana!', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_MESSAGE', '{companyName} zaakceptowała Twoją aplikację: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_ACTION', 'Zobacz ofertę', 'pl', 'notifications', 'system');

-- APPLICATION_REJECTED (Influencer receives when company rejects)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_REJECTED_TITLE', 'Application Not Selected', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_MESSAGE', '{companyName} has decided not to proceed with your application for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_ACTION', 'Find Other Opportunities', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_TITLE', 'Aplikacja odrzucona', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_MESSAGE', '{companyName} nie kontynuuje współpracy dotyczącej: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_ACTION', 'Szukaj innych ofert', 'pl', 'notifications', 'system');

-- OFFER_ACCEPTED (Company receives when influencer confirms)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_OFFER_ACCEPTED_TITLE', 'Influencer Confirmed!', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_MESSAGE', '{influencerName} has confirmed the collaboration for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_ACTION', 'View Collaboration', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_TITLE', 'Influencer potwierdził!', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_MESSAGE', '{influencerName} potwierdził współpracę: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_ACTION', 'Zobacz współpracę', 'pl', 'notifications', 'system');

-- OFFER_REJECTED (Company receives when influencer declines)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_OFFER_REJECTED_TITLE', 'Influencer Declined', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_MESSAGE', '{influencerName} has declined the collaboration for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_ACTION', 'Find Other Influencers', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_TITLE', 'Influencer odmówił', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_MESSAGE', '{influencerName} odmówił współpracy: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_ACTION', 'Szukaj innych influencerów', 'pl', 'notifications', 'system');

-- CONTENT_SUBMITTED (Company receives when influencer submits content)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_SUBMITTED_TITLE', 'Content Ready for Review', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_MESSAGE', '{influencerName} has submitted content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_ACTION', 'Review Content', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_TITLE', 'Treść do akceptacji', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_MESSAGE', '{influencerName} przesłał treść do akceptacji: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_ACTION', 'Przejrzyj treść', 'pl', 'notifications', 'system');

-- CONTENT_APPROVED (Influencer receives when company approves content)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_APPROVED_TITLE', 'Content Approved!', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_MESSAGE', '{companyName} has approved your content for: {opportunityName}. You can now publish it!', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_ACTION', 'Publish Now', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_TITLE', 'Treść zatwierdzona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_MESSAGE', '{companyName} zatwierdziła Twoją treść: {opportunityName}. Możesz ją opublikować!', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_ACTION', 'Opublikuj teraz', 'pl', 'notifications', 'system');

-- CONTENT_REJECTED (Influencer receives when company requests changes)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_REJECTED_TITLE', 'Content Needs Revision', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_MESSAGE', '{companyName} has requested changes to your content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_ACTION', 'View Feedback', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_TITLE', 'Treść wymaga poprawek', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_MESSAGE', '{companyName} prosi o poprawki treści: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_ACTION', 'Zobacz uwagi', 'pl', 'notifications', 'system');

-- CONTENT_POSTED (Company receives when influencer publishes)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_POSTED_TITLE', 'Content Published', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_MESSAGE', '{influencerName} has published the content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_ACTION', 'Verify Post', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_TITLE', 'Treść opublikowana', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_MESSAGE', '{influencerName} opublikował treść: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_ACTION', 'Zweryfikuj post', 'pl', 'notifications', 'system');

-- POST_VERIFIED (Influencer receives when post is verified)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_POST_VERIFIED_TITLE', 'Post Verified - Payment Processing', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_MESSAGE', '{companyName} has verified your post for: {opportunityName}. Payment is being processed!', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_ACTION', 'View Payment Status', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_TITLE', 'Post zweryfikowany - płatność w toku', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_MESSAGE', '{companyName} zweryfikowała Twój post: {opportunityName}. Płatność jest przetwarzana!', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_ACTION', 'Status płatności', 'pl', 'notifications', 'system');

-- POST_REJECTED (Influencer receives when post is rejected)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_POST_REJECTED_TITLE', 'Post Needs Correction', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_MESSAGE', '{companyName} has found issues with your post for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_ACTION', 'View Issues', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_TITLE', 'Post wymaga poprawek', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_MESSAGE', '{companyName} znalazła problemy z Twoim postem: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_ACTION', 'Zobacz problemy', 'pl', 'notifications', 'system');

-- COLLABORATION_COMPLETE (Both receive when done)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_COLLABORATION_COMPLETE_TITLE', 'Collaboration Complete!', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_MESSAGE', 'The collaboration for {opportunityName} has been successfully completed!', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_ACTION', 'Rate Partner', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_TITLE', 'Współpraca zakończona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_MESSAGE', 'Współpraca przy {opportunityName} została pomyślnie zakończona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_ACTION', 'Oceń partnera', 'pl', 'notifications', 'system');

-- ============================================================
-- ACCOUNT NOTIFICATIONS
-- ============================================================

-- ACCOUNT_ACTIVATED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_ACTIVATED_TITLE', 'Account Activated!', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_MESSAGE', 'Your CheckItOut account has been activated. You can now access all features!', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_ACTION', 'Explore Platform', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_TITLE', 'Konto aktywowane!', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_MESSAGE', 'Twoje konto CheckItOut zostało aktywowane. Możesz korzystać ze wszystkich funkcji!', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_ACTION', 'Odkryj platformę', 'pl', 'notifications', 'system');

-- ACCOUNT_SUSPENDED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_SUSPENDED_TITLE', 'Account Suspended', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_MESSAGE', 'Your account has been temporarily suspended. Please contact support for more information.', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_ACTION', 'Contact Support', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_TITLE', 'Konto zawieszone', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_MESSAGE', 'Twoje konto zostało tymczasowo zawieszone. Skontaktuj się z pomocą techniczną.', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_ACTION', 'Kontakt z pomocą', 'pl', 'notifications', 'system');

-- ACCOUNT_BANNED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_BANNED_TITLE', 'Account Permanently Disabled', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_MESSAGE', 'Your account has been permanently disabled due to terms of service violations.', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_ACTION', 'Appeal Decision', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_TITLE', 'Konto zablokowane', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_MESSAGE', 'Twoje konto zostało trwale zablokowane z powodu naruszenia regulaminu.', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_ACTION', 'Odwołaj się', 'pl', 'notifications', 'system');

-- ============================================================
-- SUPPORT NOTIFICATIONS
-- ============================================================

-- TICKET_RESPONSE
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_RESPONSE_TITLE', 'New Support Response', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_MESSAGE', 'Our support team has responded to your ticket: {ticketSubject}', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_ACTION', 'View Response', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_TITLE', 'Nowa odpowiedź', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_MESSAGE', 'Nasz zespół odpowiedział na Twoje zgłoszenie: {ticketSubject}', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_ACTION', 'Zobacz odpowiedź', 'pl', 'notifications', 'system');

-- TICKET_RESOLVED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_RESOLVED_TITLE', 'Ticket Resolved', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_MESSAGE', 'Your support ticket "{ticketSubject}" has been marked as resolved.', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_ACTION', 'Rate Support', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_TITLE', 'Zgłoszenie rozwiązane', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_MESSAGE', 'Twoje zgłoszenie "{ticketSubject}" zostało rozwiązane.', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_ACTION', 'Oceń pomoc', 'pl', 'notifications', 'system');

-- TICKET_CLOSED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_CLOSED_TITLE', 'Ticket Closed', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_MESSAGE', 'Your support ticket "{ticketSubject}" has been closed.', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_ACTION', 'Reopen if Needed', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_TITLE', 'Zgłoszenie zamknięte', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_MESSAGE', 'Twoje zgłoszenie "{ticketSubject}" zostało zamknięte.', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_ACTION', 'Otwórz ponownie', 'pl', 'notifications', 'system')

ON CONFLICT (entry_key, language_code) DO UPDATE SET
    value = EXCLUDED.value,
    updated_at = CURRENT_TIMESTAMP;

-- rollback DELETE FROM dictionary_entries WHERE category = 'notifications';
```

---

## Liquibase Changelog

### Master Changelog Entry (XML Format)

Add to your existing `src/main/resources/db/changelog/changelog.xml`:

```xml
<!-- Add after the existing 2025 migrations section -->

<!-- ===================================================================== -->
<!-- NOTIFICATION SYSTEM MVP (January 2025)                                -->
<!-- ===================================================================== -->

<!-- January 2025: Create notifications table -->
<include file="2025/01/03-01-2025-notification-table.sql" relativeToChangelogFile="true"/>

<!-- January 2025: Create notification indexes -->
<include file="2025/01/03-01-2025-notification-indexes.sql" relativeToChangelogFile="true"/>

<!-- January 2025: Add notification preference fields to user_preferences -->
<include file="2025/01/03-01-2025-userpreferences-notification-fields.sql" relativeToChangelogFile="true"/>

<!-- January 2025: Add notification translations (EN + PL) -->
<include file="2025/01/03-01-2025-notification-translations.sql" relativeToChangelogFile="true"/>
```

### Migration Files

Create the following files in `src/main/resources/db/changelog/2025/01/`:

#### File 1: `03-01-2025-notification-table.sql`

```sql
-- ============================================================================
-- NOTIFICATION TABLE: Core notifications storage
-- ============================================================================
-- liquibase formatted sql
-- changeset system:03-01-2025-notification-table

-- Create sequence for notifications
CREATE SEQUENCE IF NOT EXISTS notification_seq
    START WITH 1
    INCREMENT BY 50
    CACHE 50;

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
        'ACCOUNT_ACTIVATED',
        'ACCOUNT_SUSPENDED',
        'ACCOUNT_BANNED',
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

-- Add comments for documentation
COMMENT ON TABLE public.notifications IS 'User notifications for partnership workflow, account, and support events';
COMMENT ON COLUMN public.notifications.snapshot IS 'JSONB snapshot of actor/campaign data frozen at notification creation time';
COMMENT ON COLUMN public.notifications.email_enabled IS 'Whether email should be sent (based on user preferences at creation time)';
COMMENT ON COLUMN public.notifications.email_retry_count IS 'Number of failed email send attempts (max 3 before giving up)';

-- rollback DROP TABLE IF EXISTS public.notifications CASCADE;
-- rollback DROP SEQUENCE IF EXISTS notification_seq;
```

#### File 2: `03-01-2025-notification-indexes.sql`

```sql
-- ============================================================================
-- NOTIFICATION INDEXES: Performance optimization
-- ============================================================================
-- liquibase formatted sql
-- changeset system:03-01-2025-notification-indexes

-- Index 1: User's unread notifications (most common query - bell icon count + list)
CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id, is_read, created_at DESC)
    WHERE is_archived = FALSE;

-- Index 2: Email queue processing (cron job query)
CREATE INDEX idx_notifications_email_queue
    ON notifications(created_at)
    WHERE email_enabled = TRUE
      AND email_sent = FALSE
      AND email_retry_count < 3;

-- Index 3: Group notifications by workflow
CREATE INDEX idx_notifications_group
    ON notifications(user_id, group_key, created_at DESC)
    WHERE group_key IS NOT NULL;

-- Index 4: JSONB snapshot search
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
```

#### File 3: `03-01-2025-userpreferences-notification-fields.sql`

```sql
-- ============================================================================
-- USER PREFERENCES: Add granular notification settings
-- ============================================================================
-- liquibase formatted sql
-- changeset system:03-01-2025-userpreferences-notification-fields

-- Add granular notification category preferences
ALTER TABLE public.user_preferences
    ADD COLUMN IF NOT EXISTS partnership_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS account_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS support_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS email_prefs_override JSONB DEFAULT '{}';

-- Add comments for documentation
COMMENT ON COLUMN public.user_preferences.partnership_notifications_enabled
    IS 'Enable/disable all partnership workflow notifications';
COMMENT ON COLUMN public.user_preferences.account_notifications_enabled
    IS 'Enable/disable all account status notifications';
COMMENT ON COLUMN public.user_preferences.support_notifications_enabled
    IS 'Enable/disable all support ticket notifications';
COMMENT ON COLUMN public.user_preferences.email_prefs_override
    IS 'JSONB map of NotificationType -> Boolean for per-type email overrides. Example: {"APPLICATION_RECEIVED": false}';

-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS partnership_notifications_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS account_notifications_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS support_notifications_enabled;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS email_prefs_override;
```

#### File 4: `03-01-2025-notification-translations.sql`

```sql
-- ============================================================================
-- NOTIFICATION TRANSLATIONS: EN + PL for all notification types
-- ============================================================================
-- liquibase formatted sql
-- changeset system:03-01-2025-notification-translations

-- Clear existing notification translations (if any)
DELETE FROM dictionary_entries WHERE entry_key LIKE 'NOTIFICATION_%';

-- ============================================================
-- PARTNERSHIP NOTIFICATIONS
-- ============================================================

-- APPLICATION_RECEIVED (Company receives when influencer applies)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'New Application Received', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_MESSAGE', '{influencerName} has applied to your opportunity: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_ACTION', 'Review Application', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'Nowa aplikacja', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_MESSAGE', '{influencerName} aplikuje do Twojej oferty: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_ACTION', 'Przejrzyj aplikację', 'pl', 'notifications', 'system');

-- APPLICATION_ACCEPTED (Influencer receives when company accepts)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_ACCEPTED_TITLE', 'Application Accepted!', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_MESSAGE', '{companyName} has accepted your application for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_ACTION', 'View Offer', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_TITLE', 'Aplikacja zaakceptowana!', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_MESSAGE', '{companyName} zaakceptowała Twoją aplikację: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_ACTION', 'Zobacz ofertę', 'pl', 'notifications', 'system');

-- APPLICATION_REJECTED (Influencer receives when company rejects)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_REJECTED_TITLE', 'Application Not Selected', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_MESSAGE', '{companyName} has decided not to proceed with your application for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_ACTION', 'Find Other Opportunities', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_TITLE', 'Aplikacja odrzucona', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_MESSAGE', '{companyName} nie kontynuuje współpracy dotyczącej: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_ACTION', 'Szukaj innych ofert', 'pl', 'notifications', 'system');

-- OFFER_ACCEPTED (Company receives when influencer confirms)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_OFFER_ACCEPTED_TITLE', 'Influencer Confirmed!', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_MESSAGE', '{influencerName} has confirmed the collaboration for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_ACTION', 'View Collaboration', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_TITLE', 'Influencer potwierdził!', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_MESSAGE', '{influencerName} potwierdził współpracę: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_ACTION', 'Zobacz współpracę', 'pl', 'notifications', 'system');

-- OFFER_REJECTED (Company receives when influencer declines)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_OFFER_REJECTED_TITLE', 'Influencer Declined', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_MESSAGE', '{influencerName} has declined the collaboration for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_ACTION', 'Find Other Influencers', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_TITLE', 'Influencer odmówił', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_MESSAGE', '{influencerName} odmówił współpracy: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_ACTION', 'Szukaj innych influencerów', 'pl', 'notifications', 'system');

-- CONTENT_SUBMITTED (Company receives when influencer submits content)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_SUBMITTED_TITLE', 'Content Ready for Review', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_MESSAGE', '{influencerName} has submitted content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_ACTION', 'Review Content', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_TITLE', 'Treść do akceptacji', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_MESSAGE', '{influencerName} przesłał treść do akceptacji: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_ACTION', 'Przejrzyj treść', 'pl', 'notifications', 'system');

-- CONTENT_APPROVED (Influencer receives when company approves content)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_APPROVED_TITLE', 'Content Approved!', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_MESSAGE', '{companyName} has approved your content for: {opportunityName}. You can now publish it!', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_ACTION', 'Publish Now', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_TITLE', 'Treść zatwierdzona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_MESSAGE', '{companyName} zatwierdziła Twoją treść: {opportunityName}. Możesz ją opublikować!', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_ACTION', 'Opublikuj teraz', 'pl', 'notifications', 'system');

-- CONTENT_REJECTED (Influencer receives when company requests changes)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_REJECTED_TITLE', 'Content Needs Revision', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_MESSAGE', '{companyName} has requested changes to your content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_ACTION', 'View Feedback', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_TITLE', 'Treść wymaga poprawek', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_MESSAGE', '{companyName} prosi o poprawki treści: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_ACTION', 'Zobacz uwagi', 'pl', 'notifications', 'system');

-- CONTENT_POSTED (Company receives when influencer publishes)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_POSTED_TITLE', 'Content Published', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_MESSAGE', '{influencerName} has published the content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_ACTION', 'Verify Post', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_TITLE', 'Treść opublikowana', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_MESSAGE', '{influencerName} opublikował treść: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_ACTION', 'Zweryfikuj post', 'pl', 'notifications', 'system');

-- POST_VERIFIED (Influencer receives when post is verified)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_POST_VERIFIED_TITLE', 'Post Verified - Payment Processing', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_MESSAGE', '{companyName} has verified your post for: {opportunityName}. Payment is being processed!', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_ACTION', 'View Payment Status', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_TITLE', 'Post zweryfikowany - płatność w toku', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_MESSAGE', '{companyName} zweryfikowała Twój post: {opportunityName}. Płatność jest przetwarzana!', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_ACTION', 'Status płatności', 'pl', 'notifications', 'system');

-- POST_REJECTED (Influencer receives when post is rejected)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_POST_REJECTED_TITLE', 'Post Needs Correction', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_MESSAGE', '{companyName} has found issues with your post for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_ACTION', 'View Issues', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_TITLE', 'Post wymaga poprawek', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_MESSAGE', '{companyName} znalazła problemy z Twoim postem: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_ACTION', 'Zobacz problemy', 'pl', 'notifications', 'system');

-- COLLABORATION_COMPLETE (Both receive when done)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_COLLABORATION_COMPLETE_TITLE', 'Collaboration Complete!', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_MESSAGE', 'The collaboration for {opportunityName} has been successfully completed!', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_ACTION', 'Rate Partner', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_TITLE', 'Współpraca zakończona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_MESSAGE', 'Współpraca przy {opportunityName} została pomyślnie zakończona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_ACTION', 'Oceń partnera', 'pl', 'notifications', 'system');

-- ============================================================
-- ACCOUNT NOTIFICATIONS
-- ============================================================

-- ACCOUNT_ACTIVATED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_ACTIVATED_TITLE', 'Account Activated!', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_MESSAGE', 'Your CheckItOut account has been activated. You can now access all features!', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_ACTION', 'Explore Platform', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_TITLE', 'Konto aktywowane!', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_MESSAGE', 'Twoje konto CheckItOut zostało aktywowane. Możesz korzystać ze wszystkich funkcji!', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_ACTION', 'Odkryj platformę', 'pl', 'notifications', 'system');

-- ACCOUNT_SUSPENDED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_SUSPENDED_TITLE', 'Account Suspended', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_MESSAGE', 'Your account has been temporarily suspended. Please contact support for more information.', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_ACTION', 'Contact Support', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_TITLE', 'Konto zawieszone', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_MESSAGE', 'Twoje konto zostało tymczasowo zawieszone. Skontaktuj się z pomocą techniczną.', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_ACTION', 'Kontakt z pomocą', 'pl', 'notifications', 'system');

-- ACCOUNT_BANNED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_BANNED_TITLE', 'Account Permanently Disabled', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_MESSAGE', 'Your account has been permanently disabled due to terms of service violations.', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_ACTION', 'Appeal Decision', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_TITLE', 'Konto zablokowane', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_MESSAGE', 'Twoje konto zostało trwale zablokowane z powodu naruszenia regulaminu.', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_ACTION', 'Odwołaj się', 'pl', 'notifications', 'system');

-- ============================================================
-- SUPPORT NOTIFICATIONS
-- ============================================================

-- TICKET_RESPONSE
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_RESPONSE_TITLE', 'New Support Response', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_MESSAGE', 'Our support team has responded to your ticket: {ticketSubject}', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_ACTION', 'View Response', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_TITLE', 'Nowa odpowiedź', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_MESSAGE', 'Nasz zespół odpowiedział na Twoje zgłoszenie: {ticketSubject}', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_ACTION', 'Zobacz odpowiedź', 'pl', 'notifications', 'system');

-- TICKET_RESOLVED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_RESOLVED_TITLE', 'Ticket Resolved', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_MESSAGE', 'Your support ticket "{ticketSubject}" has been marked as resolved.', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_ACTION', 'Rate Support', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_TITLE', 'Zgłoszenie rozwiązane', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_MESSAGE', 'Twoje zgłoszenie "{ticketSubject}" zostało rozwiązane.', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_ACTION', 'Oceń pomoc', 'pl', 'notifications', 'system');

-- TICKET_CLOSED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_CLOSED_TITLE', 'Ticket Closed', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_MESSAGE', 'Your support ticket "{ticketSubject}" has been closed.', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_ACTION', 'Reopen if Needed', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_TITLE', 'Zgłoszenie zamknięte', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_MESSAGE', 'Twoje zgłoszenie "{ticketSubject}" zostało zamknięte.', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_ACTION', 'Otwórz ponownie', 'pl', 'notifications', 'system')

ON CONFLICT (entry_key, language_code) DO UPDATE SET
    value = EXCLUDED.value,
    updated_at = CURRENT_TIMESTAMP;

-- rollback DELETE FROM dictionary_entries WHERE category = 'notifications';
```

---

## Useful Queries

### Health Check Queries

```sql
-- 1. Check email queue health (should be low during normal operation)
SELECT
    COUNT(*) as pending_emails,
    MIN(created_at) as oldest_pending,
    NOW() - MIN(created_at) as max_wait_time
FROM notifications
WHERE email_enabled = TRUE
  AND email_sent = FALSE
  AND email_retry_count < 3
  AND created_at > NOW() - INTERVAL '1 day';

-- 2. Check for stuck emails (retries exhausted)
SELECT id, user_id, type, email_error, email_retry_count, created_at
FROM notifications
WHERE email_retry_count >= 3
  AND email_sent = FALSE
  AND created_at > NOW() - INTERVAL '7 days'
ORDER BY created_at DESC;

-- 3. Notification volume by type (last 24 hours)
SELECT
    type,
    COUNT(*) as count,
    SUM(CASE WHEN email_sent THEN 1 ELSE 0 END) as emails_sent,
    SUM(CASE WHEN is_read THEN 1 ELSE 0 END) as read_count
FROM notifications
WHERE created_at > NOW() - INTERVAL '24 hours'
GROUP BY type
ORDER BY count DESC;

-- 4. User notification preferences summary
SELECT
    COUNT(*) as total_users,
    SUM(CASE WHEN notification_email_enabled THEN 1 ELSE 0 END) as email_enabled,
    SUM(CASE WHEN partnership_notifications_enabled THEN 1 ELSE 0 END) as partnership_enabled,
    SUM(CASE WHEN account_notifications_enabled THEN 1 ELSE 0 END) as account_enabled,
    SUM(CASE WHEN support_notifications_enabled THEN 1 ELSE 0 END) as support_enabled
FROM user_preferences;

-- 5. Unread notifications per user (top 10)
SELECT
    user_id,
    COUNT(*) as unread_count
FROM notifications
WHERE is_read = FALSE AND is_archived = FALSE
GROUP BY user_id
ORDER BY unread_count DESC
LIMIT 10;
```

### Cleanup Queries (For Scheduled Maintenance)

```sql
-- Archive old read notifications (older than 90 days)
UPDATE notifications
SET is_archived = TRUE, archived_at = NOW()
WHERE is_read = TRUE
  AND is_archived = FALSE
  AND created_at < NOW() - INTERVAL '90 days';

-- Delete very old archived notifications (older than 1 year)
DELETE FROM notifications
WHERE is_archived = TRUE
  AND archived_at < NOW() - INTERVAL '1 year';
```

---

## Status to Notification Type Mapping

Quick reference for which notification type to create for each status transition:

| From Status | To Status | Notification Type | Recipient |
|-------------|-----------|-------------------|-----------|
| (new) | APPLIED | APPLICATION_RECEIVED | Company |
| APPLIED | ACCEPTED_BY_COMPANY | APPLICATION_ACCEPTED | Influencer |
| APPLIED | REJECTED_BY_COMPANY | APPLICATION_REJECTED | Influencer |
| ACCEPTED_BY_COMPANY | ACCEPTED_BY_INFLUENCER | OFFER_ACCEPTED | Company |
| ACCEPTED_BY_COMPANY | REJECTED_BY_INFLUENCER | OFFER_REJECTED | Company |
| ACCEPTED_BY_INFLUENCER | CONTENT_SEND_TO_ACCEPT | CONTENT_SUBMITTED | Company |
| CONTENT_SEND_TO_ACCEPT | CONTENT_APPROVED | CONTENT_APPROVED | Influencer |
| CONTENT_SEND_TO_ACCEPT | CONTENT_REJECTED | CONTENT_REJECTED | Influencer |
| CONTENT_APPROVED | CONTENT_POSTED | CONTENT_POSTED | Company |
| CONTENT_POSTED_REJECTED | CONTENT_POSTED | CONTENT_POSTED | Company |
| CONTENT_POSTED | TO_BE_PAID | POST_VERIFIED | Influencer |
| CONTENT_POSTED | CONTENT_POSTED_REJECTED | POST_REJECTED | Influencer |
| TO_BE_PAID | DONE | COLLABORATION_COMPLETE | Both |
| CONTENT_REJECTED | REJECTED_BY_INFLUENCER | OFFER_REJECTED | Company |

---

*Document Version: 3.1*
*Last Updated: January 2025*
*Changelog v3.1: Updated Liquibase section to match project's XML format and DD-MM-YYYY naming convention*
