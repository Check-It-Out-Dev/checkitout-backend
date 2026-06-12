package com.sm.instagram.platform.auth.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary session storage for OAuth data during registration flow.
 * Stores social data between OAuth callback and registration completion.
 * Uses in-memory storage (can be replaced with Redis later).
 */
@Slf4j
@Service
public class SocialAuthSessionService {
    
    // In-memory storage for session data
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    
    // Session timeout in minutes
    private static final int SESSION_TIMEOUT_MINUTES = 10;
    
    /**
     * Store social auth data and return a new session ID
     * 
     * @param socialData Social platform data
     * @param platform Platform name
     * @return Generated session ID
     */
    public String storeSocialData(Map<String, Object> socialData, String platform) {
        String sessionId = UUID.randomUUID().toString();
        
        // Extract social user ID for GDPR logging
        String socialUserId = socialData != null ? String.valueOf(socialData.get("user_id")) : "unknown";
        String username = socialData != null ? (String) socialData.get("username") : "unknown";
        
        // GDPR: Log social data storage
        log.info("GDPR: Operation=social_data_stored, SessionId={}, Platform={}, SocialUserId={}, DataAccessed=user_id,username,profile_data, Purpose=oauth_registration, RetentionPeriod={}minutes, LegalBasis=consent", 
            sessionId, platform, socialUserId, SESSION_TIMEOUT_MINUTES);
        
        SessionData session = SessionData.builder()
            .socialData(socialData)
            .platform(platform)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(SESSION_TIMEOUT_MINUTES))
            .build();
        
        sessions.put(sessionId, session);
        
        // GDPR: Log data categories stored
        if (socialData != null) {
            log.info("GDPR: Operation=social_data_categories, SessionId={}, Platform={}, DataFields={}, Purpose=temporary_storage", 
                sessionId, platform, socialData.keySet());
        }
        
        // Clean up expired sessions
        cleanupExpiredSessions();
        
        return sessionId;
    }
    
    /**
     * Retrieve social auth data by session ID
     */
    public SessionData getSocialData(String sessionId) {
        // GDPR: Log session retrieval attempt
        log.info("GDPR: Operation=social_data_retrieval_attempt, SessionId={}, Purpose=registration_continuation", sessionId);
        
        SessionData session = sessions.get(sessionId);
        
        if (session == null) {
            // GDPR: Log session not found
            log.warn("GDPR: Operation=social_data_not_found, SessionId={}, Purpose=session_validation", sessionId);
            return null;
        }
        
        // Check if session is expired
        if (session.isExpired()) {
            // GDPR: Log session expiration and removal
            log.warn("GDPR: Operation=social_data_expired, SessionId={}, Platform={}, Purpose=security_enforcement, Action=data_removed", 
                sessionId, session.getPlatform());
            sessions.remove(sessionId);
            return null;
        }
        
        // GDPR: Log successful retrieval
        String socialUserId = session.getSocialData() != null ? 
            String.valueOf(session.getSocialData().get("user_id")) : "unknown";
        log.info("GDPR: Operation=social_data_retrieved, SessionId={}, Platform={}, SocialUserId={}, Purpose=registration_continuation", 
            sessionId, session.getPlatform(), socialUserId);
        
        return session;
    }
    
    /**
     * Remove session data after successful use
     */
    public void removeSession(String sessionId) {
        SessionData session = sessions.get(sessionId);
        
        if (session != null) {
            String platform = session.getPlatform();
            String socialUserId = session.getSocialData() != null ? 
                String.valueOf(session.getSocialData().get("user_id")) : "unknown";
            
            // GDPR: Log data removal
            log.info("GDPR: Operation=social_data_removed, SessionId={}, Platform={}, SocialUserId={}, Purpose=session_cleanup, Action=permanent_deletion", 
                sessionId, platform, socialUserId);
        }
        
        sessions.remove(sessionId);
    }
    
    /**
     * Clean up expired sessions
     */
    private void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        
        // Count sessions before cleanup for GDPR logging
        int beforeCount = sessions.size();
        
        sessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                // GDPR: Log automatic cleanup of expired session
                SessionData session = entry.getValue();
                String socialUserId = session.getSocialData() != null ? 
                    String.valueOf(session.getSocialData().get("user_id")) : "unknown";
                    
                log.info("GDPR: Operation=social_data_auto_cleanup, SessionId={}, Platform={}, SocialUserId={}, Purpose=data_minimization, Reason=session_expired", 
                    entry.getKey(), session.getPlatform(), socialUserId);
            }
            return expired;
        });
        
        int removedCount = beforeCount - sessions.size();
        if (removedCount > 0) {
            log.info("GDPR: Operation=cleanup_completed, SessionsRemoved={}, Purpose=data_minimization", removedCount);
        }
    }
}
