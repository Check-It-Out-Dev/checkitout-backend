package com.sm.instagram.platform.auth.service;

import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.service.QRCodeGeneratorService;
import com.sm.instagram.platform.auth.service.ImprovedQRCodeService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.UserService;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.auth.dto.TotpSetupResponse;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Service for managing Two-Factor Authentication using TOTP (Time-based One-Time Passwords).
 * Implements Google Authenticator support with Firestore storage and KMS encryption.
 */
@Service
@Slf4j
public class TwoFactorAuthService {
    
    @Autowired
    private TotpFirestoreService firestoreService;
    
    @Autowired
    private UserService userService;
    
    @Value("${totp.issuer:CheckItOut}")
    private String issuer;
    
    @Value("${totp.enabled:true}")
    private boolean totpEnabled;
    
    @Autowired
    private GoogleAuthenticator gAuth;  // Injected from GoogleAuthenticatorConfig
    
    @Autowired
    private QRCodeGeneratorService qrCodeGeneratorService;
    
    @Autowired
    private ImprovedQRCodeService improvedQRCodeService;
    
    @Autowired
    private com.google.firebase.auth.FirebaseAuth firebaseAuth;
    
    /**
     * Set up 2FA for a user - generates secret and backup codes
     * @param firebaseUserId Firebase user ID
     * @return Setup response with QR code URL and backup codes
     */
    public TotpSetupResponse setupTwoFactor(String firebaseUserId) {
        log.info("Setting up 2FA for user: {}", firebaseUserId);
        
        // GDPR: Log 2FA setup initiation
        log.info("GDPR: Operation=setupTwoFactor_entry, FirebaseUID={}, DataAccessed=user.profile,user.type, Purpose=2fa_enrollment", firebaseUserId);
        
        if (!totpEnabled) {
            throw new BusinessRuleTranslatableException("error.business.invalid_state");
        }
        
        // Check if user is admin
        User user = userService.findByFirebaseUserId(firebaseUserId);
        if (user == null) {
            throw new ResourceNotFoundException("error.business.item_not_found", "User");
        }
        
        // Allow both ADMIN and PENDING_ADMIN to setup 2FA
        if (!UserType.ADMIN.equals(user.getUserType()) && !UserType.PENDING_ADMIN.equals(user.getUserType())) {
            log.warn("Attempted 2FA setup for non-admin user: {}", firebaseUserId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    firebaseUserId,
                    "setupTwoFactor",
                    "User#" + user.getId());
        }
        
        // Check if already setup
        if (firestoreService.is2FAEnabled(firebaseUserId)) {
            log.warn("2FA already enabled for user: {}", firebaseUserId);
            throw new BusinessRuleTranslatableException("error.business.duplicate_entry", "2FA setup");
        }
        
        try {
            // Generate new secret
            GoogleAuthenticatorKey key = gAuth.createCredentials();
            String secret = key.getKey();
            
            // Generate backup codes
            List<String> backupCodes = generateBackupCodes();
            
            // Store in Firestore (encrypted)
            firestoreService.storeTotpSecret(firebaseUserId, secret, backupCodes);
            
            // GDPR: Log sensitive data storage
            log.warn("GDPR: Operation=storeTotpSecret, FirebaseUID={}, DataStored=encrypted_totp_secret,backup_codes, Purpose=2fa_security, LegalBasis=consent, ThirdParty=firebase_firestore", firebaseUserId);
            
            // Use the improved QR code generation that works with all authenticator apps
            String qrCodeImage = improvedQRCodeService.generateQRCode(secret, user.getEmail());
            
            // Generate the otpauth URL for reference/debugging
            String qrCodeUrl = improvedQRCodeService.generateOtpAuthUrl(secret, user.getEmail());
            
            // Also generate external QR API URL as fallback option (QuickChart.io)
            String googleChartsUrl = improvedQRCodeService.generateGoogleChartsQRUrl(secret, user.getEmail());
            
            log.info("2FA setup initiated for user: {} with QR code image", firebaseUserId);
            
            // GDPR: Log successful 2FA setup
            log.info("GDPR: Operation=setupTwoFactor_success, FirebaseUID={}, DataGenerated=qr_code,backup_codes, Purpose=2fa_enrollment_complete", firebaseUserId);
            
            // Get manual entry info for users who can't scan
            ImprovedQRCodeService.ManualEntryInfo manualInfo = 
                improvedQRCodeService.getManualEntryInfo(secret, user.getEmail());
            
            return TotpSetupResponse.builder()
                .qrCodeUrl(qrCodeUrl)  // Keep the URL for compatibility
                .qrCodeImage(qrCodeImage)  // FIXED: QR code that actually works
                .googleChartsUrl(googleChartsUrl)  // Fallback option
                .backupCodes(backupCodes)
                .secret(secret) // For manual entry if QR doesn't work
                .secretFormatted(manualInfo.getSecret()) // Formatted with spaces
                .issuer(issuer)
                .email(user.getEmail())
                .build();
                
        } catch (Exception e) {
            log.error("GDPR: Operation=setupTwoFactor_failed, FirebaseUID={}, Error={}, Purpose=error_logging", firebaseUserId, e.getMessage());
            log.error("Failed to setup 2FA for user {}: {}", firebaseUserId, e.getMessage());
            throw new AuthenticationTranslatableException("error.auth.2fa_setup_failed");
        }
    }
    
