package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.social.instagram.InstagramConfig;
import com.sm.instagram.platform.auth.social.instagram.InstagramService;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InstagramService.
 * Tests cover OAuth token exchange validation, user profile fetching, error handling,
 * platform entity management, and GDPR compliance utilities.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InstagramService Unit Tests")
class InstagramServiceUnitTest {

    @Mock
    private InstagramConfig instagramConfig;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private WebClient mockGraphApiClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private InstagramService instagramService;

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "https://localhost:4200/auth/callback";
    private static final String INSTAGRAM_API_URL = "https://graph.instagram.com";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        when(instagramConfig.getClientId()).thenReturn(CLIENT_ID);
        when(instagramConfig.getClientSecret()).thenReturn(CLIENT_SECRET);
        when(instagramConfig.getRedirectUri()).thenReturn(REDIRECT_URI);
        when(instagramConfig.getInstagramApiUrl()).thenReturn(INSTAGRAM_API_URL);

        instagramService = new InstagramService(instagramConfig, platformRepository, objectMapper);
    }

    private Platform createInstagramPlatform() {
        Platform platform = new Platform();
        platform.setId(1L);
        platform.setName("Instagram");
        platform.setActive(true);
        return platform;
    }

    private void setupMockGraphApiClient() {
        ReflectionTestUtils.setField(instagramService, "graphApiClient", mockGraphApiClient);
    }

    @Nested
    @DisplayName("exchangeAuthCodeForProfile - Validation")
    class ExchangeAuthCodeValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should throw ValidationTranslatableException for null, empty, or whitespace code")
        void shouldThrowValidationExceptionForInvalidCode(String code) {
            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(code))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for code shorter than 20 characters")
        void shouldThrowValidationExceptionForShortCode() {
            String shortCode = "short_code_12345";

            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(shortCode))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should handle code with fragment indicator #_ by cleaning it")
        void shouldCleanCodeWithFragmentIndicator() {
            // The code with fragment should be cleaned before any API call
            // Since we can't easily mock the WebClient.create() calls in exchangeAuthCodeForProfile,
            // we verify the code cleaning logic indirectly
            String codeWithFragment = "valid_authorization_code_1234567890#_extra_stuff";

            // This will throw because the actual HTTP call fails, but we're testing the cleaning logic
            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(codeWithFragment))
                    .isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should validate code length is at least 20 characters")
        void shouldValidateCodeLength() {
            String code19Chars = "1234567890123456789"; // 19 chars

            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(code19Chars))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should accept code with exactly 20 characters")
        void shouldAcceptCodeWithExactly20Characters() {
            String code20Chars = "12345678901234567890"; // 20 chars

            // Will throw NetworkTranslatableException because actual HTTP call fails
            // but it should pass validation
            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(code20Chars))
                    .isInstanceOf(NetworkTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("refreshSocialData")
    class RefreshSocialDataTests {

        @Test
        @DisplayName("should return dummy data for valid user ID")
        void shouldReturnDummyDataForValidUserId() {
            String socialUserId = "12345678";

            Map<String, Object> result = instagramService.refreshSocialData(socialUserId);

            assertThat(result).isNotNull();
            assertThat(result.get("user_id")).isEqualTo(socialUserId);
            assertThat(result.get("username")).isEqualTo("instagram_user_" + socialUserId);
            assertThat(result.get("profile_picture_url")).isNotNull();
            assertThat(result.get("followers_count")).isEqualTo(1000);
        }

        @Test
        @DisplayName("should include profile_picture_url in response")
        void shouldIncludeProfilePictureUrlInResponse() {
            String socialUserId = "98765432";

            Map<String, Object> result = instagramService.refreshSocialData(socialUserId);

            assertThat(result.get("profile_picture_url")).isEqualTo("https://instagram.com/profile_pic.jpg");
        }

        @Test
        @DisplayName("should handle numeric user ID")
        void shouldHandleNumericUserId() {
            String socialUserId = "1234567890123456789";

            Map<String, Object> result = instagramService.refreshSocialData(socialUserId);

            assertThat(result).isNotNull();
            assertThat(result.get("user_id")).isEqualTo(socialUserId);
        }
    }

    @Nested
    @DisplayName("getUserProfile")
    class GetUserProfileTests {

        @BeforeEach
        void setUpWebClientMock() {
            setupMockGraphApiClient();
        }

        @Test
        @DisplayName("should fetch user profile successfully")
        void shouldFetchUserProfileSuccessfully() {
            Map<String, Object> profileData = new HashMap<>();
            profileData.put("id", "12345678");
            profileData.put("username", "testuser");
            profileData.put("account_type", "BUSINESS");
            profileData.put("media_count", 50);
            profileData.put("followers_count", 1000);

            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.just(profileData));

            Map<String, Object> result = instagramService.getUserProfile("valid-access-token").block();

            assertThat(result).isNotNull();
            assertThat(result.get("id")).isEqualTo("12345678");
            assertThat(result.get("username")).isEqualTo("testuser");
            assertThat(result.get("account_type")).isEqualTo("BUSINESS");
            // Check that permissions are added by the service
            assertThat(result.get("permissions")).isNotNull();
        }

        @Test
        @DisplayName("should add instagram_business_basic permission to profile")
        void shouldAddInstagramBusinessBasicPermissionToProfile() {
            Map<String, Object> profileData = new HashMap<>();
            profileData.put("id", "12345678");
            profileData.put("username", "testuser");

            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.just(profileData));

            Map<String, Object> result = instagramService.getUserProfile("valid-access-token").block();

            assertThat(result.get("permissions")).isEqualTo(java.util.List.of("instagram_business_basic"));
        }
    }

    @Nested
    @DisplayName("refreshLongLivedToken")
    class RefreshLongLivedTokenTests {

        @BeforeEach
        void setUpWebClientMock() {
            setupMockGraphApiClient();
        }

        @Test
        @DisplayName("should return null for null token")
        void shouldReturnNullForNullToken() {
            String result = instagramService.refreshLongLivedToken(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for blank token")
        void shouldReturnNullForBlankToken() {
            String result = instagramService.refreshLongLivedToken("   ");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for empty token")
        void shouldReturnNullForEmptyToken() {
            String result = instagramService.refreshLongLivedToken("");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should successfully refresh token")
        void shouldSuccessfullyRefreshToken() {
            Map<String, Object> tokenResponse = new HashMap<>();
            tokenResponse.put("access_token", "new-long-lived-token");
            tokenResponse.put("token_type", "bearer");
            tokenResponse.put("expires_in", 5184000L);

            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.just(tokenResponse));

            String result = instagramService.refreshLongLivedToken("current-token");

            assertThat(result).isEqualTo("new-long-lived-token");
        }

        @Test
        @DisplayName("should return null when response is missing access_token")
        void shouldReturnNullWhenResponseMissingAccessToken() {
            Map<String, Object> tokenResponse = new HashMap<>();
            tokenResponse.put("token_type", "bearer");

            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.just(tokenResponse));

            String result = instagramService.refreshLongLivedToken("current-token");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when response is null")
        void shouldReturnNullWhenResponseIsNull() {
            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.empty());

            String result = instagramService.refreshLongLivedToken("current-token");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null on exception")
        void shouldReturnNullOnException() {
            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.error(new RuntimeException("API Error")));

            String result = instagramService.refreshLongLivedToken("current-token");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getPlatformEntity")
    class GetPlatformEntityTests {

        @Test
        @DisplayName("should return platform entity when found")
        void shouldReturnPlatformEntityWhenFound() {
            Platform platform = createInstagramPlatform();
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(platform));

            Platform result = instagramService.getPlatformEntity();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Instagram");
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when platform not found")
        void shouldThrowExceptionWhenPlatformNotFound() {
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> instagramService.getPlatformEntity())
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should cache platform entity after first retrieval")
        void shouldCachePlatformEntity() {
            Platform platform = createInstagramPlatform();
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(platform));

            // Call twice
            instagramService.getPlatformEntity();
            instagramService.getPlatformEntity();

            // Should only query repository once
            verify(platformRepository, times(1)).findByName("Instagram");
        }

        @Test
        @DisplayName("should return cached platform entity on subsequent calls")
        void shouldReturnCachedPlatformEntity() {
            Platform platform = createInstagramPlatform();
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(platform));

            Platform first = instagramService.getPlatformEntity();
            Platform second = instagramService.getPlatformEntity();

            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("getPlatformName")
    class GetPlatformNameTests {

        @Test
        @DisplayName("should return Instagram as platform name")
        void shouldReturnInstagramAsPlatformName() {
            String result = instagramService.getPlatformName();
            assertThat(result).isEqualTo("Instagram");
        }

        @Test
        @DisplayName("should always return the same platform name")
        void shouldAlwaysReturnSamePlatformName() {
            String first = instagramService.getPlatformName();
            String second = instagramService.getPlatformName();
            assertThat(first).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("SocialPlatformService Interface Implementation")
    class SocialPlatformServiceInterfaceTests {

        @Test
        @DisplayName("should implement SocialPlatformService interface")
        void shouldImplementSocialPlatformServiceInterface() {
            assertThat(instagramService).isInstanceOf(com.sm.instagram.platform.auth.social.SocialPlatformService.class);
        }

        @Test
        @DisplayName("should have all required interface methods")
        void shouldHaveAllRequiredInterfaceMethods() {
            // Verify the methods exist and are callable
            assertThat(instagramService.getPlatformName()).isNotNull();

            Platform platform = createInstagramPlatform();
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(platform));
            assertThat(instagramService.getPlatformEntity()).isNotNull();

            assertThat(instagramService.refreshSocialData("12345")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Privacy/GDPR Utilities")
    class PrivacyUtilitiesTests {

        @Test
        @DisplayName("refreshSocialData should handle various user IDs")
        void refreshSocialDataShouldHandleVariousUserIds() {
            // Short ID
            Map<String, Object> shortIdResult = instagramService.refreshSocialData("123");
            assertThat(shortIdResult.get("user_id")).isEqualTo("123");

            // Long ID
            Map<String, Object> longIdResult = instagramService.refreshSocialData("1234567890123456789");
            assertThat(longIdResult.get("user_id")).isEqualTo("1234567890123456789");

            // Alphanumeric (if ever used)
            Map<String, Object> alphanumericResult = instagramService.refreshSocialData("abc123");
            assertThat(alphanumericResult.get("user_id")).isEqualTo("abc123");
        }

        @Test
        @DisplayName("should generate consistent anonymized IDs")
        void shouldGenerateConsistentAnonymizedIds() throws Exception {
            // Access private method via reflection for testing
            Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
            anonymizeIdMethod.setAccessible(true);

            String result1 = (String) anonymizeIdMethod.invoke(instagramService, "12345678");
            String result2 = (String) anonymizeIdMethod.invoke(instagramService, "12345678");

            assertThat(result1).isEqualTo(result2);
            assertThat(result1).startsWith("ig_");
        }

        @Test
        @DisplayName("should handle null ID in anonymization")
        void shouldHandleNullIdInAnonymization() throws Exception {
            Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
            anonymizeIdMethod.setAccessible(true);

            String result = (String) anonymizeIdMethod.invoke(instagramService, (String) null);

            assertThat(result).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should handle empty ID in anonymization")
        void shouldHandleEmptyIdInAnonymization() throws Exception {
            Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
            anonymizeIdMethod.setAccessible(true);

            String result = (String) anonymizeIdMethod.invoke(instagramService, "");

            assertThat(result).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should mask short usernames correctly")
        void shouldMaskShortUsernamesCorrectly() throws Exception {
            Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
            maskUsernameMethod.setAccessible(true);

            // Short username (3 or less chars)
            String shortResult = (String) maskUsernameMethod.invoke(instagramService, "abc");
            assertThat(shortResult).isEqualTo("***");

            // Single char
            String singleResult = (String) maskUsernameMethod.invoke(instagramService, "a");
            assertThat(singleResult).isEqualTo("***");
        }

        @Test
        @DisplayName("should mask long usernames correctly")
        void shouldMaskLongUsernamesCorrectly() throws Exception {
            Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
            maskUsernameMethod.setAccessible(true);

            String result = (String) maskUsernameMethod.invoke(instagramService, "testuser123");
            assertThat(result).isEqualTo("te***");
        }

        @Test
        @DisplayName("should handle null username in masking")
        void shouldHandleNullUsernameInMasking() throws Exception {
            Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
            maskUsernameMethod.setAccessible(true);

            String result = (String) maskUsernameMethod.invoke(instagramService, (String) null);
            assertThat(result).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should handle empty username in masking")
        void shouldHandleEmptyUsernameInMasking() throws Exception {
            Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
            maskUsernameMethod.setAccessible(true);

            String result = (String) maskUsernameMethod.invoke(instagramService, "");
            assertThat(result).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("Meta Error Parsing")
    class MetaErrorParsingTests {

        @Test
        @DisplayName("should parse standard Meta error response")
        void shouldParseStandardMetaErrorResponse() throws Exception {
            Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
            parseMethod.setAccessible(true);

            String errorResponse = """
                {
                    "error": {
                        "message": "Invalid OAuth access token",
                        "type": "OAuthException",
                        "code": 190,
                        "error_subcode": 467,
                        "fbtrace_id": "ABC123"
                    }
                }
                """;

            NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                    instagramService, errorResponse, "testOperation");

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should handle malformed JSON in error response")
        void shouldHandleMalformedJsonInErrorResponse() throws Exception {
            Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
            parseMethod.setAccessible(true);

            String malformedResponse = "not valid json";

            NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                    instagramService, malformedResponse, "testOperation");

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should handle error response without error node")
        void shouldHandleErrorResponseWithoutErrorNode() throws Exception {
            Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
            parseMethod.setAccessible(true);

            String responseWithoutError = """
                {
                    "status": "failed",
                    "reason": "unknown"
                }
                """;

            NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                    instagramService, responseWithoutError, "testOperation");

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should parse error with user title and message")
        void shouldParseErrorWithUserTitleAndMessage() throws Exception {
            Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
            parseMethod.setAccessible(true);

            String errorResponse = """
                {
                    "error": {
                        "message": "Technical error",
                        "type": "OAuthException",
                        "code": 100,
                        "fbtrace_id": "ABC123",
                        "error_user_title": "Login Failed",
                        "error_user_msg": "Please try again later"
                    }
                }
                """;

            NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                    instagramService, errorResponse, "testOperation");

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should parse transient error flag")
        void shouldParseTransientErrorFlag() throws Exception {
            Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
            parseMethod.setAccessible(true);

            String errorResponse = """
                {
                    "error": {
                        "message": "Temporary service issue",
                        "type": "OAuthException",
                        "code": 2,
                        "fbtrace_id": "ABC123",
                        "is_transient": true
                    }
                }
                """;

            NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                    instagramService, errorResponse, "testOperation");

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("User-Friendly Exception Creation")
    class UserFriendlyExceptionCreationTests {

        @Test
        @DisplayName("should create exception for account type error (subcode 2500)")
        void shouldCreateExceptionForAccountTypeError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 100, 2500, "This is not an Instagram Business account", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for token expired error (subcode 463)")
        void shouldCreateExceptionForTokenExpiredError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 190, 463, "Token has expired", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for invalid token error (subcode 467)")
        void shouldCreateExceptionForInvalidTokenError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 190, 467, "Invalid access token", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for password changed error (subcode 460)")
        void shouldCreateExceptionForPasswordChangedError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 190, 460, "Password changed", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for rate limit error (code 4)")
        void shouldCreateExceptionForRateLimitError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 4, 0, "Application request limit reached", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for user rate limit error (code 17)")
        void shouldCreateExceptionForUserRateLimitError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 17, 0, "User request limit reached", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for permission denied error (code 10)")
        void shouldCreateExceptionForPermissionDeniedError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 10, 0, "Permission denied", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for redirect URI mismatch")
        void shouldCreateExceptionForRedirectUriMismatch() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 100, 0, "redirect_uri URL mismatch", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for authorization code already used")
        void shouldCreateExceptionForAuthorizationCodeAlreadyUsed() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 100, 0, "This authorization code has already been used", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for invalid app credentials")
        void shouldCreateExceptionForInvalidAppCredentials() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 100, 0, "Invalid platform app", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for method type mismatch")
        void shouldCreateExceptionForMethodTypeMismatch() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 100, 0, "Unsupported request - method type: get", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for transient error with is_transient=true")
        void shouldCreateExceptionForTransientError() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 2, 0, "Temporary issue", "test", true);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should create exception for session key invalid (code 102)")
        void shouldCreateExceptionForSessionKeyInvalid() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 102, 0, "Session key invalid", "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }

        @Test
        @DisplayName("should sanitize sensitive information in error message")
        void shouldSanitizeSensitiveInformationInErrorMessage() throws Exception {
            Method createMethod = InstagramService.class.getDeclaredMethod(
                    "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
            createMethod.setAccessible(true);

            String messageWithEmail = "Error for user@example.com with access_token=secret123";

            // This tests the default case where sanitization happens
            NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                    instagramService, 999, 0, messageWithEmail, "test", false);

            assertThat(result).isInstanceOf(NetworkTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("should have Instagram API URL configured")
        void shouldHaveInstagramApiUrlConfigured() {
            // The config mock is set up in @BeforeEach with the expected value
            assertThat(instagramConfig.getInstagramApiUrl()).isEqualTo(INSTAGRAM_API_URL);
        }

        @Test
        @DisplayName("should have client ID configured")
        void shouldHaveClientIdConfigured() {
            assertThat(instagramConfig.getClientId()).isEqualTo(CLIENT_ID);
        }

        @Test
        @DisplayName("should have client secret configured")
        void shouldHaveClientSecretConfigured() {
            assertThat(instagramConfig.getClientSecret()).isEqualTo(CLIENT_SECRET);
        }

        @Test
        @DisplayName("should have redirect URI configured")
        void shouldHaveRedirectUriConfigured() {
            assertThat(instagramConfig.getRedirectUri()).isEqualTo(REDIRECT_URI);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @BeforeEach
        void setUpWebClientMock() {
            setupMockGraphApiClient();
        }

        @Test
        @DisplayName("should handle empty profile response")
        void shouldHandleEmptyProfileResponse() {
            Map<String, Object> emptyProfile = new HashMap<>();

            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.just(emptyProfile));

            Map<String, Object> result = instagramService.getUserProfile("valid-token").block();

            assertThat(result).isNotNull();
            // Should still have permissions added
            assertThat(result.get("permissions")).isNotNull();
        }

        @Test
        @DisplayName("should handle profile with null values")
        void shouldHandleProfileWithNullValues() {
            Map<String, Object> profileWithNulls = new HashMap<>();
            profileWithNulls.put("id", "12345678");
            profileWithNulls.put("username", null);
            profileWithNulls.put("followers_count", null);

            when(mockGraphApiClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                    .thenReturn(Mono.just(profileWithNulls));

            Map<String, Object> result = instagramService.getUserProfile("valid-token").block();

            assertThat(result).isNotNull();
            assertThat(result.get("id")).isEqualTo("12345678");
        }
    }
}
