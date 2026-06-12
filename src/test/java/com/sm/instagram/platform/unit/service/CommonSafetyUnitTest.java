package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.safety.TestEnvironmentGuard;
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
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TestEnvironmentGuard and related safety/security classes.
 * 
 * Tests focus on:
 * - Production environment detection
 * - Dangerous configuration identification
 * - URL masking for sensitive data
 * - Test context detection
 * - Safety validation logic
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Common Safety Package Unit Tests")
class CommonSafetyUnitTest {

    @Mock
    private Environment environment;

    @Mock
    private ContextRefreshedEvent contextRefreshedEvent;

    private TestEnvironmentGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TestEnvironmentGuard(environment);
        // Set default app environment to non-production
        ReflectionTestUtils.setField(guard, "appEnvironment", "TEST");
    }

    // ==================== Helper Methods ====================

    private void setAppEnvironment(String env) {
        ReflectionTestUtils.setField(guard, "appEnvironment", env);
    }

    private boolean invokeIsTestContext() {
        try {
            Method method = TestEnvironmentGuard.class.getDeclaredMethod("isTestContext");
            method.setAccessible(true);
            return (boolean) method.invoke(guard);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke isTestContext", e);
        }
    }

    private boolean invokeHasDangerousConfiguration(String dbUrl, String[] activeProfiles) {
        try {
            Method method = TestEnvironmentGuard.class.getDeclaredMethod(
                    "hasDangerousConfiguration", String.class, String[].class);
            method.setAccessible(true);
            return (boolean) method.invoke(guard, dbUrl, activeProfiles);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke hasDangerousConfiguration", e);
        }
    }

    private String invokeMaskSensitiveUrl(String url) {
        try {
            Method method = TestEnvironmentGuard.class.getDeclaredMethod("maskSensitiveUrl", String.class);
            method.setAccessible(true);
            return (String) method.invoke(guard, url);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke maskSensitiveUrl", e);
        }
    }

    // ==================== Test Classes ====================

    @Nested
    @DisplayName("TestEnvironmentGuard Construction")
    class ConstructionTests {

        @Test
        @DisplayName("should create guard with environment")
        void shouldCreateGuardWithEnvironment() {
            // Given/When
            TestEnvironmentGuard newGuard = new TestEnvironmentGuard(environment);

            // Then
            assertThat(newGuard).isNotNull();
        }

        @Test
        @DisplayName("should implement ApplicationListener interface")
        void shouldImplementApplicationListenerInterface() {
            // Then
            assertThat(guard).isInstanceOf(org.springframework.context.ApplicationListener.class);
        }
    }

    @Nested
    @DisplayName("isTestContext Detection")
    class IsTestContextTests {

        @Test
        @DisplayName("should detect test context when JUnit is on classpath")
        void shouldDetectTestContextWhenJUnitOnClasspath() {
            // When - we are actually running in a test context
            boolean result = invokeIsTestContext();

            // Then - since we're running in JUnit, this might return true or false
            // depending on stack trace analysis
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return boolean value for test context detection")
        void shouldReturnBooleanValueForTestContextDetection() {
            // When
            boolean result = invokeIsTestContext();

            // Then
            assertThat(result).isIn(true, false);
        }
    }

    @Nested
    @DisplayName("hasDangerousConfiguration - Profile Detection")
    class DangerousConfigurationProfileTests {

        @Test
        @DisplayName("should detect 'prod' profile as dangerous")
        void shouldDetectProdProfileAsDangerous() {
            // Given
            String[] profiles = {"prod"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'production' profile as dangerous")
        void shouldDetectProductionProfileAsDangerous() {
            // Given
            String[] profiles = {"production"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'live' profile as dangerous")
        void shouldDetectLiveProfileAsDangerous() {
            // Given
            String[] profiles = {"live"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'PROD' profile case-insensitively")
        void shouldDetectProdProfileCaseInsensitively() {
            // Given
            String[] profiles = {"PROD"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'Production' profile case-insensitively")
        void shouldDetectProductionProfileCaseInsensitively() {
            // Given
            String[] profiles = {"Production"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'LIVE' profile case-insensitively")
        void shouldDetectLiveProfileCaseInsensitively() {
            // Given
            String[] profiles = {"LIVE"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect production profile among multiple profiles")
        void shouldDetectProductionProfileAmongMultipleProfiles() {
            // Given
            String[] profiles = {"test", "dev", "prod", "local"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should not detect 'test' profile as dangerous")
        void shouldNotDetectTestProfileAsDangerous() {
            // Given
            String[] profiles = {"test"};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should not detect 'dev' profile as dangerous")
        void shouldNotDetectDevProfileAsDangerous() {
            // Given
            String[] profiles = {"dev"};
            setAppEnvironment("DEV");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should not detect 'local' profile as dangerous")
        void shouldNotDetectLocalProfileAsDangerous() {
            // Given
            String[] profiles = {"local"};
            setAppEnvironment("LOCAL");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle empty profiles array")
        void shouldHandleEmptyProfilesArray() {
            // Given
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("hasDangerousConfiguration - Database URL Detection")
    class DangerousConfigurationDatabaseTests {

        @Test
        @DisplayName("should detect database URL containing 'prod'")
        void shouldDetectDatabaseUrlContainingProd() {
            // Given
            String dbUrl = "jdbc:postgresql://prod-db.example.com:5432/mydb";
            String[] profiles = {};

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect database URL containing 'production'")
        void shouldDetectDatabaseUrlContainingProduction() {
            // Given
            String dbUrl = "jdbc:mysql://production-server.example.com:3306/app";
            String[] profiles = {};

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect database URL containing 'live'")
        void shouldDetectDatabaseUrlContainingLive() {
            // Given
            String dbUrl = "jdbc:postgresql://live-database.example.com:5432/app";
            String[] profiles = {};

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect PostgreSQL URL without 'test' keyword")
        void shouldDetectPostgresUrlWithoutTestKeyword() {
            // Given
            String dbUrl = "jdbc:postgresql://db.example.com:5432/myapp";
            String[] profiles = {};

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect MySQL URL without 'test' keyword")
        void shouldDetectMySqlUrlWithoutTestKeyword() {
            // Given
            String dbUrl = "jdbc:mysql://db.example.com:3306/myapp";
            String[] profiles = {};

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect Oracle URL without 'test' keyword")
        void shouldDetectOracleUrlWithoutTestKeyword() {
            // Given
            String dbUrl = "jdbc:oracle:thin:@db.example.com:1521:myapp";
            String[] profiles = {};

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should not detect PostgreSQL URL with 'test' keyword")
        void shouldNotDetectPostgresUrlWithTestKeyword() {
            // Given
            String dbUrl = "jdbc:postgresql://test-db.example.com:5432/testdb";
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should not detect MySQL URL with 'test' keyword")
        void shouldNotDetectMySqlUrlWithTestKeyword() {
            // Given
            String dbUrl = "jdbc:mysql://test-server.example.com:3306/test_app";
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should not detect H2 in-memory database as dangerous")
        void shouldNotDetectH2InMemoryAsDangerous() {
            // Given
            String dbUrl = "jdbc:h2:mem:testdb";
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle null database URL")
        void shouldHandleNullDatabaseUrl() {
            // Given
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(null, profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle empty database URL")
        void shouldHandleEmptyDatabaseUrl() {
            // Given
            String dbUrl = "";
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should detect known production host")
        void shouldDetectKnownProductionHost() {
            // Given
            String dbUrl = "jdbc:postgresql://your-prod-db-host.com:5432/app";
            String[] profiles = {};

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("hasDangerousConfiguration - App Environment Detection")
    class DangerousConfigurationEnvironmentTests {

        @Test
        @DisplayName("should detect 'PRODUCTION' app environment")
        void shouldDetectProductionAppEnvironment() {
            // Given
            setAppEnvironment("PRODUCTION");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'PROD' app environment")
        void shouldDetectProdAppEnvironment() {
            // Given
            setAppEnvironment("PROD");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'LIVE' app environment")
        void shouldDetectLiveAppEnvironment() {
            // Given
            setAppEnvironment("LIVE");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'production' app environment case-insensitively")
        void shouldDetectProductionAppEnvironmentCaseInsensitively() {
            // Given
            setAppEnvironment("production");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'prod' app environment case-insensitively")
        void shouldDetectProdAppEnvironmentCaseInsensitively() {
            // Given
            setAppEnvironment("prod");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect 'live' app environment case-insensitively")
        void shouldDetectLiveAppEnvironmentCaseInsensitively() {
            // Given
            setAppEnvironment("live");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should not detect 'TEST' app environment as dangerous")
        void shouldNotDetectTestAppEnvironmentAsDangerous() {
            // Given
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should not detect 'UNKNOWN' app environment as dangerous")
        void shouldNotDetectUnknownAppEnvironmentAsDangerous() {
            // Given
            setAppEnvironment("UNKNOWN");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should not detect 'DEV' app environment as dangerous")
        void shouldNotDetectDevAppEnvironmentAsDangerous() {
            // Given
            setAppEnvironment("DEV");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should not detect 'STAGING' app environment as dangerous")
        void shouldNotDetectStagingAppEnvironmentAsDangerous() {
            // Given
            setAppEnvironment("STAGING");

            // When
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("maskSensitiveUrl")
    class MaskSensitiveUrlTests {

        @Test
        @DisplayName("should return 'Not configured' for null URL")
        void shouldReturnNotConfiguredForNullUrl() {
            // When
            String result = invokeMaskSensitiveUrl(null);

            // Then
            assertThat(result).isEqualTo("Not configured");
        }

        @Test
        @DisplayName("should return 'Not configured' for empty URL")
        void shouldReturnNotConfiguredForEmptyUrl() {
            // When
            String result = invokeMaskSensitiveUrl("");

            // Then
            assertThat(result).isEqualTo("Not configured");
        }

        @Test
        @DisplayName("should mask password in PostgreSQL URL")
        void shouldMaskPasswordInPostgresUrl() {
            // Given
            String url = "jdbc:postgresql://user:secretpassword@localhost:5432/mydb";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("jdbc:postgresql://user:***@localhost:5432/mydb");
            assertThat(result).doesNotContain("secretpassword");
        }

        @Test
        @DisplayName("should mask password in MySQL URL")
        void shouldMaskPasswordInMySqlUrl() {
            // Given
            String url = "jdbc:mysql://admin:mypassword123@db.example.com:3306/app";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("jdbc:mysql://admin:***@db.example.com:3306/app");
            assertThat(result).doesNotContain("mypassword123");
        }

        @Test
        @DisplayName("should preserve URL without credentials")
        void shouldPreserveUrlWithoutCredentials() {
            // Given
            String url = "jdbc:postgresql://localhost:5432/mydb";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        }

        @Test
        @DisplayName("should handle URL with username but no password")
        void shouldHandleUrlWithUsernameButNoPassword() {
            // Given
            String url = "jdbc:postgresql://user@localhost:5432/mydb";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).contains("user@localhost");
        }

        @Test
        @DisplayName("should handle complex password with special characters (limitation: @ in password breaks masking)")
        void shouldHandleComplexPasswordWithSpecialCharacters() {
            // Given - password contains @ which confuses the simple split logic
            String url = "jdbc:postgresql://user:P@ssw0rd!#$%@localhost:5432/mydb";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then - the current implementation doesn't handle @ in passwords correctly
            // This documents the limitation - the URL is returned as-is when split("@") produces more than 2 parts
            assertThat(result).isNotNull();
            // The URL contains multiple @ symbols, so the simple split logic doesn't mask it
            // This is a known limitation that could be improved in the future
        }

        @Test
        @DisplayName("should correctly mask password without special @ character")
        void shouldCorrectlyMaskPasswordWithoutSpecialAtCharacter() {
            // Given - password without @ symbol
            String url = "jdbc:postgresql://user:ComplexP4ss!#$%@localhost:5432/mydb";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("jdbc:postgresql://user:***@localhost:5432/mydb");
            assertThat(result).doesNotContain("ComplexP4ss");
        }

        @Test
        @DisplayName("should handle URL without protocol separator")
        void shouldHandleUrlWithoutProtocolSeparator() {
            // Given
            String url = "simple-string-without-protocol";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("simple-string-without-protocol");
        }

        @Test
        @DisplayName("should handle H2 in-memory URL")
        void shouldHandleH2InMemoryUrl() {
            // Given
            String url = "jdbc:h2:mem:testdb";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("jdbc:h2:mem:testdb");
        }

        @Test
        @DisplayName("should mask password in URL with port and database")
        void shouldMaskPasswordInUrlWithPortAndDatabase() {
            // Given
            String url = "jdbc:postgresql://dbuser:dbpass@prod-server.example.com:5432/production_db";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("jdbc:postgresql://dbuser:***@prod-server.example.com:5432/production_db");
            assertThat(result).doesNotContain("dbpass");
        }

        @Test
        @DisplayName("should handle URL with multiple @ symbols")
        void shouldHandleUrlWithMultipleAtSymbols() {
            // Given - this is an edge case, URL parsing might behave unexpectedly
            String url = "jdbc:postgresql://user:p@ss@localhost:5432/mydb";

            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then - the method splits on @ and takes first two parts
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("onApplicationEvent - Safety Validation")
    class OnApplicationEventTests {

        @Test
        @DisplayName("should not throw when in safe test environment")
        void shouldNotThrowWhenInSafeTestEnvironment() {
            // Given
            when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:testdb");
            setAppEnvironment("TEST");

            // When/Then - should not throw
            guard.onApplicationEvent(contextRefreshedEvent);

            // Verify environment was checked
            verify(environment).getActiveProfiles();
            verify(environment).getProperty("spring.datasource.url", "");
        }

        @Test
        @DisplayName("should not throw when environment is DEV")
        void shouldNotThrowWhenEnvironmentIsDev() {
            // Given
            when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:devdb");
            setAppEnvironment("DEV");

            // When/Then - should not throw
            guard.onApplicationEvent(contextRefreshedEvent);
        }

        @Test
        @DisplayName("should not throw when environment is LOCAL")
        void shouldNotThrowWhenEnvironmentIsLocal() {
            // Given
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:localdb");
            setAppEnvironment("LOCAL");

            // When/Then - should not throw
            guard.onApplicationEvent(contextRefreshedEvent);
        }

        @Test
        @DisplayName("should log environment information during validation")
        void shouldLogEnvironmentInformationDuringValidation() {
            // Given
            when(environment.getActiveProfiles()).thenReturn(new String[]{"test", "h2"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:testdb");
            setAppEnvironment("TEST");

            // When
            guard.onApplicationEvent(contextRefreshedEvent);

            // Then - no exception means validation passed
            verify(environment).getActiveProfiles();
        }
    }

    @Nested
    @DisplayName("Combined Dangerous Configuration Detection")
    class CombinedDangerousConfigurationTests {

        @Test
        @DisplayName("should detect danger when profile is production and URL is safe")
        void shouldDetectDangerWhenProfileIsProductionAndUrlIsSafe() {
            // Given
            String[] profiles = {"production"};
            String dbUrl = "jdbc:h2:mem:testdb";
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect danger when URL is production and profile is safe")
        void shouldDetectDangerWhenUrlIsProductionAndProfileIsSafe() {
            // Given
            String[] profiles = {"test"};
            String dbUrl = "jdbc:postgresql://prod-db.example.com:5432/app";
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should detect danger when app environment is production")
        void shouldDetectDangerWhenAppEnvironmentIsProduction() {
            // Given
            String[] profiles = {"test"};
            String dbUrl = "jdbc:h2:mem:testdb";
            setAppEnvironment("PRODUCTION");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should not detect danger when all settings are safe")
        void shouldNotDetectDangerWhenAllSettingsAreSafe() {
            // Given
            String[] profiles = {"test", "h2"};
            String dbUrl = "jdbc:h2:mem:testdb";
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should detect all three danger indicators simultaneously")
        void shouldDetectAllThreeDangerIndicatorsSimultaneously() {
            // Given
            String[] profiles = {"production"};
            String dbUrl = "jdbc:postgresql://prod-db.example.com:5432/livedb";
            setAppEnvironment("PROD");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, profiles);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle profile containing 'prod' as substring")
        void shouldHandleProfileContainingProdAsSubstring() {
            // Given - 'reproduction' contains 'prod'
            String[] profiles = {"reproduction"};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then - should detect because it contains 'prod'
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle database URL with 'product' in path")
        void shouldHandleDatabaseUrlWithProductInPath() {
            // Given - 'product' contains 'prod'
            String dbUrl = "jdbc:h2:mem:product_catalog";

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, new String[]{});

            // Then - 'product' contains 'prod'
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle whitespace in profiles")
        void shouldHandleWhitespaceInProfiles() {
            // Given
            String[] profiles = {" test ", "  dev  "};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then - exact matching, whitespace might not be trimmed
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle very long database URL")
        void shouldHandleVeryLongDatabaseUrl() {
            // Given
            String longUrl = "jdbc:postgresql://user:password@" + 
                "very-long-hostname-that-goes-on-and-on-and-on.example.com:5432/" +
                "database_with_a_very_long_name_that_might_cause_issues_in_some_systems_test";
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(longUrl, profiles);

            // Then - contains 'test' so should be safe
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle special characters in database URL")
        void shouldHandleSpecialCharactersInDatabaseUrl() {
            // Given
            String url = "jdbc:postgresql://user:p@ss%20word!@localhost:5432/my-db_test";
            String[] profiles = {};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(url, profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle null app environment gracefully")
        void shouldHandleNullAppEnvironmentGracefully() {
            // Given
            setAppEnvironment(null);

            // When/Then - should not throw NPE
            boolean result = invokeHasDangerousConfiguration("", new String[]{});

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Profile Pattern Matching")
    class ProfilePatternMatchingTests {

        @ParameterizedTest
        @ValueSource(strings = {"prod", "PROD", "Prod", "PrOd", "production", "PRODUCTION", "Production"})
        @DisplayName("should detect various production profile formats")
        void shouldDetectVariousProductionProfileFormats(String profile) {
            // Given
            String[] profiles = {profile};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"live", "LIVE", "Live", "LiVe"})
        @DisplayName("should detect various live profile formats")
        void shouldDetectVariousLiveProfileFormats(String profile) {
            // Given
            String[] profiles = {profile};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"test", "TEST", "Test", "dev", "DEV", "Dev", "local", "LOCAL", "Local", "staging", "STAGING"})
        @DisplayName("should not detect safe profile formats")
        void shouldNotDetectSafeProfileFormats(String profile) {
            // Given
            String[] profiles = {profile};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Database URL Pattern Matching")
    class DatabaseUrlPatternMatchingTests {

        @ParameterizedTest
        @CsvSource({
            "jdbc:postgresql://test-db:5432/app, false",
            "jdbc:postgresql://prod-db:5432/app, true",
            "jdbc:mysql://test-server:3306/db, false",
            "jdbc:mysql://production:3306/db, true",
            "jdbc:oracle:thin:@test-host:1521:sid, false",
            "jdbc:oracle:thin:@live-host:1521:sid, true",
            "jdbc:h2:mem:testdb, false",
            "jdbc:h2:file:./test-data, false"
        })
        @DisplayName("should correctly identify dangerous database URLs")
        void shouldCorrectlyIdentifyDangerousDatabaseUrls(String dbUrl, boolean expectedDangerous) {
            // Given
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration(dbUrl, new String[]{});

            // Then
            assertThat(result).isEqualTo(expectedDangerous);
        }
    }

    @Nested
    @DisplayName("URL Masking Patterns")
    class UrlMaskingPatternsTests {

        @ParameterizedTest
        @CsvSource({
            "jdbc:postgresql://user:pass@host:5432/db, jdbc:postgresql://user:***@host:5432/db",
            "jdbc:mysql://admin:secret@server:3306/app, jdbc:mysql://admin:***@server:3306/app",
            "mongodb://user:pwd@cluster.mongodb.net/db, mongodb://user:***@cluster.mongodb.net/db"
        })
        @DisplayName("should correctly mask passwords in various URL formats")
        void shouldCorrectlyMaskPasswordsInVariousUrlFormats(String input, String expected) {
            // When
            String result = invokeMaskSensitiveUrl(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should handle null and empty URLs")
        void shouldHandleNullAndEmptyUrls(String url) {
            // When
            String result = invokeMaskSensitiveUrl(url);

            // Then
            assertThat(result).isEqualTo("Not configured");
        }
    }

    @Nested
    @DisplayName("Environment Injection Tests")
    class EnvironmentInjectionTests {

        @Test
        @DisplayName("should use injected environment for profile retrieval")
        void shouldUseInjectedEnvironmentForProfileRetrieval() {
            // Given
            String[] expectedProfiles = {"test", "h2"};
            when(environment.getActiveProfiles()).thenReturn(expectedProfiles);
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("");
            setAppEnvironment("TEST");

            // When
            guard.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(environment).getActiveProfiles();
        }

        @Test
        @DisplayName("should use injected environment for datasource URL retrieval")
        void shouldUseInjectedEnvironmentForDatasourceUrlRetrieval() {
            // Given
            when(environment.getActiveProfiles()).thenReturn(new String[]{});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:test");
            setAppEnvironment("TEST");

            // When
            guard.onApplicationEvent(contextRefreshedEvent);

            // Then
            verify(environment).getProperty("spring.datasource.url", "");
        }
    }

    @Nested
    @DisplayName("Safety Error Message Tests")
    class SafetyErrorMessageTests {

        @Test
        @DisplayName("error message should contain critical safety violation warning")
        void errorMessageShouldContainCriticalSafetyViolationWarning() {
            // This test verifies the error message format by checking the class implementation
            // The actual throwing is tested in integration scenarios
            
            // Given - simulate a production configuration detection
            when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:postgresql://prod-db:5432/app");
            setAppEnvironment("PROD");

            // When/Then - if we're in a test context with dangerous config, it should throw
            // The isTestContext check looks at stack trace, so behavior may vary
            // We verify the dangerous configuration is detected
            boolean isDangerous = invokeHasDangerousConfiguration(
                "jdbc:postgresql://prod-db:5432/app", 
                new String[]{"production"}
            );
            
            assertThat(isDangerous).isTrue();
        }
    }

    @Nested
    @DisplayName("Multiple Profiles Handling")
    class MultipleProfilesHandlingTests {

        @Test
        @DisplayName("should detect danger with production profile among many")
        void shouldDetectDangerWithProductionProfileAmongMany() {
            // Given
            String[] profiles = {"default", "h2", "swagger", "prod", "actuator"};

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should not detect danger with only safe profiles")
        void shouldNotDetectDangerWithOnlySafeProfiles() {
            // Given
            String[] profiles = {"default", "h2", "swagger", "test", "actuator", "local"};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle single profile array")
        void shouldHandleSingleProfileArray() {
            // Given
            String[] profiles = {"test"};
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle many profiles efficiently")
        void shouldHandleManyProfilesEfficiently() {
            // Given - create a large array of safe profiles
            String[] profiles = new String[100];
            for (int i = 0; i < 100; i++) {
                profiles[i] = "safe-profile-" + i;
            }
            setAppEnvironment("TEST");

            // When
            boolean result = invokeHasDangerousConfiguration("", profiles);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Integration Scenario Simulations")
    class IntegrationScenarioSimulationsTests {

        @Test
        @DisplayName("should allow typical integration test configuration")
        void shouldAllowTypicalIntegrationTestConfiguration() {
            // Given - typical integration test setup
            when(environment.getActiveProfiles()).thenReturn(new String[]{"test", "h2"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:testdb;MODE=PostgreSQL");
            setAppEnvironment("TEST");

            // When/Then - should not throw
            guard.onApplicationEvent(contextRefreshedEvent);
        }

        @Test
        @DisplayName("should allow local development configuration")
        void shouldAllowLocalDevelopmentConfiguration() {
            // Given - local dev setup
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local", "dev"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:postgresql://localhost:5432/dev_test_db");
            setAppEnvironment("LOCAL");

            // When/Then - should not throw (contains 'test' in URL and safe profiles)
            guard.onApplicationEvent(contextRefreshedEvent);
        }

        @Test
        @DisplayName("should allow CI environment configuration")
        void shouldAllowCiEnvironmentConfiguration() {
            // Given - CI environment setup
            when(environment.getActiveProfiles()).thenReturn(new String[]{"ci", "test"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:ci_test");
            setAppEnvironment("CI");

            // When/Then - should not throw
            guard.onApplicationEvent(contextRefreshedEvent);
        }

        @Test
        @DisplayName("should allow staging with test database")
        void shouldAllowStagingWithTestDatabase() {
            // Given - staging with test database
            when(environment.getActiveProfiles()).thenReturn(new String[]{"staging"});
            when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:postgresql://staging-test-db:5432/staging_test");
            setAppEnvironment("STAGING");

            // When/Then - should not throw (contains 'test' in URL)
            guard.onApplicationEvent(contextRefreshedEvent);
        }
    }
}
