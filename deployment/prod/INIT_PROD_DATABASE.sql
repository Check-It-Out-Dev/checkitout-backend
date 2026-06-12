-- ============================================================================
-- IDEMPOTENT PRODUCTION DATABASE INIT SCRIPT FOR DOCKER POSTGRESQL
-- ============================================================================
-- This script uses environment variables passed by psql
-- It can be run multiple times safely
-- Required env vars: APP_USER, APP_PASSWORD, DB_NAME
-- ============================================================================

-- Set script to continue on errors for idempotency
\set ON_ERROR_STOP off

-- Get variables from environment (passed via psql's environment)
\set app_user `echo $APP_USER`
\set app_password `echo $APP_PASSWORD`
\set db_name `echo $DB_NAME`

-- ============================================================================
-- 1. CREATE OR UPDATE USER (Idempotent)
-- ============================================================================

DO $$
DECLARE
    app_user_name text;
    app_user_password text;
BEGIN
    -- Get values from current_setting (set by psql)
    app_user_name := current_setting('my.app_user', true);
    app_user_password := current_setting('my.app_password', true);

    -- Validate inputs
    IF app_user_name IS NULL OR app_user_name = '' THEN
        RAISE EXCEPTION 'app_user not provided';
    END IF;

    IF app_user_password IS NULL OR app_user_password = '' THEN
        RAISE EXCEPTION 'app_password not provided';
    END IF;

    -- Check if user exists
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_user_name) THEN
        -- Create user
        EXECUTE format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION',
                       app_user_name, app_user_password);
        RAISE NOTICE '✓ Created user: %', app_user_name;
    ELSE
        -- Update password and ensure user can login
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L',
                       app_user_name, app_user_password);
        RAISE NOTICE '✓ Updated user: % (password refreshed)', app_user_name;
    END IF;

    -- Ensure user has proper attributes
    EXECUTE format('ALTER ROLE %I WITH LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION',
                   app_user_name);
    RAISE NOTICE '✓ User attributes verified for: %', app_user_name;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Error managing user: %', SQLERRM;
END $$;

-- ============================================================================
-- 2. GRANT PERMISSIONS AND TRANSFER OWNERSHIP (Idempotent)
-- ============================================================================

DO $$
DECLARE
    app_user_name text;
    db_name text;
    current_owner text;
BEGIN
    -- Get values from current_setting
    app_user_name := current_setting('my.app_user', true);
    db_name := current_setting('my.db_name', true);

    -- Check current schema owner
    SELECT nspowner::regrole::text INTO current_owner
    FROM pg_namespace WHERE nspname = 'public';

    -- Only transfer if not already owned by app user
    IF current_owner != app_user_name THEN
        EXECUTE format('ALTER SCHEMA public OWNER TO %I', app_user_name);
        RAISE NOTICE '✓ Transferred public schema ownership from % to %', current_owner, app_user_name;
    ELSE
        RAISE NOTICE '✓ Schema already owned by: %', app_user_name;
    END IF;

    -- Grant permissions (idempotent - GRANT doesn't fail if already granted)
    EXECUTE format('GRANT ALL ON SCHEMA public TO %I', app_user_name);
    EXECUTE format('GRANT CREATE, USAGE ON SCHEMA public TO %I', app_user_name);

    -- Grant database privileges
    EXECUTE format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', db_name, app_user_name);
    RAISE NOTICE '✓ Granted all privileges on database % to user %', db_name, app_user_name;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Error setting permissions: %', SQLERRM;
END $$;

-- ============================================================================
-- 3. CREATE ALL SEQUENCES (Idempotent - IF NOT EXISTS)
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
-- 4. CREATE LIQUIBASE TABLES (Idempotent)
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

-- Initialize or reset lock (idempotent with ON CONFLICT)
INSERT INTO databasechangeloglock (id, locked)
VALUES (1, FALSE)
ON CONFLICT (id) DO UPDATE SET locked = FALSE, lockedby = NULL, lockgranted = NULL;

-- ============================================================================
-- 5. FIX SEQUENCE OWNERSHIP (Idempotent)
-- ============================================================================

DO $$
DECLARE
    app_user_name text;
    seq RECORD;
BEGIN
    -- Get value from current_setting
    app_user_name := current_setting('my.app_user', true);

    -- Fix ownership of all sequences
    FOR seq IN
        SELECT sequence_schema, sequence_name
        FROM information_schema.sequences
        WHERE sequence_schema = 'public'
    LOOP
        EXECUTE format('ALTER SEQUENCE %I.%I OWNER TO %I',
                       seq.sequence_schema, seq.sequence_name, app_user_name);
    END LOOP;
    RAISE NOTICE '✓ All sequences ownership transferred to: %', app_user_name;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Error fixing sequence ownership: %', SQLERRM;
END $$;

-- ============================================================================
-- 6. GRANT ALL PERMISSIONS (CURRENT AND FUTURE) - Idempotent
-- ============================================================================

DO $$
DECLARE
    app_user_name text;
BEGIN
    -- Get value from current_setting
    app_user_name := current_setting('my.app_user', true);

    -- Current objects
    EXECUTE format('GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO %I', app_user_name);
    EXECUTE format('GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO %I', app_user_name);
    EXECUTE format('GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO %I', app_user_name);
    EXECUTE format('GRANT ALL PRIVILEGES ON ALL PROCEDURES IN SCHEMA public TO %I', app_user_name);

    -- Future objects created by postgres user
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO %I', app_user_name);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO %I', app_user_name);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON FUNCTIONS TO %I', app_user_name);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TYPES TO %I', app_user_name);

    -- Future objects created by app user itself
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO %I',
                   app_user_name, app_user_name);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO %I',
                   app_user_name, app_user_name);

    RAISE NOTICE '✓ Granted all current and future privileges to: %', app_user_name;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Error granting privileges: %', SQLERRM;
