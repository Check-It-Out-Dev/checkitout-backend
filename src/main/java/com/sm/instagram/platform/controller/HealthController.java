package com.sm.instagram.platform.controller;

import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple health controller for testing local development
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class HealthController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        // Extract Firebase UID if authenticated, otherwise use anonymous
        String firebaseUid = "anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null 
                && !"anonymousUser".equals(auth.getPrincipal().toString())) {
            firebaseUid = auth.getPrincipal().toString();
        }
        
        log.info("GDPR: Operation=healthPing, FirebaseUID={}, Purpose=health_monitoring", 
            firebaseUid);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "Application is running!");
        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        // Extract Firebase UID if authenticated, otherwise use anonymous
        String firebaseUid = "anonymous";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null 
                && !"anonymousUser".equals(auth.getPrincipal().toString())) {
            firebaseUid = auth.getPrincipal().toString();
        }
        
        log.info("GDPR: Operation=healthCheck, FirebaseUID={}, Purpose=system_monitoring", 
            firebaseUid);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("application", "instagram-platform");
        response.put("environment", "local-development");
        return response;
    }
}
