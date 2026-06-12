package com.sm.instagram.platform.auth.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.dto.TotpVerifyRequest;
import com.sm.instagram.platform.auth.dto.TotpSetupResponse;
import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.auth.service.TwoFactorAuthService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for 2FA status and operations.
 * This controller handles all 2FA operations AFTER login.
 *
 * The separation is critical:
 * - Login only checks Firebase custom claims (no DB queries)
 * - This controller handles actual 2FA operations
 *
 * Rate limiting: AUTH profile (20 req/min) to handle:
 * - Multiple 2FA verification attempts
 * - Setup flows with retries
 * - Network issues during TOTP validation
 */
@RestController
@RequestMapping("/twofactor")
@RequiredArgsConstructor
@Slf4j
@RateLimit(profile = RateLimitProfile.AUTH)
public class TwoFactorStatusController {
    
    private final FirebaseAuth firebaseAuth;
    private final TotpFirestoreService totpFirestoreService;
    private final TwoFactorAuthService twoFactorAuthService;
    private final TokenExchangeService tokenExchangeService;
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    
    /**
     * Check 2FA status for current user.
     * This is called AFTER login, not during.
     * 
     * Available to both PENDING_ADMIN and ADMIN roles.
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> check2FAStatus(Principal principal) {
        String firebaseUserId = principal.getName();
        log.info("GDPR: Operation=check2FAStatus, FirebaseUID={}, DataAccessed=2fa_status,user_role, Purpose=security_verification", firebaseUserId);
        
        try {
            // Check Firebase custom claims
            UserRecord user = firebaseAuth.getUser(firebaseUserId);
            Map<String, Object> claims = user.getCustomClaims();
            String role = (String) claims.get("role");
            
            // Check actual 2FA configuration in Firestore
            boolean has2FA = totpFirestoreService.is2FAEnabled(firebaseUserId);

            Map<String, Object> response = new HashMap<>();
            response.put("role", role);
            response.put("has2FA", has2FA);
            response.put("requires2FASetup", "PENDING_ADMIN".equals(role));
            response.put("canAccessAdmin", "ADMIN".equals(role));

            // Enhanced integrity checking for role/2FA mismatches
            if ("PENDING_ADMIN".equals(role) && has2FA) {
                // User completed 2FA setup but role wasn't upgraded - needs sync
                response.put("warning", "Role mismatch detected - user has 2FA but role is PENDING_ADMIN");
                response.put("integrityIssue", "role_not_upgraded");
                log.warn("INTEGRITY: User {} has 2FA enabled but role is still PENDING_ADMIN - role upgrade may have failed", firebaseUserId);
            } else if ("ADMIN".equals(role) && !has2FA) {
                // CRITICAL: Admin without 2FA - check if document exists at all
                boolean totpDocExists = totpFirestoreService.totpSecretExists(firebaseUserId);
                if (totpDocExists) {
                    // Document exists but enabled=false - setup was interrupted or corrupted
                    response.put("warning", "Role mismatch detected - user is ADMIN but 2FA not enabled");
                    response.put("integrityIssue", "totp_not_enabled");
                    log.warn("INTEGRITY: Admin {} has TOTP document but enabled=false - setup may have been interrupted", firebaseUserId);
                } else {
                    // No document at all - critical data integrity issue
                    response.put("warning", "Critical: ADMIN user has no 2FA configuration");
                    response.put("integrityIssue", "no_totp_document");
                    log.error("CRITICAL INTEGRITY: Admin {} has no TOTP document at all! User may have been promoted incorrectly or data was deleted", firebaseUserId);
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (FirebaseAuthException e) {
            log.error("Failed to check 2FA status for user: {}", firebaseUserId, e);
            throw new AuthenticationTranslatableException("error.auth.firebase_account_not_found");
        }
    }
    
    /**
     * Setup 2FA - only for PENDING_ADMIN users.
     * This generates a QR code and secret for the user to set up their authenticator app.
     */
    @PostMapping("/setup")
    @PreAuthorize("hasAuthority('PENDING_ADMIN')")
    public ResponseEntity<?> setup2FA(Principal principal) {
        String firebaseUserId = principal.getName();
        log.info("GDPR: Operation=setup2FA, FirebaseUID={}, DataAccessed=none, Purpose=security_enhancement", firebaseUserId);

        // Check if 2FA is already enabled (shouldn't be for PENDING_ADMIN)
        if (totpFirestoreService.is2FAEnabled(firebaseUserId)) {
            log.warn("2FA already enabled for PENDING_ADMIN user: {}", firebaseUserId);
            throw new BusinessRuleTranslatableException("error.auth.2fa_already_configured");
        }

        // Generate 2FA setup data - let service throw translatable exceptions
        TotpSetupResponse setupResponse = twoFactorAuthService.setupTwoFactor(firebaseUserId);

        log.info("2FA setup data generated for user: {}", firebaseUserId);

        return ResponseEntity.ok(setupResponse);
    }
    
