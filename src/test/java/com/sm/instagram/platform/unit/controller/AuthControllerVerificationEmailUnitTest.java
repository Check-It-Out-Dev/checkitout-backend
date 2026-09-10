package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.auth.AuthController;
import com.sm.instagram.platform.auth.service.OAuthCallbackService;
import com.sm.instagram.platform.auth.service.RegistrationService;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.auth.service.FirebaseAuthProxyService;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * POST /auth/send-verification-email, and the caller who is not signed in.
 *
 * <p>Everything under /api/auth/** is permitAll, because sign-in, registration and forgot-password
 * have to be reachable by someone who has no session yet. This endpoint is not one of those: it
 * resends verification to the CURRENT user, so with no principal there is no user to act on.
 *
 * <p>It used to walk on with a null uid until the repository lookup answered "User not found" --
 * a 404 that reads as "no such account" when what actually happened is that nobody was signed in.
 * A nightly scenario spent two runs being read as a missing database row.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController · send-verification-email")
class AuthControllerVerificationEmailUnitTest {

    @Mock private TokenExchangeService tokenExchangeService;
    @Mock private OAuthCallbackService oAuthCallbackService;
    @Mock private RegistrationService registrationService;
    @Mock private SocialAuthSessionService sessionService;
    @Mock private FirebaseAuthProxyService firebaseAuthProxyService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private PermissionUtils permissionUtils;
    @Mock private MessageSource messageSource;

    @InjectMocks private AuthController controller;

    private final HttpServletRequest request = new MockHttpServletRequest();

    @Test
    @DisplayName("refuses a caller with no principal, and sends nothing")
    void refusesAnonymousCaller() {
        when(permissionUtils.getUserId()).thenReturn(null);

        assertThatThrownBy(() -> controller.sendVerificationEmail(request))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasMessage("error.auth.not_authenticated");

        // The distinction that matters: nothing was looked up, so nothing can answer "not found".
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    @DisplayName("sends for the signed-in user, and for that user only")
    void sendsForTheSignedInUser() {
        when(permissionUtils.getUserId()).thenReturn("E2E_COMPANY_001");
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("sent");

        var response = controller.sendVerificationEmail(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
        verify(emailVerificationService).sendVerificationEmail("E2E_COMPANY_001", "en");
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
