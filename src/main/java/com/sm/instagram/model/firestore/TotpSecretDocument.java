package com.sm.instagram.model.firestore;

import com.google.cloud.Timestamp;
import com.sm.instagram.platform.common.security.Encrypted;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Firestore document model for TOTP secrets
 * Matches the exact structure in Firestore database
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpSecretDocument {
    
    @Encrypted(method = "Google Cloud KMS", keyId = "totp-encryption-key")
    private String encryptedSecret;
    
    private Boolean enabled;
    private Timestamp setupAt;
    private Timestamp verifiedAt;
    private Timestamp disabledAt;
    private Timestamp lastUsedAt;
    
    private BackupCodes backupCodes;
    
    /**
     * Nested backup codes structure
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackupCodes {
        @Encrypted(method = "BCrypt + KMS", keyId = "backup-codes-key")
        private String encryptedCodes;
        
        @Builder.Default
        private List<String> usedCodes = new ArrayList<>();
        
        private Timestamp generatedAt;
        private Timestamp lastUsedAt;
    }
}
