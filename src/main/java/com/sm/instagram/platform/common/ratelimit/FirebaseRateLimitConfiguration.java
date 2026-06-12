package com.sm.instagram.platform.common.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Firebase-specific rate limiting.
 * Since Firebase has its own rate limiting, we apply more relaxed limits.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rate-limit.firebase")
public class FirebaseRateLimitConfiguration {
    
    /**
     * Whether to trust Firebase's built-in rate limiting
     */
    private boolean trustFirebaseRateLimiting = true;
    
    /**
     * Multiplier for Firebase endpoints (e.g., 10x normal limits)
     */
    private int limitMultiplier = 10;
    
    /**
     * Firebase-specific endpoint configurations
     */
    private Map<String, FirebaseEndpointConfig> endpoints = createDefaultEndpoints();
    
    private static Map<String, FirebaseEndpointConfig> createDefaultEndpoints() {
        Map<String, FirebaseEndpointConfig> defaultEndpoints = new HashMap<>();
        
        // Firebase Auth endpoints - relaxed since Firebase handles rate limiting
        defaultEndpoints.put("/auth/social-sign-in", new FirebaseEndpointConfig(50, 300, false));
        defaultEndpoints.put("/auth/register", new FirebaseEndpointConfig(25, 300, false));
        defaultEndpoints.put("/auth/register-influencer", new FirebaseEndpointConfig(25, 300, false));
        
        // Firebase Storage endpoints - moderate limits
        defaultEndpoints.put("/api/firebase/upload", new FirebaseEndpointConfig(50, 3600, true));
        defaultEndpoints.put("/api/opportunities/*/content", new FirebaseEndpointConfig(30, 3600, true));
        
        // Public Firebase endpoints
        defaultEndpoints.put("/api/public/firebase/**", new FirebaseEndpointConfig(100, 60, false));
        
        return defaultEndpoints;
    }
    
    /**
     * Whether to bypass rate limiting for Firebase Admin SDK operations
     */
    private boolean bypassForAdminSdk = true;
    
    /**
     * Firebase service account identifier for bypassing
     */
    private String serviceAccountIdentifier = "firebase-adminsdk";
    
    @Data
    public static class FirebaseEndpointConfig {
        private int requests;
        private int duration; // in seconds
        private boolean perUser;
        
        public FirebaseEndpointConfig() {}
        
        public FirebaseEndpointConfig(int requests, int duration, boolean perUser) {
            this.requests = requests;
            this.duration = duration;
            this.perUser = perUser;
        }
    }
}
