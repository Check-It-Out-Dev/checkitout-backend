-- =============================================================================
-- LOCAL DATABASE INITIALIZATION SCRIPT
-- =============================================================================
-- This script is OPTIONAL - LocalDatabaseInitializer handles everything
-- If present, it will be executed after basic setup for additional config
-- 
-- Variables replaced by LocalDatabaseInitializer:
-- :app_user     -> from spring.datasource.username
-- :app_password -> from spring.datasource.password  
-- :app_database -> from spring.datasource.database
-- =============================================================================

-- Note: Database connection switch (\c) cannot be used in JDBC
-- This script assumes we're already connected to the app database

-- =============================================================================
-- EXTENSIONS (matching production setup)
-- =============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For text search

-- =============================================================================
-- ADDITIONAL PERMISSIONS (if needed)
-- =============================================================================
-- Grant usage on extensions
GRANT USAGE ON SCHEMA public TO :app_user;

-- Ensure user can create tables, sequences, etc
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO :app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO :app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO :app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TYPES TO :app_user;

-- =============================================================================
-- SEQUENCE CONFIGURATION (for Hibernate pooled-lo optimizer)
-- =============================================================================
-- Note: Sequences will be created by Liquibase with INCREMENT BY 50
-- This matches the Hibernate pooled-lo optimizer configuration

-- =============================================================================
-- DATABASE SETTINGS
-- =============================================================================
-- Note: ALTER DATABASE commands cannot be executed via JDBC within a transaction
-- These settings should be applied manually or via psql if needed:
-- ALTER DATABASE :app_database SET jit = 'on';
-- ALTER DATABASE :app_database SET log_statement = 'ddl';
-- ALTER DATABASE :app_database CONNECTION LIMIT -1;

-- Final verification
-- Database initialization completed successfully
-- Extensions created: uuid-ossp, pg_trgm
-- Permissions granted to application user
-- Ready for Liquibase migrations
