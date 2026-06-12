-- liquibase formatted sql
-- changeset system:create-cascade-delete-task-table
-- Create cascade_delete_task table for tracking multi-system delete operations
-- Supports retry queue for partial failures during user/entity deletion

-- Create sequence for cascade_delete_task IDs
CREATE SEQUENCE IF NOT EXISTS cascade_delete_task_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Create cascade_delete_task table
CREATE TABLE IF NOT EXISTS cascade_delete_task (
    id BIGINT PRIMARY KEY DEFAULT nextval('cascade_delete_task_seq'),

    -- Target identification
    user_id BIGINT,
    firebase_user_id VARCHAR(255),
    entity_type VARCHAR(50) NOT NULL,

    -- Audit information
    reason VARCHAR(500) NOT NULL,
    admin_firebase_id VARCHAR(255) NOT NULL,
    archive_url VARCHAR(2048),

    -- Per-system status tracking
    postgresql_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    firestore_instagram_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    firestore_totp_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    firebase_storage_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    firebase_auth_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Error tracking
    error_details TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_postgresql_status CHECK (postgresql_status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_firestore_instagram_status CHECK (firestore_instagram_status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_firestore_totp_status CHECK (firestore_totp_status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_firebase_storage_status CHECK (firebase_storage_status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_firebase_auth_status CHECK (firebase_auth_status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('USER', 'PARTNERSHIP_OPPORTUNITY', 'APPLIED_OPPORTUNITY'))
);

-- Create indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_cascade_delete_task_completed ON cascade_delete_task(completed_at);
CREATE INDEX IF NOT EXISTS idx_cascade_delete_task_user ON cascade_delete_task(user_id);
CREATE INDEX IF NOT EXISTS idx_cascade_delete_task_firebase ON cascade_delete_task(firebase_user_id);
CREATE INDEX IF NOT EXISTS idx_cascade_delete_task_retry ON cascade_delete_task(completed_at, retry_count) WHERE completed_at IS NULL;

-- Add comments
COMMENT ON TABLE cascade_delete_task IS 'Tracks cascade delete operations across multiple systems (PostgreSQL, Firestore, Firebase Auth) with retry support for partial failures';
COMMENT ON COLUMN cascade_delete_task.user_id IS 'PostgreSQL user ID being deleted. May be null for orphaned Firebase user cleanup';
COMMENT ON COLUMN cascade_delete_task.firebase_user_id IS 'Firebase UID for Firestore and Firebase Auth cleanup';
COMMENT ON COLUMN cascade_delete_task.entity_type IS 'Type of entity being deleted: USER, PARTNERSHIP_OPPORTUNITY, or APPLIED_OPPORTUNITY';
COMMENT ON COLUMN cascade_delete_task.reason IS 'Reason for deletion (GDPR request, admin cleanup, spam, test data, etc.)';
COMMENT ON COLUMN cascade_delete_task.admin_firebase_id IS 'Firebase UID of the admin who initiated the deletion';
COMMENT ON COLUMN cascade_delete_task.archive_url IS 'GCS path to archived data (if archival succeeded)';
COMMENT ON COLUMN cascade_delete_task.postgresql_status IS 'Status of PostgreSQL deletion: PENDING, SUCCESS, FAILED, SKIPPED';
COMMENT ON COLUMN cascade_delete_task.firestore_instagram_status IS 'Status of Firestore instagramUsers cleanup';
COMMENT ON COLUMN cascade_delete_task.firestore_totp_status IS 'Status of Firestore totpSecrets cleanup';
COMMENT ON COLUMN cascade_delete_task.firebase_storage_status IS 'Status of Firebase Storage cleanup';
COMMENT ON COLUMN cascade_delete_task.firebase_auth_status IS 'Status of Firebase Auth user deletion';
COMMENT ON COLUMN cascade_delete_task.error_details IS 'JSON format error details if any system failed';
COMMENT ON COLUMN cascade_delete_task.retry_count IS 'Number of retry attempts for failed systems';
COMMENT ON COLUMN cascade_delete_task.completed_at IS 'When all systems completed (SUCCESS or SKIPPED). Null if any system is PENDING or FAILED';

-- rollback DROP TABLE IF EXISTS cascade_delete_task;
-- rollback DROP SEQUENCE IF EXISTS cascade_delete_task_seq;
