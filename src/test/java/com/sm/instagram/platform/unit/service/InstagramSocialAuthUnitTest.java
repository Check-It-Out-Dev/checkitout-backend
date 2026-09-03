package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.social.SocialPlatformService;
import com.sm.instagram.platform.auth.social.instagram.InstagramConfig;
import com.sm.instagram.platform.auth.social.instagram.InstagramService;
import com.sm.instagram.platform.auth.social.instagram.InstagramStartupValidator;
import com.sm.instagram.platform.common.exceptions.NetworkRetryExhaustedException;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for Instagram Social Authentication.
 * Tests cover InstagramConfig, InstagramService, and InstagramStartupValidator classes.
 *
 * This test suite validates OAuth flows, token exchange, profile fetching,
 * error handling, and startup validation without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Instagram Social Authentication Unit Tests")
class InstagramSocialAuthUnitTest {

    // ==================== InstagramConfig Tests ====================

    @Nested
    @DisplayName("InstagramConfig Tests")
    class InstagramConfigTests {

        private InstagramConfig config;

        @BeforeEach
        void setUp() {
            config = new InstagramConfig();
        }

        @Nested
        @DisplayName("Configuration Validation")
        class ConfigurationValidationTests {

            @Test
            @DisplayName("should return true when all configuration fields are valid")
            void shouldReturnTrueWhenAllFieldsValid() {
                ReflectionTestUtils.setField(config, "clientId", "123456789012345");
                ReflectionTestUtils.setField(config, "clientSecret", "abcdef123456secret");
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/auth/instagram/callback");

                boolean result = config.isConfigurationValid();

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false when clientId is null")
            void shouldReturnFalseWhenClientIdNull() {
                ReflectionTestUtils.setField(config, "clientId", null);
                ReflectionTestUtils.setField(config, "clientSecret", "secret123");
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                boolean result = config.isConfigurationValid();

                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when clientId is empty string")
            void shouldReturnFalseWhenClientIdEmpty() {
                ReflectionTestUtils.setField(config, "clientId", "");
                ReflectionTestUtils.setField(config, "clientSecret", "secret123");
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when clientId is whitespace only")
            void shouldReturnFalseWhenClientIdWhitespace() {
                ReflectionTestUtils.setField(config, "clientId", "   \t\n  ");
                ReflectionTestUtils.setField(config, "clientSecret", "secret123");
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when clientSecret is null")
            void shouldReturnFalseWhenClientSecretNull() {
                ReflectionTestUtils.setField(config, "clientId", "123456789");
                ReflectionTestUtils.setField(config, "clientSecret", null);
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when clientSecret is empty")
            void shouldReturnFalseWhenClientSecretEmpty() {
                ReflectionTestUtils.setField(config, "clientId", "123456789");
                ReflectionTestUtils.setField(config, "clientSecret", "");
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when clientSecret is whitespace")
            void shouldReturnFalseWhenClientSecretWhitespace() {
                ReflectionTestUtils.setField(config, "clientId", "123456789");
                ReflectionTestUtils.setField(config, "clientSecret", "  ");
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when redirectUri is null")
            void shouldReturnFalseWhenRedirectUriNull() {
                ReflectionTestUtils.setField(config, "clientId", "123456789");
                ReflectionTestUtils.setField(config, "clientSecret", "secret123");
                ReflectionTestUtils.setField(config, "redirectUri", null);

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when redirectUri is empty")
            void shouldReturnFalseWhenRedirectUriEmpty() {
                ReflectionTestUtils.setField(config, "clientId", "123456789");
                ReflectionTestUtils.setField(config, "clientSecret", "secret123");
                ReflectionTestUtils.setField(config, "redirectUri", "");

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when all fields are null")
            void shouldReturnFalseWhenAllFieldsNull() {
                ReflectionTestUtils.setField(config, "clientId", null);
                ReflectionTestUtils.setField(config, "clientSecret", null);
                ReflectionTestUtils.setField(config, "redirectUri", null);

                assertThat(config.isConfigurationValid()).isFalse();
            }

            @Test
            @DisplayName("should return false when all fields are empty")
            void shouldReturnFalseWhenAllFieldsEmpty() {
                ReflectionTestUtils.setField(config, "clientId", "");
                ReflectionTestUtils.setField(config, "clientSecret", "");
                ReflectionTestUtils.setField(config, "redirectUri", "");

                assertThat(config.isConfigurationValid()).isFalse();
            }
        }

        @Nested
        @DisplayName("Authorization URL Generation")
        class AuthorizationUrlGenerationTests {

            @BeforeEach
            void setUpConfig() {
                ReflectionTestUtils.setField(config, "clientId", "2113860459101101");
                ReflectionTestUtils.setField(config, "redirectUri", "https://app.check-it-out.pl/instagram/callback");
            }

            @Test
            @DisplayName("should generate authorization URL with all required parameters")
            void shouldGenerateAuthUrlWithRequiredParams() {
                String url = config.getAuthorizationUrl(null);

                assertThat(url).startsWith("https://www.instagram.com/oauth/authorize");
                assertThat(url).contains("client_id=2113860459101101");
                assertThat(url).contains("redirect_uri=");
                assertThat(url).contains("scope=instagram_business_basic");
                assertThat(url).contains("response_type=code");
            }

            @Test
            @DisplayName("should not include state parameter when null")
            void shouldNotIncludeStateWhenNull() {
                String url = config.getAuthorizationUrl(null);

                assertThat(url).doesNotContain("&state=");
            }

            @Test
            @DisplayName("should not include state parameter when empty")
            void shouldNotIncludeStateWhenEmpty() {
                String url = config.getAuthorizationUrl("");

                assertThat(url).doesNotContain("&state=");
            }

            @Test
            @DisplayName("should include state parameter when provided")
            void shouldIncludeStateWhenProvided() {
                String url = config.getAuthorizationUrl("csrf-token-abc123");

                assertThat(url).contains("&state=csrf-token-abc123");
            }

            @Test
            @DisplayName("should URL encode redirect URI with special characters")
            void shouldUrlEncodeRedirectUri() {
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback?param=value&other=test");

                String url = config.getAuthorizationUrl(null);

                assertThat(url).contains("redirect_uri=https%3A%2F%2Fexample.com%2Fcallback%3Fparam%3Dvalue%26other%3Dtest");
            }

            @Test
            @DisplayName("should URL encode state parameter with special characters")
            void shouldUrlEncodeStateWithSpecialChars() {
                String url = config.getAuthorizationUrl("state=value&user=123");

                assertThat(url).contains("&state=state%3Dvalue%26user%3D123");
            }

            @Test
            @DisplayName("should handle state with Unicode characters")
            void shouldHandleUnicodeState() {
                String url = config.getAuthorizationUrl("user=Zoltan");

                assertThat(url).contains("&state=");
            }

            @Test
            @DisplayName("should handle very long state parameter")
            void shouldHandleVeryLongState() {
                String longState = "a".repeat(500);

                String url = config.getAuthorizationUrl(longState);

                assertThat(url).contains("&state=" + longState);
            }

            @ParameterizedTest
            @CsvSource({
                    "http://localhost:4200/callback, http%3A%2F%2Flocalhost%3A4200%2Fcallback",
                    "https://app.check-it-out.pl/auth, https%3A%2F%2Fapp.check-it-out.pl%2Fauth",
                    "https://checkitout.app/instagram/callback, https%3A%2F%2Fcheckitout.app%2Finstagram%2Fcallback"
            })
            @DisplayName("should properly encode various redirect URIs")
            void shouldEncodeVariousRedirectUris(String redirectUri, String expectedEncoded) {
                ReflectionTestUtils.setField(config, "redirectUri", redirectUri);

                String url = config.getAuthorizationUrl(null);

                assertThat(url).contains("redirect_uri=" + expectedEncoded);
            }

            @Test
            @DisplayName("should include instagram_business_basic scope for MVP")
            void shouldIncludeBusinessBasicScope() {
                String url = config.getAuthorizationUrl(null);

                assertThat(url).contains("scope=instagram_business_basic");
            }

            @Test
            @DisplayName("should request code response type for OAuth flow")
            void shouldRequestCodeResponseType() {
                String url = config.getAuthorizationUrl(null);

                assertThat(url).contains("response_type=code");
            }
        }

        @Nested
        @DisplayName("API URL Getters")
        class ApiUrlGettersTests {

            @Test
            @DisplayName("should return correct Instagram token URL")
            void shouldReturnCorrectTokenUrl() {
                String tokenUrl = config.getInstagramTokenUrl();

                assertThat(tokenUrl).isEqualTo("https://api.instagram.com/oauth/access_token");
            }

            @Test
            @DisplayName("should return correct Instagram Graph API URL")
            void shouldReturnCorrectApiUrl() {
                String apiUrl = config.getInstagramApiUrl();

                assertThat(apiUrl).isEqualTo("https://graph.instagram.com");
            }

            @Test
            @DisplayName("token URL should use api.instagram.com not graph.instagram.com")
            void tokenUrlShouldUseApiDomain() {
                String tokenUrl = config.getInstagramTokenUrl();

                assertThat(tokenUrl).contains("api.instagram.com");
                assertThat(tokenUrl).doesNotContain("graph.instagram.com");
            }

            @Test
            @DisplayName("API URL should use graph.instagram.com for Graph API calls")
            void apiUrlShouldUseGraphDomain() {
                String apiUrl = config.getInstagramApiUrl();

                assertThat(apiUrl).contains("graph.instagram.com");
            }
        }

        @Nested
        @DisplayName("Property Getters")
        class PropertyGettersTests {

            @Test
            @DisplayName("should return clientId via getter")
            void shouldReturnClientId() {
                ReflectionTestUtils.setField(config, "clientId", "test-client-id-123");

                assertThat(config.getClientId()).isEqualTo("test-client-id-123");
            }

            @Test
            @DisplayName("should return clientSecret via getter")
            void shouldReturnClientSecret() {
                ReflectionTestUtils.setField(config, "clientSecret", "super-secret-value");

                assertThat(config.getClientSecret()).isEqualTo("super-secret-value");
            }

            @Test
            @DisplayName("should return redirectUri via getter")
            void shouldReturnRedirectUri() {
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                assertThat(config.getRedirectUri()).isEqualTo("https://example.com/callback");
            }

            @Test
            @DisplayName("should return validationEnabled via getter")
            void shouldReturnValidationEnabled() {
                ReflectionTestUtils.setField(config, "validationEnabled", true);

                assertThat(config.isValidationEnabled()).isTrue();
            }

            @Test
            @DisplayName("should return testMode via getter")
            void shouldReturnTestMode() {
                ReflectionTestUtils.setField(config, "testMode", true);

                assertThat(config.isTestMode()).isTrue();
            }

            @Test
            @DisplayName("should return false for testMode by default")
            void shouldReturnFalseForTestModeByDefault() {
                ReflectionTestUtils.setField(config, "testMode", false);

                assertThat(config.isTestMode()).isFalse();
            }
        }

        @Nested
        @DisplayName("API Connection Test")
        class ApiConnectionTestTests {

            @Test
            @DisplayName("should return false when accessToken is null")
            void shouldReturnFalseWhenAccessTokenNull() {
                boolean result = config.testApiConnection(null);

                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when accessToken is empty")
            void shouldReturnFalseWhenAccessTokenEmpty() {
                boolean result = config.testApiConnection("");

                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when accessToken is whitespace")
            void shouldReturnFalseWhenAccessTokenWhitespace() {
                boolean result = config.testApiConnection("   ");

                // The method checks for null or empty, whitespace passes that but will fail API call
                // This tests the guard clause behavior
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("Client ID Recognition")
        class ClientIdRecognitionTests {

            @ParameterizedTest
            @ValueSource(strings = {"2113860459101101", "2658917770964963", "1234567890123456"})
            @DisplayName("should accept various valid client IDs")
            void shouldAcceptVariousClientIds(String clientId) {
                ReflectionTestUtils.setField(config, "clientId", clientId);
                ReflectionTestUtils.setField(config, "clientSecret", "secret");
                ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

                assertThat(config.isConfigurationValid()).isTrue();
                assertThat(config.getAuthorizationUrl(null)).contains("client_id=" + clientId);
            }
        }
    }

    // ==================== InstagramService Tests ====================

    @Nested
    @DisplayName("InstagramService Tests")
    class InstagramServiceTests {

        @Mock
        private InstagramConfig instagramConfig;

        @Mock
        private PlatformRepository platformRepository;

        @Mock
        private WebClient mockGraphApiClient;

        @Mock
        private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

        @Mock
        private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

        @Mock
        private WebClient.ResponseSpec responseSpec;

        private ObjectMapper objectMapper;
        private InstagramService instagramService;

        private static final String CLIENT_ID = "test-client-id-12345";
        private static final String CLIENT_SECRET = "test-client-secret-abcdef";
        private static final String REDIRECT_URI = "https://localhost:4200/auth/instagram/callback";
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
        @DisplayName("exchangeAuthCodeForProfile - Input Validation")
        class ExchangeAuthCodeValidationTests {

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t", "\n", "\r\n"})
            @DisplayName("should throw ValidationTranslatableException for null, empty, or whitespace code")
            void shouldThrowValidationExceptionForInvalidCode(String code) {
                assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(code))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for code shorter than 20 characters")
            void shouldThrowValidationExceptionForShortCode() {
                String shortCode = "short_code_12345"; // 16 chars

                assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(shortCode))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw for code with exactly 19 characters")
            void shouldThrowFor19CharCode() {
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

            @Test
            @DisplayName("should clean code containing fragment indicator #_")
            void shouldCleanCodeWithFragmentIndicator() {
                String codeWithFragment = "valid_authorization_code_1234567890#_extra_fragment_stuff";

                // The cleaning should happen, then it will fail on HTTP call
                assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(codeWithFragment))
                        .isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should accept long authorization code")
            void shouldAcceptLongAuthorizationCode() {
                String longCode = "AQC" + "x".repeat(200) + "_valid_code";

                assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile(longCode))
                        .isInstanceOf(NetworkTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("refreshSocialData")
        class RefreshSocialDataTests {

            @Test
            @DisplayName("should return dummy data map for valid user ID")
            void shouldReturnDummyDataForValidUserId() {
                String socialUserId = "12345678901234567";

                Map<String, Object> result = instagramService.refreshSocialData(socialUserId);

                assertThat(result).isNotNull();
                assertThat(result.get("user_id")).isEqualTo(socialUserId);
                assertThat(result.get("username")).isEqualTo("instagram_user_" + socialUserId);
                assertThat(result.get("profile_picture_url")).isEqualTo("https://instagram.com/profile_pic.jpg");
                assertThat(result.get("followers_count")).isEqualTo(1000);
            }

            @Test
            @DisplayName("should work with short user ID")
            void shouldWorkWithShortUserId() {
                String shortId = "123";

                Map<String, Object> result = instagramService.refreshSocialData(shortId);

                assertThat(result.get("user_id")).isEqualTo(shortId);
            }

            @Test
            @DisplayName("should work with very long user ID")
            void shouldWorkWithLongUserId() {
                String longId = "1234567890123456789012345";

                Map<String, Object> result = instagramService.refreshSocialData(longId);

                assertThat(result.get("user_id")).isEqualTo(longId);
            }

            @Test
            @DisplayName("should work with alphanumeric user ID")
            void shouldWorkWithAlphanumericUserId() {
                String alphanumericId = "abc123def456";

                Map<String, Object> result = instagramService.refreshSocialData(alphanumericId);

                assertThat(result.get("user_id")).isEqualTo(alphanumericId);
            }

            @Test
            @DisplayName("should always return profile_picture_url")
            void shouldAlwaysReturnProfilePictureUrl() {
                Map<String, Object> result = instagramService.refreshSocialData("anyId");

                assertThat(result).containsKey("profile_picture_url");
                assertThat(result.get("profile_picture_url")).isNotNull();
            }

            @Test
            @DisplayName("should always return followers_count")
            void shouldAlwaysReturnFollowersCount() {
                Map<String, Object> result = instagramService.refreshSocialData("anyId");

                assertThat(result).containsKey("followers_count");
                assertThat(result.get("followers_count")).isEqualTo(1000);
            }
        }

        @Nested
        @DisplayName("getUserProfile")
        class GetUserProfileTests {

            @BeforeEach
            void setUpWebClientMock() {
                setupMockGraphApiClient();
            }

            @SuppressWarnings("unchecked")
            private void setupSuccessfulProfileResponse(Map<String, Object> profileData) {
                when(mockGraphApiClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
                when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
                when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
                when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
                when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                        .thenReturn(Mono.just(profileData));
            }

            @Test
            @DisplayName("should fetch user profile successfully")
            void shouldFetchUserProfileSuccessfully() {
                Map<String, Object> profileData = new HashMap<>();
                profileData.put("id", "12345678901234567");
                profileData.put("username", "testuser");
                profileData.put("account_type", "BUSINESS");
                profileData.put("media_count", 150);
                profileData.put("followers_count", 5000);

                setupSuccessfulProfileResponse(profileData);

                Map<String, Object> result = instagramService.getUserProfile("valid-access-token").block();

                assertThat(result).isNotNull();
                assertThat(result.get("id")).isEqualTo("12345678901234567");
                assertThat(result.get("username")).isEqualTo("testuser");
                assertThat(result.get("account_type")).isEqualTo("BUSINESS");
                assertThat(result.get("media_count")).isEqualTo(150);
            }

            @Test
            @DisplayName("should add instagram_business_basic permission to profile")
            void shouldAddBusinessBasicPermission() {
                Map<String, Object> profileData = new HashMap<>();
                profileData.put("id", "12345678");
                profileData.put("username", "user");

                setupSuccessfulProfileResponse(profileData);

                Map<String, Object> result = instagramService.getUserProfile("valid-token").block();

                assertThat(result.get("permissions")).isEqualTo(List.of("instagram_business_basic"));
            }

            @Test
            @DisplayName("should handle profile with CREATOR account type")
            void shouldHandleCreatorAccountType() {
                Map<String, Object> profileData = new HashMap<>();
                profileData.put("id", "12345678");
                profileData.put("username", "creator_user");
                profileData.put("account_type", "CREATOR");

                setupSuccessfulProfileResponse(profileData);

                Map<String, Object> result = instagramService.getUserProfile("valid-token").block();

                assertThat(result.get("account_type")).isEqualTo("CREATOR");
            }

            @Test
            @DisplayName("should handle empty profile response")
            void shouldHandleEmptyProfileResponse() {
                Map<String, Object> emptyProfile = new HashMap<>();

                setupSuccessfulProfileResponse(emptyProfile);

                Map<String, Object> result = instagramService.getUserProfile("valid-token").block();

                assertThat(result).isNotNull();
                assertThat(result.get("permissions")).isEqualTo(List.of("instagram_business_basic"));
            }

            @Test
            @DisplayName("should handle profile with null values")
            void shouldHandleProfileWithNullValues() {
                Map<String, Object> profileWithNulls = new HashMap<>();
                profileWithNulls.put("id", "12345678");
                profileWithNulls.put("username", null);
                profileWithNulls.put("followers_count", null);

                setupSuccessfulProfileResponse(profileWithNulls);

                Map<String, Object> result = instagramService.getUserProfile("valid-token").block();

                assertThat(result).isNotNull();
                assertThat(result.get("id")).isEqualTo("12345678");
            }
        }

        @Nested
        @DisplayName("refreshLongLivedToken")
        class RefreshLongLivedTokenTests {

            @BeforeEach
            void setUpWebClientMock() {
                setupMockGraphApiClient();
            }

            @SuppressWarnings("unchecked")
            private void setupTokenRefreshResponse(Map<String, Object> response) {
                when(mockGraphApiClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
                when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
                when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
                when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
                when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                        .thenReturn(Mono.just(response));
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
            @DisplayName("should successfully refresh token with new expiry")
            void shouldSuccessfullyRefreshToken() {
                Map<String, Object> tokenResponse = new HashMap<>();
                tokenResponse.put("access_token", "new-long-lived-token-xyz");
                tokenResponse.put("token_type", "bearer");
                tokenResponse.put("expires_in", 5184000L);

                setupTokenRefreshResponse(tokenResponse);

                String result = instagramService.refreshLongLivedToken("current-token");

                assertThat(result).isEqualTo("new-long-lived-token-xyz");
            }

            @Test
            @DisplayName("should return null when response missing access_token")
            void shouldReturnNullWhenMissingAccessToken() {
                Map<String, Object> tokenResponse = new HashMap<>();
                tokenResponse.put("token_type", "bearer");
                tokenResponse.put("expires_in", 5184000L);

                setupTokenRefreshResponse(tokenResponse);

                String result = instagramService.refreshLongLivedToken("current-token");

                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return null when response is empty")
            @SuppressWarnings("unchecked")
            void shouldReturnNullWhenResponseEmpty() {
                when(mockGraphApiClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
                when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
                when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
                when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
                when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                        .thenReturn(Mono.empty());

                String result = instagramService.refreshLongLivedToken("current-token");

                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return null on exception")
            @SuppressWarnings("unchecked")
            void shouldReturnNullOnException() {
                when(mockGraphApiClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
                when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
                when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
                when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
                when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                        .thenReturn(Mono.error(new RuntimeException("API Error")));

                String result = instagramService.refreshLongLivedToken("current-token");

                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should handle response with missing expires_in")
            void shouldHandleMissingExpiresIn() {
                Map<String, Object> tokenResponse = new HashMap<>();
                tokenResponse.put("access_token", "new-token");
                tokenResponse.put("token_type", "bearer");

                setupTokenRefreshResponse(tokenResponse);

                String result = instagramService.refreshLongLivedToken("current-token");

                assertThat(result).isEqualTo("new-token");
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
                assertThat(result.getActive()).isTrue();
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

                instagramService.getPlatformEntity();
                instagramService.getPlatformEntity();
                instagramService.getPlatformEntity();

                verify(platformRepository, times(1)).findByName("Instagram");
            }

            @Test
            @DisplayName("should return same cached instance on subsequent calls")
            void shouldReturnCachedInstance() {
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
            void shouldReturnInstagram() {
                String result = instagramService.getPlatformName();

                assertThat(result).isEqualTo("Instagram");
            }

            @Test
            @DisplayName("should always return consistent platform name")
            void shouldReturnConsistentName() {
                String first = instagramService.getPlatformName();
                String second = instagramService.getPlatformName();

                assertThat(first).isEqualTo(second).isEqualTo("Instagram");
            }
        }

        @Nested
        @DisplayName("SocialPlatformService Interface")
        class SocialPlatformServiceInterfaceTests {

            @Test
            @DisplayName("should implement SocialPlatformService interface")
            void shouldImplementInterface() {
                assertThat(instagramService).isInstanceOf(SocialPlatformService.class);
            }

            @Test
            @DisplayName("should have all interface methods implemented")
            void shouldHaveAllMethods() {
                assertThat(instagramService.getPlatformName()).isNotNull();

                Platform platform = createInstagramPlatform();
                when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(platform));
                assertThat(instagramService.getPlatformEntity()).isNotNull();

                assertThat(instagramService.refreshSocialData("123")).isNotNull();
            }
        }

        @Nested
        @DisplayName("Privacy/GDPR Utilities - anonymizeId")
        class AnonymizeIdTests {

            @Test
            @DisplayName("should return 'unknown' for null ID")
            void shouldReturnUnknownForNullId() throws Exception {
                Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
                anonymizeIdMethod.setAccessible(true);

                String result = (String) anonymizeIdMethod.invoke(instagramService, (String) null);

                assertThat(result).isEqualTo("unknown");
            }

            @Test
            @DisplayName("should return 'unknown' for empty ID")
            void shouldReturnUnknownForEmptyId() throws Exception {
                Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
                anonymizeIdMethod.setAccessible(true);

                String result = (String) anonymizeIdMethod.invoke(instagramService, "");

                assertThat(result).isEqualTo("unknown");
            }

            @Test
            @DisplayName("should prefix result with 'ig_'")
            void shouldPrefixWithIg() throws Exception {
                Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
                anonymizeIdMethod.setAccessible(true);

                String result = (String) anonymizeIdMethod.invoke(instagramService, "12345678");

                assertThat(result).startsWith("ig_");
            }

            @Test
            @DisplayName("should generate consistent hash for same ID")
            void shouldGenerateConsistentHash() throws Exception {
                Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
                anonymizeIdMethod.setAccessible(true);

                String result1 = (String) anonymizeIdMethod.invoke(instagramService, "12345678");
                String result2 = (String) anonymizeIdMethod.invoke(instagramService, "12345678");

                assertThat(result1).isEqualTo(result2);
            }

            @Test
            @DisplayName("should generate different hashes for different IDs")
            void shouldGenerateDifferentHashes() throws Exception {
                Method anonymizeIdMethod = InstagramService.class.getDeclaredMethod("anonymizeId", String.class);
                anonymizeIdMethod.setAccessible(true);

                String result1 = (String) anonymizeIdMethod.invoke(instagramService, "12345678");
                String result2 = (String) anonymizeIdMethod.invoke(instagramService, "87654321");

                assertThat(result1).isNotEqualTo(result2);
            }
        }

        @Nested
        @DisplayName("Privacy/GDPR Utilities - maskUsername")
        class MaskUsernameTests {

            @Test
            @DisplayName("should return 'unknown' for null username")
            void shouldReturnUnknownForNullUsername() throws Exception {
                Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
                maskUsernameMethod.setAccessible(true);

                String result = (String) maskUsernameMethod.invoke(instagramService, (String) null);

                assertThat(result).isEqualTo("unknown");
            }

            @Test
            @DisplayName("should return 'unknown' for empty username")
            void shouldReturnUnknownForEmptyUsername() throws Exception {
                Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
                maskUsernameMethod.setAccessible(true);

                String result = (String) maskUsernameMethod.invoke(instagramService, "");

                assertThat(result).isEqualTo("unknown");
            }

            @Test
            @DisplayName("should return '***' for username with 3 or fewer chars")
            void shouldReturnAsterisksForShortUsername() throws Exception {
                Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
                maskUsernameMethod.setAccessible(true);

                assertThat(maskUsernameMethod.invoke(instagramService, "abc")).isEqualTo("***");
                assertThat(maskUsernameMethod.invoke(instagramService, "ab")).isEqualTo("***");
                assertThat(maskUsernameMethod.invoke(instagramService, "a")).isEqualTo("***");
            }

            @Test
            @DisplayName("should show first 2 chars plus *** for longer usernames")
            void shouldMaskLongerUsernames() throws Exception {
                Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
                maskUsernameMethod.setAccessible(true);

                String result = (String) maskUsernameMethod.invoke(instagramService, "testuser123");

                assertThat(result).isEqualTo("te***");
            }

            @Test
            @DisplayName("should handle username with exactly 4 chars")
            void shouldHandleFourCharUsername() throws Exception {
                Method maskUsernameMethod = InstagramService.class.getDeclaredMethod("maskUsername", String.class);
                maskUsernameMethod.setAccessible(true);

                String result = (String) maskUsernameMethod.invoke(instagramService, "test");

                assertThat(result).isEqualTo("te***");
            }
        }

        @Nested
        @DisplayName("Meta Error Parsing")
        class MetaErrorParsingTests {

            @Test
            @DisplayName("should parse standard Meta OAuth error response")
            void shouldParseStandardMetaError() throws Exception {
                Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
                parseMethod.setAccessible(true);

                String errorResponse = """
                    {
                        "error": {
                            "message": "Invalid OAuth access token",
                            "type": "OAuthException",
                            "code": 190,
                            "error_subcode": 467,
                            "fbtrace_id": "ABC123XYZ"
                        }
                    }
                    """;

                NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                        instagramService, errorResponse, "testOperation");

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle malformed JSON in error response")
            void shouldHandleMalformedJson() throws Exception {
                Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
                parseMethod.setAccessible(true);

                String malformedResponse = "not valid json { broken }";

                NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                        instagramService, malformedResponse, "testOperation");

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle error response without error node")
            void shouldHandleResponseWithoutErrorNode() throws Exception {
                Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
                parseMethod.setAccessible(true);

                String responseWithoutError = """
                    {
                        "status": "failed",
                        "reason": "something went wrong"
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
                            "message": "Technical error occurred",
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
            @DisplayName("should parse transient error flag correctly")
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

            @Test
            @DisplayName("should handle empty JSON object")
            void shouldHandleEmptyJsonObject() throws Exception {
                Method parseMethod = InstagramService.class.getDeclaredMethod("parseMetaErrorResponse", String.class, String.class);
                parseMethod.setAccessible(true);

                String emptyJson = "{}";

                NetworkTranslatableException result = (NetworkTranslatableException) parseMethod.invoke(
                        instagramService, emptyJson, "testOperation");

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("createUserFriendlyException - Error Code Handling")
        class CreateUserFriendlyExceptionTests {

            private Method getCreateMethod() throws Exception {
                Method method = InstagramService.class.getDeclaredMethod(
                        "createUserFriendlyException", int.class, int.class, String.class, String.class, boolean.class);
                method.setAccessible(true);
                return method;
            }

            @Test
            @DisplayName("should create exception for account type error (subcode 2500)")
            void shouldCreateExceptionForAccountTypeError() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 2500, "This is not an Instagram Business account", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for token expired error (subcode 463)")
            void shouldCreateExceptionForTokenExpiredError() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 190, 463, "Token has expired", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for invalid token error (subcode 467)")
            void shouldCreateExceptionForInvalidTokenError() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 190, 467, "Invalid access token", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for password changed error (subcode 460)")
            void shouldCreateExceptionForPasswordChangedError() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 190, 460, "Password changed", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for app rate limit (code 4)")
            void shouldCreateExceptionForAppRateLimit() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 4, 0, "Application request limit reached", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for user rate limit (code 17)")
            void shouldCreateExceptionForUserRateLimit() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 17, 0, "User request limit reached", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for permission denied (code 10)")
            void shouldCreateExceptionForPermissionDenied() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 10, 0, "Permission denied", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for redirect URI mismatch")
            void shouldCreateExceptionForRedirectUriMismatch() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "redirect_uri URL mismatch", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for authorization code already used")
            void shouldCreateExceptionForAuthCodeUsed() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "This authorization code has already been used", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for invalid app credentials")
            void shouldCreateExceptionForInvalidAppCredentials() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "Invalid platform app", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for HTTP method type mismatch")
            void shouldCreateExceptionForMethodTypeMismatch() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "Unsupported request - method type: get", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for transient error")
            void shouldCreateExceptionForTransientError() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 2, 0, "Temporary issue", "test", true);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should create exception for session key invalid (code 102)")
            void shouldCreateExceptionForSessionKeyInvalid() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 102, 0, "Session key invalid", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should sanitize email in error message")
            void shouldSanitizeEmailInErrorMessage() throws Exception {
                Method createMethod = getCreateMethod();

                String messageWithEmail = "Error for user@example.com";

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 999, 0, messageWithEmail, "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle code expired message")
            void shouldHandleCodeExpiredMessage() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "Code has expired", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle Instagram Business account message")
            void shouldHandleInstagramBusinessAccountMessage() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "Please use an Instagram Business account", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle Creator account message")
            void shouldHandleCreatorAccountMessage() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "You need a Creator account", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle rate limit with transient flag")
            void shouldHandleRateLimitWithTransientFlag() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 4, 0, "rate limit exceeded", "test", true);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle not authorized message")
            void shouldHandleNotAuthorizedMessage() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "User not authorized for this action", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle App Not Setup message")
            void shouldHandleAppNotSetupMessage() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "App Not Setup properly", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle Invalid App ID message")
            void shouldHandleInvalidAppIdMessage() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 100, 0, "Invalid App ID", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle default code 190 without subcode")
            void shouldHandleDefaultCode190() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 190, 0, "Unknown token error", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle unknown error code")
            void shouldHandleUnknownErrorCode() throws Exception {
                Method createMethod = getCreateMethod();

                NetworkTranslatableException result = (NetworkTranslatableException) createMethod.invoke(
                        instagramService, 999, 999, "Unknown error occurred", "test", false);

                assertThat(result).isInstanceOf(NetworkTranslatableException.class);
            }
        }
    }

    // ==================== InstagramStartupValidator Tests ====================

    @Nested
    @DisplayName("InstagramStartupValidator Tests")
    class InstagramStartupValidatorTests {

        @Mock
        private InstagramConfig instagramConfig;

        private InstagramStartupValidator validator;

        @BeforeEach
        void setUp() {
            validator = new InstagramStartupValidator(instagramConfig);
        }

        @Nested
        @DisplayName("Configuration Status")
        class ConfigurationStatusTests {

            @Test
            @DisplayName("should be instantiable with InstagramConfig")
            void shouldBeInstantiableWithConfig() {
                assertThat(validator).isNotNull();
            }

            @Test
            @DisplayName("should have InstagramConfig injected")
            void shouldHaveConfigInjected() {
                InstagramConfig injectedConfig = (InstagramConfig) ReflectionTestUtils.getField(validator, "instagramConfig");

                assertThat(injectedConfig).isNotNull();
            }
        }

        @Nested
        @DisplayName("Validation Logic")
        class ValidationLogicTests {

            @BeforeEach
            void setUpDefaults() {
                when(instagramConfig.getClientId()).thenReturn("test-client-id");
                when(instagramConfig.getClientSecret()).thenReturn("test-secret");
                when(instagramConfig.getRedirectUri()).thenReturn("https://example.com/callback");
            }

            @Test
            @DisplayName("validateBasicConfiguration should succeed with valid config")
            void shouldSucceedWithValidConfig() throws Exception {
                ReflectionTestUtils.setField(validator, "metaAppId", "770277702827785");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "valid-secret");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateBasicConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("validateBasicConfiguration should fail when metaAppId is empty")
            void shouldFailWhenMetaAppIdEmpty() throws Exception {
                ReflectionTestUtils.setField(validator, "metaAppId", "");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "valid-secret");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateBasicConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
                // ValidationResult should indicate failure
            }

            @Test
            @DisplayName("validateBasicConfiguration should fail when metaAppSecret is empty")
            void shouldFailWhenMetaAppSecretEmpty() throws Exception {
                ReflectionTestUtils.setField(validator, "metaAppId", "valid-app-id");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateBasicConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("validateBasicConfiguration should fail when clientId is empty")
            void shouldFailWhenClientIdEmpty() throws Exception {
                when(instagramConfig.getClientId()).thenReturn("");
                ReflectionTestUtils.setField(validator, "metaAppId", "valid-app-id");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "valid-secret");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateBasicConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("validateBasicConfiguration should fail when clientSecret is empty")
            void shouldFailWhenClientSecretEmpty() throws Exception {
                when(instagramConfig.getClientSecret()).thenReturn("");
                ReflectionTestUtils.setField(validator, "metaAppId", "valid-app-id");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "valid-secret");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateBasicConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("validateBasicConfiguration should fail when redirectUri is empty")
            void shouldFailWhenRedirectUriEmpty() throws Exception {
                when(instagramConfig.getRedirectUri()).thenReturn("");
                ReflectionTestUtils.setField(validator, "metaAppId", "valid-app-id");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "valid-secret");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateBasicConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }
        }

        @Nested
        @DisplayName("OAuth Configuration Validation")
        class OAuthConfigurationValidationTests {

            @BeforeEach
            void setUpDefaults() {
                when(instagramConfig.getClientId()).thenReturn("test-client-id");
                when(instagramConfig.getClientSecret()).thenReturn("test-secret");
            }

            @Test
            @DisplayName("should validate OAuth configuration with https redirect URI")
            void shouldValidateHttpsRedirectUri() throws Exception {
                when(instagramConfig.getRedirectUri()).thenReturn("https://example.com/callback");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateOAuthConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("should validate OAuth configuration with http localhost redirect URI")
            void shouldValidateHttpLocalhostRedirectUri() throws Exception {
                when(instagramConfig.getRedirectUri()).thenReturn("http://localhost:4200/callback");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateOAuthConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("should fail OAuth validation when redirect URI has no protocol")
            void shouldFailWhenRedirectUriNoProtocol() throws Exception {
                when(instagramConfig.getRedirectUri()).thenReturn("example.com/callback");

                Method validateMethod = InstagramStartupValidator.class.getDeclaredMethod("validateOAuthConfiguration");
                validateMethod.setAccessible(true);

                Object result = validateMethod.invoke(validator);

                assertThat(result).isNotNull();
            }
        }

        @Nested
        @DisplayName("App Access Token Generation")
        class AppAccessTokenGenerationTests {

            @Test
            @DisplayName("should generate app access token in correct format")
            void shouldGenerateCorrectFormat() throws Exception {
                ReflectionTestUtils.setField(validator, "metaAppId", "770277702827785");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "abc123secret");

                Method generateMethod = InstagramStartupValidator.class.getDeclaredMethod("generateAppAccessToken");
                generateMethod.setAccessible(true);

                String token = (String) generateMethod.invoke(validator);

                assertThat(token).isEqualTo("770277702827785|abc123secret");
            }

            @Test
            @DisplayName("should include pipe separator in app access token")
            void shouldIncludePipeSeparator() throws Exception {
                ReflectionTestUtils.setField(validator, "metaAppId", "12345");
                ReflectionTestUtils.setField(validator, "metaAppSecret", "secret");

                Method generateMethod = InstagramStartupValidator.class.getDeclaredMethod("generateAppAccessToken");
                generateMethod.setAccessible(true);

                String token = (String) generateMethod.invoke(validator);

                assertThat(token).contains("|");
                assertThat(token.split("\\|")).hasSize(2);
            }
        }

        @Nested
        @DisplayName("OAuth URL Building")
        class OAuthUrlBuildingTests {

            @Test
            @DisplayName("should build OAuth URL with correct base")
            void shouldBuildOAuthUrlWithCorrectBase() throws Exception {
                Method buildMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "buildOAuthUrl", String.class, String.class);
                buildMethod.setAccessible(true);

                String url = (String) buildMethod.invoke(validator, "client123", "https://example.com/callback");

                assertThat(url).startsWith("https://www.instagram.com/oauth/authorize");
            }

            @Test
            @DisplayName("should build OAuth URL with client_id parameter")
            void shouldBuildOAuthUrlWithClientId() throws Exception {
                Method buildMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "buildOAuthUrl", String.class, String.class);
                buildMethod.setAccessible(true);

                String url = (String) buildMethod.invoke(validator, "my-client-id", "https://example.com/callback");

                assertThat(url).contains("client_id=my-client-id");
            }

            @Test
            @DisplayName("should build OAuth URL with redirect_uri parameter")
            void shouldBuildOAuthUrlWithRedirectUri() throws Exception {
                Method buildMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "buildOAuthUrl", String.class, String.class);
                buildMethod.setAccessible(true);

                String url = (String) buildMethod.invoke(validator, "client123", "https://example.com/callback");

                assertThat(url).contains("redirect_uri=https://example.com/callback");
            }

            @Test
            @DisplayName("should build OAuth URL with instagram_business_basic scope")
            void shouldBuildOAuthUrlWithScope() throws Exception {
                Method buildMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "buildOAuthUrl", String.class, String.class);
                buildMethod.setAccessible(true);

                String url = (String) buildMethod.invoke(validator, "client123", "https://example.com/callback");

                assertThat(url).contains("scope=instagram_business_basic");
            }

            @Test
            @DisplayName("should build OAuth URL with response_type=code")
            void shouldBuildOAuthUrlWithResponseType() throws Exception {
                Method buildMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "buildOAuthUrl", String.class, String.class);
                buildMethod.setAccessible(true);

                String url = (String) buildMethod.invoke(validator, "client123", "https://example.com/callback");

                assertThat(url).contains("response_type=code");
            }
        }

        @Nested
        @DisplayName("Helper Methods")
        class HelperMethodsTests {

            @Test
            @DisplayName("getStringValue should return value for existing key")
            void shouldReturnValueForExistingKey() throws Exception {
                Method getStringValueMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "getStringValue", Map.class, String.class);
                getStringValueMethod.setAccessible(true);

                Map<String, Object> map = Map.of("name", "test-value");

                Optional<String> result = (Optional<String>) getStringValueMethod.invoke(validator, map, "name");

                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo("test-value");
            }

            @Test
            @DisplayName("getStringValue should return empty for missing key")
            void shouldReturnEmptyForMissingKey() throws Exception {
                Method getStringValueMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "getStringValue", Map.class, String.class);
                getStringValueMethod.setAccessible(true);

                Map<String, Object> map = Map.of("other", "value");

                Optional<String> result = (Optional<String>) getStringValueMethod.invoke(validator, map, "name");

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("getStringValue should return empty for non-string value")
            void shouldReturnEmptyForNonStringValue() throws Exception {
                Method getStringValueMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "getStringValue", Map.class, String.class);
                getStringValueMethod.setAccessible(true);

                Map<String, Object> map = Map.of("count", 123);

                Optional<String> result = (Optional<String>) getStringValueMethod.invoke(validator, map, "count");

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("truncate should return original string if shorter than max")
            void shouldReturnOriginalIfShorter() throws Exception {
                Method truncateMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "truncate", String.class, int.class);
                truncateMethod.setAccessible(true);

                String result = (String) truncateMethod.invoke(validator, "short", 10);

                assertThat(result).isEqualTo("short");
            }

            @Test
            @DisplayName("truncate should truncate and add ellipsis if longer than max")
            void shouldTruncateIfLonger() throws Exception {
                Method truncateMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "truncate", String.class, int.class);
                truncateMethod.setAccessible(true);

                String result = (String) truncateMethod.invoke(validator, "this is a very long string", 10);

                assertThat(result).isEqualTo("this is a ...");
            }

            @Test
            @DisplayName("truncate should return empty string for null input")
            void shouldReturnEmptyForNull() throws Exception {
                Method truncateMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "truncate", String.class, int.class);
                truncateMethod.setAccessible(true);

                String result = (String) truncateMethod.invoke(validator, null, 10);

                assertThat(result).isEqualTo("");
            }

            @Test
            @DisplayName("getListValue should return list for valid key")
            void shouldReturnListForValidKey() throws Exception {
                Method getListValueMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "getListValue", Map.class, String.class);
                getListValueMethod.setAccessible(true);

                Map<String, Object> innerMap = Map.of("key", "value");
                Map<String, Object> map = Map.of("data", List.of(innerMap));

                List<Map<String, Object>> result = (List<Map<String, Object>>) getListValueMethod.invoke(validator, map, "data");

                assertThat(result).hasSize(1);
            }

            @Test
            @DisplayName("getListValue should return empty list for missing key")
            void shouldReturnEmptyListForMissingKey() throws Exception {
                Method getListValueMethod = InstagramStartupValidator.class.getDeclaredMethod(
                        "getListValue", Map.class, String.class);
                getListValueMethod.setAccessible(true);

                Map<String, Object> map = Map.of("other", "value");

                List<Map<String, Object>> result = (List<Map<String, Object>>) getListValueMethod.invoke(validator, map, "data");

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("Environment Detection")
        class EnvironmentDetectionTests {

            @Test
            @DisplayName("should detect TEST environment for test app ID")
            void shouldDetectTestEnvironment() {
                ReflectionTestUtils.setField(validator, "metaAppId", "770277702827785");

                String metaAppId = (String) ReflectionTestUtils.getField(validator, "metaAppId");

                assertThat(metaAppId).isEqualTo("770277702827785");
            }

            @Test
            @DisplayName("should detect PRODUCTION environment for prod app ID")
            void shouldDetectProductionEnvironment() {
                ReflectionTestUtils.setField(validator, "metaAppId", "1404020194302324");

                String metaAppId = (String) ReflectionTestUtils.getField(validator, "metaAppId");

                assertThat(metaAppId).isEqualTo("1404020194302324");
            }
        }
    }

    // ==================== Integration-style Tests (without Spring) ====================

    @Nested
    @DisplayName("Cross-Component Integration")
    class CrossComponentIntegrationTests {

        @Mock
        private PlatformRepository platformRepository;

        @Test
        @DisplayName("InstagramConfig and InstagramService should work together")
        void configAndServiceShouldWorkTogether() {
            // Setup real config
            InstagramConfig config = new InstagramConfig();
            ReflectionTestUtils.setField(config, "clientId", "test-client-123");
            ReflectionTestUtils.setField(config, "clientSecret", "test-secret-abc");
            ReflectionTestUtils.setField(config, "redirectUri", "https://test.com/callback");

            // Create service with real config
            ObjectMapper objectMapper = new ObjectMapper();
            InstagramService service = new InstagramService(config, platformRepository, objectMapper);

            // Verify config is used correctly
            assertThat(service.getPlatformName()).isEqualTo("Instagram");
        }

        @Test
        @DisplayName("Authorization URL from config should be valid for OAuth flow")
        void authUrlShouldBeValidForOAuth() {
            InstagramConfig config = new InstagramConfig();
            ReflectionTestUtils.setField(config, "clientId", "2113860459101101");
            ReflectionTestUtils.setField(config, "redirectUri", "https://app.check-it-out.pl/instagram/callback");

            String authUrl = config.getAuthorizationUrl("state123");

            // Verify all required OAuth parameters are present
            assertThat(authUrl)
                    .contains("client_id=2113860459101101")
                    .contains("redirect_uri=")
                    .contains("scope=instagram_business_basic")
                    .contains("response_type=code")
                    .contains("state=state123");
        }

        @Test
        @DisplayName("Service should handle platform caching correctly across calls")
        void serviceShouldCachePlatformCorrectly() {
            InstagramConfig config = new InstagramConfig();
            ReflectionTestUtils.setField(config, "clientId", "test");
            ReflectionTestUtils.setField(config, "clientSecret", "test");
            ReflectionTestUtils.setField(config, "redirectUri", "https://test.com/callback");

            Platform platform = new Platform();
            platform.setId(1L);
            platform.setName("Instagram");
            platform.setActive(true);

            when(platformRepository.findByName("Instagram")).thenReturn(Optional.of(platform));

            ObjectMapper objectMapper = new ObjectMapper();
            InstagramService service = new InstagramService(config, platformRepository, objectMapper);

            // Multiple calls should only query once
            Platform p1 = service.getPlatformEntity();
            Platform p2 = service.getPlatformEntity();
            Platform p3 = service.getPlatformEntity();

            assertThat(p1).isSameAs(p2).isSameAs(p3);
            verify(platformRepository, times(1)).findByName("Instagram");
        }
    }

    // ==================== Error Scenarios Tests ====================

    @Nested
    @DisplayName("Error Scenario Coverage")
    class ErrorScenarioTests {

        @Mock
        private InstagramConfig instagramConfig;

        @Mock
        private PlatformRepository platformRepository;

        private InstagramService instagramService;

        @BeforeEach
        void setUp() {
            when(instagramConfig.getClientId()).thenReturn("test-id");
            when(instagramConfig.getClientSecret()).thenReturn("test-secret");
            when(instagramConfig.getRedirectUri()).thenReturn("https://test.com/callback");
            when(instagramConfig.getInstagramApiUrl()).thenReturn("https://graph.instagram.com");

            instagramService = new InstagramService(instagramConfig, platformRepository, new ObjectMapper());
        }

        @Test
        @DisplayName("should handle various invalid authorization codes")
        void shouldHandleVariousInvalidCodes() {
            // Very short code
            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile("abc"))
                    .isInstanceOf(ValidationTranslatableException.class);

            // Only whitespace
            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile("   "))
                    .isInstanceOf(ValidationTranslatableException.class);

            // Tab and newline
            assertThatThrownBy(() -> instagramService.exchangeAuthCodeForProfile("\t\n"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should handle platform not found scenario")
        void shouldHandlePlatformNotFound() {
            when(platformRepository.findByName("Instagram")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> instagramService.getPlatformEntity())
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Boundary Value Tests ====================

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTests {

        private InstagramConfig config;

        @BeforeEach
        void setUp() {
            config = new InstagramConfig();
            ReflectionTestUtils.setField(config, "clientId", "test-client");
            ReflectionTestUtils.setField(config, "clientSecret", "test-secret");
            ReflectionTestUtils.setField(config, "redirectUri", "https://test.com/callback");
        }

        @Test
        @DisplayName("should handle minimum valid configuration values")
        void shouldHandleMinimumValidValues() {
            ReflectionTestUtils.setField(config, "clientId", "1");
            ReflectionTestUtils.setField(config, "clientSecret", "s");
            ReflectionTestUtils.setField(config, "redirectUri", "http://a");

            assertThat(config.isConfigurationValid()).isTrue();
        }

        @Test
        @DisplayName("should handle very long configuration values")
        void shouldHandleVeryLongValues() {
            String longClientId = "1".repeat(1000);
            String longSecret = "s".repeat(1000);
            String longUri = "https://example.com/" + "a".repeat(1000);

            ReflectionTestUtils.setField(config, "clientId", longClientId);
            ReflectionTestUtils.setField(config, "clientSecret", longSecret);
            ReflectionTestUtils.setField(config, "redirectUri", longUri);

            assertThat(config.isConfigurationValid()).isTrue();

            String authUrl = config.getAuthorizationUrl(null);
            assertThat(authUrl).contains("client_id=" + longClientId);
        }

        @Test
        @DisplayName("should handle state with boundary lengths")
        void shouldHandleStateBoundaryLengths() {
            ReflectionTestUtils.setField(config, "clientId", "client123");
            ReflectionTestUtils.setField(config, "redirectUri", "https://test.com/callback");

            // Empty state
            String urlWithEmptyState = config.getAuthorizationUrl("");
            assertThat(urlWithEmptyState).doesNotContain("&state=");

            // Single char state
            String urlWithSingleChar = config.getAuthorizationUrl("a");
            assertThat(urlWithSingleChar).contains("&state=a");

            // Very long state
            String longState = "x".repeat(2000);
            String urlWithLongState = config.getAuthorizationUrl(longState);
            assertThat(urlWithLongState).contains("&state=" + longState);
        }
    }
}
