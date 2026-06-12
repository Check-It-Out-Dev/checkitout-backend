package com.sm.instagram.platform.auth.filter;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utility class for HMAC operations.
 * Provides secure HMAC generation and constant-time comparison.
 */
@Slf4j
public class HmacUtils {
    
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    /**
     * Generate HMAC signature for given data.
     * 
     * @param data Data to sign
     * @param secret Secret key
     * @return Base64-encoded HMAC signature
     */
    public static String generateHMAC(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), 
                HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
            
        } catch (NoSuchAlgorithmException e) {
            // Should never happen - HmacSHA256 is guaranteed in Java SE
            log.error("HMAC algorithm not available (should be impossible)", e);
            throw new IllegalStateException("HmacSHA256 not available", e);
        } catch (InvalidKeyException e) {
            log.error("Invalid HMAC key provided", e);
            throw new com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException("error.auth.invalid_hmac_key");
        }
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        
        // Use MessageDigest.isEqual for constant-time comparison
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
    
    /**
     * Validate HMAC signature.
     * 
     * @param data Original data
     * @param signature Signature to validate
     * @param secret Secret key
     * @return true if signature is valid
     */
    public static boolean validateHMAC(String data, String signature, String secret) {
        String expectedSignature = generateHMAC(data, secret);
        return constantTimeEquals(signature, expectedSignature);
    }
}
