package com.sm.instagram.platform.config;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.regex.Pattern;
import java.util.Properties;

/**
 * Local Database Initializer - Runs ONLY for dev/no-redis profiles
 * 
 * Ensures database and user are properly configured before Spring Boot
 * attempts to connect. This is idempotent - safe to run multiple times.
 * 
 * Execution order:
 * 1. Check if database exists
 * 2. Check if user exists  
 * 3. Check schema ownership
 * 4. Create/fix only what's needed
 * 5. Validate final state
 */
@Slf4j
@Configuration("localDatabaseInitializer")
@Profile({"dev", "no-redis"})
@Order(Ordered.HIGHEST_PRECEDENCE)  // Run before other configurations
public class LocalDatabaseInitializer {

    @Value("${spring.datasource.database:checkitout_local_db}")
    private String appDatabase;
    
    @Value("${spring.datasource.username:checkitout_app_local}")
    private String appUser;
    
    @Value("${spring.datasource.password:local_dev_password}")
    private String appPassword;
    
    @Value("${spring.datasource.host:localhost}")
    private String dbHost;
    
    @Value("${spring.datasource.port:5432}")
    private String dbPort;
    
    @Value("${postgres.superuser.username:postgres}")
    private String superUser;
    
    @Value("${postgres.superuser.password:admin}")
    private String superPassword;
    
    @Value("${local.db.init.enabled:true}")
    private boolean initEnabled;
    
    @Value("${local.db.init.force:false}")
    private boolean forceInit;  // For troubleshooting - forces recreation

    /**
     * A database, role or schema name that is safe to put into a statement.
     *
     * <p>PostgreSQL will not take an identifier as a bound parameter -- there is no
     * {@code DROP USER ?} and never will be -- so the eleven DDL statements below have to build
     * their identifiers by concatenation, and the only control left is refusing anything that is not
     * a plain identifier before it gets near a Statement. These names come from configuration rather
     * than from a request, and this component only exists under the dev and no-redis profiles, so
     * the realistic failure is a typo in a .env rather than an attack; a typo containing a semicolon
     * would still be executed, which is reason enough.
     *
     * <p>Deliberately narrower than PostgreSQL allows: unquoted identifiers there fold to lower case
     * and may contain {@code $}, but nothing in this project needs either, and a rule that permits
     * less is a rule that is easier to be sure about. 63 characters is Postgres's own NAMEDATALEN
     * limit.
     */
    private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

    /**
     * Fail the whole initialization rather than execute a statement built from something unexpected.
     * Throwing from {@code @PostConstruct} stops the context, which is the right outcome: a
     * misconfigured local database is not something to carry on with quietly.
     */
    private static String requireIdentifier(String value, String property) {
        if (value == null || !PLAIN_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalStateException(
                    property + " must be a plain SQL identifier (letters, digits and underscore, not "
                            + "starting with a digit); refusing to build a statement from: " + value);
        }
        return value;
    }

    @PostConstruct
    public void initializeDatabase() {
        // Before anything is concatenated into a statement. Every use of appDatabase and appUser
        // below this line has been through the pattern above.
        requireIdentifier(appDatabase, "spring.datasource.database");
        requireIdentifier(appUser, "spring.datasource.username");
        requireIdentifier(superUser, "postgres.superuser.username");

        if (!initEnabled) {
            log.info("🔧 Local DB initialization disabled via local.db.init.enabled=false");
            return;
        }
        
        log.info("🚀 Starting local database initialization check...");
        
        try {
            InitializationStatus status = checkInitializationStatus();
            
            if (status.isFullyInitialized() && !forceInit) {
                log.info("✅ Database fully initialized - all checks passed:");
                log.info("   ✓ Database '{}' exists", appDatabase);
                log.info("   ✓ User '{}' exists", appUser);
                log.info("   ✓ User owns schema 'public'");
                log.info("   ✓ User has all required permissions");
                log.info("   ✓ Can connect with app credentials");
                return;
            }
            
            if (forceInit) {
                log.warn("⚠️ Force initialization enabled - recreating database setup");
            } else {
                log.info("📋 Initialization needed. Current status:");
                status.logStatus();
            }
            
            performInitialization(status);
            
            // Verify everything is correct after initialization
            InitializationStatus finalStatus = checkInitializationStatus();
            if (!finalStatus.isFullyInitialized()) {
                throw new RuntimeException("Database initialization failed validation. Status: " + finalStatus);
            }
            
            log.info("✅ Local database initialization completed successfully!");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize local database", e);
            throw new RuntimeException("Local database initialization failed", e);
        }
    }
    
