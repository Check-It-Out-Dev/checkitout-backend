-- ============================================================================
-- 002-TABLES: CREATE ALL APPLICATION TABLES
-- ============================================================================
-- Consolidated schema with all columns and modifications
-- Includes all ALTER TABLE additions from migration history
-- ============================================================================

-- liquibase formatted sql
-- changeset system:002-create-all-tables

CREATE SCHEMA IF NOT EXISTS public;
SET search_path TO public;

-- ============================================================================
-- CORE ENTITIES
-- ============================================================================

-- User table (Influencers, Companies, Admins)
CREATE TABLE IF NOT EXISTS public."user" (
    id BIGINT PRIMARY KEY DEFAULT nextval('user_seq'),
    firebase_user_id VARCHAR(255) NOT NULL UNIQUE,
    user_type VARCHAR NOT NULL CHECK (user_type IN ('ADMIN', 'PENDING_ADMIN', 'COMPANY', 'INFLUENCER')),
    email VARCHAR(255) UNIQUE,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    name VARCHAR(255),
    profile_picture VARCHAR(2048),
    phone_number VARCHAR(25) UNIQUE,
    note_from_admin VARCHAR(5000) DEFAULT 'Uzupełnij swoje dane, żeby aktywować konto. Pamiętaj, że twoje konto powinno być publiczne i mieć przynajmniej 250 obserwujących.',
    account_status VARCHAR NOT NULL CHECK (account_status IN ('INACTIVE', 'IN_VALIDATION', 'ACTIVE', 'TO_BE_DELETED', 'DELETED')),
    company_description VARCHAR(1000),  -- Added 05-08-2025
    nip VARCHAR(20),  -- Added 05-08-2025
    premium BOOLEAN NOT NULL DEFAULT FALSE,  -- Added 16-08-2025
    created_time TIMESTAMP,
    last_update_time TIMESTAMP,
    updater_id VARCHAR(255),
    deleted_at TIMESTAMP,  -- soft delete - helps recover accounts
    CONSTRAINT chk_first_name_length CHECK (first_name IS NULL OR char_length(first_name) BETWEEN 2 AND 50),
    CONSTRAINT chk_last_name_length CHECK (last_name IS NULL OR char_length(last_name) BETWEEN 2 AND 50)
);

