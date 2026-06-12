-- liquibase formatted sql
-- changeset system:subscription-tables

-- ============================================================================
-- SUBSCRIPTION MODULE: Tables for Stripe payment gateway, billing periods,
-- invoice tracking (Fakturownia), and terms/pricing versioning.
-- State machine: 10 states, 56 transitions (modeled in Neo4j namespace 'subscription')
-- ============================================================================

-- ============================================================================
-- SEQUENCES (Hibernate pooled-lo optimizer, allocationSize=50)
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS subscription_plan_seq START WITH 100 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS company_subscription_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS subscription_event_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS billing_period_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS invoice_record_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS terms_version_seq START WITH 1 INCREMENT BY 50 CACHE 50;

-- ============================================================================
-- TABLES
-- ============================================================================

-- 1. subscription_plan: static reference data (FREE, BUSINESS, ENTERPRISE)
CREATE TABLE IF NOT EXISTS public.subscription_plan (
    id BIGINT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    price_pln NUMERIC(10,2) NOT NULL,
    campaign_limit INT NOT NULL,
    stripe_price_id VARCHAR(100),
    stripe_product_id VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT subscription_plan_name_check CHECK (name IN ('FREE', 'BUSINESS', 'ENTERPRISE')),
    CONSTRAINT subscription_plan_name_unique UNIQUE (name)
);

COMMENT ON TABLE subscription_plan IS 'Static subscription plan definitions with Stripe Price IDs and campaign limits.';

