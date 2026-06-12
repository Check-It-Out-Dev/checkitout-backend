package com.sm.instagram.platform.common.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Safety guard to prevent integration tests from running on production environment
 * 
 * This component checks the environment when the Spring context starts and
 * blocks execution if dangerous configurations are detected.
 */
@Slf4j
@Component
public class TestEnvironmentGuard implements ApplicationListener<ContextRefreshedEvent> {
    
    private final Environment environment;
    
    @Value("${app.environment:UNKNOWN}")
    private String appEnvironment;
    
    public TestEnvironmentGuard(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        validateTestSafety();
    }
    
    private void validateTestSafety() {
        String[] activeProfiles = environment.getActiveProfiles();
        String dbUrl = environment.getProperty("spring.datasource.url", "");
        
        log.info("Environment validation - App Environment: {}, Active Profiles: {}", 
                   appEnvironment, String.join(", ", activeProfiles));
        
        // Check if this is a test context with dangerous configuration
        boolean isTestContext = isTestContext();
        boolean hasDangerousConfig = hasDangerousConfiguration(dbUrl, activeProfiles);
        
        if (isTestContext && hasDangerousConfig) {
            String errorMsg = String.format(
                "🚨 CRITICAL SAFETY VIOLATION 🚨\n" +
                "Integration tests are attempting to run with production-like configuration!\n" +
                "App Environment: %s\n" +
                "Active Profiles: %s\n" +
                "Database URL: %s\n" +
                "This could cause DATA LOSS and SERVICE DISRUPTION!\n" +
                "Tests have been BLOCKED for safety.",
                appEnvironment, 
                String.join(", ", activeProfiles), 
                maskSensitiveUrl(dbUrl)
            );
            
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        if (isTestContext) {
            log.info("✅ Test environment validation passed - safe to run integration tests");
        }
    }
    
    private boolean isTestContext() {
        // Check if we're in a test context by looking for test-specific classes
        try {
            Class.forName("org.junit.jupiter.api.Test");
            // Also check stack trace for test execution
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                if (className.contains("Test") && 
                    (className.contains("Integration") || className.contains("SpringBootTest"))) {
                    return true;
                }
            }
        } catch (ClassNotFoundException e) {
            // JUnit not on classpath - not a test context
        }
        return false;
    }
    
    private boolean hasDangerousConfiguration(String dbUrl, String[] activeProfiles) {
        // Check for production indicators
        boolean hasProductionProfile = false;
        for (String profile : activeProfiles) {
            if (profile.toLowerCase().contains("prod") || 
                profile.toLowerCase().contains("live") ||
                profile.toLowerCase().equals("production")) {
                hasProductionProfile = true;
                break;
            }
        }
        
        // Check for production database URLs
        boolean hasProductionDatabase = dbUrl != null && (
            dbUrl.contains("prod") ||
            dbUrl.contains("production") ||
            dbUrl.contains("live") ||
            (dbUrl.contains("postgresql") && !dbUrl.contains("test")) ||
            (dbUrl.contains("mysql") && !dbUrl.contains("test")) ||
            (dbUrl.contains("oracle") && !dbUrl.contains("test")) ||
            // Add your production database patterns here
            dbUrl.contains("your-prod-db-host.com")
        );
        
        // Check app environment setting
        boolean hasProductionEnvironment = "PRODUCTION".equalsIgnoreCase(appEnvironment) ||
                                         "PROD".equalsIgnoreCase(appEnvironment) ||
                                         "LIVE".equalsIgnoreCase(appEnvironment);
        
        return hasProductionProfile || hasProductionDatabase || hasProductionEnvironment;
    }
    
    private String maskSensitiveUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "Not configured";
        }
        
        // Mask sensitive parts of database URLs
        if (url.contains("://")) {
            String[] parts = url.split("://");
            if (parts.length == 2) {
                String protocol = parts[0];
                String rest = parts[1];
                
                // Mask password if present
                if (rest.contains("@")) {
                    String[] authParts = rest.split("@");
                    if (authParts.length == 2) {
                        String credentials = authParts[0];
                        String hostAndDb = authParts[1];
                        
                        // Mask password
                        if (credentials.contains(":")) {
                            String[] credParts = credentials.split(":");
                            credentials = credParts[0] + ":***";
                        }
                        
                        return protocol + "://" + credentials + "@" + hostAndDb;
                    }
                }
            }
        }
        
        return url;
    }
}