    /**
     * Pure TOTP code verification without side effects.
     * Used by step-up authentication where audit logging and Firebase claim updates
     * are not needed (the step-up flow has its own token-based verification).
     *
     * @param firebaseUserId Firebase user ID
     * @param code The 6-digit TOTP code
     * @return true if valid, false otherwise
     */
    public boolean verifyTotpCodeOnly(String firebaseUserId, String code) {
        int codeInt = Integer.parseInt(String.format("%06d", Integer.parseInt(code)));

        if (!totpEnabled) {
            log.warn("TOTP verification attempted but TOTP is disabled");
            return false;
        }

        try {
            String secret = firestoreService.getTotpSecret(firebaseUserId);
            if (secret == null) {
                log.warn("No TOTP secret found for user: {}", firebaseUserId);
                return false;
            }

            return gAuth.authorize(secret, codeInt);
        } catch (Exception e) {
            log.error("Failed to verify TOTP code for user {}: {}", firebaseUserId, e.getMessage());
            return false;
        }
    }

    /**
     * Verify a TOTP code for a user with full audit logging and Firebase claim updates.
     * Used by the login 2FA flow.
     *
     * @param firebaseUserId Firebase user ID
     * @param code The 6-digit TOTP code
     * @return true if valid, false otherwise
     */
    public boolean verifyTotpCode(String firebaseUserId, String code) {
        // GDPR: Log verification attempt
        log.info("GDPR: Operation=verifyTotpCode_attempt, FirebaseUID={}, Purpose=2fa_verification", firebaseUserId);

        boolean valid = verifyTotpCodeOnly(firebaseUserId, code);

        // If TOTP is disabled or secret missing, verifyTotpCodeOnly returns false
        // without touching firestoreService — preserve that behavior for existing tests
        if (!totpEnabled) {
            return false;
        }

        // Log the attempt
        String ipAddress = getClientIp();
        String userAgent = getUserAgent();

        if (valid) {
            log.info("TOTP verification successful for user: {}", firebaseUserId);
            // GDPR: Log successful verification
            log.info("GDPR: Operation=verifyTotpCode_success, FirebaseUID={}, Purpose=2fa_verification_complete, IPAddress={}", firebaseUserId, ipAddress);
            firestoreService.logAuditEvent(firebaseUserId, "TOTP_VERIFY", "SUCCESS",
                ipAddress, userAgent);

            // CRITICAL: Add adminChallengeCompletedAt claim for ADMIN users
            try {
                // Get user to check if they're ADMIN
                com.google.firebase.auth.UserRecord userRecord = firebaseAuth.getUser(firebaseUserId);
                java.util.Map<String, Object> claims = userRecord.getCustomClaims();
                String role = (String) claims.get("role");

                if ("ADMIN".equals(role)) {
                    // Add the adminChallengeCompletedAt claim with current timestamp
                    java.util.Map<String, Object> updatedClaims = new java.util.HashMap<>(claims);
                    updatedClaims.put("adminChallengeCompletedAt", System.currentTimeMillis());

                    // Update Firebase claims
                    firebaseAuth.setCustomUserClaims(firebaseUserId, updatedClaims);

                    log.info("Successfully added adminChallengeCompletedAt claim for ADMIN user: {}", firebaseUserId);
                    log.info("GDPR: Operation=ADD_2FA_CHALLENGE_CLAIM, FirebaseUID={}, ClaimAdded=adminChallengeCompletedAt, Purpose=2fa_verification_state", firebaseUserId);
                }
            } catch (Exception e) {
                log.error("Failed to add adminChallengeCompletedAt claim: {}", e.getMessage(), e);
                // Don't fail the verification if claim update fails
                // The user has still successfully verified their TOTP
            }
        } else {
            log.warn("Invalid TOTP code for user: {}", firebaseUserId);
            // GDPR: Log failed verification
            log.warn("GDPR: Operation=verifyTotpCode_failed, FirebaseUID={}, Purpose=2fa_verification_failed, IPAddress={}", firebaseUserId, ipAddress);
            firestoreService.logAuditEvent(firebaseUserId, "TOTP_VERIFY", "FAILED",
                ipAddress, userAgent);
        }

        return valid;
    }
    
