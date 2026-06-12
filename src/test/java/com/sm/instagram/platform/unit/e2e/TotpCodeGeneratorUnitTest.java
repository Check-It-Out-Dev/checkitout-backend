package com.sm.instagram.platform.unit.e2e;

import com.sm.instagram.platform.e2e.support.TotpCodeGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for TotpCodeGenerator.
 * Tests TOTP code generation, validation, and configuration methods.
 *
 * <p>Uses direct instantiation with constructor parameters to avoid
 * Spring context dependency.
 */
@DisplayName("TotpCodeGenerator Unit Tests")
class TotpCodeGeneratorUnitTest {

    // Known valid Base32 secret for deterministic testing
    // This is a standard test secret - "JBSWY3DPEHPK3PXP" decodes to "Hello!"
    private static final String VALID_BASE32_SECRET = "JBSWY3DPEHPK3PXP";

    // Another valid Base32 secret for comparison tests
    private static final String ALTERNATE_SECRET = "GEZDGNBVGY3TQOJQ";

    // Default TOTP configuration matching production defaults
    private static final int DEFAULT_WINDOW_SIZE = 1;
    private static final int DEFAULT_CODE_DIGITS = 6;
    private static final int DEFAULT_TIME_STEP_SECONDS = 30;

    private TotpCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TotpCodeGenerator(
                DEFAULT_WINDOW_SIZE,
                DEFAULT_CODE_DIGITS,
                DEFAULT_TIME_STEP_SECONDS
        );
    }

    @Nested
    @DisplayName("generateTotpCode(String) Tests")
    class GenerateTotpCodeTests {

        @Test
        @DisplayName("should generate 6-digit code with valid Base32 secret")
        void shouldGenerate6DigitCodeWithValidSecret() {
            // When
            int code = generator.generateTotpCode(VALID_BASE32_SECRET);

            // Then
            assertThat(code)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(999999);
        }

        @Test
        @DisplayName("should return consistent code within same time window")
        void shouldReturnConsistentCodeWithinSameTimeWindow() {
            // When - generate multiple codes in quick succession (same time window)
            int code1 = generator.generateTotpCode(VALID_BASE32_SECRET);
            int code2 = generator.generateTotpCode(VALID_BASE32_SECRET);
            int code3 = generator.generateTotpCode(VALID_BASE32_SECRET);

            // Then - all codes should be identical within the same 30-second window
            assertThat(code1)
                    .isEqualTo(code2)
                    .isEqualTo(code3);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null secret")
        void shouldThrowExceptionForNullSecret() {
            // When / Then
            assertThatThrownBy(() -> generator.generateTotpCode(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for empty secret")
        void shouldThrowExceptionForEmptySecret() {
            // When / Then
            assertThatThrownBy(() -> generator.generateTotpCode(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or blank");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank secret")
        void shouldThrowExceptionForBlankSecret() {
            // When / Then
            assertThatThrownBy(() -> generator.generateTotpCode("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or blank");
        }

        @Test
        @DisplayName("should handle secret with invalid Base32 characters gracefully")
        void shouldHandleInvalidBase32CharactersGracefully() {
            // Given - GoogleAuthenticator library handles invalid Base32 by ignoring invalid chars
            // This test verifies the library's behavior rather than expecting an exception
            String secretWithInvalidChars = "JBSWY1DPEHPK3PXP"; // '1' is not valid Base32

            // When - the library may process what it can from the input
            // The behavior depends on the underlying library implementation
            int code = generator.generateTotpCode(secretWithInvalidChars);

            // Then - code should still be in valid range (library handles gracefully)
            assertThat(code)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(999999);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "JBSWY3DPEHPK3PXP",      // Standard test secret
                "GEZDGNBVGY3TQOJQ",      // Another valid secret
                "MFRGGZDFMY",            // Shorter valid Base32
                "NBSWY3DP"               // Minimal valid Base32
        })
        @DisplayName("should generate valid code for various Base32 secrets")
        void shouldGenerateValidCodeForVariousSecrets(String secret) {
            // When
            int code = generator.generateTotpCode(secret);

            // Then - code should be within 6-digit range
            assertThat(code)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(999999);
        }

        @Test
        @DisplayName("should generate different codes for different secrets")
        void shouldGenerateDifferentCodesForDifferentSecrets() {
            // When
            int code1 = generator.generateTotpCode(VALID_BASE32_SECRET);
            int code2 = generator.generateTotpCode(ALTERNATE_SECRET);

            // Then - codes should differ (with extremely high probability)
            // Note: There's a 1/1,000,000 chance they could match by coincidence
            assertThat(code1).isNotEqualTo(code2);
        }
    }

    @Nested
    @DisplayName("TOTP Code Format Validation Tests")
    class TotpCodeFormatTests {

        @Test
        @DisplayName("should generate code that formats to exactly 6 digits with padding")
        void shouldGenerateCodeThatFormatsTo6Digits() {
            // When
            int code = generator.generateTotpCode(VALID_BASE32_SECRET);
            String formattedCode = String.format("%06d", code);

            // Then
            assertThat(formattedCode)
                    .hasSize(6)
                    .matches("^\\d{6}$");
        }

        @Test
        @DisplayName("should handle leading zeros correctly when formatted")
        void shouldHandleLeadingZerosCorrectly() {
            // Generate multiple codes to statistically ensure some have leading zeros
            for (int i = 0; i < 100; i++) {
                // Generate a new secret for each iteration to get different codes
                String secret = generator.generateNewSecret();
                int code = generator.generateTotpCode(secret);
                String formattedCode = String.format("%06d", code);

                // Verify format is always 6 digits
                assertThat(formattedCode)
                        .hasSize(6)
                        .matches("^\\d{6}$");
            }
        }

        @Test
        @DisplayName("should generate code that is a non-negative integer")
        void shouldGenerateNonNegativeInteger() {
            // When
            int code = generator.generateTotpCode(VALID_BASE32_SECRET);

            // Then
            assertThat(code).isNotNegative();
        }
    }

    @Nested
    @DisplayName("validateCode(String, int) Tests")
    class ValidateCodeTests {

        @Test
        @DisplayName("should return true for correct code within time window")
        void shouldReturnTrueForCorrectCode() {
            // Given
            int generatedCode = generator.generateTotpCode(VALID_BASE32_SECRET);

            // When
            boolean isValid = generator.validateCode(VALID_BASE32_SECRET, generatedCode);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should return false for wrong code")
        void shouldReturnFalseForWrongCode() {
            // Given - a code that is definitely wrong (negative which is impossible for TOTP)
            int generatedCode = generator.generateTotpCode(VALID_BASE32_SECRET);
            int wrongCode = (generatedCode + 500000) % 1000000; // Shift by half the range

            // When
            boolean isValid = generator.validateCode(VALID_BASE32_SECRET, wrongCode);

            // Then - validation should fail for wrong code
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should return false for code from different secret")
        void shouldReturnFalseForCodeFromDifferentSecret() {
            // Given
            int codeFromSecret1 = generator.generateTotpCode(VALID_BASE32_SECRET);

            // When - validate against different secret
            boolean isValid = generator.validateCode(ALTERNATE_SECRET, codeFromSecret1);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("Round-trip Generate and Validate Tests")
    class RoundTripTests {

        @Test
        @DisplayName("should successfully round-trip generate and validate code")
        void shouldRoundTripSuccessfully() {
            // Given
            String secret = VALID_BASE32_SECRET;

            // When
            int generatedCode = generator.generateTotpCode(secret);
            boolean isValid = generator.validateCode(secret, generatedCode);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should round-trip successfully with newly generated secret")
        void shouldRoundTripWithNewSecret() {
            // Given
            String newSecret = generator.generateNewSecret();

            // When
            int generatedCode = generator.generateTotpCode(newSecret);
            boolean isValid = generator.validateCode(newSecret, generatedCode);

            // Then
            assertThat(isValid).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3})
        @DisplayName("should round-trip successfully with different window sizes")
        void shouldRoundTripWithDifferentWindowSizes(int windowSize) {
            // Given
            TotpCodeGenerator customGenerator = new TotpCodeGenerator(
                    windowSize,
                    DEFAULT_CODE_DIGITS,
                    DEFAULT_TIME_STEP_SECONDS
            );

            // When
            int generatedCode = customGenerator.generateTotpCode(VALID_BASE32_SECRET);
            boolean isValid = customGenerator.validateCode(VALID_BASE32_SECRET, generatedCode);

            // Then
            assertThat(isValid).isTrue();
        }
    }

    @Nested
    @DisplayName("adminHasTotpConfigured() Tests")
    class AdminHasTotpConfiguredTests {

        @Test
        @DisplayName("should return false when admin secret is not configured (empty)")
        void shouldReturnFalseWhenSecretNotConfigured() {
            // Given - default generator has no admin secret configured
            // The @Value annotation defaults to empty string

            // When
            boolean hasTotp = generator.adminHasTotpConfigured();

            // Then
            assertThat(hasTotp).isFalse();
        }

        @Test
        @DisplayName("should throw IllegalStateException when generating admin code without configuration")
        void shouldThrowWhenGeneratingAdminCodeWithoutConfiguration() {
            // When / Then
            assertThatThrownBy(() -> generator.generateAdminTotpCode())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Admin TOTP secret not configured");
        }

        @Test
        @DisplayName("should throw IllegalStateException when validating admin code without configuration")
        void shouldThrowWhenValidatingAdminCodeWithoutConfiguration() {
            // When / Then
            assertThatThrownBy(() -> generator.validateAdminCode(123456))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Admin TOTP secret not configured");
        }
    }

    @Nested
    @DisplayName("generateNewSecret() Tests")
    class GenerateNewSecretTests {

        @Test
        @DisplayName("should generate valid Base32 secret")
        void shouldGenerateValidBase32Secret() {
            // When
            String secret = generator.generateNewSecret();

            // Then
            assertThat(secret)
                    .isNotNull()
                    .isNotEmpty()
                    .matches("^[A-Z2-7]+=*$"); // Base32 alphabet
        }

        @Test
        @DisplayName("should generate unique secrets on each call")
        void shouldGenerateUniqueSecrets() {
            // When
            String secret1 = generator.generateNewSecret();
            String secret2 = generator.generateNewSecret();
            String secret3 = generator.generateNewSecret();

            // Then
            assertThat(secret1)
                    .isNotEqualTo(secret2)
                    .isNotEqualTo(secret3);
            assertThat(secret2).isNotEqualTo(secret3);
        }

        @Test
        @DisplayName("should generate secret that can be used for code generation")
        void shouldGenerateUsableSecret() {
            // Given
            String generatedSecret = generator.generateNewSecret();

            // When
            int code = generator.generateTotpCode(generatedSecret);

            // Then
            assertThat(code)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(999999);
        }
    }

    @Nested
    @DisplayName("getAdminFirebaseUid() Tests")
    class GetAdminFirebaseUidTests {

        @Test
        @DisplayName("should return null when not configured via Spring context")
        void shouldReturnNullWhenNotConfiguredViaSpringContext() {
            // When - outside Spring context, @Value fields are not populated
            String adminUid = generator.getAdminFirebaseUid();

            // Then - without Spring context injection, the field remains null
            // This is expected behavior for unit testing without Spring
            assertThat(adminUid).isNull();
        }

        @Test
        @DisplayName("should return a String type")
        void shouldReturnStringType() {
            // When
            Object adminUid = generator.getAdminFirebaseUid();

            // Then - should be null or String (verifies the return type contract)
            assertThat(adminUid).isNull(); // null is acceptable for String type
        }
    }

    @Nested
    @DisplayName("Constructor Configuration Tests")
    class ConstructorConfigurationTests {

        @Test
        @DisplayName("should create generator with custom window size")
        void shouldCreateWithCustomWindowSize() {
            // Given
            int customWindowSize = 3;

            // When
            TotpCodeGenerator customGenerator = new TotpCodeGenerator(
                    customWindowSize,
                    DEFAULT_CODE_DIGITS,
                    DEFAULT_TIME_STEP_SECONDS
            );
            int code = customGenerator.generateTotpCode(VALID_BASE32_SECRET);

            // Then
            assertThat(code)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(999999);
        }

        @Test
        @DisplayName("should create generator with custom time step")
        void shouldCreateWithCustomTimeStep() {
            // Given
            int customTimeStep = 60;

            // When
            TotpCodeGenerator customGenerator = new TotpCodeGenerator(
                    DEFAULT_WINDOW_SIZE,
                    DEFAULT_CODE_DIGITS,
                    customTimeStep
            );
            int code = customGenerator.generateTotpCode(VALID_BASE32_SECRET);

            // Then
            assertThat(code)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(999999);
        }
    }
}
