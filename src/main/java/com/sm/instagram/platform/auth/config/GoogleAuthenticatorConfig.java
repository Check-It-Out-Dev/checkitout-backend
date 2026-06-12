package com.sm.instagram.platform.auth.config;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized configuration for Google Authenticator (TOTP).
 * Provides a singleton GoogleAuthenticator bean with consistent settings.
 */
@Configuration
@Slf4j
public class GoogleAuthenticatorConfig {
    
    @Value("${totp.window-size:1}")
    private int windowSize; // Number of 30-second windows to allow before/after current
    
    @Value("${totp.code-digits:6}")
    private int codeDigits;
    
    @Value("${totp.time-step-seconds:30}")
    private long timeStepSeconds;
    
    @Value("${totp.key-representation:BASE32}")
    private String keyRepresentation;
    
    /**
     * Creates a singleton GoogleAuthenticator bean with centralized configuration.
     * This ensures consistent TOTP settings across the application.
     * 
     * @return Configured GoogleAuthenticator instance
     */
    @Bean
    public GoogleAuthenticator googleAuthenticator() {
        log.info("Configuring GoogleAuthenticator:");
        log.info("  Window Size: {} (±{} seconds tolerance)", windowSize, windowSize * 30);
        log.info("  Code Digits: {}", codeDigits);
        log.info("  Time Step: {} seconds", timeStepSeconds);
        log.info("  Key Format: {}", keyRepresentation);
        
        com.warrenstrange.googleauth.GoogleAuthenticatorConfig config = 
            new GoogleAuthenticatorConfigBuilder()
                .setWindowSize(windowSize)           // Time tolerance (default: 1 = ±30 seconds)
                .setCodeDigits(codeDigits)          // Length of TOTP code (default: 6)
                .setTimeStepSizeInMillis(timeStepSeconds * 1000) // Time step (default: 30 seconds)
                .setKeyRepresentation(com.warrenstrange.googleauth.KeyRepresentation.valueOf(keyRepresentation))
                .build();
        
        return new GoogleAuthenticator(config);
    }
}
