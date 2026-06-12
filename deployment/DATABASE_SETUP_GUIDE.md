# DATABASE SETUP - UNIFIED APPROACH

## 🎯 How It Works

### For Local/Dev/Test (Automatic)
1. **Liquibase runs FIRST as `postgres` (superuser)**
   - Creates `checkitout_app` user
   - Sets up all permissions
   - Creates sequences with correct increments
   
2. **Application then connects as `checkitout_app`**
   - Restricted user (not superuser)
   - Same as production setup
   - Prevents "works on my machine" issues

### For Production (Manual)
1. **Run `INIT_PRODUCTION_DATABASE.sql` manually**
   - As `avnadmin` in pgAdmin
   - Creates same user/permissions
   
2. **Application connects as `checkitout_app_prod`**
   - Same restricted permissions
   - Consistent with dev/test

## 📋 What Changed

### changelog.xml
- Added `000-database-setup.sql` as FIRST migration
- Only runs in `dev,test,local` contexts
- Skipped in production (context="prod")

### application.yml (local dev)
```yaml
datasource:
  username: checkitout_app     # App uses restricted user
  password: dev_password_123

liquibase:
  user: postgres               # Liquibase uses superuser
  password: admin
  contexts: dev,local
```

### application-test.yml
```yaml
datasource:
  username: checkitout_app     # App uses restricted user
  password: dev_password_123

liquibase:
  user: postgres               # Liquibase uses superuser
  password: ${POSTGRES_PASSWORD}
  contexts: test
```

### application-prod.yml
```yaml
datasource:
  username: checkitout_app_prod  # Different user for prod
  password: ${PROD_PASSWORD}

liquibase:
  contexts: prod                 # Skips setup script
  user: checkitout_app_prod      # Same as app user
```

## 🚀 Usage

### Local Development
```bash
# Just run - Liquibase creates user automatically
mvn spring-boot:run

# Database is created with:
# - User: checkitout_app
# - Password: dev_password_123
# - All permissions set
```

### Test Environment (Docker)
```bash
# Same automatic setup
docker-compose up
mvn spring-boot:run -Dspring.profiles.active=test
```

### Production
```bash
# 1. Run manual script first (as avnadmin)
# 2. Then deploy
mvn spring-boot:run -Dspring.profiles.active=prod
```

## ✅ Benefits

1. **Same permissions everywhere** - App never runs as superuser
2. **Automatic setup for devs** - Zero manual SQL
3. **Consistent behavior** - No environment-specific bugs
4. **Security** - Production uses different user/password
5. **Simple** - One approach for all environments

## 🔍 Troubleshooting

If user creation fails:
```sql
-- Check current user
SELECT current_user;

-- Check if user exists
SELECT * FROM pg_roles WHERE rolname = 'checkitout_app';

-- Reset if needed (as postgres)
DROP ROLE IF EXISTS checkitout_app CASCADE;
-- Then run app again
```

## 📝 Important Notes

- **First run**: Liquibase creates everything
- **Subsequent runs**: Setup script is skipped (already exists)
- **Clean slate**: Drop database to start fresh
- **Production**: Manual script gives you control

## 🗂️ File Structure
```
src/main/resources/
├── db/changelog/
│   ├── 000-database-setup.sql     # FIRST - Creates user (dev/test only)
│   ├── changelog.xml               # Main changelog
│   └── ... other migrations
├── application.yml                 # Local dev config
├── application-test.yml            # Test config
├── application-prod.yml            # Production config
└── application-base.yml            # Shared settings

deployment/
└── INIT_PRODUCTION_DATABASE.sql    # Manual script for production
```

That's it! Same approach everywhere, consistent behavior, no surprises.