-- Address table (shared between users and partnership opportunities)
CREATE TABLE IF NOT EXISTS public.address (
    id BIGINT PRIMARY KEY DEFAULT nextval('address_seq'),
    user_id BIGINT REFERENCES "user"(id) ON DELETE CASCADE,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    additional_info VARCHAR(255),
    address_type VARCHAR(20) NOT NULL DEFAULT 'MAIN',
    is_primary BOOLEAN DEFAULT FALSE,
    -- Address sharing columns (added 22-06-2025)
    source_type VARCHAR(50) NOT NULL DEFAULT 'COPIED_FROM_USER',
    is_shared BOOLEAN NOT NULL DEFAULT false,
    reference_count INTEGER NOT NULL DEFAULT 1,
    is_copied BOOLEAN DEFAULT FALSE,
    source_address_id BIGINT REFERENCES address(id),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Platform table (Instagram, TikTok, etc.)
CREATE TABLE IF NOT EXISTS platform (
    id BIGINT PRIMARY KEY DEFAULT nextval('platform_seq'),
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(2048),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- City table
CREATE TABLE IF NOT EXISTS public.city (
    id BIGINT PRIMARY KEY DEFAULT nextval('city_seq'),
    name VARCHAR(255) NOT NULL UNIQUE,
    state VARCHAR(255),  -- Added 05-07-2025
    country VARCHAR(255) NOT NULL DEFAULT 'Polska'  -- Added 05-07-2025
);

-- Service type table
CREATE TABLE IF NOT EXISTS public.service_type (
    id BIGINT PRIMARY KEY DEFAULT nextval('service_type_seq'),
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    category VARCHAR(100),
    CONSTRAINT chk_name_length CHECK (char_length(name) BETWEEN 2 AND 100)
);

-- Content type table
CREATE TABLE IF NOT EXISTS content_type (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_type_seq'),
    name VARCHAR(255) NOT NULL
);

-- Currency table
CREATE TABLE IF NOT EXISTS currency (
    id BIGINT PRIMARY KEY DEFAULT nextval('currency_seq'),
    name VARCHAR(255) NOT NULL,
    iso_code VARCHAR(3) UNIQUE NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    sign VARCHAR(3) NOT NULL,
    UNIQUE (iso_code, country_code)
);

-- User social connections
CREATE TABLE user_social_connection (
    id BIGINT PRIMARY KEY DEFAULT nextval('user_social_connection_seq'),
    user_id BIGINT NOT NULL REFERENCES public."user"(id) ON DELETE CASCADE,
    platform_id BIGINT REFERENCES platform(id),
    social_user_id VARCHAR(2048) NOT NULL,
    profile_url VARCHAR(2048),
    profile_picture_url VARCHAR(2048),
    display_name VARCHAR(2048),
    email VARCHAR(2048),
    note VARCHAR(5000),
    service_id BIGINT REFERENCES service_type(id),
    followers_count INTEGER,
    is_primary BOOLEAN DEFAULT FALSE,
    connection_status VARCHAR NOT NULL CHECK (connection_status IN ('CONNECTED', 'EXPIRED', 'REVOKED')),
    last_sync_time TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, platform_id, social_user_id)
);

-- User preferences
CREATE TABLE IF NOT EXISTS public.user_preferences (
    id BIGINT PRIMARY KEY DEFAULT nextval('user_preferences_seq'),
    user_id BIGINT NOT NULL REFERENCES public."user"(id) ON DELETE CASCADE,
    notification_email_enabled BOOLEAN DEFAULT FALSE,
    notification_push_enabled BOOLEAN DEFAULT FALSE,
    notification_sms_enabled BOOLEAN DEFAULT FALSE,
    dark_mode_enabled BOOLEAN DEFAULT FALSE,
    language VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(50) DEFAULT 'UTC',
    communication_frequency VARCHAR(20) DEFAULT 'WEEKLY' CHECK (communication_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'NEVER')),
    gdpr_marketing_consent BOOLEAN DEFAULT FALSE,
    two_factor_authentication_enabled BOOLEAN DEFAULT FALSE,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- ============================================================================
-- PARTNERSHIP OPPORTUNITIES
-- ============================================================================

-- Partnership opportunity table
CREATE TABLE IF NOT EXISTS public.partnership_opportunity (
    id BIGINT PRIMARY KEY DEFAULT nextval('partnership_opportunity_seq'),
    name VARCHAR(255),
    city_id BIGINT REFERENCES city(id),
    company_id BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    title VARCHAR(255),
    details VARCHAR(255),
    address_id BIGINT REFERENCES address(id),  -- Changed from address INTEGER (22-06-2025)
    requirements VARCHAR(2000),
    compensation_type VARCHAR CHECK (compensation_type IN ('CASH','BARTER')),
    compensation_amount_min INTEGER,
    compensation_amount_max INTEGER,
    followers_min BIGINT DEFAULT 0,
    followers_max BIGINT DEFAULT 0,
    compensation_description VARCHAR,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    service_id BIGINT REFERENCES service_type(id),
    currency_id BIGINT REFERENCES currency(id),
    active BOOLEAN DEFAULT TRUE,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater_id VARCHAR(255)
);

-- Partnership opportunity photos
CREATE TABLE IF NOT EXISTS public.partnership_opportunity_photo (
    id BIGINT PRIMARY KEY DEFAULT nextval('partnership_opportunity_photo_seq'),
    partnership_opportunity_id BIGINT NOT NULL REFERENCES partnership_opportunity(id) ON DELETE CASCADE,
    url VARCHAR(2048) NOT NULL,
    order_number INTEGER,
    is_cover BOOLEAN DEFAULT FALSE
);

-- Applied opportunities
CREATE TABLE IF NOT EXISTS public.applied_opportunity (
    id BIGINT PRIMARY KEY DEFAULT nextval('applied_opportunity_seq'),
    influencer_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE SET NULL,
    partnership_opportunity_id BIGINT NOT NULL REFERENCES partnership_opportunity(id),
    opportunity_status VARCHAR NOT NULL DEFAULT 'APPLIED' CHECK (opportunity_status IN (
        'APPLIED',
        'ACCEPTED_BY_COMPANY',
        'REJECTED_BY_COMPANY',
        'ACCEPTED_BY_INFLUENCER',
        'REJECTED_BY_INFLUENCER',
        'CONTENT_SEND_TO_ACCEPT',  -- Added 11-07-2025
        'CONTENT_APPROVED',
        'CONTENT_REJECTED',
        'CONTENT_POSTED',
        'CONTENT_POSTED_REJECTED',  -- Added to match Java enum
        'TO_BE_PAID',
        'DONE'
    )),
    rate_status VARCHAR(10) NOT NULL DEFAULT 'DEFAULT' CHECK (rate_status IN ('DEFAULT', 'POSITIVE', 'NEGATIVE')),
    company_rate_status VARCHAR(10) NOT NULL DEFAULT 'DEFAULT' CHECK (company_rate_status IN ('DEFAULT', 'POSITIVE', 'NEGATIVE')),
    note VARCHAR(5000),
    execution_date TIMESTAMP,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater_id VARCHAR(255),
    UNIQUE (influencer_id, partnership_opportunity_id)
);

-- Applied opportunity status history (Added 28-06-2025)
CREATE TABLE IF NOT EXISTS public.applied_opportunity_status_history (
    id BIGINT PRIMARY KEY DEFAULT nextval('applied_opportunity_status_history_seq'),
    applied_opportunity_id BIGINT NOT NULL REFERENCES applied_opportunity(id) ON DELETE CASCADE,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    changed_by_user_id BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    changed_by_firebase_id VARCHAR(255),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_reason VARCHAR(500),
    notes VARCHAR(1000),
    CONSTRAINT chk_status_values CHECK (
        (previous_status IS NULL OR previous_status IN (
            'APPLIED', 'ACCEPTED_BY_COMPANY', 'REJECTED_BY_COMPANY', 
            'ACCEPTED_BY_INFLUENCER', 'REJECTED_BY_INFLUENCER', 'CONTENT_SEND_TO_ACCEPT',
            'CONTENT_APPROVED', 'CONTENT_REJECTED', 'CONTENT_POSTED', 
            'CONTENT_POSTED_REJECTED', 'TO_BE_PAID', 'DONE'
        )) AND
        new_status IN (
            'APPLIED', 'ACCEPTED_BY_COMPANY', 'REJECTED_BY_COMPANY', 
            'ACCEPTED_BY_INFLUENCER', 'REJECTED_BY_INFLUENCER', 'CONTENT_SEND_TO_ACCEPT',
            'CONTENT_APPROVED', 'CONTENT_REJECTED', 'CONTENT_POSTED',
            'CONTENT_POSTED_REJECTED', 'TO_BE_PAID', 'DONE'
        )
    )
);

-- Applied opportunity content (Added 28-06-2025)
CREATE TABLE applied_opportunity_content (
    id BIGINT PRIMARY KEY DEFAULT nextval('applied_opportunity_content_seq'),
    applied_opportunity_id BIGINT NOT NULL REFERENCES applied_opportunity(id) ON DELETE CASCADE,
    content_type_id BIGINT NOT NULL REFERENCES content_type(id) ON DELETE RESTRICT,
    content_count INTEGER,
    urls JSONB,
    description VARCHAR(1000),
    tags VARCHAR(500),
    social_media_link VARCHAR(1000),  -- Added 19-07-2025
    content_creation_date TIMESTAMP,
    submission_date TIMESTAMP,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    approval_notes VARCHAR(500),
    likes_count BIGINT DEFAULT 0,
    comments_count BIGINT DEFAULT 0,
    views_count BIGINT DEFAULT 0,
    shares_count BIGINT DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    last_update_time TIMESTAMP NOT NULL DEFAULT NOW(),
    updater_id VARCHAR(255),
    CONSTRAINT chk_approval_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'NEEDS_REVISION', 'SUBMITTED')),
    CONSTRAINT chk_content_count_positive CHECK (content_count > 0),
    CONSTRAINT chk_engagement_metrics_non_negative 
        CHECK (likes_count >= 0 AND comments_count >= 0 AND views_count >= 0 AND shares_count >= 0)
);

