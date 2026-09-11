package com.sm.instagram.platform.auth.service;

import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Improved QR Code Service using proven java-totp library.
 * This implementation ensures compatibility with all major authenticator apps:
 * - Google Authenticator
 * - Microsoft Authenticator  
 * - Authy
 * - 1Password
 * - LastPass Authenticator
 */
@Service
@Slf4j
public class ImprovedQRCodeService {
    
    @Value("${totp.issuer:CheckItOut}")
    private String issuer;
    
    @Value("${totp.digits:6}")
    private int digits;
    
    @Value("${totp.period:30}")
    private int period;
    
    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    
    public ImprovedQRCodeService() {
        this.secretGenerator = new DefaultSecretGenerator();
        this.qrGenerator = new ZxingPngQrGenerator();
    }
    
    /**
     * Generate a new TOTP secret (Base32 encoded).
     * @return Base32 encoded secret
     */
    public String generateSecret() {
        String secret = secretGenerator.generate();
        
        // Log secret generation without revealing the actual secret
        log.info("GDPR: Service=generateTOTPSecret, Operation=GENERATE_SECRET, Purpose=2fa_setup");
        
        return secret;
    }
    
    /**
     * Generate a new TOTP secret with user tracking.
     * @param firebaseUid Firebase UID of the user
     * @return Base32 encoded secret
     */
    public String generateSecret(String firebaseUid) {
        String secret = secretGenerator.generate();
        
        log.info("GDPR: Service=generateTOTPSecret, Operation=GENERATE_SECRET, FirebaseUID={}, Purpose=2fa_setup", 
            firebaseUid);
        
        return secret;
    }
    
    /**
     * Generate QR code image for TOTP setup.
     * This method creates a QR code that is 100% compatible with all authenticator apps.
     * 
     * @param secret Base32 encoded secret
     * @param email User's email address
     * @return Base64 encoded PNG image data URL
     */
    public String generateQRCode(String secret, String email) {
        try {
            // Build QR data with all required fields
            QrData qrData = new QrData.Builder()
                .label(email)                // User identifier
                .secret(secret)               // Base32 secret
                .issuer(issuer)              // App/Company name
                .algorithm(dev.samstevens.totp.code.HashingAlgorithm.SHA1) // SHA1 for max compatibility
                .digits(digits)              // 6 digits
                .period(period)              // 30 seconds
                .build();
            
            // Generate QR code as byte array
            byte[] imageData = qrGenerator.generate(qrData);
            
            // Convert to Base64 data URL
            String base64 = Base64.getEncoder().encodeToString(imageData);
            String dataUrl = "data:image/png;base64," + base64;
            
            log.info("GDPR: Service=generateQRCode, Operation=GENERATE_2FA_QR, Email={}, Issuer={}, Purpose=2fa_setup", 
                maskEmail(email), issuer);
            
            return dataUrl;
            
        } catch (Exception e) {
            log.error("GDPR: Service=generateQRCode, Operation=GENERATE_2FA_QR_FAILED, Email={}, Error={}", 
                maskEmail(email), e.getMessage(), e);
            throw new ValidationTranslatableException("error.validation.failed");
        }
    }
    
    /**
     * Generate the otpauth:// URL manually for debugging or fallback.
     * Format: otpauth://totp/LABEL?secret=SECRET&issuer=ISSUER
     * 
     * @param secret Base32 encoded secret
     * @param email User's email
     * @return otpauth:// URL string
     */
    public String generateOtpAuthUrl(String secret, String email) {
        try {
            // Create label (issuer:email format)
            String label = String.format("%s:%s", issuer, email);
            
            // URL encode components
            String encodedLabel = URLEncoder.encode(label, StandardCharsets.UTF_8);
            String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
            
            // Build otpauth URL according to spec
            String otpauthUrl = String.format(
                "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                encodedLabel,
                secret,
                encodedIssuer,
                digits,
                period
            );
            
            // NEVER the URL itself. `secret=` in it is the user's TOTP seed and the label is
            // their email address: anyone who can read this log could mint valid second factors
            // for that account for as long as the secret lives, and would know whose. What is
            // actually useful when this needs debugging is the shape, which carries neither.
            log.debug("Generated otpauth URL: issuer={}, digits={}, period={}, length={}",
                issuer, digits, period, otpauthUrl.length());
            
            return otpauthUrl;
            
        } catch (Exception e) {
            log.error("Failed to generate otpauth URL", e);
            throw new ValidationTranslatableException("error.validation.failed");
        }
    }
    
