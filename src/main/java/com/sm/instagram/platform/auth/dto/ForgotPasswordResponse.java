package com.sm.instagram.platform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for forgot password endpoint.
 * Contains action indicator for frontend to display appropriate message.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordResponse {

    /**
     * Whether the request was processed successfully.
     * Always true for security (prevents email enumeration).
     */
    private boolean success;

    /**
     * i18n key for the frontend to display the appropriate message.
     * Always the same to prevent email enumeration attacks.
     */
    private String messageKey;

    /**
     * Factory method for password reset response.
     * SECURITY: Always returns identical response regardless of user state
     * (user exists/doesn't exist, verified/unverified) to prevent email enumeration.
     *
     * Backend still performs the correct action internally:
     * - User not found: No email sent (silent fail)
     * - User unverified: Verification email sent
     * - User verified: Password reset email sent
     *
     * But response is always identical to prevent attackers from determining
     * which scenario occurred.
     */
    public static ForgotPasswordResponse success() {
        return ForgotPasswordResponse.builder()
                .success(true)
                .messageKey("auth.forgot_password.success_message")
                .build();
    }
}
