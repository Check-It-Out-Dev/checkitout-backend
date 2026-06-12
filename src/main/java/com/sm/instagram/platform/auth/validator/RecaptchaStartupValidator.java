package com.sm.instagram.platform.auth.validator;

import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceSettings;
import com.google.recaptchaenterprise.v1.*;
import com.sm.instagram.platform.config.RecaptchaConfig;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup validator for reCAPTCHA Enterprise integration.
 * Performs comprehensive validation on application startup to ensure
 * reCAPTCHA is properly configured and operational.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // Run early in startup sequence
public class RecaptchaStartupValidator {
    
    private final RecaptchaConfig recaptchaConfig;
    private final GoogleCredentialsProvider credentialsProvider;
    
    private static final String TEST_TOKEN = "STARTUP_VALIDATION_TOKEN";
    private static final String TEST_ACTION = "STARTUP_TEST";
    
    /**
     * Validate reCAPTCHA configuration and connectivity after application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        if (!recaptchaConfig.isEnabled()) {
            log.info("🔒 reCAPTCHA is DISABLED - skipping startup validation");
            return;
        }
        
        log.info("==========================================");
        log.info("🤖 RECAPTCHA ENTERPRISE STARTUP VALIDATION");
        log.info("==========================================");
        
        List<ValidationResult> results = new ArrayList<>();
        
        // 1. Validate Configuration
        results.add(validateConfiguration());
        
        // 2. Validate Credentials
        results.add(validateCredentials());
        
        // 3. Validate Project Access
        results.add(validateProjectAccess());
        
        // 4. Validate Site Key Format
        results.add(validateSiteKey());
        
        // 5. Validate Thresholds
        results.add(validateThresholds());
        
        // 6. Test API Connectivity (without real token)
        results.add(testApiConnectivity());
        
        // Print Summary
        printValidationSummary(results);
        
        log.info("==========================================");
    }
    
    /**
     * Validate basic configuration
     */
    private ValidationResult validateConfiguration() {
        String name = "Configuration";
        try {
            if (recaptchaConfig.getProjectId() == null || recaptchaConfig.getProjectId().isEmpty()) {
                return ValidationResult.failure(name, "Project ID is not configured");
            }
            
            if (recaptchaConfig.getSiteKey() == null || recaptchaConfig.getSiteKey().isEmpty()) {
                return ValidationResult.failure(name, "Site key is not configured");
            }
            
            return ValidationResult.success(name, 
                String.format("Project: %s", recaptchaConfig.getProjectId()));
                
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Validate Google credentials are loaded
     */
    private ValidationResult validateCredentials() {
        String name = "Credentials";
        try {
            if (credentialsProvider.getCachedCredentials() == null) {
                return ValidationResult.failure(name, "No Google credentials available");
            }
            
            String serviceAccount = credentialsProvider.getServiceAccountEmail();
            String maskedAccount = maskEmail(serviceAccount);
            
            return ValidationResult.success(name, 
                String.format("Service Account: %s", maskedAccount));
                
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Validate project ID consistency
     */
    private ValidationResult validateProjectAccess() {
        String name = "Project Access";
        try {
            String configProjectId = recaptchaConfig.getProjectId();
            String credentialProjectId = credentialsProvider.getProjectId();
            
            if (!configProjectId.equals(credentialProjectId)) {
                log.warn("⚠️ Project ID mismatch - Config: {}, Credentials: {}", 
                    configProjectId, credentialProjectId);
            }
            
            String instanceType = credentialsProvider.getInstanceType();
            return ValidationResult.success(name, 
                String.format("Instance: %s", instanceType));
                
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Validate site key format
     */
    private ValidationResult validateSiteKey() {
        String name = "Site Key";
        try {
            String siteKey = recaptchaConfig.getSiteKey();
            
            // Basic validation - should be 40 characters
            if (siteKey.length() != 40) {
                return ValidationResult.warning(name, 
                    String.format("Unusual site key length: %d (expected 40)", siteKey.length()));
            }
            
            // Show first 8 chars for verification
            String preview = siteKey.substring(0, 8) + "...";
            return ValidationResult.success(name, 
                String.format("Key: %s (40 chars)", preview));
                
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Validate score thresholds
     */
    private ValidationResult validateThresholds() {
        String name = "Thresholds";
        try {
            Double loginThreshold = recaptchaConfig.getLoginThreshold();
            Double signupThreshold = recaptchaConfig.getSignupThreshold();
            Double forgotThreshold = recaptchaConfig.getForgotPasswordThreshold();
            
            // Validate ranges
            if (loginThreshold < 0 || loginThreshold > 1) {
                return ValidationResult.failure(name, "Login threshold out of range [0-1]");
            }
            
            if (signupThreshold < 0 || signupThreshold > 1) {
                return ValidationResult.failure(name, "Signup threshold out of range [0-1]");
            }
            
            if (forgotThreshold < 0 || forgotThreshold > 1) {
                return ValidationResult.failure(name, "Forgot password threshold out of range [0-1]");
            }
            
            return ValidationResult.success(name, 
                String.format("Login: %.1f | Signup: %.1f | Forgot: %.1f", 
                    loginThreshold, signupThreshold, forgotThreshold));
                    
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Test API connectivity by attempting to create an assessment with invalid token
     * This will fail but proves the API is reachable
     */
    private ValidationResult testApiConnectivity() {
        String name = "API Connectivity";
        RecaptchaEnterpriseServiceClient testClient = null;
        
        try {
            // Create a test client
            RecaptchaEnterpriseServiceSettings settings = RecaptchaEnterpriseServiceSettings
                .newBuilder()
                .setCredentialsProvider(() -> credentialsProvider.getCredentialsWithScopes(
                    "https://www.googleapis.com/auth/cloud-platform"
                ))
                .build();
            
            testClient = RecaptchaEnterpriseServiceClient.create(settings);
            
            // Try to create assessment with test token (will fail but proves connectivity)
            Event event = Event.newBuilder()
                .setSiteKey(recaptchaConfig.getSiteKey())
                .setToken(TEST_TOKEN)
                .setExpectedAction(TEST_ACTION)
                .build();
            
            Assessment assessment = Assessment.newBuilder()
                .setEvent(event)
                .build();
            
            ProjectName projectName = ProjectName.of(recaptchaConfig.getProjectId());
            
            // Create the assessment - API will accept the request but mark token as invalid
            Assessment response = testClient.createAssessment(projectName, assessment);
            
            // Check the response to see if the token was marked as invalid (expected)
            if (response != null && response.hasTokenProperties()) {
                boolean isValid = response.getTokenProperties().getValid();
                
                if (!isValid) {
                    // This is the expected result - API is reachable and working correctly
                    String reason = response.getTokenProperties().getInvalidReason() != null ?
                        response.getTokenProperties().getInvalidReason().name() : "INVALID_TOKEN";
                    
                    log.debug("API connectivity test successful - Token correctly marked as invalid: {}", reason);
                    return ValidationResult.success(name, "API reachable ✓ (Token validation working)");
                } else {
                    // This shouldn't happen with our test token
                    log.warn("Test token was unexpectedly marked as valid");
                    return ValidationResult.warning(name, "API reachable but test token accepted (unexpected)");
                }
            } else {
                // Got a response but no token properties
                return ValidationResult.warning(name, "API reachable but no validation response");
            }
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            
            // Expected errors that also indicate API is reachable
            if (errorMessage != null && (errorMessage.contains("INVALID_ARGUMENT") || 
                errorMessage.contains("invalid token") ||
                errorMessage.contains("Token is missing") ||
                errorMessage.contains("MALFORMED"))) {
                return ValidationResult.success(name, "API reachable ✓ (Request rejected as expected)");
            }
            
            // Authentication/permission errors
            if (errorMessage != null && (errorMessage.contains("PERMISSION_DENIED") || 
                errorMessage.contains("UNAUTHENTICATED"))) {
                return ValidationResult.failure(name, "Authentication failed - check credentials");
            }
            
            // Network/connectivity errors
            if (errorMessage != null && (errorMessage.contains("UNAVAILABLE") || 
                errorMessage.contains("DEADLINE_EXCEEDED") ||
                errorMessage.contains("Connection refused"))) {
                return ValidationResult.failure(name, "API unreachable - check network/firewall");
            }
            
            // Other errors - provide more context
            String shortError = errorMessage != null && errorMessage.length() > 100 ? 
                errorMessage.substring(0, 100) + "..." : errorMessage;
            return ValidationResult.warning(name, "Unexpected response: " + shortError);
            
        } finally {
            if (testClient != null) {
                try {
                    testClient.close();
                } catch (Exception e) {
                    log.debug("Error closing test client: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Print validation summary with emojis
     */
    private void printValidationSummary(List<ValidationResult> results) {
        log.info("");
        log.info("📊 VALIDATION SUMMARY:");
        log.info("------------------------------------------");
        
        int successCount = 0;
        int warningCount = 0;
        int failureCount = 0;
        
        for (ValidationResult result : results) {
            String emoji = result.success ? "✅" : (result.warning ? "⚠️" : "❌");
            String status = result.success ? "PASS" : (result.warning ? "WARN" : "FAIL");
            
            log.info("   {} {} - {}", emoji, result.name, status);
            if (result.details != null && !result.details.isEmpty()) {
                log.info("      └─ {}", result.details);
            }
            
            if (result.success) successCount++;
            else if (result.warning) warningCount++;
            else failureCount++;
        }
        
        log.info("------------------------------------------");
        log.info("   Total: {} passed | {} warnings | {} failed", 
            successCount, warningCount, failureCount);
        
        // Overall status
        if (failureCount > 0) {
            log.error("❌ RECAPTCHA VALIDATION FAILED - Service may not work correctly!");
            log.error("   Please check the configuration and credentials");
        } else if (warningCount > 0) {
            log.warn("⚠️ RECAPTCHA VALIDATION PASSED WITH WARNINGS");
            log.warn("   Service should work but review warnings above");
        } else {
            log.info("✅ RECAPTCHA VALIDATION SUCCESSFUL");
            log.info("   All checks passed - ready for production!");
        }
        
        // Configuration info
        log.info("");
        log.info("🔧 CONFIGURATION:");
        log.info("   Site Key: {}...{}", 
            recaptchaConfig.getSiteKey().substring(0, 8),
            recaptchaConfig.getSiteKey().substring(36));
        log.info("   Project: {}", recaptchaConfig.getProjectId());
        log.info("   Cache Duration: {}s", recaptchaConfig.getCacheDuration());
        log.info("   Status: {}", recaptchaConfig.isEnabled() ? "🟢 ENABLED" : "🔴 DISABLED");
    }
    
    /**
     * Mask email for security
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "N/A";
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String maskedLocal = localPart.charAt(0) + "***";
        
        return maskedLocal + domain;
    }
    
    /**
     * Internal class for validation results
     */
    private static class ValidationResult {
        final String name;
        final boolean success;
        final boolean warning;
        final String details;
        
        private ValidationResult(String name, boolean success, boolean warning, String details) {
            this.name = name;
            this.success = success;
            this.warning = warning;
            this.details = details;
        }
        
        static ValidationResult success(String name, String details) {
            return new ValidationResult(name, true, false, details);
        }
        
        static ValidationResult warning(String name, String details) {
            return new ValidationResult(name, false, true, details);
        }
        
        static ValidationResult failure(String name, String details) {
            return new ValidationResult(name, false, false, details);
        }
    }
}