    /**
     * Enable 2FA after successful verification
     * @param firebaseUserId Firebase user ID
     * @param verificationCode The code to verify before enabling
     */
    public void enableTwoFactor(String firebaseUserId, String verificationCode) {
        log.info("Attempting to enable 2FA for user: {}", firebaseUserId);
        
        // GDPR: Log 2FA enable attempt
        log.info("GDPR: Operation=enableTwoFactor_attempt, FirebaseUID={}, Purpose=2fa_activation", firebaseUserId);
        
        // Verify the code first
        if (!verifyTotpCode(firebaseUserId, verificationCode)) {
            throw new ValidationTranslatableException("error.validation.failed");
        }
        
        // Enable 2FA
        firestoreService.enable2FA(firebaseUserId);
        
        log.info("2FA enabled successfully for user: {}", firebaseUserId);
        
        // GDPR: Log 2FA activation
        log.warn("GDPR: Operation=enableTwoFactor_success, FirebaseUID={}, DataModified=2fa_status, Purpose=account_security_enhanced, LegalBasis=consent", firebaseUserId);
    }
    
    /**
     * Disable 2FA for a user (requires admin action or account recovery)
     * @param firebaseUserId Firebase user ID
     */
    public void disableTwoFactor(String firebaseUserId) {
        log.info("Disabling 2FA for user: {}", firebaseUserId);
        
        // GDPR: Log 2FA disable operation
        log.warn("GDPR: Operation=disableTwoFactor_request, FirebaseUID={}, DataModified=2fa_status, Purpose=account_security_modification", firebaseUserId);
        
        firestoreService.disable2FA(firebaseUserId);
        
        log.info("2FA disabled for user: {}", firebaseUserId);
        
        // GDPR: Log successful disable
        log.warn("GDPR: Operation=disableTwoFactor_success, FirebaseUID={}, DataDeleted=totp_secret,backup_codes, Purpose=user_requested_removal", firebaseUserId);
    }
    
    /**
     * Verify a backup code
     * @param firebaseUserId Firebase user ID
     * @param backupCode The backup code to verify
     * @return true if valid and not used, false otherwise
     */
    public boolean verifyBackupCode(String firebaseUserId, String backupCode) {
        if (!totpEnabled) {
            return false;
        }
        
        // Normalize backup code (uppercase, no spaces)
        String normalizedCode = backupCode.toUpperCase().replaceAll("\\s", "");
        
        boolean valid = firestoreService.verifyBackupCode(firebaseUserId, normalizedCode);
        
        if (valid) {
            log.info("Backup code verified for user: {}", firebaseUserId);
            // GDPR: Log backup code usage
            log.warn("GDPR: Operation=verifyBackupCode_success, FirebaseUID={}, DataAccessed=backup_codes, Purpose=emergency_access", firebaseUserId);
        } else {
            log.warn("Invalid backup code attempt for user: {}", firebaseUserId);
            // GDPR: Log failed backup code attempt
            log.warn("GDPR: Operation=verifyBackupCode_failed, FirebaseUID={}, Purpose=emergency_access_failed", firebaseUserId);
        }
        
        return valid;
    }
    
