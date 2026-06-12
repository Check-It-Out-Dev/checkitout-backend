-- liquibase formatted sql
-- changeset system:backfill-free-subscriptions

-- ============================================================================
-- BACKFILL: Create FREE subscriptions for all COMPANY users who don't have one
-- ============================================================================
-- Context: The subscription module was added after users already existed.
-- getOrCreateSubscription() creates lazily on first API access, but existing
-- COMPANY users who never hit a subscription endpoint have no row.
-- This migration ensures every COMPANY user has a FREE subscription + billing period.
-- ============================================================================

-- Step 1: Insert company_subscription for all COMPANY users missing one
INSERT INTO company_subscription (id, user_id, current_plan_id, status, trial_used, newest_terms_accepted, version, created_time, last_update_time)
SELECT
    nextval('company_subscription_seq'),
    u.id,
    (SELECT sp.id FROM subscription_plan sp WHERE sp.name = 'FREE'),
    'FREE_ACTIVE',
    false,
    true,
    0,
    NOW(),
    NOW()
FROM "user" u
WHERE u.user_type = 'COMPANY'
  AND u.account_status NOT IN ('DELETED', 'TO_BE_DELETED')
  AND NOT EXISTS (SELECT 1 FROM company_subscription cs WHERE cs.user_id = u.id);

-- Step 2: Insert billing_period for each newly created subscription
INSERT INTO billing_period (id, user_id, plan_id, start_date, end_date, status, created_time)
SELECT
    nextval('billing_period_seq'),
    cs.user_id,
    cs.current_plan_id,
    NOW(),
    NOW() + INTERVAL '30 days',
    'ACTIVE',
    NOW()
FROM company_subscription cs
WHERE cs.status = 'FREE_ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM billing_period bp WHERE bp.user_id = cs.user_id AND bp.status = 'ACTIVE');

-- Step 3: Log events for audit trail
INSERT INTO subscription_event (id, user_id, event_type, plan_to, billing_period_start, billing_period_end, metadata, created_time)
SELECT
    nextval('subscription_event_seq'),
    cs.user_id,
    'ACCOUNT_ACTIVATED',
    'FREE',
    NOW(),
    NOW() + INTERVAL '30 days',
    '{"source": "liquibase_backfill", "migration": "23-03-2026-backfill-free-subscriptions"}'::jsonb,
    NOW()
FROM company_subscription cs
JOIN "user" u ON u.id = cs.user_id
WHERE cs.status = 'FREE_ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM subscription_event se
    WHERE se.user_id = cs.user_id AND se.event_type = 'ACCOUNT_ACTIVATED'
  );
