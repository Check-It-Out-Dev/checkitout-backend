package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The bodies {@code /twofactor/**} actually sends.
 *
 * <p>Six handlers returned {@code ResponseEntity<?>} around a {@code Map}, so the document could
 * publish nothing but {@code type: object} and the generated client typed every one of them
 * {@code any} -- on the endpoints an administrator's access depends on.
 *
 * <p>Nothing secret is published by describing them. The only response carrying a TOTP secret is
 * {@code POST /twofactor/setup}, which already returns {@link TotpSetupResponse}; it was invisible
 * in the contract only because the method's own signature said {@code ?}. What the rest carry is an
 * outcome and a role, which every authenticated caller of these endpoints already sees.
 *
 * <p>{@code warning} and {@code integrityIssue} on the status response are present only when the
 * server finds a role and a TOTP document disagreeing, so they are nullable, and the
 * {@code NON_NULL} serializer drops them exactly as the map did by never putting the key.
 */
public final class TwoFactorResponses {

    private TwoFactorResponses() {
    }

    @Schema(description = "Whether this account has two-factor authentication, and whether it needs it")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Status(
            @Schema(example = "ADMIN") String role,
            boolean has2FA,
            boolean requires2FASetup,
            boolean canAccessAdmin,
            @Schema(description = "Present only when the role and the TOTP document disagree")
            String warning,
            @Schema(description = "Which disagreement, when there is one",
                    allowableValues = {"role_not_upgraded", "totp_not_enabled", "no_totp_document"})
            String integrityIssue) {

        public static Status of(String role, boolean has2FA) {
            return new Status(role, has2FA, "PENDING_ADMIN".equals(role), "ADMIN".equals(role), null, null);
        }

        public Status withIntegrityIssue(String warning, String issue) {
            return new Status(role, has2FA, requires2FASetup, canAccessAdmin, warning, issue);
        }
    }

    @Schema(description = "Two-factor authentication is now on, and the session already reflects it")
    public record SetupVerified(
            boolean success,
            String message,
            @Schema(example = "ADMIN") String newRole,
            boolean requiresRelogin,
            boolean autoLoggedIn,
            boolean twoFactorVerified,
            boolean canAccessAdmin) {
    }

    @Schema(description = "A code accepted at sign-in")
    public record Verified(
            boolean success,
            @Schema(description = "The original ID token is still the one to use; no new token is issued")
            boolean reuseIdToken,
            boolean verified,
            String message,
            boolean twoFactorVerified,
            boolean canAccessAdmin) {
    }

    @Schema(description = "Two-factor authentication is off, and the role has come back down with it")
    public record Disabled(
            boolean success,
            String message,
            @Schema(example = "PENDING_ADMIN") String newRole,
            boolean requiresRelogin) {
    }

    /**
     * The codes are returned once, here, and are not retrievable afterwards -- which is why this
     * response is the only place they appear and why the message says to save them.
     */
    @Schema(description = "A fresh set of single-use backup codes")
    public record BackupCodes(
            boolean success,
            List<String> backupCodes,
            String message) {
    }
}
