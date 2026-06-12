package com.sm.instagram.platform.auth;

import static com.sm.instagram.platform.common.util.PiiMaskingUtils.maskEmail;

import com.sm.instagram.platform.auth.dto.*;
import com.sm.instagram.platform.auth.filter.HmacUtils;
import com.sm.instagram.platform.auth.session.SessionData;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.auth.service.FirebaseAuthProxyService;
import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.auth.service.OAuthCallbackService;
import com.sm.instagram.platform.auth.service.RegistrationService;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Authentication Controller - Handles all authentication flows.
 * <p>
 * Design Principles:
 * - Controller only handles HTTP concerns (request/response mapping)
 * - All business logic delegated to services
 * - Clear endpoint responsibilities following OAuth pattern
 * - Universal token exchange for all auth methods
 * <p>
 * OAuth Flow Pattern:
 * ALL Auth → Firebase → ID Token → /exchange-token → JWT+HMAC Cookies → APIs
 *
 * @author CheckItOut Platform Team
 * @version 3.0
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String REGISTRATION_FAILED = "Registration failed";
    private final TokenExchangeService tokenExchangeService;
    private final OAuthCallbackService oAuthCallbackService;
    private final RegistrationService registrationService;
    private final SocialAuthSessionService sessionService;
    private final FirebaseAuthProxyService firebaseAuthProxyService;
    private final EmailVerificationService emailVerificationService;
    private final PermissionUtils permissionUtils;
    private final MessageSource messageSource;

    @Value("${cookie.hmac.secret}")
    private String cookieHmacSecret;

    @Value("${jwt.cookie.domain:localhost}")
    private String cookieDomain;

    @Value("${jwt.cookie.secure:true}")
    private boolean secureCookies;

    /**
     * Universal token exchange endpoint - THE BRIDGE between Firebase and Backend.
     * <p>
     * This is the ONLY endpoint that validates Firebase tokens and creates backend sessions.
     * ALL authentication methods (email, Google, Instagram) must go through this endpoint.
     * <p>
     * Flow:
     * 1. Receive Firebase ID token from frontend
     * 2. Validate token with Firebase (only time we do this)
     * 3. Find user in database
     * 4. Create backend JWT with user claims
     * 5. Generate HMAC signature for security
     * 6. Set HttpOnly secure cookies
     *
     * @param request  Contains Firebase ID token and optional expiration
     * @param response HTTP response for setting cookies
     * @return User info with success status
     */
    @PostMapping("/exchange-token")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<TokenExchangeResponse> exchangeToken(
            @Valid @RequestBody TokenExchangeRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {

        // Extract Firebase UID if available from context, or use "pending" for initial exchange
        String firebaseUid = "pending_exchange";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            firebaseUid = auth.getPrincipal().toString();
        }

        // GDPR: Log token exchange operation
        log.info("GDPR: Operation=exchangeToken, FirebaseUID={}, DataAccessed=firebase_token,user_profile, Purpose=authentication, LegalBasis=contract",
                firebaseUid);

        // Token exchange requested
        log.debug("ID token received (first 50 chars): {}",
            request.getIdToken() != null && request.getIdToken().length() > 50 ?
            request.getIdToken().substring(0, 50) + "..." : request.getIdToken());

        // Let service throw translatable exceptions - they will be caught by @ControllerAdvice handlers
        TokenExchangeResponse exchangeResponse = tokenExchangeService.exchangeToken(
            request.getIdToken(),
            request.getExpirationDays(),
            servletRequest,
            response
        );

        // GDPR: Log successful authentication
        if (exchangeResponse.isSuccess() && exchangeResponse.getFirebaseUid() != null) {
            log.info("GDPR: TokenExchangeComplete, FirebaseUID={}, UserID={}, DataProcessed=authentication_tokens, Purpose=session_creation",
                exchangeResponse.getFirebaseUid(), exchangeResponse.getUserId());
        }

        return ResponseEntity.ok(exchangeResponse);
    }

    /**
     * OAuth callback endpoint for Instagram.
     * <p>
     * Handles both new and existing users:
     * - Existing users: Generate custom token → Set cookies → Redirect to success
     * - New users: Validate consent cookies → Create user → Process consents → Redirect to success
     * <p>
     * CONSENT COOKIES REQUIRED (for new users): The browser must send these HMAC-signed
     * cookies alongside the OAuth redirect (SameSite=Lax allows this on top-level GET):
     * - {@code consent_cookie_policy} + {@code consent_cookie_policy_sig} — anonymous cookie banner consent record ID
     * - {@code consent_terms_of_service} + {@code consent_terms_of_service_sig} — ToS consent proof (JSON)
     * - {@code consent_privacy_policy} + {@code consent_privacy_policy_sig} — Privacy Policy consent proof (JSON)
     * <p>
     * If consent cookies are missing for a new user, redirects to {@code /auth/error?reason=consent_required}.
     * <p>
     * CIO-366: Added Safari iOS detection and logging for debugging OAuth redirect issues
     *
     * @param code             OAuth authorization code from Instagram
     * @param state            Optional state for CSRF protection
     * @param error            OAuth error if authentication failed
     * @param errorDescription Detailed error description
     * @param userAgent        User-Agent header for Safari iOS detection
     * @param request          HTTP request for client detection (also carries consent cookies)
     * @param response         HTTP response for cookies/redirects
     * @return Redirect response or JSON for API clients
     */
    @GetMapping("/social/callback/instagram")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<?> instagramCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Extract identifier - for OAuth callback, we don't have Firebase UID yet
        String sessionIdentifier = state != null ? state : "oauth_callback_" + System.currentTimeMillis();

        // CIO-366: Detect Safari iOS for enhanced logging
        boolean isSafariIOS = isSafariIOS(userAgent);

        // GDPR: Log OAuth callback
        log.info("GDPR: Operation=instagramOAuthCallback, SessionID={}, DataAccessed=instagram_profile, Purpose=social_authentication, LegalBasis=consent, ThirdParty=Instagram",
            sessionIdentifier);

        // CIO-366: Enhanced logging for Safari iOS debugging
        if (isSafariIOS) {
            log.info("[Safari iOS OAuth] Callback received - code: {}, state: {}, error: {}",
                code != null ? "present" : "MISSING",
                state != null ? "present" : "MISSING",
                error != null ? error : "none");
            log.info("[Safari iOS OAuth] User-Agent: {}", userAgent);
        } else {
            log.info("Instagram OAuth callback - code present: {}, error: {}",
                code != null, error);
        }

        try {
            // Handle OAuth errors
            if (error != null) {
                if (isSafariIOS) {
                    log.warn("[Safari iOS OAuth] OAuth error received: {} - {}", error, errorDescription);
                }
                return oAuthCallbackService.handleOAuthError(
                    error, errorDescription, request
                );
            }

            // Validate code presence
            if (code == null || code.isBlank()) {
                if (isSafariIOS) {
                    log.warn("[Safari iOS OAuth] Missing authorization code - this may be an ITP issue");
                }
                return oAuthCallbackService.handleMissingCode(request);
            }

            // CIO-366: Log successful callback receipt for Safari iOS
            if (isSafariIOS) {
                log.info("[Safari iOS OAuth] Authorization code received successfully, proceeding with token exchange");
            }

            // Process OAuth callback
            return oAuthCallbackService.processInstagramCallback(
                code, state, request, response
            );

        } catch (Exception e) {
            if (isSafariIOS) {
                log.error("[Safari iOS OAuth] Callback processing failed - possible ITP interference", e);
            } else {
                log.error("Instagram callback processing failed", e);
            }
            return oAuthCallbackService.handleProcessingError(e, request);
        }
    }

    /**
     * CIO-366: Detect Safari on iOS from User-Agent header
     * Safari iOS has known issues with OAuth redirect flows due to ITP (Intelligent Tracking Prevention)
     *
     * @param userAgent User-Agent header string
     * @return true if Safari on iOS is detected
     */
    private boolean isSafariIOS(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }

        // iOS devices
        boolean isIOS = userAgent.contains("iPhone") ||
                       userAgent.contains("iPad") ||
                       userAgent.contains("iPod");

        // WebKit browser engine (Safari)
        boolean isWebKit = userAgent.contains("WebKit");

        // Not Chrome iOS (CriOS)
        boolean notChrome = !userAgent.contains("CriOS");

        // Not Firefox iOS (FxiOS)
        boolean notFirefox = !userAgent.contains("FxiOS");

        return isIOS && isWebKit && notChrome && notFirefox;
    }

    /**
     * Complete social registration for new OAuth users (two-step path).
     * <p>
     * Flow:
     * 1. Validate consent cookies (HMAC-signed, SameSite=Lax)
     * 2. Retrieve session data using sessionId
     * 3. Create user in database + Firebase
     * 4. Process consent cookies → create ConsentRecord entries
     * 5. Return token for frontend to exchange
     * <p>
     * CONSENT COOKIES REQUIRED: The request must include these HMAC-signed cookies:
     * - {@code consent_cookie_policy} + {@code consent_cookie_policy_sig}
     * - {@code consent_terms_of_service} + {@code consent_terms_of_service_sig}
     * - {@code consent_privacy_policy} + {@code consent_privacy_policy_sig}
     * Returns 400 if any consent cookie is missing or has invalid HMAC.
     *
     * @param request Contains sessionId and email
     * @return Registration response with custom token
     */
    @PostMapping("/complete-social-registration")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<RegistrationResponse> completeSocialRegistration(
            @Valid @RequestBody CompleteSocialRegistrationRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        // GDPR: Log social registration completion
        log.info("GDPR: Operation=completeSocialRegistration, SessionID={}, Email={}, DataAccessed=social_profile,email, Purpose=account_creation, LegalBasis=contract",
                request.getSessionId(), maskEmail(request.getEmail()));

        log.info("Completing social registration for session: {}", request.getSessionId());

        // Validate session exists
        SessionData sessionData = sessionService.getSocialData(request.getSessionId());
        if (sessionData == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RegistrationResponse.error("Session expired or invalid"));
        }

        // Process registration - let service throw translatable exceptions
        RegistrationResponse registrationResponse = registrationService.completeSocialRegistration(
            request.getSessionId(),
            request.getEmail(),
            sessionData,
            servletRequest,
            servletResponse
        );

        // GDPR: Log registration result
        if (registrationResponse.isSuccess() && registrationResponse.getFirebaseUid() != null) {
            log.info("GDPR: RegistrationComplete, FirebaseUID={}, UserID={}, DataStored=user_profile,social_connections, Purpose=account_creation",
                    registrationResponse.getFirebaseUid(), registrationResponse.getUserId());
        }

        // Clean up session on success
        if (registrationResponse.isSuccess()) {
            sessionService.removeSession(request.getSessionId());
        }

        return ResponseEntity.ok(registrationResponse);
    }

    /**
     * Register new user (email/password flow).
     * <p>
     * After successful registration, automatically logs in the user via Firebase REST API
     * and sets the FirebaseIdToken cookie, enabling immediate token exchange.
     * <p>
     * CONSENT COOKIES REQUIRED: The request must include these HMAC-signed cookies:
     * - {@code consent_cookie_policy} + {@code consent_cookie_policy_sig}
     * - {@code consent_terms_of_service} + {@code consent_terms_of_service_sig}
     * - {@code consent_privacy_policy} + {@code consent_privacy_policy_sig}
     * Returns 400 if any consent cookie is missing or has invalid HMAC.
     * Consent validation runs BEFORE user creation to fail fast.
     *
     * @param request         User registration details
     * @param servletRequest  HTTP request for client info extraction (also carries consent cookies)
     * @param servletResponse HTTP response for setting cookies
     * @return Registration response with success status
     */
    @PostMapping("/register")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<RegistrationResponse> registerUser(
            @Valid @RequestBody RegisterUserRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        // GDPR: Log registration attempt
        log.info("GDPR: Operation=registerUser, Email={}, DataAccessed=registration_data, Purpose=account_creation, LegalBasis=contract",
                maskEmail(request.getEmail()));

        log.info("Registration requested for email: {}", maskEmail(request.getEmail()));

        // Let service throw translatable exceptions - they will be caught by @ControllerAdvice handlers
        RegistrationResponse response = registrationService.registerUser(request, servletRequest, servletResponse);

        // GDPR: Log successful registration
        if (response.isSuccess() && response.getFirebaseUid() != null) {
            log.info("GDPR: RegistrationComplete, FirebaseUID={}, Email={}, DataStored=user_profile,credentials, Purpose=account_creation",
                response.getFirebaseUid(), maskEmail(request.getEmail()));

            // NEW: After successful registration, login via Firebase REST API to get idToken
            // This sets the FirebaseIdToken cookie, same as the login flow
            try {
                FirebaseAuthRequest authRequest = new FirebaseAuthRequest();
                authRequest.setEmail(request.getEmail());
                authRequest.setPassword(request.getPassword());

                String clientIp = extractClientIp(servletRequest);
                String userAgent = servletRequest.getHeader("User-Agent");
                String correlationId = java.util.UUID.randomUUID().toString();

                FirebaseAuthResponse authResponse = firebaseAuthProxyService.login(
                        authRequest, clientIp, userAgent, correlationId
                );

                if (authResponse != null && authResponse.getIdToken() != null) {
                    // Set FirebaseIdToken cookie with HMAC signature (same as login flow)
                    String idToken = authResponse.getIdToken();
                    String hmacSignature = HmacUtils.generateHMAC(idToken, cookieHmacSecret);

                    setFirebaseIdTokenCookie(servletResponse, "FirebaseIdToken", idToken, 30);
                    setFirebaseIdTokenCookie(servletResponse, "FirebaseIdToken_sig", hmacSignature, 30);

                    log.info("FirebaseIdToken cookie set after registration for user: {}", maskEmail(request.getEmail()));
                } else {
                    log.warn("Could not obtain idToken after registration for user: {}", maskEmail(request.getEmail()));
                }
            } catch (Exception e) {
                // Don't fail registration if cookie setting fails - user can still login manually
                log.error("Failed to set FirebaseIdToken cookie after registration: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(response);
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
     * Helper method to set Firebase token cookies.
     */
    private void setFirebaseIdTokenCookie(HttpServletResponse response,
                                          String name,
                                          String value,
                                          int maxAgeMinutes) {
        String sameSite = "Strict";
        String domain = "";

        if (!"localhost".equals(cookieDomain) && cookieDomain != null) {
            domain = String.format("; Domain=%s", cookieDomain);
        }

        String secureFlag = secureCookies ? "; Secure" : "";

        String cookieString = String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly%s; SameSite=%s%s",
                name,
                value,
                maxAgeMinutes * 60,
                secureFlag,
                sameSite,
                domain
        );

        response.addHeader("Set-Cookie", cookieString);
    }

    /**
     * Get supported OAuth platforms.
     *
     * @return Set of supported platform names
     */
    @GetMapping("/supported-platforms")
    public ResponseEntity<Set<String>> getSupportedPlatforms() {
        // For now, only Instagram is supported
        return ResponseEntity.ok(Set.of("Instagram"));
    }

    /**
     * Sign out - Clear session cookies.
     *
     * @param response HTTP response for clearing cookies
     * @return Success response
     */
    @PostMapping("/sign-out")
    public ResponseEntity<Map<String, Object>> signOut(
            HttpServletResponse response) {

        // Extract Firebase UID from security context
        String firebaseUid = "unknown";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            firebaseUid = auth.getPrincipal().toString();
        }

        // GDPR: Log sign out operation - important for audit trail
        log.info("GDPR: Operation=signOut, FirebaseUID={}, DataAccessed=session_tokens, Purpose=session_termination, Action=cookies_cleared",
                firebaseUid);

        log.info("Sign out requested");

        tokenExchangeService.clearSessionCookies(response);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Signed out successfully"
        ));
    }

    /**
     * Silent session refresh endpoint.
     * <p>
     * Called by frontend when token version mismatch is detected.
     * Re-issues a new session token with updated permissions without requiring re-login.
     * <p>
     * Use cases:
     * - Admin changed user's account status (ban → active or active → ban)
     * - User's permissions changed
     * - Session needs to be refreshed with latest user data
     *
     * @param request  HTTP request (existing session cookie used for authentication)
     * @param response HTTP response for setting new cookies
     * @return New session data or error if user can no longer login
     */
    @PostMapping("/refresh-session")
    @RateLimit(profile = RateLimitProfile.AUTH)
    public ResponseEntity<TokenExchangeResponse> refreshSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Get current authentication from SecurityContext (set by JwtAuthenticationFilter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.warn("SECURITY_METRIC: event_type=REFRESH_SESSION_NO_AUTH");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(TokenExchangeResponse.error("error.auth.not_authenticated"));
        }

        String firebaseUid = auth.getPrincipal().toString();

        // GDPR: Log refresh session request
        log.info("GDPR: Operation=refreshSession, FirebaseUID={}, Purpose=session_refresh_on_permission_change", firebaseUid);

        try {
            TokenExchangeResponse refreshResponse = tokenExchangeService.createRefreshedSession(
                firebaseUid,
                request,
                response
            );

            // GDPR: Log successful refresh
            if (refreshResponse.isSuccess()) {
                log.info("GDPR: Operation=refreshSession_success, FirebaseUID={}, NewRole={}, Purpose=permission_update_applied",
                    firebaseUid, refreshResponse.getRole());
            }

            return ResponseEntity.ok(refreshResponse);

        } catch (com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException e) {
            // User is now banned or inactive - clear session and let handler process exception
            log.info("GDPR: Operation=refreshSession_denied, FirebaseUID={}, Reason={}, Purpose=access_revoked",
                firebaseUid, e.getMessage());
            tokenExchangeService.clearSessionCookies(response);
            throw e; // Re-throw to let exception handler translate properly

        } catch (ResourceNotFoundException e) {
            // User not found - clear session and let handler process exception
            log.error("GDPR: Operation=refreshSession_failed, FirebaseUID={}, Error=user_not_found", firebaseUid);
            tokenExchangeService.clearSessionCookies(response);
            throw e; // Re-throw to let exception handler translate properly
        }
    }

    /**
     * Resend email verification to current user.
     * Rate limited to 10 requests per 60 seconds.
     */
    @PostMapping("/send-verification-email")
    @RateLimit(profile = RateLimitProfile.STRICT)
    public ResponseEntity<Map<String, Object>> sendVerificationEmail(HttpServletRequest request) {
        String firebaseUid = permissionUtils.getUserId();
        String language = LocaleContextHolder.getLocale().getLanguage();

        log.info("GDPR: Operation=sendVerificationEmail, FirebaseUID={}, Purpose=email_verification", firebaseUid);

        emailVerificationService.sendVerificationEmail(firebaseUid, language);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", messageSource.getMessage("auth.verification.email_sent", null, LocaleContextHolder.getLocale()));

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     *
     * @return Service status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "auth",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