-- ============================================================================
-- SUPPORT SYSTEM (Added 20-04-2025)
-- ============================================================================

-- Support tickets
CREATE TABLE support_ticket (
    id BIGINT PRIMARY KEY DEFAULT nextval('support_ticket_seq'),
    user_id BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    contact_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    technical_description VARCHAR(100000),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    category VARCHAR(50) NOT NULL,
    ip_address VARCHAR(50),
    ticket_reference VARCHAR(20) NOT NULL UNIQUE,
    admin_assignee VARCHAR(255),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_time TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Ticket responses
CREATE TABLE ticket_response (
    id BIGINT PRIMARY KEY DEFAULT nextval('ticket_response_seq'),
    ticket_id BIGINT REFERENCES support_ticket(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    is_from_admin BOOLEAN NOT NULL,
    admin_name VARCHAR(255),
    is_email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Ticket attachments
CREATE TABLE ticket_attachment (
    id BIGINT PRIMARY KEY DEFAULT nextval('ticket_attachment_seq'),
    ticket_id BIGINT REFERENCES support_ticket(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(2048) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Response attachments
CREATE TABLE response_attachment (
    id BIGINT PRIMARY KEY DEFAULT nextval('response_attachment_seq'),
    response_id BIGINT REFERENCES ticket_response(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(2048) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Help article categories
CREATE TABLE help_article_category (
    id BIGINT PRIMARY KEY DEFAULT nextval('help_article_category_seq'),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    icon VARCHAR(100),
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Help article tags
CREATE TABLE help_article_tag (
    id BIGINT PRIMARY KEY DEFAULT nextval('help_article_tag_seq'),
    name VARCHAR(50) NOT NULL UNIQUE,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Help articles
CREATE TABLE help_article (
    id BIGINT PRIMARY KEY DEFAULT nextval('help_article_seq'),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    content TEXT NOT NULL,
    content_format VARCHAR(20) NOT NULL DEFAULT 'HTML',
    summary VARCHAR(500),
    category_id BIGINT REFERENCES help_article_category(id),
    meta_title VARCHAR(255),
    meta_description VARCHAR(500),
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    view_count INT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Help article media
CREATE TABLE help_article_media (
    id BIGINT PRIMARY KEY DEFAULT nextval('help_article_media_seq'),
    article_id BIGINT REFERENCES help_article(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(2048) NOT NULL,
    alt_text VARCHAR(255),
    caption VARCHAR(500),
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- Search query log
CREATE TABLE search_query_log (
    id BIGINT PRIMARY KEY DEFAULT nextval('search_query_log_seq'),
    query VARCHAR(255) NOT NULL,
    result_count INT NOT NULL,
    user_id BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    ip_address VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- ============================================================================
-- FAQ SYSTEM (Added 24-04-2025)
-- ============================================================================

-- FAQ categories
CREATE TABLE IF NOT EXISTS faq_category (
    id BIGINT PRIMARY KEY DEFAULT nextval('faq_category_seq'),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- FAQ entries
CREATE TABLE IF NOT EXISTS faq (
    id BIGINT PRIMARY KEY DEFAULT nextval('faq_seq'),
    category_id BIGINT REFERENCES faq_category(id) ON DELETE SET NULL,
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR(255)
);

-- ============================================================================
-- DICTIONARY AND TRANSLATIONS
-- ============================================================================

-- Dictionary entries
CREATE TABLE IF NOT EXISTS public.dictionary_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_key VARCHAR(255) NOT NULL,
    value VARCHAR(1000) NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    category VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater_id VARCHAR
);

-- ============================================================================
-- MANY-TO-MANY JUNCTION TABLES
-- ============================================================================

-- Platform content types
CREATE TABLE IF NOT EXISTS platform_content_type (
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    content_type_id BIGINT NOT NULL REFERENCES content_type(id) ON DELETE CASCADE,
    PRIMARY KEY (platform_id, content_type_id)
);

-- Partnership opportunity platforms
CREATE TABLE IF NOT EXISTS partnership_opportunity_platform (
    partnership_opportunity_id BIGINT NOT NULL REFERENCES partnership_opportunity(id) ON DELETE CASCADE,
    platform_id BIGINT NOT NULL REFERENCES platform(id) ON DELETE CASCADE,
    PRIMARY KEY (partnership_opportunity_id, platform_id)
);

-- Partnership opportunity content types
CREATE TABLE IF NOT EXISTS partnership_opportunity_content_type (
    partnership_opportunity_id BIGINT NOT NULL REFERENCES partnership_opportunity(id) ON DELETE CASCADE,
    content_type_id BIGINT NOT NULL REFERENCES content_type(id) ON DELETE CASCADE,
    PRIMARY KEY (partnership_opportunity_id, content_type_id)
);

-- Help article tags relation
CREATE TABLE help_article_tags (
    article_id BIGINT REFERENCES help_article(id) ON DELETE CASCADE,
    tag_id BIGINT REFERENCES help_article_tag(id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, tag_id),
    updater_id VARCHAR(255)
);

-- Help article related articles
CREATE TABLE help_article_related (
    article_id BIGINT REFERENCES help_article(id) ON DELETE CASCADE,
    related_article_id BIGINT REFERENCES help_article(id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, related_article_id),
    updater_id VARCHAR(255)
);

-- ============================================================================
-- TABLE COMMENTS
-- ============================================================================

COMMENT ON TABLE applied_opportunity_status_history IS 'Audit log for tracking all status changes in applied_opportunity table';
COMMENT ON COLUMN applied_opportunity_status_history.previous_status IS 'Status before the change (NULL for initial creation)';
COMMENT ON COLUMN applied_opportunity_status_history.new_status IS 'Status after the change';
COMMENT ON COLUMN applied_opportunity_status_history.changed_by_user_id IS 'Database ID of user who made the change';
COMMENT ON COLUMN applied_opportunity_status_history.changed_by_firebase_id IS 'Firebase ID of user who made the change (for backup reference)';
COMMENT ON COLUMN applied_opportunity_status_history.change_reason IS 'System-generated reason (e.g., "Status updated via API", "Bulk update")';
COMMENT ON COLUMN applied_opportunity_status_history.notes IS 'User-provided notes or additional context for the change';

COMMENT ON TABLE applied_opportunity_content IS 'Stores content submissions for applied opportunities';
COMMENT ON COLUMN applied_opportunity_content.content_type_id IS 'Foreign key to content_type table';
COMMENT ON COLUMN applied_opportunity_content.urls IS 'JSON array of URLs to the content';
COMMENT ON COLUMN applied_opportunity_content.approval_status IS 'Status of content approval workflow';
COMMENT ON COLUMN applied_opportunity_content.content_creation_date IS 'When the content was originally created/posted';
COMMENT ON COLUMN applied_opportunity_content.submission_date IS 'When the content was submitted for review';
COMMENT ON COLUMN applied_opportunity_content.social_media_link IS 'URL link to the content posted on social media platforms (e.g., Instagram post, TikTok video, etc.)';

-- rollback DROP TABLE IF EXISTS help_article_related CASCADE;
-- rollback DROP TABLE IF EXISTS help_article_tags CASCADE;
-- rollback DROP TABLE IF EXISTS partnership_opportunity_content_type CASCADE;
-- rollback DROP TABLE IF EXISTS partnership_opportunity_platform CASCADE;
-- rollback DROP TABLE IF EXISTS platform_content_type CASCADE;
-- rollback DROP TABLE IF EXISTS dictionary_entries CASCADE;
-- rollback DROP TABLE IF EXISTS faq CASCADE;
-- rollback DROP TABLE IF EXISTS faq_category CASCADE;
-- rollback DROP TABLE IF EXISTS search_query_log CASCADE;
-- rollback DROP TABLE IF EXISTS help_article_media CASCADE;
-- rollback DROP TABLE IF EXISTS help_article CASCADE;
-- rollback DROP TABLE IF EXISTS help_article_tag CASCADE;
-- rollback DROP TABLE IF EXISTS help_article_category CASCADE;
-- rollback DROP TABLE IF EXISTS response_attachment CASCADE;
-- rollback DROP TABLE IF EXISTS ticket_attachment CASCADE;
-- rollback DROP TABLE IF EXISTS ticket_response CASCADE;
-- rollback DROP TABLE IF EXISTS support_ticket CASCADE;
-- rollback DROP TABLE IF EXISTS applied_opportunity_content CASCADE;
-- rollback DROP TABLE IF EXISTS applied_opportunity_status_history CASCADE;
-- rollback DROP TABLE IF EXISTS applied_opportunity CASCADE;
-- rollback DROP TABLE IF EXISTS partnership_opportunity_photo CASCADE;
-- rollback DROP TABLE IF EXISTS partnership_opportunity CASCADE;
-- rollback DROP TABLE IF EXISTS user_preferences CASCADE;
-- rollback DROP TABLE IF EXISTS user_social_connection CASCADE;
-- rollback DROP TABLE IF EXISTS currency CASCADE;
-- rollback DROP TABLE IF EXISTS content_type CASCADE;
-- rollback DROP TABLE IF EXISTS service_type CASCADE;
-- rollback DROP TABLE IF EXISTS city CASCADE;
-- rollback DROP TABLE IF EXISTS platform CASCADE;
-- rollback DROP TABLE IF EXISTS address CASCADE;
-- rollback DROP TABLE IF EXISTS "user" CASCADE;
