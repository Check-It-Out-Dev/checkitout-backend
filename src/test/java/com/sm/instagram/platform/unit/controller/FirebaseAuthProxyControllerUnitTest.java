package com.sm.instagram.platform.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.FirebaseAuthProxyController;
import com.sm.instagram.platform.auth.dto.*;
import com.sm.instagram.platform.auth.service.FirebaseAuthProxyService;
import com.sm.instagram.platform.auth.service.PasswordResetService;
import com.sm.instagram.platform.auth.stepup.StepUpAuthService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FirebaseAuthProxyController.
 * Pure Mockito unit tests without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Firebase Auth Proxy Controller Unit Tests")
class FirebaseAuthProxyControllerUnitTest {

    @Mock
    private FirebaseAuthProxyService firebaseAuthProxyService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private StepUpAuthService stepUpAuthService;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private HttpServletResponse servletResponse;

    @Mock
    private FirebaseToken firebaseToken;

    @Mock
    private UserRecord userRecord;

    private FirebaseAuthProxyController firebaseAuthProxyController;

    // Test constants
    private static final String FIREBASE_UID = "firebase-uid-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String JWT_SECRET = "test-jwt-secret-key-that-is-long-enough-for-256-bit";
    private static final String HMAC_SECRET = "test-hmac-secret-key";

