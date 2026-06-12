package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response object from reCAPTCHA Enterprise assessment
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecaptchaResponse {
    
    @JsonProperty("tokenProperties")
    private TokenProperties tokenProperties;
    
    @JsonProperty("riskAnalysis")
    private RiskAnalysis riskAnalysis;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("event")
    private Object event;
    
    // Inner class for token properties
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenProperties {
        @JsonProperty("valid")
        private boolean valid;
        
        @JsonProperty("invalidReason")
        private String invalidReason;
        
        @JsonProperty("hostname")
        private String hostname;
        
        @JsonProperty("action")
        private String action;
        
        @JsonProperty("createTime")
        private String createTime;
        
        // Getters and setters
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public String getInvalidReason() {
            return invalidReason;
        }
        
        public void setInvalidReason(String invalidReason) {
            this.invalidReason = invalidReason;
        }
        
        public String getHostname() {
            return hostname;
        }
        
        public void setHostname(String hostname) {
            this.hostname = hostname;
        }
        
        public String getAction() {
            return action;
        }
        
        public void setAction(String action) {
            this.action = action;
        }
        
        public String getCreateTime() {
            return createTime;
        }
        
        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }
    }
    
    // Inner class for risk analysis
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskAnalysis {
        @JsonProperty("score")
        private Double score;
        
        @JsonProperty("reasons")
        private String[] reasons;
        
        // Getters and setters
        public Double getScore() {
            return score;
        }
        
        public void setScore(Double score) {
            this.score = score;
        }
        
        public String[] getReasons() {
            return reasons;
        }
        
        public void setReasons(String[] reasons) {
            this.reasons = reasons;
        }
    }
    
    // Getters and setters
    public TokenProperties getTokenProperties() {
        return tokenProperties;
    }
    
    public void setTokenProperties(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }
    
    public RiskAnalysis getRiskAnalysis() {
        return riskAnalysis;
    }
    
    public void setRiskAnalysis(RiskAnalysis riskAnalysis) {
        this.riskAnalysis = riskAnalysis;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Object getEvent() {
        return event;
    }
    
    public void setEvent(Object event) {
        this.event = event;
    }
}
