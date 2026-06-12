-- liquibase formatted sql
-- changeset system:downgrade-all-to-free-for-rollout

-- ============================================================================
-- FREE-ONLY ROLLOUT: Downgrade ALL non-FREE_ACTIVE subscriptions to FREE_ACTIVE
-- ============================================================================
-- Context: app.payments.enabled defaults to false. The PaymentsDisabledBootGuard
-- crashes the app at boot if any CompanySubscription row has status != FREE_ACTIVE.
-- This migration runs BEFORE the guard fires (Liquibase runs during context refresh,
-- before @PostConstruct).
--
-- What it does:
-- 1. Expires active billing periods for non-FREE subscriptions
-- 2. Resets all non-FREE subscriptions to FREE_ACTIVE with the FREE plan
-- 3. Clears Stripe IDs (subscriptions cancelled externally or unused in sandbox)
-- 4. Creates fresh FREE billing periods for affected users
-- 5. Logs audit events
--
-- This is safe for staging/prod because:
-- - No paying users exist yet (confirmed by product)
-- - Any sandbox/test Stripe subscriptions are abandoned when toggle goes OFF
-- - The Stripe dashboard should also be cleaned up manually
-- ============================================================================

-- Step 1: Expire all active billing periods for non-FREE subscriptions
UPDATE billing_period bp
SET status = 'EXPIRED',
    end_date = NOW()
WHERE bp.status = 'ACTIVE'
  AND bp.user_id IN (
    SELECT cs.user_id FROM company_subscription cs
    WHERE cs.status != 'FREE_ACTIVE'
  );

-- Step 2: Reset all non-FREE subscriptions to FREE_ACTIVE
UPDATE company_subscription
SET status = 'FREE_ACTIVE',
    current_plan_id = (SELECT sp.id FROM subscription_plan sp WHERE sp.name = 'FREE'),
    previous_plan_id = NULL,
    target_plan_id = NULL,
    stripe_subscription_id = NULL,
    stripe_schedule_id = NULL,
    stripe_customer_id = NULL,
    previous_state = NULL,
    grace_deadline = NULL,
    newest_terms_accepted = true,
    last_update_time = NOW()
WHERE status != 'FREE_ACTIVE';

-- Step 3: Create fresh FREE billing periods for users who now have none
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
  AND NOT EXISTS (
    SELECT 1 FROM billing_period bp
    WHERE bp.user_id = cs.user_id AND bp.status = 'ACTIVE'
  );

-- Step 4: Audit trail
INSERT INTO subscription_event (id, user_id, event_type, plan_from, plan_to, billing_period_start, billing_period_end, metadata, created_time)
SELECT
    nextval('subscription_event_seq'),
    cs.user_id,
    'ACCOUNT_ACTIVATED',
    NULL,
    'FREE',
    NOW(),
    NOW() + INTERVAL '30 days',
    '{"source": "liquibase_migration", "migration": "09-04-2026-downgrade-all-to-free-for-rollout", "reason": "free-only rollout, app.payments.enabled=false"}'::jsonb,
    NOW()
FROM company_subscription cs
WHERE cs.status = 'FREE_ACTIVE';

-- rollback UPDATE company_subscription SET status = 'FREE_ACTIVE' WHERE status = 'FREE_ACTIVE';
-- rollback -- Note: rollback is a no-op since we cannot reconstruct previous paid states.
-- rollback -- To restore paid subscriptions, re-enable payments and let users re-subscribe.
