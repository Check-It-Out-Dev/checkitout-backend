-- liquibase formatted sql

-- changeset system:update-free-plan-campaign-limit
-- comment: Free-only rollout — raise FREE plan campaign_limit from 2 to 5.
-- This migration is intentionally NOT coupled to app.payments.enabled. Reverting
-- the toggle does not revert the limit; if FREE=2 must be restored later, ship a
-- separate migration.
UPDATE subscription_plan SET campaign_limit = 5 WHERE name = 'FREE';

-- rollback UPDATE subscription_plan SET campaign_limit = 2 WHERE name = 'FREE';
