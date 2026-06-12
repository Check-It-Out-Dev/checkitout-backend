package com.sm.instagram.platform.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.dto.*;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.common.ratelimit.RateLimiterService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Authentication Proxy Service.
 * <p>
 * Handles HTTP communication with Firebase Auth REST API.
 * Implements audit logging for all authentication attempts.
 * <p>
 * Firebase Web API endpoints:
 * - Login: POST /accounts:signInWithPassword
 * - Register: POST /accounts:signUp
 * - Refresh: POST /token (different base URL)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseAuthProxyService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FirebaseAuth firebaseAuth;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private TotpFirestoreService totpFirestoreService;

    @Autowired
    private AdminIntegrityChecker adminIntegrityChecker;

    @Autowired
    private GoogleCredentialsProvider credentialsProvider;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private UserCacheService userCacheService;

    @Autowired
    private EmailChangeService emailChangeService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private com.sm.instagram.platform.common.authorization.PermissionUtils permissionUtils;

    @Autowired
    private com.sm.instagram.platform.registry.CompanyDataRepository companyDataRepository;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final String IDENTITY_TOOLKIT_BASE = "https://identitytoolkit.googleapis.com/v1";
    private static final String SECURE_TOKEN_BASE = "https://securetoken.googleapis.com/v1";

    /**
     * OAuth scope for Firebase Identity Toolkit API.
     */
    private static final String IDENTITY_TOOLKIT_SCOPE = "https://www.googleapis.com/auth/identitytoolkit";

    /**
     * Get OAuth access token for Firebase Identity Toolkit API calls.
     * Uses the service account credentials via GoogleCredentialsProvider.
     *
     * @return Fresh OAuth access token
     */
    private String getAccessToken() {
        try {
            GoogleCredentials credentials = credentialsProvider.getCredentialsWithScopes(IDENTITY_TOOLKIT_SCOPE);
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            log.error("Failed to obtain OAuth access token for Firebase Identity Toolkit", e);
            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Proxy login request to Firebase.
     *
     * @param request       Login credentials
     * @param clientIp      Client IP for audit logging
     * @param userAgent     User agent for audit logging
     * @param correlationId Request correlation ID
     * @return Firebase authentication response
     */
    public FirebaseAuthResponse login(
            FirebaseAuthRequest request,
            String clientIp,
            String userAgent,
            String correlationId) {

        // Validate input parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "email");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "password");
        }

        long startTime = System.currentTimeMillis();

        String url = IDENTITY_TOOLKIT_BASE + "/accounts:signInWithPassword";

        Map<String, Object> firebaseRequest = new HashMap<>();
        firebaseRequest.put("email", request.getEmail());
        firebaseRequest.put("password", request.getPassword());
        firebaseRequest.put("returnSecureToken", true);

        // GDPR: Log login attempt
        log.info("GDPR: Service=firebaseLogin, Email={}, IP={}, DataAccessed=credentials,firebase_auth, Purpose=authentication, LegalBasis=contract",
                maskEmail(request.getEmail()), clientIp);

        // Audit log - attempt (never log password)
        log.info("AUTH_ATTEMPT event=\"login_attempt\" request_id=\"{}\" email=\"{}\" ip=\"{}\" user_agent=\"{}\"",
                MDC.get("REQUEST_ID"), maskEmail(request.getEmail()), clientIp, userAgent);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(firebaseRequest, headers);

            ResponseEntity<FirebaseAuthResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, FirebaseAuthResponse.class
            );

            FirebaseAuthResponse authResponse = response.getBody();
            if (authResponse != null) {
                long duration = System.currentTimeMillis() - startTime;
                authResponse.setProcessingTime(duration);
                authResponse.setCorrelationId(correlationId);
                authResponse.setSuccess(true);  // Explicit success flag for login

                // Check Firebase custom claims to determine if 2FA is required
                String firebaseUserId = authResponse.getLocalId();

                try {
                    // Get user record from Firebase to check custom claims
                    UserRecord userRecord = firebaseAuth.getUser(firebaseUserId);
                    Map<String, Object> claims = userRecord.getCustomClaims();
                    String role = (String) claims.get("role");

                    log.info("User login - Firebase UID: {}, Role: {}", firebaseUserId, role);

                    // NEW: Run integrity check for admin users
                    if ("ADMIN".equals(role) || "PENDING_ADMIN".equals(role)) {
                        boolean integrityPassed = adminIntegrityChecker.checkAndSyncAdminIntegrity(
                                firebaseUserId,
                                request.getEmail()
                        );

                        if (!integrityPassed) {
                            // Re-fetch claims after potential fix
                            userRecord = firebaseAuth.getUser(firebaseUserId);
                            claims = userRecord.getCustomClaims();
                            role = (String) claims.get("role");
                            log.info("Role adjusted after integrity check to: {}", role);
                        }
                    }

                    if ("PENDING_ADMIN".equals(role)) {
                        // Admin without 2FA - allow login but flag for setup
                        authResponse.setRequires2FASetup(true);
                        authResponse.setRole("PENDING_ADMIN");
                        authResponse.setMessage("2FA setup required for full admin access");
                        // Keep the idToken so frontend can exchange it for session cookies
                        // authResponse.setIdToken(authResponse.getIdToken()); // Already set from Firebase
                        log.info("PENDING_ADMIN login - 2FA setup required: {}", maskEmail(request.getEmail()));

                    } else if ("ADMIN".equals(role)) {
                        // CRITICAL: Reset 2FA verification on each fresh login
                        // This ensures 2FA is required for every new session
                        try {
                            Map<String, Object> resetClaims = new HashMap<>(claims);
                            resetClaims.put("role", "ADMIN");
                            resetClaims.put("twoFactorVerified", false);  // ALWAYS reset on login
                            resetClaims.put("loginTimestamp", System.currentTimeMillis());
                            resetClaims.remove("verifiedAt");  // Clear old verification

                            // Update Firebase to require fresh 2FA
                            firebaseAuth.setCustomUserClaims(firebaseUserId, resetClaims);
                            log.info("Reset 2FA state for fresh ADMIN login - will require verification");
                        } catch (Exception e) {
                            log.error("Failed to reset 2FA state", e);
                        }

                        // Full admin with 2FA configured - require verification
                        authResponse.setRequires2FA(true);
                        authResponse.setRole("ADMIN");
                        authResponse.setMessage("2FA verification required");
                        // KEEP the idToken so frontend can exchange it for partial session cookies!
                        // The exchange-token endpoint will create a 10-minute partial auth token
                        // authResponse.setIdToken(authResponse.getIdToken()); // Already set from Firebase
                        // Clear refresh token to prevent full session creation
                        authResponse.setRefreshToken(null);
                        // Keep the Firebase UID so frontend can use it for 2FA validation
                        log.info("ADMIN login requires 2FA verification: {}", maskEmail(request.getEmail()));

                    } else {
                        // Regular user - no 2FA required
                        authResponse.setRole(role != null ? role : "USER");
                        log.info("Regular user login successful: {}", maskEmail(request.getEmail()));
                    }

                } catch (Exception e) {
                    // If we can't get custom claims, proceed as regular user
                    log.warn("Could not retrieve custom claims for user: {} - proceeding as regular user",
                            firebaseUserId, e);
                    authResponse.setRole("USER");
                }

                // GDPR: Log successful authentication with Firebase UID
                log.info("GDPR: LoginSuccess, FirebaseUID={}, Email={}, Role={}, DataProcessed=authentication_tokens,custom_claims, Purpose=session_creation",
                        authResponse.getLocalId(), maskEmail(request.getEmail()), authResponse.getRole());

                // AUDIT LOG - SUCCESS: Log Firebase UID and masked email
                log.info("AUTH_SUCCESS event=\"successful_login\" request_id=\"{}\" firebase_uid=\"{}\" " +
                                "email=\"{}\" ip=\"{}\" role=\"{}\" execution_time_ms={} requires_2fa={}",
                        MDC.get("REQUEST_ID"), authResponse.getLocalId(),
                        maskEmail(request.getEmail()), clientIp,
                        authResponse.getRole(), duration,
                        authResponse.isRequires2FA() || authResponse.isRequires2FASetup());
            }

            return authResponse;

        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorCode = extractFirebaseErrorCode(e.getResponseBodyAsString());

            // AUDIT LOG - FAILURE: Log FULL email for security (protect users from attacks)
            // GDPR: Legitimate interest - security monitoring and fraud prevention
            log.error("AUTH_FAILED event=\"failed_login\" request_id=\"{}\" " +
                            "email=\"{}\" ip=\"{}\" user_agent=\"{}\" error_code=\"{}\" " +
                            "execution_time_ms={}",
                    MDC.get("REQUEST_ID"),
                    request.getEmail(),  // FULL EMAIL - NOT MASKED for security
                    clientIp, userAgent, errorCode, duration);

            // Handle failed login with differentiated rate limiting
            handleFailedLogin(request, clientIp, e);

            // Return specific error based on Firebase error code
            if (errorCode != null) {
                if (errorCode.contains("INVALID_PASSWORD") || errorCode.contains("EMAIL_NOT_FOUND")) {
                    throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
                } else if (errorCode.contains("USER_DISABLED")) {
                    throw new AuthenticationTranslatableException("error.auth.account_disabled");
                } else if (errorCode.contains("TOO_MANY_ATTEMPTS_TRY_LATER")) {
                    throw new AuthenticationTranslatableException("error.auth.service_unavailable");
                }
            }

            // Generic authentication error
            throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
        }
    }

    /**
     * Proxy registration request to Firebase.
     *
     * @param request       Registration credentials
     * @param clientIp      Client IP for audit logging
     * @param userAgent     User agent for audit logging
     * @param correlationId Request correlation ID
     * @return Firebase authentication response
     */
    public FirebaseAuthResponse register(
            FirebaseAuthRequest request,
            String clientIp,
            String userAgent,
            String correlationId) {

        // Validate input parameters
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "email");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "password");
        }

        long startTime = System.currentTimeMillis();

        String url = IDENTITY_TOOLKIT_BASE + "/accounts:signUp";

        Map<String, Object> firebaseRequest = new HashMap<>();
        firebaseRequest.put("email", request.getEmail());
        firebaseRequest.put("password", request.getPassword());
        firebaseRequest.put("returnSecureToken", true);

        // GDPR: Log registration attempt
        log.info("GDPR: Service=firebaseRegister, Email={}, IP={}, DataAccessed=registration_data, Purpose=account_creation, LegalBasis=contract",
                maskEmail(request.getEmail()), clientIp);

        // Audit log - attempt
        log.info("REGISTER_ATTEMPT event=\"registration_attempt\" request_id=\"{}\" email=\"{}\" ip=\"{}\" user_agent=\"{}\"",
                MDC.get("REQUEST_ID"), maskEmail(request.getEmail()), clientIp, userAgent);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(firebaseRequest, headers);

            ResponseEntity<FirebaseAuthResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, FirebaseAuthResponse.class
            );

            FirebaseAuthResponse authResponse = response.getBody();
            if (authResponse != null) {
                long duration = System.currentTimeMillis() - startTime;
                authResponse.setProcessingTime(duration);
                authResponse.setCorrelationId(correlationId);

                // IMPORTANT: Set flags for successful registration
                authResponse.setSuccess(true);  // Explicit success flag
                authResponse.setRegistered(true);  // User is now registered

                // GDPR: Log successful registration with Firebase UID
                log.info("GDPR: RegistrationSuccess, FirebaseUID={}, Email={}, DataStored=user_profile,credentials, Purpose=account_creation",
                        authResponse.getLocalId(), maskEmail(request.getEmail()));

                // AUDIT LOG - SUCCESS: Log Firebase UID and masked email
                log.info("REGISTER_SUCCESS event=\"successful_registration\" request_id=\"{}\" firebase_uid=\"{}\" " +
                                "email=\"{}\" ip=\"{}\" execution_time_ms={}",
                        MDC.get("REQUEST_ID"), authResponse.getLocalId(),
                        maskEmail(request.getEmail()), clientIp, duration);
            }

            return authResponse;

        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorCode = extractFirebaseErrorCode(e.getResponseBodyAsString());

            // AUDIT LOG - FAILURE: Log FULL email for security monitoring
            log.error("REGISTER_FAILED event=\"failed_registration\" request_id=\"{}\" " +
                            "email=\"{}\" ip=\"{}\" user_agent=\"{}\" error_code=\"{}\" " +
                            "execution_time_ms={}",
                    MDC.get("REQUEST_ID"),
                    request.getEmail(),  // FULL EMAIL - NOT MASKED for security
                    clientIp, userAgent, errorCode, duration);

            // Check for specific registration errors
            if (errorCode != null && errorCode.contains("EMAIL_EXISTS")) {
                throw new BusinessRuleTranslatableException("error.auth.email_already_exists");
            } else if (errorCode != null && errorCode.contains("WEAK_PASSWORD")) {
                throw new ValidationTranslatableException("error.auth.weak_password");
            } else if (errorCode != null && errorCode.contains("INVALID_EMAIL")) {
                throw new ValidationTranslatableException("error.validation.invalid_email");
            }

            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Proxy token refresh request to Firebase.
     *
     * @param refreshToken  Refresh token
     * @param clientIp      Client IP for audit logging
     * @param correlationId Request correlation ID
     * @return New authentication tokens
     */
    public FirebaseAuthResponse refreshToken(
            String refreshToken,
            String clientIp,
            String correlationId) {

        // Validate input parameters
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "refreshToken");
        }

        long startTime = System.currentTimeMillis();

        String url = SECURE_TOKEN_BASE + "/token";

        Map<String, Object> firebaseRequest = new HashMap<>();
        firebaseRequest.put("grant_type", "refresh_token");
        firebaseRequest.put("refresh_token", refreshToken);

        // Audit log - attempt
        log.info(createAuditLog("refresh.attempt", clientIp, null,
                null, null, 0L, correlationId));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(firebaseRequest, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    }
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                long duration = System.currentTimeMillis() - startTime;

                // Convert response to our DTO
                FirebaseAuthResponse authResponse = FirebaseAuthResponse.builder()
                        .idToken((String) responseBody.get("id_token"))
                        .refreshToken((String) responseBody.get("refresh_token"))
                        .expiresIn((String) responseBody.get("expires_in"))
                        .processingTime(duration)
                        .correlationId(correlationId)
                        .build();

                // Audit log - success
                log.info(createAuditLog("refresh.success", clientIp, null,
                        "SUCCESS", null, duration, correlationId));

                return authResponse;
            }

            throw new NetworkTranslatableException("error.network.external_service");

        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorCode = extractFirebaseErrorCode(e.getResponseBodyAsString());

            // Audit log - failure
            log.warn(createAuditLog("refresh.failed", clientIp, null,
                    "FAILED", errorCode, duration, correlationId));

            if (errorCode != null && errorCode.contains("TOKEN_EXPIRED")) {
                throw new AuthenticationTranslatableException("error.auth.token_expired");
            } else if (errorCode != null && errorCode.contains("INVALID_REFRESH_TOKEN")) {
                throw new AuthenticationTranslatableException("error.auth.invalid_token");
            }

            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Create structured audit log entry.
     * <p>
     * Format: JSON with timestamp, service, event, ip, email, result, errorCode, duration, firebaseUid
     */
    private String createAuditLog(String event, String ip, String email,
                                  String result, String errorCode, Long duration,
                                  String correlationId) {
        return createAuditLog(event, ip, email, result, errorCode, duration, correlationId, null);
    }

    /**
     * Create structured audit log entry with Firebase UID.
     */
    private String createAuditLog(String event, String ip, String email,
                                  String result, String errorCode, Long duration,
                                  String correlationId, String firebaseUid) {
        Map<String, Object> log = new HashMap<>();
        log.put("timestamp", System.currentTimeMillis());
        log.put("service", "auth-proxy");
        log.put("event", event);
        log.put("ip", maskIp(ip));

        if (email != null) {
            log.put("email", email); // Already masked
        }
        if (result != null) {
            log.put("result", result);
        }
        if (errorCode != null) {
            log.put("errorCode", errorCode);
        }
        if (duration != null && duration > 0) {
            log.put("duration_ms", duration);
        }
        if (correlationId != null) {
            log.put("correlation_id", correlationId);
        }
        if (firebaseUid != null) {
            log.put("firebase_uid", firebaseUid);
        }

        try {
            return objectMapper.writeValueAsString(log);
        } catch (Exception e) {
            return String.format("{\"event\":\"%s\",\"error\":\"log_format_failed\"}", event);
        }
    }

    /**
     * Extract Firebase error code from response.
     */
    @SuppressWarnings("unchecked")
    private String extractFirebaseErrorCode(String responseBody) {
        try {
            Map<String, Object> errorResponse = objectMapper.readValue(responseBody, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
            Map<String, Object> error = (Map<String, Object>) errorResponse.get("error");
            if (error != null) {
                return (String) error.get("message");
            }
        } catch (Exception e) {
            log.debug("Failed to parse Firebase error response", e);
        }
        return "UNKNOWN_ERROR";
    }

    /**
     * Mask email for logging (first 3 chars + domain).
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 4 || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
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
        if (ip.contains(".")) {
            // IPv4
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
            }
        }
        return ip.substring(0, Math.min(ip.length(), 10)) + "***";
    }

    /**
     * Handle failed login with differentiated rate limiting based on account type.
     */
    private void handleFailedLogin(FirebaseAuthRequest request, String clientIp, Exception error) {
        String email = request.getEmail();

        // Detect account type from database
        RateLimitProfile profile = detectAccountProfile(email);

        // Apply differentiated rate limiting
        String rateLimitKey = profile.getKeyPrefix() + ":" + clientIp;
        RateLimiterService.RateLimitResult result = rateLimiterService.checkLimit(
                rateLimitKey,
                profile.getMaxAttempts(),
                profile.getWindowSeconds()
        );

        if (!result.isAllowed()) {
            throw new RateLimitTranslatableException(
                    "error.auth.too_many_attempts",
                    String.valueOf(profile.getBlockDurationSeconds())
            );
        }

        // Log with account type for monitoring
        log.warn("Failed login attempt | Type: {} | IP: {} | Email: {} | Error: {} | Attempts: {}/{}",
                profile.name(),
                maskIp(clientIp),
                maskEmail(email),
                error.getMessage(),
                profile.getMaxAttempts() - result.getRemaining() + 1,
                profile.getMaxAttempts()
        );
    }

    /**
     * Detect account type based on email to apply appropriate rate limiting.
     */
    private RateLimitProfile detectAccountProfile(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    switch (user.getUserType()) {
                        case ADMIN:
                            return RateLimitProfile.ADMIN_AUTH;
                        case COMPANY:
                            return RateLimitProfile.COMPANY_AUTH;
                        case INFLUENCER:
                            return RateLimitProfile.INFLUENCER_AUTH;
                        default:
                            return RateLimitProfile.UNKNOWN_AUTH;
                    }
                })
                .orElse(RateLimitProfile.UNKNOWN_AUTH);
    }

    /**
     * Extract client IP address from HTTP request.
     * Handles proxy headers like X-Forwarded-For.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated list of IPs (when behind multiple proxies)
                int commaIndex = ip.indexOf(',');
                if (commaIndex > 0) {
                    ip = ip.substring(0, commaIndex).trim();
                }
                return ip;
            }
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }

    /**
     * Change user password using Firebase Admin SDK.
     *
     * @param userId        Firebase user ID
     * @param userEmail     User email
     * @param request       Password change request
     * @param correlationId Request correlation ID
     * @return Operation response
     */
    @Transactional
    public AuthOperationResponse changePassword(
            String userId,
            String userEmail,
            PasswordChangeRequest request,
            String correlationId) {

        // Validate input parameters
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "userId");
        }
        if (userEmail == null || userEmail.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "userEmail");
        }
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getCurrentPassword() == null || request.getCurrentPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "currentPassword");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "newPassword");
        }

        long startTime = System.currentTimeMillis();

        try {
            // First verify current password via REST API
            FirebaseAuthRequest authRequest = new FirebaseAuthRequest();
            authRequest.setEmail(userEmail);
            authRequest.setPassword(request.getCurrentPassword());

            // Attempt login to verify current password
            String verifyUrl = IDENTITY_TOOLKIT_BASE + "/accounts:signInWithPassword";

            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("email", userEmail);
            verifyRequest.put("password", request.getCurrentPassword());
            verifyRequest.put("returnSecureToken", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(verifyRequest, headers);

            try {
                restTemplate.exchange(verifyUrl, HttpMethod.POST, entity, new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                });
            } catch (HttpClientErrorException e) {
                log.warn("Password verification failed for user: {}", maskEmail(userEmail));
                throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
            }

            // Update password using Admin SDK
            UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(userId)
                    .setPassword(request.getNewPassword());

            UserRecord updatedUser = firebaseAuth.updateUser(updateRequest);

            // Invalidate all existing sessions by incrementing tokenVersion
            userRepository.findByFirebaseUserId(userId).ifPresent(user -> {
                user.incrementTokenVersion();
                userCacheService.evict(userId);
                userRepository.saveAndFlush(user);
                log.info("SECURITY: All sessions invalidated after password change for user {}", userId);
            });

            long duration = System.currentTimeMillis() - startTime;

            // GDPR: Log password change success
            log.info("GDPR: PasswordChangeSuccess, FirebaseUID={}, Email={}, DataAccessed=credentials, Purpose=security_update, LegalBasis=contract",
                    userId, maskEmail(userEmail));

            // Audit log - password change success
            log.info(createAuditLog("password.change.success", null, userEmail,
                    "SUCCESS", null, duration, correlationId, userId));

            return AuthOperationResponse.success(
                    "Password changed successfully",
                    Map.of(
                            "userId", userId,
                            "correlationId", correlationId,
                            "processingTime", duration
                    )
            );

        } catch (AuthenticationTranslatableException e) {
            // Re-throw authentication exceptions
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // Audit log - password change failure
            log.error(createAuditLog("password.change.failed", null, userEmail,
                    "FAILED", e.getMessage(), duration, correlationId, userId));

            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Delete user account using Firebase Admin SDK.
     *
     * @param userId        Firebase user ID
     * @param userEmail     User email
     * @param authRequest   Authentication request for password verification
     * @param reason        Optional reason for deletion
     * @param clientIp      Client IP for audit logging
     * @param correlationId Request correlation ID
     * @return Operation response
     */
    @Transactional
    public AuthOperationResponse deleteAccount(
            String userId,
            String userEmail,
            FirebaseAuthRequest authRequest,
            String reason,
            String clientIp,
            String correlationId) {

        // Validate input parameters
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "userId");
        }
        if (userEmail == null || userEmail.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "userEmail");
        }
        if (authRequest == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "authRequest");
        }
        if (authRequest.getPassword() == null || authRequest.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "password");
        }

        long startTime = System.currentTimeMillis();

        try {
            // First verify password
            String verifyUrl = IDENTITY_TOOLKIT_BASE + "/accounts:signInWithPassword";

            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("email", userEmail);
            verifyRequest.put("password", authRequest.getPassword());
            verifyRequest.put("returnSecureToken", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(verifyRequest, headers);

            try {
                restTemplate.exchange(verifyUrl, HttpMethod.POST, entity, new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                });
            } catch (HttpClientErrorException e) {
                log.warn("Password verification failed for account deletion - user: {}", maskEmail(userEmail));
                throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
            }

            // GDPR: Log account deletion - CRITICAL for GDPR compliance
            log.warn("GDPR: DELETION_INITIATED FirebaseUID={}, Email={}, Reason={}, DataAccessed=all_user_data, Purpose=user_requested_deletion, LegalBasis=data_subject_request",
                    userId, maskEmail(userEmail), reason != null ? reason : "User requested");

            // Delete user from database FIRST (reversible via @Transactional rollback)
            userRepository.findByEmail(userEmail).ifPresent(user -> {
                userCacheService.evict(userId);
                userRepository.delete(user);
                userRepository.flush();
                // GDPR: Log database deletion
                log.warn("GDPR: DATABASE_DELETION FirebaseUID={}, Email={}, RecordRemoved=true",
                        userId, maskEmail(userEmail));
                log.info("Deleted user from database - email: {}", maskEmail(userEmail));
            });

            // Delete user from Firebase LAST (irreversible - only after PG succeeds)
            firebaseAuth.deleteUser(userId);

            long duration = System.currentTimeMillis() - startTime;

            // Audit log - account deletion success
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("userId", userId);
            auditData.put("email", maskEmail(userEmail));
            auditData.put("reason", reason != null ? reason : "User requested");
            auditData.put("ip", maskIp(clientIp));
            auditData.put("duration_ms", duration);
            auditData.put("correlationId", correlationId);

            // GDPR: Log deletion completion - CRITICAL
            log.warn("GDPR: DELETION_COMPLETE FirebaseUID={}, Email={}, AllDataRemoved=true, Permanent=true",
                    userId, maskEmail(userEmail));

            log.info("Account deletion successful - {}", auditData);

            return AuthOperationResponse.success(
                    "Account deleted successfully",
                    Map.of(
                            "userId", userId,
                            "correlationId", correlationId,
                            "processingTime", duration
                    )
            );

        } catch (AuthenticationTranslatableException e) {
            // Re-throw authentication exceptions
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // Audit log - account deletion failure
            log.error(createAuditLog("account.delete.failed", clientIp, userEmail,
                    "FAILED", e.getMessage(), duration, correlationId, userId));

            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Update user email using Firebase Admin SDK.
     *
     * @param userId        Firebase user ID
     * @param currentEmail  Current email address
     * @param request       Email update request
     * @param correlationId Request correlation ID
     * @return Operation response
     */
    @Transactional
    public AuthOperationResponse updateEmail(
            String userId,
            String currentEmail,
            EmailUpdateRequest request,
            String correlationId) {

        // Validate input parameters
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "userId");
        }
        if (currentEmail == null || currentEmail.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "currentEmail");
        }
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getNewEmail() == null || request.getNewEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "newEmail");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "password");
        }

        long startTime = System.currentTimeMillis();
        String newEmail = request.getNewEmail();

        try {
            // 1. Verify password via REST API (proxy-specific requirement)
            String verifyUrl = IDENTITY_TOOLKIT_BASE + "/accounts:signInWithPassword";

            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("email", currentEmail);
            verifyRequest.put("password", request.getPassword());
            verifyRequest.put("returnSecureToken", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(verifyRequest, headers);

            try {
                restTemplate.exchange(verifyUrl, HttpMethod.POST, entity, new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                });
            } catch (HttpClientErrorException e) {
                log.warn("Password verification failed for email update - user: {}", maskEmail(currentEmail));
                throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
            }

            // 2. Find user by firebaseUserId
            User user = userRepository.findByFirebaseUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

            // GDPR: Log email update operation
            String oldEmail = user.getEmail();
            log.info("GDPR: EmailUpdateOperation, FirebaseUID={}, OldEmail={}, NewEmail={}, DataAccessed=email_address, Purpose=account_update, LegalBasis=contract",
                    userId, maskEmail(oldEmail), maskEmail(newEmail));

            // 3. Unified email change (validates, PG save, Firebase, cache evict, verification email)
            boolean changed = emailChangeService.changeEmail(user, newEmail);

            long duration = System.currentTimeMillis() - startTime;

            if (!changed) {
                return AuthOperationResponse.success(
                        "Email is already set to the requested address.",
                        Map.of(
                                "userId", userId,
                                "correlationId", correlationId,
                                "processingTime", duration
                        )
                );
            }

            // GDPR: Log email update success
            log.info("GDPR: EmailUpdateSuccess, FirebaseUID={}, OldEmail={}, NewEmail={}, DataStored=updated_email, Purpose=account_update",
                    userId, maskEmail(oldEmail), maskEmail(newEmail));

            // Audit log - email update success
            log.info(createAuditLog("email.update.success", null, oldEmail,
                    "SUCCESS", "to: " + maskEmail(newEmail), duration, correlationId, userId));

            return AuthOperationResponse.success(
                    "Email updated successfully. Please verify your new email address.",
                    Map.of(
                            "userId", userId,
                            "newEmail", maskEmail(newEmail),
                            "verificationRequired", true,
                            "correlationId", correlationId,
                            "processingTime", duration
                    )
            );

        } catch (AuthenticationTranslatableException | BusinessRuleTranslatableException
                 | ResourceNotFoundException | ValidationTranslatableException
                 | ExternalServiceException e) {
            // Re-throw specific exceptions
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // Audit log - email update failure
            log.error(createAuditLog("email.update.failed", null, currentEmail,
                    "FAILED", e.getMessage(), duration, correlationId, userId));

            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Apply an email verification action code (oobCode).
     * Uses Admin SDK via EmailVerificationService to set emailVerified=true directly,
     * bypassing the Identity Toolkit REST API which requires client-side auth for oobCode consumption.
     *
     * <p>The oobCode → user mapping is stored in Redis when the verification email is generated.
     *
     * @param oobCode The out-of-band code from the email verification link
     * @return AuthOperationResponse indicating success with the user's email
     */
    public AuthOperationResponse applyActionCode(String oobCode) {
        String email = emailVerificationService.applyVerificationCode(oobCode);

        log.info("Email verification action code applied successfully");

        return AuthOperationResponse.success(
                "Email verified successfully",
                email != null ? Map.of("email", email) : null
        );
    }

    /**
     * Verify a password reset action code (oobCode) via Firebase Identity Toolkit.
     * Returns the email associated with the reset code without consuming it.
     *
     * @param oobCode The out-of-band code from the password reset link
     * @return AuthOperationResponse with the user's email
     */
    @SuppressWarnings("unchecked")
    public AuthOperationResponse verifyResetCode(String oobCode) {
        String url = IDENTITY_TOOLKIT_BASE + "/accounts:resetPassword";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("oobCode", oobCode);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class
            );

            Map<String, Object> body = response.getBody();
            String email = body != null ? (String) body.get("email") : null;
            String requestType = body != null ? (String) body.get("requestType") : null;

            log.info("Password reset code verified successfully, requestType={}", requestType);

            return AuthOperationResponse.success(
                    "Reset code is valid",
                    email != null ? Map.of("email", email) : null
            );

        } catch (HttpClientErrorException e) {
            String errorCode = extractFirebaseErrorCode(e.getResponseBodyAsString());
            log.warn("Failed to verify reset code: {}", errorCode);

            if (errorCode.contains("INVALID_OOB_CODE")) {
                throw new ValidationTranslatableException("error.auth.invalid_action_code");
            } else if (errorCode.contains("EXPIRED_OOB_CODE")) {
                throw new ValidationTranslatableException("error.auth.expired_action_code");
            }

            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Confirm a password reset using an oobCode and new password via Firebase Identity Toolkit.
     *
     * @param oobCode     The out-of-band code from the password reset link
     * @param newPassword The new password to set
     * @return AuthOperationResponse with the user's email
     */
    @SuppressWarnings("unchecked")
    public AuthOperationResponse confirmPasswordReset(String oobCode, String newPassword) {
        String url = IDENTITY_TOOLKIT_BASE + "/accounts:resetPassword";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("oobCode", oobCode);
        requestBody.put("newPassword", newPassword);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(getAccessToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class
            );

            Map<String, Object> body = response.getBody();
            String email = body != null ? (String) body.get("email") : null;

            log.info("Password reset confirmed successfully for email={}", maskEmail(email));

            return AuthOperationResponse.success(
                    "Password has been reset successfully",
                    email != null ? Map.of("email", email) : null
            );

        } catch (HttpClientErrorException e) {
            String errorCode = extractFirebaseErrorCode(e.getResponseBodyAsString());
            log.warn("Failed to confirm password reset: {}", errorCode);

            if (errorCode.contains("INVALID_OOB_CODE")) {
                throw new ValidationTranslatableException("error.auth.invalid_action_code");
            } else if (errorCode.contains("EXPIRED_OOB_CODE")) {
                throw new ValidationTranslatableException("error.auth.expired_action_code");
            } else if (errorCode.contains("WEAK_PASSWORD")) {
                throw new ValidationTranslatableException("error.auth.weak_password");
            }

            throw new NetworkTranslatableException("error.network.external_service");
        }
    }

    /**
     * Complete email verification via Firebase REST API and optionally set password.
     * Uses Firebase Identity Toolkit accounts:update with oobCode (no Redis needed).
     * For INFLUENCER users, also sets password via Admin SDK.
     *
     * @param oobCode  The verification code from the email link
     * @param password Optional password (required for influencers)
     * @return AuthOperationResponse with email and userType
     */
    /**
     * Completes email verification + optional password setup with full transactional guarantees.
     *
     * <p>Validates oobCode via Redis (generated by sendVerificationEmail), then mutates Firebase
     * and PostgreSQL with compensating transactions for Firebase rollback on PG failure.
     *
     * <p>For INFLUENCER users: also sets password (creates email/password provider in Firebase).
     * For COMPANY/ADMIN users: only verifies email (password provider already exists).
     */
    @Transactional
    public AuthOperationResponse completeVerification(String oobCode, String password) {
        // ===== PHASE A: VALIDATE (user identified via oobCode, no session required) =====

        String[] oobData = emailVerificationService.lookupOobCode(oobCode);
        if (oobData == null) {
            throw new ValidationTranslatableException("error.auth.invalid_action_code");
        }
        String firebaseUid = oobData[0];
        String storedEmail = oobData[1];

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        String email = user.getEmail();
        boolean isInfluencer = user.getUserType() == UserType.INFLUENCER;
        boolean hasPassword = password != null && !password.isBlank();

        // Verify email consistency between Redis and DB
        if (email != null && !email.equalsIgnoreCase(storedEmail)) {
            log.warn("oobCode email mismatch: stored={}, current={}", storedEmail, email);
            throw new ValidationTranslatableException("error.auth.invalid_action_code");
        }

        // ===== PHASE B: MUTATE FIREBASE (single Admin SDK call) =====

        boolean firebaseMutated = false;
        try {
            UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(firebaseUid)
                    .setEmailVerified(true);
            if (isInfluencer && hasPassword) {
                updateRequest.setPassword(password);
            }
            firebaseAuth.updateUser(updateRequest);
            firebaseMutated = true;

            log.info("Firebase updated for user {}: emailVerified=true{}",
                    firebaseUid, isInfluencer && hasPassword ? " + password set" : "");
        } catch (Exception e) {
            log.error("Failed to update Firebase for user {}: {}", firebaseUid, e.getMessage());
            throw new ExternalServiceException("Failed to verify email in Firebase", "Firebase", "updateUser", e);
        }

        // ===== PHASE C: MUTATE PG + CLAIMS (inline, same @Transactional) =====
        // NOT calling syncEmailVerificationStatus() — it uses REQUIRES_NEW (can't roll back)
        // and its PG-wins guard blocks verification when initialAccountSetupCompleted=false.

        try {
            // PG mutations (all within the same @Transactional — rolled back atomically on failure)
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(java.time.LocalDateTime.now());
            user.setLastVerifiedEmail(user.getEmail());

            // Activate only from IN_VALIDATION, and only if user-type-specific conditions are met
            if (user.getAccountStatus() == com.sm.instagram.platform.user.AccountStatus.IN_VALIDATION) {
                boolean shouldActivate = false;

                if (user.getUserType() == com.sm.instagram.platform.user.UserType.INFLUENCER) {
                    shouldActivate = true; // Email verification is sufficient for influencers
                } else if (user.getUserType() == com.sm.instagram.platform.user.UserType.COMPANY) {
                    // COMPANY requires both email verified AND company data verified
                    shouldActivate = companyDataRepository.existsByUserIdAndDataVerifiedTrue(user.getId());
                    if (!shouldActivate) {
                        log.info("COMPANY user {} verified email but company data not yet submitted — staying IN_VALIDATION", firebaseUid);
                    }
                }
                // ADMIN/PENDING_ADMIN: don't auto-activate via this path

                if (shouldActivate) {
                    user.setAccountStatus(com.sm.instagram.platform.user.AccountStatus.ACTIVE);
                    user.incrementTokenVersion();
                    eventPublisher.publishEvent(new com.sm.instagram.platform.notification.event.AccountActivatedEvent(
                            this, user, com.sm.instagram.platform.user.AccountStatus.IN_VALIDATION, "EMAIL_VERIFICATION"));
                }
            }

            if (EmailVerificationService.isProfileComplete(user)) {
                user.setInitialAccountSetupCompleted(true);
                log.info("Initial account setup completed for user {} (email verified + profile complete)", firebaseUid);
            }

            userRepository.saveAndFlush(user);
            // PG is now consistent. If anything below fails, @Transactional rolls back PG.

            // Firebase claims update (reflect actual status, which may still be IN_VALIDATION)
            permissionUtils.changeUserRole(firebaseUid, user.getAccountStatus(), user.getUserType());

            // Cache eviction (best-effort, Redis DEL)
            userCacheService.evict(firebaseUid);

        } catch (Exception e) {
            // @Transactional rolls back PG automatically.
            // Compensate Firebase: revert emailVerified + remove password provider if created.
            if (firebaseMutated) {
                compensateFirebase(firebaseUid, isInfluencer && hasPassword);
            }
            throw e;
        }

        // Consume the oobCode AFTER all critical mutations succeed (best-effort — TTL expires anyway)
        try {
            emailVerificationService.invalidateOobCode(oobCode);
        } catch (Exception e) {
            log.warn("Failed to invalidate oobCode for user {}: {}", firebaseUid, e.getMessage());
        }

        String userType = user.getUserType().name();
        log.info("Email verification completed for user={}, type={}, email={}", firebaseUid, userType, email);

        return AuthOperationResponse.success(
                "Email verified successfully",
                Map.of("email", email != null ? email : "", "userType", userType)
        );
    }

    /**
     * Compensates Firebase state on PG failure during completeVerification.
     * Reverts emailVerified to false and optionally removes the password provider.
     * Two-tier fallback: if unlinking provider fails, still revert emailVerified.
     */
    private void compensateFirebase(String firebaseUid, boolean removePasswordProvider) {
        try {
            UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(firebaseUid)
                    .setEmailVerified(false);
            if (removePasswordProvider) {
                request.setProvidersToUnlink(java.util.List.of("password"));
            }
            firebaseAuth.updateUser(request);
            log.warn("COMPENSATED Firebase state for user {} (emailVerified=false{})",
                    firebaseUid, removePasswordProvider ? ", password provider removed" : "");
        } catch (Exception e) {
            if (removePasswordProvider) {
                // Fallback: just revert emailVerified without touching provider
                try {
                    firebaseAuth.updateUser(new UserRecord.UpdateRequest(firebaseUid).setEmailVerified(false));
                    log.warn("COMPENSATED Firebase emailVerified for user {} (provider removal failed: {})",
                            firebaseUid, e.getMessage());
                } catch (Exception e2) {
                    log.error("CRITICAL: Firebase compensation FAILED for user {}. Manual intervention required. Error: {}",
                            firebaseUid, e2.getMessage());
                }
            } else {
                log.error("CRITICAL: Firebase compensation FAILED for user {}. Manual intervention required. Error: {}",
                        firebaseUid, e.getMessage());
            }
        }
    }

}
