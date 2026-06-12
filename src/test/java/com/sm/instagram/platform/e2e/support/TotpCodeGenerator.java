package com.sm.instagram.platform.e2e.support;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder;
import com.warrenstrange.googleauth.KeyRepresentation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TOTP code generator for E2E tests.
 * Generates valid TOTP codes for admin 2FA authentication testing.
 *
 * <p>This class uses the same GoogleAuthenticator library and configuration
 * as the production TwoFactorAuthService to ensure compatibility.
 *
 * <p>For E2E tests, you can either:
 * <ol>
 *   <li>Use a known test secret (configured via e2e.admin.totp-secret)</li>
 *   <li>Access KMS to decrypt the real admin's TOTP secret</li>
 * </ol>
 */
@Slf4j
@Component
public class TotpCodeGenerator {

    private final GoogleAuthenticator gAuth;

    @Value("${e2e.admin.totp-secret:}")
    private String adminTotpSecret;

    @Value("${e2e.admin.firebase-uid:E2E_ADMIN_001}")
    private String adminFirebaseUid;

    /**
     * Creates TotpCodeGenerator with matching production configuration.
     */
    public TotpCodeGenerator(
            @Value("${totp.window-size:1}") int windowSize,
            @Value("${totp.code-digits:6}") int codeDigits,
            @Value("${totp.time-step-seconds:30}") int timeStepSeconds) {

        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfigBuilder()
            .setWindowSize(windowSize)
            .setCodeDigits(codeDigits)
            .setTimeStepSizeInMillis(timeStepSeconds * 1000L)
            .setKeyRepresentation(KeyRepresentation.BASE32)
            .build();

        this.gAuth = new GoogleAuthenticator(config);

        log.info("[E2E] TotpCodeGenerator initialized with windowSize={}, codeDigits={}, timeStep={}s",
                windowSize, codeDigits, timeStepSeconds);
    }

    /**
     * Generates a valid TOTP code for the admin user.
     *
     * @return 6-digit TOTP code
     * @throws IllegalStateException if admin TOTP secret is not configured
     */
    public int generateAdminTotpCode() {
        if (adminTotpSecret == null || adminTotpSecret.isBlank()) {
            throw new IllegalStateException(
                "Admin TOTP secret not configured. Set e2e.admin.totp-secret or E2E_ADMIN_TOTP_SECRET environment variable.");
        }

        int code = gAuth.getTotpPassword(adminTotpSecret);
        log.info("[E2E] Generated TOTP code for admin (secret masked)");
        return code;
    }

    /**
     * Generates a TOTP code for a given secret.
     *
     * @param secret Base32-encoded TOTP secret
     * @return 6-digit TOTP code
     */
    public int generateTotpCode(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("TOTP secret cannot be null or blank");
        }

        return gAuth.getTotpPassword(secret);
    }

    /**
     * Validates a TOTP code against a secret.
     *
     * @param secret Base32-encoded TOTP secret
     * @param code TOTP code to validate
     * @return true if code is valid
     */
    public boolean validateCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }

    /**
     * Validates a TOTP code for the admin user.
     *
     * @param code TOTP code to validate
     * @return true if code is valid
     */
    public boolean validateAdminCode(int code) {
        if (adminTotpSecret == null || adminTotpSecret.isBlank()) {
            throw new IllegalStateException("Admin TOTP secret not configured");
        }

        return gAuth.authorize(adminTotpSecret, code);
    }

    /**
     * Checks if admin TOTP is configured.
     *
     * @return true if admin TOTP secret is available
     */
    public boolean adminHasTotpConfigured() {
        return adminTotpSecret != null && !adminTotpSecret.isBlank();
    }

    /**
     * Gets the configured admin Firebase UID.
     *
     * @return Admin Firebase UID
     */
    public String getAdminFirebaseUid() {
        return adminFirebaseUid;
    }

    /**
     * Generates a new random TOTP secret.
     * Useful for creating test users with 2FA.
     *
     * @return Base32-encoded secret
     */
    public String generateNewSecret() {
        return gAuth.createCredentials().getKey();
    }
}
