package com.sm.instagram.platform.auth.controller;

import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.dto.RegisterUserRequest;
import com.sm.instagram.platform.auth.filter.HmacUtils;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.sandbox.SandboxPersonaPolicy;
import com.sm.instagram.platform.common.util.RequestContextUtils;
import com.sm.instagram.platform.common.jwt.JwtTokenProvider;
import com.sm.instagram.platform.legal.ConsentSource;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import com.sm.instagram.platform.userpreferences.UserPreferencesService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test-only authentication controller for E2E tests.
 * This controller is ONLY available when the "e2e" profile is active.
 *
 * <p>It provides endpoints to create valid session cookies without going through
 * Firebase authentication, allowing E2E tests to authenticate as any user type.
 *
 * <p><b>WARNING:</b> This controller should NEVER be deployed to production.
 * It bypasses all security checks.
 *
 * <p><b>SECURITY:</b> This bean is guarded by
 * {@code @Profile("(e2e | dev-lite) & !prod & !test")}. It only exists when the
 * {@code e2e} or {@code dev-lite} (credential-less simulator) profile is
 * explicitly active AND neither {@code prod} nor {@code test} profiles are
 * active. In production and standard test profiles, this controller is not
 * registered and its endpoints return 404.
 */
@Slf4j
@RestController
@Profile("(e2e | dev-lite) & !prod & !test")
@RequestMapping("/test/auth")
@RequiredArgsConstructor
public class TestAuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final SandboxPersonaPolicy sandboxPersonaPolicy;
    private final FirestoreService firestoreService;
    private final PlatformRepository platformRepository;
    private final UserSocialConnectionRepository socialConnectionRepository;
    private final UserCacheService userCacheService;
    private final LegalConsentService legalConsentService;
    private final UserPreferencesService userPreferencesService;
    private final com.sm.instagram.platform.auth.service.EmailVerificationService emailVerificationService;
    private final com.sm.instagram.platform.support.common.EmailService emailService;

    @Value("${cookie.hmac.secret}")
    private String hmacSecret;

    @Value("${jwt.cookie.domain:localhost}")
    private String cookieDomain;

    @Value("${frontend.url:https://localhost:4200}")
    private String frontendUrl;

    /**
     * Request DTO for creating mock sessions.
     */
    /**
     * {@code setupCompleted} is EXPLICIT tri-state: true/false forces the
     * flag; absent (null) leaves it untouched. The earlier implicit
     * "double-seed flips it" contract was nondeterministic — seedSession
     * retries transient 409s, and a retried FIRST seed silently landed in
     * the existing-user branch and flipped actors that the
     * incomplete-setup step-up specs required to stay unflipped.
     */
    public record MockSessionRequest(
        String email,
        String role,
        boolean partial,
        String firebaseUid,
        Boolean setupCompleted
    ) {}

    /**
     * Response DTO with session info.
     */
    public record MockSessionResponse(
        String firebaseUid,
        Long userId,
        String role,
        boolean partial
    ) {}

    /**
     * Creates a mock session for E2E testing.
     * Sets session and session_sig cookies that the application will accept.
     *
     * @param request Session request with email, role, and partial flag
     * @param response HTTP response for setting cookies
     * @return Session info response
     */
    @PostMapping("/mock-session")
    @Transactional
    public ResponseEntity<MockSessionResponse> createMockSession(
            @RequestBody MockSessionRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        log.info("[E2E] Creating mock session for email: {}, role: {}, partial: {}",
                request.email(), request.role(), request.partial());
        // The public sandbox admits its personas only (403 otherwise); a no-op everywhere else.
        sandboxPersonaPolicy.requirePersona(request.email(), request.role());

        // Get the actual User-Agent from the request for session fingerprinting
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            userAgent = "E2E-Test-Agent";
        }
        log.info("[E2E] Using User-Agent for session: {}", userAgent);

        // Find user by email or use provided firebaseUid
        String firebaseUid;
        Long userId = null;
        UserType userType;

        Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            firebaseUid = user.getFirebaseUserId();
            userId = user.getId();
            userType = user.getUserType();
            // Existing E2E users (created in earlier mock-session calls before
            // the emailVerified fix below) lack the flag — patch them
            // idempotently so EmailVerificationEnforcementFilter doesn't block
            // POST /partnership-opportunity or /applied-opportunity.
            boolean needsRecache = false;
            // Reconcile an explicitly requested firebaseUid onto the stored
            // row. An email-keyed row minted earlier with a MOCK uid breaks
            // every uid-keyed flow for the REAL account it shadows
            // (uid-keyed /test hooks 400, real-token /auth/exchange-token
            // 401) — hit on 2026-06-10 after the dev postgres volume was
            // recreated. Idempotent: no-op when the uids already match.
            if (request.firebaseUid() != null
                    && !request.firebaseUid().equals(user.getFirebaseUserId())) {
                userCacheService.evict(user.getFirebaseUserId());
                log.info("[E2E] Reconciling firebaseUid {} -> {} for {}",
                        user.getFirebaseUserId(), request.firebaseUid(), request.email());
                user.setFirebaseUserId(request.firebaseUid());
                firebaseUid = request.firebaseUid();
                needsRecache = true;
            }
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(true);
                user.setEmailVerifiedAt(LocalDateTime.now());
                needsRecache = true;
                log.info("[E2E] Backfilled emailVerified=true for {}", firebaseUid);
            }
            // Explicit tri-state only — see MockSessionRequest docs. Absent
            // leaves the flag alone so retried seeds can't flip actors that
            // the incomplete-setup step-up specs need unflipped.
            if (request.setupCompleted() != null
                    && !request.setupCompleted().equals(user.getInitialAccountSetupCompleted())) {
                user.setInitialAccountSetupCompleted(request.setupCompleted());
                needsRecache = true;
                log.info("[E2E] Set initialAccountSetupCompleted={} (explicit) for {}",
                        request.setupCompleted(), firebaseUid);
            }
            // Step-up email codes go to lastVerifiedEmail ("never to the new
            // unverified address") — a verified production user always has
            // one; mirror that or /step-up/request 400s "To address must not
            // be null" for every mock actor.
            if (user.getLastVerifiedEmail() == null) {
                user.setLastVerifiedEmail(user.getEmail());
                needsRecache = true;
            }
            // The seed creates influencers IN_VALIDATION, and such an account may not apply; on the public
            // sandbox the persona is activated at sign-in so a visitor can complete the journey.
            if (sandboxPersonaPolicy.isEnabled()
                    && (user.getAccountStatus() == null || !user.getAccountStatus().isActive())) {
                log.info("[SANDBOX] Activating persona {} (was {})", request.email(), user.getAccountStatus());
                user.setAccountStatus(AccountStatus.ACTIVE);
                needsRecache = true;
            }
            if (needsRecache) {
                user = userRepository.save(user);
                // Enforcement + step-up filters read from UserCacheService
                // (Redis), not the DB. Re-cache so the flags are visible.
                userCacheService.cacheUser(firebaseUid, user);
            }
            log.info("[E2E] Found existing user: {} (id={})", firebaseUid, userId);
        } else {
            // User doesn't exist - create them for E2E testing
            // Alphanumeric only — production Firebase UIDs never carry
            // underscores, and prod validation (e.g. Address.updaterId
            // @Pattern ^[a-zA-Z0-9]+$) rightly rejects them; an underscored
            // test UID made every authenticated address write 400.
            firebaseUid = request.firebaseUid() != null
                ? request.firebaseUid()
                : "E2E" + request.role() + System.currentTimeMillis();
            userType = UserType.valueOf(request.role());

            // Create the user in the database so that subsequent requests work
            User newUser = new User();
            newUser.setFirebaseUserId(firebaseUid);
            newUser.setEmail(request.email());
            newUser.setUserType(userType);
            newUser.setAccountStatus(AccountStatus.ACTIVE);
            // EmailVerificationEnforcementFilter blocks POST /partnership-opportunity
            // and /applied-opportunity for unverified users — and the production
            // sign-up flow sets email_verified after the BE-side OTP click. For
            // an auto-created E2E user we don't have that flow, so set the flag
            // explicitly. Without this, mock-session test actors are unusable
            // for any campaign-mutation flow.
            newUser.setEmailVerified(true);
            newUser.setEmailVerifiedAt(LocalDateTime.now());
            // Verified production users always carry lastVerifiedEmail — the
            // step-up email-code sender addresses it ("never to the new
            // unverified address") and 400s when it's null.
            newUser.setLastVerifiedEmail(request.email());
            // Explicit tri-state; default (absent) = setup-incomplete, which
            // the incomplete-setup step-up specs rely on for fresh actors.
            // Default NEW rows to setup-complete: mock actors model
            // ESTABLISHED accounts, and the step-up email-change gate is
            // SKIPPED for incomplete setups — on a fresh DB an unset flag
            // silently disarmed step-up for every oracle actor (profile
            // edge-case expected 401, got validation 400; found
            // 2026-06-10). Incomplete-setup specs pass false explicitly.
            newUser.setInitialAccountSetupCompleted(
                    request.setupCompleted() != null ? request.setupCompleted() : Boolean.TRUE);
            newUser.setCreatedTime(LocalDateTime.now());
            newUser.setLastUpdateTime(LocalDateTime.now());
            newUser = userRepository.save(newUser);
            userId = newUser.getId();

            log.info("[E2E] Created new user: firebaseUid={}, userId={}, email={}",
                firebaseUid, userId, request.email());
        }

        // Build claims matching production format
        // Use X-Forwarded-For header for IP spoofing in E2E tests
        String clientIp = RequestContextUtils.getClientIpAddress(httpRequest);
        String fingerprintData = (clientIp != null ? clientIp : "unknown") + ":" +
                (userAgent != null ? userAgent : "unknown");
        String fingerprint = HmacUtils.generateHMAC(fingerprintData, hmacSecret + "-fingerprint");

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", request.role());
        claims.put("email", request.email());
        // IMPORTANT: Use same claim names as SessionSecurityService expects
        claims.put("sessionIp", clientIp);  // Note: lowercase 'p'
        claims.put("sessionUA", userAgent);
        claims.put("sessionStart", System.currentTimeMillis());
        claims.put("fingerprint", fingerprint);
        claims.put("accountStatus", AccountStatus.ACTIVE.name());
        claims.put("tokenVersion", userOpt.isPresent()
                ? userOpt.get().getTokenVersion()
                : 1L);

        if (userId != null) {
            claims.put("userId", userId);
        }

        String jwt;
        String cookieName;
        String sigCookieName;
        int maxAge;

        if (request.partial()) {
            // Create partial session (for 2FA challenge)
            claims.put("requires2FA", true);
            claims.put("role", "ADMIN_2FA_CHALLENGED");
            jwt = jwtTokenProvider.createTokenWithMinutesExpiry(firebaseUid, claims, 10);
            cookieName = "partialSession";
            sigCookieName = "partialSessionSig";
            maxAge = 10 * 60; // 10 minutes
            log.info("[E2E] Created partial session token for 2FA challenge");
        } else {
            // Create full session
            jwt = jwtTokenProvider.createTokenWithClaims(firebaseUid, claims, 7);
            cookieName = "session";
            sigCookieName = "session_sig";
            maxAge = 7 * 24 * 60 * 60; // 7 days
            log.info("[E2E] Created full session token");
        }

        // Generate HMAC signature
        String signature = HmacUtils.generateHMAC(jwt, hmacSecret);

        // Set session cookie
        Cookie sessionCookie = new Cookie(cookieName, jwt);
        sessionCookie.setHttpOnly(true);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(maxAge);
        sessionCookie.setSecure(false); // Allow HTTP for tests
        response.addCookie(sessionCookie);

        // Set signature cookie
        Cookie sigCookie = new Cookie(sigCookieName, signature);
        sigCookie.setHttpOnly(true);
        sigCookie.setPath("/");
        sigCookie.setMaxAge(maxAge);
        sigCookie.setSecure(false);
        response.addCookie(sigCookie);

        log.info("[E2E] Set cookies: {} and {}", cookieName, sigCookieName);

        return ResponseEntity.ok(new MockSessionResponse(
            firebaseUid,
            userId,
            request.partial() ? "ADMIN_2FA_CHALLENGED" : request.role(),
            request.partial()
        ));
    }

    /**
     * Clears all session cookies.
     * Useful for logout scenarios in E2E tests.
     */
    @PostMapping("/clear-session")
    public ResponseEntity<Void> clearSession(HttpServletResponse response) {
        log.info("[E2E] Clearing session cookies");

        // Clear all possible session cookies
        for (String cookieName : new String[]{"session", "session_sig", "partialSession", "partialSessionSig"}) {
            Cookie cookie = new Cookie(cookieName, "");
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Creates a test user if it doesn't exist.
     * Useful for setting up E2E test data.
     */
    @PostMapping("/ensure-user")
    public ResponseEntity<Map<String, Object>> ensureUser(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String role = request.get("role");
        String firebaseUid = request.getOrDefault("firebaseUid", "E2E" + role + System.currentTimeMillis());

        log.info("[E2E] Ensuring user exists: email={}, role={}", email, role);

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            return ResponseEntity.ok(Map.of(
                "exists", true,
                "userId", user.getId(),
                "firebaseUid", user.getFirebaseUserId(),
                "email", user.getEmail(),
                "role", user.getUserType().name()
            ));
        }

        // Create new user
        User newUser = new User();
        newUser.setFirebaseUserId(firebaseUid);
        newUser.setEmail(email);
        newUser.setUserType(UserType.valueOf(role));
        newUser.setAccountStatus(AccountStatus.ACTIVE);

        User saved = userRepository.save(newUser);

        log.info("[E2E] Created new user: id={}, email={}", saved.getId(), email);

        return ResponseEntity.ok(Map.of(
            "exists", false,
            "created", true,
            "userId", saved.getId(),
            "firebaseUid", saved.getFirebaseUserId(),
            "email", saved.getEmail(),
            "role", saved.getUserType().name()
        ));
    }

    /**
     * Health check for test auth controller.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "profile", "e2e",
            "message", "Test auth controller is active"
        ));
    }

    /**
     * Request DTO for simulating influencer OAuth login.
     */
    public record SimulateInfluencerOAuthRequest(
        String firebaseUid,
        String email,
        boolean validateInstagramToken
    ) {}

    /**
     * Response DTO for simulated OAuth login.
     */
    public record SimulateInfluencerOAuthResponse(
        String firebaseUid,
        Long userId,
        Long socialConnectionId,
        String instagramUserId,
        String instagramUsername,
        Integer followersCount,
        boolean tokenValid
    ) {}

    /**
     * Simulates influencer OAuth login using existing Instagram token from Firestore.
     *
     * <p>This endpoint performs the following steps:
     * <ol>
     *   <li>Retrieves Instagram user data from TEST Firestore (automatically decrypts via KMS)</li>
     *   <li>Creates or finds the User in PostgreSQL with role INFLUENCER</li>
     *   <li>Creates or updates UserSocialConnection for Instagram platform</li>
     *   <li>Issues JWT session cookies with oauth=true and provider=instagram claims</li>
     * </ol>
     *
     * <p><b>Prerequisites:</b>
     * <ul>
     *   <li>Test influencer must exist in Firestore (instagramUsers/{firebaseUid})</li>
     *   <li>Instagram token must be stored and encrypted with KMS</li>
     *   <li>Firebase service account must have KMS decrypt permissions</li>
     * </ul>
     *
     * @param request Contains firebaseUid of existing test influencer
     * @param response HTTP response for setting cookies
     * @return Simulated OAuth response with user and social connection details
     */
    @PostMapping("/simulate-influencer-oauth")
    @Transactional
    public ResponseEntity<?> simulateInfluencerOAuth(
            @RequestBody SimulateInfluencerOAuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        log.info("[E2E] Simulating influencer OAuth for firebaseUid: {}", request.firebaseUid());

        // Get the actual User-Agent from the request for session fingerprinting
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            userAgent = "E2E-Test-Agent";
        }

        try {
            // Step 1: Retrieve Instagram data from Firestore (auto-decrypts via KMS)
            Map<String, Object> instagramData = firestoreService.getInstagramUserDataByFirebaseUid(request.firebaseUid());

            if (instagramData == null) {
                log.error("[E2E] No Instagram data found in Firestore for firebaseUid: {}", request.firebaseUid());
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Instagram data not found",
                    "message", "No Instagram user data found in Firestore for firebaseUid: " + request.firebaseUid(),
                    "hint", "Ensure the test influencer exists in the TEST Firestore collection: instagramUsers/" + request.firebaseUid()
                ));
            }

            String instagramUserId = getStringValue(instagramData, "user_id", "instagramId");
            String instagramUsername = getStringValue(instagramData, "username");
            String accessToken = getStringValue(instagramData, "access_token");
            Integer followersCount = getIntegerValue(instagramData, "followers_count");
            String profilePictureUrl = getStringValue(instagramData, "profile_picture_url");

            log.info("[E2E] Retrieved Instagram data: username={}, userId={}, followers={}",
                    instagramUsername, instagramUserId, followersCount);

            // Validate required fields
            if (instagramUserId == null || instagramUserId.isBlank()) {
                log.error("[E2E] Instagram user_id missing from Firestore data for firebaseUid: {}", request.firebaseUid());
                log.error("[E2E] Available keys in Firestore data: {}", instagramData.keySet());
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing Instagram user_id",
                    "message", "Firestore data exists but 'user_id' or 'instagramId' field is missing",
                    "availableKeys", String.join(", ", instagramData.keySet().stream().map(Object::toString).toList()),
                    "hint", "Check that the Firestore document contains 'user_id' field"
                ));
            }

            // Step 2: Optional - Validate Instagram token with Instagram API
            boolean tokenValid = true;
            if (request.validateInstagramToken() && accessToken != null) {
                // Token validation could be added here if needed
                // For now, we trust the token from Firestore is valid
                log.info("[E2E] Token validation requested but skipped (trusting Firestore data)");
            }

            // Step 3: Create or find User in PostgreSQL
            String email = request.email() != null ? request.email() :
                           instagramUsername + "@instagram-e2e.test";

            User user = userRepository.findByFirebaseUserId(request.firebaseUid())
                .orElseGet(() -> {
                    log.info("[E2E] Creating new user for firebaseUid: {}", request.firebaseUid());
                    User newUser = new User();
                    newUser.setFirebaseUserId(request.firebaseUid());
                    newUser.setEmail(email);
                    newUser.setUserType(UserType.INFLUENCER);
                    newUser.setAccountStatus(AccountStatus.ACTIVE);
                    return userRepository.save(newUser);
                });

            // Ensure user type is INFLUENCER
            if (user.getUserType() != UserType.INFLUENCER) {
                user.setUserType(UserType.INFLUENCER);
                user = userRepository.save(user);
                log.info("[E2E] Updated user type to INFLUENCER for userId: {}", user.getId());
            }

            // Make user effectively final for lambda
            final User finalUser = user;

            // Step 4: Create or update UserSocialConnection
            Platform instagramPlatform = platformRepository.findByName("Instagram")
                .orElseGet(() -> platformRepository.findByName("instagram")
                    .orElseThrow(() -> new IllegalStateException("Instagram platform not found in database")));

            // Capture variables for lambda
            final String finalInstagramUserId = instagramUserId;
            final String finalInstagramUsername = instagramUsername;
            final String finalProfilePictureUrl = profilePictureUrl;
            final Integer finalFollowersCount = followersCount;

            UserSocialConnection socialConnection = socialConnectionRepository
                .findByUserIdAndPlatformId(finalUser.getId(), instagramPlatform.getId())
                .orElseGet(() -> {
                    log.info("[E2E] Creating new social connection for userId: {}", finalUser.getId());
                    return UserSocialConnection.builder()
                        .user(finalUser)
                        .platform(instagramPlatform)
                        .socialUserId(finalInstagramUserId)
                        .displayName(finalInstagramUsername)
                        .profilePictureUrl(finalProfilePictureUrl)
                        .followersCount(finalFollowersCount)
                        .isPrimary(true)
                        .connectionStatus(ConnectionStatus.CONNECTED)
                        .createdTime(LocalDateTime.now())
                        .lastUpdateTime(LocalDateTime.now())
                        .build();
                });

            // Update connection if it already exists
            socialConnection.setSocialUserId(instagramUserId);
            socialConnection.setDisplayName(instagramUsername);
            socialConnection.setProfilePictureUrl(profilePictureUrl);
            socialConnection.setFollowersCount(followersCount);
            socialConnection.setConnectionStatus(ConnectionStatus.CONNECTED);
            socialConnection.setLastSyncTime(LocalDateTime.now());
            socialConnection.setLastUpdateTime(LocalDateTime.now());
            socialConnection = socialConnectionRepository.save(socialConnection);

            log.info("[E2E] Social connection ready: id={}, platform={}, username={}",
                    socialConnection.getId(), instagramPlatform.getName(), instagramUsername);

            // Step 5: Create JWT with OAuth claims (matching production TokenExchangeService)
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "INFLUENCER");
            claims.put("userId", user.getId().toString());
            claims.put("email", user.getEmail());
            claims.put("accountStatus", user.getAccountStatus().name());
            claims.put("tokenVersion", user.getTokenVersion() != null ? user.getTokenVersion() : 1L);

            // OAuth-specific claims
            claims.put("oauth", true);
            claims.put("provider", "instagram");
            claims.put("verifiedRole", true);
            claims.put("canAccessInfluencerFeatures", true);

            // Session fingerprint - use actual request values for session security validation
            // Generate fingerprint using same algorithm as SessionSecurityService
            // Use X-Forwarded-For header for IP spoofing in E2E tests
            String clientIp = RequestContextUtils.getClientIpAddress(httpRequest);
            String fingerprintData = (clientIp != null ? clientIp : "unknown") + ":" +
                    (userAgent != null ? userAgent : "unknown");
            String fingerprint = HmacUtils.generateHMAC(fingerprintData, hmacSecret + "-fingerprint");

            claims.put("sessionIp", clientIp);
            claims.put("sessionUA", userAgent);
            claims.put("sessionStart", System.currentTimeMillis());
            claims.put("fingerprint", fingerprint);

            String jwt = jwtTokenProvider.createTokenWithClaims(request.firebaseUid(), claims, 7);
            String signature = HmacUtils.generateHMAC(jwt, hmacSecret);

            // Set session cookies
            Cookie sessionCookie = new Cookie("session", jwt);
            sessionCookie.setHttpOnly(true);
            sessionCookie.setPath("/");
            sessionCookie.setMaxAge(7 * 24 * 60 * 60);
            sessionCookie.setSecure(false);
            response.addCookie(sessionCookie);

            Cookie sigCookie = new Cookie("session_sig", signature);
            sigCookie.setHttpOnly(true);
            sigCookie.setPath("/");
            sigCookie.setMaxAge(7 * 24 * 60 * 60);
            sigCookie.setSecure(false);
            response.addCookie(sigCookie);

            log.info("[E2E] Influencer OAuth simulation complete: userId={}, socialConnectionId={}",
                    user.getId(), socialConnection.getId());

            return ResponseEntity.ok(new SimulateInfluencerOAuthResponse(
                request.firebaseUid(),
                user.getId(),
                socialConnection.getId(),
                instagramUserId,
                instagramUsername,
                followersCount,
                tokenValid
            ));

        } catch (Exception e) {
            log.error("[E2E] Failed to simulate influencer OAuth", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Simulation failed");
            errorResponse.put("message", e.getMessage() != null ? e.getMessage() : e.toString());
            errorResponse.put("type", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // ==================== User Sync Endpoint ====================

    /**
     * Request DTO for syncing user from Firestore to PostgreSQL.
     */
    public record SyncUserFromFirestoreRequest(
        String firebaseUid,
        String role,
        boolean syncInstagramData,
        String email
    ) {}

    /**
     * Response DTO for user sync operation.
     */
    public record SyncUserFromFirestoreResponse(
        Long userId,
        String firebaseUid,
        String email,
        String userType,
        Long socialConnectionId,
        String instagramUserId,
        String instagramUsername,
        Integer followersCount,
        boolean created,
        boolean instagramSynced
    ) {}

    /**
     * Syncs a user from Firestore to PostgreSQL without creating a session.
     *
     * <p>This endpoint is designed for E2E test data setup. It ensures that a user
     * with the given firebaseUid exists in PostgreSQL, optionally syncing their
     * Instagram data from Firestore.
     *
     * <p><b>Use case:</b> When running E2E tests with Testcontainers (isolated PostgreSQL),
     * the database is empty but Firestore has real test user data. This endpoint bridges
     * the gap by syncing the necessary user records before test execution.
     *
     * <p><b>Key difference from simulate-influencer-oauth:</b> This endpoint does NOT
     * create session cookies. It only ensures data consistency between Firestore and PostgreSQL.
     *
     * @param request Contains firebaseUid, role, and sync options
     * @return Sync result with user and optional social connection details
     */
    @PostMapping("/sync-user-from-firestore")
    @Transactional
    public ResponseEntity<?> syncUserFromFirestore(@RequestBody SyncUserFromFirestoreRequest request) {
        log.info("[E2E] Syncing user from Firestore: firebaseUid={}, role={}, syncInstagram={}",
                request.firebaseUid(), request.role(), request.syncInstagramData());

        if (request.firebaseUid() == null || request.firebaseUid().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing firebaseUid",
                "message", "firebaseUid is required for syncing user data"
            ));
        }

        try {
            boolean created = false;
            boolean instagramSynced = false;
            Long socialConnectionId = null;
            String instagramUserId = null;
            String instagramUsername = null;
            Integer followersCount = null;

            // Step 1: Check if user already exists in PostgreSQL
            User user = userRepository.findByFirebaseUserId(request.firebaseUid()).orElse(null);

            if (user == null) {
                // Try to get email: request > Firebase Auth > Firestore > fallback
                String email = request.email();
                if (email == null || email.isBlank()) {
                    // Try Firebase Auth first (works for real Firebase users)
                    try {
                        var firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance()
                                .getUser(request.firebaseUid());
                        if (firebaseUser.getEmail() != null && !firebaseUser.getEmail().isBlank()) {
                            email = firebaseUser.getEmail();
                            log.info("[E2E] Got email from Firebase Auth: {}", email);
                        }
                    } catch (Exception e) {
                        log.debug("[E2E] Firebase Auth email lookup failed for {}: {}",
                                request.firebaseUid(), e.getMessage());
                    }
                }
                if (email == null || email.isBlank()) {
                    Map<String, Object> instagramData = firestoreService.getInstagramUserDataByFirebaseUid(request.firebaseUid());
                    if (instagramData != null) {
                        String username = getStringValue(instagramData, "username");
                        email = username != null ? username + "@instagram-e2e.test" : request.firebaseUid() + "@e2e.test";
                    } else {
                        email = request.firebaseUid() + "@e2e.test";
                    }
                }

                // Create new user
                user = new User();
                user.setFirebaseUserId(request.firebaseUid());
                user.setEmail(email);
                user.setUserType(UserType.valueOf(request.role()));
                user.setAccountStatus(AccountStatus.ACTIVE);
                user.setEmailVerified(true);  // E2E users are pre-verified
                user.setLastVerifiedEmail(email);
                user.setInitialAccountSetupCompleted(true);  // Prevent PG-wins from resetting emailVerified at login
                user = userRepository.save(user);
                created = true;

                // Also set Firebase emailVerified=true so login token exchange won't overwrite PG
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().updateUser(
                            new com.google.firebase.auth.UserRecord.UpdateRequest(request.firebaseUid()).setEmailVerified(true));
                    log.info("[E2E] Set Firebase emailVerified=true for new user: {}", request.firebaseUid());
                } catch (Exception e) {
                    log.warn("[E2E] Could not set Firebase emailVerified=true for new user: {}", e.getMessage());
                }

                log.info("[E2E] Created user: id={}, firebaseUid={}, email={}, type={}",
                        user.getId(), user.getFirebaseUserId(), user.getEmail(), user.getUserType());
            } else {
                log.info("[E2E] User already exists: id={}, firebaseUid={}, currentStatus={}",
                        user.getId(), user.getFirebaseUserId(), user.getAccountStatus());

                // Update role if different (role is optional for email-only sync during cleanup)
                boolean needsSave = false;
                if (request.role() != null && !request.role().isBlank()) {
                    UserType requestedType = UserType.valueOf(request.role());
                    if (user.getUserType() != requestedType) {
                        user.setUserType(requestedType);
                        needsSave = true;
                        log.info("[E2E] Updated user type to: {}", requestedType);
                    }
                }

                // ALWAYS reset accountStatus to ACTIVE during E2E sync
                // This prevents test pollution from admin ban/unban tests
                if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                    user.setAccountStatus(AccountStatus.ACTIVE);
                    needsSave = true;
                    log.info("[E2E] Reset account status from {} to ACTIVE", user.getAccountStatus());
                }

                // Self-healing email sync: if caller provides canonical email, use it as
                // source of truth and heal Firebase Auth if a previous test changed it.
                // Otherwise fall back to reading from Firebase Auth (legacy behavior).
                String targetEmail = null;
                if (request.email() != null && !request.email().isBlank()) {
                    targetEmail = request.email();
                } else {
                    try {
                        var fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getUser(request.firebaseUid());
                        targetEmail = fbUser.getEmail();
                    } catch (Exception e) {
                        log.warn("[E2E] Could not read email from Firebase Auth: {}", e.getMessage());
                    }
                }
                if (targetEmail != null) {
                    // Heal Firebase Auth email if it drifted
                    try {
                        var fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getUser(request.firebaseUid());
                        if (!targetEmail.equals(fbUser.getEmail())) {
                            com.google.firebase.auth.FirebaseAuth.getInstance().updateUser(
                                    new com.google.firebase.auth.UserRecord.UpdateRequest(request.firebaseUid()).setEmail(targetEmail));
                            log.info("[E2E] Healed Firebase Auth email: {} -> {}", fbUser.getEmail(), targetEmail);
                        }
                    } catch (Exception e) {
                        log.warn("[E2E] Could not heal Firebase Auth email: {}", e.getMessage());
                    }
                    // Sync to PG
                    if (!targetEmail.equals(user.getEmail())) {
                        log.info("[E2E] Syncing email to PG: {} -> {}", user.getEmail(), targetEmail);
                        user.setEmail(targetEmail);
                        needsSave = true;
                    }
                }

                // Reset rate-limit timestamps to prevent cross-test pollution
                if (user.getPasswordResetSentAt() != null) {
                    user.setPasswordResetSentAt(null);
                    needsSave = true;
                }
                if (user.getEmailVerificationSentAt() != null) {
                    user.setEmailVerificationSentAt(null);
                    needsSave = true;
                }

                // Ensure emailVerified=true for ACTIVE E2E users
                // (EmailVerificationEnforcementFilter blocks actions when emailVerified=false)
                if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                    user.setEmailVerified(true);
                    user.setLastVerifiedEmail(user.getEmail());
                    needsSave = true;
                    log.info("[E2E] Set emailVerified=true for ACTIVE user");
                }

                // Ensure initialAccountSetupCompleted=true for ACTIVE E2E users
                // (Prevents PG-wins enforcement from resetting Firebase emailVerified at login)
                if (!Boolean.TRUE.equals(user.getInitialAccountSetupCompleted())) {
                    user.setInitialAccountSetupCompleted(true);
                    needsSave = true;
                    log.info("[E2E] Set initialAccountSetupCompleted=true for ACTIVE user");
                }

                if (needsSave) {
                    user = userRepository.save(user);
                    // Evict user from Redis cache to ensure token exchange reads fresh status
                    // This prevents stale BANNED status from blocking authentication
                    userCacheService.evict(request.firebaseUid());
                    log.info("[E2E] Evicted user cache for: {}", request.firebaseUid());
                }

                // Also set Firebase emailVerified=true so login token exchange won't overwrite PG
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().updateUser(
                            new com.google.firebase.auth.UserRecord.UpdateRequest(request.firebaseUid()).setEmailVerified(true));
                } catch (Exception e) {
                    log.warn("[E2E] Could not set Firebase emailVerified=true for existing user: {}", e.getMessage());
                }
            }

            // Step 2: Sync Instagram data if requested and role is INFLUENCER
            if (request.syncInstagramData() && "INFLUENCER".equals(request.role())) {
                Map<String, Object> instagramData = firestoreService.getInstagramUserDataByFirebaseUid(request.firebaseUid());

                if (instagramData != null) {
                    instagramUserId = getStringValue(instagramData, "user_id", "instagramId");
                    instagramUsername = getStringValue(instagramData, "username");
                    followersCount = getIntegerValue(instagramData, "followers_count");
                    String profilePictureUrl = getStringValue(instagramData, "profile_picture_url");

                    // Find Instagram platform
                    Platform instagramPlatform = platformRepository.findByName("Instagram")
                        .orElseGet(() -> platformRepository.findByName("instagram")
                            .orElse(null));

                    if (instagramPlatform != null && instagramUserId != null) {
                        // Create variables for lambda
                        final User finalUser = user;
                        final String finalInstagramUserId = instagramUserId;
                        final String finalInstagramUsername = instagramUsername;
                        final String finalProfilePictureUrl = profilePictureUrl;
                        final Integer finalFollowersCount = followersCount;

                        UserSocialConnection connection = socialConnectionRepository
                            .findByUserIdAndPlatformId(user.getId(), instagramPlatform.getId())
                            .orElseGet(() -> {
                                log.info("[E2E] Creating social connection for userId: {}", finalUser.getId());
                                return UserSocialConnection.builder()
                                    .user(finalUser)
                                    .platform(instagramPlatform)
                                    .socialUserId(finalInstagramUserId)
                                    .displayName(finalInstagramUsername)
                                    .profilePictureUrl(finalProfilePictureUrl)
                                    .followersCount(finalFollowersCount)
                                    .isPrimary(true)
                                    .connectionStatus(ConnectionStatus.CONNECTED)
                                    .createdTime(LocalDateTime.now())
                                    .lastUpdateTime(LocalDateTime.now())
                                    .build();
                            });

                        // Update existing connection
                        connection.setSocialUserId(instagramUserId);
                        connection.setDisplayName(instagramUsername);
                        connection.setProfilePictureUrl(profilePictureUrl);
                        connection.setFollowersCount(followersCount);
                        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
                        connection.setLastSyncTime(LocalDateTime.now());
                        connection.setLastUpdateTime(LocalDateTime.now());
                        connection = socialConnectionRepository.save(connection);

                        socialConnectionId = connection.getId();
                        instagramSynced = true;

                        log.info("[E2E] Synced Instagram connection: id={}, instagramUserId={}, username={}",
                                connection.getId(), instagramUserId, instagramUsername);
                    } else {
                        log.warn("[E2E] Instagram platform not found or no Instagram user ID - skipping social connection");
                    }
                } else {
                    log.warn("[E2E] No Instagram data in Firestore for firebaseUid: {}", request.firebaseUid());
                }
            }

            return ResponseEntity.ok(new SyncUserFromFirestoreResponse(
                user.getId(),
                user.getFirebaseUserId(),
                user.getEmail(),
                user.getUserType().name(),
                socialConnectionId,
                instagramUserId,
                instagramUsername,
                followersCount,
                created,
                instagramSynced
            ));

        } catch (Exception e) {
            log.error("[E2E] Failed to sync user from Firestore", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Sync failed",
                "message", e.getMessage(),
                "type", e.getClass().getSimpleName()
            ));
        }
    }

    // ==================== Force Firebase Claims Endpoint ====================

    /**
     * Request DTO for forcing Firebase custom claims update.
     */
    public record ForceFirebaseClaimsRequest(
        String firebaseUid,
        String role
    ) {}

    /**
     * Forces Firebase custom claims to be set regardless of current state.
     * This is critical for E2E tests where database and Firebase claims may be out of sync.
     *
     * <p>The JWT role is determined by Firebase custom claims, NOT the PostgreSQL database.
     * If claims are stale (e.g., role=null or role=USER when DB has COMPANY), the user
     * will get wrong role in their JWT. This endpoint forces the claims to be set correctly.
     *
     * @param request Contains firebaseUid and desired role
     * @return Success response with updated claims
     */
    @PostMapping("/force-firebase-claims")
    public ResponseEntity<?> forceFirebaseClaims(@RequestBody ForceFirebaseClaimsRequest request) {
        log.info("[E2E] Forcing Firebase claims for firebaseUid={}, role={}",
                request.firebaseUid(), request.role());

        if (request.firebaseUid() == null || request.firebaseUid().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing firebaseUid",
                "message", "firebaseUid is required"
            ));
        }

        if (request.role() == null || request.role().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing role",
                "message", "role is required (COMPANY, INFLUENCER, ADMIN)"
            ));
        }

        try {
            // Get existing claims to preserve provider/instagram info
            var firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .getUser(request.firebaseUid());
            Map<String, Object> existingClaims = firebaseUser.getCustomClaims();

            // Build new claims with forced role
            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put("role", request.role());
            newClaims.put("activated", true);
            newClaims.put("forcedAt", java.time.Instant.now().toString());

            // Preserve existing provider/instagram claims
            if (existingClaims != null) {
                if (existingClaims.containsKey("provider")) {
                    newClaims.put("provider", existingClaims.get("provider"));
                }
                if (existingClaims.containsKey("instagramId")) {
                    newClaims.put("instagramId", existingClaims.get("instagramId"));
                }
                if (existingClaims.containsKey("instagramUsername")) {
                    newClaims.put("instagramUsername", existingClaims.get("instagramUsername"));
                }
            }

            // Force set the claims
            com.google.firebase.auth.FirebaseAuth.getInstance()
                    .setCustomUserClaims(request.firebaseUid(), newClaims);

            // Verify the update
            var updatedUser = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .getUser(request.firebaseUid());
            Map<String, Object> updatedClaims = updatedUser.getCustomClaims();

            log.info("[E2E] Firebase claims forced successfully: firebaseUid={}, newClaims={}",
                    request.firebaseUid(), updatedClaims);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "firebaseUid", request.firebaseUid(),
                "role", request.role(),
                "claims", updatedClaims != null ? updatedClaims : Map.of()
            ));

        } catch (Exception e) {
            log.error("[E2E] Failed to force Firebase claims for {}: {}",
                    request.firebaseUid(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to force Firebase claims",
                "message", e.getMessage(),
                "type", e.getClass().getSimpleName()
            ));
        }
    }

    // ==================== Firebase User State Management (E2E only) ====================

    /**
     * Request DTO for updating Firebase user state.
     */
    public record UpdateFirebaseUserRequest(
        String firebaseUid,
        String password,
        Boolean emailVerified,
        String email
    ) {}

    /**
     * Updates a real Firebase Auth user's state via Admin SDK.
     * Used by E2E tests to set up / clean up Firebase state for happy-path magic link tests.
     *
     * <p>All fields except firebaseUid are optional — only non-null fields are applied.
     *
     * @param request Contains firebaseUid (required) and optional password, emailVerified, email
     * @return Updated user info from Firebase
     */
    @PostMapping("/update-firebase-user")
    public ResponseEntity<?> updateFirebaseUser(@RequestBody UpdateFirebaseUserRequest request) {
        log.info("[E2E] Updating Firebase user: firebaseUid={}, password={}, emailVerified={}, email={}",
                request.firebaseUid(),
                request.password() != null ? "***" : null,
                request.emailVerified(),
                request.email());

        if (request.firebaseUid() == null || request.firebaseUid().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing firebaseUid",
                "message", "firebaseUid is required"
            ));
        }

        try {
            var updateRequest = new com.google.firebase.auth.UserRecord.UpdateRequest(request.firebaseUid());

            if (request.password() != null) {
                updateRequest.setPassword(request.password());
            }
            if (request.emailVerified() != null) {
                updateRequest.setEmailVerified(request.emailVerified());
            }
            if (request.email() != null) {
                updateRequest.setEmail(request.email());
            }

            var updatedUser = com.google.firebase.auth.FirebaseAuth.getInstance().updateUser(updateRequest);

            log.info("[E2E] Firebase user updated: firebaseUid={}, emailVerified={}, email={}",
                    updatedUser.getUid(), updatedUser.isEmailVerified(), updatedUser.getEmail());

            return ResponseEntity.ok(Map.of(
                "firebaseUid", updatedUser.getUid(),
                "emailVerified", updatedUser.isEmailVerified(),
                "email", updatedUser.getEmail() != null ? updatedUser.getEmail() : ""
            ));
        } catch (Exception e) {
            log.error("[E2E] Failed to update Firebase user {}: {}",
                    request.firebaseUid(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to update Firebase user",
                "message", e.getMessage(),
                "type", e.getClass().getSimpleName()
            ));
        }
    }

    // ==================== OobCode Generation (E2E only) ====================

    /**
     * Generate a synthetic verification oobCode and store it in Redis.
     * Used by E2E error scenarios that need a valid oobCode but don't test the email delivery.
     * The oobCode is stored in Redis so applyActionCode can consume it via Admin SDK.
     *
     * <p>Does NOT call Firebase's generateEmailVerificationLink (avoids external rate limits).
     * The synthetic code works because applyActionCode validates against our Redis store, not Firebase.
     */
    @PostMapping("/generate-verification-oob")
    public ResponseEntity<?> generateVerificationOob(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String firebaseUid = request.get("firebaseUid");

        log.info("[E2E] Generating synthetic verification oobCode for email={}, uid={}", email, firebaseUid);

        String oobCode = "E2E_VERIFY_" + java.util.UUID.randomUUID().toString().replace("-", "");

        // Store in Redis so applyActionCode can consume it
        emailVerificationService.storeOobCode(oobCode, firebaseUid, email);

        log.info("[E2E] Generated synthetic verification oobCode (length={})", oobCode.length());
        return ResponseEntity.ok(Map.of("oobCode", oobCode));
    }

    /**
     * Send a verification email WITHOUT calling Firebase's generateEmailVerificationLink.
     * Generates a synthetic oobCode, stores it in Redis, and sends the real SMTP email
     * (captured by GreenMail in E2E). This bypasses Firebase's external rate limit
     * (TOO_MANY_ATTEMPTS_TRY_LATER) which causes flaky E2E tests.
     *
     * <p>The complete-verification endpoint validates oobCodes against Redis (not Firebase),
     * so synthetic codes work identically to Firebase-generated ones.
     */
    @PostMapping("/send-verification-email-bypass")
    public ResponseEntity<?> sendVerificationEmailBypass(@RequestBody Map<String, String> request) {
        String firebaseUid = request.get("firebaseUid");

        log.info("[E2E] Sending verification email bypassing Firebase for uid={}", firebaseUid);

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new com.sm.instagram.platform.common.exceptions.ResourceNotFoundException(
                        "error.business.item_not_found", "User"));

        // Generate synthetic oobCode (same pattern as generate-verification-oob)
        String oobCode = "E2E_VERIFY_" + java.util.UUID.randomUUID().toString().replace("-", "");

        // Store in Redis so complete-verification can consume it
        emailVerificationService.storeOobCode(oobCode, firebaseUid, user.getEmail());

        // Build verification link (same logic as EmailVerificationService)
        String ut = switch (user.getUserType()) {
            case INFLUENCER -> "I";
            case COMPANY -> "C";
            case ADMIN, PENDING_ADMIN -> "A";
        };
        String iac = Boolean.TRUE.equals(user.getInitialAccountSetupCompleted()) ? "1" : "0";
        String verificationLink = frontendUrl + "/auth/action?mode=verifyEmail&oobCode=" + oobCode + "&ut=" + ut + "&iac=" + iac;

        // Send real email via SMTP (GreenMail captures it)
        try {
            emailService.sendVerificationEmail(
                    user.getEmail(),
                    user.getFirstName() != null ? user.getFirstName() : "User",
                    verificationLink,
                    "en"
            );
        } catch (jakarta.mail.MessagingException e) {
            log.error("[E2E] Failed to send verification email via SMTP: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "SMTP send failed",
                    "message", e.getMessage()
            ));
        }

        log.info("[E2E] Verification email sent via SMTP bypass for uid={}", firebaseUid);
        return ResponseEntity.ok(Map.of("oobCode", oobCode, "email", user.getEmail()));
    }

    /**
     * Generate a password reset oobCode directly via Admin SDK without sending an email.
     * Used by E2E error scenarios that need a valid oobCode but don't test the email delivery.
     */
    @PostMapping("/generate-password-reset-oob")
    public ResponseEntity<?> generatePasswordResetOob(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        log.info("[E2E] Generating password reset oobCode for email={}", email);

        try {
            var settings = com.google.firebase.auth.ActionCodeSettings.builder()
                    .setUrl("https://localhost:4200/auth/sign-in?passwordReset=success")
                    .setHandleCodeInApp(false)
                    .build();

            String firebaseLink = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .generatePasswordResetLink(email, settings);

            String oobCode = org.springframework.web.util.UriComponentsBuilder.fromUriString(firebaseLink)
                    .build().getQueryParams().getFirst("oobCode");

            log.info("[E2E] Generated password reset oobCode (length={})", oobCode != null ? oobCode.length() : 0);
            return ResponseEntity.ok(Map.of("oobCode", oobCode));

        } catch (Exception e) {
            log.error("[E2E] Failed to generate password reset oobCode: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to generate password reset oobCode",
                    "message", e.getMessage()
            ));
        }
    }

    // ==================== Consent E2E Test Endpoints ====================

    /**
     * Set user account status directly (E2E only).
     * Used to test ConsentEnforcementFilter with BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS status.
     */
    @PostMapping("/set-account-status")
    @Transactional
    public ResponseEntity<?> setAccountStatus(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String status = request.get("status");

        log.info("[E2E] Setting account status: email={}, status={}", email, status);

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("User not found: " + email));
        user.setAccountStatus(AccountStatus.valueOf(status));

        if (AccountStatus.BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS.name().equals(status)) {
            user.setNewestConsentsAccepted(false);
        }

        userRepository.save(user);
        userCacheService.evict(user.getFirebaseUserId());

        return ResponseEntity.ok(Map.of("userId", user.getId(), "status", status));
    }

    /**
     * Reset an influencer to pre-verification state for E2E re-testing.
     * Resets PG (IN_VALIDATION, emailVerified=false) and Firebase (removes password provider,
     * sets emailVerified=false). Allows testing the verify+password flow multiple times
     * with the same influencer user.
     */
    @PostMapping("/reset-influencer-for-verification")
    @Transactional
    public ResponseEntity<?> resetInfluencerForVerification(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        log.info("[E2E] Resetting influencer for verification: email={}", email);

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("User not found: " + email));

        // Reset PostgreSQL state
        user.setAccountStatus(AccountStatus.IN_VALIDATION);
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);
        user.setEmailVerificationSentAt(null);
        userRepository.save(user);

        // Reset Firebase state: remove password provider + reset emailVerified
        try {
            var updateRequest = new com.google.firebase.auth.UserRecord.UpdateRequest(user.getFirebaseUserId())
                    .setProvidersToUnlink(java.util.List.of("password"))
                    .setEmailVerified(false);
            com.google.firebase.auth.FirebaseAuth.getInstance().updateUser(updateRequest);
            log.info("[E2E] Firebase state reset for uid={}", user.getFirebaseUserId());
        } catch (Exception e) {
            log.warn("[E2E] Firebase reset partial failure (may not have had password provider): {}", e.getMessage());
            // Try without unlinking password provider (user may not have one yet)
            try {
                var updateRequest = new com.google.firebase.auth.UserRecord.UpdateRequest(user.getFirebaseUserId())
                        .setEmailVerified(false);
                com.google.firebase.auth.FirebaseAuth.getInstance().updateUser(updateRequest);
            } catch (Exception e2) {
                log.error("[E2E] Firebase reset failed entirely: {}", e2.getMessage());
            }
        }

        // Evict cache
        userCacheService.evict(user.getFirebaseUserId());

        log.info("[E2E] Influencer reset complete: email={}, userId={}", email, user.getId());
        return ResponseEntity.ok(Map.of("reset", true, "userId", user.getId(), "email", email));
    }

    /**
     * Register user without Firebase (E2E only).
     * Skips Firebase user creation but runs the same consent validation and processing.
     * Allows isolated consent E2E tests without depending on Firebase.
     */
    @PostMapping("/register-without-firebase")
    @Transactional
    public ResponseEntity<?> registerWithoutFirebase(
            @RequestBody RegisterUserRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        log.info("[E2E] Register without Firebase: email={}, userType={}", request.getEmail(), request.getUserType());

        // Run the same consent validation as the real registration flow
        legalConsentService.validateConsentCookiesPresent(httpRequest);

        // Create user directly in DB (skip Firebase)
        String firebaseUid = "E2E_CONSENT_" + System.currentTimeMillis();
        User user = new User();
        user.setFirebaseUserId(firebaseUid);
        user.setEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUserType(UserType.valueOf(request.getUserType()));
        user.setAccountStatus(AccountStatus.IN_VALIDATION);
        user.setCreatedTime(LocalDateTime.now());
        user.setLastUpdateTime(LocalDateTime.now());

        if (UserType.COMPANY.name().equals(request.getUserType()) && request.getCompanyName() != null) {
            user.setName(request.getCompanyName());
        }

        User savedUser = userRepository.save(user);

        // Create default user preferences
        userPreferencesService.createDefaultPreferences(savedUser);

        // Process consent cookies (same as real registration)
        legalConsentService.processRegistrationConsents(
                savedUser.getId(), httpRequest, response, ConsentSource.REGISTRATION);

        log.info("[E2E] Registered user without Firebase: userId={}, firebaseUid={}", savedUser.getId(), firebaseUid);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", savedUser.getId(),
                "firebaseUid", firebaseUid,
                "email", savedUser.getEmail(),
                "userType", savedUser.getUserType().name()
        ));
    }

    /**
     * Seed an Instagram UserSocialConnection for a mock-session INFLUENCER.
     *
     * <p>The production INFLUENCER apply flow ({@code POST /applied-opportunity})
     * requires the caller to have an active social connection — without one
     * the SocialConnectionGuard rejects with 403 Insufficient Permissions.
     * Mock-session targets don't have one by default, so notification +
     * partnership-application integration tests self-skip at the apply step.
     *
     * <p>This endpoint short-circuits that blocker by creating (or refreshing)
     * a CONNECTED Instagram connection with deterministic dummy data, scoped
     * to the user identified by {@code email}. No Firestore / KMS / Firebase
     * round-trip — pure PG mutation + Redis cache evict.
     *
     * <p>Profile-guarded to {@code (e2e | dev-lite) & !prod & !test} per the
     * class annotation — never registered in production.
     */
    @PostMapping("/seed-instagram-connection")
    @Transactional
    public ResponseEntity<?> seedInstagramConnection(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing email"));
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "User not found",
                "email", email,
                "hint", "Call /test/auth/mock-session first to create the user"
            ));
        }

        Platform instagramPlatform = platformRepository.findByName("Instagram")
            .orElseGet(() -> platformRepository.findByName("instagram").orElse(null));
        if (instagramPlatform == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Instagram platform not seeded",
                "hint", "Liquibase platform seed must run before this endpoint is callable"
            ));
        }

        final User finalUser = user;
        final long now = System.currentTimeMillis();
        final String dummyHandle = "e2e_" + email.split("@")[0];

        UserSocialConnection conn = socialConnectionRepository
            .findByUserIdAndPlatformId(user.getId(), instagramPlatform.getId())
            .orElseGet(() -> UserSocialConnection.builder()
                .user(finalUser)
                .platform(instagramPlatform)
                .socialUserId("E2E_INSTAGRAM_" + now)
                .displayName(dummyHandle)
                .followersCount(1500)
                .isPrimary(true)
                .connectionStatus(ConnectionStatus.CONNECTED)
                .createdTime(LocalDateTime.now())
                .lastUpdateTime(LocalDateTime.now())
                .build());

        // Update existing or freshen newly-built connection
        conn.setConnectionStatus(ConnectionStatus.CONNECTED);
        if (conn.getSocialUserId() == null || conn.getSocialUserId().isBlank()) {
            conn.setSocialUserId("E2E_INSTAGRAM_" + now);
        }
        if (conn.getDisplayName() == null || conn.getDisplayName().isBlank()) {
            conn.setDisplayName(dummyHandle);
        }
        if (conn.getFollowersCount() == null) {
            conn.setFollowersCount(1500);
        }
        conn.setLastSyncTime(LocalDateTime.now());
        conn.setLastUpdateTime(LocalDateTime.now());
        conn = socialConnectionRepository.save(conn);

        // Evict user cache so SocialConnectionGuard re-reads the fresh state
        userCacheService.evict(user.getFirebaseUserId());

        log.info("[E2E] Seeded Instagram connection: userId={}, connectionId={}, handle={}",
                user.getId(), conn.getId(), conn.getDisplayName());

        return ResponseEntity.ok(Map.of(
            "userId", user.getId(),
            "socialConnectionId", conn.getId(),
            "instagramUsername", conn.getDisplayName(),
            "followersCount", conn.getFollowersCount()
        ));
    }

    // ==================== Helper Methods ====================

    /**
     * Helper to extract string value from map with fallback keys.
     */
    private String getStringValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Helper to extract integer value from map.
     */
    private Integer getIntegerValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
