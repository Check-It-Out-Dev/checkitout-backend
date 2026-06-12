package com.sm.instagram.platform.unit.security;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.util.ServiceAccountKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ServiceAccountKeyValidator.
 * These are pure unit tests - no Spring context needed.
 */
@DisplayName("ServiceAccountKeyValidator")
class ServiceAccountKeyValidatorUnitTest {

    private ServiceAccountKeyValidator validator;

    // Valid service account JSON fixture
    private static final String VALID_SERVICE_ACCOUNT_JSON = """
            {
                "type": "service_account",
                "project_id": "my-test-project",
                "private_key_id": "key123",
                "private_key": "-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEF...\\n-----END PRIVATE KEY-----\\n",
                "client_email": "test-service@my-test-project.iam.gserviceaccount.com",
                "client_id": "123456789",
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token"
            }
            """;

    @BeforeEach
    void setUp() {
        validator = new ServiceAccountKeyValidator();
    }

    private InputStream toInputStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("validateServiceAccountKey - Valid Input")
    class ValidInput {

        @Test
        @DisplayName("should accept valid service account JSON with all required fields")
        void shouldAcceptValidServiceAccountJson() {
            // Given
            InputStream keyStream = toInputStream(VALID_SERVICE_ACCOUNT_JSON);

            // When/Then
            assertThatCode(() -> validator.validateServiceAccountKey(keyStream))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should accept minimal valid service account JSON with only required fields")
        void shouldAcceptMinimalValidServiceAccountJson() {
            // Given
            String minimalJson = """
                    {
                        "type": "service_account",
                        "project_id": "minimal-project",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\ntest-key\\n-----END PRIVATE KEY-----",
                        "client_email": "service@project.iam.gserviceaccount.com"
                    }
                    """;
            InputStream keyStream = toInputStream(minimalJson);

            // When/Then
            assertThatCode(() -> validator.validateServiceAccountKey(keyStream))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should accept service account JSON with additional optional fields")
        void shouldAcceptJsonWithAdditionalFields() {
            // Given
            String jsonWithExtras = """
                    {
                        "type": "service_account",
                        "project_id": "test-project",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----",
                        "client_email": "test@test.iam.gserviceaccount.com",
                        "extra_field": "should be ignored",
                        "another_field": 12345
                    }
                    """;
            InputStream keyStream = toInputStream(jsonWithExtras);

            // When/Then
            assertThatCode(() -> validator.validateServiceAccountKey(keyStream))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateServiceAccountKey - Null Input")
    class NullInput {

        @Test
        @DisplayName("should throw ValidationTranslatableException for null input stream")
        void shouldThrowForNullInputStream() {
            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(null))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.required_field");
        }
    }

    @Nested
    @DisplayName("validateServiceAccountKey - Missing Required Fields")
    class MissingRequiredFields {

        @Test
        @DisplayName("should throw ValidationTranslatableException when project_id is missing")
        void shouldThrowWhenProjectIdMissing() {
            // Given
            String jsonMissingProjectId = """
                    {
                        "type": "service_account",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----",
                        "client_email": "test@test.iam.gserviceaccount.com"
                    }
                    """;
            InputStream keyStream = toInputStream(jsonMissingProjectId);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.missing_parameter");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when private_key is missing")
        void shouldThrowWhenPrivateKeyMissing() {
            // Given
            String jsonMissingPrivateKey = """
                    {
                        "type": "service_account",
                        "project_id": "test-project",
                        "client_email": "test@test.iam.gserviceaccount.com"
                    }
                    """;
            InputStream keyStream = toInputStream(jsonMissingPrivateKey);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.missing_parameter");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when client_email is missing")
        void shouldThrowWhenClientEmailMissing() {
            // Given
            String jsonMissingClientEmail = """
                    {
                        "type": "service_account",
                        "project_id": "test-project",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----"
                    }
                    """;
            InputStream keyStream = toInputStream(jsonMissingClientEmail);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.missing_parameter");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when type field is missing")
        void shouldThrowWhenTypeMissing() {
            // Given
            String jsonMissingType = """
                    {
                        "project_id": "test-project",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----",
                        "client_email": "test@test.iam.gserviceaccount.com"
                    }
                    """;
            InputStream keyStream = toInputStream(jsonMissingType);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.missing_parameter");
        }
    }

    @Nested
    @DisplayName("validateServiceAccountKey - Malformed JSON")
    class MalformedJson {

        @Test
        @DisplayName("should throw ValidationTranslatableException for malformed JSON")
        void shouldThrowForMalformedJson() {
            // Given
            String malformedJson = "{ this is not valid json }";
            InputStream keyStream = toInputStream(malformedJson);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.invalid_json");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for empty JSON object")
        void shouldThrowForEmptyJsonObject() {
            // Given
            String emptyJson = "{}";
            InputStream keyStream = toInputStream(emptyJson);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.missing_parameter");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for empty input string")
        void shouldThrowForEmptyInputString() {
            // Given
            String emptyString = "";
            InputStream keyStream = toInputStream(emptyString);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.invalid_json");
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for JSON array instead of object")
        void shouldThrowForJsonArray() {
            // Given
            String jsonArray = "[\"type\", \"service_account\"]";
            InputStream keyStream = toInputStream(jsonArray);

            // When/Then - Jackson will throw on readValue when expecting Map
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.validation.invalid_json");
        }
    }

    @Nested
    @DisplayName("validateServiceAccountKey - Type Validation")
    class TypeValidation {

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when type is not service_account")
        void shouldThrowWhenTypeIsNotServiceAccount() {
            // Given
            String jsonWrongType = """
                    {
                        "type": "user_account",
                        "project_id": "test-project",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----",
                        "client_email": "test@test.iam.gserviceaccount.com"
                    }
                    """;
            InputStream keyStream = toInputStream(jsonWrongType);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.validation.type_mismatch");
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when type is empty string")
        void shouldThrowWhenTypeIsEmpty() {
            // Given
            String jsonEmptyType = """
                    {
                        "type": "",
                        "project_id": "test-project",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----",
                        "client_email": "test@test.iam.gserviceaccount.com"
                    }
                    """;
            InputStream keyStream = toInputStream(jsonEmptyType);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.validation.type_mismatch");
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when type has wrong case")
        void shouldThrowWhenTypeHasWrongCase() {
            // Given - service_account vs SERVICE_ACCOUNT (case sensitive)
            String jsonWrongCase = """
                    {
                        "type": "SERVICE_ACCOUNT",
                        "project_id": "test-project",
                        "private_key": "-----BEGIN PRIVATE KEY-----\\nkey\\n-----END PRIVATE KEY-----",
                        "client_email": "test@test.iam.gserviceaccount.com"
                    }
                    """;
            InputStream keyStream = toInputStream(jsonWrongCase);

            // When/Then
            assertThatThrownBy(() -> validator.validateServiceAccountKey(keyStream))
                    .isInstanceOf(BusinessRuleTranslatableException.class)
                    .hasMessageContaining("error.validation.type_mismatch");
        }
    }
}
