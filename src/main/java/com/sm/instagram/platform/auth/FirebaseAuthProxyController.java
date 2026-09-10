package com.sm.instagram.platform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.annotation.RequiresRecaptcha;
import com.sm.instagram.platform.auth.dto.*;
import com.sm.instagram.platform.auth.service.PasswordResetService;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.auth.filter.HmacUtils;
import com.sm.instagram.platform.auth.service.FirebaseAuthProxyService;
import com.sm.instagram.platform.auth.stepup.StepUpActionType;
import com.sm.instagram.platform.auth.stepup.StepUpAuthService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Firebase Authentication Proxy Controller.
 * <p>
 * Proxies authentication requests from frontend to Firebase,
 * enabling centralized audit logging and rate limiting.
 * <p>
 * Flow: Frontend → This Proxy → Firebase Auth → Audit Log → Response
 *
 * @author CheckItOut Platform Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/auth/firebase")
@RequiredArgsConstructor
@RateLimit(profile = RateLimitProfile.AUTH)
public class FirebaseAuthProxyController {

    public static final String USER_AGENT = "User-Agent";
    public static final String OPERATION = "operation";
    public static final String CORRELATION_ID = "correlationId";
    public static final String EMAIL = "email";
    public static final String USER_NOT_AUTHENTICATED = "User not authenticated";
    private final FirebaseAuthProxyService firebaseAuthProxyService;
    private final ObjectMapper objectMapper;
    private final FirebaseAuth firebaseAuth;
    private final PasswordResetService passwordResetService;
    private final StepUpAuthService stepUpAuthService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${cookie.hmac.secret}")
    private String cookieHmacSecret;

    @Value("${jwt.cookie.domain:localhost}")
    private String cookieDomain;

    @Value("${jwt.cookie.secure:true}")
    private boolean secureCookies;

    /**
     * Proxy login requests to Firebase Authentication.
     *
     * @param request        Login credentials (email/password)
     * @param servletRequest HTTP request for IP extraction
     * @return Firebase authentication response with tokens
     */
    @PostMapping("/login")
    @RateLimit(profile = RateLimitProfile.AUTH, errorMessage = "error.auth.too_many_attempts")
    @RequiresRecaptcha(action = "LOGIN")
    public ResponseEntity<FirebaseAuthResponse> login(
            @Valid @RequestBody FirebaseAuthRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        // Validate required parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "email");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "password");
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "firebase.login");

        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(USER_AGENT);

        // GDPR: Log login attempt
        log.info("GDPR: Operation=firebaseLogin, Email={}, IP={}, DataAccessed=credentials, Purpose=authentication, LegalBasis=contract",
                maskEmail(request.getEmail()), maskIp(clientIp));

        log.info("Auth attempt - email: {}, ip: {}",
                maskEmail(request.getEmail()), maskIp(clientIp));

