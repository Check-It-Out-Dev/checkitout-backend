package com.sm.instagram.platform.auth.dto;

import java.util.Map;

/**
 * Request object for creating reCAPTCHA Enterprise assessment
 */
public class RecaptchaRequest {
    
    private Map<String, Object> event;
    
    public RecaptchaRequest() {
    }
    
    public Map<String, Object> getEvent() {
        return event;
    }
    
    public void setEvent(Map<String, Object> event) {
        this.event = event;
    }
}