    /**
     * Alternative: Generate QR code using Google Charts API (100% reliable fallback).
     * This method uses Google's chart service which is guaranteed to work.
     * 
     * @param secret Base32 encoded secret  
     * @param email User's email
     * @return URL to Google Charts QR code image
     */
    public String generateGoogleChartsQRUrl(String secret, String email) {
        try {
            String otpauthUrl = generateOtpAuthUrl(secret, email);
            String encodedUrl = URLEncoder.encode(otpauthUrl, StandardCharsets.UTF_8);
            
            // Google Charts QR API - Updated endpoint
            // Note: Google Charts API is deprecated but still works
            // Alternative: Use quickchart.io or qr-server.com
            String googleQrUrl = String.format(
                "https://quickchart.io/qr?text=%s&size=300",
                encodedUrl
            );
            
            log.info("GDPR: Service=generateGoogleChartsQR, Operation=GENERATE_EXTERNAL_QR, Email={}, Purpose=2fa_fallback", 
                maskEmail(email));
            
            return googleQrUrl;
            
        } catch (Exception e) {
            log.error("GDPR: Service=generateGoogleChartsQR, Operation=GENERATE_EXTERNAL_QR_FAILED, Error={}", 
                e.getMessage(), e);
            throw new ValidationTranslatableException("error.validation.failed");
        }
    }
    
    /**
     * Validate that a secret is properly formatted.
     * 
     * @param secret The secret to validate
     * @return true if valid Base32 secret
     */
    public boolean isValidSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return false;
        }
        
        // Base32 only contains uppercase A-Z and digits 2-7
        // No 0, 1, 8, 9 to avoid confusion with O, I, B, G
        return secret.matches("^[A-Z2-7]+$");
    }
    
    /**
     * Generate manual entry information for users who can't scan QR codes.
     * 
     * @param secret Base32 encoded secret
     * @param email User's email
     * @return Manual entry info object
     */
    public ManualEntryInfo getManualEntryInfo(String secret, String email) {
        return ManualEntryInfo.builder()
            .accountName(email)
            .issuer(issuer)
            .secret(formatSecretForDisplay(secret))
            .secretRaw(secret)
            .type("Time based (TOTP)")
            .algorithm("SHA1")
            .digits(String.valueOf(digits))
            .period(period + " seconds")
            .build();
    }
    
    /**
     * Format secret for manual entry display (add spaces every 4 characters).
     * 
     * @param secret Raw secret
     * @return Formatted secret
     */
    private String formatSecretForDisplay(String secret) {
        if (secret == null || secret.length() <= 4) {
            return secret;
        }
        
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < secret.length(); i += 4) {
            if (i > 0) {
                formatted.append(" ");
            }
            int endIndex = Math.min(i + 4, secret.length());
            formatted.append(secret, i, endIndex);
        }
        
        return formatted.toString();
    }
    
    /**
     * Data class for manual entry information.
     */
    @lombok.Builder
    @lombok.Data
    public static class ManualEntryInfo {
        private String accountName;
        private String issuer;
        private String secret;       // Formatted with spaces
        private String secretRaw;    // Raw secret for copying
        private String type;
        private String algorithm;
        private String digits;
        private String period;
    }
    
    /**
     * Mask email for GDPR-compliant logging.
     * Shows first 2 chars and domain.
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 3) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***@***";
        }
        String localPart = email.substring(0, Math.min(2, atIndex));
        String domain = email.substring(atIndex);
        return localPart + "***" + domain;
    }
}
