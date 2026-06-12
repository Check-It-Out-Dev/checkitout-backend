package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.kms.v1.*;
import com.google.protobuf.ByteString;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.security.KMSValidationService;
import com.sm.instagram.platform.common.security.TokenEncryptionService;
import com.sm.instagram.platform.common.security.TotpEncryptionService;
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
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for encryption services:
 * - TokenEncryptionService
 * - TotpEncryptionService
 * - KMSValidationService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Encryption Services Unit Tests")
class EncryptionServicesUnitTest {

    // =====================================================================
    // TokenEncryptionService Tests
    // =====================================================================

    @Nested
    @DisplayName("TokenEncryptionService")
    class TokenEncryptionServiceTests {

        @Mock
        private KMSValidationService kmsService;

        private TokenEncryptionService tokenEncryptionService;

        @BeforeEach
        void setUp() {
            tokenEncryptionService = new TokenEncryptionService();
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", true);
        }

        @Nested
        @DisplayName("encryptToken()")
        class EncryptTokenTests {

            @Test
            @DisplayName("should encrypt token successfully when KMS is enabled")
            void shouldEncryptTokenSuccessfully() {
                // Given
                String plainToken = "instagram-access-token-12345";
                String encryptedToken = "base64-encrypted-token-data";
                when(kmsService.encryptToken(plainToken)).thenReturn(encryptedToken);

                // When
                String result = tokenEncryptionService.encryptToken(plainToken);

                // Then
                assertThat(result).isEqualTo(encryptedToken);
                verify(kmsService).encryptToken(plainToken);
            }

            @Test
            @DisplayName("should return null for null input token")
            void shouldReturnNullForNullToken() {
                // When
                String result = tokenEncryptionService.encryptToken(null);

                // Then
                assertThat(result).isNull();
                verify(kmsService, never()).encryptToken(anyString());
            }

