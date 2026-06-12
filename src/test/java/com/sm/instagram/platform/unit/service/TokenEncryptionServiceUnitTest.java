package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.security.KMSValidationService;
import com.sm.instagram.platform.common.security.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenEncryptionService Unit Tests")
class TokenEncryptionServiceUnitTest {

    @Mock
    private KMSValidationService kmsService;

    private TokenEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new TokenEncryptionService();
        ReflectionTestUtils.setField(service, "kmsService", kmsService);
        ReflectionTestUtils.setField(service, "kmsEnabled", true);
    }

    @Nested
    @DisplayName("encryptToken")
    class EncryptTokenTests {

        @Test
        @DisplayName("should encrypt token when KMS is enabled")
        void shouldEncryptTokenWhenKmsEnabled() {
            // Given
            String plainToken = "test-access-token-123";
            String encryptedToken = "encrypted-base64-token";
            when(kmsService.encryptToken(plainToken)).thenReturn(encryptedToken);

            // When
            String result = service.encryptToken(plainToken);

            // Then
            assertThat(result).isEqualTo(encryptedToken);
            verify(kmsService).encryptToken(plainToken);
        }

        @Test
        @DisplayName("should return null for null token")
        void shouldReturnNullForNullToken() {
            // When
            String result = service.encryptToken(null);

            // Then
            assertThat(result).isNull();
            verify(kmsService, never()).encryptToken(anyString());
        }

        @Test
        @DisplayName("should return null for empty token")
        void shouldReturnNullForEmptyToken() {
            // When
            String result = service.encryptToken("");

            // Then
            assertThat(result).isNull();
            verify(kmsService, never()).encryptToken(anyString());
        }

        @Test
        @DisplayName("should throw exception when KMS is disabled")
        void shouldThrowExceptionWhenKmsDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "kmsEnabled", false);

            // When/Then
            assertThatThrownBy(() -> service.encryptToken("test-token"))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.security.encryption_disabled");
        }

        @Test
        @DisplayName("should throw exception when KMS service fails")
        void shouldThrowExceptionWhenKmsServiceFails() {
            // Given
            when(kmsService.encryptToken(anyString()))
                .thenThrow(new RuntimeException("KMS error"));

            // When/Then
            assertThatThrownBy(() -> service.encryptToken("test-token"))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.security.encryption_failed");
        }
    }

    @Nested
    @DisplayName("decryptToken")
    class DecryptTokenTests {

        @Test
        @DisplayName("should decrypt token when KMS is enabled")
        void shouldDecryptTokenWhenKmsEnabled() {
            // Given
            String encryptedToken = "encrypted-base64-token";
            String plainToken = "test-access-token-123";
            when(kmsService.decryptToken(encryptedToken)).thenReturn(plainToken);

            // When
            String result = service.decryptToken(encryptedToken);

            // Then
            assertThat(result).isEqualTo(plainToken);
            verify(kmsService).decryptToken(encryptedToken);
        }

        @Test
        @DisplayName("should return null for null token")
        void shouldReturnNullForNullToken() {
            // When
            String result = service.decryptToken(null);

            // Then
            assertThat(result).isNull();
            verify(kmsService, never()).decryptToken(anyString());
        }

        @Test
        @DisplayName("should return null for empty token")
        void shouldReturnNullForEmptyToken() {
            // When
            String result = service.decryptToken("");

            // Then
            assertThat(result).isNull();
            verify(kmsService, never()).decryptToken(anyString());
        }

        @Test
        @DisplayName("should throw exception when KMS is disabled")
        void shouldThrowExceptionWhenKmsDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "kmsEnabled", false);

            // When/Then
            assertThatThrownBy(() -> service.decryptToken("encrypted-token"))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.security.decryption_disabled");
        }

        @Test
        @DisplayName("should throw exception when KMS service fails")
        void shouldThrowExceptionWhenKmsServiceFails() {
            // Given
            when(kmsService.decryptToken(anyString()))
                .thenThrow(new RuntimeException("KMS error"));

            // When/Then
            assertThatThrownBy(() -> service.decryptToken("encrypted-token"))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.security.decryption_failed");
        }
    }

    @Nested
    @DisplayName("isEncryptionEnabled")
    class IsEncryptionEnabledTests {

        @Test
        @DisplayName("should return true when KMS is enabled")
        void shouldReturnTrueWhenKmsEnabled() {
            // Given
            ReflectionTestUtils.setField(service, "kmsEnabled", true);

            // When/Then
            assertThat(service.isEncryptionEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return false when KMS is disabled")
        void shouldReturnFalseWhenKmsDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "kmsEnabled", false);

            // When/Then
            assertThat(service.isEncryptionEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("validateKMSService")
    class ValidateKMSServiceTests {

        @Test
        @DisplayName("should return true when KMS works correctly")
        void shouldReturnTrueWhenKmsWorksCorrectly() {
            // Given - fresh service instance
            service = new TokenEncryptionService();
            ReflectionTestUtils.setField(service, "kmsService", kmsService);
            ReflectionTestUtils.setField(service, "kmsEnabled", true);

            // Simulate successful round-trip encryption/decryption
            when(kmsService.encryptToken(anyString())).thenAnswer(inv -> "encrypted_" + inv.getArgument(0));
            when(kmsService.decryptToken(anyString())).thenAnswer(inv -> {
                String encrypted = inv.getArgument(0);
                return encrypted.replace("encrypted_", "");
            });

            // When
            boolean result = service.validateKMSService();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when KMS is disabled")
        void shouldReturnFalseWhenKmsDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "kmsEnabled", false);

            // When
            boolean result = service.validateKMSService();

            // Then
            assertThat(result).isFalse();
            verify(kmsService, never()).encryptToken(anyString());
        }

        @Test
        @DisplayName("should return false when encrypt/decrypt mismatch")
        void shouldReturnFalseWhenMismatch() {
            // Given
            when(kmsService.encryptToken(anyString())).thenReturn("encrypted");
            when(kmsService.decryptToken("encrypted")).thenReturn("wrong_data");

            // When
            boolean result = service.validateKMSService();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when KMS throws exception")
        void shouldReturnFalseWhenKmsThrowsException() {
            // Given
            when(kmsService.encryptToken(anyString()))
                .thenThrow(new RuntimeException("KMS unavailable"));

            // When
            boolean result = service.validateKMSService();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("End-to-end encryption flow")
    class EndToEndEncryptionTests {

        @Test
        @DisplayName("should encrypt and decrypt successfully")
        void shouldEncryptAndDecryptSuccessfully() {
            // Given
            String originalToken = "instagram-access-token-xyz123";
            when(kmsService.encryptToken(originalToken)).thenReturn("encrypted_token_data");
            when(kmsService.decryptToken("encrypted_token_data")).thenReturn(originalToken);

            // When
            String encrypted = service.encryptToken(originalToken);
            String decrypted = service.decryptToken(encrypted);

            // Then
            assertThat(encrypted).isEqualTo("encrypted_token_data");
            assertThat(decrypted).isEqualTo(originalToken);
        }

        @Test
        @DisplayName("should handle long tokens")
        void shouldHandleLongTokens() {
            // Given
            String longToken = "a".repeat(1000);
            when(kmsService.encryptToken(longToken)).thenReturn("encrypted_long");
            when(kmsService.decryptToken("encrypted_long")).thenReturn(longToken);

            // When
            String encrypted = service.encryptToken(longToken);
            String decrypted = service.decryptToken(encrypted);

            // Then
            assertThat(decrypted).isEqualTo(longToken);
            assertThat(decrypted).hasSize(1000);
        }

        @Test
        @DisplayName("should handle special characters in token")
        void shouldHandleSpecialCharacters() {
            // Given
            String specialToken = "token=123&user=test@example.com#fragment";
            when(kmsService.encryptToken(specialToken)).thenReturn("encrypted_special");
            when(kmsService.decryptToken("encrypted_special")).thenReturn(specialToken);

            // When
            String encrypted = service.encryptToken(specialToken);
            String decrypted = service.decryptToken(encrypted);

            // Then
            assertThat(decrypted).isEqualTo(specialToken);
        }
    }
}
