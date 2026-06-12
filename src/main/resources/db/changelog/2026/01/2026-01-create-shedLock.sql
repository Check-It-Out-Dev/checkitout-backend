-- changeset notification:2026-01-shedlock-table

-- ========================================================================
-- SHEDLOCK TABLE
-- Purpose:
-- Distributed lock storage for @SchedulerLock in Kubernetes environment.
-- Ensures only ONE pod executes scheduled jobs at a time.
-- ========================================================================

CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

-- ========================================================================
-- INDEXES
-- ========================================================================

-- Index to speed up lock expiration checks
CREATE INDEX idx_shedlock_lock_until
    ON shedlock(lock_until);

-- ========================================================================
-- NOTES
-- - Table is intentionally small (one row per scheduled job)
-- - Uses DB time (NOW()) when using JdbcTemplateLockProvider#usingDbTime()
-- - Safe for multi-pod Kubernetes deployments
-- ========================================================================

-- rollback DROP INDEX IF EXISTS idx_shedlock_lock_until;
-- rollback DROP TABLE IF EXISTS shedlock;
