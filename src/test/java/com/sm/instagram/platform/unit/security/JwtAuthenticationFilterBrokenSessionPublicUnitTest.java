package com.sm.instagram.platform.unit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.service.SessionSecurityService;
import com.sm.instagram.platform.common.authorization.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the broken-session-on-public-endpoint policy
 * (discovered 2026-06-10 via the greenfield FE Chrome sweep): a browser
 * holding an expired / forged / superseded / banned-user session cookie
 * received hard 401s on PUBLIC endpoints — including /legal/current, the
 * registration clickwrap source — so registration was dead for every
 * returning visitor whose session had died. Public and optional-auth
 * endpoints must degrade to ANONYMOUS on any session-validation failure;
 * protected endpoints keep their hard 401s.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter — broken sessions degrade to anonymous on public endpoints")
class JwtAuthenticationFilterBrokenSessionPublicUnitTest {

    private static final String JWT_SECRET =
            "unit-test-jwt-secret-needs-at-least-64-bytes-for-HS512-signing-0123456789";
    private static final String HMAC_SECRET = "unit-test-cookie-hmac-secret";
    private static final String FIREBASE_UID = "unit-test-firebase-uid";
    private static final String PUBLIC_URI = "/api/legal/current";
    private static final String PROTECTED_URI = "/api/users/me";

    @Mock private UserCacheService userCache;
    @Mock private SessionSecurityService sessionSecurityService;
    @Mock private MessageSource messageSource;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                userCache, sessionSecurityService, messageSource,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(filter, "cookieHmacSecret", HMAC_SECRET);

        when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
        when(userCache.isUserActive(FIREBASE_UID)).thenReturn(true);
        when(userCache.getTokenVersion(FIREBASE_UID)).thenReturn(1L);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenReturn("msg");

        SecurityContextHolder.clearContext();
    }

    private String jwt(Date expiration) {
        return Jwts.builder()
                .setSubject(FIREBASE_UID)
                .claim("role", "COMPANY")
                .claim("tokenVersion", 1L)
                .setIssuedAt(new Date())
                .setExpiration(expiration)
                .signWith(
                        Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS512)
                .compact();
    }

    private String validJwt() {
        return jwt(new Date(System.currentTimeMillis() + 3_600_000));
    }

    private String hmacOf(String token) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder()
                .encodeToString(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
    }

    private MockHttpServletRequest requestWithCookies(String uri, String token, String sig) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.setCookies(new Cookie("session", token), new Cookie("session_sig", sig));
        return request;
    }

    // ── the four broken-session flavors on the PUBLIC endpoint ────────────────

    @Test
    @DisplayName("forged signature on public endpoint proceeds anonymously")
    void forgedSignaturePublicProceeds() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                requestWithCookies(PUBLIC_URI, "garbage-stale-token", "bogus"), response, chain);

        assertThat(response.getStatus()).as("no 401 short-circuit").isEqualTo(200);
        assertThat(chain.getRequest()).as("chain continues to the controller").isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("request is anonymous").isNull();
    }

    @Test
    @DisplayName("expired token on public endpoint proceeds anonymously")
    void expiredTokenPublicProceeds() throws Exception {
        String expired = jwt(new Date(System.currentTimeMillis() - 60_000));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                requestWithCookies(PUBLIC_URI, expired, hmacOf(expired)), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("hijack-suspect session on public endpoint proceeds anonymously AND terminates the cookie")
    void invalidFingerprintPublicProceedsAndTerminates() throws Exception {
        when(sessionSecurityService.validateSession(any(), any())).thenReturn(false);
        String token = validJwt();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithCookies(PUBLIC_URI, token, hmacOf(token)), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        verify(sessionSecurityService).terminateSession(response);
    }

    @Test
    @DisplayName("banned/deleted user's cookie on public endpoint proceeds anonymously — registration clickwrap stays reachable")
    void inactiveUserPublicProceeds() throws Exception {
        when(userCache.isUserActive(FIREBASE_UID)).thenReturn(false);
        String token = validJwt();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithCookies(PUBLIC_URI, token, hmacOf(token)), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── protected endpoints keep the hard 401 contract ────────────────────────

    @Test
    @DisplayName("forged signature on protected endpoint still 401s")
    void forgedSignatureProtectedStill401s() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                requestWithCookies(PROTECTED_URI, "garbage-stale-token", "bogus"),
                response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).as("chain must NOT continue").isNull();
    }

    @Test
    @DisplayName("banned user's cookie on protected endpoint still 401s")
    void inactiveUserProtectedStill401s() throws Exception {
        when(userCache.isUserActive(FIREBASE_UID)).thenReturn(false);
        String token = validJwt();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithCookies(PROTECTED_URI, token, hmacOf(token)), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("valid session on public endpoint still authenticates (no anonymity downgrade)")
    void validSessionPublicStillAuthenticates() throws Exception {
        String token = validJwt();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithCookies(PUBLIC_URI, token, hmacOf(token)), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("valid sessions stay authenticated on public endpoints").isNotNull();
    }
}