        try {
            FirebaseAuthResponse response = firebaseAuthProxyService.login(
                    request, clientIp, userAgent, correlationId
            );

            if (response.isSuccess() && response.getIdToken() != null) {
                // NEW: Set Firebase ID token as HttpOnly cookie with HMAC
                String idToken = response.getIdToken();

                // Generate HMAC signature
                String hmacSignature = HmacUtils.generateHMAC(idToken, cookieHmacSecret);

                // Set HttpOnly cookies (30 min expiry for exchange)
                setFirebaseIdTokenCookie(servletResponse, "FirebaseIdToken", idToken, 30);
                setFirebaseIdTokenCookie(servletResponse, "FirebaseIdToken_sig", hmacSignature, 30);

                // CRITICAL: For ADMIN users, remove adminChallengeCompletedAt claim
                if ("ADMIN".equals(response.getRole())) {
                    String firebaseUserId = response.getLocalId();
                    try {
                        // Get current claims
                        UserRecord userRecord = firebaseAuth.getUser(firebaseUserId);
                        Map<String, Object> currentClaims = userRecord.getCustomClaims();

                        // Remove the 2FA verification claim if it exists
                        if (currentClaims.containsKey("adminChallengeCompletedAt")) {
                            Map<String, Object> updatedClaims = new HashMap<>(currentClaims);
                            updatedClaims.remove("adminChallengeCompletedAt");

                            // Update Firebase claims
                            firebaseAuth.setCustomUserClaims(firebaseUserId, updatedClaims);

                            // CRITICAL: Verify removal with retry logic
                            int maxRetries = 3;
                            boolean claimRemoved = false;

                            for (int i = 0; i < maxRetries; i++) {
                                Thread.sleep(100); // Small delay for Firebase propagation
                                UserRecord verifyRecord = firebaseAuth.getUser(firebaseUserId);
                                Map<String, Object> verifyClaims = verifyRecord.getCustomClaims();

                                if (!verifyClaims.containsKey("adminChallengeCompletedAt")) {
                                    claimRemoved = true;
                                    log.info("Successfully removed adminChallengeCompletedAt claim for ADMIN user: {}",
                                            maskEmail(request.getEmail()));
                                    break;
                                }

                                if (i < maxRetries - 1) {
                                    // Retry the removal
                                    firebaseAuth.setCustomUserClaims(firebaseUserId, updatedClaims);
                                    log.warn("Retrying claim removal, attempt {} of {}", i + 2, maxRetries);
                                }
                            }

                            if (!claimRemoved) {
                                log.error("CRITICAL: Failed to remove adminChallengeCompletedAt claim after {} attempts", maxRetries);
                                throw new BusinessRuleTranslatableException("error.business.invalid_state");
                            }
                        }

                        // Set requires2FA to true for ADMIN
                        response.setRequires2FA(true);
                        response.setMessage("Authentication successful. 2FA verification required.");

                    } catch (BusinessRuleTranslatableException e) {
                        // Re-throw business rule exceptions
                        throw e;
                    } catch (Exception e) {
                        // A broad catch takes InterruptedException with it, and the interrupt flag goes
                        // too: a pool thread told to stop would carry on as if nothing had happened.
                        // Restore it, then handle the failure exactly as before.
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        log.error("Error handling ADMIN 2FA claim removal: {}", e.getMessage(), e);
                        throw new AuthenticationTranslatableException("error.auth.not_authenticated");
                    }
                } else if ("COMPANY".equals(response.getRole())) {
                    // Company users don't require 2FA
                    response.setRequires2FA(false);
                    response.setMessage("Authentication successful.");
                } else {
                    // Default: no 2FA required for other roles
                    response.setRequires2FA(false);
                    response.setMessage("Authentication successful.");
                }

                // IMPORTANT: Remove raw token from response for security
                response.setIdToken(null);
                response.setRefreshToken(null);  // Also remove refresh token
            }

            // GDPR: Log successful authentication
            if (response.isSuccess()) {
                log.info("GDPR: FirebaseLoginSuccess, Email={}, DataProcessed=authentication_tokens, Purpose=session_creation",
                        maskEmail(request.getEmail()));
            }

            log.info("Auth success - email: {}, duration: {}ms",
                    maskEmail(request.getEmail()),
                    response.getProcessingTime());

            return ResponseEntity.ok(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Proxy registration requests to Firebase Authentication.
     *
     * @param request        Registration credentials (email/password)
     * @param servletRequest HTTP request for IP extraction
     * @return Firebase authentication response with tokens
     */
    @PostMapping("/register")
    @RateLimit(profile = RateLimitProfile.AUTH)
    @RequiresRecaptcha(action = "SIGNUP")
    public ResponseEntity<FirebaseAuthResponse> register(
            @Valid @RequestBody FirebaseAuthRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        // Validate required parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "email");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "password");
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "firebase.register");

        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(USER_AGENT);

        // GDPR: Log registration attempt
        log.info("GDPR: Operation=firebaseRegister, Email={}, IP={}, DataAccessed=registration_data, Purpose=account_creation, LegalBasis=contract",
                maskEmail(request.getEmail()), maskIp(clientIp));

        log.info("Registration attempt - email: {}, ip: {}",
                maskEmail(request.getEmail()), maskIp(clientIp));

        try {
            FirebaseAuthResponse response = firebaseAuthProxyService.register(
                    request, clientIp, userAgent, correlationId
            );

            if (response.isSuccess() && response.getIdToken() != null) {
                // NEW: Set Firebase ID token as HttpOnly cookie with HMAC (same as login)
                String idToken = response.getIdToken();

                // Generate HMAC signature
                String hmacSignature = HmacUtils.generateHMAC(idToken, cookieHmacSecret);

                // Set HttpOnly cookies (30 min expiry for exchange)
                setFirebaseIdTokenCookie(servletResponse, "FirebaseIdToken", idToken, 30);
                setFirebaseIdTokenCookie(servletResponse, "FirebaseIdToken_sig", hmacSignature, 30);

                // IMPORTANT: Remove raw token from response for security
                response.setIdToken(null);
                response.setRefreshToken(null);  // Also remove refresh token

                // Add flag to indicate cookie was set
                response.setMessage("Registration successful. Token stored securely.");
            }

            // GDPR: Log successful registration
            if (response.isSuccess()) {
                log.info("GDPR: FirebaseRegistrationSuccess, Email={}, DataStored=user_profile,credentials, Purpose=account_creation",
                        maskEmail(request.getEmail()));
            }

            log.info("Registration success - email: {}, duration: {}ms",
                    maskEmail(request.getEmail()),
                    response.getProcessingTime());

            return ResponseEntity.ok(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Proxy token refresh requests to Firebase Authentication.
     *
     * @param request        Refresh token request
     * @param servletRequest HTTP request for IP extraction
     * @return New authentication tokens
     */
    @PostMapping("/refresh")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<FirebaseAuthResponse> refresh(
            @Valid @RequestBody FirebaseAuthRequest request,
            HttpServletRequest servletRequest) {

        // Validate required parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getRefreshToken() == null || request.getRefreshToken().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "refreshToken");
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "firebase.refresh");

        String clientIp = extractClientIp(servletRequest);

        log.debug("Token refresh attempt - ip: {}", maskIp(clientIp));

        try {
            FirebaseAuthResponse response = firebaseAuthProxyService.refreshToken(
                    request.getRefreshToken(), clientIp, correlationId
            );

            log.debug("Token refresh success - duration: {}ms",
                    response.getProcessingTime());

            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Extract client IP address from request.
     * Checks headers set by proxies/load balancers first.
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        // Handle comma-separated IPs (from proxy chain)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * Mask email for logging (first 3 chars + domain).
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 4 || !email.contains("@")) {
            return "***";
        }
        // VULN-002 FIX: Sanitize newlines and control characters to prevent log injection
        String sanitized = email.replaceAll("[\\n\\r\\t]", "_");
        String[] parts = sanitized.split("@");
        String local = parts[0].length() > 3 ?
                parts[0].substring(0, 3) + "***" : "***";
        return local + "@" + parts[1];
    }

    /**
     * Mask IP address for logging (last octet hidden).
     */
    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }
        // VULN-003 FIX: Sanitize newlines and control characters to prevent log injection
        String sanitized = ip.replaceAll("[\\n\\r\\t]", "_");

        if (sanitized.contains(".")) {
            // IPv4
            String[] parts = sanitized.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
            }
        } else if (sanitized.contains(":")) {
            // IPv6 - mask last segment
            int lastColon = sanitized.lastIndexOf(":");
            if (lastColon > 0) {
                return sanitized.substring(0, lastColon) + ":xxxx";
            }
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 10)) + "***";
    }

    /**
     * Change user password.
     *
     * @param request        Password change request with current and new password
     * @param servletRequest HTTP request for context
     * @return Operation response
     */
    @PostMapping("/change-password")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    public ResponseEntity<AuthOperationResponse> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest servletRequest) {

        // Validate required parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getCurrentPassword() == null || request.getCurrentPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "currentPassword");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "newPassword");
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "password.change");

        try {
            // Since /auth endpoints are public, manually extract JWT from cookie
            Claims claims = extractJwtClaims(servletRequest);
            if (claims == null) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            // Get user details from JWT claims
            String userId = claims.getSubject(); // Firebase UID
            String userEmail = claims.get(EMAIL, String.class);

            if (userId == null || userId.trim().isEmpty()) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }
            if (userEmail == null || userEmail.trim().isEmpty()) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            // Step-up authentication required for password change
            String stepUpToken = servletRequest.getHeader("X-Step-Up-Token");
            stepUpAuthService.validateTokenIfRequired(userId, StepUpActionType.PASSWORD_CHANGE, stepUpToken);

            // GDPR: Log password change request
            log.info("GDPR: Operation=changePassword, FirebaseUID={}, Email={}, DataAccessed=credentials, Purpose=security_update, LegalBasis=contract",
                    userId, maskEmail(userEmail));

            log.info("Password change request - user: {}", maskEmail(userEmail));

            // Call service to change password
            AuthOperationResponse response = firebaseAuthProxyService.changePassword(
                    userId, userEmail, request, correlationId
            );

            if (!response.isSuccess()) {
                throw new BusinessRuleTranslatableException("error.business.invalid_state");
            }

            log.info("Password changed successfully - user: {}", maskEmail(userEmail));
            return ResponseEntity.ok(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Re-authenticate user.
     *
     * @param request        Re-authentication request with password
     * @param servletRequest HTTP request for context
     * @return Authentication response with refreshed tokens
     */
    @PostMapping("/reauthenticate")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<FirebaseAuthResponse> reauthenticate(
            @Valid @RequestBody ReauthRequest request,
            HttpServletRequest servletRequest) {

        // Validate required parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "password");
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "reauthenticate");

        try {
            // Since /auth endpoints are public, manually extract JWT from cookie
            Claims claims = extractJwtClaims(servletRequest);
            if (claims == null) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            // Get user email from JWT claims
            String userEmail = claims.get(EMAIL, String.class);
            if (userEmail == null || userEmail.trim().isEmpty()) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            log.info("Re-authentication request - user: {}", maskEmail(userEmail));

            // Re-authenticate with email and password
            FirebaseAuthRequest authRequest = new FirebaseAuthRequest();
            authRequest.setEmail(userEmail);
            authRequest.setPassword(request.getPassword());

            String clientIp = extractClientIp(servletRequest);
            String userAgent = servletRequest.getHeader(USER_AGENT);

            FirebaseAuthResponse response = firebaseAuthProxyService.login(
                    authRequest, clientIp, userAgent, correlationId
            );

            log.info("Re-authentication success - user: {}", maskEmail(userEmail));
            return ResponseEntity.ok(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Delete user account.
     *
     * @param request        Account deletion request with password and optional reason
     * @param servletRequest HTTP request for context
     * @return Operation response
     */
    @DeleteMapping("/delete-account")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<AuthOperationResponse> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest servletRequest) {

        // Validate required parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "password");
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "account.delete");

        try {
            // Since /auth endpoints are public, manually extract JWT from cookie
            Claims claims = extractJwtClaims(servletRequest);
            if (claims == null) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            // Get user details from JWT claims
            String userId = claims.getSubject(); // Firebase UID
            String userEmail = claims.get(EMAIL, String.class);

            if (userId == null || userId.trim().isEmpty()) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }
            if (userEmail == null || userEmail.trim().isEmpty()) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            // GDPR: Log account deletion request - CRITICAL for GDPR compliance
            log.warn("GDPR: DELETION_REQUEST Operation=deleteAccount, FirebaseUID={}, Email={}, Reason={}, DataAccessed=all_user_data, Purpose=user_requested_deletion, LegalBasis=data_subject_request",
                    userId, maskEmail(userEmail), request.getReason());

            log.info("Account deletion request - user: {}, reason: {}",
                    maskEmail(userEmail), request.getReason());

            // Verify password before deletion
            FirebaseAuthRequest authRequest = new FirebaseAuthRequest();
            authRequest.setEmail(userEmail);
            authRequest.setPassword(request.getPassword());

            String clientIp = extractClientIp(servletRequest);

            // Call service to delete account
            AuthOperationResponse response = firebaseAuthProxyService.deleteAccount(
                    userId, userEmail, authRequest, request.getReason(), clientIp, correlationId
            );

            if (!response.isSuccess()) {
                throw new BusinessRuleTranslatableException("error.business.invalid_state");
            }

            // GDPR: Log successful deletion - CRITICAL for GDPR compliance
            log.warn("GDPR: DELETION_COMPLETE FirebaseUID={}, Email={}, AllDataRemoved=true, Permanent=true",
                    userId, maskEmail(userEmail));
            log.info("Account deleted successfully - user: {}", maskEmail(userEmail));
            return ResponseEntity.ok(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Update user email address.
     *
     * @param request        Email update request with new email and password
     * @param servletRequest HTTP request for context
     * @return Operation response
     */
    @PostMapping("/update-email")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<AuthOperationResponse> updateEmail(
            @Valid @RequestBody EmailUpdateRequest request,
            HttpServletRequest servletRequest) {

        // Validate required parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getNewEmail() == null || request.getNewEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "newEmail");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "password");
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "email.update");

        try {
            // Since /auth endpoints are public, manually extract JWT from cookie
            Claims claims = extractJwtClaims(servletRequest);
            if (claims == null) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            // Get user details from JWT claims
            String userId = claims.getSubject(); // Firebase UID
            String currentEmail = claims.get(EMAIL, String.class);

            if (userId == null || userId.trim().isEmpty()) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }
            if (currentEmail == null || currentEmail.trim().isEmpty()) {
                throw new AuthenticationTranslatableException("error.auth.not_authenticated");
            }

            // GDPR: Log email update request
            log.info("GDPR: Operation=updateEmail, FirebaseUID={}, OldEmail={}, NewEmail={}, DataAccessed=email_address, Purpose=account_update, LegalBasis=contract",
                    userId, maskEmail(currentEmail), maskEmail(request.getNewEmail()));

            log.info("Email update request - from: {}, to: {}",
                    maskEmail(currentEmail), maskEmail(request.getNewEmail()));

            // Call service to update email
            AuthOperationResponse response = firebaseAuthProxyService.updateEmail(
                    userId, currentEmail, request, correlationId
            );

            if (!response.isSuccess()) {
                throw new BusinessRuleTranslatableException("error.business.invalid_state");
            }

            log.info("Email updated successfully - user: {}", userId);
            return ResponseEntity.ok(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Request password reset.
     * Sends password reset email if user is verified, otherwise sends verification email.
     *
     * @param request        Forgot password request containing email
     * @param language       Accept-Language header for email localization
     * @return Response indicating what action was taken
     */
    @PostMapping("/forgot-password")
    // BUG FIX #10: Changed from IP_ONLY to IP_ENDPOINT to prevent email enumeration via rate limit bypass
    // IP_ONLY allows attacker to test many emails from one IP; IP_ENDPOINT ties rate limit to endpoint
    @RateLimit(profile = RateLimitProfile.AUTH, keyType = RateLimitKeyType.IP_ENDPOINT, errorMessage = "error.ratelimit.password_reset")
    @RequiresRecaptcha(action = "PASSWORD_RESET")  // BUG FIX #19: Consistent naming (LOGIN, SIGNUP, PASSWORD_RESET)
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest,  // BUG FIX #9: Added for client IP extraction (GDPR compliance)
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String language) {

        // BUG FIX #13: Add explicit request validation like other endpoints
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "email");
        }

        // BUG FIX #20 + MEDIUM-001: Sanitize language header (max 10 chars, no newlines)
        String sanitizedLanguage = language != null
            ? language.substring(0, Math.min(language.length(), 10)).replaceAll("[\\n\\r\\t]", "_")
            : "en";

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        MDC.put(OPERATION, "password.forgot");

        // BUG FIX #9: Extract client IP for GDPR logging
        String clientIp = extractClientIp(servletRequest);

        try {
            // GDPR: Log password reset request with client IP
            log.info("GDPR: Operation=forgotPassword, Email={}, IP={}, DataAccessed=email.address, Purpose=password_reset, LegalBasis=contract",
                    maskEmail(request.getEmail()), maskIp(clientIp));

            log.info("Password reset request - email: {}, ip: {}", maskEmail(request.getEmail()), maskIp(clientIp));

            ForgotPasswordResponse response = passwordResetService.requestPasswordReset(
                    request.getEmail(), sanitizedLanguage);  // BUG FIX #20: Use sanitized language

            // BUG FIX #1: 'action' field removed to prevent email enumeration
            log.info("Password reset request processed - email: {}, success: {}",
                    maskEmail(request.getEmail()), response.isSuccess());

            return ResponseEntity.ok(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * Apply an email verification action code.
     * Used by the frontend when user clicks the verification link in their email.
     *
     * @param request Contains the oobCode from the email verification link
     * @return Operation response indicating success
     */
    @PostMapping("/apply-action-code")
    @RateLimit(profile = RateLimitProfile.AUTH, keyType = RateLimitKeyType.IP_ENDPOINT)
    public ResponseEntity<AuthOperationResponse> applyActionCode(
            @Valid @RequestBody ApplyActionCodeRequest request) {
        return ResponseEntity.ok(firebaseAuthProxyService.applyActionCode(request.getOobCode()));
    }

    /**
     * Verify a password reset code.
     * Used by the frontend to validate the oobCode before showing the password reset form.
     *
     * @param request Contains the oobCode from the password reset link
     * @return Operation response with the user's email
     */
    @PostMapping("/verify-reset-code")
    @RateLimit(profile = RateLimitProfile.AUTH, keyType = RateLimitKeyType.IP_ENDPOINT)
    public ResponseEntity<AuthOperationResponse> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeRequest request) {
        return ResponseEntity.ok(firebaseAuthProxyService.verifyResetCode(request.getOobCode()));
    }

    /**
     * Complete email verification and optionally set password.
     * For INFLUENCER users: verifies email + sets password in one atomic operation.
     * For COMPANY/ADMIN users: verifies email only (they already have passwords).
     *
     * @param request Contains oobCode (required) and password (optional, for influencers)
     * @return Operation response with email and userType
     */
    @PostMapping("/complete-verification")
    @RateLimit(profile = RateLimitProfile.AUTH, keyType = RateLimitKeyType.IP_ENDPOINT)
    public ResponseEntity<AuthOperationResponse> completeVerification(
            @Valid @RequestBody CompleteVerificationRequest request) {
        return ResponseEntity.ok(firebaseAuthProxyService.completeVerification(
                request.getOobCode(), request.getPassword()));
    }

    /**
     * Confirm a password reset with a new password.
     * Used by the frontend after the user enters their new password.
     *
     * @param request Contains the oobCode and the new password
     * @return Operation response indicating success
     */
    @PostMapping("/confirm-password-reset")
    @RateLimit(profile = RateLimitProfile.AUTH, keyType = RateLimitKeyType.IP_ENDPOINT)
    public ResponseEntity<AuthOperationResponse> confirmPasswordReset(
            @Valid @RequestBody ConfirmPasswordResetRequest request) {
        return ResponseEntity.ok(firebaseAuthProxyService.confirmPasswordReset(
                request.getOobCode(), request.getNewPassword()));
    }

    /**
     * Extract JWT claims from session cookie.
     * Since /auth endpoints are public, we need to manually validate the JWT.
     */
    private Claims extractJwtClaims(HttpServletRequest request) {
        try {
            // Get session and signature cookies
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                log.warn("No cookies found in request");
                return null;
            }

            String token = null;
            String signature = null;
            for (Cookie cookie : cookies) {
                if ("session".equals(cookie.getName())) {
                    token = cookie.getValue();
                } else if ("session_sig".equals(cookie.getName())) {
                    signature = cookie.getValue();
                }
            }

            if (token == null) {
                log.warn("No session cookie found");
                return null;
            }

            // Validate HMAC signature (defense-in-depth, matches JwtAuthenticationFilter)
            if (signature == null || !HmacUtils.validateHMAC(token, signature, cookieHmacSecret)) {
                log.warn("SECURITY: Invalid or missing session_sig for extractJwtClaims");
                return null;
            }

            // Parse and validate JWT
            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

        } catch (Exception e) {
            log.error("Failed to extract JWT claims: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to set Firebase token cookies.
     */
    private void setFirebaseIdTokenCookie(HttpServletResponse response,
                                          String name,
                                          String value,
                                          int maxAgeMinutes) {
        String sameSite = "Strict";  // Strict for same-site security
        String domain = "";

        if (!"localhost".equals(cookieDomain) && cookieDomain != null) {
            domain = String.format("; Domain=%s", cookieDomain);
        }

        String secureFlag = secureCookies ? "; Secure" : "";

        String cookieString = String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly%s; SameSite=%s%s",
                name,
                value,
                maxAgeMinutes * 60,  // Convert to seconds
                secureFlag,
                sameSite,
                domain
        );

        response.addHeader("Set-Cookie", cookieString);
        // Cookie set: {name}
    }
}