    /**
     * Verify TOTP code during 2FA setup.
     * This is called by PENDING_ADMIN users to complete their 2FA setup.
     * Upon successful verification, the user's role is upgraded to ADMIN.
     */
    @PostMapping("/verify-setup")
    @PreAuthorize("hasAuthority('PENDING_ADMIN')")
    @Transactional
    public ResponseEntity<?> verifySetup2FA(@RequestBody TotpVerifyRequest request, 
                                           Principal principal,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        String firebaseUserId = principal.getName();
        log.info("GDPR: Operation=verify2FASetup, FirebaseUID={}, DataAccessed=2fa_secret,user_role, Purpose=security_verification", firebaseUserId);

        // Verify the TOTP code - let service throw if invalid
        boolean isValid = twoFactorAuthService.verifyTotpCode(firebaseUserId, request.getCode());

        if (!isValid) {
            log.warn("Invalid TOTP code during setup for user: {}", firebaseUserId);
            throw new ValidationTranslatableException("error.auth.2fa_invalid_code");
        }

        try {
            // Enable 2FA in Firestore
            totpFirestoreService.enable2FA(firebaseUserId);

            // Upgrade user role from PENDING_ADMIN to ADMIN
            UserRecord userRecord = firebaseAuth.getUser(firebaseUserId);
            Map<String, Object> claims = new HashMap<>(userRecord.getCustomClaims());
            claims.put("role", "ADMIN");
            claims.put("2faEnabledAt", System.currentTimeMillis());
            firebaseAuth.setCustomUserClaims(firebaseUserId, claims);

            // Update database UserType to match Firebase role
            User user = userRepository.findByFirebaseUserId(firebaseUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not_found", firebaseUserId));
            user.setUserType(UserType.ADMIN);
            userRepository.save(user);

            log.info("GDPR: Operation=upgradeUserRole, FirebaseUID={}, DataAccessed=user.role,user.userType, Purpose=role_elevation", firebaseUserId);
            log.info("GDPR: Operation=updateDatabaseUserType, FirebaseUID={}, DataAccessed=user.userType, Purpose=data_consistency", firebaseUserId);

            // AUTO-LOGIN: Create a fully authenticated session immediately
            // User just proved they have 2FA, no need to re-authenticate
            var sessionResponse = tokenExchangeService.createTwoFactorVerifiedSession(
                user, firebaseUserId, httpRequest, httpResponse
            );

            log.info("Auto-login after 2FA setup - user has full ADMIN access immediately");

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "2FA has been successfully enabled. You now have full admin access!",
                "newRole", "ADMIN",
                "requiresRelogin", false,  // No re-login needed!
                "autoLoggedIn", true,
                "twoFactorVerified", true,
                "canAccessAdmin", true
            ));
            
        } catch (FirebaseAuthException e) {
            log.error("Failed to update Firebase during 2FA setup for user: {}", firebaseUserId, e);
            throw new AuthenticationTranslatableException("error.auth.2fa_setup_failed");
        }
    }
    
    /**
     * Verify 2FA during login.
     * This endpoint requires at least partial authentication (JWT token from initial login).
     * It validates the partial token and extracts the Firebase UID from it.
     * 
     * Security measures:
     * - Requires valid JWT token (prevents anonymous brute force)
     * - Rate limited to prevent authenticated brute force
     * - Validates token claims before processing
     * 
     * Upon successful verification, returns a Firebase custom token with updated claims.
     * Frontend will use this token to get an ID token and exchange for full session.
     */
    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated() or hasAuthority('PARTIAL_AUTH') or hasAuthority('PENDING_2FA')")
    public ResponseEntity<?> verify2FA(@RequestBody TotpVerifyRequest request, 
                                      Principal principal,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        // Extract Firebase user ID from the authenticated principal
        // This ensures the user has a valid token from initial login
        String firebaseUserId = null;
        
        if (principal != null) {
            firebaseUserId = principal.getName();
            log.debug("Firebase UID from principal: {}", firebaseUserId);
        } else {
            // This shouldn't happen with @PreAuthorize
            log.error("No principal found despite @PreAuthorize - possible security issue");
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }
        log.info("GDPR: Operation=verify2FALogin, FirebaseUID={}, DataAccessed=2fa_secret,user_role, Purpose=authentication", firebaseUserId);

        // Verify the TOTP code - let service throw if invalid
        boolean isValid = twoFactorAuthService.verifyTotpCode(firebaseUserId, request.getCode());

        if (!isValid) {
            log.warn("Invalid TOTP code during login for admin user: {}", firebaseUserId);
            throw new ValidationTranslatableException("error.auth.2fa_invalid_code");
        }
        
        try {
            // Update Firebase custom claims to mark 2FA as verified WITH TIMESTAMP
            // Get existing claims to preserve other data
            UserRecord user = firebaseAuth.getUser(firebaseUserId);
            Map<String, Object> updatedClaims = new HashMap<>(user.getCustomClaims());
            
            // Add 2FA verification with timestamp for time-bound validation
            long timestamp = System.currentTimeMillis();
            updatedClaims.put("role", "ADMIN");
            updatedClaims.put("twoFactorVerified", true);  // Mark as verified
            updatedClaims.put("verifiedAt", timestamp);  // Deprecated - use twoFactorTimestamp
            updatedClaims.put("twoFactorTimestamp", timestamp);  // New standard timestamp
            // CRITICAL: Explicitly set adminChallengeCompletedAt to avoid race condition.
            // TwoFactorAuthService.verifyTotpCode() already sets this, but due to Firebase
            // eventual consistency, the getUser() call above may return stale claims.
            // By explicitly including it here, we ensure it survives the claim update.
            updatedClaims.put("adminChallengeCompletedAt", timestamp);
            
            // Update user in Firebase - this updates what Firebase knows about the user
            // The original ID token is still valid and can be reused!
            firebaseAuth.setCustomUserClaims(firebaseUserId, updatedClaims);
            
            // VERIFY the claims were set
            UserRecord updatedUser = firebaseAuth.getUser(firebaseUserId);
            Map<String, Object> verifiedClaims = updatedUser.getCustomClaims();
            Boolean verified = (Boolean) verifiedClaims.get("twoFactorVerified");
            
            // Handle numeric conversion properly - Firebase can return BigDecimal, Integer, or Long
            Long savedTimestamp = null;
            Object timestampObj = verifiedClaims.get("twoFactorTimestamp");
            if (timestampObj != null) {
                if (timestampObj instanceof Number) {
                    savedTimestamp = ((Number) timestampObj).longValue();
                }
            }
            
            if (verified == null || !verified || savedTimestamp == null) {
                log.error("GDPR: Operation=2FA_VERIFY, UserId={}, Result=FIREBASE_UPDATE_FAILED", firebaseUserId);
                throw new BusinessRuleTranslatableException("error.auth.2fa_update_failed");
            }

            log.info("GDPR: Operation=2FA_VERIFY, UserId={}, Result=SUCCESS, Timestamp={}, ExpiresAt={}",
                     firebaseUserId, timestamp, timestamp + (2 * 60 * 1000));
            log.info("2FA verification successful for admin user: {}", firebaseUserId);
            log.info("Firebase claims updated with timestamp - valid for 2 minutes");
            log.info("The exchange-token endpoint will check Firebase for the updated claims");

            return ResponseEntity.ok(Map.of(
                "success", true,
                "reuseIdToken", true,  // Tell frontend to reuse the original ID token
                "verified", true,
                "message", "2FA verification successful - reuse your original ID token",
                "twoFactorVerified", true,
                "canAccessAdmin", true
            ));
            
        } catch (FirebaseAuthException e) {
            log.error("Failed to update Firebase during 2FA verification for user: {}", firebaseUserId, e);
            throw new AuthenticationTranslatableException("error.auth.firebase_account_not_found");
        }
    }
    
    /**
     * Disable 2FA - only for ADMIN users.
     * This will downgrade the user from ADMIN to PENDING_ADMIN.
     * Requires current TOTP code for security.
     */
    @PostMapping("/disable")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> disable2FA(@RequestBody TotpVerifyRequest request, Principal principal) {
        String firebaseUserId = principal.getName();
        log.warn("GDPR: Operation=disable2FA, FirebaseUID={}, DataAccessed=2fa_settings,user_role, Purpose=security_downgrade", firebaseUserId);

        // Verify current TOTP code before allowing disable
        boolean isValid = twoFactorAuthService.verifyTotpCode(firebaseUserId, request.getCode());

        if (!isValid) {
            log.warn("Invalid TOTP code when trying to disable 2FA for user: {}", firebaseUserId);
            throw new ValidationTranslatableException("error.auth.2fa_invalid_code");
        }

        try {
            // Disable 2FA in Firestore
            totpFirestoreService.disable2FA(firebaseUserId);

            // Downgrade user role from ADMIN to PENDING_ADMIN
            UserRecord userRecord = firebaseAuth.getUser(firebaseUserId);
            Map<String, Object> claims = new HashMap<>(userRecord.getCustomClaims());
            claims.put("role", "PENDING_ADMIN");
            claims.put("2faDisabledAt", System.currentTimeMillis());
            claims.remove("2faEnabledAt");
            firebaseAuth.setCustomUserClaims(firebaseUserId, claims);

            // Update database UserType to match Firebase role
            User user = userRepository.findByFirebaseUserId(firebaseUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not_found", firebaseUserId));
            user.setUserType(UserType.PENDING_ADMIN);
            // Invalidate the live ADMIN session so the downgrade actually takes effect: bump
            // tokenVersion (the existing JWT's role=ADMIN claim then 419s -> silent-refresh into the
            // PENDING_ADMIN claim) and evict the cache so it stops serving the stale userType/version.
            // Without this, the requiresRelogin:true response is advisory only and the admin keeps
            // ADMIN authority on the backend until the cookie naturally expires.
            user.incrementTokenVersion();
            userRepository.save(user);
            userCacheService.evict(firebaseUserId);

            log.warn("GDPR: Operation=downgradeUserRole, FirebaseUID={}, DataAccessed=user.role,user.userType, Purpose=role_reduction", firebaseUserId);
            log.info("GDPR: Operation=updateDatabaseUserType, FirebaseUID={}, DataAccessed=user.userType, Purpose=data_consistency", firebaseUserId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "2FA has been disabled. You will need to set it up again to access admin features.",
                "newRole", "PENDING_ADMIN",
                "requiresRelogin", true
            ));
            
        } catch (FirebaseAuthException e) {
            log.error("Failed to update Firebase during 2FA disable for user: {}", firebaseUserId, e);
            throw new AuthenticationTranslatableException("error.auth.firebase_account_not_found");
        }
    }
    
    /**
     * Generate backup codes - only for ADMIN users.
     * Requires current TOTP code for security.
     */
    @PostMapping("/backup-codes")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> generateBackupCodes(@RequestBody TotpVerifyRequest request, Principal principal) {
        String firebaseUserId = principal.getName();
        log.info("GDPR: Operation=generateBackupCodes, FirebaseUID={}, DataAccessed=2fa_backup_codes, Purpose=account_recovery", firebaseUserId);

        // Verify current TOTP code - let service throw if invalid
        boolean isValid = twoFactorAuthService.verifyTotpCode(firebaseUserId, request.getCode());

        if (!isValid) {
            log.warn("Invalid TOTP code when generating backup codes for user: {}", firebaseUserId);
            throw new ValidationTranslatableException("error.auth.2fa_invalid_code");
        }

        // Generate new backup codes - let service throw translatable exceptions
        var backupCodes = twoFactorAuthService.regenerateBackupCodes(firebaseUserId);

        log.info("Generated {} backup codes for user: {}", backupCodes.size(), firebaseUserId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "backupCodes", backupCodes,
            "message", "Please save these backup codes in a secure location"
        ));
    }
}
