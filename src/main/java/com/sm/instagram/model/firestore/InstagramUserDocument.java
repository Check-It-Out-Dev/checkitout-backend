package com.sm.instagram.model.firestore;

import com.sm.instagram.platform.common.security.Encrypted;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Firestore document model for Instagram users
 * Matches the exact structure in Firestore database
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramUserDocument {
    
    @Encrypted(method = "Google Cloud KMS", keyId = "token-encryption-key")
    private String access_token;  // Long-lived token for Instagram Business API
    private String firebaseUserId;
    private Long followers_count;
    private Long lastUpdated;  // Stored as epoch seconds
    private List<String> permissions;
    private String profile_picture_url;
    private String provider;
    private InstagramUserData user;  // Nested user object
    private String user_id;
    private String username;
    
    /**
     * Nested user data structure
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstagramUserData {
        private String account_type;
        private Long followers_count;
        private String id;
        private Long media_count;
        private String username;
    }
}
