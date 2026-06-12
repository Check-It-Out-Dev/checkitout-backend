-- ============================================================================
-- 005-FILE-UPLOADS-TABLE: CREATE FILE UPLOADS TABLE FOR STORAGE SYSTEM
-- ============================================================================
-- Creates the file_uploads table for tracking Google Cloud Storage uploads
-- ============================================================================

-- liquibase formatted sql
-- changeset file-upload-system:005-create-file-uploads-table

-- File uploads table for tracking upload status and metadata
CREATE TABLE IF NOT EXISTS file_uploads (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL UNIQUE,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_time TIMESTAMP WITH TIME ZONE NOT NULL,
    public_url VARCHAR(1000),
    description TEXT,
    alt_text VARCHAR(500),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'WEBHOOK', 'FAILED', 'DELETED')),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_file_uploads_user_id ON file_uploads(user_id);
CREATE INDEX IF NOT EXISTS idx_file_uploads_upload_time ON file_uploads(upload_time);
CREATE INDEX IF NOT EXISTS idx_file_uploads_file_path ON file_uploads(file_path);
CREATE INDEX IF NOT EXISTS idx_file_uploads_status_created_at ON file_uploads(status, created_at);

-- Table and column comments
COMMENT ON TABLE file_uploads IS 'Tracks file upload status and metadata for Google Cloud Storage files';
COMMENT ON COLUMN file_uploads.id IS 'UUID identifier for the file upload';
COMMENT ON COLUMN file_uploads.user_id IS 'Firebase user ID who uploaded the file';
COMMENT ON COLUMN file_uploads.file_path IS 'Unique path to the file in Google Cloud Storage';
COMMENT ON COLUMN file_uploads.status IS 'Upload status: PENDING (signed URL generated), CONFIRMED (upload confirmed), WEBHOOK (confirmed via Firebase), FAILED, DELETED';
COMMENT ON COLUMN file_uploads.upload_time IS 'When the file was uploaded to Google Cloud Storage';
COMMENT ON COLUMN file_uploads.confirmed_at IS 'When the upload was confirmed as successful';

-- rollback DROP TABLE IF EXISTS file_uploads CASCADE;