    private InitializationStatus checkInitializationStatus() throws SQLException {
        InitializationStatus status = new InitializationStatus();
        
        // Connect as superuser to check everything
        try (Connection conn = getSuperuserConnection()) {
            
            // 1. Check if database exists
            status.databaseExists = checkDatabaseExists(conn, appDatabase);
            
            // 2. Check if user exists
            status.userExists = checkUserExists(conn, appUser);
            
            if (status.databaseExists) {
                // Connect to app database to check schema ownership
                try (Connection appConn = getSuperuserConnection(appDatabase)) {
                    
                    // 3. Check schema ownership
                    status.schemaOwnership = checkSchemaOwnership(appConn, appUser);
                    
                    // 4. Check user permissions
                    status.hasPermissions = checkUserPermissions(appConn, appUser);
                }
            }
            
            // 5. Try to connect with app credentials
            if (status.databaseExists && status.userExists) {
                status.canConnect = checkAppConnection();
            }
        }
        
        return status;
    }
    
    private void performInitialization(InitializationStatus status) throws Exception {
        log.info("🔨 Performing database initialization...");
        
        try (Connection conn = getSuperuserConnection()) {
            
            // 1. Create user if needed
            if (!status.userExists || forceInit) {
                createUser(conn);
            }
            
            // 2. Create database if needed
            if (!status.databaseExists || forceInit) {
                createDatabase(conn);
            }
            
            // 3. Setup schema and permissions
            try (Connection appConn = getSuperuserConnection(appDatabase)) {
                setupSchemaAndPermissions(appConn);
            }
            
            // 4. Run initialization SQL if provided
            if (hasInitScript()) {
                runInitializationScript();
            }
        }
    }
    
    private boolean checkDatabaseExists(Connection conn, String dbName) throws SQLException {
        String sql = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dbName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private boolean checkUserExists(Connection conn, String userName) throws SQLException {
        String sql = "SELECT 1 FROM pg_user WHERE usename = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private boolean checkSchemaOwnership(Connection conn, String userName) throws SQLException {
        // First check if public schema exists at all
        String checkSchema = "SELECT 1 FROM pg_namespace WHERE nspname = 'public'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSchema)) {
            if (!rs.next()) {
                log.debug("Public schema does not exist yet");
                return false;  // Schema doesn't exist, so ownership is not set
            }
        }
        
        // Now check ownership
        String sql = "SELECT 1 FROM pg_namespace n " +
                    "JOIN pg_user u ON n.nspowner = u.usesysid " +
                    "WHERE n.nspname = 'public' AND u.usename = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private boolean checkUserPermissions(Connection conn, String userName) throws SQLException {
        // First check if public schema exists
        String checkSchema = "SELECT 1 FROM pg_namespace WHERE nspname = 'public'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSchema)) {
            if (!rs.next()) {
                log.debug("Public schema does not exist yet - cannot check permissions");
                return false;
            }
        }
        
