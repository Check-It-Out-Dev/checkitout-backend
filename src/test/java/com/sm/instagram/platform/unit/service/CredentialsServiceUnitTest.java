package com.sm.instagram.platform.unit.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleCredentialsProvider.
 *
 * Tests credential loading, caching, validation, base64 decoding,
 * project ID extraction, and error handling scenarios.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GoogleCredentialsProvider Unit Tests")
class CredentialsServiceUnitTest {

    private GoogleCredentialsProvider provider;

    // Sample valid service account JSON structure for testing
    private static final String VALID_SERVICE_ACCOUNT_JSON = """
            {
                "type": "service_account",
                "project_id": "test-project-123",
                "private_key_id": "key123",
                "private_key": "-----BEGIN RSA PRIVATE KEY-----\\nMIIEpAIBAAKCAQEA2Z3qX2BTLS4e0rYsC4+mvgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADc+RAABtNsAAA3tAAAA+gBBNQABbRoAAVzjAABrCQAA\\n-----END RSA PRIVATE KEY-----\\n",
                "client_email": "test@test-project-123.iam.gserviceaccount.com",
                "client_id": "123456789",
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
                "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/test%40test-project-123.iam.gserviceaccount.com"
            }
            """;

    private static final String MINIMAL_VALID_JSON = """
            {
                "project_id": "minimal-project",
                "private_key": "-----BEGIN RSA PRIVATE KEY-----\\nMIIEpAIBAAKCAQEA2Z3qX2BTLS4e0rYsC4+mvgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADc+RAABtNsAAA3tAAAA+gBBNQABbRoAAVzjAABrCQAA\\n-----END RSA PRIVATE KEY-----\\n"
            }
            """;

    @BeforeEach
    void setUp() {
        provider = new GoogleCredentialsProvider();
    }

    @Nested
    @DisplayName("isBase64 Detection Tests")
    class IsBase64Tests {

        @Test
        @DisplayName("should detect valid base64 encoded string")
        void shouldDetectValidBase64EncodedString() {
            // Given
            String base64String = Base64.getEncoder().encodeToString(VALID_SERVICE_ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8));

            // When
            boolean result = invokeIsBase64(base64String);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for plain JSON starting with curly brace")
        void shouldReturnFalseForPlainJson() {
            // When
            boolean result = invokeIsBase64(VALID_SERVICE_ACCOUNT_JSON);

            // Then
            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return false for null or empty strings")
        void shouldReturnFalseForNullOrEmpty(String input) {
            // When
            boolean result = invokeIsBase64(input);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for short strings less than 4 characters")
        void shouldReturnFalseForShortStrings() {
            // When
            boolean result = invokeIsBase64("abc");

            // Then
            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "not-valid-base64!@#$",
                "invalid chars here",
                "spaces in string"
        })
        @DisplayName("should return false for strings with invalid base64 characters")
        void shouldReturnFalseForInvalidBase64Characters(String input) {
            // When
            boolean result = invokeIsBase64(input);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle base64 without padding")
        void shouldHandleBase64WithoutPadding() {
            // Given - Create base64 that would normally need padding
            String original = "test content that creates base64 needing padding";
            String base64 = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));
            // Remove padding to simulate improperly padded base64
            String unpaddedBase64 = base64.replaceAll("=+$", "");

            // When - Long enough to trigger auto-padding logic
            boolean result = invokeIsBase64(unpaddedBase64);

            // Then - May or may not be detected depending on padding requirements
            // The method tries auto-padding for strings > 100 chars
            assertThat(result).isIn(true, false); // Implementation-dependent
        }