    @BeforeEach
    void setUp() {
        firebaseAuthProxyController = new FirebaseAuthProxyController(
                firebaseAuthProxyService,
                objectMapper,
                firebaseAuth,
                passwordResetService,
                stepUpAuthService
        );

        // Set @Value fields via reflection
        ReflectionTestUtils.setField(firebaseAuthProxyController, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(firebaseAuthProxyController, "cookieHmacSecret", HMAC_SECRET);
        ReflectionTestUtils.setField(firebaseAuthProxyController, "cookieDomain", "localhost");
        ReflectionTestUtils.setField(firebaseAuthProxyController, "secureCookies", false);
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Login Tests")
    class FirebaseLoginTests {

        @Test
        @DisplayName("should login successfully for regular user")
        void shouldLoginSuccessfullyForRegularUser() throws Exception {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password("Password1!")
                    .build();

            FirebaseAuthResponse mockResponse = FirebaseAuthResponse.builder()
                    .success(true)
                    .idToken("id-token")
                    .localId(FIREBASE_UID)
                    .role("USER")
                    .build();

            when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(servletRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(servletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(firebaseAuthProxyService.login(any(), anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<FirebaseAuthResponse> response = firebaseAuthProxyController.login(
                    request, servletRequest, servletResponse);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            // Token should be removed from response and set as cookie
            assertThat(response.getBody().getIdToken()).isNull();
        }

        @Test
        @DisplayName("should login successfully for ADMIN user with 2FA required")
        void shouldLoginSuccessfullyForAdminWith2FA() throws Exception {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("admin@example.com")
                    .password("Password1!")
                    .build();

            FirebaseAuthResponse mockResponse = FirebaseAuthResponse.builder()
                    .success(true)
                    .idToken("id-token")
                    .localId(FIREBASE_UID)
                    .role("ADMIN")
                    .build();

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");

            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(servletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(firebaseAuthProxyService.login(any(), anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(claims);

            // When
            ResponseEntity<FirebaseAuthResponse> response = firebaseAuthProxyController.login(
                    request, servletRequest, servletResponse);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isRequires2FA()).isTrue();
        }

        @Test
        @DisplayName("should login successfully for COMPANY user without 2FA")
        void shouldLoginSuccessfullyForCompanyWithout2FA() throws Exception {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("company@example.com")
                    .password("Password1!")
                    .build();

            FirebaseAuthResponse mockResponse = FirebaseAuthResponse.builder()
                    .success(true)
                    .idToken("id-token")
                    .localId(FIREBASE_UID)
                    .role("COMPANY")
                    .build();

            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(servletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(firebaseAuthProxyService.login(any(), anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<FirebaseAuthResponse> response = firebaseAuthProxyController.login(
                    request, servletRequest, servletResponse);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isRequires2FA()).isFalse();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowValidationExceptionWhenLoginRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.login(null, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when email is null")
        void shouldThrowValidationExceptionWhenEmailIsNull() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(null)
                    .password("Password1!")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.login(request, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when email is empty")
        void shouldThrowValidationExceptionWhenEmailIsEmpty() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("")
                    .password("Password1!")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.login(request, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is null")
        void shouldThrowValidationExceptionWhenPasswordIsNull() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.login(request, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is empty")
        void shouldThrowValidationExceptionWhenPasswordIsEmpty() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password("  ")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.login(request, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should extract client IP from X-Forwarded-For header")
        void shouldExtractClientIpFromXForwardedFor() throws Exception {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password("Password1!")
                    .build();

            FirebaseAuthResponse mockResponse = FirebaseAuthResponse.builder()
                    .success(true)
                    .idToken("id-token")
                    .localId(FIREBASE_UID)
                    .role("USER")
                    .build();

            when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
            when(servletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(firebaseAuthProxyService.login(any(), eq("192.168.1.100"), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<FirebaseAuthResponse> response = firebaseAuthProxyController.login(
                    request, servletRequest, servletResponse);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(firebaseAuthProxyService).login(any(), eq("192.168.1.100"), anyString(), anyString());
        }

        @Test
        @DisplayName("should extract client IP from X-Real-IP header")
        void shouldExtractClientIpFromXRealIp() throws Exception {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password("Password1!")
                    .build();

            FirebaseAuthResponse mockResponse = FirebaseAuthResponse.builder()
                    .success(true)
                    .idToken("id-token")
                    .localId(FIREBASE_UID)
                    .role("USER")
                    .build();

            when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(servletRequest.getHeader("X-Real-IP")).thenReturn("10.0.0.50");
            when(servletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(firebaseAuthProxyService.login(any(), eq("10.0.0.50"), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<FirebaseAuthResponse> response = firebaseAuthProxyController.login(
                    request, servletRequest, servletResponse);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(firebaseAuthProxyService).login(any(), eq("10.0.0.50"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Register Tests")
    class FirebaseRegisterTests {

        @Test
        @DisplayName("should register successfully")
        void shouldRegisterSuccessfully() throws Exception {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password("Password1!")
                    .build();

            FirebaseAuthResponse mockResponse = FirebaseAuthResponse.builder()
                    .success(true)
                    .idToken("id-token")
                    .localId(FIREBASE_UID)
                    .registered(true)
                    .build();

            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(servletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(firebaseAuthProxyService.register(any(), anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<FirebaseAuthResponse> response = firebaseAuthProxyController.register(
                    request, servletRequest, servletResponse);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getIdToken()).isNull(); // Should be removed
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when register request is null")
        void shouldThrowValidationExceptionWhenRegisterRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.register(null, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when register email is null")
        void shouldThrowValidationExceptionWhenRegisterEmailIsNull() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(null)
                    .password("Password1!")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.register(request, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when register password is null")
        void shouldThrowValidationExceptionWhenRegisterPasswordIsNull() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.register(request, servletRequest, servletResponse))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Refresh Tests")
    class FirebaseRefreshTests {

        @Test
        @DisplayName("should refresh token successfully")
        void shouldRefreshTokenSuccessfully() throws Exception {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            FirebaseAuthResponse mockResponse = FirebaseAuthResponse.builder()
                    .idToken("new-id-token")
                    .refreshToken("new-refresh-token")
                    .expiresIn("3600")
                    .build();

            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(firebaseAuthProxyService.refreshToken(eq("valid-refresh-token"), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<FirebaseAuthResponse> response = firebaseAuthProxyController.refresh(
                    request, servletRequest);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getIdToken()).isEqualTo("new-id-token");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when refresh request is null")
        void shouldThrowValidationExceptionWhenRefreshRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.refresh(null, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when refreshToken is null")
        void shouldThrowValidationExceptionWhenRefreshTokenIsNull() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .refreshToken(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.refresh(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when refreshToken is empty")
        void shouldThrowValidationExceptionWhenRefreshTokenIsEmpty() {
            // Given
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .refreshToken("  ")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.refresh(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Change Password Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowValidationExceptionWhenChangePasswordRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.changePassword(null, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when currentPassword is null")
        void shouldThrowValidationExceptionWhenCurrentPasswordIsNull() {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword(null)
                    .newPassword("newPassword123")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.changePassword(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when currentPassword is empty")
        void shouldThrowValidationExceptionWhenCurrentPasswordIsEmpty() {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("  ")
                    .newPassword("newPassword123")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.changePassword(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when newPassword is null")
        void shouldThrowValidationExceptionWhenNewPasswordIsNull() {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("currentPassword123")
                    .newPassword(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.changePassword(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when newPassword is empty")
        void shouldThrowValidationExceptionWhenNewPasswordIsEmpty() {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("currentPassword123")
                    .newPassword("")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.changePassword(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when no cookies present")
        void shouldThrowAuthExceptionWhenNoCookiesPresent() {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("currentPassword123")
                    .newPassword("newPassword123")
                    .build();

            when(servletRequest.getCookies()).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.changePassword(request, servletRequest))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when session cookie not found")
        void shouldThrowAuthExceptionWhenSessionCookieNotFound() {
            // Given
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("currentPassword123")
                    .newPassword("newPassword123")
                    .build();

            Cookie otherCookie = new Cookie("other", "value");
            when(servletRequest.getCookies()).thenReturn(new Cookie[]{otherCookie});

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.changePassword(request, servletRequest))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Reauthenticate Tests")
    class ReauthenticateTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowValidationExceptionWhenReauthRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.reauthenticate(null, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is null")
        void shouldThrowValidationExceptionWhenReauthPasswordIsNull() {
            // Given
            ReauthRequest request = ReauthRequest.builder()
                    .password(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.reauthenticate(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is empty")
        void shouldThrowValidationExceptionWhenReauthPasswordIsEmpty() {
            // Given
            ReauthRequest request = ReauthRequest.builder()
                    .password("  ")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.reauthenticate(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when no session cookie")
        void shouldThrowAuthExceptionWhenNoSessionCookieForReauth() {
            // Given
            ReauthRequest request = ReauthRequest.builder()
                    .password("Password1!")
                    .build();

            when(servletRequest.getCookies()).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.reauthenticate(request, servletRequest))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Delete Account Tests")
    class DeleteAccountTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowValidationExceptionWhenDeleteRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.deleteAccount(null, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is null")
        void shouldThrowValidationExceptionWhenDeletePasswordIsNull() {
            // Given
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.deleteAccount(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is empty")
        void shouldThrowValidationExceptionWhenDeletePasswordIsEmpty() {
            // Given
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password("  ")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.deleteAccount(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when no session cookie for delete")
        void shouldThrowAuthExceptionWhenNoSessionCookieForDelete() {
            // Given
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password("Password1!")
                    .reason("Testing")
                    .build();

            when(servletRequest.getCookies()).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.deleteAccount(request, servletRequest))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Update Email Tests")
    class UpdateEmailTests {

        @Test
        @DisplayName("should throw ValidationTranslatableException when request is null")
        void shouldThrowValidationExceptionWhenUpdateEmailRequestIsNull() {
            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.updateEmail(null, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when newEmail is null")
        void shouldThrowValidationExceptionWhenNewEmailIsNull() {
            // Given
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail(null)
                    .password("Password1!")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.updateEmail(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when newEmail is empty")
        void shouldThrowValidationExceptionWhenNewEmailIsEmpty() {
            // Given
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("  ")
                    .password("Password1!")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.updateEmail(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is null for email update")
        void shouldThrowValidationExceptionWhenPasswordIsNullForEmailUpdate() {
            // Given
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("new@example.com")
                    .password(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.updateEmail(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when password is empty for email update")
        void shouldThrowValidationExceptionWhenPasswordIsEmptyForEmailUpdate() {
            // Given
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("new@example.com")
                    .password("  ")
                    .build();

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.updateEmail(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when no session cookie for email update")
        void shouldThrowAuthExceptionWhenNoSessionCookieForEmailUpdate() {
            // Given
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("new@example.com")
                    .password("Password1!")
                    .build();

            when(servletRequest.getCookies()).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> firebaseAuthProxyController.updateEmail(request, servletRequest))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthRequest DTO Tests")
    class FirebaseAuthRequestDtoTests {

        @Test
        @DisplayName("should create FirebaseAuthRequest with builder")
        void shouldCreateFirebaseAuthRequestWithBuilder() {
            // Given/When
            FirebaseAuthRequest request = FirebaseAuthRequest.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .refreshToken("refresh-token")
                    .token("custom-token")
                    .build();

            // Then
            assertThat(request.getEmail()).isEqualTo("test@example.com");
            assertThat(request.getPassword()).isEqualTo("Password1!");
            assertThat(request.isReturnSecureToken()).isTrue(); // Default value
            assertThat(request.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(request.getToken()).isEqualTo("custom-token");
        }

        @Test
        @DisplayName("should have returnSecureToken default to true")
        void shouldHaveReturnSecureTokenDefaultToTrue() {
            // Given/When
            FirebaseAuthRequest request = FirebaseAuthRequest.builder().build();

            // Then
            assertThat(request.isReturnSecureToken()).isTrue();
        }
    }

    @Nested
    @DisplayName("FirebaseAuthResponse DTO Tests")
    class FirebaseAuthResponseDtoTests {

        @Test
        @DisplayName("should create FirebaseAuthResponse with builder")
        void shouldCreateFirebaseAuthResponseWithBuilder() {
            // Given/When
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .idToken("id-token")
                    .refreshToken("refresh-token")
                    .expiresIn("3600")
                    .localId("local-id")
                    .email("test@example.com")
                    .emailVerified(true)
                    .displayName("Test User")
                    .registered(true)
                    .processingTime(100L)
                    .correlationId("corr-123")
                    .requires2FA(false)
                    .requires2FASetup(false)
                    .message("Success")
                    .role("USER")
                    .success(true)
                    .build();

            // Then
            assertThat(response.getIdToken()).isEqualTo("id-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getExpiresIn()).isEqualTo("3600");
            assertThat(response.getLocalId()).isEqualTo("local-id");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.isEmailVerified()).isTrue();
            assertThat(response.getDisplayName()).isEqualTo("Test User");
            assertThat(response.isRegistered()).isTrue();
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should create error response")
        void shouldCreateErrorResponse() {
            // Given/When
            FirebaseAuthResponse response = FirebaseAuthResponse.error("Error message");

            // Then
            assertThat(response.getError()).isNotNull();
            assertThat(response.getError().getMessage()).isEqualTo("Error message");
            assertThat(response.getError().getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("should report success when success flag is true")
        void shouldReportSuccessWhenSuccessFlagIsTrue() {
            // Given/When
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .success(true)
                    .build();

            // Then
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should report success when error is null and idToken is present")
        void shouldReportSuccessWhenErrorIsNullAndIdTokenPresent() {
            // Given/When
            FirebaseAuthResponse response = FirebaseAuthResponse.builder()
                    .idToken("some-token")
                    .build();

            // Then
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should report failure when error is present")
        void shouldReportFailureWhenErrorIsPresent() {
            // Given/When
            FirebaseAuthResponse response = FirebaseAuthResponse.error("Some error");

            // Then
            assertThat(response.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("AuthOperationResponse DTO Tests")
    class AuthOperationResponseDtoTests {

        @Test
        @DisplayName("should create success response")
        void shouldCreateSuccessResponse() {
            // Given/When
            AuthOperationResponse response = AuthOperationResponse.success("Operation successful");

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Operation successful");
        }

        @Test
        @DisplayName("should create success response with data")
        void shouldCreateSuccessResponseWithData() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("key", "value");

            // When
            AuthOperationResponse response = AuthOperationResponse.success("Success", data);

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Success");
            assertThat(response.getData()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should create error response")
        void shouldCreateErrorResponseForAuthOperation() {
            // Given/When
            AuthOperationResponse response = AuthOperationResponse.error("Error occurred");

            // Then
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Error occurred");
        }
    }

    @Nested
    @DisplayName("Password Change Request DTO Tests")
    class PasswordChangeRequestDtoTests {

        @Test
        @DisplayName("should create PasswordChangeRequest with builder")
        void shouldCreatePasswordChangeRequestWithBuilder() {
            // Given/When
            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("oldPassword")
                    .newPassword("newPassword")
                    .build();

            // Then
            assertThat(request.getCurrentPassword()).isEqualTo("oldPassword");
            assertThat(request.getNewPassword()).isEqualTo("newPassword");
        }
    }

    @Nested
    @DisplayName("Reauth Request DTO Tests")
    class ReauthRequestDtoTests {

        @Test
        @DisplayName("should create ReauthRequest with builder")
        void shouldCreateReauthRequestWithBuilder() {
            // Given/When
            ReauthRequest request = ReauthRequest.builder()
                    .password("Password1!")
                    .build();

            // Then
            assertThat(request.getPassword()).isEqualTo("Password1!");
        }
    }

    @Nested
    @DisplayName("Delete Account Request DTO Tests")
    class DeleteAccountRequestDtoTests {

        @Test
        @DisplayName("should create DeleteAccountRequest with builder")
        void shouldCreateDeleteAccountRequestWithBuilder() {
            // Given/When
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password("Password1!")
                    .reason("Not using the service anymore")
                    .build();

            // Then
            assertThat(request.getPassword()).isEqualTo("Password1!");
            assertThat(request.getReason()).isEqualTo("Not using the service anymore");
        }

        @Test
        @DisplayName("should allow null reason")
        void shouldAllowNullReason() {
            // Given/When
            DeleteAccountRequest request = DeleteAccountRequest.builder()
                    .password("Password1!")
                    .build();

            // Then
            assertThat(request.getPassword()).isEqualTo("Password1!");
            assertThat(request.getReason()).isNull();
        }
    }

    @Nested
    @DisplayName("Email Update Request DTO Tests")
    class EmailUpdateRequestDtoTests {

        @Test
        @DisplayName("should create EmailUpdateRequest with builder")
        void shouldCreateEmailUpdateRequestWithBuilder() {
            // Given/When
            EmailUpdateRequest request = EmailUpdateRequest.builder()
                    .newEmail("new@example.com")
                    .password("Password1!")
                    .build();

            // Then
            assertThat(request.getNewEmail()).isEqualTo("new@example.com");
            assertThat(request.getPassword()).isEqualTo("Password1!");
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Forgot Password Tests")
    class ForgotPasswordTests {

        @Test
        @DisplayName("should return 200 with valid forgot password request")
        void forgotPassword_validRequest_returns200() {
            // Given
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .email(TEST_EMAIL)
                    .build();

            ForgotPasswordResponse mockResponse = ForgotPasswordResponse.success();
            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(passwordResetService.requestPasswordReset(eq(TEST_EMAIL), anyString()))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<ForgotPasswordResponse> response =
                    firebaseAuthProxyController.forgotPassword(request, servletRequest, "en");

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessageKey()).isEqualTo("auth.forgot_password.success_message");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when request body is null")
        void forgotPassword_nullBody_throwsValidation() {
            // When/Then
            assertThatThrownBy(() ->
                    firebaseAuthProxyController.forgotPassword(null, servletRequest, "en"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when email is null")
        void forgotPassword_nullEmail_throwsValidation() {
            // Given
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .email(null)
                    .build();

            // When/Then
            assertThatThrownBy(() ->
                    firebaseAuthProxyController.forgotPassword(request, servletRequest, "en"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when email is empty")
        void forgotPassword_emptyEmail_throwsValidation() {
            // Given
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .email("")
                    .build();

            // When/Then
            assertThatThrownBy(() ->
                    firebaseAuthProxyController.forgotPassword(request, servletRequest, "en"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should truncate language header to 10 characters")
        void forgotPassword_languageSanitized_truncatesTo10Chars() {
            // Given
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .email(TEST_EMAIL)
                    .build();

            ForgotPasswordResponse mockResponse = ForgotPasswordResponse.success();
            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(passwordResetService.requestPasswordReset(eq(TEST_EMAIL), anyString()))
                    .thenReturn(mockResponse);

            String longLanguage = "en-US-extended-very-long-value";

            // When
            firebaseAuthProxyController.forgotPassword(request, servletRequest, longLanguage);

            // Then - verify the service receives a truncated language (max 10 chars)
            ArgumentCaptor<String> languageCaptor = ArgumentCaptor.forClass(String.class);
            verify(passwordResetService).requestPasswordReset(eq(TEST_EMAIL), languageCaptor.capture());
            assertThat(languageCaptor.getValue()).hasSize(10);
            assertThat(languageCaptor.getValue()).isEqualTo("en-US-exte");
        }

        @Test
        @DisplayName("should call service with correct email and sanitized language parameters")
        void forgotPassword_callsService_withCorrectParams() {
            // Given
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .email(TEST_EMAIL)
                    .build();

            ForgotPasswordResponse mockResponse = ForgotPasswordResponse.success();
            when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(passwordResetService.requestPasswordReset(eq(TEST_EMAIL), eq("pl")))
                    .thenReturn(mockResponse);

            // When
            firebaseAuthProxyController.forgotPassword(request, servletRequest, "pl");

            // Then
            verify(passwordResetService).requestPasswordReset(TEST_EMAIL, "pl");
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Apply Action Code Tests")
    class ApplyActionCodeTests {

        @Test
        @DisplayName("should return 200 with valid apply action code request")
        void applyActionCode_validRequest_returns200() {
            ApplyActionCodeRequest request = ApplyActionCodeRequest.builder()
                    .oobCode("valid-oob-code")
                    .build();

            AuthOperationResponse mockResponse = AuthOperationResponse.success(
                    "Email verified successfully", Map.of("email", "a@b.com"));
            when(firebaseAuthProxyService.applyActionCode("valid-oob-code"))
                    .thenReturn(mockResponse);

            ResponseEntity<AuthOperationResponse> response =
                    firebaseAuthProxyController.applyActionCode(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Email verified successfully");
        }

        @Test
        @DisplayName("should propagate ValidationTranslatableException from service")
        void applyActionCode_serviceThrowsValidation_propagates() {
            ApplyActionCodeRequest request = ApplyActionCodeRequest.builder()
                    .oobCode("invalid-code")
                    .build();

            when(firebaseAuthProxyService.applyActionCode("invalid-code"))
                    .thenThrow(new ValidationTranslatableException("error.auth.invalid_action_code"));

            assertThatThrownBy(() -> firebaseAuthProxyController.applyActionCode(request))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should throw NullPointerException when request body is null")
        void applyActionCode_nullBody_throwsException() {
            assertThatThrownBy(() -> firebaseAuthProxyController.applyActionCode(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Verify Reset Code Tests")
    class VerifyResetCodeTests {

        @Test
        @DisplayName("should return 200 with valid verify reset code request")
        void verifyResetCode_validRequest_returns200() {
            VerifyResetCodeRequest request = VerifyResetCodeRequest.builder()
                    .oobCode("valid-oob-code")
                    .build();

            AuthOperationResponse mockResponse = AuthOperationResponse.success(
                    "Reset code is valid", Map.of("email", "a@b.com"));
            when(firebaseAuthProxyService.verifyResetCode("valid-oob-code"))
                    .thenReturn(mockResponse);

            ResponseEntity<AuthOperationResponse> response =
                    firebaseAuthProxyController.verifyResetCode(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Reset code is valid");
        }

        @Test
        @DisplayName("should propagate ValidationTranslatableException from service")
        void verifyResetCode_serviceThrowsValidation_propagates() {
            VerifyResetCodeRequest request = VerifyResetCodeRequest.builder()
                    .oobCode("expired-code")
                    .build();

            when(firebaseAuthProxyService.verifyResetCode("expired-code"))
                    .thenThrow(new ValidationTranslatableException("error.auth.expired_action_code"));

            assertThatThrownBy(() -> firebaseAuthProxyController.verifyResetCode(request))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.expired_action_code");
        }

        @Test
        @DisplayName("should throw NullPointerException when request body is null")
        void verifyResetCode_nullBody_throwsException() {
            assertThatThrownBy(() -> firebaseAuthProxyController.verifyResetCode(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("FirebaseAuthProxyController - Confirm Password Reset Tests")
    class ConfirmPasswordResetTests {

        @Test
        @DisplayName("should return 200 with valid confirm password reset request")
        void confirmPasswordReset_validRequest_returns200() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("valid-oob-code")
                    .newPassword("NewPassword1")
                    .build();

            AuthOperationResponse mockResponse = AuthOperationResponse.success(
                    "Password has been reset successfully", Map.of("email", "a@b.com"));
            when(firebaseAuthProxyService.confirmPasswordReset("valid-oob-code", "NewPassword1"))
                    .thenReturn(mockResponse);

            ResponseEntity<AuthOperationResponse> response =
                    firebaseAuthProxyController.confirmPasswordReset(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Password has been reset successfully");
        }

        @Test
        @DisplayName("should propagate ValidationTranslatableException from service")
        void confirmPasswordReset_serviceThrowsValidation_propagates() {
            ConfirmPasswordResetRequest request = ConfirmPasswordResetRequest.builder()
                    .oobCode("invalid-code")
                    .newPassword("NewPassword1")
                    .build();

            when(firebaseAuthProxyService.confirmPasswordReset("invalid-code", "NewPassword1"))
                    .thenThrow(new ValidationTranslatableException("error.auth.invalid_action_code"));

            assertThatThrownBy(() -> firebaseAuthProxyController.confirmPasswordReset(request))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should throw NullPointerException when request body is null")
        void confirmPasswordReset_nullBody_throwsException() {
            assertThatThrownBy(() -> firebaseAuthProxyController.confirmPasswordReset(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Controller Constants Tests")
    class ControllerConstantsTests {

        @Test
        @DisplayName("should have correct USER_AGENT constant")
        void shouldHaveCorrectUserAgentConstant() {
            assertThat(FirebaseAuthProxyController.USER_AGENT).isEqualTo("User-Agent");
        }

        @Test
        @DisplayName("should have correct OPERATION constant")
        void shouldHaveCorrectOperationConstant() {
            assertThat(FirebaseAuthProxyController.OPERATION).isEqualTo("operation");
        }

        @Test
        @DisplayName("should have correct CORRELATION_ID constant")
        void shouldHaveCorrectCorrelationIdConstant() {
            assertThat(FirebaseAuthProxyController.CORRELATION_ID).isEqualTo("correlationId");
        }

        @Test
        @DisplayName("should have correct EMAIL constant")
        void shouldHaveCorrectEmailConstant() {
            assertThat(FirebaseAuthProxyController.EMAIL).isEqualTo("email");
        }

        @Test
        @DisplayName("should have correct USER_NOT_AUTHENTICATED constant")
        void shouldHaveCorrectUserNotAuthenticatedConstant() {
            assertThat(FirebaseAuthProxyController.USER_NOT_AUTHENTICATED).isEqualTo("User not authenticated");
        }
    }
}
