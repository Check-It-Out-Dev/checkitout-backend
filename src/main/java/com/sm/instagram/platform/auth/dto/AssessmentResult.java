package com.sm.instagram.platform.auth.dto;

/**
 * Result of reCAPTCHA assessment
 */
public class AssessmentResult {
    
    private boolean allowed;
    private Double score;
    private String reason;
    private String status;
    
    // Private constructor
    private AssessmentResult(boolean allowed, Double score, String reason, String status) {
        this.allowed = allowed;
        this.score = score;
        this.reason = reason;
        this.status = status;
    }
    
    // Factory methods
    public static AssessmentResult success(Double score) {
        return new AssessmentResult(true, score, "Valid assessment", "SUCCESS");
    }
    
    public static AssessmentResult blocked(Double score, String reason) {
        return new AssessmentResult(false, score, reason, "BLOCKED");
    }
    
    public static AssessmentResult invalid(String reason) {
        return new AssessmentResult(false, 0.0, reason, "INVALID");
    }
    
    public static AssessmentResult allowed(String reason) {
        return new AssessmentResult(true, 1.0, reason, "ALLOWED");
    }
    
    // Getters
    public boolean isAllowed() {
        return allowed;
    }
    
    public Double getScore() {
        return score;
    }
    
    public String getReason() {
        return reason;
    }
    
    public String getStatus() {
        return status;
    }
    
    @Override
    public String toString() {
        return String.format("AssessmentResult[status=%s, score=%.2f, allowed=%s, reason=%s]", 
            status, score, allowed, reason);
    }
}
