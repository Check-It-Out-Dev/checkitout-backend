# Local Development Database Setup

## Overview
This document explains the automated database setup for local development profiles (`dev` and `no-redis`).

## Architecture

### Dual Credential Pattern
Local development mirrors production architecture with two sets of credentials:

1. **Superuser (postgres)** - Used only for initial database/user creation
2. **Application User (checkitout_app_local)** - Used for runtime and migrations

### Components

#### 1. LocalDatabaseInitializer
- **Location**: `com.sm.instagram.platform.config.LocalDatabaseInitializer`
- **Profiles**: Active only for `dev` and `no-redis`
- **Purpose**: Creates database and user before Spring Boot connects
- **Features**:
  - Idempotent (safe to run multiple times)
  - Checks existing state before making changes
  - Provides clear status messages

#### 2. DualDataSourceConfiguration
- **Location**: `com.sm.instagram.platform.config.DualDataSourceConfiguration`
- **Purpose**: Manages dual DataSource beans
- **Beans**:
  - `liquibaseDataSource` - For schema migrations
  - `dataSource` (Primary) - For application runtime

## Configuration

### application-dev.yml / application-no-redis.yml
```yaml
# Superuser credentials (for initial setup)
postgres:
  superuser:
    username: postgres
    password: admin

# Application credentials (created automatically)
spring:
  datasource:
    database: checkitout_local_db
    username: checkitout_app_local
    password: local_dev_password
    
  liquibase:
    # Uses same credentials as datasource
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
```

### Control Flags
```yaml
local:
  db:
    init:
      enabled: true   # Enable/disable auto-initialization
      force: false    # Force recreation (WARNING: data loss!)
```

## Execution Flow

1. **Spring Boot Starts** → Profile `dev` or `no-redis` active
2. **LocalDatabaseInitializer** runs:
   - Connects as `postgres` superuser
   - Checks if database exists
   - Checks if user exists
   - Checks schema ownership
   - Creates/fixes only what's needed
   - Shows status: ✅ or creates missing components
3. **DualDataSourceConfiguration** creates DataSources:
   - `liquibaseDataSource` uses app user
   - Verifies user exists
4. **Liquibase** runs migrations:
   - Uses app user (owns schema)
   - Has all necessary permissions
5. **Application** starts:
   - Uses app user for all operations
   - Restricted permissions (security)

## Status Messages

### Everything OK
```
🚀 Starting local database initialization check...
✅ Database fully initialized - all checks passed:
   ✓ Database 'checkitout_local_db' exists
   ✓ User 'checkitout_app_local' exists
   ✓ User owns schema 'public'
   ✓ User has all required permissions
   ✓ Can connect with app credentials
```

### Initialization Needed
```
📋 Initialization needed. Current status:
   ✗ Database exists
   ✗ User exists
   ✓ Schema ownership correct
   ✓ User has permissions
   ✗ Can connect with app credentials
🔨 Performing database initialization...
   Creating user 'checkitout_app_local'...
   ✓ User created
   Creating database 'checkitout_local_db'...
   ✓ Database created
   Setting up schema ownership and permissions...
   ✓ Schema ownership and permissions configured
✅ Local database initialization completed successfully!
```

## Troubleshooting

### Force Recreation
If you need to completely recreate the local database:

1. Set in `application-dev.yml`:
```yaml
local:
  db:
    init:
      force: true
```

2. Run the application once
3. Set `force` back to `false`

### Manual Database Creation
If automatic initialization fails, create manually:

```sql
-- Connect as postgres superuser
CREATE USER checkitout_app_local WITH PASSWORD 'local_dev_password' CREATEDB;
CREATE DATABASE checkitout_local_db OWNER checkitout_app_local;

-- Connect to checkitout_local_db
\c checkitout_local_db

-- Set schema ownership
ALTER SCHEMA public OWNER TO checkitout_app_local;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE checkitout_local_db TO checkitout_app_local;
GRANT ALL ON SCHEMA public TO checkitout_app_local;
```

### Disable Auto-Initialization
To disable automatic database creation:

```yaml
local:
  db:
    init:
      enabled: false
```

## Benefits

1. **Zero Manual Setup** - Database and user created automatically
2. **Idempotent** - Safe to restart application multiple times
3. **Mirrors Production** - Same security model as prod/test
4. **Clear Feedback** - Shows exactly what's happening
5. **Consistent Migrations** - Single Liquibase changelog for all environments

## Requirements

- PostgreSQL installed locally
- Default superuser `postgres` with password `admin`
- Or configure custom superuser in `application-dev.yml`

## Security Notes

- Superuser credentials only used for initial setup
- Application runs with restricted user (principle of least privilege)
- Passwords in `application-dev.yml` are for local development only
- Never commit real passwords to version control
