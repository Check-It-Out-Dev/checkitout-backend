package com.sm.instagram.platform.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to check and sync 2FA status for admin users during login.
 * Similar to AdminCheckRunner but runs on-demand for any admin user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminIntegrityChecker {
    
    private final FirebaseAuth firebaseAuth;
    private final TotpFirestoreService totpFirestoreService;
    private final UserRepository userRepository;
    
    /**
     * Check and sync 2FA status for an admin user.
     * Called during login to ensure role consistency.
     * 
     * @param firebaseUid Firebase user ID
     * @param email User email for logging
     * @return true if check passed, false if role was adjusted
     */
    @Transactional
    public boolean checkAndSyncAdminIntegrity(String firebaseUid, String email) {
        try {
            // Get user from database
            User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElse(null);
            
            if (user == null) {
                log.debug("User {} not found in database - new user", firebaseUid);
                return true; // Not an issue for new users
            }
            
            // Only check admin users
            if (user.getUserType() != UserType.ADMIN && user.getUserType() != UserType.PENDING_ADMIN) {
                log.debug("User {} is not admin type - skipping integrity check", firebaseUid);
                return true;
            }
            
            log.info("Running 2FA integrity check for admin user: {}", email);
            
            // Get Firebase user record
            UserRecord userRecord = firebaseAuth.getUser(firebaseUid);
            Map<String, Object> claims = userRecord.getCustomClaims();
            String currentRole = (String) claims.get("role");
            
            // Check actual 2FA status in Firestore
            boolean has2FA = totpFirestoreService.is2FAEnabled(firebaseUid);
            
            // Determine expected role based on 2FA status
            String expectedRole = has2FA ? "ADMIN" : "PENDING_ADMIN";
            UserType expectedUserType = has2FA ? UserType.ADMIN : UserType.PENDING_ADMIN;
            
            // Check for mismatches
            boolean roleNeedsUpdate = !expectedRole.equals(currentRole);
            boolean dbNeedsUpdate = user.getUserType() != expectedUserType;
            
            if (roleNeedsUpdate || dbNeedsUpdate) {
                log.warn("2FA integrity issue detected for user {}: has2FA={}, currentRole={}, dbType={}", 
                    email, has2FA, currentRole, user.getUserType());
                
                // Fix Firebase role
                if (roleNeedsUpdate) {
                    Map<String, Object> newClaims = new HashMap<>(claims);
                    newClaims.put("role", expectedRole);
                    
                    // If downgrading to PENDING_ADMIN, clear 2FA verification flags
                    if (!has2FA) {
                        newClaims.remove("twoFactorVerified");
                        newClaims.remove("verifiedAt");
                        newClaims.put("2faIntegrityCheck", "failed");
                        newClaims.put("2faCheckTime", System.currentTimeMillis());
                    }
                    
                    firebaseAuth.setCustomUserClaims(firebaseUid, newClaims);
                    log.info("Fixed Firebase role for user {} from {} to {}", email, currentRole, expectedRole);
                }
                
                // Fix database type
                if (dbNeedsUpdate) {
                    user.setUserType(expectedUserType);
                    userRepository.save(user);
                    log.info("Fixed database UserType for user {} to {}", email, expectedUserType);
                }
                
                // Integrity was broken but now fixed
                return false;
            }
            
            log.debug("2FA integrity check passed for user {}: role={}, has2FA={}", email, currentRole, has2FA);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to check admin integrity for user {}: {}", firebaseUid, e.getMessage());
            // Don't block login on integrity check failure
            return true;
        }
    }
    
    /**
     * Quick check if user claims indicate admin but might need verification.
     * Used to trigger full integrity check.
     */
    public boolean needsIntegrityCheck(Map<String, Object> claims) {
        String role = (String) claims.get("role");
        
        // Check if user claims to be admin
        if (!"ADMIN".equals(role) && !"PENDING_ADMIN".equals(role)) {
            return false; // Not admin, no check needed
        }
        
        // Check if we recently did an integrity check
        // Handle numeric conversion properly - Firebase can return BigDecimal, Integer, or Long
        Long lastCheck = null;
        Object lastCheckObj = claims.get("2faCheckTime");
        if (lastCheckObj instanceof Number) {
            lastCheck = ((Number) lastCheckObj).longValue();
        }
        if (lastCheck != null) {
            long timeSinceCheck = System.currentTimeMillis() - lastCheck;
            // Skip if checked within last hour
            if (timeSinceCheck < 60 * 60 * 1000) {
                return false;
            }
        }
        
        return true; // Needs check
    }
}
