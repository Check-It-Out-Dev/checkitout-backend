package com.sm.instagram.platform.secrets;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production secret service that handles secret retrieval with two strategies:
 * <p>
 * 1. PRODUCTION (default): Read from mounted volume (/app/config)
 * - Secrets populated by init container (Google Secret Manager)
 * - Init container writes unprefixed names (POSTGRES_PASSWORD not TEST_POSTGRES_PASSWORD)
 * <p>
 * 2. DEVELOPMENT: Local files and Spring properties
 * - Uses local configuration files and application properties
 */
@Slf4j
@Service
public class ProductionSecretService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> secretCache = new ConcurrentHashMap<>();
    @Autowired
    private Environment env;
    @Value("${secrets.mount.path:/app/config}")
    private String secretsMountPath;
    @Value("${secrets.development.mode:false}")
    private boolean developmentMode;
    @Value("${secrets.cache.enabled:true}")
    private boolean cacheEnabled;
    @Value("${spring.profiles.active:}")
    private String activeProfile;
    // Local development properties
    @Value("${spring.datasource.username:}")
    private String localDbUsername;
    @Value("${spring.datasource.password:}")
    private String localDbPassword;
    @Value("${firebase.config.path:}")
    private String localFirebaseConfigPath;
    @Value("${instagram.client.secret:}")
    private String localInstagramSecret;
    @Value("${jwt.secret:}")
    private String localJwtSecret;
    @Value("${cookie.hmac.secret:}")
    private String localCookieHmacSecret;
    @Value("${consent.hmac-secret:}")
    private String localConsentHmacSecret;

    /**
     * Get secret using appropriate strategy based on environment
     */
    public String getSecret(String secretName) {
        // Check cache first
        if (cacheEnabled) {
            String cached = secretCache.get(secretName);
            if (cached != null) {
                log.trace("Returning cached secret '{}'", secretName);
                return cached;
            }
        }

        String value;

        if (developmentMode) {
            // Development: Use local files and properties
            value = getFromLocalFiles(secretName);
        } else {
            // Production/Test: Read from mounted volume
            value = readFromMountedSecret(secretName);
            if (value != null) {
                log.debug("Secret '{}' loaded from mounted volume", secretName);
            } else {
                log.error("Secret '{}' not found in mounted volume at {}", secretName, secretsMountPath);
            }
        }

        if (value != null && cacheEnabled) {
            secretCache.put(secretName, value);
        }

        return value;
    }

    /**
     * Get database password - convenience method
     */
    public String getDatabasePassword() {
        String password = getSecret("POSTGRES_PASSWORD");
        if (password == null) {
            password = getSecret("DATABASE_PASSWORD");
        }
        if (password == null) {
            password = getSecret("APP_DB_PASSWORD");
        }
        return password;
    }

    /**
     * Get database username - convenience method
     */
    public String getDatabaseUsername() {
        String username = getSecret("POSTGRES_USER");
        if (username == null) {
            username = getSecret("DATABASE_USER");
        }
        if (username == null) {
            username = getSecret("APP_DB_USER");
        }
        return username;
    }

    /**
     * Get database name - convenience method
     */
    public String getDatabaseName() {
        String dbName = getSecret("POSTGRES_DB");
        if (dbName == null) {
            dbName = getSecret("DATABASE_NAME");
        }
        if (dbName == null) {
            dbName = getSecret("DB_NAME");
        }
        return dbName;
    }

    /**
     * Get Firebase service account JSON - convenience method
     */
    public String getFirebaseServiceAccountJson() {
        return getSecret("FIREBASE_SERVICE_ACCOUNT_JSON");
    }

    /**
     * Get Instagram client secret - convenience method
     */
    public String getInstagramClientSecret() {
        return getSecret("INSTAGRAM_CLIENT_SECRET");
    }

    /**
     * Get JWT secret - convenience method
     */
    public String getJwtSecret() {
        return getSecret("JWT_SECRET");
    }

    /**
     * Get Cookie HMAC secret - convenience method
     */
    public String getCookieHmacSecret() {
        return getSecret("COOKIE_HMAC_SECRET");
    }

    /**
     * Get Consent HMAC secret - convenience method (separate from session cookie HMAC)
     */
    public String getConsentHmacSecret() {
        return getSecret("CONSENT_HMAC_SECRET");
    }

    /**
     * Development strategy: Use local files and Spring properties
     */
    private String getFromLocalFiles(String secretName) {
        log.debug("Getting secret '{}' from local configuration", secretName);

        // Map unprefixed names to local properties
        switch (secretName) {
            case "POSTGRES_USER":
            case "DATABASE_USER":
            case "APP_DB_USER":
                return localDbUsername;

            case "POSTGRES_PASSWORD":
            case "DATABASE_PASSWORD":
            case "APP_DB_PASSWORD":
                return localDbPassword;

            case "POSTGRES_DB":
            case "DATABASE_NAME":
            case "DB_NAME":
                // Extract from JDBC URL if needed
                String url = env.getProperty("spring.datasource.url", "");
                int lastSlash = url.lastIndexOf('/');
                if (lastSlash > 0) {
                    String dbName = url.substring(lastSlash + 1);
                    // Remove any query parameters
                    int questionMark = dbName.indexOf('?');
                    if (questionMark > 0) {
                        dbName = dbName.substring(0, questionMark);
                    }
                    return dbName;
                }
                return "checkitout_local_db";

            case "FIREBASE_SERVICE_ACCOUNT_JSON":
                return readLocalFirebaseConfig();

            case "INSTAGRAM_CLIENT_SECRET":
                return localInstagramSecret;

            case "JWT_SECRET":
                return localJwtSecret;

            case "COOKIE_HMAC_SECRET":
                return localCookieHmacSecret;

            case "CONSENT_HMAC_SECRET":
                return localConsentHmacSecret;

            default:
                // For unknown secrets in dev, try environment variables
                String envValue = env.getProperty(secretName);
                if (envValue != null) {
                    return envValue;
                }

                // As last resort, try to read from local secrets directory
                String localSecretsPath = env.getProperty("secrets.local.path", "src/main/resources/secrets");
                return readFromPath(localSecretsPath, secretName);
        }
    }

    /**
     * Read Firebase config from local file path
     */
    private String readLocalFirebaseConfig() {
        if (localFirebaseConfigPath == null || localFirebaseConfigPath.isEmpty()) {
            log.warn("Firebase config path not configured for local development");
            return null;
        }

        try {
            Path configPath = Paths.get(localFirebaseConfigPath);
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                log.debug("Successfully read Firebase config from: {}", localFirebaseConfigPath);
                return content;
            } else {
                log.error("Firebase config file not found at: {}", localFirebaseConfigPath);
            }
        } catch (Exception e) {
            log.error("Error reading Firebase config from {}: {}", localFirebaseConfigPath, e.getMessage());
        }

        return null;
    }

    /**
     * Read secret from mounted volume (populated by init container)
     */
    private String readFromMountedSecret(String secretName) {
        try {
            // Format 1: Individual files (e.g., /app/config/POSTGRES_PASSWORD)
            Path directPath = Paths.get(secretsMountPath, secretName);
            if (Files.exists(directPath)) {
                return Files.readString(directPath).trim();
            }

            // Format 2: JSON file with all secrets (e.g., /app/config/secrets.json)
            Path jsonPath = Paths.get(secretsMountPath, "secrets.json");
            if (Files.exists(jsonPath)) {
                String json = Files.readString(jsonPath);
                Map<String, Object> secrets = objectMapper.readValue(json, Map.class);
                Object value = secrets.get(secretName);
                if (value != null) {
                    return value.toString();
                }
            }

            // Format 3: Property file format (e.g., /app/config/secrets.properties)
            Path propsPath = Paths.get(secretsMountPath, "secrets.properties");
            if (Files.exists(propsPath)) {
                return Files.lines(propsPath)
                        .filter(line -> line.startsWith(secretName + "="))
                        .findFirst()
                        .map(line -> line.substring(secretName.length() + 1))
                        .orElse(null);
            }

            // Format 4: Environment file format (e.g., /app/config/secrets.env)
            Path envPath = Paths.get(secretsMountPath, "secrets.env");
            if (Files.exists(envPath)) {
                log.trace("Reading secret '{}' from secrets.env file", secretName);
                return Files.lines(envPath)
                        .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                        .filter(line -> line.startsWith(secretName + "="))
                        .findFirst()
                        .map(line -> {
                            String value = line.substring(secretName.length() + 1);
                            // Remove quotes if present (common in .env files)
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                    (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            return value;
                        })
                        .orElse(null);
            }

        } catch (Exception e) {
            log.debug("Error reading mounted secret '{}': {}", secretName, e.getMessage());
        }

        return null;
    }

    /**
     * Generic path reader for local development
     */
    private String readFromPath(String basePath, String secretName) {
        try {
            Path secretPath = Paths.get(basePath, secretName);
            if (Files.exists(secretPath)) {
                return Files.readString(secretPath).trim();
            }
        } catch (Exception e) {
            log.debug("Could not read secret '{}' from path '{}': {}",
                    secretName, basePath, e.getMessage());
        }
        return null;
    }

    /**
     * Clear cache
     */
    public void clearCache() {
        secretCache.clear();
        log.info("Secret cache cleared");
    }

    /**
     * Get detailed status for debugging
     */
    public SecretSourceStatus getSecretStatus(String secretName) {
        SecretSourceStatus status = new SecretSourceStatus();
        status.secretName = secretName;
        status.developmentMode = developmentMode;

        if (developmentMode) {
            status.strategy = "LOCAL_FILES";
            status.activeSource = "Local configuration";
            return status;
        }

        status.strategy = "MOUNTED_VOLUME";

        // Check mounted source
        try {
            String value = readFromMountedSecret(secretName);
            status.mountedAvailable = (value != null);
            status.mountedPath = secretsMountPath + "/" + secretName;
        } catch (Exception e) {
            status.mountedAvailable = false;
            status.mountedError = e.getMessage();
        }

        status.activeSource = status.mountedAvailable ? "MOUNTED" : "NONE";

        return status;
    }

    /**
     * Status object for debugging
     */
    public static class SecretSourceStatus {
        public String secretName;
        public String strategy;
        public boolean developmentMode;
        public boolean mountedAvailable;
        public String mountedPath;
        public String mountedError;
        public String activeSource;
    }
}