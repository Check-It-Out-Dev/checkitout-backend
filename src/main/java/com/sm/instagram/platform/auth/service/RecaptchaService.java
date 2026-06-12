package com.sm.instagram.platform.auth.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceSettings;
import com.google.recaptchaenterprise.v1.*;
import com.sm.instagram.platform.auth.dto.AssessmentResult;
import com.sm.instagram.platform.common.exceptions.RecaptchaValidationException;
import com.sm.instagram.platform.config.RecaptchaConfig;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service for validating reCAPTCHA Enterprise tokens.
 * Uses centralized GoogleCredentialsProvider for authentication,
 * which handles credentials from base64 property, file, or application default.
 */
@Slf4j
@Service
public class RecaptchaService {
    
    @Autowired
    private RecaptchaConfig recaptchaConfig;
    
    @Autowired
    private GoogleCredentialsProvider credentialsProvider;
    
    private RecaptchaEnterpriseServiceClient recaptchaClient;
    
    /**
     * Initialize the reCAPTCHA client on startup
     */
    @PostConstruct
    public void init() {
        if (!recaptchaConfig.isEnabled()) {
            log.info("reCAPTCHA is disabled, skipping client initialization");
            return;
        }
        
        try {
            initializeClient();
            log.info("reCAPTCHA Enterprise client initialized successfully using GoogleCredentialsProvider");
        } catch (Exception e) {
            log.error("Failed to initialize reCAPTCHA client: {}", e.getMessage());
            // Don't throw - allow application to start even if reCAPTCHA init fails
        }
    }
    
    /**
     * Initialize the reCAPTCHA client with Google credentials from centralized provider
     */
    private void initializeClient() throws IOException {
        // Get credentials with the required scope from the centralized provider
        GoogleCredentials credentials = credentialsProvider.getCredentialsWithScopes(
            "https://www.googleapis.com/auth/cloud-platform"
        );
        
        // Create reCAPTCHA client with credentials
        RecaptchaEnterpriseServiceSettings settings = RecaptchaEnterpriseServiceSettings
            .newBuilder()
            .setCredentialsProvider(() -> credentials)
            .build();
        
        recaptchaClient = RecaptchaEnterpriseServiceClient.create(settings);
        log.debug("reCAPTCHA client created with credentials from GoogleCredentialsProvider");
        log.debug("Using project: {}", credentialsProvider.getProjectId());
    }
    
    /**
     * Verify reCAPTCHA token and return assessment result
     * 
     * @param token The reCAPTCHA token from frontend
     * @param expectedAction The expected action name (LOGIN, SIGNUP, etc.)
     * @param request The HTTP request for additional signals
     * @return Assessment result with score and validity
     */
    public AssessmentResult verifyToken(String token, String expectedAction, HttpServletRequest request) {
        // If reCAPTCHA is disabled, allow all requests
        if (!recaptchaConfig.isEnabled()) {
            log.debug("reCAPTCHA is disabled, allowing request");
            return AssessmentResult.allowed("reCAPTCHA disabled");
        }
        
        // If no token provided
        if (token == null || token.isEmpty()) {
            log.warn("No reCAPTCHA token provided for action: {}", expectedAction);
            throw new RecaptchaValidationException("error.auth.recaptcha_token_required");
        }
        
        // Ensure client is initialized
        if (recaptchaClient == null) {
            log.error("reCAPTCHA client not initialized");
            // Fail open if configured
            if (!recaptchaConfig.isEnabled()) {
                return AssessmentResult.allowed("reCAPTCHA client not initialized");
            }
            throw new RecaptchaValidationException("error.auth.recaptcha_unavailable");
        }
        
        try {
            // Create the assessment request
            Event event = Event.newBuilder()
                .setSiteKey(recaptchaConfig.getSiteKey())
                .setToken(token)
                .setExpectedAction(expectedAction)
                .setUserIpAddress(getClientIp(request))
                .setUserAgent(request.getHeader("User-Agent"))
                .build();
            
            Assessment assessment = Assessment.newBuilder()
                .setEvent(event)
                .build();
            
            // Use project ID from credentials provider for consistency
            String projectId = recaptchaConfig.getProjectId() != null ? 
                recaptchaConfig.getProjectId() : credentialsProvider.getProjectId();
            ProjectName projectName = ProjectName.of(projectId);
            
            // Create the assessment
            Assessment response = recaptchaClient.createAssessment(projectName, assessment);
            
            // Log assessment name for debugging
            log.debug("Assessment created: {}", response.getName());
            
            // Check if token is valid
            if (!response.getTokenProperties().getValid()) {
                log.warn("Invalid reCAPTCHA token: {}", 
                    response.getTokenProperties().getInvalidReason());
                return AssessmentResult.invalid(
                    response.getTokenProperties().getInvalidReason().name());
            }
            
            // Check if action matches
            if (!expectedAction.equals(response.getTokenProperties().getAction())) {
                log.warn("Action mismatch. Expected: {}, Got: {}", 
                    expectedAction, response.getTokenProperties().getAction());
                return AssessmentResult.invalid("Action mismatch");
            }
            
            // Check risk analysis score
            float score = response.getRiskAnalysis().getScore();
            Double threshold = recaptchaConfig.getThresholdForAction(expectedAction);
            
            log.info("reCAPTCHA assessment for action '{}': score={}, threshold={}", 
                expectedAction, score, threshold);
            
            if (score < threshold) {
                log.warn("Low reCAPTCHA score for action '{}': {} < {}", 
                    expectedAction, score, threshold);
                return AssessmentResult.blocked((double) score, "Score below threshold");
            }
            
            return AssessmentResult.success((double) score);
            
        } catch (Exception e) {
            log.error("Error during reCAPTCHA validation: {}", e.getMessage(), e);
            // Fail open if configured
            if (!recaptchaConfig.isEnabled()) {
                return AssessmentResult.allowed("reCAPTCHA error, failing open");
            }
            throw new RecaptchaValidationException("error.auth.recaptcha_failed");
        }
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Check if token is valid for the given action with caching
     */
    @Cacheable(value = "recaptchaAssessments", key = "#token + ':' + #action")
    public boolean isValidToken(String token, String action, HttpServletRequest request) {
        try {
            AssessmentResult result = verifyToken(token, action, request);
            return result.isAllowed();
        } catch (Exception e) {
            log.error("Error validating reCAPTCHA token: {}", e.getMessage());
            // Fail open if configured
            return !recaptchaConfig.isEnabled();
        }
    }
    
    /**
     * Clean up resources on shutdown
     */
    @PreDestroy
    public void cleanup() {
        if (recaptchaClient != null) {
            try {
                recaptchaClient.close();
                log.info("reCAPTCHA client closed");
            } catch (Exception e) {
                log.error("Error closing reCAPTCHA client: {}", e.getMessage());
            }
        }
    }
}
