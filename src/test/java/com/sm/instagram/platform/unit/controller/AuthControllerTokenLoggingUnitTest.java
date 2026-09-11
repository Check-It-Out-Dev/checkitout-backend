package com.sm.instagram.platform.unit.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sm.instagram.platform.auth.AuthController;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.auth.service.FirebaseAuthProxyService;
import com.sm.instagram.platform.auth.service.OAuthCallbackService;
import com.sm.instagram.platform.auth.service.RegistrationService;
import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.auth.dto.TokenExchangeRequest;
import com.sm.instagram.platform.auth.dto.TokenExchangeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * POST /auth/exchange-token must not write the Firebase ID token to the log.
 *
 * <p>It logged the first fifty characters of it at DEBUG. Fifty characters is the JWT header plus
 * the start of the payload rather than a usable credential, but the line had a second branch: when
 * the token was fifty characters or shorter it logged the WHOLE thing. That is precisely the
 * malformed-token case someone turns DEBUG on to look at, and a short bearer value is not
 * necessarily a useless one. CodeQL reported it as {@code java/sensitive-log}.
 *
 * <p>The replacement writes presence and length, which is what the debugging question actually was.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthController · exchange-token never logs the token")
class AuthControllerTokenLoggingUnitTest {

    @Mock private TokenExchangeService tokenExchangeService;
    @Mock private OAuthCallbackService oAuthCallbackService;
    @Mock private RegistrationService registrationService;
    @Mock private SocialAuthSessionService sessionService;
    @Mock private FirebaseAuthProxyService firebaseAuthProxyService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private PermissionUtils permissionUtils;
    @Mock private MessageSource messageSource;

    @InjectMocks private AuthController controller;

    private final HttpServletRequest servletRequest = new MockHttpServletRequest();
    private final HttpServletResponse servletResponse = new MockHttpServletResponse();

    private ch.qos.logback.classic.Logger controllerLogger;
    private ListAppender<ILoggingEvent> logs;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        controllerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthController.class);
        previousLevel = controllerLogger.getLevel();
        // DEBUG deliberately: the leak was at DEBUG, so a test at INFO would pass on the old code.
        controllerLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        controllerLogger.addAppender(logs);

        when(tokenExchangeService.exchangeToken(anyString(), anyInt(), any(), any()))
                .thenReturn(new TokenExchangeResponse());
    }

    @AfterEach
    void tearDown() {
        controllerLogger.detachAppender(logs);
        controllerLogger.setLevel(previousLevel);
    }

    private String logged() {
        return logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private TokenExchangeRequest requestWith(String idToken) {
        return TokenExchangeRequest.builder().idToken(idToken).expirationDays(5).build();
    }

    @Test
    @DisplayName("a full-length token: no part of it is written")
    void longTokenIsNotLogged() {
        String token = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImFiY2RlZjEyMzQ1Njc4OTAiLCJ0eXAiOiJKV1QifQ"
                + ".eyJzdWIiOiJ1c2VyLTEyMyIsImF1ZCI6ImNoZWNraXRvdXQiLCJleHAiOjE3ODkwMDAwMDB9"
                + ".c2lnbmF0dXJlLXRoYXQtbXVzdC1uZXZlci1iZS1sb2dnZWQ";

        controller.exchangeToken(requestWith(token), servletRequest, servletResponse);

        String written = logged();
        assertThat(written).as("something was logged; silence would pass for free").isNotEmpty();
        assertThat(written).doesNotContain(token);
        assertThat(written)
                .as("the old line cut at 50 characters, so that prefix is the tell")
                .doesNotContain(token.substring(0, 50));
    }

    @Test
    @DisplayName("a token of 50 characters or fewer: the branch that logged it whole is gone")
    void shortTokenIsNotLoggedEither() {
        String token = "short.but.still.a.bearer.value";
        assertThat(token.length()).isLessThanOrEqualTo(50);

        controller.exchangeToken(requestWith(token), servletRequest, servletResponse);

        assertThat(logged()).doesNotContain(token);
    }

    @Test
    @DisplayName("what is logged instead is presence and length")
    void logsPresenceAndLength() {
        controller.exchangeToken(requestWith("short.but.still.a.bearer.value"), servletRequest, servletResponse);

        assertThat(logged()).contains("present=true").contains("length=30");
    }
}
