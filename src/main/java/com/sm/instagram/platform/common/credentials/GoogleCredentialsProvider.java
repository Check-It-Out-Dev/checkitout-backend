package com.sm.instagram.platform.common.credentials;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Centralized provider for Google/Firebase credentials.
 * Eliminates duplicate credential loading logic across multiple configuration classes.
 * 
 * This component:
 * - Loads credentials once during startup and caches them
 * - Provides a single source of truth for project ID
 * - Handles all base64 decoding and validation logic
 * - Reduces ~400 lines of duplicate code across 3 configuration classes
 * 
 * @author CheckItOut Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class GoogleCredentialsProvider {
    
    @Value("${firebase.service.account.json.base64:}")
    private String serviceAccountJsonBase64;
    
    @Value("classpath:service-account.json")
    private Resource serviceAccountFile;
    
    @Value("${firebase.project.id:}")
    private String configuredProjectId;
    
    @Value("${gcp.project-id:${firebase.project.id:check-it-out-47c50}}")
    private String gcpProjectId;
    
    @Value("${app.environment:UNKNOWN}")
    private String appEnvironment;
    
    /**
     * Cached Google credentials - loaded once during initialization
     */
    @Getter
    private GoogleCredentials cachedCredentials;
    
    /**
     * Cached project ID extracted from credentials
     */
    @Getter
    private String projectId;
    
    /**
     * Service account email if using service account credentials
     */
    @Getter
    private String serviceAccountEmail;
    
    /**
     * Indicates whether production credentials are being used
     */
    @Getter
    private boolean usingProductionCredentials;
    
    /**
     * Initialize and cache credentials on startup
     */
    @PostConstruct
    public void init() {
        boolean isProduction = isProductionEnvironment();
        
        if (!isProduction) {
            log.info("==========================================");
            log.info("🔐 GOOGLE CREDENTIALS PROVIDER INITIALIZATION");
            log.info("==========================================");
            log.info("Environment: {}", appEnvironment);
            log.info("Configured Project ID: {}", 
                StringUtils.hasText(configuredProjectId) ? configuredProjectId : "NOT CONFIGURED");
        }
        
        try {
            // Load and cache credentials
            this.cachedCredentials = loadCredentials();
            
            // Extract and cache project ID
            this.projectId = extractProjectId();
            
            // Extract service account email if applicable
            if (cachedCredentials instanceof ServiceAccountCredentials) {
                ServiceAccountCredentials saCredentials = (ServiceAccountCredentials) cachedCredentials;
                this.serviceAccountEmail = saCredentials.getClientEmail();
                log.info("Service Account: {}", serviceAccountEmail);
            }
            
            // Determine if using production credentials
            this.usingProductionCredentials = isProductionProject(projectId);
            
            // Log credential summary
            logCredentialSummary();
            
        } catch (Exception e) {
            log.error("❌ FAILED to initialize Google credentials!", e);
            throw new IllegalStateException("Could not initialize Google credentials provider", e);
        }
        
        if (!isProduction) {
            log.info("==========================================");
        }
    }
    
    /**
     * Get Google credentials with scopes.
     * 
     * @param scopes OAuth2 scopes required
     * @return GoogleCredentials with requested scopes
     */
    public GoogleCredentials getCredentialsWithScopes(String... scopes) {
        if (scopes == null || scopes.length == 0) {
            return cachedCredentials;
        }
        
        try {
            // Create new credentials with requested scopes
            return cachedCredentials.createScoped(scopes);
        } catch (Exception e) {
            log.warn("Could not create scoped credentials, returning base credentials", e);
            return cachedCredentials;
        }
    }
    
    /**
     * Load Google credentials using priority:
     * 1. Base64-encoded service account JSON from property
     * 2. Service account JSON file from classpath
     * 3. Application Default Credentials (for GCP environments)
     */
    private GoogleCredentials loadCredentials() throws IOException {
        boolean isProduction = isProductionEnvironment();
        
        if (!isProduction) {
            log.info("🔍 Loading Google credentials...");
        } else {
            log.debug("Loading Google credentials...");
        }
        
        // Priority 1: Check base64 property first
        if (StringUtils.hasText(serviceAccountJsonBase64)) {
            log.info("📄 Attempting to use PROPERTY credentials");
            
            try {
                GoogleCredentials credentials = loadFromBase64Property(serviceAccountJsonBase64);
                log.info("✅ SUCCESS: Loaded credentials from PROPERTY");
                return credentials;
            } catch (Exception e) {
                log.warn("⚠️ Failed to load from property, will try fallback: {}", e.getMessage());
            }
        }
        
        // Priority 2: Fall back to file
        if (serviceAccountFile != null && serviceAccountFile.exists()) {
            log.info("📁 Attempting to use FILE credentials");
            
            try (InputStream credentialsStream = serviceAccountFile.getInputStream()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
                log.info("✅ SUCCESS: Loaded credentials from FILE");
                return credentials;
            } catch (IOException e) {
                log.error("❌ Failed to load from file: {}", e.getMessage());
                throw new IOException("Could not load credentials from service-account.json", e);
            }
        }
        
        // Priority 3: Try Application Default Credentials
        log.info("☁️ Attempting to use Application Default Credentials");
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            log.info("✅ SUCCESS: Using Application Default Credentials");
            return credentials;
        } catch (IOException e) {
            // Priority 4 (NON-PRODUCTION only): synthetic offline credentials.
            // A fresh clone has no .env, no service-account.json and no ADC —
            // without this fallback the whole test context dies on bean
            // creation. Token signing works offline with any valid RSA key;
            // every real network call fails loudly, which is exactly right
            // for tests that mock Firebase. Production still fails hard.
            if (!isProductionEnvironment()) {
                log.warn("⚠️ No Google credentials found — using SYNTHETIC offline "
                        + "credentials (non-production only). Firebase network calls "
                        + "will fail; tests mock them.");
                return buildSyntheticCredentials();
            }
            throw new IOException(
                "Could not load Google credentials. Please provide either:\n" +
                "1. firebase.service.account.json.base64 property, or\n" +
                "2. service-account.json file in src/main/resources/, or\n" +
                "3. Application Default Credentials", e);
        }
    }

    /**
     * Build clearly-synthetic service-account credentials with a freshly
     * generated in-memory RSA key. Never written to disk, never valid
     * against any Google API — offline signing only.
     */
    private GoogleCredentials buildSyntheticCredentials() {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair keyPair = generator.generateKeyPair();
            return ServiceAccountCredentials.newBuilder()
                    .setClientId("synthetic-offline-client")
                    .setClientEmail("synthetic-tests@check-it-out-47c50.iam.gserviceaccount.com")
                    .setPrivateKey(keyPair.getPrivate())
                    .setPrivateKeyId("synthetic-offline-key")
                    .setProjectId("check-it-out-47c50")
                    .build();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA unavailable for synthetic credentials", e);
        }
    }
    
    /**
     * Load credentials from base64-encoded property
     */
    private GoogleCredentials loadFromBase64Property(String base64OrJson) throws IOException {
        String jsonContent = base64OrJson;
        
        // Check if it's base64 encoded and decode if necessary
        if (isBase64(base64OrJson)) {
            log.debug("Decoding base64 credentials");
            byte[] decodedBytes = Base64.getDecoder().decode(base64OrJson);
            jsonContent = new String(decodedBytes, StandardCharsets.UTF_8);
        } else if (!base64OrJson.trim().startsWith("{")) {
            // Try with auto-padding for improperly padded base64
            log.debug("Attempting auto-padding for base64");
            jsonContent = decodeWithAutoPadding(base64OrJson);
        }
        
        // Validate JSON structure
        if (!jsonContent.contains("project_id") || !jsonContent.contains("private_key")) {
            throw new IllegalArgumentException("Invalid service account JSON structure");
        }
        
        try (InputStream credentialsStream = new ByteArrayInputStream(
                jsonContent.getBytes(StandardCharsets.UTF_8))) {
            return GoogleCredentials.fromStream(credentialsStream);
        }
    }
    
    /**
     * Decode base64 with automatic padding correction
     */
    private String decodeWithAutoPadding(String base64) throws IOException {
        String paddedBase64 = base64;
        int paddingNeeded = 4 - (base64.length() % 4);
        if (paddingNeeded < 4) {
            paddedBase64 = base64 + "=".repeat(paddingNeeded);
            log.debug("Added {} padding characters", paddingNeeded);
        }
        
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(paddedBase64);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid base64 encoding even with padding", e);
        }
    }
    
    /**
     * Extract project ID from loaded credentials or configuration
     */
    private String extractProjectId() {
        // First try to extract from credentials if they're service account credentials
        if (cachedCredentials instanceof ServiceAccountCredentials) {
            ServiceAccountCredentials saCredentials = (ServiceAccountCredentials) cachedCredentials;
            String credentialProjectId = saCredentials.getProjectId();
            if (StringUtils.hasText(credentialProjectId)) {
                log.debug("Extracted project ID from credentials: {}", credentialProjectId);
                return credentialProjectId;
            }
        }
        
        // Try to extract from the JSON content
        String extractedId = extractProjectIdFromJson();
        if (StringUtils.hasText(extractedId)) {
            log.debug("Extracted project ID from JSON: {}", extractedId);
            return extractedId;
        }
        
        // Fall back to configured project ID
        if (StringUtils.hasText(gcpProjectId)) {
            log.debug("Using configured GCP project ID: {}", gcpProjectId);
            return gcpProjectId;
        }
        
        if (StringUtils.hasText(configuredProjectId)) {
            log.debug("Using configured Firebase project ID: {}", configuredProjectId);
            return configuredProjectId;
        }
        
        // Default fallback
        log.warn("Could not determine project ID, using default: check-it-out-47c50");
        return "check-it-out-47c50";
    }
    
    /**
     * Extract project ID from JSON content
     */
    private String extractProjectIdFromJson() {
        try {
            String jsonContent = null;
            
            // Get JSON content from property or file
            if (StringUtils.hasText(serviceAccountJsonBase64)) {
                jsonContent = isBase64(serviceAccountJsonBase64) 
                    ? new String(Base64.getDecoder().decode(serviceAccountJsonBase64), StandardCharsets.UTF_8)
                    : serviceAccountJsonBase64;
            } else if (serviceAccountFile != null && serviceAccountFile.exists()) {
                try (InputStream is = serviceAccountFile.getInputStream()) {
                    jsonContent = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                }
            }
            
            // Extract project_id from JSON
            if (StringUtils.hasText(jsonContent) && jsonContent.contains("project_id")) {
                int start = jsonContent.indexOf("\"project_id\"");
                if (start != -1) {
                    start = jsonContent.indexOf(":", start) + 1;
                    start = jsonContent.indexOf("\"", start) + 1;
                    int end = jsonContent.indexOf("\"", start);
                    if (end > start) {
                        return jsonContent.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract project ID from JSON: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if a string is base64 encoded
     */
    private boolean isBase64(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        // Quick check: if it starts with '{' it's likely plain JSON
        if (str.trim().startsWith("{")) {
            return false;
        }
        
        // Must be at least 4 characters for meaningful base64
        if (str.length() < 4) {
            return false;
        }
        
        // Check if it matches base64 pattern
        if (!str.matches("^[A-Za-z0-9+/]*={0,2}$")) {
            return false;
        }
        
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            // For reasonably long strings, try with auto-padding
            if (str.length() > 100) {
                try {
                    int paddingNeeded = 4 - (str.length() % 4);
                    if (paddingNeeded < 4) {
                        String paddedStr = str + "=".repeat(paddingNeeded);
                        Base64.getDecoder().decode(paddedStr);
                        return true;
                    }
                } catch (IllegalArgumentException ex) {
                    return false;
                }
            }
            return false;
        }
    }
    
    /**
     * Check if the project ID is a production project
     */
    private boolean isProductionProject(String projectId) {
        return projectId != null && projectId.equals("check-it-out-prod");
    }
    
    /**
     * Log credential summary for debugging
     */
    private void logCredentialSummary() {
        boolean isProduction = isProductionEnvironment();
        
        if (!isProduction) {
            log.info("\n📊 CREDENTIAL SUMMARY:");
            log.info("   Project ID: {}", projectId);
            log.info("   Instance Type: {}", getInstanceType());
            log.info("   Service Account: {}", maskEmail(serviceAccountEmail));
            log.info("   Is Production: {}", usingProductionCredentials);
            log.info("   Environment: {}", appEnvironment);
        } else {
            // In production, log minimal info at INFO level
            log.info("Credentials loaded successfully for project: {}", projectId);
            // Detailed info only at DEBUG level
            log.debug("Service Account: {}", maskEmail(serviceAccountEmail));
        }
        
        // Warn about mismatches
        boolean isProductionEnv = "PRODUCTION".equals(appEnvironment) || "PRODUCTION-STANDALONE".equals(appEnvironment);
        if (isProductionEnv && !usingProductionCredentials) {
            log.error("❌ CRITICAL: Production environment but NOT using production credentials!");
        } else if (!isProductionEnv && usingProductionCredentials) {
            log.warn("⚠️ WARNING: Non-production environment using PRODUCTION credentials!");
        } else {
            log.info("✅ Environment and credentials match");
        }
    }
    
    /**
     * Get human-readable instance type
     */
    public String getInstanceType() {
        if (projectId == null) {
            return "🔵 UNKNOWN";
        }
        
        switch (projectId) {
            case "check-it-out-prod":
                return "🔴 PRODUCTION";
            case "check-it-out-47c50":
                return "🟢 TEST";
            default:
                return "🔵 CUSTOM (" + projectId + ")";
        }
    }
    
    /**
     * Verify credentials are valid and can be used
     */
    public boolean verifyCredentials() {
        try {
            if (cachedCredentials == null) {
                log.error("No credentials loaded");
                return false;
            }
            
            // Try to refresh the credentials as a test
            cachedCredentials.refresh();
            log.debug("Credentials verification successful");
            return true;
        } catch (Exception e) {
            log.error("Credentials verification failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if running in production environment
     */
    private boolean isProductionEnvironment() {
        return "PRODUCTION".equals(appEnvironment) || 
               "PRODUCTION-STANDALONE".equals(appEnvironment) ||
               "PROD".equals(appEnvironment);
    }
    
    /**
     * Mask email address for security logging
     * Example: test@example.com -> t***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "N/A";
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email; // Can't mask properly
        }
        
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        // Show first character and mask the rest
        String maskedLocal = localPart.charAt(0) + "***";
        return maskedLocal + domain;
    }
    
    /**
     * Sanitize log output to prevent injection attacks
     */
    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        // Remove line breaks and control characters
        return input.replaceAll("[\\r\\n\\t]", " ")
                   .replaceAll("[\\x00-\\x1F\\x7F]", "")
                   .trim();
    }
}
