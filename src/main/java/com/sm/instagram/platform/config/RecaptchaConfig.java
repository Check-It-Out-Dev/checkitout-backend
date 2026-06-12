package com.sm.instagram.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for reCAPTCHA Enterprise integration
 * Manages site keys, project IDs, and validation thresholds
 */
@Configuration
@ConfigurationProperties(prefix = "recaptcha")
public class RecaptchaConfig {
    
    private String projectId;
    private String siteKey;
    private Double scoreThreshold = 0.5;
    private boolean enabled = true;
    private int cacheDuration = 60; // seconds
    
    // Action-specific thresholds (optional)
    private Double loginThreshold = 0.5;
    private Double signupThreshold = 0.3; // Lower threshold for signup to reduce friction
    private Double forgotPasswordThreshold = 0.3;
    
    // Getters and Setters
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public String getSiteKey() {
        return siteKey;
    }
    
    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }
    
    public Double getScoreThreshold() {
        return scoreThreshold;
    }
    
    public void setScoreThreshold(Double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getCacheDuration() {
        return cacheDuration;
    }
    
    public void setCacheDuration(int cacheDuration) {
        this.cacheDuration = cacheDuration;
    }
    
    public Double getLoginThreshold() {
        return loginThreshold != null ? loginThreshold : scoreThreshold;
    }
    
    public void setLoginThreshold(Double loginThreshold) {
        this.loginThreshold = loginThreshold;
    }
    
    public Double getSignupThreshold() {
        return signupThreshold != null ? signupThreshold : scoreThreshold;
    }
    
    public void setSignupThreshold(Double signupThreshold) {
        this.signupThreshold = signupThreshold;
    }
    
    public Double getForgotPasswordThreshold() {
        return forgotPasswordThreshold != null ? forgotPasswordThreshold : scoreThreshold;
    }
    
    public void setForgotPasswordThreshold(Double forgotPasswordThreshold) {
        this.forgotPasswordThreshold = forgotPasswordThreshold;
    }
    
    /**
     * Get the threshold for a specific action
     */
    public Double getThresholdForAction(String action) {
        if (action == null) {
            return scoreThreshold;
        }
        
        switch (action.toUpperCase()) {
            case "LOGIN":
                return getLoginThreshold();
            case "SIGNUP":
                return getSignupThreshold();
            case "FORGOT_PASSWORD":
                return getForgotPasswordThreshold();
            default:
                return scoreThreshold;
        }
    }
}