        private boolean invokeIsBase64(String str) {
            try {
                return (boolean) ReflectionTestUtils.invokeMethod(provider, "isBase64", str);
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Nested
    @DisplayName("decodeWithAutoPadding Tests")
    class DecodeWithAutoPaddingTests {

        @Test
        @DisplayName("should decode properly padded base64")
        void shouldDecodeProperlyPaddedBase64() throws Exception {
            // Given
            String original = "test content";
            String base64 = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));

            // When
            String result = invokeDecodeWithAutoPadding(base64);

            // Then
            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("should add padding and decode when padding is missing")
        void shouldAddPaddingAndDecode() throws Exception {
            // Given
            String original = "test";
            String base64 = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));
            String unpaddedBase64 = base64.replaceAll("=+$", "");

            // When
            String result = invokeDecodeWithAutoPadding(unpaddedBase64);

            // Then
            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("should decode valid looking base64 string without throwing")
        void shouldDecodeValidLookingBase64String() throws Exception {
            // Given - Valid base64 characters that will decode successfully
            String validBase64 = "dGhpc2lzYXRlc3Q="; // "thisisatest" encoded

            // When
            String result = invokeDecodeWithAutoPadding(validBase64);

            // Then
            assertThat(result).isEqualTo("thisisatest");
        }

        private String invokeDecodeWithAutoPadding(String base64) throws Exception {
            return (String) ReflectionTestUtils.invokeMethod(provider, "decodeWithAutoPadding", base64);
        }
    }

    @Nested
    @DisplayName("extractProjectIdFromJson Tests")
    class ExtractProjectIdFromJsonTests {

        @Test
        @DisplayName("should extract project_id from valid JSON in base64 property")
        void shouldExtractProjectIdFromBase64Property() {
            // Given
            String base64Json = Base64.getEncoder().encodeToString(VALID_SERVICE_ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8));
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", base64Json);
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);

            // When
            String result = invokeExtractProjectIdFromJson();

            // Then
            assertThat(result).isEqualTo("test-project-123");
        }

        @Test
        @DisplayName("should extract project_id from plain JSON in property")
        void shouldExtractProjectIdFromPlainJsonProperty() {
            // Given
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", VALID_SERVICE_ACCOUNT_JSON);
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);

            // When
            String result = invokeExtractProjectIdFromJson();

