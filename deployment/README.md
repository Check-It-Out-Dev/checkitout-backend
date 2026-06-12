# PRODUCTION DEPLOYMENT - SIMPLE GUIDE

## 📋 ONE Script, THREE Steps

### Step 1: Run Database Init
```sql
-- In pgAdmin, connected as 'avnadmin' to your OVH database:
-- Run: INIT_PRODUCTION_DATABASE.sql
-- Change the password in the script first!
```

### Step 2: Update Your Config
Your Spring profiles are already updated with gold standard configuration.
Just set the database password in environment variable or application-prod.yml

### Step 3: Deploy
```bash
# Test locally first
mvn spring-boot:run -Dspring.profiles.active=prod-standalone

# Then deploy to production
mvn spring-boot:run -Dspring.profiles.active=prod
```

## ✅ That's it!

## 🔍 If Something Goes Wrong

Check in pgAdmin:
```sql
-- Is user created?
SELECT * FROM pg_roles WHERE rolname = 'checkitout_app_prod';

-- Are sequences created with correct increment?
SELECT * FROM pg_sequences WHERE schemaname = 'public';

-- Is Liquibase unlocked?
UPDATE databasechangeloglock SET locked = FALSE WHERE id = 1;
```

## 📝 Remember

- All your entities must use `allocationSize = 50` (matches INCREMENT BY 50)
- Hibernate will only validate schema (never create/modify)
- Liquibase handles all table creation
- Same configuration in all environments = no environment-specific bugs

## 🗑️ Cleanup

You can delete these files from `deployment/config/` - they're all combined in INIT_PRODUCTION_DATABASE.sql:
- manual-grant-permissions.sql
- gold-standard-postgres-init.sql
- ovh-postgres-init-liquibase.sql
- fresh-database-init.sql
- pre-deployment-verification.sql
- quick-fix-common-issues.sql
- All the .md guides except this one

Keep only:
- INIT_PRODUCTION_DATABASE.sql (the ONE script)
- This README.md (simple guide)