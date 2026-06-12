package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.secrets.ProductionSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductionSecretService Unit Tests")
class SecretsServiceUnitTest {

    @Mock
    private Environment env;

    private ProductionSecretService secretService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        secretService = new ProductionSecretService();
        ReflectionTestUtils.setField(secretService, "env", env);
        ReflectionTestUtils.setField(secretService, "secretsMountPath", tempDir.toString());
        ReflectionTestUtils.setField(secretService, "developmentMode", false);
        ReflectionTestUtils.setField(secretService, "cacheEnabled", true);
        ReflectionTestUtils.setField(secretService, "activeProfile", "");
        ReflectionTestUtils.setField(secretService, "localDbUsername", "");
        ReflectionTestUtils.setField(secretService, "localDbPassword", "");
        ReflectionTestUtils.setField(secretService, "localFirebaseConfigPath", "");
        ReflectionTestUtils.setField(secretService, "localInstagramSecret", "");
        ReflectionTestUtils.setField(secretService, "localJwtSecret", "");
        ReflectionTestUtils.setField(secretService, "localCookieHmacSecret", "");
    }

    @Nested
    @DisplayName("getSecret - Production Mode (Mounted Volume)")
    class GetSecretProductionModeTests {

        @Test
        @DisplayName("should read secret from individual file")
        void shouldReadSecretFromIndividualFile() throws IOException {
            // Given
            String secretName = "TEST_SECRET";
            String secretValue = "my-secret-value";
            Files.writeString(tempDir.resolve(secretName), secretValue);

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo(secretValue);
        }

        @Test
        @DisplayName("should trim whitespace from secret file")
        void shouldTrimWhitespaceFromSecretFile() throws IOException {
            // Given
            String secretName = "WHITESPACE_SECRET";
            Files.writeString(tempDir.resolve(secretName), "  secret-with-spaces  \n");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("secret-with-spaces");
        }

        @Test
        @DisplayName("should read secret from JSON file")
        void shouldReadSecretFromJsonFile() throws IOException {
            // Given
            String json = "{\"DB_PASSWORD\": \"json-password\", \"API_KEY\": \"json-api-key\"}";
            Files.writeString(tempDir.resolve("secrets.json"), json);

            // When
            String result = secretService.getSecret("DB_PASSWORD");

            // Then
            assertThat(result).isEqualTo("json-password");
        }

        @Test
        @DisplayName("should read multiple secrets from JSON file")
        void shouldReadMultipleSecretsFromJsonFile() throws IOException {
            // Given
            String json = "{\"SECRET_ONE\": \"value1\", \"SECRET_TWO\": \"value2\", \"SECRET_THREE\": \"value3\"}";
            Files.writeString(tempDir.resolve("secrets.json"), json);

            // When
            String result1 = secretService.getSecret("SECRET_ONE");
            String result2 = secretService.getSecret("SECRET_TWO");
            String result3 = secretService.getSecret("SECRET_THREE");

            // Then
            assertThat(result1).isEqualTo("value1");
            assertThat(result2).isEqualTo("value2");
            assertThat(result3).isEqualTo("value3");
        }

        @Test
        @DisplayName("should read secret from properties file")
        void shouldReadSecretFromPropertiesFile() throws IOException {
            // Given
            String props = "MY_SECRET=props-secret-value\nANOTHER_SECRET=another-value";
            Files.writeString(tempDir.resolve("secrets.properties"), props);

            // When
            String result = secretService.getSecret("MY_SECRET");

            // Then
            assertThat(result).isEqualTo("props-secret-value");
        }

        @Test
        @DisplayName("should read secret from env file")
        void shouldReadSecretFromEnvFile() throws IOException {
            // Given
            String envContent = "ENV_SECRET=env-secret-value\nANOTHER_ENV_SECRET=another-env-value";
            Files.writeString(tempDir.resolve("secrets.env"), envContent);

            // When
            String result = secretService.getSecret("ENV_SECRET");

            // Then
            assertThat(result).isEqualTo("env-secret-value");
        }

        @Test
        @DisplayName("should remove double quotes from env file values")
        void shouldRemoveDoubleQuotesFromEnvFileValues() throws IOException {
            // Given
            String envContent = "QUOTED_SECRET=\"quoted-value\"";
            Files.writeString(tempDir.resolve("secrets.env"), envContent);

            // When
            String result = secretService.getSecret("QUOTED_SECRET");

            // Then
            assertThat(result).isEqualTo("quoted-value");
        }

        @Test
        @DisplayName("should remove single quotes from env file values")
        void shouldRemoveSingleQuotesFromEnvFileValues() throws IOException {
            // Given
            String envContent = "SINGLE_QUOTED='single-quoted-value'";
            Files.writeString(tempDir.resolve("secrets.env"), envContent);

            // When
            String result = secretService.getSecret("SINGLE_QUOTED");

            // Then
            assertThat(result).isEqualTo("single-quoted-value");
        }

        @Test
        @DisplayName("should skip comment lines in env file")
        void shouldSkipCommentLinesInEnvFile() throws IOException {
            // Given
            String envContent = "# This is a comment\nCOMMENT_TEST=actual-value\n# Another comment";
            Files.writeString(tempDir.resolve("secrets.env"), envContent);

            // When
            String result = secretService.getSecret("COMMENT_TEST");

            // Then
            assertThat(result).isEqualTo("actual-value");
        }

        @Test
        @DisplayName("should skip empty lines in env file")
        void shouldSkipEmptyLinesInEnvFile() throws IOException {
            // Given
            String envContent = "\n\nEMPTY_LINE_TEST=test-value\n\n";
            Files.writeString(tempDir.resolve("secrets.env"), envContent);

            // When
            String result = secretService.getSecret("EMPTY_LINE_TEST");

            // Then
            assertThat(result).isEqualTo("test-value");
        }

        @Test
        @DisplayName("should return null when secret not found")
        void shouldReturnNullWhenSecretNotFound() {
            // When
            String result = secretService.getSecret("NON_EXISTENT_SECRET");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should prefer individual file over JSON file")
        void shouldPreferIndividualFileOverJsonFile() throws IOException {
            // Given
            String secretName = "PRIORITY_SECRET";
            Files.writeString(tempDir.resolve(secretName), "individual-file-value");
            String json = "{\"PRIORITY_SECRET\": \"json-value\"}";
            Files.writeString(tempDir.resolve("secrets.json"), json);

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("individual-file-value");
        }
    }

    @Nested
    @DisplayName("getSecret - Caching")
    class GetSecretCachingTests {

        @Test
        @DisplayName("should cache secret when caching is enabled")
        void shouldCacheSecretWhenCachingEnabled() throws IOException {
            // Given
            String secretName = "CACHED_SECRET";
            Files.writeString(tempDir.resolve(secretName), "cached-value");

            // When - First call
            String result1 = secretService.getSecret(secretName);
            // Modify the file
            Files.writeString(tempDir.resolve(secretName), "modified-value");
            // Second call should return cached value
            String result2 = secretService.getSecret(secretName);

            // Then
            assertThat(result1).isEqualTo("cached-value");
            assertThat(result2).isEqualTo("cached-value");
        }

        @Test
        @DisplayName("should not cache when caching is disabled")
        void shouldNotCacheWhenCachingDisabled() throws IOException {
            // Given
            ReflectionTestUtils.setField(secretService, "cacheEnabled", false);
            String secretName = "UNCACHED_SECRET";
            Files.writeString(tempDir.resolve(secretName), "original-value");

            // When - First call
            String result1 = secretService.getSecret(secretName);
            // Modify the file
            Files.writeString(tempDir.resolve(secretName), "modified-value");
            // Second call should get fresh value
            String result2 = secretService.getSecret(secretName);

            // Then
            assertThat(result1).isEqualTo("original-value");
            assertThat(result2).isEqualTo("modified-value");
        }

        @Test
        @DisplayName("should not cache null values")
        void shouldNotCacheNullValues() throws IOException {
            // Given - Secret doesn't exist initially
            String secretName = "LATE_SECRET";

            // When - First call returns null
            String result1 = secretService.getSecret(secretName);
            // Create the secret file
            Files.writeString(tempDir.resolve(secretName), "late-value");
            // Second call should find the newly created secret
            String result2 = secretService.getSecret(secretName);

            // Then
            assertThat(result1).isNull();
            assertThat(result2).isEqualTo("late-value");
        }

        @Test
        @DisplayName("should clear cache when clearCache is called")
        void shouldClearCacheWhenClearCacheIsCalled() throws IOException {
            // Given
            String secretName = "CLEARABLE_SECRET";
            Files.writeString(tempDir.resolve(secretName), "original-value");
            secretService.getSecret(secretName); // Cache the secret

            // When
            Files.writeString(tempDir.resolve(secretName), "new-value");
            secretService.clearCache();
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("new-value");
        }
    }

    @Nested
    @DisplayName("getSecret - Development Mode")
    class GetSecretDevelopmentModeTests {

        @BeforeEach
        void setUpDevelopmentMode() {
            ReflectionTestUtils.setField(secretService, "developmentMode", true);
        }

        @Test
        @DisplayName("should return local DB username for POSTGRES_USER")
        void shouldReturnLocalDbUsernameForPostgresUser() {
            // Given
            ReflectionTestUtils.setField(secretService, "localDbUsername", "local-db-user");

            // When
            String result = secretService.getSecret("POSTGRES_USER");

            // Then
            assertThat(result).isEqualTo("local-db-user");
        }

        @Test
        @DisplayName("should return local DB username for DATABASE_USER")
        void shouldReturnLocalDbUsernameForDatabaseUser() {
            // Given
            ReflectionTestUtils.setField(secretService, "localDbUsername", "local-db-user");

            // When
            String result = secretService.getSecret("DATABASE_USER");

            // Then
            assertThat(result).isEqualTo("local-db-user");
        }

        @Test
        @DisplayName("should return local DB username for APP_DB_USER")
        void shouldReturnLocalDbUsernameForAppDbUser() {
            // Given
            ReflectionTestUtils.setField(secretService, "localDbUsername", "local-db-user");

            // When
            String result = secretService.getSecret("APP_DB_USER");

            // Then
            assertThat(result).isEqualTo("local-db-user");
        }

        @Test
        @DisplayName("should return local DB password for POSTGRES_PASSWORD")
        void shouldReturnLocalDbPasswordForPostgresPassword() {
            // Given
            ReflectionTestUtils.setField(secretService, "localDbPassword", "local-db-password");

            // When
            String result = secretService.getSecret("POSTGRES_PASSWORD");

            // Then
            assertThat(result).isEqualTo("local-db-password");
        }

        @Test
        @DisplayName("should return local DB password for DATABASE_PASSWORD")
        void shouldReturnLocalDbPasswordForDatabasePassword() {
            // Given
            ReflectionTestUtils.setField(secretService, "localDbPassword", "local-db-password");

            // When
            String result = secretService.getSecret("DATABASE_PASSWORD");

            // Then
            assertThat(result).isEqualTo("local-db-password");
        }

        @Test
        @DisplayName("should return local DB password for APP_DB_PASSWORD")
        void shouldReturnLocalDbPasswordForAppDbPassword() {
            // Given
            ReflectionTestUtils.setField(secretService, "localDbPassword", "local-db-password");

            // When
            String result = secretService.getSecret("APP_DB_PASSWORD");

            // Then
            assertThat(result).isEqualTo("local-db-password");
        }

        @Test
        @DisplayName("should extract database name from JDBC URL for POSTGRES_DB")
        void shouldExtractDatabaseNameFromJdbcUrlForPostgresDb() {
            // Given
            when(env.getProperty("spring.datasource.url", ""))
                .thenReturn("jdbc:postgresql://localhost:5432/checkitout_db");

            // When
            String result = secretService.getSecret("POSTGRES_DB");

            // Then
            assertThat(result).isEqualTo("checkitout_db");
        }

        @Test
        @DisplayName("should extract database name from JDBC URL with query params")
        void shouldExtractDatabaseNameFromJdbcUrlWithQueryParams() {
            // Given
            when(env.getProperty("spring.datasource.url", ""))
                .thenReturn("jdbc:postgresql://localhost:5432/mydb?ssl=true&sslmode=require");

            // When
            String result = secretService.getSecret("POSTGRES_DB");

            // Then
            assertThat(result).isEqualTo("mydb");
        }

        @Test
        @DisplayName("should return default database name when URL is empty")
        void shouldReturnDefaultDatabaseNameWhenUrlIsEmpty() {
            // Given
            when(env.getProperty("spring.datasource.url", "")).thenReturn("");

            // When
            String result = secretService.getSecret("POSTGRES_DB");

            // Then
            assertThat(result).isEqualTo("checkitout_local_db");
        }

        @Test
        @DisplayName("should return Instagram client secret")
        void shouldReturnInstagramClientSecret() {
            // Given
            ReflectionTestUtils.setField(secretService, "localInstagramSecret", "instagram-client-secret");

            // When
            String result = secretService.getSecret("INSTAGRAM_CLIENT_SECRET");

            // Then
            assertThat(result).isEqualTo("instagram-client-secret");
        }

        @Test
        @DisplayName("should return JWT secret")
        void shouldReturnJwtSecret() {
            // Given
            ReflectionTestUtils.setField(secretService, "localJwtSecret", "jwt-secret-key");

            // When
            String result = secretService.getSecret("JWT_SECRET");

            // Then
            assertThat(result).isEqualTo("jwt-secret-key");
        }

        @Test
        @DisplayName("should return Cookie HMAC secret")
        void shouldReturnCookieHmacSecret() {
            // Given
            ReflectionTestUtils.setField(secretService, "localCookieHmacSecret", "cookie-hmac-secret");

            // When
            String result = secretService.getSecret("COOKIE_HMAC_SECRET");

            // Then
            assertThat(result).isEqualTo("cookie-hmac-secret");
        }

        @Test
        @DisplayName("should read Firebase config from local file")
        void shouldReadFirebaseConfigFromLocalFile() throws IOException {
            // Given
            Path firebaseConfig = tempDir.resolve("firebase-config.json");
            String configContent = "{\"project_id\": \"test-project\"}";
            Files.writeString(firebaseConfig, configContent);
            ReflectionTestUtils.setField(secretService, "localFirebaseConfigPath", firebaseConfig.toString());

            // When
            String result = secretService.getSecret("FIREBASE_SERVICE_ACCOUNT_JSON");

            // Then
            assertThat(result).isEqualTo(configContent);
        }

        @Test
        @DisplayName("should return null when Firebase config path is empty")
        void shouldReturnNullWhenFirebaseConfigPathIsEmpty() {
            // Given
            ReflectionTestUtils.setField(secretService, "localFirebaseConfigPath", "");

            // When
            String result = secretService.getSecret("FIREBASE_SERVICE_ACCOUNT_JSON");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when Firebase config file not found")
        void shouldReturnNullWhenFirebaseConfigFileNotFound() {
            // Given
            ReflectionTestUtils.setField(secretService, "localFirebaseConfigPath", "/non/existent/path.json");

            // When
            String result = secretService.getSecret("FIREBASE_SERVICE_ACCOUNT_JSON");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should try environment property for unknown secrets")
        void shouldTryEnvironmentPropertyForUnknownSecrets() {
            // Given
            when(env.getProperty("CUSTOM_SECRET")).thenReturn("custom-env-value");

            // When
            String result = secretService.getSecret("CUSTOM_SECRET");

            // Then
            assertThat(result).isEqualTo("custom-env-value");
        }

        @Test
        @DisplayName("should try local secrets path for unknown secrets")
        void shouldTryLocalSecretsPathForUnknownSecrets() throws IOException {
            // Given
            Path localSecretsDir = tempDir.resolve("local-secrets");
            Files.createDirectories(localSecretsDir);
            Files.writeString(localSecretsDir.resolve("CUSTOM_FILE_SECRET"), "file-secret-value");
            when(env.getProperty("CUSTOM_FILE_SECRET")).thenReturn(null);
            when(env.getProperty("secrets.local.path", "src/main/resources/secrets"))
                .thenReturn(localSecretsDir.toString());

            // When
            String result = secretService.getSecret("CUSTOM_FILE_SECRET");

            // Then
            assertThat(result).isEqualTo("file-secret-value");
        }
    }

    @Nested
    @DisplayName("Convenience Methods")
    class ConvenienceMethodsTests {

        @Test
        @DisplayName("getDatabasePassword should try POSTGRES_PASSWORD first")
        void getDatabasePasswordShouldTryPostgresPasswordFirst() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("POSTGRES_PASSWORD"), "postgres-pwd");

            // When
            String result = secretService.getDatabasePassword();

            // Then
            assertThat(result).isEqualTo("postgres-pwd");
        }

        @Test
        @DisplayName("getDatabasePassword should fallback to DATABASE_PASSWORD")
        void getDatabasePasswordShouldFallbackToDatabasePassword() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("DATABASE_PASSWORD"), "database-pwd");

            // When
            String result = secretService.getDatabasePassword();

            // Then
            assertThat(result).isEqualTo("database-pwd");
        }

        @Test
        @DisplayName("getDatabasePassword should fallback to APP_DB_PASSWORD")
        void getDatabasePasswordShouldFallbackToAppDbPassword() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("APP_DB_PASSWORD"), "app-db-pwd");

            // When
            String result = secretService.getDatabasePassword();

            // Then
            assertThat(result).isEqualTo("app-db-pwd");
        }

        @Test
        @DisplayName("getDatabasePassword should return null when no password found")
        void getDatabasePasswordShouldReturnNullWhenNoPasswordFound() {
            // When
            String result = secretService.getDatabasePassword();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getDatabaseUsername should try POSTGRES_USER first")
        void getDatabaseUsernameShouldTryPostgresUserFirst() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("POSTGRES_USER"), "postgres-user");

            // When
            String result = secretService.getDatabaseUsername();

            // Then
            assertThat(result).isEqualTo("postgres-user");
        }

        @Test
        @DisplayName("getDatabaseUsername should fallback to DATABASE_USER")
        void getDatabaseUsernameShouldFallbackToDatabaseUser() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("DATABASE_USER"), "database-user");

            // When
            String result = secretService.getDatabaseUsername();

            // Then
            assertThat(result).isEqualTo("database-user");
        }

        @Test
        @DisplayName("getDatabaseUsername should fallback to APP_DB_USER")
        void getDatabaseUsernameShouldFallbackToAppDbUser() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("APP_DB_USER"), "app-db-user");

            // When
            String result = secretService.getDatabaseUsername();

            // Then
            assertThat(result).isEqualTo("app-db-user");
        }

        @Test
        @DisplayName("getDatabaseName should try POSTGRES_DB first")
        void getDatabaseNameShouldTryPostgresDbFirst() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("POSTGRES_DB"), "checkitout");

            // When
            String result = secretService.getDatabaseName();

            // Then
            assertThat(result).isEqualTo("checkitout");
        }

        @Test
        @DisplayName("getDatabaseName should fallback to DATABASE_NAME")
        void getDatabaseNameShouldFallbackToDatabaseName() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("DATABASE_NAME"), "mydb");

            // When
            String result = secretService.getDatabaseName();

            // Then
            assertThat(result).isEqualTo("mydb");
        }

        @Test
        @DisplayName("getDatabaseName should fallback to DB_NAME")
        void getDatabaseNameShouldFallbackToDbName() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("DB_NAME"), "testdb");

            // When
            String result = secretService.getDatabaseName();

            // Then
            assertThat(result).isEqualTo("testdb");
        }

        @Test
        @DisplayName("getFirebaseServiceAccountJson should return Firebase config")
        void getFirebaseServiceAccountJsonShouldReturnFirebaseConfig() throws IOException {
            // Given
            String configJson = "{\"project_id\": \"firebase-project\"}";
            Files.writeString(tempDir.resolve("FIREBASE_SERVICE_ACCOUNT_JSON"), configJson);

            // When
            String result = secretService.getFirebaseServiceAccountJson();

            // Then
            assertThat(result).isEqualTo(configJson);
        }

        @Test
        @DisplayName("getInstagramClientSecret should return Instagram secret")
        void getInstagramClientSecretShouldReturnInstagramSecret() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("INSTAGRAM_CLIENT_SECRET"), "instagram-secret");

            // When
            String result = secretService.getInstagramClientSecret();

            // Then
            assertThat(result).isEqualTo("instagram-secret");
        }

        @Test
        @DisplayName("getJwtSecret should return JWT secret")
        void getJwtSecretShouldReturnJwtSecret() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("JWT_SECRET"), "jwt-secret-value");

            // When
            String result = secretService.getJwtSecret();

            // Then
            assertThat(result).isEqualTo("jwt-secret-value");
        }

        @Test
        @DisplayName("getCookieHmacSecret should return Cookie HMAC secret")
        void getCookieHmacSecretShouldReturnCookieHmacSecret() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("COOKIE_HMAC_SECRET"), "cookie-hmac-value");

            // When
            String result = secretService.getCookieHmacSecret();

            // Then
            assertThat(result).isEqualTo("cookie-hmac-value");
        }
    }

    @Nested
    @DisplayName("getSecretStatus")
    class GetSecretStatusTests {

        @Test
        @DisplayName("should return status for development mode")
        void shouldReturnStatusForDevelopmentMode() {
            // Given
            ReflectionTestUtils.setField(secretService, "developmentMode", true);

            // When
            ProductionSecretService.SecretSourceStatus status = secretService.getSecretStatus("TEST_SECRET");

            // Then
            assertThat(status.secretName).isEqualTo("TEST_SECRET");
            assertThat(status.developmentMode).isTrue();
            assertThat(status.strategy).isEqualTo("LOCAL_FILES");
            assertThat(status.activeSource).isEqualTo("Local configuration");
        }

        @Test
        @DisplayName("should return status for production mode with mounted secret")
        void shouldReturnStatusForProductionModeWithMountedSecret() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("MOUNTED_SECRET"), "mounted-value");

            // When
            ProductionSecretService.SecretSourceStatus status = secretService.getSecretStatus("MOUNTED_SECRET");

            // Then
            assertThat(status.secretName).isEqualTo("MOUNTED_SECRET");
            assertThat(status.developmentMode).isFalse();
            assertThat(status.strategy).isEqualTo("MOUNTED_VOLUME");
            assertThat(status.mountedAvailable).isTrue();
            assertThat(status.activeSource).isEqualTo("MOUNTED");
            assertThat(status.mountedPath).contains("MOUNTED_SECRET");
        }

        @Test
        @DisplayName("should return status for production mode without mounted secret")
        void shouldReturnStatusForProductionModeWithoutMountedSecret() {
            // When
            ProductionSecretService.SecretSourceStatus status = secretService.getSecretStatus("MISSING_SECRET");

            // Then
            assertThat(status.secretName).isEqualTo("MISSING_SECRET");
            assertThat(status.developmentMode).isFalse();
            assertThat(status.strategy).isEqualTo("MOUNTED_VOLUME");
            assertThat(status.mountedAvailable).isFalse();
            assertThat(status.activeSource).isEqualTo("NONE");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle IO exception when reading secret file")
        void shouldHandleIoExceptionWhenReadingSecretFile() {
            // Given - Set to non-existent path
            ReflectionTestUtils.setField(secretService, "secretsMountPath", "/non/existent/path");

            // When
            String result = secretService.getSecret("ANY_SECRET");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle malformed JSON in secrets.json")
        void shouldHandleMalformedJsonInSecretsJson() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("secrets.json"), "{ invalid json }");

            // When
            String result = secretService.getSecret("SOME_SECRET");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle empty secrets.json")
        void shouldHandleEmptySecretsJson() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("secrets.json"), "{}");

            // When
            String result = secretService.getSecret("MISSING_KEY");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle empty secrets.properties")
        void shouldHandleEmptySecretsProperties() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("secrets.properties"), "");

            // When
            String result = secretService.getSecret("MISSING_KEY");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle empty secrets.env")
        void shouldHandleEmptySecretsEnv() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("secrets.env"), "");

            // When
            String result = secretService.getSecret("MISSING_KEY");

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("SecretSourceStatus")
    class SecretSourceStatusTests {

        @Test
        @DisplayName("should have all fields accessible")
        void shouldHaveAllFieldsAccessible() {
            // Given
            ProductionSecretService.SecretSourceStatus status = new ProductionSecretService.SecretSourceStatus();

            // When
            status.secretName = "TEST";
            status.strategy = "MOUNTED_VOLUME";
            status.developmentMode = false;
            status.mountedAvailable = true;
            status.mountedPath = "/app/config/TEST";
            status.mountedError = null;
            status.activeSource = "MOUNTED";

            // Then
            assertThat(status.secretName).isEqualTo("TEST");
            assertThat(status.strategy).isEqualTo("MOUNTED_VOLUME");
            assertThat(status.developmentMode).isFalse();
            assertThat(status.mountedAvailable).isTrue();
            assertThat(status.mountedPath).isEqualTo("/app/config/TEST");
            assertThat(status.mountedError).isNull();
            assertThat(status.activeSource).isEqualTo("MOUNTED");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle secret with equals sign in value")
        void shouldHandleSecretWithEqualsSignInValue() throws IOException {
            // Given
            String envContent = "SECRET_WITH_EQUALS=value=with=equals";
            Files.writeString(tempDir.resolve("secrets.env"), envContent);

            // When
            String result = secretService.getSecret("SECRET_WITH_EQUALS");

            // Then
            assertThat(result).isEqualTo("value=with=equals");
        }

        @Test
        @DisplayName("should handle secret with special characters")
        void shouldHandleSecretWithSpecialCharacters() throws IOException {
            // Given
            String specialValue = "p@ssw0rd!#$%^&*()_+-=[]{}|;':\",./<>?";
            Files.writeString(tempDir.resolve("SPECIAL_SECRET"), specialValue);

            // When
            String result = secretService.getSecret("SPECIAL_SECRET");

            // Then
            assertThat(result).isEqualTo(specialValue);
        }

        @Test
        @DisplayName("should handle secret with newlines in JSON")
        void shouldHandleSecretWithNewlinesInJson() throws IOException {
            // Given
            String json = "{\"MULTILINE\": \"line1\\nline2\\nline3\"}";
            Files.writeString(tempDir.resolve("secrets.json"), json);

            // When
            String result = secretService.getSecret("MULTILINE");

            // Then
            assertThat(result).isEqualTo("line1\nline2\nline3");
        }

        @Test
        @DisplayName("should handle unicode characters in secret")
        void shouldHandleUnicodeCharactersInSecret() throws IOException {
            // Given
            String unicodeValue = "secret-with-unicode-chars";
            Files.writeString(tempDir.resolve("UNICODE_SECRET"), unicodeValue);

            // When
            String result = secretService.getSecret("UNICODE_SECRET");

            // Then
            assertThat(result).isEqualTo(unicodeValue);
        }

        @Test
        @DisplayName("should handle very long secret value")
        void shouldHandleVeryLongSecretValue() throws IOException {
            // Given
            String longValue = "a".repeat(10000);
            Files.writeString(tempDir.resolve("LONG_SECRET"), longValue);

            // When
            String result = secretService.getSecret("LONG_SECRET");

            // Then
            assertThat(result).hasSize(10000);
            assertThat(result).isEqualTo(longValue);
        }

        @Test
        @DisplayName("should handle empty secret value")
        void shouldHandleEmptySecretValue() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("EMPTY_SECRET"), "");

            // When
            String result = secretService.getSecret("EMPTY_SECRET");

            // Then
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"POSTGRES_USER", "DATABASE_USER", "APP_DB_USER"})
        @DisplayName("should handle all database user secret names in development mode")
        void shouldHandleAllDatabaseUserSecretNamesInDevMode(String secretName) {
            // Given
            ReflectionTestUtils.setField(secretService, "developmentMode", true);
            ReflectionTestUtils.setField(secretService, "localDbUsername", "test-user");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("test-user");
        }

        @ParameterizedTest
        @ValueSource(strings = {"POSTGRES_PASSWORD", "DATABASE_PASSWORD", "APP_DB_PASSWORD"})
        @DisplayName("should handle all database password secret names in development mode")
        void shouldHandleAllDatabasePasswordSecretNamesInDevMode(String secretName) {
            // Given
            ReflectionTestUtils.setField(secretService, "developmentMode", true);
            ReflectionTestUtils.setField(secretService, "localDbPassword", "test-password");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("test-password");
        }

        @ParameterizedTest
        @ValueSource(strings = {"POSTGRES_DB", "DATABASE_NAME", "DB_NAME"})
        @DisplayName("should handle all database name secret names in development mode")
        void shouldHandleAllDatabaseNameSecretNamesInDevMode(String secretName) {
            // Given
            ReflectionTestUtils.setField(secretService, "developmentMode", true);
            when(env.getProperty("spring.datasource.url", ""))
                .thenReturn("jdbc:postgresql://localhost:5432/test_db");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("test_db");
        }
    }

    @Nested
    @DisplayName("File Format Priority")
    class FileFormatPriorityTests {

        @Test
        @DisplayName("should check individual file before JSON")
        void shouldCheckIndividualFileBeforeJson() throws IOException {
            // Given
            String secretName = "PRIORITY_TEST";
            Files.writeString(tempDir.resolve(secretName), "from-individual-file");
            Files.writeString(tempDir.resolve("secrets.json"),
                "{\"" + secretName + "\": \"from-json\"}");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("from-individual-file");
        }

        @Test
        @DisplayName("should check JSON before properties when no individual file")
        void shouldCheckJsonBeforePropertiesWhenNoIndividualFile() throws IOException {
            // Given
            String secretName = "JSON_PRIORITY_TEST";
            Files.writeString(tempDir.resolve("secrets.json"),
                "{\"" + secretName + "\": \"from-json\"}");
            Files.writeString(tempDir.resolve("secrets.properties"),
                secretName + "=from-properties");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("from-json");
        }

        @Test
        @DisplayName("should check properties before env when no JSON")
        void shouldCheckPropertiesBeforeEnvWhenNoJson() throws IOException {
            // Given
            String secretName = "PROPS_PRIORITY_TEST";
            Files.writeString(tempDir.resolve("secrets.properties"),
                secretName + "=from-properties");
            Files.writeString(tempDir.resolve("secrets.env"),
                secretName + "=from-env");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("from-properties");
        }

        @Test
        @DisplayName("should use env file as last resort")
        void shouldUseEnvFileAsLastResort() throws IOException {
            // Given
            String secretName = "ENV_ONLY_TEST";
            Files.writeString(tempDir.resolve("secrets.env"),
                secretName + "=from-env-only");

            // When
            String result = secretService.getSecret(secretName);

            // Then
            assertThat(result).isEqualTo("from-env-only");
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCacheTests {

        @Test
        @DisplayName("should clear all cached secrets")
        void shouldClearAllCachedSecrets() throws IOException {
            // Given
            Files.writeString(tempDir.resolve("SECRET_A"), "value-a");
            Files.writeString(tempDir.resolve("SECRET_B"), "value-b");
            secretService.getSecret("SECRET_A");
            secretService.getSecret("SECRET_B");

            // Modify files
            Files.writeString(tempDir.resolve("SECRET_A"), "new-value-a");
            Files.writeString(tempDir.resolve("SECRET_B"), "new-value-b");

            // When
            secretService.clearCache();
            String resultA = secretService.getSecret("SECRET_A");
            String resultB = secretService.getSecret("SECRET_B");

            // Then
            assertThat(resultA).isEqualTo("new-value-a");
            assertThat(resultB).isEqualTo("new-value-b");
        }

        @Test
        @DisplayName("should not throw when cache is already empty")
        void shouldNotThrowWhenCacheIsAlreadyEmpty() {
            // When/Then - should not throw
            assertThatCode(() -> secretService.clearCache()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("should handle concurrent secret reads")
        void shouldHandleConcurrentSecretReads() throws Exception {
            // Given
            Files.writeString(tempDir.resolve("CONCURRENT_SECRET"), "concurrent-value");

            // When - Multiple threads reading same secret
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(10);
            java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < 100; i++) {
                futures.add(executor.submit(() -> secretService.getSecret("CONCURRENT_SECRET")));
            }

            executor.shutdown();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            // Then
            for (java.util.concurrent.Future<String> future : futures) {
                assertThat(future.get()).isEqualTo("concurrent-value");
            }
        }

        @Test
        @DisplayName("should handle concurrent cache clearing")
        void shouldHandleConcurrentCacheClearing() throws Exception {
            // Given
            Files.writeString(tempDir.resolve("CLEAR_TEST_SECRET"), "clear-test-value");

            // When - Multiple threads clearing cache and reading
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(5);
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < 50; i++) {
                if (i % 5 == 0) {
                    futures.add(executor.submit(() -> {
                        secretService.clearCache();
                        return null;
                    }));
                } else {
                    futures.add(executor.submit(() -> secretService.getSecret("CLEAR_TEST_SECRET")));
                }
            }

            executor.shutdown();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            // Then - No exceptions should be thrown
            for (java.util.concurrent.Future<?> future : futures) {
                assertThatCode(() -> future.get()).doesNotThrowAnyException();
            }
        }
    }
}
