package com.sm.instagram.platform.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.dto.TokenExchangeResponse;
import com.sm.instagram.platform.auth.exceptions.TwoFactorAuthException;
import com.sm.instagram.platform.auth.filter.HmacUtils;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.social.instagram.InstagramService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ConsentRequiredTranslatableException;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.legal.ConsentCookieService;
import com.sm.instagram.platform.legal.ConsentProofPayload;
import com.sm.instagram.platform.legal.ConsentRecordRepository;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.legal.LegalDocumentService;
import com.sm.instagram.platform.legal.LegalDocumentType;
import com.sm.instagram.platform.common.jwt.JwtTokenProvider;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for token exchange operations.
 * This is the ONLY service that validates Firebase tokens and creates backend sessions.
 * <p>
 * Pattern: Firebase ID Token → Backend JWT + HMAC → Session Cookies
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {

    private static final ObjectMapper JWT_PAYLOAD_MAPPER = new ObjectMapper();

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TwoFactorAuthService twoFactorAuthService;
    private final FirestoreService firestoreService;
    private final TotpFirestoreService totpFirestoreService;
    private final InstagramService instagramService;
    private final EmailVerificationService emailVerificationService;
    private final ConsentCookieService consentCookieService;
    private final LegalConsentService legalConsentService;
    private final LegalDocumentService legalDocumentService;
    private final ConsentRecordRepository consentRecordRepository;

    @Value("${cookie.hmac.secret}")
    private String cookieHmacSecret;

    @Value("${jwt.cookie.domain:localhost}")
    private String cookieDomain;

    @Value("${jwt.cookie.secure:true}")  // HTTPS everywhere including localhost
    private boolean secureCookies;
    
    @Value("${instagram.api.validation.enabled:true}")
    private boolean instagramApiValidationEnabled;

    @Value("${instagram.api.validation.privileged.only:false}")
    private boolean validatePrivilegedOnly;

    @Value("${session.full-duration-seconds:604800}")
    private int fullSessionDurationSeconds;  // Default: 7 days (COMPANY, INFLUENCER)

    @Value("${session.admin-full-duration-seconds:7200}")
    private int adminFullSessionDurationSeconds;  // Default: 2 hours (ADMIN, PENDING_ADMIN)

    @Value("${session.partial-duration-seconds:600}")
    private int partialSessionDurationSeconds;  // Default: 10 minutes

    /**
     * Returns the full session duration based on user role.
     * ADMIN/PENDING_ADMIN get a shorter session (2 hours) as a security hardening measure.
     */
    private int getFullSessionDuration(String role) {
        if ("ADMIN".equals(role) || "PENDING_ADMIN".equals(role)) {
            return adminFullSessionDurationSeconds;
        }
        return fullSessionDurationSeconds;
    }

    /**
     * Exchange token - NOW SUPPORTS BOTH METHODS
     * 1. ID token in request body (email/password flow)
     * 2. Custom token in HttpOnly cookie (OAuth flow)
     *
     * @param idToken        Firebase ID token to validate (can be null for OAuth)
     * @param expirationDays Optional cookie expiration in days (default 7)
     * @param request        HTTP request to read cookies
     * @param response       HTTP response for setting cookies
     * @return Token exchange response with user info
     */
    @Transactional(readOnly = true)
    public TokenExchangeResponse exchangeToken(String idToken, Integer expirationDays,
                                               HttpServletRequest request, HttpServletResponse response) {

        // GDPR: Log operation entry
        log.info("GDPR: Operation=exchangeToken_entry, Purpose=authentication, DataAccessed=firebase_token");

        // NEW: Check for FirebaseIdToken cookie FIRST (email/password flow)
        FirebaseTokenCookieData firebaseTokenData = getValidatedFirebaseTokenCookie(request);
        
        if (firebaseTokenData != null && firebaseTokenData.isValid()) {

            // Use the token from cookie instead of request body
            idToken = firebaseTokenData.token;
            
            // Don't clear cookies yet - we'll decide later based on role
            // clearFirebaseTokenCookies(response);

        }
        
        // Check for OAuth custom token cookie (existing flow)
        OAuthCookieData oauthData = getValidatedOAuthCookie(request, response);
        
        if (oauthData != null && oauthData.isValid()) {

            return exchangeCustomToken(oauthData.token, expirationDays, request, response);
        }

        // Rest of existing validation...


        // Validate input
        if (idToken == null || idToken.trim().isEmpty()) {
            log.error("Token exchange failed: ID token is null or empty");
            throw new ValidationTranslatableException("error.validation.required_field", "ID token");
        }

        if (expirationDays == null) {
            expirationDays = 7; // Default to 7 days
        } else if (expirationDays < 1 || expirationDays > 30) {
            throw new ValidationTranslatableException("error.validation.type_mismatch", "expirationDays");
        }

        try {


            // 1. Verify Firebase ID token (ONLY place we do this)
            FirebaseToken decodedToken = FirebaseAuth.getInstance()
                    .verifyIdToken(idToken);

            String firebaseUid = decodedToken.getUid();
            String email = decodedToken.getEmail();

            // GDPR: Log Firebase verification and data extraction
            log.info("GDPR: Operation=firebase_token_verified, FirebaseUID={}, DataAccessed=uid,email, Purpose=authentication", firebaseUid);

            // 2. SECURITY GATEWAY: Validate ALL roles via Firebase Admin SDK
            // NEVER trust embedded token claims for role-based access
            FirebaseRoleValidation roleValidation = validateFirebaseRole(firebaseUid);
            
            // 3. Enhanced verification for privileged roles
            if ("ADMIN".equals(roleValidation.role) || "COMPANY".equals(roleValidation.role)) {
                verifyTokenIntegrity(decodedToken, firebaseUid);
                log.info("Enhanced token verification passed for privileged role: {}", roleValidation.role);
            }
            
            String role = roleValidation.role;
            boolean requires2FASetup = roleValidation.requires2FASetup;
            boolean requires2FA = roleValidation.requires2FA;
            boolean twoFactorVerified = roleValidation.twoFactorVerified;
            
            // Log the validation result
            log.info("Firebase role validation complete - Role: {}, 2FA Required: {}, 2FA Verified: {}", 
                     role, requires2FA, twoFactorVerified);

            // 3. Find user in database (for business data only)
            User user = userRepository.findByFirebaseUserId(firebaseUid)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

            // GDPR: Log database access
            log.info("GDPR: DatabaseQuery=findByFirebaseUserId, FirebaseUID={}, Table=users, DataAccessed=user.id,user.email,user.userType, Purpose=session_creation", firebaseUid);

            // Sync email verification status from Firebase (using already-fetched user)
            boolean firebaseEmailVerified = decodedToken.isEmailVerified();
            emailVerificationService.syncEmailVerificationStatus(user, firebaseEmailVerified);

            // 4. Check user status in cache
            if (!userCacheService.isUserActive(firebaseUid)) {
                throw new AuthenticationTranslatableException("error.auth.account_disabled");
            }

            // 4b. Verify cookie policy consent — cookie first, DB fallback
            verifyCookiePolicyConsent(user, request);

            // 5. Create backend JWT with claims including session fingerprint
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", role); // Use validated role from Firebase
            claims.put("userId", user.getId().toString());
            claims.put("email", user.getEmail());
            claims.put("uid", firebaseUid);  // Add Firebase UID to claims
            claims.put("tokenVersion", user.getTokenVersion()); // For session invalidation on status change
            claims.put("accountStatus", user.getAccountStatus().name()); // For frontend to show appropriate UI
            claims.put("emailVerified", user.getEmailVerified()); // Email verification status for logging/metrics

            // Add role-specific claims based on Firebase validation
            if ("PENDING_ADMIN".equals(role)) {
                claims.put("requires2FASetup", true);
                claims.put("canAccessAdmin", false);
                // Note: Role stays as PENDING_ADMIN, not ADMIN
                // This prevents access to admin endpoints even with full session
            } else if ("ADMIN".equals(role)) {
                // Log the 2FA challenge age if present
                if (roleValidation.challengeAge >= 0) {
                    log.info("GDPR: Operation=ADMIN_2FA_CHALLENGE_CHECK, FirebaseUID={}, ChallengeAge={}ms, Valid={}", 
                        firebaseUid, roleValidation.challengeAge, twoFactorVerified);
                }
                
                // Use the already-validated 2FA status from Firebase
                if (twoFactorVerified) {
                    // 2FA is verified with valid adminChallengeCompletedAt timestamp! Create full session
                    claims.put("requiresTwoFactor", true);
                    claims.put("twoFactorEnabled", true);
                    claims.put("twoFactorVerified", true); // VERIFIED!
                    claims.put("canAccessAdmin", true); // Full admin access
                    claims.put("sessionType", "FULL");
                    log.info("ADMIN has valid 2FA challenge - issuing full session with challengeAge: {}ms", roleValidation.challengeAge);
                } else {
                    // 2FA not verified or expired, create partial token with restricted role
                    // SECURITY FIX: Use special challenged role instead of ADMIN
                    claims.put("role", "ADMIN_2FA_CHALLENGED"); // Override role for partial session
                    claims.put("actualRole", "ADMIN"); // Store actual role for after 2FA
                    claims.put("requiresTwoFactor", true);
                    claims.put("twoFactorEnabled", true);
                    claims.put("twoFactorVerified", false); // Must be FALSE for partial session
                    claims.put("canAccessAdmin", false); // No admin access until 2FA verified
                    claims.put("PARTIAL_AUTH", true); // Mark as partial authentication
                    claims.put("PENDING_2FA", true); // Specifically pending 2FA
                    claims.put("sessionType", "PARTIAL"); // Mark session type
                    claims.put("issuedAt", System.currentTimeMillis());
                    expirationDays = 0; // Will handle below for 10-minute expiry
                    log.info("ADMIN requires 2FA verification - issuing partial session with ADMIN_2FA_CHALLENGED role");
                }
            } else if ("COMPANY".equals(role)) {
                // COMPANY role - validated through Firebase
                claims.put("canAccessCompanyFeatures", true);
                claims.put("verifiedRole", true); // Mark as Firebase-verified
                log.info("COMPANY role verified via Firebase - granting company access");
            } else if ("INFLUENCER".equals(role)) {
                // INFLUENCER role - validated through Firebase
                claims.put("canAccessInfluencerFeatures", true);
                claims.put("verifiedRole", true); // Mark as Firebase-verified
                log.info("INFLUENCER role verified via Firebase - granting influencer access");
            } else {
                // Default USER role
                claims.put("canAccessBasicFeatures", true);
                claims.put("verifiedRole", true); // Even USER role is Firebase-verified
            }

            // Add session fingerprint components for security
            String clientIp = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            long sessionStart = System.currentTimeMillis();

            claims.put("sessionIp", clientIp);
            claims.put("sessionUA", userAgent != null ? userAgent : "unknown");
            claims.put("sessionStart", sessionStart);
            claims.put("fingerprint", generateSessionFingerprint(clientIp, userAgent));

            log.debug("Session fingerprint created for IP: {} with UA: {}", 
            maskIpForLogging(clientIp), userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "none");
            
            // Create token with appropriate expiration
            String backendJwt;
            int sessionDuration = getFullSessionDuration(role);
            // Check if this is a partial session (ADMIN without valid 2FA)
            boolean isPartialSession = "ADMIN".equals(role) && 
                                      claims.get("PARTIAL_AUTH") != null && 
                                      (Boolean) claims.get("PARTIAL_AUTH");
            
            if (isPartialSession) {
                // Create short-lived partial token for 2FA verification
                backendJwt = jwtTokenProvider.createTokenWithSecondsExpiry(
                    firebaseUid,
                    claims,
                    partialSessionDurationSeconds
                );
                log.debug("Creating partial session token with {} seconds expiry", partialSessionDurationSeconds);
            } else {
                // Regular token with role-based expiration (ADMIN: 2hr, others: 7d)
                backendJwt = jwtTokenProvider.createTokenWithSecondsExpiry(
                    firebaseUid,
                    claims,
                    sessionDuration
                );
                log.debug("Creating full session token with {} seconds expiry for role {}", sessionDuration, role);
            }

            // 5. Generate HMAC signature for cookie security
            String hmacSignature = HmacUtils.generateHMAC(backendJwt, cookieHmacSecret);

            // 6. Set secure HTTP-only cookies
            // Use different cookie names for partial auth vs full auth
            if (isPartialSession) {
                // Partial auth cookies for ADMIN without 2FA verification
                addSecureCookieWithSeconds(response, "partialSession", backendJwt, partialSessionDurationSeconds);
                addSecureCookieWithSeconds(response, "partialSessionSig", hmacSignature, partialSessionDurationSeconds);

                // For admins needing 2FA, preserve the FirebaseIdToken cookie
                // Don't clear it yet - it will be needed for the second exchange after 2FA
                if (firebaseTokenData != null && firebaseTokenData.isValid()) {
                    // Re-set the FirebaseIdToken cookies with same expiry
                    String tokenHmacSignature = HmacUtils.generateHMAC(idToken, cookieHmacSecret);

                    // Set with same expiry as partial session
                    int partialMinutes = partialSessionDurationSeconds / 60;
                    setFirebaseTokenCookieWithMinutes(response, "FirebaseIdToken", idToken, Math.max(1, partialMinutes));
                    setFirebaseTokenCookieWithMinutes(response, "FirebaseIdToken_sig", tokenHmacSignature, Math.max(1, partialMinutes));
                }

            } else {
                // Full session cookies (for regular users OR 2FA-verified admins)

                // For regular users or verified admins, clear Firebase token cookies
                if (firebaseTokenData != null) {
                    clearFirebaseTokenCookies(response);
                }

                // If this is an ADMIN who just verified 2FA, clear partial cookies first
                if ("ADMIN".equals(role) && twoFactorVerified) {
                    clearPartialSessionCookies(response);
                }

                addSecureCookieWithSeconds(response, "session", backendJwt, sessionDuration);
                addSecureCookieWithSeconds(response, "session_sig", hmacSignature, sessionDuration);

                // Link anonymous cookie consent record to user (same approach as registration)
                linkAnonymousCookieConsent(user, request);
            }



            // 7. Return response with cookie type information
            TokenExchangeResponse.TokenExchangeResponseBuilder responseBuilder = TokenExchangeResponse.builder()
                    .success(true)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(role) // Validated role from Firebase Admin SDK
                    .firebaseUid(firebaseUid)
                    .requires2FASetup(requires2FASetup)
                    .requires2FA(requires2FA);
            
            // Add cookie type information based on session type
            if (isPartialSession) {
                responseBuilder.cookieType("PARTIAL")
                              .sessionDuration(formatDuration(partialSessionDurationSeconds))
                              .twoFactorVerified(false);
            } else if ("ADMIN".equals(role) && twoFactorVerified) {
                responseBuilder.cookieType("FULL")
                              .sessionDuration(formatDuration(sessionDuration))
                              .twoFactorVerified(true);
            } else {
                responseBuilder.cookieType("FULL")
                              .sessionDuration(formatDuration(sessionDuration));
            }
            
            // GDPR: Log successful token exchange
            log.info("GDPR: Operation=exchangeToken_success, FirebaseUID={}, SessionType={}, Purpose=authentication_complete", 
                firebaseUid, isPartialSession ? "PARTIAL" : "FULL");
            
            return responseBuilder.build();

        } catch (FirebaseAuthException e) {
            log.error("GDPR: Operation=exchangeToken_failed, Error=firebase_auth_error, Details={}, Purpose=error_logging", e.getMessage());
            log.error("Firebase token validation failed: {}", e.getMessage());
            log.error("Firebase error code: {}", e.getErrorCode());
            log.error("Firebase auth error details: ", e);
            throw new AuthenticationTranslatableException("error.auth.invalid_token");

        } catch (AuthenticationTranslatableException | ValidationTranslatableException | ConsentRequiredTranslatableException e) {
            // Re-throw translatable exceptions - they will be handled by exception handlers
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during token exchange", e);
            throw new AuthenticationTranslatableException("error.auth.token_exchange_failed");
        }
    }

    /**
     * Get and validate OAuth cookies with HMAC
     */
    private OAuthCookieData getValidatedOAuthCookie(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() == null) {
            return null;
        }

        String customToken = null;
        String providedSignature = null;

        // Extract both cookies
        for (Cookie cookie : request.getCookies()) {
            if ("oauth_token".equals(cookie.getName())) {
                customToken = cookie.getValue();
            } else if ("oauth_sig".equals(cookie.getName())) {
                providedSignature = cookie.getValue();
            }
        }

        // Both must be present
        if (customToken == null || providedSignature == null) {
            log.debug("OAuth cookies not found or incomplete");
            return null;
        }

        // VALIDATE HMAC SIGNATURE
        String expectedSignature = HmacUtils.generateHMAC(customToken, cookieHmacSecret);

        // Constant-time comparison to prevent timing attacks
        if (!HmacUtils.constantTimeEquals(providedSignature, expectedSignature)) {
            log.error("OAuth cookie HMAC validation failed - possible tampering!");

            // Clear invalid cookies immediately
            clearOAuthCookies(response);

            // Log security event
            logSecurityEvent("OAUTH_HMAC_FAILURE", request);

            return null;
        }

        log.debug("OAuth cookie HMAC validation successful");
        return new OAuthCookieData(customToken, true);
    }

    /**
     * Exchange validated custom token from OAuth flow.
     * IMPROVED: Uses Firebase UID as primary key for Firestore lookup.
     * Validates roles via Firebase Admin SDK, then checks Instagram token freshness.
     */
    private TokenExchangeResponse exchangeCustomToken(
            String customToken,
            Integer expirationDays,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            // GDPR: Log OAuth custom token exchange
            log.info("GDPR: Operation=exchangeCustomToken_entry, Purpose=oauth_authentication, DataAccessed=custom_token");
            
            // The custom token is already HMAC-validated in getValidatedOAuthCookie()
            
            // 1. Extract Firebase UID from custom token (the subject/uid field)
            String firebaseUid = extractUidFromCustomToken(customToken);
            log.info("OAuth: Extracted Firebase UID: {} from custom token", firebaseUid);
            
            // 2. SECURITY GATEWAY: Validate role via Firebase Admin SDK FIRST
            FirebaseRoleValidation roleValidation = validateFirebaseRole(firebaseUid);
            log.info("OAuth: Role validated via Firebase Admin SDK - Role: {}, 2FA Required: {}", 
                     roleValidation.role, roleValidation.requires2FA);
            
            // 3. Query Firestore using Firebase UID to get Instagram data
            Map<String, Object> instagramDoc = firestoreService.getInstagramUserDataByFirebaseUid(firebaseUid);
            
            if (instagramDoc == null) {
                log.error("Instagram user document not found in Firestore for Firebase UID: {}", firebaseUid);
                clearOAuthCookies(response);
                throw new AuthenticationTranslatableException("error.auth.instagram_link_failed");
            }
            
            // 4. Extract Instagram data from Firestore document
            String instagramId = (String) instagramDoc.get("instagramId");
            String instagramUsername = (String) instagramDoc.get("username");
            
            // GDPR: Log data access without exposing token
            log.info("GDPR: Operation=firestore_read, FirebaseUID={}, DataAccessed=instagram_profile,encrypted_token, Purpose=oauth_validation", firebaseUid);
            
            // 5. ENHANCED VALIDATION: Verify Instagram data freshness
            // The access token is already decrypted by FirestoreService
            if (shouldValidateWithInstagramAPI(instagramDoc, roleValidation.role)) {
                validateInstagramTokenFreshness(instagramDoc, instagramId, instagramUsername);
            }
            
            
            // 6. Verify Firebase user exists
            boolean firebaseEmailVerified;
            try {
                com.google.firebase.auth.UserRecord firebaseUser = FirebaseAuth.getInstance().getUser(firebaseUid);
                firebaseEmailVerified = firebaseUser.isEmailVerified();
                log.info("OAuth: Firebase user verified - Email: {}", firebaseUser.getEmail());
            } catch (FirebaseAuthException e) {
                log.error("Firebase user not found for UID: {}", firebaseUid);
                clearOAuthCookies(response);
                throw new AuthenticationTranslatableException("error.auth.firebase_account_not_found");
            }

            // 7. Find user in database using Firebase UID
            User user = userRepository.findByFirebaseUserId(firebaseUid)
                    .orElseThrow(() -> {
                        log.error("User not found in database for Firebase UID: {}", firebaseUid);
                        return new ResourceNotFoundException("error.business.item_not_found", "User");
                    });

            // GDPR: Log database access for OAuth user
            log.info("GDPR: DatabaseQuery=findByFirebaseUserId_oauth, FirebaseUID={}, Table=users, DataAccessed=user.profile, Purpose=oauth_session_creation", firebaseUid);

            // Sync email verification status from Firebase with PG-wins guard
            emailVerificationService.syncEmailVerificationStatus(user, firebaseEmailVerified);

            // 8. Clear OAuth cookies immediately (one-time use)
            clearOAuthCookies(response);
            
            // 9. Create HMAC-signed session cookies with validated role
            createSecureSessionCookies(user, firebaseUid, expirationDays, request, response);

            // GDPR: Log successful OAuth token exchange
            log.info("GDPR: Operation=exchangeCustomToken_success, FirebaseUID={}, Purpose=oauth_authentication_complete", firebaseUid);
            
            return TokenExchangeResponse.builder()
                    .success(true)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(roleValidation.role) // Use Firebase-validated role
                    .firebaseUid(firebaseUid)
                    .requires2FA(roleValidation.requires2FA)
                    .twoFactorVerified(roleValidation.twoFactorVerified)
                    .build();

        } catch (AuthenticationTranslatableException | ValidationTranslatableException e) {
            // Re-throw translatable exceptions with proper logging
            log.error("GDPR: Operation=exchangeCustomToken_failed, Error=auth_exception, Details={}, Purpose=error_logging", e.getMessage());
            clearOAuthCookies(response);
            throw e;
        } catch (Exception e) {
            log.error("GDPR: Operation=exchangeCustomToken_failed, Error={}, Purpose=error_logging", e.getMessage());
            log.error("Custom token exchange failed", e);
            clearOAuthCookies(response);
            throw new AuthenticationTranslatableException("error.auth.oauth_failed");
        }
    }


    
    /**
     * Extract UID from custom token (Firebase custom tokens have UID in "uid" field).
     * Note: For OAuth tokens, this returns Instagram ID, not Firebase UID!
     */
    private String extractUidFromCustomToken(String customToken) {
        try {
            String[] parts = customToken.split("\\.");
            if (parts.length != 3) {
                throw new AuthenticationTranslatableException("error.auth.invalid_token");
            }

            // Decode and parse the payload (middle part) as JSON
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode json = JWT_PAYLOAD_MAPPER.readTree(payload);

            // Firebase custom tokens: "uid" field has user UID, "sub" has service account
            String uid = json.path("uid").asText(null);
            if (uid != null && !uid.contains("@") && !uid.contains("firebase-adminsdk")) {
                return uid;
            }

            // Fallback: try "sub" field if it's not the service account
            String sub = json.path("sub").asText(null);
            if (sub != null && !sub.contains("@") && !sub.contains("firebase-adminsdk")) {
                return sub;
            }

            log.error("Could not find valid UID in token payload");
            throw new AuthenticationTranslatableException("error.auth.invalid_token");

        } catch (AuthenticationTranslatableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract UID from custom token", e);
            throw new AuthenticationTranslatableException("error.auth.invalid_token");
        }
    }

    /**
     * Clear OAuth cookies securely
     * Uses SameSite=Lax for OAuth cookies to support redirect flow
     */
    private void clearOAuthCookies(HttpServletResponse response) {
        // OAuth cookies need SameSite=Lax for redirect compatibility
        String sameSite = "Lax";
        String secureFlag = secureCookies ? "; Secure" : "";

        // Build domain string
        String domain = "";
        if (!"localhost".equals(cookieDomain) && cookieDomain != null && !cookieDomain.isEmpty()) {
            domain = String.format("; Domain=%s", cookieDomain);
        }

        // Clear WITH domain
        response.addHeader("Set-Cookie", String.format("oauth_token=; Max-Age=0; Path=/; HttpOnly%s; SameSite=%s%s", secureFlag, sameSite, domain));
        response.addHeader("Set-Cookie", String.format("oauth_sig=; Max-Age=0; Path=/; HttpOnly%s; SameSite=%s%s", secureFlag, sameSite, domain));
        response.addHeader("Set-Cookie", String.format("oauth_meta=; Max-Age=0; Path=/; SameSite=%s%s", sameSite, domain));

        // Clear WITHOUT domain (cookies set without domain are separate in the browser)
        if (!domain.isEmpty()) {
            response.addHeader("Set-Cookie", String.format("oauth_token=; Max-Age=0; Path=/; HttpOnly%s; SameSite=%s", secureFlag, sameSite));
            response.addHeader("Set-Cookie", String.format("oauth_sig=; Max-Age=0; Path=/; HttpOnly%s; SameSite=%s", secureFlag, sameSite));
            response.addHeader("Set-Cookie", String.format("oauth_meta=; Max-Age=0; Path=/; SameSite=%s", sameSite));
        }

        log.debug("Cleared OAuth cookies with SameSite=Lax (with and without domain)");
    }

    /**
     * Create HMAC-signed session cookies with request fingerprinting
     */
    private void createSecureSessionCookies(
            User user,
            String firebaseUid,
            Integer expirationDays,
            HttpServletRequest request,
            HttpServletResponse response) {

        // SECURITY GATEWAY: Validate role via Firebase Admin SDK
        FirebaseRoleValidation roleValidation = validateFirebaseRole(firebaseUid);
        String verifiedRole = roleValidation.role;

        // Use role-based session duration (expirationDays parameter is now ignored)
        int sessionDurationSeconds = getFullSessionDuration(verifiedRole);
        
        // Create JWT with session fingerprint
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", verifiedRole); // Use Firebase-verified role, not DB role
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail() != null ? user.getEmail() : "");
        claims.put("verifiedRole", true); // Mark as Firebase-verified
        claims.put("tokenVersion", user.getTokenVersion()); // For session invalidation on status change
        claims.put("accountStatus", user.getAccountStatus().name()); // For frontend to show appropriate UI

        // Add role-specific claims based on Firebase validation
        if ("ADMIN".equals(verifiedRole)) {
            claims.put("requiresTwoFactor", true);
            claims.put("twoFactorEnabled", twoFactorAuthService.is2FAEnabled(firebaseUid));
            claims.put("twoFactorVerified", roleValidation.twoFactorVerified);
            claims.put("canAccessAdmin", roleValidation.twoFactorVerified);
            
            if (roleValidation.requires2FASetup) {
                claims.put("requires2FASetup", true);
            }
        } else if ("PENDING_ADMIN".equals(verifiedRole)) {
            // PENDING_ADMIN needs to setup 2FA
            claims.put("requires2FASetup", true);
            claims.put("canAccessAdmin", false);
        } else if ("COMPANY".equals(verifiedRole)) {
            claims.put("canAccessCompanyFeatures", true);
            claims.put("verifiedRole", true);
        } else if ("INFLUENCER".equals(verifiedRole)) {
            claims.put("canAccessInfluencerFeatures", true);
            claims.put("verifiedRole", true);
        }

        // Session fingerprint for security - attempt to get from request if available
        String clientIp = request != null ? getClientIpAddress(request) : "oauth-internal";
        String userAgent = request != null ? request.getHeader("User-Agent") : "oauth-internal";
        long sessionStart = System.currentTimeMillis();

        claims.put("sessionIp", clientIp);
        claims.put("sessionUA", userAgent != null ? userAgent : "unknown");
        claims.put("sessionStart", sessionStart);
        claims.put("fingerprint", generateSessionFingerprint(clientIp, userAgent));

        log.debug("OAuth session fingerprint for IP: {} with UA: {}",
                maskIpForLogging(clientIp), userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "none");

        String backendJwt = jwtTokenProvider.createTokenWithSecondsExpiry(
                firebaseUid,
                claims,
                sessionDurationSeconds
        );

        // Generate HMAC for session (using existing session secret)
        String sessionHmac = HmacUtils.generateHMAC(backendJwt, cookieHmacSecret);

        // Set session cookies
        addSecureCookieWithSeconds(response, "session", backendJwt, sessionDurationSeconds);
        addSecureCookieWithSeconds(response, "session_sig", sessionHmac, sessionDurationSeconds);

        log.info("Created HMAC-signed session cookies for user: {} (duration: {})", user.getId(), formatDuration(sessionDurationSeconds));
    }

    /**
     * Log security events for monitoring
     */
    private void logSecurityEvent(String eventType, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        log.warn("SECURITY EVENT: {} | IP: {} | UA: {}",
                eventType, clientIp, userAgent);

        // In production, send to SIEM or security monitoring service
        // metricsService.recordSecurityEvent(eventType, clientIp);
    }
    
    /**
     * Method to validate Firebase ID token cookie with HMAC
     */
    private FirebaseTokenCookieData getValidatedFirebaseTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        String firebaseToken = null;
        String providedSignature = null;

        // Extract both cookies
        for (Cookie cookie : request.getCookies()) {
            if ("FirebaseIdToken".equals(cookie.getName())) {
                firebaseToken = cookie.getValue();
            } else if ("FirebaseIdToken_sig".equals(cookie.getName())) {
                providedSignature = cookie.getValue();
            }
        }

        // Both must be present
        if (firebaseToken == null || providedSignature == null) {
            log.debug("Firebase token cookies not found or incomplete");
            return null;
        }

        // VALIDATE HMAC SIGNATURE
        String expectedSignature = HmacUtils.generateHMAC(firebaseToken, cookieHmacSecret);

        // Constant-time comparison to prevent timing attacks
        if (!HmacUtils.constantTimeEquals(providedSignature, expectedSignature)) {
            log.error("Firebase token cookie HMAC validation failed - possible tampering!");
            
            // Log security event
            logSecurityEvent("FIREBASE_TOKEN_HMAC_FAILURE", request);
            
            return null;
        }

        log.debug("Firebase token cookie HMAC validation successful");
        return new FirebaseTokenCookieData(firebaseToken, true);
    }
    
    /**
     * Clear Firebase token cookies
     */
    private void clearFirebaseTokenCookies(HttpServletResponse response) {
        String sameSite = "Strict";
        
        String domain = "";
        if (!"localhost".equals(cookieDomain) && cookieDomain != null) {
            domain = String.format("; Domain=%s", cookieDomain);
        }
        
        String secureFlag = secureCookies ? "; Secure" : "";
        
        // Clear token cookie
        String tokenCookie = String.format(
            "FirebaseIdToken=; Max-Age=0; Path=/; HttpOnly%s; SameSite=%s%s",
            secureFlag, sameSite, domain
        );
        response.addHeader("Set-Cookie", tokenCookie);
        
        // Clear signature cookie
        String sigCookie = String.format(
            "FirebaseIdToken_sig=; Max-Age=0; Path=/; HttpOnly%s; SameSite=%s%s",
            secureFlag, sameSite, domain
        );
        response.addHeader("Set-Cookie", sigCookie);
        
        log.debug("Cleared Firebase token cookies");
    }
    
    /**
     * Helper method to set Firebase token cookies with minute expiry.
     */
    private void setFirebaseTokenCookieWithMinutes(HttpServletResponse response, 
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
        log.debug("Set Firebase token cookie: {} for {} minutes", name, maxAgeMinutes);
    }

    /**
     * Helper class for OAuth cookie data
     */
    private static class OAuthCookieData {
        final String token;
        final boolean valid;

        OAuthCookieData(String token, boolean valid) {
            this.token = token;
            this.valid = valid;
        }

        boolean isValid() {
            return valid && token != null && !token.isEmpty();
        }
    }
    
    /**
     * Helper class for Firebase token cookie data
     */
    private static class FirebaseTokenCookieData {
        final String token;
        final boolean valid;

        FirebaseTokenCookieData(String token, boolean valid) {
            this.token = token;
            this.valid = valid;
        }

        boolean isValid() {
            return valid && token != null && !token.isEmpty();
        }
    }

    /**
     * Inner class to hold Firebase role validation results.
     */
    private static class FirebaseRoleValidation {
        final String role;
        final boolean requires2FASetup;
        final boolean requires2FA;
        final boolean twoFactorVerified;
        final long challengeAge;  // milliseconds since challenge completed
        
        FirebaseRoleValidation(String role, boolean requires2FASetup, boolean requires2FA, 
                              boolean twoFactorVerified, long challengeAge) {
            this.role = role;
            this.requires2FASetup = requires2FASetup;
            this.requires2FA = requires2FA;
            this.twoFactorVerified = twoFactorVerified;
            this.challengeAge = challengeAge;
        }
    }
    
    /**
     * SECURITY GATEWAY: Validate ALL roles via Firebase Admin SDK.
     * This is the single source of truth for role-based access control.
     * NEVER trust embedded token claims - always fetch fresh from Firebase.
     * 
     * @param uid Firebase user ID
     * @return FirebaseRoleValidation with complete role and 2FA status
     */
    private FirebaseRoleValidation validateFirebaseRole(String uid) {
        try {
            // ALWAYS fetch fresh claims from Firebase Admin SDK
            // This ensures we have the latest role and never trust stale token claims
            com.google.firebase.auth.UserRecord userRecord = FirebaseAuth.getInstance().getUser(uid);
            Map<String, Object> claims = userRecord.getCustomClaims();
            
            // Extract role - default to USER if not set
            String role = (String) claims.get("role");
            if (role == null || role.isEmpty()) {
                role = "USER";
                log.info("No role claim found for user {}, defaulting to USER", uid);
            }
            
            // Log role validation from Firebase
            log.info("GDPR: Operation=ROLE_VALIDATION, FirebaseUID={}, Role={}, Source=FIREBASE_ADMIN_SDK", uid, role);
            
            // Initialize 2FA flags - default to false
            boolean requires2FASetup = false;
            boolean requires2FA = false;
            boolean twoFactorVerified = false;
            long challengeAge = -1;
            
            // Handle role-specific logic
            switch (role) {
                case "ADMIN":
                    // ADMIN requires 2FA verification with adminChallengeCompletedAt claim
                    // Handle numeric conversion properly - Firebase can return BigDecimal, Integer, or Long
                    Long adminChallengeCompletedAt = null;
                    Object challengeObj = claims.get("adminChallengeCompletedAt");
                    if (challengeObj instanceof Number) {
                        adminChallengeCompletedAt = ((Number) challengeObj).longValue();
                    }
                    
                    if (adminChallengeCompletedAt != null && adminChallengeCompletedAt > 0) {
                        // Calculate age of challenge completion
                        long currentTime = System.currentTimeMillis();
                        challengeAge = currentTime - adminChallengeCompletedAt;
                        long maxValidTime = 2 * 60 * 1000; // 2 minutes
                        
                        if (challengeAge >= 0 && challengeAge <= maxValidTime) {
                            // Challenge completed and still valid
                            twoFactorVerified = true;
                            requires2FA = false;
                            log.info("GDPR: Operation=2FA_CHALLENGE_VALIDATED, FirebaseUID={}, ChallengeAge={}ms, Result=VALID", uid, challengeAge);
                        } else {
                            // Challenge expired
                            twoFactorVerified = false;
                            requires2FA = true;
                            log.info("GDPR: Operation=2FA_CHALLENGE_EXPIRED, FirebaseUID={}, ChallengeAge={}ms, Result=EXPIRED", uid, challengeAge);
                        }
                    } else {
                        // No challenge completed
                        twoFactorVerified = false;
                        requires2FA = true;
                        log.info("GDPR: Operation=2FA_CHALLENGE_MISSING, FirebaseUID={}, Result=NOT_VERIFIED", uid);
                    }
                    break;
                    
                case "PENDING_ADMIN":
                    // PENDING_ADMIN may have already set up 2FA (e.g., if role transition failed)
                    // Check Firestore to determine if user already has 2FA configured
                    try {
                        boolean has2FAConfigured = totpFirestoreService.is2FAEnabled(uid);

                        if (has2FAConfigured) {
                            // User already has 2FA in Firestore -> show TOTP verification screen
                            requires2FASetup = false;
                            requires2FA = true;
                            twoFactorVerified = false;
                            log.info("PENDING_ADMIN {} has existing 2FA in Firestore - requiring TOTP verification", uid);
                        } else {
                            // User doesn't have 2FA in Firestore -> show setup screen
                            requires2FASetup = true;
                            requires2FA = false;
                            twoFactorVerified = false;
                            log.info("PENDING_ADMIN {} needs 2FA setup (no 2FA found in Firestore)", uid);
                        }
                    } catch (TwoFactorAuthException e) {
                        // Firestore error - fail safely rather than showing wrong screen
                        log.error("Cannot determine 2FA status for PENDING_ADMIN {} - Firestore error: {}",
                                uid, e.getMessage());
                        throw new AuthenticationTranslatableException("error.auth.2fa_status_unavailable");
                    }
                    break;
                    
                case "COMPANY":
                    // Company role - no 2FA required, just validate role from Firebase
                    requires2FASetup = false;
                    requires2FA = false;
                    twoFactorVerified = false;
                    log.info("COMPANY role validated via Firebase Admin SDK");
                    break;
                    
                case "INFLUENCER":
                    // Influencer role - no 2FA required, just validate role from Firebase
                    requires2FASetup = false;
                    requires2FA = false;
                    twoFactorVerified = false;
                    log.info("INFLUENCER role validated via Firebase Admin SDK");
                    break;
                    
                default:
                    // Default USER role - no special requirements
                    requires2FASetup = false;
                    requires2FA = false;
                    twoFactorVerified = false;
                    log.info("USER role validated via Firebase Admin SDK");
                    break;
            }
            
            return new FirebaseRoleValidation(role, requires2FASetup, requires2FA, twoFactorVerified, challengeAge);
            
        } catch (Exception e) {
            log.error("Failed to validate role via Firebase Admin SDK for user {}: {}", uid, e.getMessage());
            throw new ExternalServiceException("error.network.external_service_unavailable", "Firebase", "validateFirebaseRole", e);
        }
    }

    /**
     * Create session cookies directly for OAuth-authenticated users.
     * This bypasses Firebase ID token validation since we've already verified
     * the user through OAuth and created/updated their Firebase account.
     * <p>
     * Used exclusively by OAuth callback after successful authentication.
     *
     * @param user        Verified user from database
     * @param firebaseUid Firebase UID (already verified to exist)
     * @param response    HTTP response for setting cookies
     */
    @Transactional(readOnly = true)
    public void createOAuthSessionCookies(User user, String firebaseUid,
                                          HttpServletRequest request, HttpServletResponse response) {
        log.info("Creating OAuth session cookies for user: {}", user.getId());
        
        // GDPR: Log OAuth session creation
        log.info("GDPR: Operation=createOAuthSessionCookies, FirebaseUID={}, DataAccessed=user.profile,user.status, Purpose=oauth_session_initialization", firebaseUid);

        try {
            // Verify user is active
            if (!userCacheService.isUserActive(firebaseUid)) {
                throw new AuthenticationTranslatableException("error.auth.account_disabled");
            }

            // SECURITY GATEWAY: Validate role via Firebase Admin SDK for OAuth flow too
            FirebaseRoleValidation roleValidation = validateFirebaseRole(firebaseUid);
            String verifiedRole = roleValidation.role;
            
            log.info("OAuth user role validated via Firebase: Role={}, 2FA Required={}", 
                     verifiedRole, roleValidation.requires2FA);

            // Create backend JWT with claims including session fingerprint
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", verifiedRole); // Use Firebase-verified role
            claims.put("userId", user.getId().toString());
            claims.put("email", user.getEmail() != null ? user.getEmail() : "");
            claims.put("verifiedRole", true); // Mark as Firebase-verified
            claims.put("tokenVersion", user.getTokenVersion()); // For session invalidation on status change
            claims.put("accountStatus", user.getAccountStatus().name()); // For frontend to show appropriate UI

            // Add role-specific claims based on Firebase validation
            if ("ADMIN".equals(verifiedRole)) {
                // Check if 2FA is verified
                if (!roleValidation.twoFactorVerified) {
                    // SECURITY FIX: OAuth admins without 2FA get challenged role
                    claims.put("role", "ADMIN_2FA_CHALLENGED"); // Override role
                    claims.put("actualRole", "ADMIN"); // Store actual role for after 2FA
                }
                claims.put("requiresTwoFactor", true);
                claims.put("twoFactorEnabled", twoFactorAuthService.is2FAEnabled(firebaseUid));
                claims.put("twoFactorVerified", roleValidation.twoFactorVerified); // Will be false for OAuth
                claims.put("canAccessAdmin", roleValidation.twoFactorVerified);
                
                if (roleValidation.requires2FASetup) {
                    claims.put("requires2FASetup", true);
                }
            } else if ("PENDING_ADMIN".equals(verifiedRole)) {
                // PENDING_ADMIN needs to setup 2FA
                claims.put("requires2FASetup", true);
                claims.put("canAccessAdmin", false);
            } else if ("COMPANY".equals(verifiedRole)) {
                claims.put("canAccessCompanyFeatures", true);
            } else if ("INFLUENCER".equals(verifiedRole)) {
                claims.put("canAccessInfluencerFeatures", true);
            }

            // Mark as OAuth-authenticated
            claims.put("oauth", true);
            claims.put("provider", "instagram");

            // Add session fingerprint using real request context
            String clientIp = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            long sessionStart = System.currentTimeMillis();

            claims.put("sessionIp", clientIp);
            claims.put("sessionUA", userAgent != null ? userAgent : "unknown");
            claims.put("sessionStart", sessionStart);
            claims.put("fingerprint", generateSessionFingerprint(clientIp, userAgent));

            log.debug("OAuth direct session fingerprint for IP: {} with UA: {}",
                    maskIpForLogging(clientIp), userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "none");

            // Add account status for new users
            if (user.getAccountStatus() != null) {
                claims.put("status", user.getAccountStatus().toString());
                if ("IN_VALIDATION".equals(user.getAccountStatus().toString())) {
                    claims.put("needsOnboarding", true);
                }
            }

            // Use role-based session duration
            int oauthSessionDuration = getFullSessionDuration(verifiedRole);
            String backendJwt = jwtTokenProvider.createTokenWithSecondsExpiry(
                    firebaseUid,
                    claims,
                    oauthSessionDuration
            );

            // Generate HMAC signature for cookie security
            String hmacSignature = HmacUtils.generateHMAC(backendJwt, cookieHmacSecret);

            // Set secure HTTP-only cookies
            addSecureCookieWithSeconds(response, "session", backendJwt, oauthSessionDuration);
            addSecureCookieWithSeconds(response, "session_sig", hmacSignature, oauthSessionDuration);

            log.info("OAuth session cookies created successfully for user: {} (duration: {})", user.getId(), formatDuration(oauthSessionDuration));

            // GDPR: Log successful OAuth session creation
            log.info("GDPR: Operation=createOAuthSessionCookies_success, FirebaseUID={}, SessionDuration={}, Purpose=oauth_session_established", firebaseUid, formatDuration(oauthSessionDuration));

        } catch (AuthenticationTranslatableException | ValidationTranslatableException e) {
            log.error("GDPR: Operation=createOAuthSessionCookies_failed, FirebaseUID={}, Error=auth_exception, Purpose=error_logging", firebaseUid);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create OAuth session cookies", e);
            throw new AuthenticationTranslatableException("error.auth.session_creation_failed");
        }
    }

    /**
     * Create or update session after successful 2FA verification.
     * This method is called after an admin user successfully verifies their TOTP code.
     *
     * @param user        The authenticated user
     * @param firebaseUid Firebase UID
     * @param response    HTTP response for setting cookies
     * @return Token exchange response with updated 2FA status
     */
    @Transactional(readOnly = true)
    public TokenExchangeResponse createTwoFactorVerifiedSession(User user, String firebaseUid,
                                                                HttpServletRequest request,
                                                                HttpServletResponse response) {
        log.info("Creating 2FA-verified session for admin user: {}", user.getId());
        
        // GDPR: Log 2FA verification session creation
        log.info("GDPR: Operation=createTwoFactorVerifiedSession, FirebaseUID={}, DataAccessed=user.profile,2fa_status, Purpose=admin_authentication", firebaseUid);

        try {
            // Verify user is active
            if (!userCacheService.isUserActive(firebaseUid)) {
                throw new AuthenticationTranslatableException("error.auth.account_disabled");
            }

            // SECURITY GATEWAY: Validate role via Firebase Admin SDK (not DB)
            FirebaseRoleValidation roleValidation = validateFirebaseRole(firebaseUid);
            String verifiedRole = roleValidation.role;

            // Create backend JWT with 2FA verified claims
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", verifiedRole);
            claims.put("userId", user.getId().toString());
            claims.put("email", user.getEmail());
            claims.put("tokenVersion", user.getTokenVersion()); // For session invalidation on status change
            claims.put("accountStatus", user.getAccountStatus().name()); // For frontend to show appropriate UI

            // Add 2FA claims - now verified
            if ("ADMIN".equals(verifiedRole) || "PENDING_ADMIN".equals(verifiedRole)) {
                claims.put("requiresTwoFactor", true);
                claims.put("twoFactorEnabled", true);
                claims.put("twoFactorVerified", true); // Key difference - now verified

                // PENDING_ADMIN can now access admin features after 2FA verification
                if ("PENDING_ADMIN".equals(verifiedRole)) {
                    claims.put("canAccessAdmin", true);
                }
            }

            // Add session fingerprint
            String clientIp = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            long sessionStart = System.currentTimeMillis();

            claims.put("sessionIp", clientIp);
            claims.put("sessionUA", userAgent != null ? userAgent : "unknown");
            claims.put("sessionStart", sessionStart);
            claims.put("fingerprint", generateSessionFingerprint(clientIp, userAgent));

            // Create token with role-based duration (ADMIN: 2hr)
            int adminSessionDuration = getFullSessionDuration(verifiedRole);
            String backendJwt = jwtTokenProvider.createTokenWithSecondsExpiry(
                    firebaseUid,
                    claims,
                    adminSessionDuration
            );

            // Generate HMAC signature
            String hmacSignature = HmacUtils.generateHMAC(backendJwt, cookieHmacSecret);

            // Set secure HTTP-only cookies
            addSecureCookieWithSeconds(response, "session", backendJwt, adminSessionDuration);
            addSecureCookieWithSeconds(response, "session_sig", hmacSignature, adminSessionDuration);

            log.info("2FA-verified session created successfully for admin: {} (duration: {})", user.getId(), formatDuration(adminSessionDuration));

            // GDPR: Log successful 2FA session creation
            log.info("GDPR: Operation=createTwoFactorVerifiedSession_success, FirebaseUID={}, SessionType=FULL_ADMIN, Duration={}, Purpose=admin_access_granted", firebaseUid, formatDuration(adminSessionDuration));

            return TokenExchangeResponse.builder()
                    .success(true)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(verifiedRole)
                    .firebaseUid(firebaseUid)
                    .twoFactorVerified(true)
                    .cookieType("FULL")  // Full session after 2FA verification
                    .sessionDuration(formatDuration(adminSessionDuration))
                    .build();

        } catch (AuthenticationTranslatableException | ValidationTranslatableException e) {
            log.error("Failed to create 2FA-verified session: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create 2FA-verified session", e);
            throw new AuthenticationTranslatableException("error.auth.session_creation_failed");
        }
    }

    /**
     * Create a limited session for 2FA verification.
     * This creates a short-lived token that only allows access to 2FA endpoints.
     *
     * @param user        The user needing 2FA
     * @param firebaseUid Firebase UID
     * @param response    HTTP response
     * @return Limited token response
     */
    public TokenExchangeResponse createLimited2FASession(User user, String firebaseUid,
                                                         HttpServletRequest request,
                                                         HttpServletResponse response) {
        log.info("Creating limited 2FA session for user: {}", user.getId());

        // SECURITY GATEWAY: Validate role via Firebase Admin SDK (not DB)
        FirebaseRoleValidation roleValidation = validateFirebaseRole(firebaseUid);
        String verifiedRole = roleValidation.role;

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", verifiedRole);
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("requiresTwoFactor", true);
        claims.put("twoFactorVerified", false);
        claims.put("limitedFor2FA", true); // Mark as limited session
        claims.put("tokenVersion", user.getTokenVersion()); // For session invalidation
        claims.put("accountStatus", user.getAccountStatus().name()); // For status checks

        // Create short-lived token (5 minutes)
        String limitedJwt = jwtTokenProvider.createTokenWithClaims(
                firebaseUid,
                claims,
                5 * 60 * 1000 // 5 minutes in milliseconds
        );

        // Generate HMAC
        String hmacSignature = HmacUtils.generateHMAC(limitedJwt, cookieHmacSecret);

        // Set temporary cookies
        addSecureCookie(response, "session_temp", limitedJwt, 1); // 1 day max but token expires in 5 min
        addSecureCookie(response, "session_temp_sig", hmacSignature, 1);

        return TokenExchangeResponse.builder()
                .success(true)
                .userId(user.getId())
                .email(user.getEmail())
                .role(verifiedRole)
                .firebaseUid(firebaseUid)
                .requires2FA(true)
                .twoFactorVerified(false)
                .build();
    }

    /**
     * Clear session cookies.
     *
     * @param response HTTP response for clearing cookies
     */
    public void clearSessionCookies(HttpServletResponse response) {
        // GDPR: Log session termination
        log.info("GDPR: Operation=clearSessionCookies, Purpose=session_termination, DataCleared=all_auth_cookies");

        // Clear session cookies
        addClearCookie(response, "session");
        addClearCookie(response, "session_sig");
        addClearCookie(response, "partialSession");
        addClearCookie(response, "partialSessionSig");
        addClearCookie(response, "session_temp");
        addClearCookie(response, "session_temp_sig");

        // Clear Firebase intermediate cookies (defense in depth)
        clearFirebaseTokenCookies(response);

        // Clear OAuth intermediate cookies (defense in depth)
        clearOAuthCookies(response);

        log.debug("All authentication cookies cleared");
    }
    
    /**
     * Clear only partial session cookies.
     * Used after successful 2FA verification.
     *
     * @param response HTTP response for clearing cookies
     */
    public void clearPartialSessionCookies(HttpServletResponse response) {
        addClearCookie(response, "partialSession");
        addClearCookie(response, "partialSessionSig");
        log.debug("Partial session cookies cleared");
    }

    /**
     * Add a secure HTTP-only cookie with expiration in seconds.
     * Used for short-lived cookies like partial session tokens.
     *
     * @param response HTTP response
     * @param name     Cookie name
     * @param value    Cookie value
     * @param maxAgeSeconds Expiration in seconds
     */
    private void addSecureCookieWithSeconds(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        // Use Strict for all session cookies for maximum security
        // This prevents CSRF attacks by not sending cookies on cross-site requests
        String sameSite = "Strict";
        
        // Build the complete cookie string with all attributes
        String domain = "";
        if (!"localhost".equals(cookieDomain) && cookieDomain != null && !cookieDomain.isEmpty()) {
            domain = String.format("; Domain=%s", cookieDomain);
        }
        
        // CRITICAL: For localhost, don't set Secure flag in development
        // Check if we're in development mode (localhost without HTTPS)
        boolean isLocalDev = "localhost".equals(cookieDomain) && !secureCookies;
        String secureFlag = isLocalDev ? "" : "; Secure";
        
        String cookieString = String.format(
            "%s=%s; Max-Age=%d; Path=/; HttpOnly%s; SameSite=%s%s",
            name, 
            value, 
            maxAgeSeconds,  // Use seconds directly
            secureFlag,
            sameSite,
            domain
        );
        

        
        // Use addHeader to set the cookie with full control
        response.addHeader("Set-Cookie", cookieString);
        

    }

    /**
     * Add a secure HTTP-only cookie.
     * ALL environments use HTTPS (including localhost), so Secure flag is always true.
     *
     * @param response       HTTP response
     * @param name           Cookie name
     * @param value          Cookie value
     * @param expirationDays Expiration in days
     */
    private void addSecureCookie(HttpServletResponse response, String name, String value, int expirationDays) {
        // Convert days to seconds and use the common method
        addSecureCookieWithSeconds(response, name, value, expirationDays * 24 * 60 * 60);
    }

    /**
     * Add a cookie that clears an existing cookie.
     * Uses SameSite=Strict for session cookies for maximum security.
     *
     * @param response HTTP response
     * @param name     Cookie name to clear
     */
    private void addClearCookie(HttpServletResponse response, String name) {
        // Use Strict for session cookies
        String sameSite = "Strict";
        
        // Build domain string
        String domain = "";
        if (!"localhost".equals(cookieDomain) && cookieDomain != null && !cookieDomain.isEmpty()) {
            domain = String.format("; Domain=%s", cookieDomain);
        }
        
        String secureFlag = secureCookies ? "; Secure" : "";
        
        // Build cookie string with all attributes
        String cookieString = String.format(
            "%s=; Max-Age=0; Path=/; HttpOnly%s; SameSite=%s%s",
            name, secureFlag, sameSite, domain
        );
        
        response.addHeader("Set-Cookie", cookieString);
    }

    /**
     * Format duration in seconds to a human-readable string.
     *
     * @param seconds Duration in seconds
     * @return Formatted duration string (e.g., "7 days", "10 minutes", "15 seconds")
     */
    private String formatDuration(int seconds) {
        if (seconds >= 86400 && seconds % 86400 == 0) {
            int days = seconds / 86400;
            return days + (days == 1 ? " day" : " days");
        } else if (seconds >= 3600 && seconds % 3600 == 0) {
            int hours = seconds / 3600;
            return hours + (hours == 1 ? " hour" : " hours");
        } else if (seconds >= 60 && seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        } else {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
    }

    /**
     * Check if we should validate with Instagram API.
     * Validates based on configuration and role/data freshness.
     */
    private boolean shouldValidateWithInstagramAPI(Map<String, Object> instagramDoc, String role) {
        // Check if validation is enabled
        if (!instagramApiValidationEnabled) {
            log.debug("Instagram API validation is disabled by configuration");
            return false;
        }
        
        // Check role-based validation
        boolean isPrivileged = "ADMIN".equals(role) || "COMPANY".equals(role);
        
        if (isPrivileged) {
            log.info("Will validate Instagram token for privileged role: {}", role);
            return true; // Always validate privileged roles
        }
        
        // If configured to only validate privileged roles, stop here
        if (validatePrivilegedOnly) {
            return false;
        }
        
        // Check if data is stale (older than 1 hour)
        // Handle numeric conversion properly - Firebase can return BigDecimal, Integer, or Long
        Long lastUpdated = null;
        Object lastUpdatedObj = instagramDoc.get("lastUpdated");
        if (lastUpdatedObj instanceof Number) {
            lastUpdated = ((Number) lastUpdatedObj).longValue();
        }
        if (lastUpdated != null) {
            long currentTime = System.currentTimeMillis() / 1000;
            long age = currentTime - lastUpdated;
            if (age > 3600) { // 1 hour
                log.info("Instagram data is {} seconds old, will validate with API", age);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Validate Instagram token freshness by calling Instagram Graph API.
     * This adds an extra layer of security by verifying:
     * 1. The access token is still valid
     * 2. The Instagram user ID matches
     * 3. The username hasn't changed
     * 
     * @param instagramDoc Firestore document with Instagram data
     * @param expectedId Expected Instagram ID from token
     * @param expectedUsername Expected Instagram username from token
     * @throws SecurityException if validation fails
     */
    private void validateInstagramTokenFreshness(Map<String, Object> instagramDoc, 
                                                 String expectedId, 
                                                 String expectedUsername) {
        try {
            // Extract access token from Firestore (it should be encrypted)
            String accessToken = (String) instagramDoc.get("access_token");
            if (accessToken == null || accessToken.isEmpty()) {
                log.warn("No Instagram access token found in Firestore, skipping API validation");
                return;
            }
            
            // Note: The access token is already decrypted by FirestoreService.getInstagramUserData()
            
            log.info("Validating Instagram token freshness for user: {}", expectedUsername);
            
            // Call Instagram Graph API /me endpoint to verify token
            log.info("Calling Instagram Graph API to validate token for user {}", expectedId);
            
            // Use InstagramService to get user profile with the access token
            // This verifies the token is still valid and gets fresh user data
            Map<String, Object> freshProfile = instagramService.getUserProfile(accessToken)
                .doOnError(error -> log.error("Instagram API call failed: {}", error.getMessage()))
                .block(); // Block since we're in a synchronous context
            
            if (freshProfile == null) {
                log.error("Instagram API returned null profile for user {}", expectedId);
                throw new AuthenticationTranslatableException("error.auth.instagram_validation_failed");
            }
            
            // Validation checks:
            // 1. Verify Instagram user ID matches
            String apiUserId = String.valueOf(freshProfile.get("id"));
            if (!apiUserId.equals(expectedId)) {
                log.error("Instagram ID mismatch! Token claims: {}, API returned: {}", expectedId, apiUserId);
                throw new AuthenticationTranslatableException("error.auth.account_mismatch");
            }
            
            // 2. Verify username matches (allow case-insensitive)
            String apiUsername = (String) freshProfile.get("username");
            if (apiUsername != null && !apiUsername.equalsIgnoreCase(expectedUsername)) {
                log.warn("Instagram username changed from {} to {}", expectedUsername, apiUsername);
                // Username change is allowed but logged for audit
                // Could update Firestore with new username here
            }
            
            // 3. Check if account type indicates business/creator (for COMPANY role)
            String accountType = (String) freshProfile.get("account_type");
            if (accountType != null) {
                log.info("Instagram account type: {} for user {}", accountType, apiUsername);
                
                // Additional validation for COMPANY role
                String role = (String) instagramDoc.get("role");
                if ("COMPANY".equals(role) && !"BUSINESS".equals(accountType)) {
                    log.warn("Company role but Instagram account type is: {}", accountType);
                    // Could enforce business account requirement here
                }
            }
            
            // 4. Log fresh metrics for monitoring
            Object followersCount = freshProfile.get("followers_count");
            if (followersCount != null) {
                log.info("Instagram user {} has {} followers", apiUsername, followersCount);
            }
            
            log.info("Instagram token validation successful for user: {}", apiUsername);
            
        } catch (Exception e) {
            log.error("Instagram API validation failed: {}", e.getMessage());
            // Decide whether to fail authentication or just log warning
            // For now, we log but don't fail (graceful degradation)
            log.warn("Continuing authentication despite Instagram API validation failure");
        }
    }
    
    /**
     * Verify token integrity for privileged roles (ADMIN, COMPANY).
     * Performs additional security checks on the Firebase ID token.
     *
     * @param token Firebase decoded token
     * @param expectedUid Expected Firebase UID
     * @throws AuthenticationTranslatableException if verification fails
     */
    private void verifyTokenIntegrity(FirebaseToken token, String expectedUid) {
        // 1. Verify issuer
        String issuer = token.getIssuer();
        if (!issuer.startsWith("https://securetoken.google.com/")) {
            log.error("Invalid token issuer: {}", issuer);
            throw new AuthenticationTranslatableException("error.auth.invalid_issuer");
        }

        // 2. Verify audience (should be your project ID)
        String audience = (String) token.getClaims().get("aud");
        // Note: You may need to configure the expected project ID
        // For now, we verify it's not empty and matches the issuer pattern
        if (audience == null || audience.isEmpty()) {
            log.error("Invalid token audience: {}", audience);
            throw new AuthenticationTranslatableException("error.auth.invalid_audience");
        }

        // 3. Verify UID matches
        if (!token.getUid().equals(expectedUid)) {
            log.error("Token UID mismatch. Expected: {}, Got: {}", expectedUid, token.getUid());
            throw new AuthenticationTranslatableException("error.auth.uid_mismatch");
        }

        // 4. Check token age (not too old)
        // Handle numeric conversion properly - Firebase can return BigDecimal, Integer, or Long
        long issuedAt = 0;
        Object iatObj = token.getClaims().get("iat");
        if (iatObj instanceof Number) {
            issuedAt = ((Number) iatObj).longValue();
        }
        long currentTime = System.currentTimeMillis() / 1000;
        long tokenAge = currentTime - issuedAt;

        if (tokenAge > 3600) { // 1 hour
            log.error("Token too old. Age: {} seconds", tokenAge);
            throw new AuthenticationTranslatableException("error.auth.token_too_old");
        }

        // 5. Check expiration
        // Handle numeric conversion properly - Firebase can return BigDecimal, Integer, or Long
        long expiration = 0;
        Object expObj = token.getClaims().get("exp");
        if (expObj instanceof Number) {
            expiration = ((Number) expObj).longValue();
        }
        if (currentTime >= expiration) {
            log.error("Token expired at: {}, Current time: {}", expiration, currentTime);
            throw new AuthenticationTranslatableException("error.auth.token_expired");
        }

        log.debug("Token integrity verification passed. Issuer: {}, Age: {}s", issuer, tokenAge);
    }
    
    /**
     * Generate a secure session fingerprint from IP and User-Agent.
     * Uses HMAC to create a secure, non-reversible fingerprint.
     *
     * @param clientIp  Client IP address
     * @param userAgent User-Agent header
     * @return HMAC fingerprint
     */
    private String generateSessionFingerprint(String clientIp, String userAgent) {
        String fingerprintData = (clientIp != null ? clientIp : "unknown") + ":" +
                (userAgent != null ? userAgent : "unknown");

        // Use a separate secret for fingerprints (can be same as cookie secret)
        // This creates a unique, secure fingerprint that can't be forged
        return HmacUtils.generateHMAC(fingerprintData, cookieHmacSecret + "-fingerprint");
    }

    /**
     * Extract client IP address from request, checking for proxy headers.
     *
     * @param request HTTP request
     * @return Client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check for proxy headers (in order of preference)
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
                "HTTP_FORWARDED"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated list (X-Forwarded-For can have multiple IPs)
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
     * Mask IP address for logging to comply with privacy requirements.
     * Masks the last octet of IPv4 or last 4 segments of IPv6.
     *
     * @param ip IP address to mask
     * @return Masked IP address
     */
    private String maskIpForLogging(String ip) {
        if (ip == null) {
            return "unknown";
        }

        if (ip.contains(":")) {
            // IPv6 - mask last 4 segments
            int lastColon = ip.lastIndexOf(':');
            if (lastColon > 0) {
                return ip.substring(0, lastColon) + ":xxxx";
            }
        } else if (ip.contains(".")) {
            // IPv4 - mask last octet
            int lastDot = ip.lastIndexOf('.');
            if (lastDot > 0) {
                return ip.substring(0, lastDot) + ".xxx";
            }
        }

        return ip; // Return as-is if format unknown
    }

    /**
     * Extract Firebase user ID from a partial/temporary token.
     * Used during 2FA verification when user has limited authentication.
     *
     * @param request HTTP request containing cookies
     * @return Firebase user ID or null if not found
     */
    public String extractFirebaseUserIdFromPartialToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        
        // GDPR: Log partial token extraction attempt
        log.debug("GDPR: Operation=extractFirebaseUserIdFromPartialToken, Purpose=2fa_verification, DataAccessed=partial_session_token");

        String sessionToken = null;
        String partialToken = null;
        String tempToken = null;

        // Look for session cookies (prioritize full session, then partial, then temp)
        for (Cookie cookie : request.getCookies()) {
            if ("session".equals(cookie.getName())) {
                sessionToken = cookie.getValue();
            } else if ("partialSession".equals(cookie.getName())) {
                partialToken = cookie.getValue();
            } else if ("session_temp".equals(cookie.getName())) {
                tempToken = cookie.getValue();
            }
        }

        // Priority: full session > partial session > temp session
        String tokenToCheck = sessionToken != null ? sessionToken : 
                             (partialToken != null ? partialToken : tempToken);

        if (tokenToCheck == null) {
            log.debug("No session token found in cookies");
            return null;
        }

        try {
            // Extract the subject (Firebase UID) from the JWT
            return jwtTokenProvider.getSubject(tokenToCheck);
        } catch (Exception e) {
            log.debug("Could not extract Firebase UID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create a refreshed session for a user whose permissions may have changed.
     * Called when token version mismatch is detected (admin changed user's status).
     * <p>
     * This method:
     * 1. Verifies user still exists and can login
     * 2. Creates new JWT with updated claims and token version
     * 3. Sets new session cookies
     *
     * @param firebaseUid Firebase UID from existing session
     * @param request     HTTP request
     * @param response    HTTP response for setting cookies
     * @return TokenExchangeResponse with new session data
     * @throws SecurityException if user can no longer login (banned/inactive)
     */
    @Transactional(readOnly = true)
    public TokenExchangeResponse createRefreshedSession(String firebaseUid,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {
        // GDPR: Log refresh session operation
        log.info("GDPR: Operation=createRefreshedSession, FirebaseUID={}, Purpose=permission_update", firebaseUid);

        // 1. Find user in database
        User user = userRepository.findByFirebaseUserId(firebaseUid)
            .orElseThrow(() -> new com.sm.instagram.platform.common.exceptions.ResourceNotFoundException(
                "error.business.item_not_found", "User"));

        // 2. Check user can still login (not INACTIVE)
        if (!userCacheService.isUserActive(firebaseUid)) {
            log.info("GDPR: Operation=createRefreshedSession_denied, FirebaseUID={}, Reason=account_disabled", firebaseUid);
            throw new AuthenticationTranslatableException("error.auth.account_disabled");
        }

        // 2b. BUG-21 (revised 2026-05-31): a BANNED user IS allowed to refresh. They remain
        // authenticatable (isUserActive returns true for BANNED) so they can load the
        // whitelisted ban page (/users/me) to see why they are banned and contact support.
        // BannedUserAuthorizationFilter blocks every non-whitelisted endpoint, so a refreshed
        // token grants no extra reach; the refreshed JWT carries accountStatus=BANNED (below)
        // for the frontend to render the ban UI. Denying refresh entirely hard-locked banned
        // users out of the ban page and contradicted the whitelist design + the
        // security-advanced-session e2e contract (5 scenarios). Do NOT re-add a BANNED throw here.

        // 3. Validate role via Firebase Admin SDK
        FirebaseRoleValidation roleValidation = validateFirebaseRole(firebaseUid);
        String role = roleValidation.role;

        // 4. Create new JWT claims with updated token version
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("uid", firebaseUid);
        claims.put("tokenVersion", user.getTokenVersion()); // Current version from DB
        claims.put("verifiedRole", true);
        claims.put("accountStatus", user.getAccountStatus().name()); // For frontend to show appropriate UI

        // Add role-specific claims
        if ("ADMIN".equals(role)) {
            // For token REFRESH: Check if 2FA is already configured in Firestore
            // The 2-minute challenge window (adminChallengeCompletedAt) is only for FRESH logins
            // For refresh, if admin has 2FA enabled in Firestore, they've already proven access
            boolean has2FAEnabled = twoFactorAuthService.is2FAEnabled(firebaseUid);

            claims.put("requiresTwoFactor", true);
            claims.put("twoFactorEnabled", has2FAEnabled);
            claims.put("twoFactorVerified", has2FAEnabled);  // Trust Firestore for refresh
            claims.put("canAccessAdmin", has2FAEnabled);

            log.debug("ADMIN refresh: firebaseUid={}, 2FA enabled in Firestore={}", firebaseUid, has2FAEnabled);
        } else if ("PENDING_ADMIN".equals(role)) {
            // Admin who hasn't completed 2FA setup yet
            claims.put("requiresTwoFactor", true);
            claims.put("requires2FASetup", true);
            claims.put("twoFactorEnabled", false);
            claims.put("twoFactorVerified", false);
            claims.put("canAccessAdmin", false);
        } else if ("COMPANY".equals(role)) {
            claims.put("canAccessCompanyFeatures", true);
        } else if ("INFLUENCER".equals(role)) {
            claims.put("canAccessInfluencerFeatures", true);
        }

        // Add session fingerprint
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        long sessionStart = System.currentTimeMillis();

        claims.put("sessionIp", clientIp);
        claims.put("sessionUA", userAgent != null ? userAgent : "unknown");
        claims.put("sessionStart", sessionStart);
        claims.put("fingerprint", generateSessionFingerprint(clientIp, userAgent));
        claims.put("refreshedAt", sessionStart); // Mark as refreshed session

        // 5. Create new JWT with role-based duration
        int refreshSessionDuration = getFullSessionDuration(role);
        String backendJwt = jwtTokenProvider.createTokenWithSecondsExpiry(
            firebaseUid,
            claims,
            refreshSessionDuration
        );

        // 6. Generate HMAC and set cookies
        String hmacSignature = HmacUtils.generateHMAC(backendJwt, cookieHmacSecret);
        addSecureCookieWithSeconds(response, "session", backendJwt, refreshSessionDuration);
        addSecureCookieWithSeconds(response, "session_sig", hmacSignature, refreshSessionDuration);

        // Determine effective 2FA state for response (consistent with JWT claims)
        boolean effectiveRequires2FA = "ADMIN".equals(role) || "PENDING_ADMIN".equals(role);
        boolean effectiveTwoFactorVerified = "ADMIN".equals(role)
            ? twoFactorAuthService.is2FAEnabled(firebaseUid)  // ADMIN: trust Firestore
            : roleValidation.twoFactorVerified;               // Others: use Firebase validation

        log.info("GDPR: Operation=createRefreshedSession_success, FirebaseUID={}, NewRole={}, TokenVersion={}, 2FAVerified={}, Duration={}",
            firebaseUid, role, user.getTokenVersion(), effectiveTwoFactorVerified, formatDuration(refreshSessionDuration));

        return TokenExchangeResponse.builder()
            .success(true)
            .userId(user.getId())
            .email(user.getEmail())
            .role(role)
            .firebaseUid(firebaseUid)
            .requires2FA(effectiveRequires2FA)
            .twoFactorVerified(effectiveTwoFactorVerified)
            .cookieType("FULL")
            .sessionDuration(formatDuration(refreshSessionDuration))
            .build();
    }

    /**
     * Verify the user has accepted the cookie policy (essential consent).
     * Check order: 1) consent_cookie_policy HMAC cookie, 2) DB consent_record fallback.
     * Throws 451 if neither exists.
     */
    private void verifyCookiePolicyConsent(User user, HttpServletRequest request) {
        // 1. Check if consent_cookie_policy HMAC cookie exists and is valid
        ConsentProofPayload cookiePayload = consentCookieService.readConsentCookie(
                request, ConsentCookieService.COOKIE_CONSENT_COOKIE_POLICY, ConsentProofPayload.class);
        if (cookiePayload != null) {
            return; // Cookie exists and HMAC is valid — consent proven
        }

        // 2. Cookie missing (expired or cleared) — fallback to DB check
        Optional<Integer> latestVersion = legalDocumentService.getLatestVersion(LegalDocumentType.COOKIE_POLICY);
        if (latestVersion.isEmpty()) {
            return; // No cookie policy document published — skip check
        }

        boolean hasAccepted = consentRecordRepository.hasUserAcceptedDocumentVersion(
                user.getId(), LegalDocumentType.COOKIE_POLICY, latestVersion.get());
        if (hasAccepted) {
            log.debug("Cookie policy consent verified via DB for user {}", user.getId());
            return; // User consented before, cookie just expired
        }

        // 3. No consent found — block login
        log.warn("Login blocked: no cookie policy consent for user {} (firebaseUid={})",
                user.getId(), user.getFirebaseUserId());
        throw new ConsentRequiredTranslatableException("error.auth.cookie_consent_required");
    }

    /**
     * Link anonymous cookie consent record to the user after successful full session creation.
     * Same approach as processCookieConsent() in LegalConsentService (used during registration).
     */
    private void linkAnonymousCookieConsent(User user, HttpServletRequest request) {
        try {
            legalConsentService.linkAnonymousCookieConsentToUser(user, request);
        } catch (Exception e) {
            // Non-fatal — consent linking failure shouldn't block login
            log.warn("Failed to link anonymous consent during login for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