        // Check if user has CREATE privilege on public schema
        String sql = "SELECT has_schema_privilege(?, 'public', 'CREATE')";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        }
        return false;
    }
    
    private boolean checkAppConnection() {
        try {
            String url = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, appDatabase);
            Properties props = new Properties();
            props.setProperty("user", appUser);
            props.setProperty("password", appPassword);
            props.setProperty("connectTimeout", "5");
            
            try (Connection conn = DriverManager.getConnection(url, props)) {
                return conn.isValid(1);
            }
        } catch (SQLException e) {
            log.debug("Cannot connect with app credentials: {}", e.getMessage());
            return false;
        }
    }
    
    private void createUser(Connection conn) throws SQLException {
        log.info("   Creating user '{}'...", appUser);
        
        // Drop user if force mode and exists
        if (forceInit) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP USER IF EXISTS " + appUser);
            }
        }
        
        String sql = String.format(
            "CREATE USER %s WITH PASSWORD '%s' CREATEDB",
            appUser, appPassword
        );
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("   ✓ User created");
        }
    }
    
    private void createDatabase(Connection conn) throws SQLException {
        log.info("   Creating database '{}'...", appDatabase);
        
        // Drop database if force mode and exists
        if (forceInit) {
            // Terminate existing connections first
            String terminateSql = String.format(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
                "WHERE datname = '%s' AND pid <> pg_backend_pid()",
                appDatabase
            );
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(terminateSql);
                stmt.execute("DROP DATABASE IF EXISTS " + appDatabase);
            }
        }
        
        String sql = String.format(
            "CREATE DATABASE %s OWNER %s ENCODING 'UTF8'",
            appDatabase, appUser
        );
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("   ✓ Database created");
        }
    }
    
    private void setupSchemaAndPermissions(Connection conn) throws SQLException {
        log.info("   Setting up schema ownership and permissions...");
        
        try (Statement stmt = conn.createStatement()) {
            // 0. Ensure public schema exists (mirrors production setup)
            stmt.execute("CREATE SCHEMA IF NOT EXISTS public");
            
            // 1. Transfer schema ownership
            stmt.execute("ALTER SCHEMA public OWNER TO " + appUser);
            
            // 2. Grant all privileges on database
            stmt.execute(String.format(
                "GRANT ALL PRIVILEGES ON DATABASE %s TO %s",
                appDatabase, appUser
            ));
            
            // 3. Grant all privileges on schema
            stmt.execute("GRANT ALL ON SCHEMA public TO " + appUser);
            
            // 4. Grant default privileges for future objects
            stmt.execute(String.format(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO %s",
                appUser
            ));
            stmt.execute(String.format(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO %s",
                appUser
            ));
            stmt.execute(String.format(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO %s",
                appUser
            ));
            
            log.info("   ✓ Schema ownership and permissions configured");
        }
    }
    
    private boolean hasInitScript() {
        try {
            ClassPathResource resource = new ClassPathResource("db/init/INIT_LOCAL_DATABASE.sql");
            return resource.exists();
        } catch (Exception e) {
            return false;
        }
    }
    
    private void runInitializationScript() throws Exception {
        log.info("   Running initialization script...");
        
        ClassPathResource resource = new ClassPathResource("db/init/INIT_LOCAL_DATABASE.sql");
        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        
        // Replace placeholders
        sql = sql.replace(":app_user", appUser)
                 .replace(":app_password", appPassword)
                 .replace(":app_database", appDatabase);
        
        try (Connection conn = getSuperuserConnection(appDatabase);
             Statement stmt = conn.createStatement()) {
            
            // Execute each statement separately (split by semicolon)
            String[] statements = sql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            
            log.info("   ✓ Initialization script executed");
        }
    }
    
    private Connection getSuperuserConnection() throws SQLException {
        return getSuperuserConnection("postgres");
    }
    
    private Connection getSuperuserConnection(String database) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, database);
        Properties props = new Properties();
        props.setProperty("user", superUser);
        props.setProperty("password", superPassword);
        props.setProperty("connectTimeout", "10");
        
        return DriverManager.getConnection(url, props);
    }
    
    /**
     * Inner class to track initialization status
     */
    private static class InitializationStatus {
        boolean databaseExists = false;
        boolean userExists = false;
        boolean schemaOwnership = false;
        boolean hasPermissions = false;
        boolean canConnect = false;
        
        boolean isFullyInitialized() {
            return databaseExists && userExists && schemaOwnership && 
                   hasPermissions && canConnect;
        }
        
        void logStatus() {
            log.info("   {} Database exists", statusIcon(databaseExists));
            log.info("   {} User exists", statusIcon(userExists));
            log.info("   {} Schema ownership correct", statusIcon(schemaOwnership));
            log.info("   {} User has permissions", statusIcon(hasPermissions));
            log.info("   {} Can connect with app credentials", statusIcon(canConnect));
        }
        
        private String statusIcon(boolean status) {
            return status ? "✓" : "✗";
        }
        
        @Override
        public String toString() {
            return String.format(
                "DB:%s, User:%s, Schema:%s, Perms:%s, Connect:%s",
                databaseExists, userExists, schemaOwnership, hasPermissions, canConnect
            );
        }
    }
}
