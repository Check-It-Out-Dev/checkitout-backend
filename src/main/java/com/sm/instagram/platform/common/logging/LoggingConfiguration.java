package com.sm.instagram.platform.common.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration class for request logging setup.
 * Provides centralized logging configuration for request tracking and security events.
 */
@Configuration
public class LoggingConfiguration {
    
    // This class serves as a marker for the logging package
    // and can be extended with additional logging configuration beans if needed
    
    /**
     * Log levels for different environments:
     * 
     * Production:
     * - REQUEST_SUCCESS: INFO
     * - REQUEST_REJECTED: WARN  
     * - REQUEST_ERROR: ERROR
     * - AUTHENTICATION_FAILURE: WARN
     * - ACCESS_DENIED: WARN
     * - CORS issues: WARN/ERROR
     * 
     * Development:
     * - All above + DEBUG level for detailed request inspection
     * 
     * Test:
     * - Minimal logging to avoid noise during tests
     */
}
