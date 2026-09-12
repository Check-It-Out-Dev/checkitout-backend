package com.sm.instagram.platform.auth.service;

import com.sm.instagram.platform.auth.filter.HmacUtils;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
import com.sm.instagram.platform.auth.social.instagram.InstagramService;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.NetworkRetryExhaustedException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.legal.ConsentSource;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.storage.service.ProfilePictureProxyService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.platform.PlatformRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import com.sm.instagram.platform.auth.dto.OAuthCallbackFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Pinterest-style OAuth Callback Service
 * Simplified flow: oauth → callback → user_check → session → success (6 states)
 * New users: Immediate creation with instagram_username as Firebase UID
 * Existing users: Direct JWT session
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthCallbackService {

    private final InstagramService instagramService;
    private final UserRepository userRepository;
    private final UserSocialConnectionRepository socialConnectionRepository;
    private final FirebaseService firebaseService;
    private final FirestoreService firestoreService;
    private final SocialAuthSessionService sessionService;
    private final TokenExchangeService tokenExchangeService;
    private final PlatformRepository platformRepository;
    private final ProfilePictureProxyService profilePictureProxyService;
    private final LegalConsentService legalConsentService;
    
    @Value("${frontend.url:https://localhost:4200}")
    private String frontendUrl;
    
    @Value("${cookie.hmac.secret}")
    private String cookieHmacSecret;  // Same secret for all HMAC cookies
    
    @Value("${jwt.cookie.secure:true}")
    private boolean secureCookies;
    
    @Value("${jwt.cookie.domain:localhost}")
    private String cookieDomain;
    
    @Value("${spring.profiles.active:local}")
    private String activeProfile;
    
    // Hardcoded paths - consistent across all environments
    private static final String ERROR_REDIRECT_PATH = "/auth/error";
    private static final String SUCCESS_REDIRECT_PATH = "/auth/success";
    private static final String REGISTRATION_REDIRECT_PATH = "/auth/social/callback/instagram";

    /**
     * Clean Architecture OAuth callback processing
     * 
     * Flow for user lookup and creation:
     * 1. Exchange code for Instagram profile
     * 2. Check if user exists by Firebase UID in local DB (source of truth)
     * 3. New: Create Firebase user (auto-generated UID), save to Firestore, save to DB
     * 4. Generate custom token with HMAC signature
     * 5. Redirect to success with secure cookie
     * 
     * @param code OAuth authorization code
     * @param state Optional CSRF state
     * @param request HTTP request
     * @param response HTTP response
     * @return Redirect or JSON response
     */
    @Transactional
    public ResponseEntity<?> processInstagramCallback(
            String code, 
            String state, 
            HttpServletRequest request,
            HttpServletResponse response) {
        
        // GDPR: Log OAuth callback processing
        log.info("GDPR: Operation=instagramOAuthCallback, DataAccessed=instagram_profile,oauth_tokens, Purpose=social_authentication, LegalBasis=consent, ThirdParty=Instagram");
        
        log.info("Processing Instagram OAuth callback");
        debugOAuthFlow("CALLBACK_START", Map.of("code", code != null ? code.substring(0, Math.min(code.length(), 10)) + "..." : "null", 
            "state", state != null ? state : "null"));
        
        try {
            // 1. Exchange code for Instagram profile
            Map<String, Object> instagramData = instagramService.exchangeAuthCodeForProfile(code);
            debugOAuthFlow("TOKEN_EXCHANGE_SUCCESS", Map.of(
                "userId", instagramData.get("user_id"),
                "username", instagramData.get("username"),
                "hasAccessToken", instagramData.containsKey("access_token")
            ));
            
            String socialUserId = String.valueOf(instagramData.get("user_id"));
            String username = (String) instagramData.get("username");
            String profilePictureUrl = (String) instagramData.get("profile_picture_url");
            
            // GDPR: Log Instagram data retrieval
            log.info("GDPR: InstagramProfileRetrieved, SocialUserID={}, Username={}, DataAccessed=profile_data,oauth_tokens, Purpose=user_identification",
                socialUserId, username);
            
            log.info("Instagram profile retrieved for user: {}", username);
            
            // 2. Check if user exists by looking up social connection first
            // Then verify Firebase UID exists
            var existingConnection = socialConnectionRepository
                .findByPlatform_NameAndSocialUserId("Instagram", socialUserId);
            
            if (existingConnection.isPresent()) {
                // Existing user - verify Firebase and generate token
                return handleExistingUser(existingConnection.get(), instagramData, response, request);
            } else {
                // New user: Create with clean architecture
                return createNewUserCleanArchitecture(instagramData, response, request);
            }
            
        } catch (NetworkRetryExhaustedException e) {
            log.error("OAuth callback failed after network retries: {}", e.getMessage());
            log.error("Service: {}, Attempts: {}, Last Error: {}", 
                e.getServiceName(), e.getAttemptsMade(), 
                e.getLastError() != null ? e.getLastError().getMessage() : "N/A");
            
            // User-friendly message for network issues
            String message = "Instagram is temporarily unavailable. Please try again in a few minutes.";
            return redirectWithError(message, request);
            
        } catch (ExternalServiceException e) {
            log.error("OAuth callback processing failed with detailed error: {}", e.getMessage());
            log.error("Service: {}, Operation: {}, Status Code: {}", 
                e.getServiceName(), e.getOperation(), e.getStatusCode());
            
            // Log the full stack trace at debug level for troubleshooting
            log.debug("Full exception details:", e);
            
            // Pass the user-friendly message from the ExternalServiceException
            return redirectWithError(e.getMessage(), request);
            
        } catch (IllegalArgumentException e) {
            log.error("OAuth callback validation error: {}", e.getMessage());
            return redirectWithError(e.getMessage(), request);
            
        } catch (Exception e) {
            log.error("Unexpected OAuth callback processing error", e);
            
            // Try to extract more context if possible
            String errorMessage = "Authentication failed";
            if (e.getCause() instanceof ExternalServiceException) {
                ExternalServiceException ese = (ExternalServiceException) e.getCause();
                errorMessage = ese.getMessage();
                log.error("Nested ExternalServiceException - Service: {}, Operation: {}", 
                    ese.getServiceName(), ese.getOperation());
            }
            
            return redirectWithError(errorMessage, request);
        }
    }
    
    /**
     * Set SECURE HttpOnly cookie with custom token AND HMAC signature
     * Double protection: HttpOnly + HMAC verification
     * 
     * @param response HTTP response
     * @param customToken Firebase custom token
     * @param isNewUser Whether this is a new user
     */
    private void setSecureOAuthCookies(HttpServletResponse response, String customToken, boolean isNewUser) {
        // 1. Generate HMAC signature for the custom token
        String hmacSignature = HmacUtils.generateHMAC(customToken, cookieHmacSecret);
        
        // 2. Set custom token cookie (HttpOnly, secure)
        Cookie tokenCookie = new Cookie("oauth_token", customToken);
        tokenCookie.setHttpOnly(true);  // ✅ Not accessible to JavaScript
        tokenCookie.setSecure(secureCookies);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(120); // 2 minutes TTL
        
        // 3. Set HMAC signature cookie (also HttpOnly)
        Cookie hmacCookie = new Cookie("oauth_sig", hmacSignature);
        hmacCookie.setHttpOnly(true);  // ✅ Signature also protected
        hmacCookie.setSecure(secureCookies);
        hmacCookie.setPath("/");
        hmacCookie.setMaxAge(120); // Same TTL
        
        if (!"localhost".equals(cookieDomain)) {
            tokenCookie.setDomain(cookieDomain);
            hmacCookie.setDomain(cookieDomain);
        }
        
        // 4. Use Set-Cookie headers for full control (with domain for consistent clearing)
        String domainStr = (!"localhost".equals(cookieDomain) && cookieDomain != null && !cookieDomain.isEmpty())
                ? " Domain=" + cookieDomain + ";" : "";
        String tokenHeader = String.format(
            "%s=%s; Max-Age=120; Path=/; HttpOnly; %s%s SameSite=Lax",
            "oauth_token",
            customToken,
            secureCookies ? "Secure;" : "",
            domainStr
        );

        String hmacHeader = String.format(
            "%s=%s; Max-Age=120; Path=/; HttpOnly; %s%s SameSite=Lax",
            "oauth_sig",
            hmacSignature,
            secureCookies ? "Secure;" : "",
            domainStr
        );

        response.addHeader("Set-Cookie", tokenHeader);
        response.addHeader("Set-Cookie", hmacHeader);
        
        // 5. Add metadata cookie (non-HttpOnly, for UI only) — via Set-Cookie for consistent domain
        String metaHeader = String.format(
            "oauth_meta=%s; Max-Age=120; Path=/; %s%s SameSite=Lax",
            isNewUser ? "new_user" : "existing_user",
            secureCookies ? "Secure;" : "",
            domainStr
        );
        response.addHeader("Set-Cookie", metaHeader);
        
        log.info("Set HMAC-signed OAuth cookies for {} user", isNewUser ? "new" : "existing");
    }
    
    /**
     * Handle existing user authentication.
     * Verifies Firebase user exists and custom claims are correct.
     * Updates profile picture if changed.
     * 
     * @param connection Existing social connection
     * @param instagramData Fresh Instagram data from OAuth
     * @param response HTTP response
     * @param request HTTP request
     * @return Redirect response
     */
    private ResponseEntity<?> handleExistingUser(
            UserSocialConnection connection,
            Map<String, Object> instagramData,
            HttpServletResponse response,
            HttpServletRequest request) throws IOException {
        
        User user = connection.getUser();
        
        // GDPR: Log existing user authentication
        log.info("GDPR: ExistingUserAuth, FirebaseUID={}, UserID={}, DataAccessed=user_profile,social_connection, Purpose=authentication",
            user.getFirebaseUserId(), user.getId());
        
        log.info("Existing user found via local DB: {}", user.getId());
        log.info("User Firebase UID: {}", user.getFirebaseUserId());
        
        // Sync Instagram data - profile picture, followers count, etc.
        String newProfilePictureUrl = (String) instagramData.get("profile_picture_url");
        if (newProfilePictureUrl != null && !newProfilePictureUrl.isEmpty()) {
            boolean userUpdated = false;
            boolean connectionUpdated = false;

            // Check if user already has a permanent (Firebase Storage) profile picture
            // If so, DON'T overwrite it - user may have manually changed their photo
            boolean hasPreservablePhoto = profilePictureProxyService.hasPreservableProfilePicture(user.getProfilePicture());

            if (hasPreservablePhoto) {
                // User has a permanent photo (Firebase Storage URL) - preserve it
                log.info("User {} has a permanent profile picture in Firebase Storage - preserving it, not overwriting with Instagram photo",
                        user.getId());
            } else {
                // User has no photo OR has an expiring Instagram CDN URL - update it
                if (user.getProfilePicture() == null || !user.getProfilePicture().equals(newProfilePictureUrl)) {
                    user.setProfilePicture(newProfilePictureUrl);
                    userUpdated = true;
                    log.info("Updated user profile picture URL: {}", newProfilePictureUrl);
                }
            }

            // ALWAYS update the social connection's profile picture URL
            // This tracks the current Instagram photo separately from the user's display photo
            if (connection.getProfilePictureUrl() == null || !connection.getProfilePictureUrl().equals(newProfilePictureUrl)) {
                connection.setProfilePictureUrl(newProfilePictureUrl);
                connectionUpdated = true;
            }

            // Update followers count if changed
            Integer newFollowersCount = extractFollowersCount(instagramData.get("followers_count"));
            if (newFollowersCount != null && !newFollowersCount.equals(connection.getFollowersCount())) {
                connection.setFollowersCount(newFollowersCount);
                connectionUpdated = true;
                log.info("Updated followers count: {}", newFollowersCount);
            }

            // Save updates if needed
            if (userUpdated) {
                userRepository.save(user);
            }
            if (connectionUpdated) {
                socialConnectionRepository.save(connection);
            }
        }
        
        // Also update Firestore with fresh data
        try {
            firestoreService.storeInstagramUserData(instagramData, user.getFirebaseUserId());
            log.info("Updated Instagram data in Firestore for existing user");
        } catch (Exception e) {
            log.warn("Failed to update Firestore for existing user, continuing: {}", e.getMessage());
        }
        
        // Check token age and refresh if expires in < 14 days
        try {
            Map<String, Object> firestoreData = firestoreService.getInstagramUserDataByFirebaseUid(user.getFirebaseUserId());
            if (firestoreData != null) {
                Long lastUpdated = (Long) firestoreData.get("lastUpdated");
                if (lastUpdated != null) {
                    long currentTime = System.currentTimeMillis() / 1000;
                    long tokenAge = currentTime - lastUpdated;
                    long daysUntilExpiry = (60 * 24 * 60 * 60 - tokenAge) / (24 * 60 * 60); // 60 days total
                    
                    if (daysUntilExpiry < 14) {
                        log.info("Token expires in {} days, refreshing...", daysUntilExpiry);
                        
                        String currentToken = firestoreService.getDecryptedAccessToken(user.getFirebaseUserId());
                        if (currentToken != null) {
                            String newToken = instagramService.refreshLongLivedToken(currentToken);
                            if (newToken != null) {
                                firestoreService.updateAccessToken(user.getFirebaseUserId(), newToken, currentTime);
                                log.info("Token refreshed successfully for user: {}", user.getFirebaseUserId());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check/refresh token, continuing: {}", e.getMessage());
        }
        
        // Re-proxy profile picture if still using Instagram CDN
        String currentPictureUrl = user.getProfilePicture();
        if (currentPictureUrl != null && profilePictureProxyService.isInstagramCdnUrl(currentPictureUrl)) {
            log.info("Profile picture still uses Instagram CDN, re-proxying to Firebase Storage");
            String permanentUrl = profilePictureProxyService.proxyToFirebaseStorage(currentPictureUrl, user.getFirebaseUserId());
            if (permanentUrl != null) {
                user.setProfilePicture(permanentUrl);
                connection.setProfilePictureUrl(permanentUrl);
                userRepository.save(user);
                socialConnectionRepository.save(connection);
                log.info("Profile picture re-proxied to Firebase Storage for user: {}", user.getId());
            }
        }
        
        try {
            // Verify Firebase user exists and get custom claims from Firebase (source of truth)
            try {
                var firebaseUser = firebaseService.getUserById(user.getFirebaseUserId());
                log.info("Firebase user verified for UID: {}", user.getFirebaseUserId());
                
                // Get existing claims from Firebase - DO NOT auto-update
                Map<String, Object> firebaseClaims = firebaseUser.getCustomClaims();
                if (firebaseClaims == null) {
                    log.warn("Firebase user has no custom claims. Admin activation required.");
                    firebaseClaims = new HashMap<>();
                }
                
                // Check if user has been activated (has role claim)
                String firebaseRole = (String) firebaseClaims.get("role");
                if (firebaseRole == null && user.getAccountStatus() == AccountStatus.ACTIVE) {
                    log.warn("User {} is ACTIVE but has no Firebase role. Admin sync required.", user.getId());
                }
                
                // Log role status for monitoring
                if (firebaseRole != null) {
                    log.info("User {} has Firebase role: {}", user.getId(), firebaseRole);
                } else {
                    log.info("User {} has no Firebase role - awaiting admin activation", user.getId());
                }
            } catch (Exception e) {
                log.error("Firebase user NOT found for UID: {}. This should not happen for existing users!", 
                    user.getFirebaseUserId());
                return redirectWithError("Authentication error: Firebase user not found", request);
            }
            
            // Generate custom token with claims from Firebase (source of truth) + session data
            Map<String, Object> claims = new HashMap<>();
            
            // Use Firebase claims as base (these are managed by admin only)
            var firebaseUser = firebaseService.getUserById(user.getFirebaseUserId());
            Map<String, Object> firebaseClaims = firebaseUser.getCustomClaims();
            if (firebaseClaims != null) {
                // Copy Firebase-managed claims
                // Role might not exist if user hasn't been activated by admin yet
                if (firebaseClaims.containsKey("role")) {
                    claims.put("role", firebaseClaims.get("role"));
                }
                claims.put("provider", firebaseClaims.getOrDefault("provider", "instagram"));
                claims.put("instagramId", firebaseClaims.getOrDefault("instagramId", connection.getSocialUserId()));
                claims.put("instagramUsername", firebaseClaims.getOrDefault("instagramUsername", connection.getDisplayName()));
                claims.put("pendingActivation", firebaseClaims.getOrDefault("pendingActivation", true));
            }
            
            // Add session-specific data (not managed by Firebase)
            claims.put("userId", user.getId().toString());  // Local DB ID for backend lookups
            claims.put("email", user.getEmail());  // Current email from DB
            claims.put("accountStatus", user.getAccountStatus().toString());  // Current status from DB
            claims.put("oauth", true);  // Session flag
            
            // Generate Firebase custom token with claims
            String customToken = firebaseService.generateCustomTokenWithClaims(
                user.getFirebaseUserId(), 
                claims
            );
            
            // Set HMAC-signed HttpOnly cookies
            setSecureOAuthCookies(response, customToken, false);
            
            // GDPR: Log successful OAuth authentication
            log.info("GDPR: OAuthSuccess, FirebaseUID={}, UserID={}, DataProcessed=authentication_tokens,session_cookies, Purpose=session_creation",
                user.getFirebaseUserId(), user.getId());

            // Redirect to frontend success page
            String redirectUrl = frontendUrl + "/auth/success";
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();

        } catch (Exception e) {
            log.error("Failed to generate custom token", e);
            return redirectWithError("Authentication failed", request);
        }
    }

    /**
     * Clean Architecture: Create new user with proper Firebase flow
     * No email required initially - collected during account validation
     * 
     * Flow:
     * 1. Create Firebase user with auto-generated UID (no email)
     * 2. Save to Firestore for Firebase features
     * 3. Save to PostgreSQL (source of truth)
     * 4. Generate custom token with all claims
     * 
     * @param instagramData Instagram profile data
     * @param response HTTP response
     * @param request HTTP request
     * @return Redirect response with session
     */
    /**
     * Defensively coerce Instagram's {@code followers_count} to an Integer. The payload type has
     * varied between Integer, Long, and String depending on the API version + JSON parser; a raw
     * {@code (Integer)} cast threw a ClassCastException AFTER the Firebase user + Firestore doc were
     * already created, orphaning the account. Returns null for absent/unparseable values; never throws.
     */
    public static Integer extractFollowersCount(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private ResponseEntity<?> createNewUserCleanArchitecture(
            Map<String, Object> instagramData,
            HttpServletResponse response,
            HttpServletRequest request) {
        
        // GDPR: Log new user creation from OAuth
        log.info("GDPR: Operation=createNewOAuthUser, DataAccessed=instagram_profile, Purpose=account_creation, LegalBasis=consent, ThirdParty=Instagram");

        // Validate consent cookies before creating any user resources
        try {
            legalConsentService.validateConsentCookiesPresent(request);
        } catch (ValidationTranslatableException e) {
            log.warn("OAuth callback: consent cookies missing for new user, redirecting to registration");
            return redirectWithError("consent_required", request);
        }

        log.info("Creating new user with clean Firebase architecture (consent cookies validated)");

        try {
            String socialUserId = String.valueOf(instagramData.get("user_id"));
            String username = (String) instagramData.get("username");
            String profilePictureUrl = (String) instagramData.get("profile_picture_url");
            
            // 1. Create Firebase user with auto-generated UID (no email)
            Map<String, Object> firebaseResult = firebaseService.createInstagramFirebaseUser(
                username,
                socialUserId
            );
            
            String firebaseUid = (String) firebaseResult.get("uid");
            log.info("Created Firebase user with auto-generated UID: {}", firebaseUid);
            
            // 2. Proxy profile picture to Firebase Storage for permanent URL
            String permanentPictureUrl = profilePictureProxyService.proxyToFirebaseStorage(
                    profilePictureUrl,
                    firebaseUid
            );
            // Use permanent URL if available, fallback to Instagram CDN URL
            String finalPictureUrl = permanentPictureUrl != null ? permanentPictureUrl : profilePictureUrl;
            
            // 3. Save to Firestore for Firebase features
            saveToFirestore(firebaseUid, socialUserId, username, instagramData);
            
            // 4. Create user in PostgreSQL (source of truth)
            User newUser = new User();
            newUser.setFirebaseUserId(firebaseUid);  // Auto-generated Firebase UID
            newUser.setName(username);  // Instagram username for display
            newUser.setAccountStatus(AccountStatus.IN_VALIDATION); // Will change to ACTIVE after email validation
            newUser.setUserType(UserType.INFLUENCER);
            // OAuth users don't provide firstName/lastName initially - these are NULL
            newUser.setFirstName(null);  // Will be collected during onboarding
            newUser.setLastName(null);   // Will be collected during onboarding
            // Set profile picture (permanent Firebase URL or Instagram CDN fallback)
            if (finalPictureUrl != null && !finalPictureUrl.isEmpty()) {
                newUser.setProfilePicture(finalPictureUrl);
                log.info("Set profile picture URL: {} (permanent: {})", finalPictureUrl, permanentPictureUrl != null);
            }
            // NO EMAIL YET - will be set during account validation
            
            User savedUser = userRepository.save(newUser);
            
            // GDPR: Log new user creation
            log.info("GDPR: NewUserCreated, FirebaseUID={}, UserID={}, DataStored=user_profile,social_connection, Purpose=account_creation, Source=Instagram_OAuth",
                firebaseUid, savedUser.getId());
            
            log.info("Created user in PostgreSQL with ID: {} and Firebase UID: {}", 
                savedUser.getId(), firebaseUid);
            
            // Save Instagram connection to local DB
            UserSocialConnection connection = new UserSocialConnection();
            connection.setUser(savedUser);
            connection.setPlatform(platformRepository.findByName("Instagram")
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Instagram platform")));
            connection.setSocialUserId(socialUserId);  // Instagram numeric ID
            connection.setDisplayName(username);
            connection.setFollowersCount(extractFollowersCount(instagramData.get("followers_count")));
            connection.setIsPrimary(true);
            connection.setConnectionStatus(ConnectionStatus.CONNECTED);
            // Also save profile picture URL to social connection (use permanent URL if available)
            if (finalPictureUrl != null && !finalPictureUrl.isEmpty()) {
                connection.setProfilePictureUrl(finalPictureUrl);
            }
            
            socialConnectionRepository.save(connection);
            log.info("Saved Instagram connection for user: {}", savedUser.getId());

            // 5. Process consent cookies → create ConsentRecord entries + link anonymous cookie consent
            try {
                legalConsentService.processRegistrationConsents(
                        savedUser.getId(), request, response, ConsentSource.SOCIAL_REGISTRATION);
                log.info("Processed consent records for OAuth user: {}", savedUser.getId());
            } catch (Exception e) {
                // BUG-16: previously this was a log.warn — a silent failure that left the user
                // with newestConsentsAccepted=false and (38-day archive cron) eventually
                // auto-archived them. Recovery via the ConsentEnforcementFilter X-Consent-Required
                // path still works on next request, so we keep the flow non-fatal — but the
                // failure must be OBSERVABLE so an alarm can fire and a human can intervene if
                // the user never logs back in to trigger reconsent.
                log.error("SECURITY_METRIC: event_type=OAUTH_CONSENT_PROCESSING_FAILED, FirebaseUID={}, UserId={}, Reason={}",
                        savedUser.getFirebaseUserId(), savedUser.getId(), e.getMessage(), e);
                log.error("GDPR: Operation=processRegistrationConsents_failed, FirebaseUID={}, Purpose=oauth_signup, RecoveryPath=ConsentEnforcementFilter_X-Consent-Required",
                        savedUser.getFirebaseUserId());
                // Non-fatal: user created, newestConsentsAccepted=false, enforcement filter
                // returns 403 + X-Consent-Required on the next protected request → FE shows
                // the reconsent modal → user reaccepts → recovery complete.
            }

            // 6. Generate custom token - Firebase claims are already set during user creation
            // We just add session-specific data
            Map<String, Object> claims = new HashMap<>();
            
            // NO ROLE CLAIM until admin activation - user must be activated first
            // claims.put("role", "INFLUENCER");  // REMOVED - set by admin
            
            // Firebase-managed claims (already set in createInstagramFirebaseUser)
            claims.put("provider", "instagram");  // Provider info
            claims.put("instagramId", socialUserId);  // Instagram ID
            claims.put("instagramUsername", username);  // Instagram username
            claims.put("pendingActivation", true);  // Pending flag
            
            // Session-specific data (not managed by Firebase)
            claims.put("userId", savedUser.getId().toString());  // Local DB ID
            claims.put("accountStatus", "IN_VALIDATION");  // Current status
            claims.put("needsOnboarding", true);  // UI flag
            claims.put("needsEmail", true);  // UI flag for email collection
            claims.put("oauth", true);  // Session flag
            
            // Generate Firebase custom token with claims
            String customToken = firebaseService.generateCustomTokenWithClaims(
                firebaseUid, 
                claims
            );
            
            // Set HMAC-signed HttpOnly cookies
            setSecureOAuthCookies(response, customToken, true);
            
            // GDPR: Log successful new user OAuth flow
            log.info("GDPR: NewUserOAuthComplete, FirebaseUID={}, UserID={}, DataProcessed=authentication_tokens,session_cookies, Purpose=initial_session_creation",
                firebaseUid, savedUser.getId());

            // Redirect to frontend success page
            String redirectUrl = frontendUrl + "/auth/success";
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();

        } catch (Exception e) {
            log.error("Failed to create new user", e);
            return redirectWithError("User creation failed", request);
        }
    }

    /**
     * Save Instagram login data to Firestore.
     * Stores in instagramUsers collection with document ID as Instagram user ID.
     * 
     * @param firebaseUid The Firebase UID
     * @param socialUserId Instagram numeric ID
     * @param username Instagram username
     * @param instagramData Full Instagram data from OAuth
     */
    private void saveToFirestore(String firebaseUid, String socialUserId, 
                                 String username, Map<String, Object> instagramData) {
        try {
            // Store ONLY in instagramUsers collection - no need for duplicate socialConnections
            // The instagramUsers collection has all the data we need
            firestoreService.storeInstagramUserData(instagramData, firebaseUid);
            
            log.info("Successfully saved Instagram data to Firestore for Firebase UID: {}", firebaseUid);
            
        } catch (Exception e) {
            // Don't fail the auth flow if Firestore fails
            log.error("Failed to save to Firestore, but continuing with auth flow", e);
        }
    }
    
    /**
     * Handle OAuth error response.
     * 
     * @param error OAuth error code
     * @param errorDescription Error description
     * @param request HTTP request
     * @return Error response
     */
    public ResponseEntity<?> handleOAuthError(
            String error, 
            String errorDescription,
            HttpServletRequest request) {
        
        log.error("OAuth error: {} - {}", error, errorDescription);
        
        String message = errorDescription != null ? errorDescription : error;
        return redirectWithError(message, request);
    }
    
    /**
     * Handle missing authorization code.
     * 
     * @param request HTTP request
     * @return Error response
     */
    public ResponseEntity<?> handleMissingCode(HttpServletRequest request) {
        log.error("OAuth callback missing authorization code");
        return redirectWithError("Authorization code missing", request);
    }
    
    /**
     * Handle processing error.
     * 
     * @param exception Processing exception
     * @param request HTTP request
     * @return Error response
     */
    public ResponseEntity<?> handleProcessingError(
            Exception exception,
            HttpServletRequest request) {
        
        log.error("OAuth processing error", exception);
        return redirectWithError("Authentication processing failed", request);
    }
    
    /**
     * Create error redirect response.
     * 
     * @param message Error message
     * @param request HTTP request
     * @return Redirect or JSON response
     */
    private ResponseEntity<?> redirectWithError(String message, HttpServletRequest request) {
        if (isWebClient(request)) {
            String redirectUrl = String.format("%s%s?error=%s",
                frontendUrl,
                ERROR_REDIRECT_PATH,
                URLEncoder.encode(message, StandardCharsets.UTF_8)
            );
            
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new OAuthCallbackFailure(false, message));
        }
    }
    
    /**
     * Check if request is from web browser or API client.
     *
     * @param request HTTP request
     * @return true if web client
     */
    private boolean isWebClient(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        String userAgent = request.getHeader("User-Agent");

        // Check if this is a browser request
        return (acceptHeader != null && acceptHeader.contains("text/html")) ||
               (userAgent != null && (userAgent.contains("Mozilla") || userAgent.contains("Chrome")));
    }


    
    /**
     * Debug helper for OAuth flow (only in test/local environments).
     * Logs detailed information about the OAuth callback process.
     * This method should be removed or disabled in production.
     *
     * @param stage The current stage of the OAuth flow
     * @param data The data to log (will be serialized to JSON)
     */
    private void debugOAuthFlow(String stage, Object data) {
        if ("test".equals(activeProfile) || "local".equals(activeProfile)) {
            try {
                String jsonData = data instanceof String ? (String) data : 
                    new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(data);
                log.info("OAUTH_DEBUG [{}]: {}", stage, jsonData);
            } catch (Exception e) {
                log.info("OAUTH_DEBUG [{}]: Unable to serialize data - {}", stage, data.toString());
            }
        }
    }
}
