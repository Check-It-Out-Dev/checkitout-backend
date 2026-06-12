-- ============================================================================
-- THE ONE PRODUCTION DATABASE INIT SCRIPT FOR OVH POSTGRESQL
-- ============================================================================
-- Run this ONCE as 'avnadmin' in pgAdmin on your OVH database
-- This does EVERYTHING needed for production
-- ============================================================================

-- ============================================================================
-- 1. CREATE USER
-- ============================================================================

-- First revoke and drop owned objects if role exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'checkitout_app_prod') THEN
        -- Reassign owned objects to current user
        REASSIGN OWNED BY checkitout_app_prod TO CURRENT_USER;
        -- Drop any remaining owned objects
        DROP OWNED BY checkitout_app_prod;
        -- Now drop the role
        DROP ROLE checkitout_app_prod;
    END IF;
END $$;

CREATE ROLE checkitout_app_prod WITH
    LOGIN
    PASSWORD 'CHANGE_ME_STRONG_PASSWORD'  -- CHANGE THIS!
    NOSUPERUSER
    INHERIT
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION;

-- ============================================================================
-- 2. GRANT PERMISSIONS AND TRANSFER OWNERSHIP
-- ============================================================================

-- Transfer schema ownership to app user (mirrors OVH production model)
ALTER SCHEMA public OWNER TO checkitout_app_prod;

-- Additional grants for completeness (owner already has these implicitly)
GRANT ALL ON SCHEMA public TO checkitout_app_prod;
GRANT CREATE, USAGE ON SCHEMA public TO checkitout_app_prod;

-- Database - GRANT ALL PRIVILEGES (includes CONNECT, CREATE, TEMPORARY)
GRANT ALL PRIVILEGES ON DATABASE checkitout_app_prod_database TO checkitout_app_prod;

-- ============================================================================
-- 3. CREATE ALL SEQUENCES
-- ============================================================================

-- Hibernate default (keep at 1)
CREATE SEQUENCE IF NOT EXISTS hibernate_sequence
    START WITH 1 INCREMENT BY 1 CACHE 1;

-- All entity sequences (INCREMENT BY 50 for Hibernate pooled-lo optimizer)
CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS address_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS applied_opportunity_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS applied_opportunity_content_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS applied_opportunity_status_history_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS city_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS content_type_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS currency_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS faq_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS faq_category_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_category_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_media_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_tag_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS partnership_opportunity_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS partnership_opportunity_photo_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS platform_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS response_attachment_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS service_type_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS support_ticket_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS ticket_attachment_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS ticket_response_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS user_preferences_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS user_social_connection_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS search_query_log_seq START WITH 1 INCREMENT BY 50 CACHE 50;

-- ============================================================================
-- 4. CREATE LIQUIBASE TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS databasechangelog (
    id VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    dateexecuted TIMESTAMP NOT NULL,
    orderexecuted INTEGER NOT NULL,
    exectype VARCHAR(10) NOT NULL,
    md5sum VARCHAR(35),
    description VARCHAR(255),
    comments VARCHAR(255),
    tag VARCHAR(255),
    liquibase VARCHAR(20),
    contexts VARCHAR(255),
    labels VARCHAR(255),
    deployment_id VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS databasechangeloglock (
    id INTEGER NOT NULL,
    locked BOOLEAN NOT NULL,
    lockgranted TIMESTAMP,
    lockedby VARCHAR(255),
    PRIMARY KEY (id)
);

-- Initialize lock
INSERT INTO databasechangeloglock (id, locked) 
VALUES (1, FALSE) 
ON CONFLICT (id) DO UPDATE SET locked = FALSE;

-- ============================================================================
-- 5. GRANT ALL PERMISSIONS (CURRENT AND FUTURE)
-- ============================================================================

-- Current objects
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO checkitout_app_prod;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO checkitout_app_prod;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO checkitout_app_prod;
GRANT ALL PRIVILEGES ON ALL PROCEDURES IN SCHEMA public TO checkitout_app_prod;

-- Future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL PRIVILEGES ON TABLES TO checkitout_app_prod;

ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL PRIVILEGES ON SEQUENCES TO checkitout_app_prod;

ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL PRIVILEGES ON FUNCTIONS TO checkitout_app_prod;

ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL PRIVILEGES ON TYPES TO checkitout_app_prod;

-- Objects created by app user itself
ALTER DEFAULT PRIVILEGES FOR ROLE checkitout_app_prod IN SCHEMA public 
    GRANT ALL PRIVILEGES ON TABLES TO checkitout_app_prod;

ALTER DEFAULT PRIVILEGES FOR ROLE checkitout_app_prod IN SCHEMA public 
    GRANT ALL PRIVILEGES ON SEQUENCES TO checkitout_app_prod;

-- ============================================================================
-- 6. USEFUL EXTENSIONS
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- DONE! 
-- ============================================================================

SELECT 'DATABASE READY FOR PRODUCTION!' AS status;

-- Quick verification:
SELECT 
    'Sequences: ' || COUNT(*) || ' created' AS check_1
FROM pg_sequences 
WHERE schemaname = 'public'
UNION ALL
SELECT 
    'User can login: ' || CASE WHEN rolcanlogin THEN 'YES' ELSE 'NO' END
FROM pg_roles 
WHERE rolname = 'checkitout_app_prod'
UNION ALL
SELECT 
    'Schema owner: ' || nspowner::regrole
FROM pg_namespace
WHERE nspname = 'public'
UNION ALL
SELECT 
    'Liquibase tables: ' || COUNT(*) || ' ready'
FROM information_schema.tables
WHERE table_schema = 'public'
    AND table_name IN ('databasechangelog', 'databasechangeloglock');

-- ============================================================================
-- REMEMBER:
-- 1. Change the password above
-- 2. Update your application-prod.yml with the same password
-- 3. Your entities must use allocationSize=50 (except hibernate_sequence=1)
-- 4. Run your app - Liquibase will create all tables
-- ============================================================================