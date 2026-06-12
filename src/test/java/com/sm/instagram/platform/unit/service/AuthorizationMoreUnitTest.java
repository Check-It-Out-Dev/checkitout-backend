package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.service.SessionSecurityService;
import com.sm.instagram.platform.common.authorization.*;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Extended Unit tests for Authorization module classes.
 * Covers JwtAuthenticationFilter, AdminCheckRunner, Permission enum,
 * and additional edge cases for authorization components.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Authorization Module Extended Unit Tests")
class AuthorizationMoreUnitTest {

    private static final String JWT_SECRET = "test-jwt-secret-key-that-is-long-enough-for-hmac-256";
    private static final String HMAC_SECRET = "test-hmac-secret-key-for-cookies";

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private SessionSecurityService sessionSecurityService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TotpFirestoreService totpFirestoreService;

    @Mock
    private UserRecord userRecord;

    @Mock
    private Claims claims;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                userCacheService,
                sessionSecurityService,
                messageSource,
                objectMapper
        );
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "cookieHmacSecret", HMAC_SECRET);
    }

    private String generateValidJwt(String subject, String role, Long dbId) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600000); // 1 hour

        return Jwts.builder()
                .setSubject(subject)
                .claim("role", role)
                .claim("dbId", dbId)
                .claim("tokenVersion", 1L)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key)
                .compact();
    }

    private String generateExpiredJwt(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiration = new Date(now.getTime() - 3600000); // 1 hour ago

        return Jwts.builder()
                .setSubject(subject)
                .claim("role", "INFLUENCER")
                .setIssuedAt(new Date(now.getTime() - 7200000))
                .setExpiration(expiration)
                .signWith(key)
                .compact();
    }

    private String generateHmacSignature(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    HMAC_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(keySpec);
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Cookie createCookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        return cookie;
    }

    private User createUser(Long id, String firebaseUid, UserType userType) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        return user;
    }

    private void setupErrorResponseMocks() throws Exception {
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("Error");
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    // =========================================================================
    // JWT AUTHENTICATION FILTER - PUBLIC ENDPOINT TESTS
    // =========================================================================
    @Nested
    @DisplayName("JwtAuthenticationFilter - Public Endpoints")
    class JwtFilterPublicEndpointTests {

        @Test
        @DisplayName("should allow auth/login endpoint without authentication")
        void shouldAllowAuthLoginWithoutAuthentication() throws Exception {
            when(request.getRequestURI()).thenReturn("/auth/login");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow auth/register endpoint without authentication")
        void shouldAllowAuthRegisterWithoutAuthentication() throws Exception {
            when(request.getRequestURI()).thenReturn("/auth/register");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow api/auth/login endpoint without authentication")
        void shouldAllowApiAuthLoginWithoutAuthentication() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/auth/login");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow api/health endpoint without authentication")
        void shouldAllowApiHealthWithoutAuthentication() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/health");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow error endpoint without authentication")
        void shouldAllowErrorWithoutAuthentication() throws Exception {
            when(request.getRequestURI()).thenReturn("/error");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow actuator/health endpoint without authentication")
        void shouldAllowActuatorHealthWithoutAuthentication() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/actuator/health");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow POST to /api/support/ticket without authentication")
        void shouldAllowSupportTicketCreation() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/ticket");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow GET to /api/support/ticket/status without authentication")
        void shouldAllowSupportTicketStatusCheck() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/ticket/status");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow POST to /api/support/ticket/response without authentication")
        void shouldAllowSupportTicketResponse() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/ticket/response");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow FAQ endpoints without authentication")
        void shouldAllowFaqEndpoints() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/faq/list");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow ticket attachments upload without authentication")
        void shouldAllowTicketAttachments() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/ticket/123/attachments");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should require authentication for response attachments")
        void shouldRequireAuthForResponseAttachments() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/ticket/123/response/456/attachments");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should NOT treat refresh-session as public endpoint")
        void shouldNotTreatRefreshSessionAsPublic() throws Exception {
            when(request.getRequestURI()).thenReturn("/auth/refresh-session");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    // =========================================================================
    // JWT AUTHENTICATION FILTER - VALID TOKEN TESTS
    // =========================================================================
    @Nested
    @DisplayName("JwtAuthenticationFilter - Valid Token Processing")
    class JwtFilterValidTokenTests {

        @Test
        @DisplayName("should authenticate user with valid full session token")
        void shouldAuthenticateWithValidFullSessionToken() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid-123")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("firebase-uid-123");
        }

        @Test
        @DisplayName("should authenticate user with partial session token")
        void shouldAuthenticateWithPartialSessionToken() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "PENDING_ADMIN", 1L);
            String signature = generateHmacSignature(token);

            Cookie partialCookie = createCookie("partialSession", token);
            Cookie partialSigCookie = createCookie("partialSessionSig", signature);

            when(request.getRequestURI()).thenReturn("/api/twofactor/verify");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(new Cookie[]{partialCookie, partialSigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid-123")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        @Test
        @DisplayName("should add PARTIAL_AUTH authority for partial session")
        void shouldAddPartialAuthAuthorityForPartialSession() throws Exception {
            SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setSubject("firebase-uid-123")
                    .claim("role", "PENDING_ADMIN")
                    .claim("PARTIAL_AUTH", true)
                    .claim("PENDING_2FA", true)
                    .claim("tokenVersion", 1L)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(key)
                    .compact();

            String signature = generateHmacSignature(token);

            Cookie partialCookie = createCookie("partialSession", token);
            Cookie partialSigCookie = createCookie("partialSessionSig", signature);

            when(request.getRequestURI()).thenReturn("/api/twofactor/setup");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(new Cookie[]{partialCookie, partialSigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid-123")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(auth -> auth.getAuthority().equals("PARTIAL_AUTH"));
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(auth -> auth.getAuthority().equals("PENDING_2FA"));
        }
    }

    // =========================================================================
    // JWT AUTHENTICATION FILTER - INVALID TOKEN TESTS
    // =========================================================================
    @Nested
    @DisplayName("JwtAuthenticationFilter - Invalid Token Handling")
    class JwtFilterInvalidTokenTests {

        @Test
        @DisplayName("should reject request with invalid HMAC signature")
        void shouldRejectRequestWithInvalidHmacSignature() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "INFLUENCER", 1L);
            String invalidSignature = "invalid-signature";

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", invalidSignature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should reject request with expired JWT")
        void shouldRejectRequestWithExpiredJwt() throws Exception {
            String expiredToken = generateExpiredJwt("firebase-uid-123");
            String signature = generateHmacSignature(expiredToken);

            Cookie sessionCookie = createCookie("session", expiredToken);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject request when session validation fails")
        void shouldRejectRequestWhenSessionValidationFails() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(false);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(sessionSecurityService).terminateSession(response);
        }

        @Test
        @DisplayName("should reject request when user is not active")
        void shouldRejectRequestWhenUserNotActive() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(false);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should return 419 when token version mismatch")
        void shouldReturn419WhenTokenVersionMismatch() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid-123")).thenReturn(2L);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(419);
        }

        @Test
        @DisplayName("should allow refresh-session endpoint even with token version mismatch")
        void shouldAllowRefreshSessionWithTokenVersionMismatch() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/auth/refresh-session");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid-123")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid-123")).thenReturn(2L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(response, never()).setStatus(419);
        }

        @Test
        @DisplayName("should reject request when no cookies provided for protected endpoint")
        void shouldRejectRequestWhenNoCookiesProvided() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject request when only session cookie provided without signature")
        void shouldRejectRequestWhenMissingSignatureCookie() throws Exception {
            String token = generateValidJwt("firebase-uid-123", "INFLUENCER", 1L);
            Cookie sessionCookie = createCookie("session", token);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    // =========================================================================
    // JWT AUTHENTICATION FILTER - OPTIONAL AUTH ENDPOINTS
    // =========================================================================
    @Nested
    @DisplayName("JwtAuthenticationFilter - Optional Auth Endpoints")
    class JwtFilterOptionalAuthTests {

        @Test
        @DisplayName("should allow public opportunities endpoint without auth")
        void shouldAllowPublicOpportunitiesWithoutAuth() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/public/opportunities");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow search influencers endpoint without auth")
        void shouldAllowSearchInfluencersWithoutAuth() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/search/influencers");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // =========================================================================
    // ADMIN CHECK RUNNER TESTS
    // =========================================================================
    @Nested
    @DisplayName("AdminCheckRunner Tests")
    class AdminCheckRunnerTests {

        private AdminCheckRunner adminCheckRunner;

        @BeforeEach
        void setUp() {
            adminCheckRunner = new AdminCheckRunner(
                    firebaseAuth,
                    userManagementService,
                    userRepository,
                    totpFirestoreService
            );
        }

        @Test
        @DisplayName("should throw exception when ADMIN_FIREBASE_UID not configured")
        void shouldThrowWhenAdminFirebaseUidNotConfigured() {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            assertThatThrownBy(() -> adminCheckRunner.run())
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("ADMIN_FIREBASE_UID");
        }

        @Test
        @DisplayName("should throw exception when ADMIN_EMAIL not configured")
        void shouldThrowWhenAdminEmailNotConfigured() {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "");

            assertThatThrownBy(() -> adminCheckRunner.run())
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("ADMIN_EMAIL");
        }

        @Test
        @DisplayName("should throw exception when ADMIN_FIREBASE_UID is null")
        void shouldThrowWhenAdminFirebaseUidNull() {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", null);
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            assertThatThrownBy(() -> adminCheckRunner.run())
                    .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("should throw exception when ADMIN_EMAIL is null")
        void shouldThrowWhenAdminEmailNull() {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", null);

            assertThatThrownBy(() -> adminCheckRunner.run())
                    .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("should throw exception when ADMIN_FIREBASE_UID is whitespace only")
        void shouldThrowWhenAdminFirebaseUidWhitespace() {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "   ");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            assertThatThrownBy(() -> adminCheckRunner.run())
                    .isInstanceOf(ExternalServiceException.class);
        }

        @Test
        @DisplayName("should create admin in database when not exists and 2FA enabled")
        void shouldCreateAdminWhenNotExistsAnd2FAEnabled() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRecord.getDisplayName()).thenReturn("Admin User");
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.empty());

            User savedUser = createUser(1L, "admin-uid", UserType.ADMIN);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            adminCheckRunner.run();

            verify(userRepository, atLeastOnce()).save(any(User.class));
            verify(firebaseAuth).setCustomUserClaims(eq("admin-uid"), argThat(claims ->
                    "ADMIN".equals(claims.get("role"))
            ));
        }

        @Test
        @DisplayName("should set PENDING_ADMIN role when 2FA not enabled")
        void shouldSetPendingAdminRoleWhen2FANotEnabled() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRecord.getDisplayName()).thenReturn("Admin User");
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(false);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.empty());

            User savedUser = createUser(1L, "admin-uid", UserType.PENDING_ADMIN);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            adminCheckRunner.run();

            verify(firebaseAuth).setCustomUserClaims(eq("admin-uid"), argThat(claims ->
                    "PENDING_ADMIN".equals(claims.get("role"))
            ));
        }

        @Test
        @DisplayName("should update existing admin user type when 2FA status changes")
        void shouldUpdateExistingAdminUserType() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            User existingUser = createUser(1L, "admin-uid", UserType.PENDING_ADMIN);
            existingUser.setEmail("old@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "PENDING_ADMIN"));
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(existingUser);

            adminCheckRunner.run();

            verify(userRepository).save(argThat(user ->
                    user.getUserType() == UserType.ADMIN &&
                            "admin@test.com".equals(user.getEmail())
            ));
        }

        @Test
        @DisplayName("should update Firebase claims when role mismatch")
        void shouldUpdateFirebaseClaimsWhenRoleMismatch() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            User existingUser = createUser(1L, "admin-uid", UserType.ADMIN);

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "PENDING_ADMIN"));
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.of(existingUser));

            adminCheckRunner.run();

            verify(firebaseAuth).setCustomUserClaims(eq("admin-uid"), argThat(claims ->
                    "ADMIN".equals(claims.get("role"))
            ));
        }

        @Test
        @DisplayName("should not update Firebase claims when role already correct")
        void shouldNotUpdateFirebaseClaimsWhenRoleCorrect() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            User existingUser = createUser(1L, "admin-uid", UserType.ADMIN);

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "ADMIN"));
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.of(existingUser));

            adminCheckRunner.run();

            verify(firebaseAuth, never()).setCustomUserClaims(anyString(), any());
        }

        @Test
        @DisplayName("should throw exception when Firebase operation fails")
        void shouldThrowExceptionWhenFirebaseOperationFails() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
            when(firebaseException.getMessage()).thenReturn("User not found");
            when(firebaseAuth.getUser("admin-uid")).thenThrow(firebaseException);

            assertThatThrownBy(() -> adminCheckRunner.run())
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to initialize admin user");
        }

        @Test
        @DisplayName("should handle admin user with display name containing only first name")
        void shouldHandleAdminWithSingleNamePart() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRecord.getDisplayName()).thenReturn("AdminOnly");
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.empty());

            User savedUser = createUser(1L, "admin-uid", UserType.ADMIN);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            adminCheckRunner.run();

            verify(userRepository, atLeastOnce()).save(argThat(user ->
                    "AdminOnly".equals(user.getFirstName())
            ));
        }

        @Test
        @DisplayName("should use email prefix as name when display name is null")
        void shouldUseEmailPrefixWhenDisplayNameNull() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRecord.getDisplayName()).thenReturn(null);
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.empty());

            User savedUser = createUser(1L, "admin-uid", UserType.ADMIN);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            adminCheckRunner.run();

            verify(userRepository, atLeastOnce()).save(argThat(user ->
                    "admin".equals(user.getName()) &&
                            "admin".equals(user.getFirstName())
            ));
        }

        @Test
        @DisplayName("should throw exception when database save returns null")
        void shouldThrowExceptionWhenDatabaseSaveReturnsNull() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRecord.getDisplayName()).thenReturn("Admin");
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(null);

            assertThatThrownBy(() -> adminCheckRunner.run())
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to initialize admin user");
        }
    }

    // =========================================================================
    // PERMISSION ENUM TESTS
    // =========================================================================
    @Nested
    @DisplayName("Permission Enum Tests")
    class PermissionEnumTests {

        @Test
        @DisplayName("should have all expected permission values")
        void shouldHaveAllExpectedPermissionValues() {
            assertThat(Permission.values()).hasSize(4);
            assertThat(Permission.values()).contains(
                    Permission.INFLUENCER,
                    Permission.ADMIN,
                    Permission.COMPANY,
                    Permission.PENDING_ADMIN
            );
        }

        @Test
        @DisplayName("should return ordinal values")
        void shouldReturnOrdinalValues() {
            assertThat(Permission.INFLUENCER.ordinal()).isGreaterThanOrEqualTo(0);
            assertThat(Permission.ADMIN.ordinal()).isGreaterThanOrEqualTo(0);
            assertThat(Permission.COMPANY.ordinal()).isGreaterThanOrEqualTo(0);
            assertThat(Permission.PENDING_ADMIN.ordinal()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should have unique ordinals")
        void shouldHaveUniqueOrdinals() {
            Set<Integer> ordinals = new HashSet<>();
            for (Permission p : Permission.values()) {
                assertThat(ordinals.add(p.ordinal())).isTrue();
            }
        }

        @Test
        @DisplayName("should support enum comparison")
        void shouldSupportEnumComparison() {
            assertThat(Permission.ADMIN).isEqualTo(Permission.ADMIN);
            assertThat(Permission.ADMIN).isNotEqualTo(Permission.INFLUENCER);
        }

        @ParameterizedTest
        @CsvSource({
                "INFLUENCER,INFLUENCER",
                "ADMIN,ADMIN",
                "COMPANY,COMPANY",
                "PENDING_ADMIN,PENDING_ADMIN"
        })
        @DisplayName("should convert from string to enum correctly")
        void shouldConvertFromStringToEnum(String input, String expected) {
            Permission permission = Permission.valueOf(input);
            assertThat(permission.name()).isEqualTo(expected);
        }

        @Test
        @DisplayName("should convert permission to string correctly")
        void shouldConvertPermissionToStringCorrectly() {
            assertThat(Permission.ADMIN.toString()).isEqualTo("ADMIN");
            assertThat(Permission.INFLUENCER.toString()).isEqualTo("INFLUENCER");
            assertThat(Permission.COMPANY.toString()).isEqualTo("COMPANY");
            assertThat(Permission.PENDING_ADMIN.toString()).isEqualTo("PENDING_ADMIN");
        }

        @Test
        @DisplayName("should throw exception for invalid permission name")
        void shouldThrowExceptionForInvalidPermissionName() {
            assertThatThrownBy(() -> Permission.valueOf("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // HMAC SIGNATURE VALIDATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("HMAC Signature Validation Tests")
    class HmacSignatureValidationTests {

        @Test
        @DisplayName("should validate correct HMAC signature")
        void shouldValidateCorrectHmacSignature() throws Exception {
            String token = generateValidJwt("firebase-uid", "INFLUENCER", 1L);
            String validSignature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", validSignature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should reject tampered HMAC signature")
        void shouldRejectTamperedHmacSignature() throws Exception {
            String token = generateValidJwt("firebase-uid", "INFLUENCER", 1L);
            String validSignature = generateHmacSignature(token);
            String tamperedSignature = validSignature.substring(0, validSignature.length() - 5) + "XXXXX";

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", tamperedSignature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject empty HMAC signature")
        void shouldRejectEmptyHmacSignature() throws Exception {
            String token = generateValidJwt("firebase-uid", "INFLUENCER", 1L);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", "");

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should reject signature for different token")
        void shouldRejectSignatureForDifferentToken() throws Exception {
            String token1 = generateValidJwt("firebase-uid-1", "INFLUENCER", 1L);
            String token2 = generateValidJwt("firebase-uid-2", "INFLUENCER", 2L);
            String signatureForToken2 = generateHmacSignature(token2);

            Cookie sessionCookie = createCookie("session", token1);
            Cookie sigCookie = createCookie("session_sig", signatureForToken2);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    // =========================================================================
    // JWT CLAIMS EXTRACTION TESTS
    // =========================================================================
    @Nested
    @DisplayName("JWT Claims Extraction Tests")
    class JwtClaimsExtractionTests {

        @Test
        @DisplayName("should extract role from JWT claims")
        void shouldExtractRoleFromJwtClaims() throws Exception {
            String token = generateValidJwt("firebase-uid", "COMPANY", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(auth -> auth.getAuthority().equals("COMPANY"));
        }

        @Test
        @DisplayName("should use USER as default role when role claim is null")
        void shouldUseUserAsDefaultRole() throws Exception {
            SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setSubject("firebase-uid")
                    .claim("tokenVersion", 1L)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(key)
                    .compact();

            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(auth -> auth.getAuthority().equals("USER"));
        }

        @Test
        @DisplayName("should extract dbId from JWT claims")
        void shouldExtractDbIdFromJwtClaims() throws Exception {
            String token = generateValidJwt("firebase-uid", "INFLUENCER", 42L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            Claims storedClaims = (Claims) SecurityContextHolder.getContext().getAuthentication().getDetails();
            assertThat(storedClaims.get("dbId", Long.class)).isEqualTo(42L);
        }
    }

    // =========================================================================
    // REQUEST ID HANDLING TESTS
    // =========================================================================
    @Nested
    @DisplayName("Request ID Handling Tests")
    class RequestIdHandlingTests {

        @Test
        @DisplayName("should set X-Request-ID header in error response")
        void shouldSetRequestIdHeaderInErrorResponse() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setHeader(eq("X-Request-ID"), anyString());
        }
    }

    // =========================================================================
    // SECURITY CONTEXT TESTS
    // =========================================================================
    @Nested
    @DisplayName("Security Context Tests")
    class SecurityContextTests {

        @Test
        @DisplayName("should clear security context for unauthenticated requests")
        void shouldClearSecurityContextForUnauthenticatedRequests() throws Exception {
            when(request.getRequestURI()).thenReturn("/auth/login");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("should set authentication with correct principal")
        void shouldSetAuthenticationWithCorrectPrincipal() throws Exception {
            String token = generateValidJwt("firebase-uid-abc", "ADMIN", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/admin/users");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid-abc")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid-abc")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo("firebase-uid-abc");
        }

        @Test
        @DisplayName("should store claims as authentication details")
        void shouldStoreClaimsAsAuthenticationDetails() throws Exception {
            String token = generateValidJwt("firebase-uid", "INFLUENCER", 99L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
            assertThat(details).isInstanceOf(Claims.class);
        }
    }

    // =========================================================================
    // MULTIPLE AUTHORITIES TESTS
    // =========================================================================
    @Nested
    @DisplayName("Multiple Authorities Tests")
    class MultipleAuthoritiesTests {

        @Test
        @DisplayName("should add multiple authorities for partial auth with pending 2FA")
        void shouldAddMultipleAuthoritiesForPartialAuthWith2FA() throws Exception {
            SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setSubject("firebase-uid")
                    .claim("role", "ADMIN")
                    .claim("PARTIAL_AUTH", true)
                    .claim("PENDING_2FA", true)
                    .claim("tokenVersion", 1L)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(key)
                    .compact();

            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("partialSession", token);
            Cookie sigCookie = createCookie("partialSessionSig", signature);

            when(request.getRequestURI()).thenReturn("/api/twofactor/verify");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).hasSize(3);
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ADMIN"));
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("PARTIAL_AUTH"));
            assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("PENDING_2FA"));
        }
    }

    // =========================================================================
    // EDGE CASES TESTS
    // =========================================================================
    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle malformed JWT gracefully")
        void shouldHandleMalformedJwtGracefully() throws Exception {
            String malformedToken = "not.a.valid.jwt";
            String signature = generateHmacSignature(malformedToken);

            Cookie sessionCookie = createCookie("session", malformedToken);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should handle empty token value")
        void shouldHandleEmptyTokenValue() throws Exception {
            Cookie sessionCookie = createCookie("session", "");
            Cookie sigCookie = createCookie("session_sig", "");

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should handle null token version in cache gracefully")
        void shouldHandleNullTokenVersionInCacheGracefully() throws Exception {
            String token = generateValidJwt("firebase-uid", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should handle JWT without token version claim")
        void shouldHandleJwtWithoutTokenVersionClaim() throws Exception {
            SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setSubject("firebase-uid")
                    .claim("role", "INFLUENCER")
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(key)
                    .compact();

            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // =========================================================================
    // COOKIE EXTRACTION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Cookie Extraction Tests")
    class CookieExtractionTests {

        @Test
        @DisplayName("should extract session cookie correctly")
        void shouldExtractSessionCookieCorrectly() throws Exception {
            String token = generateValidJwt("firebase-uid", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie otherCookie = createCookie("other_cookie", "other_value");
            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{otherCookie, sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("firebase-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("firebase-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should prioritize full session over partial session")
        void shouldPrioritizeFullSessionOverPartialSession() throws Exception {
            String fullToken = generateValidJwt("full-session-uid", "ADMIN", 1L);
            String fullSignature = generateHmacSignature(fullToken);
            String partialToken = generateValidJwt("partial-session-uid", "PENDING_ADMIN", 2L);
            String partialSignature = generateHmacSignature(partialToken);

            Cookie fullSessionCookie = createCookie("session", fullToken);
            Cookie fullSigCookie = createCookie("session_sig", fullSignature);
            Cookie partialSessionCookie = createCookie("partialSession", partialToken);
            Cookie partialSigCookie = createCookie("partialSessionSig", partialSignature);

            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{
                    fullSessionCookie, fullSigCookie, partialSessionCookie, partialSigCookie
            });
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("full-session-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("full-session-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo("full-session-uid");
        }

        @Test
        @DisplayName("should fall back to partial session when full session missing")
        void shouldFallBackToPartialSessionWhenFullMissing() throws Exception {
            String partialToken = generateValidJwt("partial-uid", "PENDING_ADMIN", 1L);
            String partialSignature = generateHmacSignature(partialToken);

            Cookie partialSessionCookie = createCookie("partialSession", partialToken);
            Cookie partialSigCookie = createCookie("partialSessionSig", partialSignature);

            when(request.getRequestURI()).thenReturn("/api/twofactor/setup");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{partialSessionCookie, partialSigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("partial-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("partial-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo("partial-uid");
        }
    }

    // =========================================================================
    // ADMIN CHECK RUNNER - ADDITIONAL EDGE CASES
    // =========================================================================
    @Nested
    @DisplayName("AdminCheckRunner Edge Cases")
    class AdminCheckRunnerEdgeCasesTests {

        private AdminCheckRunner adminCheckRunner;

        @BeforeEach
        void setUp() {
            adminCheckRunner = new AdminCheckRunner(
                    firebaseAuth,
                    userManagementService,
                    userRepository,
                    totpFirestoreService
            );
        }

        @Test
        @DisplayName("should update account status to ACTIVE if not already")
        void shouldUpdateAccountStatusToActiveIfNotAlready() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            User existingUser = createUser(1L, "admin-uid", UserType.ADMIN);
            existingUser.setAccountStatus(AccountStatus.IN_VALIDATION);

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "ADMIN"));
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(existingUser);

            adminCheckRunner.run();

            verify(userRepository).save(argThat(user ->
                    user.getAccountStatus() == AccountStatus.ACTIVE
            ));
        }

        @Test
        @DisplayName("should not update database when no changes needed")
        void shouldNotUpdateDatabaseWhenNoChangesNeeded() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            User existingUser = createUser(1L, "admin-uid", UserType.ADMIN);
            existingUser.setAccountStatus(AccountStatus.ACTIVE);
            existingUser.setEmail("admin@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(Map.of("role", "ADMIN"));
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.of(existingUser));

            adminCheckRunner.run();

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should handle admin with full display name")
        void shouldHandleAdminWithFullDisplayName() throws Exception {
            ReflectionTestUtils.setField(adminCheckRunner, "adminFirebaseUid", "admin-uid");
            ReflectionTestUtils.setField(adminCheckRunner, "adminEmail", "admin@test.com");

            when(firebaseAuth.getUser("admin-uid")).thenReturn(userRecord);
            when(userRecord.getEmail()).thenReturn("admin@test.com");
            when(userRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRecord.getDisplayName()).thenReturn("John Smith Doe");
            when(totpFirestoreService.is2FAEnabled("admin-uid")).thenReturn(true);
            when(userRepository.findByFirebaseUserId("admin-uid")).thenReturn(Optional.empty());

            User savedUser = createUser(1L, "admin-uid", UserType.ADMIN);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            adminCheckRunner.run();

            verify(userRepository, atLeastOnce()).save(argThat(user ->
                    "John".equals(user.getFirstName()) &&
                            "Smith Doe".equals(user.getLastName())
            ));
        }
    }

    // =========================================================================
    // HTTP METHOD SPECIFIC TESTS
    // =========================================================================
    @Nested
    @DisplayName("HTTP Method Specific Tests")
    class HttpMethodSpecificTests {

        @Test
        @DisplayName("should identify GET auth/login as public")
        void shouldIdentifyGetAuthLoginAsPublic() throws Exception {
            when(request.getRequestURI()).thenReturn("/auth/login");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should identify POST auth/register as public")
        void shouldIdentifyPostAuthRegisterAsPublic() throws Exception {
            when(request.getRequestURI()).thenReturn("/auth/register");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should identify POST api/support/ticket as public")
        void shouldIdentifyPostSupportTicketAsPublic() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/ticket");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should identify GET api/support/ticket/status as public")
        void shouldIdentifyGetSupportTicketStatusAsPublic() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/support/ticket/status");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should require auth for GET api/users/me")
        void shouldRequireAuthForGetUsersMe() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/users/me");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(null);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should require auth for POST api/opportunities")
        void shouldRequireAuthForPostOpportunities() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/opportunities");
            when(request.getMethod()).thenReturn("POST");
            when(request.getCookies()).thenReturn(null);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should require auth for DELETE api/users/1")
        void shouldRequireAuthForDeleteUsers() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/users/1");
            when(request.getMethod()).thenReturn("DELETE");
            when(request.getCookies()).thenReturn(null);
            setupErrorResponseMocks();

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    // =========================================================================
    // ADDITIONAL ROLE-BASED TESTS
    // =========================================================================
    @Nested
    @DisplayName("Role-Based Authentication Tests")
    class RoleBasedAuthTests {

        @Test
        @DisplayName("should authenticate ADMIN role correctly")
        void shouldAuthenticateAdminRoleCorrectly() throws Exception {
            String token = generateValidJwt("admin-uid", "ADMIN", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/admin/dashboard");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("admin-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("admin-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("ADMIN"));
        }

        @Test
        @DisplayName("should authenticate COMPANY role correctly")
        void shouldAuthenticateCompanyRoleCorrectly() throws Exception {
            String token = generateValidJwt("company-uid", "COMPANY", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/company/profile");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("company-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("company-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("COMPANY"));
        }

        @Test
        @DisplayName("should authenticate INFLUENCER role correctly")
        void shouldAuthenticateInfluencerRoleCorrectly() throws Exception {
            String token = generateValidJwt("influencer-uid", "INFLUENCER", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/influencer/profile");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("influencer-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("influencer-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("INFLUENCER"));
        }

        @Test
        @DisplayName("should authenticate PENDING_ADMIN role correctly")
        void shouldAuthenticatePendingAdminRoleCorrectly() throws Exception {
            String token = generateValidJwt("pending-admin-uid", "PENDING_ADMIN", 1L);
            String signature = generateHmacSignature(token);

            Cookie sessionCookie = createCookie("session", token);
            Cookie sigCookie = createCookie("session_sig", signature);

            when(request.getRequestURI()).thenReturn("/api/admin/setup");
            when(request.getMethod()).thenReturn("GET");
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie, sigCookie});
            when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
            when(userCacheService.isUserActive("pending-admin-uid")).thenReturn(true);
            when(userCacheService.getTokenVersion("pending-admin-uid")).thenReturn(1L);

            jwtAuthenticationFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .anyMatch(a -> a.getAuthority().equals("PENDING_ADMIN"));
        }
    }
}
