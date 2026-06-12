-- liquibase formatted sql

-- changeset norbert:instagram-pending-data-deletion-request
-- comment: Create table for tracking Meta data deletion requests with deferred processing

CREATE SEQUENCE IF NOT EXISTS pending_data_deletion_request_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE pending_data_deletion_request (
    id                  BIGINT          NOT NULL DEFAULT nextval('pending_data_deletion_request_seq'),
    user_id             BIGINT          NOT NULL,
    confirmation_code   VARCHAR(36)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    requested_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    blockers            JSONB,
    CONSTRAINT pk_pending_data_deletion_request PRIMARY KEY (id),
    CONSTRAINT fk_pending_data_deletion_request_user FOREIGN KEY (user_id) REFERENCES "user"(id),
    CONSTRAINT uq_pending_data_deletion_confirmation_code UNIQUE (confirmation_code)
);

CREATE INDEX idx_pending_data_deletion_status ON pending_data_deletion_request(status);
CREATE INDEX idx_pending_data_deletion_user_id ON pending_data_deletion_request(user_id);

-- rollback DROP TABLE IF EXISTS pending_data_deletion_request;
-- rollback DROP SEQUENCE IF EXISTS pending_data_deletion_request_seq;