    /**
     * Check if 2FA is enabled for a user
     * @param firebaseUserId Firebase user ID
     * @return true if enabled, false otherwise
     */
    public boolean is2FAEnabled(String firebaseUserId) {
        return totpEnabled && firestoreService.is2FAEnabled(firebaseUserId);
    }
    
    /**
     * Check if 2FA is required for a user (based on user type)
     * @param firebaseUserId Firebase user ID
     * @return true if required, false otherwise
     */
    public boolean is2FARequired(String firebaseUserId) {
        if (!totpEnabled) {
            return false;
        }
        
        User user = userService.findByFirebaseUserId(firebaseUserId);
        if (user == null) {
            return false;
        }
        
        // 2FA is mandatory for admin and pending admin users
        return UserType.ADMIN.equals(user.getUserType()) || UserType.PENDING_ADMIN.equals(user.getUserType());
    }
    
    /**
     * Generate a current TOTP code (for testing purposes)
     * @param secret The TOTP secret
     * @return Current 6-digit code
     */
    public int generateCurrentCode(String secret) {
        return gAuth.getTotpPassword(secret);
    }
    
    /**
     * Generate new backup codes for an existing 2FA user
     * @param firebaseUserId Firebase user ID
     * @return List of new backup codes
     */
    public List<String> regenerateBackupCodes(String firebaseUserId) {
        log.info("Regenerating backup codes for user: {}", firebaseUserId);
        
        // GDPR: Log backup codes regeneration
        log.warn("GDPR: Operation=regenerateBackupCodes_request, FirebaseUID={}, DataModified=backup_codes, Purpose=account_recovery_update", firebaseUserId);
        
        // Check if 2FA is enabled
        if (!firestoreService.is2FAEnabled(firebaseUserId)) {
            throw new BusinessRuleTranslatableException("error.business.invalid_state");
        }
        
        try {
            // Get existing secret (we need to keep the same secret)
            String secret = firestoreService.getTotpSecret(firebaseUserId);
            if (secret == null) {
                throw new BusinessRuleTranslatableException("error.business.invalid_state");
            }
            
            // Generate new backup codes
            List<String> backupCodes = generateBackupCodes();
            
            // Store updated backup codes (reusing the same secret)
            firestoreService.storeTotpSecret(firebaseUserId, secret, backupCodes);
            
            log.info("Backup codes regenerated for user: {}", firebaseUserId);
            
            // GDPR: Log successful regeneration
            log.warn("GDPR: Operation=regenerateBackupCodes_success, FirebaseUID={}, DataReplaced=backup_codes, RetentionPeriod=until_used, Purpose=account_recovery_updated", firebaseUserId);
            
            return backupCodes;
            
        } catch (Exception e) {
            log.error("GDPR: Operation=regenerateBackupCodes_failed, FirebaseUID={}, Error={}, Purpose=error_logging", firebaseUserId, e.getMessage());
            log.error("Failed to regenerate backup codes for user {}: {}", firebaseUserId, e.getMessage());
            throw new AuthenticationTranslatableException("error.auth.2fa_backup_codes_failed");
        }
    }
    
    /**
     * Generate backup codes - 10 codes of 8 alphanumeric characters
     * @return List of backup codes
     */
    private List<String> generateBackupCodes() {
        return IntStream.range(0, 10)
            .mapToObj(i -> RandomStringUtils.randomAlphanumeric(8).toUpperCase())
            .collect(Collectors.toList());
    }
    
    /**
     * Get client IP address from request
     * @return IP address or null
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) 
                RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            
            // Check for proxy headers
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            
            // If multiple IPs, take the first one
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            
            return ip;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get user agent from request
     * @return User agent string or null
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) 
                RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }
}
