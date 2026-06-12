package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.social.instagram.InstagramConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InstagramConfig.
 * Tests configuration validation and OAuth URL generation.
 */
@DisplayName("InstagramConfig Unit Tests")
class InstagramConfigUnitTest {

    private InstagramConfig config;

    @BeforeEach
    void setUp() {
        config = new InstagramConfig();
    }

    // ==================== Configuration Validation Tests ====================

    @Nested
    @DisplayName("Configuration Validation")
    class ConfigurationValidationTests {

        @Test
        @DisplayName("should return true when all fields are valid")
        void shouldReturnTrueWhenAllFieldsValid() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "clientSecret", "secret123");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when clientId is null")
        void shouldReturnFalseWhenClientIdNull() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", null);
            ReflectionTestUtils.setField(config, "clientSecret", "secret123");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when clientId is empty")
        void shouldReturnFalseWhenClientIdEmpty() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "");
            ReflectionTestUtils.setField(config, "clientSecret", "secret123");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when clientId is whitespace only")
        void shouldReturnFalseWhenClientIdWhitespace() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "   ");
            ReflectionTestUtils.setField(config, "clientSecret", "secret123");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when clientSecret is null")
        void shouldReturnFalseWhenClientSecretNull() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "clientSecret", null);
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when clientSecret is empty")
        void shouldReturnFalseWhenClientSecretEmpty() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "clientSecret", "");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when redirectUri is null")
        void shouldReturnFalseWhenRedirectUriNull() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "clientSecret", "secret123");
            ReflectionTestUtils.setField(config, "redirectUri", null);

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when redirectUri is empty")
        void shouldReturnFalseWhenRedirectUriEmpty() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "clientSecret", "secret123");
            ReflectionTestUtils.setField(config, "redirectUri", "");

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when all fields are null")
        void shouldReturnFalseWhenAllFieldsNull() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", null);
            ReflectionTestUtils.setField(config, "clientSecret", null);
            ReflectionTestUtils.setField(config, "redirectUri", null);

            // When
            boolean result = config.isConfigurationValid();

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== Authorization URL Tests ====================

    @Nested
    @DisplayName("Authorization URL Generation")
    class AuthorizationUrlTests {

        @BeforeEach
        void setUpConfig() {
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");
        }

        @Test
        @DisplayName("should generate authorization URL without state")
        void shouldGenerateAuthUrlWithoutState() {
            // When
            String url = config.getAuthorizationUrl(null);

            // Then
            assertThat(url).startsWith("https://www.instagram.com/oauth/authorize");
            assertThat(url).contains("client_id=123456789");
            assertThat(url).contains("redirect_uri=");
            assertThat(url).contains("scope=instagram_business_basic");
            assertThat(url).contains("response_type=code");
            assertThat(url).doesNotContain("&state=");
        }

        @Test
        @DisplayName("should generate authorization URL with state")
        void shouldGenerateAuthUrlWithState() {
            // When
            String url = config.getAuthorizationUrl("csrf-token-123");

            // Then
            assertThat(url).startsWith("https://www.instagram.com/oauth/authorize");
            assertThat(url).contains("client_id=123456789");
            assertThat(url).contains("&state=csrf-token-123");
        }

        @Test
        @DisplayName("should URL encode redirect URI")
        void shouldUrlEncodeRedirectUri() {
            // Given
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback?param=value");

            // When
            String url = config.getAuthorizationUrl(null);

            // Then
            assertThat(url).contains("redirect_uri=https%3A%2F%2Fexample.com%2Fcallback%3Fparam%3Dvalue");
        }

        @Test
        @DisplayName("should URL encode state parameter with special characters")
        void shouldUrlEncodeStateWithSpecialChars() {
            // When
            String url = config.getAuthorizationUrl("state=value&other=param");

            // Then
            // The state should be URL encoded
            assertThat(url).contains("&state=state%3Dvalue%26other%3Dparam");
        }

        @Test
        @DisplayName("should not include state when empty")
        void shouldNotIncludeEmptyState() {
            // When
            String url = config.getAuthorizationUrl("");

            // Then
            assertThat(url).doesNotContain("&state=");
        }

        @Test
        @DisplayName("should include instagram_business_basic scope")
        void shouldIncludeBusinessBasicScope() {
            // When
            String url = config.getAuthorizationUrl(null);

            // Then
            assertThat(url).contains("scope=instagram_business_basic");
        }

        @Test
        @DisplayName("should request code response type")
        void shouldRequestCodeResponseType() {
            // When
            String url = config.getAuthorizationUrl(null);

            // Then
            assertThat(url).contains("response_type=code");
        }

        @ParameterizedTest
        @CsvSource({
                "http://localhost:4200/callback, http%3A%2F%2Flocalhost%3A4200%2Fcallback",
                "https://app.check-it-out.pl/auth, https%3A%2F%2Fapp.check-it-out.pl%2Fauth",
                "https://checkitout.app/instagram/callback, https%3A%2F%2Fcheckitout.app%2Finstagram%2Fcallback"
        })
        @DisplayName("should properly encode various redirect URIs")
        void shouldEncodeVariousRedirectUris(String redirectUri, String expectedEncoded) {
            // Given
            ReflectionTestUtils.setField(config, "redirectUri", redirectUri);

            // When
            String url = config.getAuthorizationUrl(null);

            // Then
            assertThat(url).contains("redirect_uri=" + expectedEncoded);
        }
    }

    // ==================== API URL Getters Tests ====================

    @Nested
    @DisplayName("API URL Getters")
    class ApiUrlGettersTests {

        @Test
        @DisplayName("should return Instagram token URL")
        void shouldReturnInstagramTokenUrl() {
            // When
            String tokenUrl = config.getInstagramTokenUrl();

            // Then
            assertThat(tokenUrl).isEqualTo("https://api.instagram.com/oauth/access_token");
        }

        @Test
        @DisplayName("should return Instagram API URL")
        void shouldReturnInstagramApiUrl() {
            // When
            String apiUrl = config.getInstagramApiUrl();

            // Then
            assertThat(apiUrl).isEqualTo("https://graph.instagram.com");
        }
    }

    // ==================== Getter Tests ====================

    @Nested
    @DisplayName("Getters")
    class GetterTests {

        @Test
        @DisplayName("should return clientId")
        void shouldReturnClientId() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "test-client-id");

            // When
            String result = config.getClientId();

            // Then
            assertThat(result).isEqualTo("test-client-id");
        }

        @Test
        @DisplayName("should return clientSecret")
        void shouldReturnClientSecret() {
            // Given
            ReflectionTestUtils.setField(config, "clientSecret", "test-secret");

            // When
            String result = config.getClientSecret();

            // Then
            assertThat(result).isEqualTo("test-secret");
        }

        @Test
        @DisplayName("should return redirectUri")
        void shouldReturnRedirectUri() {
            // Given
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            String result = config.getRedirectUri();

            // Then
            assertThat(result).isEqualTo("https://example.com/callback");
        }

        @Test
        @DisplayName("should return validationEnabled")
        void shouldReturnValidationEnabled() {
            // Given
            ReflectionTestUtils.setField(config, "validationEnabled", true);

            // When
            boolean result = config.isValidationEnabled();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return testMode")
        void shouldReturnTestMode() {
            // Given
            ReflectionTestUtils.setField(config, "testMode", false);

            // When
            boolean result = config.isTestMode();

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== testApiConnection Tests ====================

    @Nested
    @DisplayName("API Connection Test")
    class ApiConnectionTests {

        @Test
        @DisplayName("should return false when accessToken is null")
        void shouldReturnFalseWhenAccessTokenNull() {
            // When
            boolean result = config.testApiConnection(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when accessToken is empty")
        void shouldReturnFalseWhenAccessTokenEmpty() {
            // When
            boolean result = config.testApiConnection("");

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle state with Unicode characters")
        void shouldHandleUnicodeState() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            String url = config.getAuthorizationUrl("państwo=Polska");

            // Then
            assertThat(url).contains("&state=");
            // URL should be properly encoded
            assertThat(url).doesNotContain("państwo");
        }

        @Test
        @DisplayName("should handle very long state")
        void shouldHandleVeryLongState() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");
            String longState = "a".repeat(1000);

            // When
            String url = config.getAuthorizationUrl(longState);

            // Then
            assertThat(url).contains("&state=" + longState);
        }

        @Test
        @DisplayName("should handle localhost redirect URI")
        void shouldHandleLocalhostRedirectUri() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "redirectUri", "http://localhost:4200/instagram/callback");

            // When
            String url = config.getAuthorizationUrl(null);

            // Then
            assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A4200%2Finstagram%2Fcallback");
        }

        @Test
        @DisplayName("should handle production redirect URI")
        void shouldHandleProductionRedirectUri() {
            // Given
            ReflectionTestUtils.setField(config, "clientId", "123456789");
            ReflectionTestUtils.setField(config, "redirectUri", "https://checkitout.app/instagram/callback");

            // When
            String url = config.getAuthorizationUrl("session-abc123");

            // Then
            assertThat(url).startsWith("https://www.instagram.com/oauth/authorize");
            assertThat(url).contains("redirect_uri=https%3A%2F%2Fcheckitout.app%2Finstagram%2Fcallback");
            assertThat(url).contains("&state=session-abc123");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2000000000000001", "2000000000000002", "1234567890123456"})
        @DisplayName("should accept various client IDs")
        void shouldAcceptVariousClientIds(String clientId) {
            // Given
            ReflectionTestUtils.setField(config, "clientId", clientId);
            ReflectionTestUtils.setField(config, "clientSecret", "secret");
            ReflectionTestUtils.setField(config, "redirectUri", "https://example.com/callback");

            // When
            boolean valid = config.isConfigurationValid();
            String url = config.getAuthorizationUrl(null);

            // Then
            assertThat(valid).isTrue();
            assertThat(url).contains("client_id=" + clientId);
        }
    }
}
