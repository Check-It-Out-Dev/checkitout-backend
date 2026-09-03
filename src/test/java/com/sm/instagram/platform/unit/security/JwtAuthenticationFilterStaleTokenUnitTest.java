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
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the stale-cookie deadlock (discovered 2026-06-10
 * driving the greenfield FE): after a tokenVersion bump, a browser still
 * holding the superseded HttpOnly session cookie received HTTP 419 on EVERY
 * endpoint — including the public session-bootstrap endpoints
 * (/auth/firebase/login, /auth/sign-out, /auth/exchange-token) whose whole job
 * is to replace or remove that cookie. If the silent refresh could not help
 * (mock users, Firebase outage, revoked token), the user was locked out with
 * no server-side recovery path.
 *
 * The fix exempts PUBLIC endpoints from the tokenVersion 419 (they never gate
 * on the token's authority); protected endpoints keep the 419 silent-refresh
 * contract.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter — stale tokenVersion vs public endpoints")
class JwtAuthenticationFilterStaleTokenUnitTest {

    private static final String JWT_SECRET =
            "unit-test-jwt-secret-needs-at-least-64-bytes-for-HS512-signing-0123456789";
    private static final String HMAC_SECRET = "unit-test-cookie-hmac-secret";
    private static final String FIREBASE_UID = "unit-test-firebase-uid";

    @Mock private UserCacheService userCache;
    @Mock private SessionSecurityService sessionSecurityService;
    @Mock private MessageSource messageSource;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        // findAndRegisterModules: the 419 error body carries a LocalDateTime.
        filter = new JwtAuthenticationFilter(
                userCache, sessionSecurityService, messageSource,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(filter, "cookieHmacSecret", HMAC_SECRET);

        // Valid session, active user — the ONLY failing check is tokenVersion.
        when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
        when(userCache.isUserActive(FIREBASE_UID)).thenReturn(true);
        // JWT carries version 3; the cache says 7 (bumped since issue).
        when(userCache.getTokenVersion(FIREBASE_UID)).thenReturn(7L);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenReturn("msg");

        SecurityContextHolder.clearContext();
    }

    private String staleJwt() {
        return Jwts.builder()
                .setSubject(FIREBASE_UID)
                .claim("role", "COMPANY")
                .claim("tokenVersion", 3L)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(
                        Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS512)
                .compact();
    }

    private String hmacOf(String token) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder()
                .encodeToString(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
    }

    private MockHttpServletRequest requestTo(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        String token = staleJwt();
        request.setCookies(
                new Cookie("session", token),
                new Cookie("session_sig", hmacOf(token)));
        return request;
    }

    @Test
    @DisplayName("protected endpoint still 419s on a stale tokenVersion (silent-refresh contract)")
    void protectedEndpointStill419s() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestTo("/api/users/me"), response, chain);

        assertThat(response.getStatus()).isEqualTo(419);
        assertThat(chain.getRequest()).as("chain must NOT continue").isNull();
    }

    @Test
    @DisplayName("public sign-out proceeds despite the stale cookie (can clear it)")
    void signOutProceeds() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestTo("/api/auth/sign-out"), response, chain);

        assertThat(response.getStatus()).as("no 419 short-circuit").isEqualTo(200);
        assertThat(chain.getRequest()).as("chain must continue to the controller").isNotNull();
    }

    @Test
    @DisplayName("public login proceeds despite the stale cookie (can replace it)")
    void loginProceeds() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestTo("/api/auth/firebase/login"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("public e2e mock-session hook proceeds despite the stale cookie")
    void mockSessionHookProceeds() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestTo("/api/test/auth/mock-session"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
