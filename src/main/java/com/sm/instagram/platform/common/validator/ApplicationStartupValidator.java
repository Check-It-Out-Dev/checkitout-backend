package com.sm.instagram.platform.common.validator;

import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.config.RecaptchaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive startup validator for all critical services.
 * Performs validation checks on application startup to ensure
 * all integrations are properly configured and operational.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2) // Run after individual validators
public class ApplicationStartupValidator {
    
    private final GoogleCredentialsProvider credentialsProvider;
    private final RecaptchaConfig recaptchaConfig;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;

    @Value("${storage.mode:inmemory}")
    private String storageMode;
    
    @Value("${app.environment:UNKNOWN}")
    private String environment;
    
    @Value("${firebase.project.id:check-it-out-47c50}")
    private String firebaseProjectId;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    /**
     * Perform comprehensive startup validation
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateAllServices() {
        log.info("");
        log.info("==========================================");
        log.info("🚀 APPLICATION STARTUP VALIDATION");
        log.info("==========================================");
        log.info("📍 Environment: {}", environment);
        log.info("📍 Server Port: {}", serverPort);
        log.info("📍 Storage Mode: {}", storageMode.toUpperCase());
        log.info("");
        
        List<ServiceStatus> statuses = new ArrayList<>();
        
        // Check all critical services
        statuses.add(checkFirebaseAuth());
        statuses.add(checkGoogleCloudStorage());
        statuses.add(checkReCaptcha());
        statuses.add(checkStorageMode());
        statuses.add(checkProjectConsistency());
        statuses.add(checkSmtpConfiguration());

        // Print summary
        printServiceSummary(statuses);
        
        // Final status
        printFinalStatus(statuses);
        
        log.info("==========================================");
        log.info("");
    }
    
    /**
     * Check Firebase Authentication status
     */
    private ServiceStatus checkFirebaseAuth() {
        try {
            if (credentialsProvider.getCachedCredentials() != null) {
                String serviceAccount = maskEmail(credentialsProvider.getServiceAccountEmail());
                return ServiceStatus.operational("Firebase Auth", 
                    String.format("Account: %s", serviceAccount));
            }
            return ServiceStatus.degraded("Firebase Auth", "No credentials available");
        } catch (Exception e) {
            return ServiceStatus.failed("Firebase Auth", e.getMessage());
        }
    }
    
    /**
     * Check Google Cloud Storage status
     */
    private ServiceStatus checkGoogleCloudStorage() {
        try {
            String projectId = credentialsProvider.getProjectId();
            if (projectId != null && !projectId.isEmpty()) {
                return ServiceStatus.operational("Cloud Storage", 
                    String.format("Buckets configured for %s", projectId));
            }
            return ServiceStatus.degraded("Cloud Storage", "Project ID not available");
        } catch (Exception e) {
            return ServiceStatus.failed("Cloud Storage", e.getMessage());
        }
    }
    
    /**
     * Check reCAPTCHA status
     */
    private ServiceStatus checkReCaptcha() {
        try {
            if (!recaptchaConfig.isEnabled()) {
                return ServiceStatus.disabled("reCAPTCHA", "Disabled in configuration");
            }
            
            if (recaptchaConfig.getSiteKey() != null && recaptchaConfig.getProjectId() != null) {
                String keyPreview = recaptchaConfig.getSiteKey().substring(0, 8) + "...";
                return ServiceStatus.operational("reCAPTCHA", 
                    String.format("Site key: %s", keyPreview));
            }
            
            return ServiceStatus.degraded("reCAPTCHA", "Missing configuration");
        } catch (Exception e) {
            return ServiceStatus.failed("reCAPTCHA", e.getMessage());
        }
    }
    
    /**
     * Check storage mode configuration
     */
    private ServiceStatus checkStorageMode() {
        try {
            switch (storageMode.toLowerCase()) {
                case "redis":
                    return ServiceStatus.operational("Cache Storage", "Redis mode active");
                case "inmemory":
                    return ServiceStatus.operational("Cache Storage", "In-memory mode (development)");
                default:
                    return ServiceStatus.degraded("Cache Storage", 
                        String.format("Unknown mode: %s", storageMode));
            }
        } catch (Exception e) {
            return ServiceStatus.failed("Cache Storage", e.getMessage());
        }
    }
    
    /**
     * Check project ID consistency across services
     */
    private ServiceStatus checkProjectConsistency() {
        try {
            String credentialProjectId = credentialsProvider.getProjectId();
            String recaptchaProjectId = recaptchaConfig.getProjectId();
            
            boolean consistent = credentialProjectId.equals(firebaseProjectId) &&
                                credentialProjectId.equals(recaptchaProjectId);
            
            if (consistent) {
                return ServiceStatus.operational("Project Config", 
                    String.format("All services using: %s", credentialProjectId));
            } else {
                return ServiceStatus.warning("Project Config", 
                    String.format("Inconsistent IDs - Firebase: %s, reCAPTCHA: %s", 
                        firebaseProjectId, recaptchaProjectId));
            }
        } catch (Exception e) {
            return ServiceStatus.failed("Project Config", e.getMessage());
        }
    }