            @Test
            @DisplayName("should return null for empty input token")
            void shouldReturnNullForEmptyToken() {
                // When
                String result = tokenEncryptionService.encryptToken("");

                // Then
                assertThat(result).isNull();
                verify(kmsService, never()).encryptToken(anyString());
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when KMS is disabled")
            void shouldThrowExceptionWhenKmsDisabled() {
                // Given
                ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", false);

                // When/Then
                assertThatThrownBy(() -> tokenEncryptionService.encryptToken("some-token"))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.encryption_disabled");
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when KMS service throws exception")
            void shouldThrowExceptionWhenKmsServiceFails() {
                // Given
                when(kmsService.encryptToken(anyString()))
                        .thenThrow(new RuntimeException("KMS encryption error"));

                // When/Then
                assertThatThrownBy(() -> tokenEncryptionService.encryptToken("some-token"))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.encryption_failed");
            }

            @Test
            @DisplayName("should handle tokens with special characters")
            void shouldHandleTokensWithSpecialCharacters() {
                // Given
                String specialToken = "token=abc&user=test@example.com#fragment%20encoded";
                String encryptedResult = "encrypted-special";
                when(kmsService.encryptToken(specialToken)).thenReturn(encryptedResult);

                // When
                String result = tokenEncryptionService.encryptToken(specialToken);

                // Then
                assertThat(result).isEqualTo(encryptedResult);
                verify(kmsService).encryptToken(specialToken);
            }

            @Test
            @DisplayName("should handle very long tokens")
            void shouldHandleVeryLongTokens() {
                // Given
                String longToken = "a".repeat(5000);
                String encryptedResult = "encrypted-long-token";
                when(kmsService.encryptToken(longToken)).thenReturn(encryptedResult);

                // When
                String result = tokenEncryptionService.encryptToken(longToken);

                // Then
                assertThat(result).isEqualTo(encryptedResult);
                verify(kmsService).encryptToken(longToken);
            }

            @Test
            @DisplayName("should handle Unicode characters in token")
            void shouldHandleUnicodeCharacters() {
                // Given
                String unicodeToken = "token-with-unicode-\u00e9\u00f1\u4e2d\u6587";
                String encryptedResult = "encrypted-unicode";
                when(kmsService.encryptToken(unicodeToken)).thenReturn(encryptedResult);

                // When
                String result = tokenEncryptionService.encryptToken(unicodeToken);

                // Then
                assertThat(result).isEqualTo(encryptedResult);
            }
        }

        @Nested
        @DisplayName("decryptToken()")
        class DecryptTokenTests {

            @Test
            @DisplayName("should decrypt token successfully when KMS is enabled")
            void shouldDecryptTokenSuccessfully() {
                // Given
                String encryptedToken = "base64-encrypted-token";
                String plainToken = "instagram-access-token-12345";
                when(kmsService.decryptToken(encryptedToken)).thenReturn(plainToken);

                // When
                String result = tokenEncryptionService.decryptToken(encryptedToken);

                // Then
                assertThat(result).isEqualTo(plainToken);
                verify(kmsService).decryptToken(encryptedToken);
            }

            @Test
            @DisplayName("should return null for null encrypted token")
            void shouldReturnNullForNullToken() {
                // When
                String result = tokenEncryptionService.decryptToken(null);

                // Then
                assertThat(result).isNull();
                verify(kmsService, never()).decryptToken(anyString());
            }

            @Test
            @DisplayName("should return null for empty encrypted token")
            void shouldReturnNullForEmptyToken() {
                // When
                String result = tokenEncryptionService.decryptToken("");

                // Then
                assertThat(result).isNull();
                verify(kmsService, never()).decryptToken(anyString());
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when KMS is disabled")
            void shouldThrowExceptionWhenKmsDisabled() {
                // Given
                ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", false);

                // When/Then
                assertThatThrownBy(() -> tokenEncryptionService.decryptToken("encrypted-token"))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.decryption_disabled");
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when KMS service throws exception")
            void shouldThrowExceptionWhenKmsServiceFails() {
                // Given
                when(kmsService.decryptToken(anyString()))
                        .thenThrow(new RuntimeException("KMS decryption error"));

                // When/Then
                assertThatThrownBy(() -> tokenEncryptionService.decryptToken("encrypted-token"))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.decryption_failed");
            }

            @Test
            @DisplayName("should handle base64 encoded encrypted tokens")
            void shouldHandleBase64EncodedTokens() {
                // Given
                String base64Encrypted = Base64.getEncoder().encodeToString("encrypted-data".getBytes());
                String plainToken = "decrypted-token";
                when(kmsService.decryptToken(base64Encrypted)).thenReturn(plainToken);

                // When
                String result = tokenEncryptionService.decryptToken(base64Encrypted);

                // Then
                assertThat(result).isEqualTo(plainToken);
            }
        }

        @Nested
        @DisplayName("isEncryptionEnabled()")
        class IsEncryptionEnabledTests {

            @Test
            @DisplayName("should return true when KMS is enabled")
            void shouldReturnTrueWhenEnabled() {
                // Given
                ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", true);

                // When/Then
                assertThat(tokenEncryptionService.isEncryptionEnabled()).isTrue();
            }

            @Test
            @DisplayName("should return false when KMS is disabled")
            void shouldReturnFalseWhenDisabled() {
                // Given
                ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", false);

                // When/Then
                assertThat(tokenEncryptionService.isEncryptionEnabled()).isFalse();
            }
        }

        @Nested
        @DisplayName("validateKMSService()")
        class ValidateKMSServiceTests {

            @Test
            @DisplayName("should return true when encrypt/decrypt round-trip succeeds")
            void shouldReturnTrueWhenRoundTripSucceeds() {
                // Given
                when(kmsService.encryptToken(anyString())).thenAnswer(inv -> "encrypted_" + inv.getArgument(0));
                when(kmsService.decryptToken(anyString())).thenAnswer(inv -> {
                    String encrypted = inv.getArgument(0);
                    return encrypted.replace("encrypted_", "");
                });

                // When
                boolean result = tokenEncryptionService.validateKMSService();

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false when KMS is disabled")
            void shouldReturnFalseWhenKmsDisabled() {
                // Given
                ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", false);

                // When
                boolean result = tokenEncryptionService.validateKMSService();

                // Then
                assertThat(result).isFalse();
                verify(kmsService, never()).encryptToken(anyString());
            }

            @Test
            @DisplayName("should return false when encrypt/decrypt data mismatch")
            void shouldReturnFalseWhenDataMismatch() {
                // Given
                when(kmsService.encryptToken(anyString())).thenReturn("encrypted_value");
                when(kmsService.decryptToken("encrypted_value")).thenReturn("wrong_decrypted_value");

                // When
                boolean result = tokenEncryptionService.validateKMSService();

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when encryption throws exception")
            void shouldReturnFalseWhenEncryptionFails() {
                // Given
                when(kmsService.encryptToken(anyString()))
                        .thenThrow(new RuntimeException("KMS unavailable"));

                // When
                boolean result = tokenEncryptionService.validateKMSService();

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when decryption throws exception")
            void shouldReturnFalseWhenDecryptionFails() {
                // Given
                when(kmsService.encryptToken(anyString())).thenReturn("encrypted");
                when(kmsService.decryptToken(anyString()))
                        .thenThrow(new RuntimeException("Decryption failed"));

                // When
                boolean result = tokenEncryptionService.validateKMSService();

                // Then
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("End-to-end scenarios")
        class EndToEndTests {

            @Test
            @DisplayName("should successfully encrypt and then decrypt token")
            void shouldEncryptAndDecryptSuccessfully() {
                // Given
                String original = "my-instagram-access-token";
                when(kmsService.encryptToken(original)).thenReturn("encrypted_data");
                when(kmsService.decryptToken("encrypted_data")).thenReturn(original);

                // When
                String encrypted = tokenEncryptionService.encryptToken(original);
                String decrypted = tokenEncryptionService.decryptToken(encrypted);

                // Then
                assertThat(encrypted).isEqualTo("encrypted_data");
                assertThat(decrypted).isEqualTo(original);
            }

            @Test
            @DisplayName("should handle multiple tokens correctly")
            void shouldHandleMultipleTokensCorrectly() {
                // Given
                String token1 = "token-1";
                String token2 = "token-2";
                when(kmsService.encryptToken(token1)).thenReturn("encrypted_1");
                when(kmsService.encryptToken(token2)).thenReturn("encrypted_2");
                when(kmsService.decryptToken("encrypted_1")).thenReturn(token1);
                when(kmsService.decryptToken("encrypted_2")).thenReturn(token2);

                // When
                String enc1 = tokenEncryptionService.encryptToken(token1);
                String enc2 = tokenEncryptionService.encryptToken(token2);
                String dec1 = tokenEncryptionService.decryptToken(enc1);
                String dec2 = tokenEncryptionService.decryptToken(enc2);

                // Then
                assertThat(dec1).isEqualTo(token1);
                assertThat(dec2).isEqualTo(token2);
                assertThat(enc1).isNotEqualTo(enc2);
            }
        }
    }

    // =====================================================================
    // TotpEncryptionService Tests
    // =====================================================================

    @Nested
    @DisplayName("TotpEncryptionService")
    class TotpEncryptionServiceTests {

        @Mock
        private KMSValidationService kmsService;

        private TotpEncryptionService totpEncryptionService;
        private ObjectMapper objectMapper;
        private BCryptPasswordEncoder passwordEncoder;

        @BeforeEach
        void setUp() {
            totpEncryptionService = new TotpEncryptionService();
            ReflectionTestUtils.setField(totpEncryptionService, "kmsService", kmsService);

            // Initialize password encoder like the service does
            passwordEncoder = new BCryptPasswordEncoder(12);
            ReflectionTestUtils.setField(totpEncryptionService, "passwordEncoder", passwordEncoder);

            objectMapper = new ObjectMapper();
        }

        @Nested
        @DisplayName("init()")
        class InitTests {

            @Test
            @DisplayName("should initialize BCrypt encoder on PostConstruct")
            void shouldInitializeBCryptEncoder() {
                // Given
                TotpEncryptionService freshService = new TotpEncryptionService();
                ReflectionTestUtils.setField(freshService, "kmsService", kmsService);

                // When
                freshService.init();

                // Then
                BCryptPasswordEncoder encoder = (BCryptPasswordEncoder) ReflectionTestUtils
                        .getField(freshService, "passwordEncoder");
                assertThat(encoder).isNotNull();
            }
        }

        @Nested
        @DisplayName("encryptTotpSecret()")
        class EncryptTotpSecretTests {

            @Test
            @DisplayName("should encrypt TOTP secret successfully")
            void shouldEncryptTotpSecretSuccessfully() {
                // Given
                String plainSecret = "JBSWY3DPEHPK3PXP";
                String encryptedSecret = "encrypted-totp-secret";
                when(kmsService.encryptTotpSecret(plainSecret)).thenReturn(encryptedSecret);

                // When
                String result = totpEncryptionService.encryptTotpSecret(plainSecret);

                // Then
                assertThat(result).isEqualTo(encryptedSecret);
                verify(kmsService).encryptTotpSecret(plainSecret);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for null secret")
            void shouldThrowExceptionForNullSecret() {
                // When/Then
                assertThatThrownBy(() -> totpEncryptionService.encryptTotpSecret(null))
                        .isInstanceOf(ValidationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for empty secret")
            void shouldThrowExceptionForEmptySecret() {
                // When/Then
                assertThatThrownBy(() -> totpEncryptionService.encryptTotpSecret(""))
                        .isInstanceOf(ValidationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @Test
            @DisplayName("should throw StorageTranslatableException when KMS fails")
            void shouldThrowExceptionWhenKmsFails() {
                // Given
                when(kmsService.encryptTotpSecret(anyString()))
                        .thenThrow(new RuntimeException("KMS error"));

                // When/Then
                assertThatThrownBy(() -> totpEncryptionService.encryptTotpSecret("valid-secret"))
                        .isInstanceOf(StorageTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }

            @Test
            @DisplayName("should handle standard Base32 TOTP secrets")
            void shouldHandleBase32Secrets() {
                // Given
                String base32Secret = "ORSXG5DJNZUXI4ZTORUGS3TH";
                when(kmsService.encryptTotpSecret(base32Secret)).thenReturn("encrypted");

                // When
                String result = totpEncryptionService.encryptTotpSecret(base32Secret);

                // Then
                assertThat(result).isEqualTo("encrypted");
            }
        }

        @Nested
        @DisplayName("decryptTotpSecret()")
        class DecryptTotpSecretTests {

            @Test
            @DisplayName("should decrypt TOTP secret successfully")
            void shouldDecryptTotpSecretSuccessfully() {
                // Given
                String encrypted = "encrypted-totp-secret";
                String plainSecret = "JBSWY3DPEHPK3PXP";
                when(kmsService.decryptTotpSecret(encrypted)).thenReturn(plainSecret);

                // When
                String result = totpEncryptionService.decryptTotpSecret(encrypted);

                // Then
                assertThat(result).isEqualTo(plainSecret);
                verify(kmsService).decryptTotpSecret(encrypted);
            }

            @Test
            @DisplayName("should return null for null encrypted input")
            void shouldReturnNullForNullInput() {
                // When
                String result = totpEncryptionService.decryptTotpSecret(null);

                // Then
                assertThat(result).isNull();
                verify(kmsService, never()).decryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should return null for empty encrypted input")
            void shouldReturnNullForEmptyInput() {
                // When
                String result = totpEncryptionService.decryptTotpSecret("");

                // Then
                assertThat(result).isNull();
                verify(kmsService, never()).decryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should throw StorageTranslatableException when KMS fails")
            void shouldThrowExceptionWhenKmsFails() {
                // Given
                when(kmsService.decryptTotpSecret(anyString()))
                        .thenThrow(new RuntimeException("KMS error"));

                // When/Then
                assertThatThrownBy(() -> totpEncryptionService.decryptTotpSecret("encrypted-secret"))
                        .isInstanceOf(StorageTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }
        }

        @Nested
        @DisplayName("encryptBackupCodes()")
        class EncryptBackupCodesTests {

            @Test
            @DisplayName("should encrypt backup codes successfully")
            void shouldEncryptBackupCodesSuccessfully() throws Exception {
                // Given
                List<String> plainCodes = Arrays.asList("CODE1234", "CODE5678", "CODE9012");
                when(kmsService.encryptTotpSecret(anyString())).thenReturn("encrypted-codes");

                // When
                String result = totpEncryptionService.encryptBackupCodes(plainCodes);

                // Then
                assertThat(result).isEqualTo("encrypted-codes");
                verify(kmsService).encryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for null codes list")
            void shouldThrowExceptionForNullCodes() {
                // When/Then
                assertThatThrownBy(() -> totpEncryptionService.encryptBackupCodes(null))
                        .isInstanceOf(ValidationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException for empty codes list")
            void shouldThrowExceptionForEmptyCodes() {
                // When/Then
                assertThatThrownBy(() -> totpEncryptionService.encryptBackupCodes(Collections.emptyList()))
                        .isInstanceOf(ValidationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @Test
            @DisplayName("should BCrypt hash each code before encryption")
            void shouldBCryptHashEachCode() throws Exception {
                // Given
                List<String> plainCodes = Arrays.asList("ABC123");
                when(kmsService.encryptTotpSecret(anyString())).thenAnswer(invocation -> {
                    String json = invocation.getArgument(0);
                    List<String> hashedCodes = objectMapper.readValue(json, new TypeReference<List<String>>() {});
                    // Verify BCrypt hash format (starts with $2a$ or $2b$)
                    assertThat(hashedCodes).hasSize(1);
                    assertThat(hashedCodes.get(0)).startsWith("$2a$");
                    return "encrypted";
                });

                // When
                String result = totpEncryptionService.encryptBackupCodes(plainCodes);

                // Then
                assertThat(result).isEqualTo("encrypted");
            }

            @Test
            @DisplayName("should handle multiple backup codes")
            void shouldHandleMultipleBackupCodes() throws Exception {
                // Given
                List<String> plainCodes = Arrays.asList("CODE1", "CODE2", "CODE3", "CODE4", "CODE5");
                when(kmsService.encryptTotpSecret(anyString())).thenAnswer(invocation -> {
                    String json = invocation.getArgument(0);
                    List<String> hashedCodes = objectMapper.readValue(json, new TypeReference<List<String>>() {});
                    assertThat(hashedCodes).hasSize(5);
                    return "encrypted-multiple";
                });

                // When
                String result = totpEncryptionService.encryptBackupCodes(plainCodes);

                // Then
                assertThat(result).isEqualTo("encrypted-multiple");
            }

            @Test
            @DisplayName("should throw StorageTranslatableException when KMS fails")
            void shouldThrowExceptionWhenKmsFails() {
                // Given
                List<String> plainCodes = Arrays.asList("CODE1234");
                when(kmsService.encryptTotpSecret(anyString()))
                        .thenThrow(new RuntimeException("KMS error"));

                // When/Then
                assertThatThrownBy(() -> totpEncryptionService.encryptBackupCodes(plainCodes))
                        .isInstanceOf(StorageTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }
        }

        @Nested
        @DisplayName("verifyBackupCode()")
        class VerifyBackupCodeTests {

            @Test
            @DisplayName("should return true when backup code matches")
            void shouldReturnTrueWhenCodeMatches() throws Exception {
                // Given
                String plainCode = "BACKUP123";
                String hashedCode = passwordEncoder.encode(plainCode);
                List<String> hashedCodes = Arrays.asList(hashedCode, passwordEncoder.encode("OTHER"));
                String json = objectMapper.writeValueAsString(hashedCodes);

                when(kmsService.decryptTotpSecret("encrypted")).thenReturn(json);

                // When
                boolean result = totpEncryptionService.verifyBackupCode(plainCode, "encrypted");

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false when backup code does not match")
            void shouldReturnFalseWhenCodeDoesNotMatch() throws Exception {
                // Given
                String hashedCode = passwordEncoder.encode("CORRECT_CODE");
                List<String> hashedCodes = Arrays.asList(hashedCode);
                String json = objectMapper.writeValueAsString(hashedCodes);

                when(kmsService.decryptTotpSecret("encrypted")).thenReturn(json);

                // When
                boolean result = totpEncryptionService.verifyBackupCode("WRONG_CODE", "encrypted");

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false for null plain code")
            void shouldReturnFalseForNullPlainCode() {
                // When
                boolean result = totpEncryptionService.verifyBackupCode(null, "encrypted");

                // Then
                assertThat(result).isFalse();
                verify(kmsService, never()).decryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should return false for null encrypted codes")
            void shouldReturnFalseForNullEncryptedCodes() {
                // When
                boolean result = totpEncryptionService.verifyBackupCode("CODE123", null);

                // Then
                assertThat(result).isFalse();
                verify(kmsService, never()).decryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should return false when KMS decryption fails")
            void shouldReturnFalseWhenKmsFails() {
                // Given
                when(kmsService.decryptTotpSecret(anyString()))
                        .thenThrow(new RuntimeException("KMS error"));

                // When
                boolean result = totpEncryptionService.verifyBackupCode("CODE123", "encrypted");

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when JSON parsing fails")
            void shouldReturnFalseWhenJsonParsingFails() {
                // Given
                when(kmsService.decryptTotpSecret("encrypted")).thenReturn("invalid-json{{{");

                // When
                boolean result = totpEncryptionService.verifyBackupCode("CODE123", "encrypted");

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should verify code among multiple hashed codes")
            void shouldVerifyCodeAmongMultiple() throws Exception {
                // Given
                String targetCode = "TARGET_CODE";
                List<String> hashedCodes = Arrays.asList(
                        passwordEncoder.encode("OTHER1"),
                        passwordEncoder.encode("OTHER2"),
                        passwordEncoder.encode(targetCode),
                        passwordEncoder.encode("OTHER3")
                );
                String json = objectMapper.writeValueAsString(hashedCodes);
                when(kmsService.decryptTotpSecret("encrypted")).thenReturn(json);

                // When
                boolean result = totpEncryptionService.verifyBackupCode(targetCode, "encrypted");

                // Then
                assertThat(result).isTrue();
            }
        }

        @Nested
        @DisplayName("removeUsedBackupCode()")
        class RemoveUsedBackupCodeTests {

            @Test
            @DisplayName("should remove used backup code and return new encrypted codes")
            void shouldRemoveUsedBackupCode() throws Exception {
                // Given
                String usedCode = "USED_CODE";
                String keepCode = "KEEP_CODE";
                List<String> hashedCodes = Arrays.asList(
                        passwordEncoder.encode(usedCode),
                        passwordEncoder.encode(keepCode)
                );
                String json = objectMapper.writeValueAsString(hashedCodes);

                when(kmsService.decryptTotpSecret("encrypted")).thenReturn(json);
                when(kmsService.encryptTotpSecret(anyString())).thenAnswer(invocation -> {
                    String newJson = invocation.getArgument(0);
                    List<String> remaining = objectMapper.readValue(newJson, new TypeReference<List<String>>() {});
                    assertThat(remaining).hasSize(1);
                    return "new-encrypted";
                });

                // When
                String result = totpEncryptionService.removeUsedBackupCode(usedCode, "encrypted");

                // Then
                assertThat(result).isEqualTo("new-encrypted");
            }

            @Test
            @DisplayName("should return original encrypted codes for null used code")
            void shouldReturnOriginalForNullUsedCode() {
                // When
                String result = totpEncryptionService.removeUsedBackupCode(null, "encrypted");

                // Then
                assertThat(result).isEqualTo("encrypted");
                verify(kmsService, never()).decryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should return original encrypted codes for null encrypted codes")
            void shouldReturnOriginalForNullEncryptedCodes() {
                // When
                String result = totpEncryptionService.removeUsedBackupCode("CODE123", null);

                // Then
                assertThat(result).isNull();
                verify(kmsService, never()).decryptTotpSecret(anyString());
            }

            @Test
            @DisplayName("should return original when KMS decryption fails")
            void shouldReturnOriginalWhenDecryptionFails() {
                // Given
                when(kmsService.decryptTotpSecret(anyString()))
                        .thenThrow(new RuntimeException("KMS error"));

                // When
                String result = totpEncryptionService.removeUsedBackupCode("CODE123", "encrypted");

                // Then
                assertThat(result).isEqualTo("encrypted");
            }

            @Test
            @DisplayName("should keep all codes when used code not found")
            void shouldKeepAllCodesWhenNotFound() throws Exception {
                // Given
                List<String> hashedCodes = Arrays.asList(
                        passwordEncoder.encode("CODE1"),
                        passwordEncoder.encode("CODE2")
                );
                String json = objectMapper.writeValueAsString(hashedCodes);

                when(kmsService.decryptTotpSecret("encrypted")).thenReturn(json);
                when(kmsService.encryptTotpSecret(anyString())).thenAnswer(invocation -> {
                    String newJson = invocation.getArgument(0);
                    List<String> remaining = objectMapper.readValue(newJson, new TypeReference<List<String>>() {});
                    assertThat(remaining).hasSize(2);
                    return "same-encrypted";
                });

                // When
                String result = totpEncryptionService.removeUsedBackupCode("NOT_FOUND", "encrypted");

                // Then
                assertThat(result).isEqualTo("same-encrypted");
            }

            @Test
            @DisplayName("should handle removing last backup code")
            void shouldHandleRemovingLastCode() throws Exception {
                // Given
                String lastCode = "LAST_CODE";
                List<String> hashedCodes = Arrays.asList(passwordEncoder.encode(lastCode));
                String json = objectMapper.writeValueAsString(hashedCodes);

                when(kmsService.decryptTotpSecret("encrypted")).thenReturn(json);
                when(kmsService.encryptTotpSecret(anyString())).thenAnswer(invocation -> {
                    String newJson = invocation.getArgument(0);
                    List<String> remaining = objectMapper.readValue(newJson, new TypeReference<List<String>>() {});
                    assertThat(remaining).isEmpty();
                    return "empty-encrypted";
                });

                // When
                String result = totpEncryptionService.removeUsedBackupCode(lastCode, "encrypted");

                // Then
                assertThat(result).isEqualTo("empty-encrypted");
            }
        }

        @Nested
        @DisplayName("End-to-end TOTP scenarios")
        class TotpEndToEndTests {

            @Test
            @DisplayName("should encrypt then decrypt TOTP secret successfully")
            void shouldEncryptThenDecryptSecret() {
                // Given
                String secret = "JBSWY3DPEHPK3PXP";
                when(kmsService.encryptTotpSecret(secret)).thenReturn("encrypted-secret");
                when(kmsService.decryptTotpSecret("encrypted-secret")).thenReturn(secret);

                // When
                String encrypted = totpEncryptionService.encryptTotpSecret(secret);
                String decrypted = totpEncryptionService.decryptTotpSecret(encrypted);

                // Then
                assertThat(decrypted).isEqualTo(secret);
            }

            @Test
            @DisplayName("should encrypt backup codes then verify one successfully")
            void shouldEncryptThenVerifyBackupCode() throws Exception {
                // Given
                List<String> codes = Arrays.asList("BACKUP1", "BACKUP2", "BACKUP3");

                // Capture the encrypted JSON to return for verification
                when(kmsService.encryptTotpSecret(anyString())).thenAnswer(inv -> {
                    String json = inv.getArgument(0);
                    return Base64.getEncoder().encodeToString(json.getBytes());
                });
                when(kmsService.decryptTotpSecret(anyString())).thenAnswer(inv -> {
                    String encoded = inv.getArgument(0);
                    return new String(Base64.getDecoder().decode(encoded));
                });

                // When
                String encrypted = totpEncryptionService.encryptBackupCodes(codes);
                boolean verified = totpEncryptionService.verifyBackupCode("BACKUP2", encrypted);

                // Then
                assertThat(verified).isTrue();
            }
        }
    }

    // =====================================================================
    // KMSValidationService Tests
    // =====================================================================

    @Nested
    @DisplayName("KMSValidationService")
    class KMSValidationServiceTests {

        @Mock
        private KeyManagementServiceClient kmsClient;

        @Mock
        private Resource serviceAccountFile;

        @Mock
        private EncryptResponse encryptResponse;

        @Mock
        private DecryptResponse decryptResponse;

        private KMSValidationService kmsValidationService;

        @BeforeEach
        void setUp() {
            kmsValidationService = new KMSValidationService();
            ReflectionTestUtils.setField(kmsValidationService, "kmsEnabled", true);
            ReflectionTestUtils.setField(kmsValidationService, "projectId", "test-project");
            ReflectionTestUtils.setField(kmsValidationService, "kmsLocation", "europe-central2");
            ReflectionTestUtils.setField(kmsValidationService, "keyRingName", "test-key-ring");
            ReflectionTestUtils.setField(kmsValidationService, "keyName", "test-key");
            ReflectionTestUtils.setField(kmsValidationService, "totpKeyName", "test-totp-key");
            ReflectionTestUtils.setField(kmsValidationService, "kmsClient", kmsClient);

            // Set up crypto key names
            CryptoKeyName cryptoKeyName = CryptoKeyName.of("test-project", "europe-central2", "test-key-ring", "test-key");
            CryptoKeyName totpCryptoKeyName = CryptoKeyName.of("test-project", "europe-central2", "test-key-ring", "test-totp-key");
            ReflectionTestUtils.setField(kmsValidationService, "cryptoKeyName", cryptoKeyName);
            ReflectionTestUtils.setField(kmsValidationService, "totpCryptoKeyName", totpCryptoKeyName);
        }

        @Nested
        @DisplayName("encryptToken()")
        class EncryptTokenKmsTests {

            @Test
            @DisplayName("should encrypt token successfully")
            void shouldEncryptTokenSuccessfully() {
                // Given
                String plaintext = "test-token";
                byte[] ciphertext = "encrypted-data".getBytes();
                when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);
                when(encryptResponse.getCiphertext()).thenReturn(ByteString.copyFrom(ciphertext));

                // When
                String result = kmsValidationService.encryptToken(plaintext);

                // Then
                assertThat(result).isEqualTo(Base64.getEncoder().encodeToString(ciphertext));
                verify(kmsClient).encrypt(any(EncryptRequest.class));
            }

            @Test
            @DisplayName("should throw when KMS is disabled")
            void shouldThrowWhenKmsDisabled() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsEnabled", false);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.encryptToken("test-token"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw when KMS client is null")
            void shouldThrowWhenKmsClientNull() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsClient", null);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.encryptToken("test-token"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when encryption fails")
            void shouldThrowExceptionWhenEncryptionFails() {
                // Given
                when(kmsClient.encrypt(any(EncryptRequest.class)))
                        .thenThrow(new RuntimeException("KMS encryption failed"));

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.encryptToken("test"))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.kms_encryption_failed");
            }
        }

        @Nested
        @DisplayName("decryptToken()")
        class DecryptTokenKmsTests {

            @Test
            @DisplayName("should decrypt token successfully")
            void shouldDecryptTokenSuccessfully() {
                // Given
                byte[] ciphertext = "encrypted-data".getBytes();
                String encryptedBase64 = Base64.getEncoder().encodeToString(ciphertext);
                String plaintext = "decrypted-token";

                when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);
                when(decryptResponse.getPlaintext()).thenReturn(ByteString.copyFromUtf8(plaintext));

                // When
                String result = kmsValidationService.decryptToken(encryptedBase64);

                // Then
                assertThat(result).isEqualTo(plaintext);
                verify(kmsClient).decrypt(any(DecryptRequest.class));
            }

            @Test
            @DisplayName("should throw when KMS is disabled")
            void shouldThrowWhenKmsDisabledDecrypt() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsEnabled", false);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.decryptToken("encrypted-token"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw when KMS client is null")
            void shouldThrowWhenKmsClientNullDecrypt() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsClient", null);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.decryptToken("encrypted-token"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when decryption fails")
            void shouldThrowExceptionWhenDecryptionFails() {
                // Given
                String validBase64 = Base64.getEncoder().encodeToString("test".getBytes());
                when(kmsClient.decrypt(any(DecryptRequest.class)))
                        .thenThrow(new RuntimeException("KMS decryption failed"));

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.decryptToken(validBase64))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.kms_decryption_failed");
            }

            @Test
            @DisplayName("should throw exception for invalid base64 input")
            void shouldThrowExceptionForInvalidBase64() {
                // Given - invalid base64 string
                String invalidBase64 = "not-valid-base64!!!";

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.decryptToken(invalidBase64))
                        .isInstanceOf(AuthenticationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("encryptTotpSecret()")
        class EncryptTotpSecretKmsTests {

            @Test
            @DisplayName("should encrypt TOTP secret successfully")
            void shouldEncryptTotpSecretSuccessfully() {
                // Given
                String secret = "JBSWY3DPEHPK3PXP";
                byte[] ciphertext = "encrypted-totp".getBytes();
                when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);
                when(encryptResponse.getCiphertext()).thenReturn(ByteString.copyFrom(ciphertext));

                // When
                String result = kmsValidationService.encryptTotpSecret(secret);

                // Then
                assertThat(result).isEqualTo(Base64.getEncoder().encodeToString(ciphertext));
            }

            @Test
            @DisplayName("should throw IllegalStateException when KMS is disabled")
            void shouldThrowExceptionWhenKmsDisabled() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsEnabled", false);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.encryptTotpSecret("secret"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw IllegalStateException when KMS client is null")
            void shouldThrowExceptionWhenKmsClientNull() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsClient", null);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.encryptTotpSecret("secret"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when encryption fails")
            void shouldThrowExceptionWhenEncryptionFails() {
                // Given
                when(kmsClient.encrypt(any(EncryptRequest.class)))
                        .thenThrow(new RuntimeException("TOTP encryption failed"));

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.encryptTotpSecret("secret"))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.totp_encryption_failed");
            }
        }

        @Nested
        @DisplayName("decryptTotpSecret()")
        class DecryptTotpSecretKmsTests {

            @Test
            @DisplayName("should decrypt TOTP secret successfully")
            void shouldDecryptTotpSecretSuccessfully() {
                // Given
                byte[] ciphertext = "encrypted-totp".getBytes();
                String encryptedBase64 = Base64.getEncoder().encodeToString(ciphertext);
                String plainSecret = "JBSWY3DPEHPK3PXP";

                when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);
                when(decryptResponse.getPlaintext()).thenReturn(ByteString.copyFromUtf8(plainSecret));

                // When
                String result = kmsValidationService.decryptTotpSecret(encryptedBase64);

                // Then
                assertThat(result).isEqualTo(plainSecret);
            }

            @Test
            @DisplayName("should throw IllegalStateException when KMS is disabled")
            void shouldThrowExceptionWhenKmsDisabled() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsEnabled", false);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.decryptTotpSecret("encrypted"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw IllegalStateException when KMS client is null")
            void shouldThrowExceptionWhenKmsClientNull() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsClient", null);

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.decryptTotpSecret("encrypted"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("KMS not available");
            }

            @Test
            @DisplayName("should throw AuthenticationTranslatableException when decryption fails")
            void shouldThrowExceptionWhenDecryptionFails() {
                // Given
                String validBase64 = Base64.getEncoder().encodeToString("test".getBytes());
                when(kmsClient.decrypt(any(DecryptRequest.class)))
                        .thenThrow(new RuntimeException("TOTP decryption failed"));

                // When/Then
                assertThatThrownBy(() -> kmsValidationService.decryptTotpSecret(validBase64))
                        .isInstanceOf(AuthenticationTranslatableException.class)
                        .hasFieldOrPropertyWithValue("messageKey", "error.security.totp_decryption_failed");
            }
        }

        @Nested
        @DisplayName("shutdown()")
        class ShutdownTests {

            @Test
            @DisplayName("should close KMS client on shutdown")
            void shouldCloseKmsClientOnShutdown() {
                // When
                kmsValidationService.shutdown();

                // Then
                verify(kmsClient).close();
            }

            @Test
            @DisplayName("should handle null KMS client on shutdown")
            void shouldHandleNullKmsClientOnShutdown() {
                // Given
                ReflectionTestUtils.setField(kmsValidationService, "kmsClient", null);

                // When/Then - should not throw
                assertThatNoException().isThrownBy(() -> kmsValidationService.shutdown());
            }
        }

        @Nested
        @DisplayName("isBase64() helper method")
        class IsBase64Tests {

            @Test
            @DisplayName("should return true for valid base64")
            void shouldReturnTrueForValidBase64() throws Exception {
                // Given
                java.lang.reflect.Method method = KMSValidationService.class
                        .getDeclaredMethod("isBase64", String.class);
                method.setAccessible(true);

                String validBase64 = Base64.getEncoder().encodeToString("test data".getBytes());

                // When
                boolean result = (boolean) method.invoke(kmsValidationService, validBase64);

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false for JSON string")
            void shouldReturnFalseForJsonString() throws Exception {
                // Given
                java.lang.reflect.Method method = KMSValidationService.class
                        .getDeclaredMethod("isBase64", String.class);
                method.setAccessible(true);

                String jsonString = "{\"key\": \"value\"}";

                // When
                boolean result = (boolean) method.invoke(kmsValidationService, jsonString);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false for null string")
            void shouldReturnFalseForNullString() throws Exception {
                // Given
                java.lang.reflect.Method method = KMSValidationService.class
                        .getDeclaredMethod("isBase64", String.class);
                method.setAccessible(true);

                // When
                boolean result = (boolean) method.invoke(kmsValidationService, (String) null);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false for empty string")
            void shouldReturnFalseForEmptyString() throws Exception {
                // Given
                java.lang.reflect.Method method = KMSValidationService.class
                        .getDeclaredMethod("isBase64", String.class);
                method.setAccessible(true);

                // When
                boolean result = (boolean) method.invoke(kmsValidationService, "");

                // Then
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("Configuration tests")
        class ConfigurationTests {

            @Test
            @DisplayName("should use configured project ID")
            void shouldUseConfiguredProjectId() {
                // Given
                String projectId = (String) ReflectionTestUtils.getField(kmsValidationService, "projectId");

                // Then
                assertThat(projectId).isEqualTo("test-project");
            }

            @Test
            @DisplayName("should use configured KMS location")
            void shouldUseConfiguredKmsLocation() {
                // Given
                String location = (String) ReflectionTestUtils.getField(kmsValidationService, "kmsLocation");

                // Then
                assertThat(location).isEqualTo("europe-central2");
            }

            @Test
            @DisplayName("should use configured key ring name")
            void shouldUseConfiguredKeyRingName() {
                // Given
                String keyRing = (String) ReflectionTestUtils.getField(kmsValidationService, "keyRingName");

                // Then
                assertThat(keyRing).isEqualTo("test-key-ring");
            }

            @Test
            @DisplayName("should use configured key name")
            void shouldUseConfiguredKeyName() {
                // Given
                String keyName = (String) ReflectionTestUtils.getField(kmsValidationService, "keyName");

                // Then
                assertThat(keyName).isEqualTo("test-key");
            }

            @Test
            @DisplayName("should use configured TOTP key name")
            void shouldUseConfiguredTotpKeyName() {
                // Given
                String totpKeyName = (String) ReflectionTestUtils.getField(kmsValidationService, "totpKeyName");

                // Then
                assertThat(totpKeyName).isEqualTo("test-totp-key");
            }
        }

        @Nested
        @DisplayName("End-to-end KMS scenarios")
        class KmsEndToEndTests {

            @Test
            @DisplayName("should encrypt and decrypt token round-trip")
            void shouldEncryptAndDecryptTokenRoundTrip() {
                // Given
                String original = "test-access-token";
                byte[] ciphertext = "encrypted-data".getBytes();

                when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);
                when(encryptResponse.getCiphertext()).thenReturn(ByteString.copyFrom(ciphertext));

                when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);
                when(decryptResponse.getPlaintext()).thenReturn(ByteString.copyFromUtf8(original));

                // When
                String encrypted = kmsValidationService.encryptToken(original);
                String decrypted = kmsValidationService.decryptToken(encrypted);

                // Then
                assertThat(decrypted).isEqualTo(original);
            }

            @Test
            @DisplayName("should encrypt and decrypt TOTP secret round-trip")
            void shouldEncryptAndDecryptTotpSecretRoundTrip() {
                // Given
                String secret = "JBSWY3DPEHPK3PXP";
                byte[] ciphertext = "encrypted-totp".getBytes();

                when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);
                when(encryptResponse.getCiphertext()).thenReturn(ByteString.copyFrom(ciphertext));

                when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);
                when(decryptResponse.getPlaintext()).thenReturn(ByteString.copyFromUtf8(secret));

                // When
                String encrypted = kmsValidationService.encryptTotpSecret(secret);
                String decrypted = kmsValidationService.decryptTotpSecret(encrypted);

                // Then
                assertThat(decrypted).isEqualTo(secret);
            }
        }
    }

    // =====================================================================
    // Cross-Service Integration Tests
    // =====================================================================

    @Nested
    @DisplayName("Cross-Service Integration")
    class CrossServiceIntegrationTests {

        @Mock
        private KMSValidationService kmsService;

        private TokenEncryptionService tokenEncryptionService;
        private TotpEncryptionService totpEncryptionService;

        @BeforeEach
        void setUp() {
            tokenEncryptionService = new TokenEncryptionService();
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", true);

            totpEncryptionService = new TotpEncryptionService();
            ReflectionTestUtils.setField(totpEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(totpEncryptionService, "passwordEncoder", new BCryptPasswordEncoder(12));
        }

        @Test
        @DisplayName("should use same KMS service for both token and TOTP encryption")
        void shouldUseSameKmsService() {
            // Given
            when(kmsService.encryptToken("token")).thenReturn("encrypted-token");
            when(kmsService.encryptTotpSecret("secret")).thenReturn("encrypted-secret");

            // When
            String encryptedToken = tokenEncryptionService.encryptToken("token");
            String encryptedSecret = totpEncryptionService.encryptTotpSecret("secret");

            // Then
            assertThat(encryptedToken).isEqualTo("encrypted-token");
            assertThat(encryptedSecret).isEqualTo("encrypted-secret");
            verify(kmsService).encryptToken("token");
            verify(kmsService).encryptTotpSecret("secret");
        }

        @Test
        @DisplayName("should handle KMS failure affecting both services")
        void shouldHandleKmsFailureAffectingBothServices() {
            // Given
            when(kmsService.encryptToken(anyString()))
                    .thenThrow(new RuntimeException("KMS unavailable"));
            when(kmsService.encryptTotpSecret(anyString()))
                    .thenThrow(new RuntimeException("KMS unavailable"));

            // When/Then
            assertThatThrownBy(() -> tokenEncryptionService.encryptToken("token"))
                    .isInstanceOf(AuthenticationTranslatableException.class);
            assertThatThrownBy(() -> totpEncryptionService.encryptTotpSecret("secret"))
                    .isInstanceOf(StorageTranslatableException.class);
        }
    }

    // =====================================================================
    // Edge Cases and Boundary Tests
    // =====================================================================

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesTests {

        @Mock
        private KMSValidationService kmsService;

        private TokenEncryptionService tokenEncryptionService;
        private TotpEncryptionService totpEncryptionService;

        @BeforeEach
        void setUp() {
            tokenEncryptionService = new TokenEncryptionService();
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", true);

            totpEncryptionService = new TotpEncryptionService();
            ReflectionTestUtils.setField(totpEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(totpEncryptionService, "passwordEncoder", new BCryptPasswordEncoder(12));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should handle null and empty tokens for encryption")
        void shouldHandleNullAndEmptyTokensForEncryption(String token) {
            // When
            String result = tokenEncryptionService.encryptToken(token);

            // Then
            assertThat(result).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should handle null and empty tokens for decryption")
        void shouldHandleNullAndEmptyTokensForDecryption(String token) {
            // When
            String result = tokenEncryptionService.decryptToken(token);

            // Then
            assertThat(result).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "ab", "abc"})
        @DisplayName("should handle very short tokens")
        void shouldHandleVeryShortTokens(String shortToken) {
            // Given
            when(kmsService.encryptToken(shortToken)).thenReturn("encrypted-" + shortToken);

            // When
            String result = tokenEncryptionService.encryptToken(shortToken);

            // Then
            assertThat(result).startsWith("encrypted-");
        }

        @Test
        @DisplayName("should handle token with only whitespace")
        void shouldHandleWhitespaceOnlyToken() {
            // Given
            String whitespaceToken = "   ";
            when(kmsService.encryptToken(whitespaceToken)).thenReturn("encrypted-whitespace");

            // When
            String result = tokenEncryptionService.encryptToken(whitespaceToken);

            // Then
            assertThat(result).isEqualTo("encrypted-whitespace");
        }

        @Test
        @DisplayName("should handle token with newlines")
        void shouldHandleTokenWithNewlines() {
            // Given
            String newlineToken = "token\nwith\nnewlines";
            when(kmsService.encryptToken(newlineToken)).thenReturn("encrypted-newline");

            // When
            String result = tokenEncryptionService.encryptToken(newlineToken);

            // Then
            assertThat(result).isEqualTo("encrypted-newline");
        }

        @Test
        @DisplayName("should handle token with tabs")
        void shouldHandleTokenWithTabs() {
            // Given
            String tabToken = "token\twith\ttabs";
            when(kmsService.encryptToken(tabToken)).thenReturn("encrypted-tabs");

            // When
            String result = tokenEncryptionService.encryptToken(tabToken);

            // Then
            assertThat(result).isEqualTo("encrypted-tabs");
        }

        @Test
        @DisplayName("should handle maximum reasonable token length")
        void shouldHandleMaximumReasonableTokenLength() {
            // Given
            String maxToken = "x".repeat(10000);
            when(kmsService.encryptToken(maxToken)).thenReturn("encrypted-max");

            // When
            String result = tokenEncryptionService.encryptToken(maxToken);

            // Then
            assertThat(result).isEqualTo("encrypted-max");
        }

        @Test
        @DisplayName("should handle binary-like data in token")
        void shouldHandleBinaryLikeDataInToken() {
            // Given - create a string with various byte values
            StringBuilder binaryLike = new StringBuilder();
            for (int i = 32; i < 127; i++) {
                binaryLike.append((char) i);
            }
            String token = binaryLike.toString();
            when(kmsService.encryptToken(token)).thenReturn("encrypted-binary");

            // When
            String result = tokenEncryptionService.encryptToken(token);

            // Then
            assertThat(result).isEqualTo("encrypted-binary");
        }
    }

    // =====================================================================
    // Concurrent Access Tests
    // =====================================================================

    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {

        @Mock
        private KMSValidationService kmsService;

        private TokenEncryptionService tokenEncryptionService;

        @BeforeEach
        void setUp() {
            tokenEncryptionService = new TokenEncryptionService();
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", true);
        }

        @Test
        @DisplayName("should handle multiple encrypt calls correctly")
        void shouldHandleMultipleEncryptCalls() {
            // Given
            when(kmsService.encryptToken("token1")).thenReturn("encrypted1");
            when(kmsService.encryptToken("token2")).thenReturn("encrypted2");
            when(kmsService.encryptToken("token3")).thenReturn("encrypted3");

            // When
            String result1 = tokenEncryptionService.encryptToken("token1");
            String result2 = tokenEncryptionService.encryptToken("token2");
            String result3 = tokenEncryptionService.encryptToken("token3");

            // Then
            assertThat(result1).isEqualTo("encrypted1");
            assertThat(result2).isEqualTo("encrypted2");
            assertThat(result3).isEqualTo("encrypted3");
            verify(kmsService, times(3)).encryptToken(anyString());
        }

        @Test
        @DisplayName("should handle multiple decrypt calls correctly")
        void shouldHandleMultipleDecryptCalls() {
            // Given
            when(kmsService.decryptToken("encrypted1")).thenReturn("token1");
            when(kmsService.decryptToken("encrypted2")).thenReturn("token2");
            when(kmsService.decryptToken("encrypted3")).thenReturn("token3");

            // When
            String result1 = tokenEncryptionService.decryptToken("encrypted1");
            String result2 = tokenEncryptionService.decryptToken("encrypted2");
            String result3 = tokenEncryptionService.decryptToken("encrypted3");

            // Then
            assertThat(result1).isEqualTo("token1");
            assertThat(result2).isEqualTo("token2");
            assertThat(result3).isEqualTo("token3");
            verify(kmsService, times(3)).decryptToken(anyString());
        }
    }

    // =====================================================================
    // Error Message Validation Tests
    // =====================================================================

    @Nested
    @DisplayName("Error Message Validation")
    class ErrorMessageValidationTests {

        @Mock
        private KMSValidationService kmsService;

        private TokenEncryptionService tokenEncryptionService;
        private TotpEncryptionService totpEncryptionService;

        @BeforeEach
        void setUp() {
            tokenEncryptionService = new TokenEncryptionService();
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", true);

            totpEncryptionService = new TotpEncryptionService();
            ReflectionTestUtils.setField(totpEncryptionService, "kmsService", kmsService);
            ReflectionTestUtils.setField(totpEncryptionService, "passwordEncoder", new BCryptPasswordEncoder(12));
        }

        @Test
        @DisplayName("should have correct message key for encryption disabled")
        void shouldHaveCorrectMessageKeyForEncryptionDisabled() {
            // Given
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", false);

            // When/Then
            assertThatThrownBy(() -> tokenEncryptionService.encryptToken("token"))
                    .isInstanceOf(AuthenticationTranslatableException.class)
                    .extracting("messageKey")
                    .isEqualTo("error.security.encryption_disabled");
        }

        @Test
        @DisplayName("should have correct message key for decryption disabled")
        void shouldHaveCorrectMessageKeyForDecryptionDisabled() {
            // Given
            ReflectionTestUtils.setField(tokenEncryptionService, "kmsEnabled", false);

            // When/Then
            assertThatThrownBy(() -> tokenEncryptionService.decryptToken("token"))
                    .isInstanceOf(AuthenticationTranslatableException.class)
                    .extracting("messageKey")
                    .isEqualTo("error.security.decryption_disabled");
        }

        @Test
        @DisplayName("should have correct message key for encryption failed")
        void shouldHaveCorrectMessageKeyForEncryptionFailed() {
            // Given
            when(kmsService.encryptToken(anyString()))
                    .thenThrow(new RuntimeException("KMS error"));

            // When/Then
            assertThatThrownBy(() -> tokenEncryptionService.encryptToken("token"))
                    .isInstanceOf(AuthenticationTranslatableException.class)
                    .extracting("messageKey")
                    .isEqualTo("error.security.encryption_failed");
        }

        @Test
        @DisplayName("should have correct message key for decryption failed")
        void shouldHaveCorrectMessageKeyForDecryptionFailed() {
            // Given
            when(kmsService.decryptToken(anyString()))
                    .thenThrow(new RuntimeException("KMS error"));

            // When/Then
            assertThatThrownBy(() -> tokenEncryptionService.decryptToken("token"))
                    .isInstanceOf(AuthenticationTranslatableException.class)
                    .extracting("messageKey")
                    .isEqualTo("error.security.decryption_failed");
        }

        @Test
        @DisplayName("should have correct message key for TOTP secret validation")
        void shouldHaveCorrectMessageKeyForTotpSecretValidation() {
            // When/Then
            assertThatThrownBy(() -> totpEncryptionService.encryptTotpSecret(null))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .extracting("messageKey")
                    .isEqualTo("error.validation.required_field");
        }

        @Test
        @DisplayName("should have correct message key for backup codes validation")
        void shouldHaveCorrectMessageKeyForBackupCodesValidation() {
            // When/Then
            assertThatThrownBy(() -> totpEncryptionService.encryptBackupCodes(null))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .extracting("messageKey")
                    .isEqualTo("error.validation.required_field");
        }
    }
}
