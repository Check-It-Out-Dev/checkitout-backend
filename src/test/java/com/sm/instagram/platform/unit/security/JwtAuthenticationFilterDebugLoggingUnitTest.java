package com.sm.instagram.platform.unit.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.service.SessionSecurityService;
import com.sm.instagram.platform.common.authorization.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The filter's two debug blocks for 2FA endpoints, at the level each branch needs.
 *
 * <p>Which side of {@code log.isDebugEnabled()} ran used to depend on where surefire put the class:
 * logback-test.xml said DEBUG until the first Spring context booted and set INFO. The test logback
 * says INFO now, so this class raises the filter's logger itself and restores it afterwards, and
 * covers both sides on purpose.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter — debug logging on 2FA endpoints")
class JwtAuthenticationFilterDebugLoggingUnitTest {

    private static final String JWT_SECRET =
            "unit-test-jwt-secret-needs-at-least-64-bytes-for-HS512-signing-0123456789";
    private static final String HMAC_SECRET = "unit-test-cookie-hmac-secret";
    private static final String FIREBASE_UID = "unit-test-firebase-uid";
    private static final String TWO_FACTOR_URI = "/api/twofactor/status";

    @Mock private UserCacheService userCache;
    @Mock private SessionSecurityService sessionSecurityService;
    @Mock private MessageSource messageSource;

    private JwtAuthenticationFilter filter;
    private Logger filterLogger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                userCache, sessionSecurityService, messageSource,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(filter, "cookieHmacSecret", HMAC_SECRET);

        when(sessionSecurityService.validateSession(any(), any())).thenReturn(true);
        when(userCache.isUserActive(FIREBASE_UID)).thenReturn(true);
        when(userCache.getTokenVersion(FIREBASE_UID)).thenReturn(3L);

        filterLogger = (Logger) LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        previousLevel = filterLogger.getLevel();
        logs = new ListAppender<>();
        logs.start();
        filterLogger.addAppender(logs);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logs);
        filterLogger.setLevel(previousLevel);
    }

    private String jwt() {
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

    private MockHttpServletRequest sessionRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", TWO_FACTOR_URI);
        request.setRequestURI(TWO_FACTOR_URI);
        String token = jwt();
        request.setCookies(new Cookie("session", token), new Cookie("session_sig", hmacOf(token)));
        return request;
    }

    private List<String> logged() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("at DEBUG, a 2FA request logs its cookie inspection and the authorities it was given")
    void debugLogsCookiesAndAuthorities() throws Exception {
        filterLogger.setLevel(Level.DEBUG);

        filter.doFilter(sessionRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(logged())
                .anyMatch(m -> m.equals("=== AUTH DEBUG: " + TWO_FACTOR_URI + " ==="))
                .anyMatch(m -> m.equals("Missing partialSession cookie for 2FA endpoint"))
                .anyMatch(m -> m.startsWith("Authorities for " + TWO_FACTOR_URI));
    }

    @Test
    @DisplayName("at DEBUG, a 2FA request without cookies says so")
    void debugLogsMissingCookies() throws Exception {
        filterLogger.setLevel(Level.DEBUG);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", TWO_FACTOR_URI);
        request.setRequestURI(TWO_FACTOR_URI);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(logged()).contains("No cookies in request for 2FA endpoint");
    }

    @Test
    @DisplayName("at INFO, the same request authenticates and logs neither block")
    void infoLogsNothingFromTheDebugBlocks() throws Exception {
        filterLogger.setLevel(Level.INFO);

        filter.doFilter(sessionRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(logged())
                .noneMatch(m -> m.startsWith("=== AUTH DEBUG"))
                .noneMatch(m -> m.startsWith("Authorities for"));
    }
}