    /**
     * Check SMTP configuration status
     */
    private ServiceStatus checkSmtpConfiguration() {
        try {
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl javaMailSender) {
                String host = javaMailSender.getHost();
                int port = javaMailSender.getPort();
                String username = javaMailSender.getUsername();

                if (host != null && username != null) {
                    return ServiceStatus.operational("SMTP Mail",
                        String.format("Configured: %s@%s:%d", username, host, port));
                }
            }
            return ServiceStatus.degraded("SMTP Mail", "Configuration incomplete");
        } catch (Exception e) {
            return ServiceStatus.failed("SMTP Mail", e.getMessage());
        }
    }

    /**
     * Print service summary table
     */
    private void printServiceSummary(List<ServiceStatus> statuses) {
        log.info("📊 SERVICE STATUS:");
        log.info("------------------------------------------");
        
        for (ServiceStatus status : statuses) {
            String emoji = getStatusEmoji(status.status);
            String statusText = formatStatus(status.status);
            
            log.info("   {} {} - {}", emoji, status.serviceName, statusText);
            if (status.details != null && !status.details.isEmpty()) {
                log.info("      └─ {}", status.details);
            }
        }
        
        log.info("------------------------------------------");
    }
    
    /**
     * Print final application status
     */
    private void printFinalStatus(List<ServiceStatus> statuses) {
        long failedCount = statuses.stream()
            .filter(s -> s.status == Status.FAILED)
            .count();
        
        long degradedCount = statuses.stream()
            .filter(s -> s.status == Status.DEGRADED || s.status == Status.WARNING)
            .count();
        
        log.info("");
        if (failedCount > 0) {
            log.error("❌ APPLICATION STATUS: CRITICAL");
            log.error("   {} service(s) failed - immediate attention required!", failedCount);
        } else if (degradedCount > 0) {
            log.warn("⚠️ APPLICATION STATUS: DEGRADED");
            log.warn("   {} service(s) in degraded state - review recommended", degradedCount);
        } else {
            log.info("✅ APPLICATION STATUS: OPERATIONAL");
            log.info("   All critical services are running correctly");
        }
        
        // Environment-specific checks
        if ("PRODUCTION".equals(environment) && credentialsProvider.getProjectId().contains("test")) {
            log.error("🚨 CRITICAL: Production environment using TEST credentials!");
        }
        
        if (!"PRODUCTION".equals(environment) && credentialsProvider.getProjectId().contains("prod")) {
            log.warn("⚠️ WARNING: Non-production environment using PRODUCTION credentials!");
        }
        
        // Instance type
        log.info("");
        log.info("🏷️ INSTANCE INFORMATION:");
        log.info("   Type: {}", credentialsProvider.getInstanceType());
        log.info("   Environment: {}", environment);
        log.info("   Project: {}", credentialsProvider.getProjectId());
        log.info("   Ready URL: http://localhost:{}", serverPort);
    }
    
    /**
     * Get emoji for status
     */
    private String getStatusEmoji(Status status) {
        switch (status) {
            case OPERATIONAL:
                return "✅";
            case DEGRADED:
            case WARNING:
                return "⚠️";
            case FAILED:
                return "❌";
            case DISABLED:
                return "🔒";
            default:
                return "❓";
        }
    }
    
    /**
     * Format status text
     */
    private String formatStatus(Status status) {
        switch (status) {
            case OPERATIONAL:
                return "OPERATIONAL";
            case DEGRADED:
                return "DEGRADED";
            case WARNING:
                return "WARNING";
            case FAILED:
                return "FAILED";
            case DISABLED:
                return "DISABLED";
            default:
                return "UNKNOWN";
        }
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
        
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
    
    /**
     * Service status class
     */
    private static class ServiceStatus {
        final String serviceName;
        final Status status;
        final String details;
        
        private ServiceStatus(String serviceName, Status status, String details) {
            this.serviceName = serviceName;
            this.status = status;
            this.details = details;
        }
        
        static ServiceStatus operational(String name, String details) {
            return new ServiceStatus(name, Status.OPERATIONAL, details);
        }
        
        static ServiceStatus degraded(String name, String details) {
            return new ServiceStatus(name, Status.DEGRADED, details);
        }
        
        static ServiceStatus warning(String name, String details) {
            return new ServiceStatus(name, Status.WARNING, details);
        }
        
        static ServiceStatus failed(String name, String details) {
            return new ServiceStatus(name, Status.FAILED, details);
        }
        
        static ServiceStatus disabled(String name, String details) {
            return new ServiceStatus(name, Status.DISABLED, details);
        }
    }
    
    /**
     * Status enum
     */
    private enum Status {
        OPERATIONAL,
        DEGRADED,
        WARNING,
        FAILED,
        DISABLED
    }
}
