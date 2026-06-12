package com.sm.instagram.platform.auth.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Session data holder for temporary OAuth registration data.
 * Stores social platform data between OAuth callback and registration completion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionData {
    
    private Map<String, Object> socialData;
    private String platform;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    
    /**
     * Check if session is expired.
     * 
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}