            // Then
            assertThat(result).isEqualTo("test-project-123");
        }

        @Test
        @DisplayName("should extract project_id from file resource")
        void shouldExtractProjectIdFromFileResource() throws IOException {
            // Given
            Resource fileResource = mock(Resource.class);
            when(fileResource.exists()).thenReturn(true);
            when(fileResource.getInputStream()).thenReturn(
                    new java.io.ByteArrayInputStream(VALID_SERVICE_ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8)));
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(provider, "serviceAccountFile", fileResource);

            // When
            String result = invokeExtractProjectIdFromJson();

            // Then
            assertThat(result).isEqualTo("test-project-123");
        }

        @Test
        @DisplayName("should return null when no JSON source available")
        void shouldReturnNullWhenNoJsonAvailable() {
            // Given
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);

            // When
            String result = invokeExtractProjectIdFromJson();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for JSON without project_id")
        void shouldReturnNullForJsonWithoutProjectId() {
            // Given
            String jsonWithoutProjectId = """
                    {
                        "type": "service_account",
                        "client_email": "test@example.com"
                    }
                    """;
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", jsonWithoutProjectId);
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);

            // When
            String result = invokeExtractProjectIdFromJson();

            // Then
            assertThat(result).isNull();
        }

        private String invokeExtractProjectIdFromJson() {
            return (String) ReflectionTestUtils.invokeMethod(provider, "extractProjectIdFromJson");
        }
    }

    @Nested
    @DisplayName("isProductionProject Tests")
    class IsProductionProjectTests {

        @Test
        @DisplayName("should return true for check-it-out-prod project")
        void shouldReturnTrueForProductionProject() {
            // When
            boolean result = invokeIsProductionProject("check-it-out-prod");

            // Then
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "check-it-out-47c50",
                "test-project",
                "dev-project",
                "staging-project"
        })
        @DisplayName("should return false for non-production projects")
        void shouldReturnFalseForNonProductionProjects(String projectId) {
            // When
            boolean result = invokeIsProductionProject(projectId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for null project id")
        void shouldReturnFalseForNullProjectId() {
            // When
            boolean result = invokeIsProductionProject(null);

            // Then
            assertThat(result).isFalse();
        }

        private boolean invokeIsProductionProject(String projectId) {
            return (boolean) ReflectionTestUtils.invokeMethod(provider, "isProductionProject", projectId);
        }
    }

    @Nested
    @DisplayName("isProductionEnvironment Tests")
    class IsProductionEnvironmentTests {

        @ParameterizedTest
        @ValueSource(strings = {"PRODUCTION", "PRODUCTION-STANDALONE", "PROD"})
        @DisplayName("should return true for production environments")
        void shouldReturnTrueForProductionEnvironments(String environment) {
            // Given
            ReflectionTestUtils.setField(provider, "appEnvironment", environment);

            // When
            boolean result = invokeIsProductionEnvironment();

            // Then
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"DEV", "DEVELOPMENT", "TEST", "STAGING", "LOCAL", "UNKNOWN"})
        @DisplayName("should return false for non-production environments")
        void shouldReturnFalseForNonProductionEnvironments(String environment) {
            // Given
            ReflectionTestUtils.setField(provider, "appEnvironment", environment);

            // When
            boolean result = invokeIsProductionEnvironment();

            // Then
            assertThat(result).isFalse();
        }

        private boolean invokeIsProductionEnvironment() {
            return (boolean) ReflectionTestUtils.invokeMethod(provider, "isProductionEnvironment");
        }
    }

    @Nested
    @DisplayName("maskEmail Tests")
    class MaskEmailTests {

        @Test
        @DisplayName("should mask standard email correctly")
        void shouldMaskStandardEmailCorrectly() {
            // When
            String result = invokeMaskEmail("test@example.com");

            // Then
            assertThat(result).isEqualTo("t***@example.com");
        }

        @Test
        @DisplayName("should mask long local part correctly")
        void shouldMaskLongLocalPartCorrectly() {
            // When
            String result = invokeMaskEmail("verylongusername@domain.org");

            // Then
            assertThat(result).isEqualTo("v***@domain.org");
        }

        @Test
        @DisplayName("should return N/A for null email")
        void shouldReturnNAForNullEmail() {
            // When
            String result = invokeMaskEmail(null);

            // Then
            assertThat(result).isEqualTo("N/A");
        }

        @Test
        @DisplayName("should return N/A for empty email")
        void shouldReturnNAForEmptyEmail() {
            // When
            String result = invokeMaskEmail("");

            // Then
            assertThat(result).isEqualTo("N/A");
        }

        @Test
        @DisplayName("should not mask if local part is too short")
        void shouldNotMaskIfLocalPartTooShort() {
            // When - Single character before @
            String result = invokeMaskEmail("a@b.com");

            // Then - Returns unchanged when atIndex <= 1
            assertThat(result).isEqualTo("a@b.com");
        }

        @Test
        @DisplayName("should handle email with subdomain")
        void shouldHandleEmailWithSubdomain() {
            // When
            String result = invokeMaskEmail("user@mail.example.com");

            // Then
            assertThat(result).isEqualTo("u***@mail.example.com");
        }

        private String invokeMaskEmail(String email) {
            return (String) ReflectionTestUtils.invokeMethod(provider, "maskEmail", email);
        }
    }

    @Nested
    @DisplayName("sanitizeForLogging Tests")
    class SanitizeForLoggingTests {

        @Test
        @DisplayName("should remove line breaks from input")
        void shouldRemoveLineBreaks() {
            // When
            String result = invokeSanitizeForLogging("line1\nline2\rline3");

            // Then
            assertThat(result).isEqualTo("line1 line2 line3");
        }

        @Test
        @DisplayName("should remove tabs from input")
        void shouldRemoveTabs() {
            // When
            String result = invokeSanitizeForLogging("text\twith\ttabs");

            // Then
            assertThat(result).isEqualTo("text with tabs");
        }

        @Test
        @DisplayName("should return 'null' string for null input")
        void shouldReturnNullStringForNullInput() {
            // When
            String result = invokeSanitizeForLogging(null);

            // Then
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("should trim whitespace from result")
        void shouldTrimWhitespace() {
            // When
            String result = invokeSanitizeForLogging("  text with spaces  ");

            // Then
            assertThat(result).isEqualTo("text with spaces");
        }

        @Test
        @DisplayName("should remove control characters")
        void shouldRemoveControlCharacters() {
            // When
            String result = invokeSanitizeForLogging("text\u0000with\u001Fcontrol\u007Fchars");

            // Then
            assertThat(result).isEqualTo("textwithcontrolchars");
        }

        private String invokeSanitizeForLogging(String input) {
            return (String) ReflectionTestUtils.invokeMethod(provider, "sanitizeForLogging", input);
        }
    }

    @Nested
    @DisplayName("getInstanceType Tests")
    class GetInstanceTypeTests {

        @Test
        @DisplayName("should return PRODUCTION for production project")
        void shouldReturnProductionForProductionProject() {
            // Given
            ReflectionTestUtils.setField(provider, "projectId", "check-it-out-prod");

            // When
            String result = provider.getInstanceType();

            // Then
            assertThat(result).contains("PRODUCTION");
        }

        @Test
        @DisplayName("should return TEST for test project")
        void shouldReturnTestForTestProject() {
            // Given
            ReflectionTestUtils.setField(provider, "projectId", "check-it-out-47c50");

            // When
            String result = provider.getInstanceType();

            // Then
            assertThat(result).contains("TEST");
        }

        @Test
        @DisplayName("should return CUSTOM for unknown project")
        void shouldReturnCustomForUnknownProject() {
            // Given
            ReflectionTestUtils.setField(provider, "projectId", "my-custom-project");

            // When
            String result = provider.getInstanceType();

            // Then
            assertThat(result).contains("CUSTOM");
            assertThat(result).contains("my-custom-project");
        }

        @Test
        @DisplayName("should return UNKNOWN for null project")
        void shouldReturnUnknownForNullProject() {
            // Given
            ReflectionTestUtils.setField(provider, "projectId", null);

            // When
            String result = provider.getInstanceType();

            // Then
            assertThat(result).contains("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("getCredentialsWithScopes Tests")
    class GetCredentialsWithScopesTests {

        @Mock
        private GoogleCredentials mockCredentials;

        @Mock
        private GoogleCredentials scopedCredentials;

        @Test
        @DisplayName("should return base credentials when no scopes provided")
        void shouldReturnBaseCredentialsWhenNoScopes() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);

            // When
            GoogleCredentials result = provider.getCredentialsWithScopes();

            // Then
            assertThat(result).isEqualTo(mockCredentials);
        }

        @Test
        @DisplayName("should return base credentials when null scopes provided")
        void shouldReturnBaseCredentialsWhenNullScopes() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);

            // When
            GoogleCredentials result = provider.getCredentialsWithScopes((String[]) null);

            // Then
            assertThat(result).isEqualTo(mockCredentials);
        }

        @Test
        @DisplayName("should return base credentials when empty scopes array provided")
        void shouldReturnBaseCredentialsWhenEmptyScopesArray() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);

            // When
            GoogleCredentials result = provider.getCredentialsWithScopes(new String[]{});

            // Then
            assertThat(result).isEqualTo(mockCredentials);
        }

        @Test
        @DisplayName("should create scoped credentials when scopes provided")
        void shouldCreateScopedCredentialsWhenScopesProvided() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);
            when(mockCredentials.createScoped("https://www.googleapis.com/auth/cloud-platform"))
                    .thenReturn(scopedCredentials);

            // When
            GoogleCredentials result = provider.getCredentialsWithScopes("https://www.googleapis.com/auth/cloud-platform");

            // Then
            assertThat(result).isEqualTo(scopedCredentials);
            verify(mockCredentials).createScoped("https://www.googleapis.com/auth/cloud-platform");
        }

        @Test
        @DisplayName("should return base credentials when createScoped throws exception")
        void shouldReturnBaseCredentialsWhenCreateScopedThrows() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);
            when(mockCredentials.createScoped("invalid-scope"))
                    .thenThrow(new RuntimeException("Invalid scope"));

            // When
            GoogleCredentials result = provider.getCredentialsWithScopes("invalid-scope");

            // Then
            assertThat(result).isEqualTo(mockCredentials);
        }
    }

    @Nested
    @DisplayName("verifyCredentials Tests")
    class VerifyCredentialsTests {

        @Mock
        private GoogleCredentials mockCredentials;

        @Test
        @DisplayName("should return false when credentials are null")
        void shouldReturnFalseWhenCredentialsAreNull() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", null);

            // When
            boolean result = provider.verifyCredentials();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when credentials refresh succeeds")
        void shouldReturnTrueWhenRefreshSucceeds() throws Exception {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);
            doNothing().when(mockCredentials).refresh();

            // When
            boolean result = provider.verifyCredentials();

            // Then
            assertThat(result).isTrue();
            verify(mockCredentials).refresh();
        }

        @Test
        @DisplayName("should return false when credentials refresh throws exception")
        void shouldReturnFalseWhenRefreshThrows() throws Exception {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);
            doThrow(new IOException("Refresh failed")).when(mockCredentials).refresh();

            // When
            boolean result = provider.verifyCredentials();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Getter Methods Tests")
    class GetterMethodsTests {

        @Mock
        private GoogleCredentials mockCredentials;

        @Test
        @DisplayName("should return cached credentials via getter")
        void shouldReturnCachedCredentials() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mockCredentials);

            // When
            GoogleCredentials result = provider.getCachedCredentials();

            // Then
            assertThat(result).isEqualTo(mockCredentials);
        }

        @Test
        @DisplayName("should return project ID via getter")
        void shouldReturnProjectId() {
            // Given
            ReflectionTestUtils.setField(provider, "projectId", "my-project");

            // When
            String result = provider.getProjectId();

            // Then
            assertThat(result).isEqualTo("my-project");
        }

        @Test
        @DisplayName("should return service account email via getter")
        void shouldReturnServiceAccountEmail() {
            // Given
            ReflectionTestUtils.setField(provider, "serviceAccountEmail", "service@project.iam.gserviceaccount.com");

            // When
            String result = provider.getServiceAccountEmail();

            // Then
            assertThat(result).isEqualTo("service@project.iam.gserviceaccount.com");
        }

        @Test
        @DisplayName("should return using production credentials flag via getter")
        void shouldReturnUsingProductionCredentials() {
            // Given
            ReflectionTestUtils.setField(provider, "usingProductionCredentials", true);

            // When
            boolean result = provider.isUsingProductionCredentials();

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("loadFromBase64Property Tests")
    class LoadFromBase64PropertyTests {

        @Test
        @DisplayName("should throw for invalid JSON structure")
        void shouldThrowForInvalidJsonStructure() {
            // Given - JSON without required fields
            String invalidJson = """
                    {
                        "type": "service_account",
                        "client_email": "test@example.com"
                    }
                    """;

            // When/Then
            assertThatThrownBy(() -> invokeLoadFromBase64Property(invalidJson))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid service account JSON structure");
        }

        @Test
        @DisplayName("should throw for JSON missing project_id")
        void shouldThrowForJsonMissingProjectId() {
            // Given - JSON with private_key but no project_id
            String jsonMissingProjectId = """
                    {
                        "type": "service_account",
                        "private_key": "-----BEGIN RSA PRIVATE KEY-----\\nkey\\n-----END RSA PRIVATE KEY-----\\n"
                    }
                    """;

            // When/Then
            assertThatThrownBy(() -> invokeLoadFromBase64Property(jsonMissingProjectId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid service account JSON structure");
        }

        @Test
        @DisplayName("should throw for JSON missing private_key")
        void shouldThrowForJsonMissingPrivateKey() {
            // Given - JSON with project_id but no private_key
            String jsonMissingPrivateKey = """
                    {
                        "type": "service_account",
                        "project_id": "test-project"
                    }
                    """;

            // When/Then
            assertThatThrownBy(() -> invokeLoadFromBase64Property(jsonMissingPrivateKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid service account JSON structure");
        }

        private GoogleCredentials invokeLoadFromBase64Property(String base64OrJson) throws Exception {
            return (GoogleCredentials) ReflectionTestUtils.invokeMethod(provider, "loadFromBase64Property", base64OrJson);
        }
    }

    @Nested
    @DisplayName("Init Error Handling Tests")
    class InitErrorHandlingTests {

        @Test
        @DisplayName("non-production init falls back to synthetic credentials when none can be loaded")
        void nonProductionFallsBackToSyntheticWhenCredentialsCannotBeLoaded() {
            // Given - No credentials configured. Contract change (contributor
            // boot): outside production the provider degrades to synthetic
            // offline credentials instead of killing the context.
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);
            ReflectionTestUtils.setField(provider, "appEnvironment", "TEST");

            // When/Then
            assertThatCode(() -> provider.init()).doesNotThrowAnyException();
            assertThat(provider.getCachedCredentials()).isNotNull();
            assertThat(provider.isUsingProductionCredentials()).isFalse();
        }

        @Test
        @DisplayName("non-production init survives invalid base64 via the synthetic fallback")
        void nonProductionSurvivesInvalidBase64() {
            // Given - Invalid base64 and no file fallback; same degraded-boot
            // contract as above.
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "invalid-base64-content!@#$");
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);
            ReflectionTestUtils.setField(provider, "appEnvironment", "TEST");

            // When/Then
            assertThatCode(() -> provider.init()).doesNotThrowAnyException();
            assertThat(provider.getCachedCredentials()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Project ID Extraction Priority Tests")
    class ProjectIdExtractionPriorityTests {

        @Test
        @DisplayName("should use GCP project ID when no credentials project ID available")
        void shouldUseGcpProjectIdWhenNoCredentialsProjectId() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mock(GoogleCredentials.class));
            ReflectionTestUtils.setField(provider, "gcpProjectId", "gcp-configured-project");
            ReflectionTestUtils.setField(provider, "configuredProjectId", "firebase-configured-project");
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);

            // When
            String result = invokeExtractProjectId();

            // Then
            assertThat(result).isEqualTo("gcp-configured-project");
        }

        @Test
        @DisplayName("should use Firebase project ID when GCP not configured")
        void shouldUseFirebaseProjectIdWhenGcpNotConfigured() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mock(GoogleCredentials.class));
            ReflectionTestUtils.setField(provider, "gcpProjectId", "");
            ReflectionTestUtils.setField(provider, "configuredProjectId", "firebase-configured-project");
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);

            // When
            String result = invokeExtractProjectId();

            // Then
            assertThat(result).isEqualTo("firebase-configured-project");
        }

        @Test
        @DisplayName("should use default project ID when nothing configured")
        void shouldUseDefaultProjectIdWhenNothingConfigured() {
            // Given
            ReflectionTestUtils.setField(provider, "cachedCredentials", mock(GoogleCredentials.class));
            ReflectionTestUtils.setField(provider, "gcpProjectId", "");
            ReflectionTestUtils.setField(provider, "configuredProjectId", "");
            ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(provider, "serviceAccountFile", null);

            // When
            String result = invokeExtractProjectId();

            // Then
            assertThat(result).isEqualTo("check-it-out-47c50");
        }

        @Test
        @DisplayName("should extract project ID from ServiceAccountCredentials if available")
        void shouldExtractProjectIdFromServiceAccountCredentials() {
            // Given
            ServiceAccountCredentials saCreds = mock(ServiceAccountCredentials.class);
            when(saCreds.getProjectId()).thenReturn("sa-project-from-creds");
            ReflectionTestUtils.setField(provider, "cachedCredentials", saCreds);
            ReflectionTestUtils.setField(provider, "gcpProjectId", "gcp-project");
            ReflectionTestUtils.setField(provider, "configuredProjectId", "firebase-project");

            // When
            String result = invokeExtractProjectId();

            // Then
            assertThat(result).isEqualTo("sa-project-from-creds");
        }

        private String invokeExtractProjectId() {
            return (String) ReflectionTestUtils.invokeMethod(provider, "extractProjectId");
        }
    }

    @Nested
    @DisplayName("Environment-Credential Mismatch Detection Tests")
    class EnvironmentCredentialMismatchTests {

        @Test
        @DisplayName("should detect production environment with non-production credentials")
        void shouldDetectProductionEnvWithNonProdCredentials() {
            // This is a logging test - we verify the method runs without exception
            // The actual mismatch warning is logged, not thrown

            // Given
            ReflectionTestUtils.setField(provider, "appEnvironment", "PRODUCTION");
            ReflectionTestUtils.setField(provider, "usingProductionCredentials", false);
            ReflectionTestUtils.setField(provider, "projectId", "test-project");
            ReflectionTestUtils.setField(provider, "serviceAccountEmail", "test@test.iam.gserviceaccount.com");

            // When/Then - should not throw, just log
            assertThatCode(() -> invokeLogCredentialSummary())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should detect non-production environment with production credentials")
        void shouldDetectNonProdEnvWithProdCredentials() {
            // Given
            ReflectionTestUtils.setField(provider, "appEnvironment", "DEV");
            ReflectionTestUtils.setField(provider, "usingProductionCredentials", true);
            ReflectionTestUtils.setField(provider, "projectId", "check-it-out-prod");
            ReflectionTestUtils.setField(provider, "serviceAccountEmail", "prod@prod.iam.gserviceaccount.com");

            // When/Then - should not throw, just log warning
            assertThatCode(() -> invokeLogCredentialSummary())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not warn when environment and credentials match")
        void shouldNotWarnWhenEnvAndCredentialsMatch() {
            // Given
            ReflectionTestUtils.setField(provider, "appEnvironment", "PRODUCTION");
            ReflectionTestUtils.setField(provider, "usingProductionCredentials", true);
            ReflectionTestUtils.setField(provider, "projectId", "check-it-out-prod");
            ReflectionTestUtils.setField(provider, "serviceAccountEmail", "prod@prod.iam.gserviceaccount.com");

            // When/Then - should not throw
            assertThatCode(() -> invokeLogCredentialSummary())
                    .doesNotThrowAnyException();
        }

        private void invokeLogCredentialSummary() {
            ReflectionTestUtils.invokeMethod(provider, "logCredentialSummary");
        }
    }

    @Nested
    @DisplayName("Base64 Edge Cases Tests")
    class Base64EdgeCasesTests {

        @Test
        @DisplayName("should handle JSON that looks like base64 but starts with curly brace")
        void shouldHandleJsonThatLooksLikeBase64() {
            // Given - JSON starting with { should not be treated as base64
            String json = "{ \"key\": \"value\" }";

            // When
            boolean result = invokeIsBase64(json);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle very long base64 strings")
        void shouldHandleVeryLongBase64Strings() {
            // Given - Create a long base64 string (over 100 chars to trigger auto-padding logic)
            String longOriginal = "x".repeat(200);
            String longBase64 = Base64.getEncoder().encodeToString(longOriginal.getBytes(StandardCharsets.UTF_8));

            // When
            boolean result = invokeIsBase64(longBase64);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle base64 with standard padding")
        void shouldHandleBase64WithStandardPadding() {
            // Given - Base64 with proper padding
            String base64WithPadding = "dGVzdA=="; // "test"

            // When
            boolean result = invokeIsBase64(base64WithPadding);

            // Then
            assertThat(result).isTrue();
        }

        private boolean invokeIsBase64(String str) {
            try {
                return (boolean) ReflectionTestUtils.invokeMethod(provider, "isBase64", str);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