-- 2. company_subscription: 1:1 with User (company accounts only)
CREATE TABLE IF NOT EXISTS public.company_subscription (
    id BIGINT PRIMARY KEY DEFAULT nextval('company_subscription_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    current_plan_id BIGINT NOT NULL REFERENCES subscription_plan(id),
    previous_plan_id BIGINT REFERENCES subscription_plan(id),
    status VARCHAR(30) NOT NULL,
    previous_state VARCHAR(30),
    target_plan_id BIGINT REFERENCES subscription_plan(id),
    trial_end_date TIMESTAMP,
    trial_used BOOLEAN NOT NULL DEFAULT FALSE,
    stripe_customer_id VARCHAR(100),
    stripe_subscription_id VARCHAR(100),
    stripe_schedule_id VARCHAR(100),
    newest_terms_accepted BOOLEAN NOT NULL DEFAULT TRUE,
    grace_deadline TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id BIGINT,

    CONSTRAINT company_subscription_user_unique UNIQUE (user_id),
    CONSTRAINT company_subscription_status_check CHECK (status IN (
        'FREE_ACTIVE', 'TRIAL_ENTERPRISE', 'BUSINESS_ACTIVE', 'ENTERPRISE_ACTIVE',
        'DOWNGRADE_PENDING', 'PAYMENT_FAILED', 'TERMS_PENDING', 'SUSPENDED_LEGAL',
        'ACCOUNT_DEACTIVATED'
    )),
    CONSTRAINT company_subscription_previous_state_check CHECK (previous_state IS NULL OR previous_state IN (
        'FREE_ACTIVE', 'TRIAL_ENTERPRISE', 'BUSINESS_ACTIVE', 'ENTERPRISE_ACTIVE',
        'DOWNGRADE_PENDING', 'PAYMENT_FAILED'
    ))
);

COMMENT ON TABLE company_subscription IS 'Subscription state for each company. 1:1 with user. Optimistic locking via version column.';

-- 3. subscription_event: immutable event log
CREATE TABLE IF NOT EXISTS public.subscription_event (
    id BIGINT PRIMARY KEY DEFAULT nextval('subscription_event_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    plan_from VARCHAR(20),
    plan_to VARCHAR(20),
    stripe_event_id VARCHAR(100),
    billing_period_start TIMESTAMP,
    billing_period_end TIMESTAMP,
    metadata JSONB,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT subscription_event_stripe_unique UNIQUE (stripe_event_id)
);

COMMENT ON TABLE subscription_event IS 'Immutable event log. stripe_event_id unique constraint enforces webhook idempotency.';

-- 4. billing_period: tracks billing cycles per company
CREATE TABLE IF NOT EXISTS public.billing_period (
    id BIGINT PRIMARY KEY DEFAULT nextval('billing_period_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES subscription_plan(id),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT billing_period_status_check CHECK (status IN ('ACTIVE', 'EXPIRED', 'PENDING_DOWNGRADE')),
    CONSTRAINT billing_period_dates_check CHECK (end_date > start_date)
);

COMMENT ON TABLE billing_period IS 'Billing periods per company. Campaign count is per active billing period.';

-- 5. invoice_record: Fakturownia invoice tracking with async retry
CREATE TABLE IF NOT EXISTS public.invoice_record (
    id BIGINT PRIMARY KEY DEFAULT nextval('invoice_record_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    billing_period_id BIGINT REFERENCES billing_period(id),
    fakturownia_invoice_id BIGINT,
    invoice_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    amount_pln NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    error_message TEXT,
    last_attempt_at TIMESTAMP,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT invoice_record_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD_LETTER')),
    CONSTRAINT invoice_record_type_check CHECK (invoice_type IN ('STANDARD'))
);

COMMENT ON TABLE invoice_record IS 'Tracks Fakturownia invoice creation. Async fire-and-forget with retry cron. DEAD_LETTER triggers admin alert.';

-- 6. terms_version: pricing and terms versioning
CREATE TABLE IF NOT EXISTS public.terms_version (
    id BIGINT PRIMARY KEY DEFAULT nextval('terms_version_seq'),
    version INT NOT NULL,
    content_hash VARCHAR(100) NOT NULL,
    pricing_snapshot JSONB,
    published_at TIMESTAMP,
    grace_period_days INT NOT NULL DEFAULT 38,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT terms_version_version_unique UNIQUE (version)
);

COMMENT ON TABLE terms_version IS 'Versioned terms and pricing. 38-day grace period for acceptance. Pricing snapshot is JSON of plan prices at time of publication.';

-- ============================================================================
-- INDEXES
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_company_subscription_user ON company_subscription(user_id);
CREATE INDEX IF NOT EXISTS idx_company_subscription_status ON company_subscription(status);
CREATE INDEX IF NOT EXISTS idx_company_subscription_stripe_customer ON company_subscription(stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_subscription_event_user ON subscription_event(user_id);
CREATE INDEX IF NOT EXISTS idx_subscription_event_stripe ON subscription_event(stripe_event_id);
CREATE INDEX IF NOT EXISTS idx_billing_period_user_status ON billing_period(user_id, status);
CREATE INDEX IF NOT EXISTS idx_billing_period_dates ON billing_period(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_invoice_record_status ON invoice_record(status, retry_count);
CREATE INDEX IF NOT EXISTS idx_invoice_record_user ON invoice_record(user_id);

-- Partial index for retry cron: only pick retryable records
CREATE INDEX IF NOT EXISTS idx_invoice_record_retryable
    ON invoice_record(last_attempt_at)
    WHERE status IN ('PENDING', 'FAILED') AND retry_count < 5;

-- ============================================================================
-- TRIGGERS (reuse existing trigger functions from 004-triggers.sql)
-- ============================================================================

CREATE TRIGGER set_created_time_company_subscription
BEFORE INSERT ON company_subscription FOR EACH ROW EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_last_update_time_company_subscription
BEFORE UPDATE ON company_subscription FOR EACH ROW EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_created_time_subscription_event
BEFORE INSERT ON subscription_event FOR EACH ROW EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_billing_period
BEFORE INSERT ON billing_period FOR EACH ROW EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_invoice_record
BEFORE INSERT ON invoice_record FOR EACH ROW EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_terms_version
BEFORE INSERT ON terms_version FOR EACH ROW EXECUTE FUNCTION set_created_time();

-- ============================================================================
-- SEED DATA: Subscription plans
-- ============================================================================

INSERT INTO subscription_plan (id, name, price_pln, campaign_limit, stripe_price_id, stripe_product_id) VALUES
    (1, 'FREE', 0.00, 2, NULL, NULL),
    (2, 'BUSINESS', 29.00, 5, 'price_1TDprAEF0n7JDo59KpKh8vnt', 'prod_UCEJdIcXpCI8XY'),
    (3, 'ENTERPRISE', 99.00, 10, 'price_1TDprIEF0n7JDo591xcehTLl', 'prod_UCEJEPOpJvXeut');

-- ============================================================================
-- MODIFY EXISTING: Add SUBSCRIPTION_PURCHASE to consent_record source
-- ============================================================================

ALTER TABLE consent_record DROP CONSTRAINT IF EXISTS consent_record_source_check;
ALTER TABLE consent_record ADD CONSTRAINT consent_record_source_check CHECK (source IN (
    'REGISTRATION', 'OAUTH_REGISTRATION', 'SOCIAL_REGISTRATION',
    'LOGIN_PROMPT', 'COOKIE_BANNER', 'SETTINGS', 'ACCOUNT_DELETION',
    'COMPANY_DATA_FORM', 'SUBSCRIPTION_PURCHASE'
));

COMMENT ON CONSTRAINT consent_record_source_check ON consent_record IS 'Valid consent sources. SUBSCRIPTION_PURCHASE for EU Article 16(m) immediate activation consent.';

-- rollback ALTER TABLE consent_record DROP CONSTRAINT IF EXISTS consent_record_source_check;
-- rollback ALTER TABLE consent_record ADD CONSTRAINT consent_record_source_check CHECK (source IN ('REGISTRATION', 'OAUTH_REGISTRATION', 'SOCIAL_REGISTRATION', 'LOGIN_PROMPT', 'COOKIE_BANNER', 'SETTINGS', 'ACCOUNT_DELETION', 'COMPANY_DATA_FORM'));
-- rollback DROP TABLE IF EXISTS terms_version CASCADE;
-- rollback DROP TABLE IF EXISTS invoice_record CASCADE;
-- rollback DROP TABLE IF EXISTS billing_period CASCADE;
-- rollback DROP TABLE IF EXISTS subscription_event CASCADE;
-- rollback DROP TABLE IF EXISTS company_subscription CASCADE;
-- rollback DROP TABLE IF EXISTS subscription_plan CASCADE;
-- rollback DROP SEQUENCE IF EXISTS terms_version_seq;
-- rollback DROP SEQUENCE IF EXISTS invoice_record_seq;
-- rollback DROP SEQUENCE IF EXISTS billing_period_seq;
-- rollback DROP SEQUENCE IF EXISTS subscription_event_seq;
-- rollback DROP SEQUENCE IF EXISTS company_subscription_seq;
-- rollback DROP SEQUENCE IF EXISTS subscription_plan_seq;