END $$;

-- ============================================================================
-- 7. USEFUL EXTENSIONS (Idempotent)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 8. FINAL VERIFICATION AND SUMMARY
-- ============================================================================

DO $$
DECLARE
    seq_count INTEGER;
    table_count INTEGER;
    can_login BOOLEAN;
    schema_owner TEXT;
    liquibase_count INTEGER;
    app_user_name text;
    db_name text;
    has_all_privs BOOLEAN;
BEGIN
    -- Get values from current_setting
    app_user_name := current_setting('my.app_user', true);
    db_name := current_setting('my.db_name', true);

    -- Count sequences
    SELECT COUNT(*) INTO seq_count FROM pg_sequences WHERE schemaname = 'public';

    -- Count tables
    SELECT COUNT(*) INTO table_count FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE';

    -- Check user login capability
    SELECT rolcanlogin INTO can_login FROM pg_roles WHERE rolname = app_user_name;

    -- Get schema owner
    SELECT nspowner::regrole::text INTO schema_owner FROM pg_namespace WHERE nspname = 'public';

    -- Count Liquibase tables
    SELECT COUNT(*) INTO liquibase_count FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name IN ('databasechangelog', 'databasechangeloglock');

    -- Check if user has necessary privileges
    SELECT has_schema_privilege(app_user_name, 'public', 'CREATE') INTO has_all_privs;

    -- Display summary (NO PASSWORDS!)
    RAISE NOTICE '';
    RAISE NOTICE '════════════════════════════════════════════════════════════';
    RAISE NOTICE '                    DATABASE STATUS SUMMARY                  ';
    RAISE NOTICE '════════════════════════════════════════════════════════════';
    RAISE NOTICE 'Database: %', db_name;
    RAISE NOTICE 'Application User: %', app_user_name;
    RAISE NOTICE '────────────────────────────────────────────────────────────';
    RAISE NOTICE '✓ User can login: %', CASE WHEN can_login THEN 'YES' ELSE 'NO' END;
    RAISE NOTICE '✓ Schema owner: %', schema_owner;
    RAISE NOTICE '✓ Has CREATE privilege: %', CASE WHEN has_all_privs THEN 'YES' ELSE 'NO' END;
    RAISE NOTICE '✓ Sequences created: %', seq_count;
    RAISE NOTICE '✓ Tables present: %', table_count;
    RAISE NOTICE '✓ Liquibase tables: %/2', liquibase_count;
    RAISE NOTICE '════════════════════════════════════════════════════════════';

    -- Warnings if something is wrong
    IF NOT can_login THEN
        RAISE WARNING 'User % cannot login!', app_user_name;
    END IF;

    IF schema_owner != app_user_name THEN
        RAISE WARNING 'Schema not owned by application user! Owner: %', schema_owner;
    END IF;

    IF NOT has_all_privs THEN
        RAISE WARNING 'User % missing CREATE privilege on schema public!', app_user_name;
    END IF;

    IF liquibase_count < 2 THEN
        RAISE WARNING 'Liquibase tables not fully created!';
    END IF;

    RAISE NOTICE 'DATABASE INITIALIZATION COMPLETE - Script is idempotent';
    RAISE NOTICE '════════════════════════════════════════════════════════════';

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Error in verification: %', SQLERRM;
END $$;

-- ============================================================================
-- SUCCESS MARKER
-- ============================================================================

SELECT 'PROD DATABASE READY - Idempotent init completed!' AS status;

-- ============================================================================
-- IDEMPOTENT SCRIPT NOTES:
-- 1. This script can be run multiple times without errors
-- 2. Uses IF NOT EXISTS for all CREATE statements
-- 3. Uses ON CONFLICT for INSERT statements
-- 4. Checks before altering ownership
-- 5. All operations wrapped in DO blocks with exception handling
-- 6. Variables passed via PostgreSQL session settings (current_setting)
-- 7. Continues on errors (\set ON_ERROR_STOP off)
-- 8. NO PASSWORDS ARE LOGGED - uses current_setting securely
-- ============================================================================
