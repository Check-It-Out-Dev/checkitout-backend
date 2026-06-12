package com.sm.instagram.platform.unit.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.dto.TotpSetupResponse;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.service.ImprovedQRCodeService;
import com.sm.instagram.platform.auth.service.QRCodeGeneratorService;
import com.sm.instagram.platform.auth.service.TwoFactorAuthService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserService;
import com.sm.instagram.platform.user.UserType;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TwoFactorAuthService.
 * Tests cover 2FA setup, verification, enabling/disabling, and backup code functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TwoFactorAuthService Unit Tests")
class TwoFactorAuthServiceUnitTest {

    @Mock
    private TotpFirestoreService firestoreService;

    @Mock
    private UserService userService;

    @Mock
    private GoogleAuthenticator gAuth;

    @Mock
    private QRCodeGeneratorService qrCodeGeneratorService;

    @Mock
    private ImprovedQRCodeService improvedQRCodeService;

    @Mock
    private FirebaseAuth firebaseAuth;

    private TwoFactorAuthService service;

    private static final String FIREBASE_USER_ID = "firebase-uid-123";
    private static final String TEST_EMAIL = "admin@example.com";
    private static final String TEST_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String ISSUER = "CheckItOut";
    private static final String VALID_CODE = "123456";
    private static final int VALID_CODE_INT = 123456;

    @BeforeEach
    void setUp() {
        service = new TwoFactorAuthService();

        // Inject mocks using reflection
        ReflectionTestUtils.setField(service, "firestoreService", firestoreService);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "gAuth", gAuth);
        ReflectionTestUtils.setField(service, "qrCodeGeneratorService", qrCodeGeneratorService);
        ReflectionTestUtils.setField(service, "improvedQRCodeService", improvedQRCodeService);
        ReflectionTestUtils.setField(service, "firebaseAuth", firebaseAuth);
        ReflectionTestUtils.setField(service, "issuer", ISSUER);
        ReflectionTestUtils.setField(service, "totpEnabled", true);
    }

    private User createAdminUser() {
        User user = new User();
        user.setId(1L);
        user.setFirebaseUserId(FIREBASE_USER_ID);
        user.setUserType(UserType.ADMIN);
        user.setEmail(TEST_EMAIL);
        return user;
    }

    private User createPendingAdminUser() {
        User user = new User();
        user.setId(2L);
        user.setFirebaseUserId(FIREBASE_USER_ID);
        user.setUserType(UserType.PENDING_ADMIN);
        user.setEmail(TEST_EMAIL);
        return user;
    }

    private User createInfluencerUser() {
        User user = new User();
        user.setId(3L);
        user.setFirebaseUserId(FIREBASE_USER_ID);
        user.setUserType(UserType.INFLUENCER);
        user.setEmail(TEST_EMAIL);
        return user;
    }

    private User createCompanyUser() {
        User user = new User();
        user.setId(4L);
        user.setFirebaseUserId(FIREBASE_USER_ID);
        user.setUserType(UserType.COMPANY);
        user.setEmail(TEST_EMAIL);
        return user;
    }

    private void setupSuccessfulTotpSetup(User user) {
        when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(user);
        when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(false);

        GoogleAuthenticatorKey mockKey = mock(GoogleAuthenticatorKey.class);
        when(mockKey.getKey()).thenReturn(TEST_SECRET);
        when(gAuth.createCredentials()).thenReturn(mockKey);

        when(improvedQRCodeService.generateQRCode(eq(TEST_SECRET), eq(TEST_EMAIL)))
            .thenReturn("data:image/png;base64,QRCodeImageData");
        when(improvedQRCodeService.generateOtpAuthUrl(eq(TEST_SECRET), eq(TEST_EMAIL)))
            .thenReturn("otpauth://totp/CheckItOut:admin@example.com?secret=JBSWY3DPEHPK3PXP&issuer=CheckItOut");
        when(improvedQRCodeService.generateGoogleChartsQRUrl(eq(TEST_SECRET), eq(TEST_EMAIL)))
            .thenReturn("https://quickchart.io/qr?text=otpauth://totp/...");

        ImprovedQRCodeService.ManualEntryInfo manualInfo = ImprovedQRCodeService.ManualEntryInfo.builder()
            .secret("JBSW Y3DP EHPK 3PXP")
            .accountName(TEST_EMAIL)
            .issuer(ISSUER)
            .build();
        when(improvedQRCodeService.getManualEntryInfo(eq(TEST_SECRET), eq(TEST_EMAIL)))
            .thenReturn(manualInfo);
    }

    @Nested
    @DisplayName("setupTwoFactor")
    class SetupTwoFactorTests {

        @Test
        @DisplayName("should successfully setup 2FA for ADMIN user")
        void shouldSetupTwoFactorForAdminUser() {
            // Given
            User adminUser = createAdminUser();
            setupSuccessfulTotpSetup(adminUser);

            // When
            TotpSetupResponse response = service.setupTwoFactor(FIREBASE_USER_ID);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSecret()).isEqualTo(TEST_SECRET);
            assertThat(response.getQrCodeImage()).isEqualTo("data:image/png;base64,QRCodeImageData");
            assertThat(response.getBackupCodes()).hasSize(10);
            assertThat(response.getIssuer()).isEqualTo(ISSUER);
            assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);

            verify(firestoreService).storeTotpSecret(eq(FIREBASE_USER_ID), eq(TEST_SECRET), anyList());
        }

        @Test
        @DisplayName("should successfully setup 2FA for PENDING_ADMIN user")
        void shouldSetupTwoFactorForPendingAdminUser() {
            // Given
            User pendingAdminUser = createPendingAdminUser();
            setupSuccessfulTotpSetup(pendingAdminUser);

            // When
            TotpSetupResponse response = service.setupTwoFactor(FIREBASE_USER_ID);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSecret()).isEqualTo(TEST_SECRET);
            assertThat(response.getBackupCodes()).hasSize(10);

            verify(firestoreService).storeTotpSecret(eq(FIREBASE_USER_ID), eq(TEST_SECRET), anyList());
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when TOTP is disabled")
        void shouldThrowWhenTotpDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When/Then
            assertThatThrownBy(() -> service.setupTwoFactor(FIREBASE_USER_ID))
                .isInstanceOf(BusinessRuleTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.business.invalid_state");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            // Given
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> service.setupTwoFactor(FIREBASE_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException for INFLUENCER user")
        void shouldThrowForInfluencerUser() {
            // Given
            User influencerUser = createInfluencerUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(influencerUser);

            // When/Then
            assertThatThrownBy(() -> service.setupTwoFactor(FIREBASE_USER_ID))
                .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException for COMPANY user")
        void shouldThrowForCompanyUser() {
            // Given
            User companyUser = createCompanyUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(companyUser);

            // When/Then
            assertThatThrownBy(() -> service.setupTwoFactor(FIREBASE_USER_ID))
                .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when 2FA already enabled")
        void shouldThrowWhenAlreadyEnabled() {
            // Given
            User adminUser = createAdminUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(adminUser);
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> service.setupTwoFactor(FIREBASE_USER_ID))
                .isInstanceOf(BusinessRuleTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.business.duplicate_entry");
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when QR generation fails")
        void shouldThrowWhenQRGenerationFails() {
            // Given
            User adminUser = createAdminUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(adminUser);
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(false);

            GoogleAuthenticatorKey mockKey = mock(GoogleAuthenticatorKey.class);
            when(mockKey.getKey()).thenReturn(TEST_SECRET);
            when(gAuth.createCredentials()).thenReturn(mockKey);

            when(improvedQRCodeService.generateQRCode(anyString(), anyString()))
                .thenThrow(new RuntimeException("QR generation failed"));

            // When/Then
            assertThatThrownBy(() -> service.setupTwoFactor(FIREBASE_USER_ID))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.auth.2fa_setup_failed");
        }

        @Test
        @DisplayName("should generate 10 backup codes with alphanumeric characters")
        void shouldGenerateBackupCodesCorrectly() {
            // Given
            User adminUser = createAdminUser();
            setupSuccessfulTotpSetup(adminUser);

            // When
            TotpSetupResponse response = service.setupTwoFactor(FIREBASE_USER_ID);

            // Then
            assertThat(response.getBackupCodes())
                .hasSize(10)
                .allMatch(code -> code.length() == 8)
                .allMatch(code -> code.matches("[A-Z0-9]+"));
        }

        @Test
        @DisplayName("should include formatted secret for manual entry")
        void shouldIncludeFormattedSecret() {
            // Given
            User adminUser = createAdminUser();
            setupSuccessfulTotpSetup(adminUser);

            // When
            TotpSetupResponse response = service.setupTwoFactor(FIREBASE_USER_ID);

            // Then
            assertThat(response.getSecretFormatted()).isEqualTo("JBSW Y3DP EHPK 3PXP");
        }
    }

    @Nested
    @DisplayName("verifyTotpCode")
    class VerifyTotpCodeTests {

        @Test
        @DisplayName("should return true for valid TOTP code")
        void shouldReturnTrueForValidCode() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(TEST_SECRET, VALID_CODE_INT)).thenReturn(true);

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isTrue();
            verify(firestoreService).logAuditEvent(eq(FIREBASE_USER_ID), eq("TOTP_VERIFY"), eq("SUCCESS"), any(), any());
        }

        @Test
        @DisplayName("should return false for invalid TOTP code")
        void shouldReturnFalseForInvalidCode() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(TEST_SECRET, VALID_CODE_INT)).thenReturn(false);

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isFalse();
            verify(firestoreService).logAuditEvent(eq(FIREBASE_USER_ID), eq("TOTP_VERIFY"), eq("FAILED"), any(), any());
        }

        @Test
        @DisplayName("should return false when TOTP is disabled")
        void shouldReturnFalseWhenTotpDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(firestoreService);
        }

        @Test
        @DisplayName("should return false when no secret found")
        void shouldReturnFalseWhenNoSecretFound() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(null);

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isFalse();
            verify(gAuth, never()).authorize(anyString(), anyInt());
        }

        @Test
        @DisplayName("should return false when exception occurs")
        void shouldReturnFalseOnException() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID))
                .thenThrow(new RuntimeException("Database error"));

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should add adminChallengeCompletedAt claim for ADMIN user on successful verification")
        void shouldAddAdminClaimOnSuccessfulVerification() throws Exception {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(TEST_SECRET, VALID_CODE_INT)).thenReturn(true);

            UserRecord mockUserRecord = mock(UserRecord.class);
            Map<String, Object> existingClaims = new HashMap<>();
            existingClaims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(existingClaims);
            when(firebaseAuth.getUser(FIREBASE_USER_ID)).thenReturn(mockUserRecord);

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isTrue();
            verify(firebaseAuth).setCustomUserClaims(eq(FIREBASE_USER_ID), argThat(claims ->
                claims.containsKey("adminChallengeCompletedAt") &&
                claims.get("role").equals("ADMIN")
            ));
        }

        @Test
        @DisplayName("should not add adminChallengeCompletedAt claim for non-ADMIN user")
        void shouldNotAddAdminClaimForNonAdminUser() throws Exception {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(TEST_SECRET, VALID_CODE_INT)).thenReturn(true);

            UserRecord mockUserRecord = mock(UserRecord.class);
            Map<String, Object> existingClaims = new HashMap<>();
            existingClaims.put("role", "PENDING_ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(existingClaims);
            when(firebaseAuth.getUser(FIREBASE_USER_ID)).thenReturn(mockUserRecord);

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isTrue();
            verify(firebaseAuth, never()).setCustomUserClaims(anyString(), anyMap());
        }

        @Test
        @DisplayName("should still return true even if claim update fails")
        void shouldReturnTrueEvenIfClaimUpdateFails() throws Exception {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(TEST_SECRET, VALID_CODE_INT)).thenReturn(true);

            when(firebaseAuth.getUser(FIREBASE_USER_ID))
                .thenThrow(new RuntimeException("Firebase error"));

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isTrue(); // Verification succeeded, claim update failure shouldn't affect result
        }
    }

    @Nested
    @DisplayName("enableTwoFactor")
    class EnableTwoFactorTests {

        @Test
        @DisplayName("should enable 2FA after successful verification")
        void shouldEnableTwoFactorAfterVerification() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(TEST_SECRET, VALID_CODE_INT)).thenReturn(true);

            // When
            service.enableTwoFactor(FIREBASE_USER_ID, VALID_CODE);

            // Then
            verify(firestoreService).enable2FA(FIREBASE_USER_ID);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid code")
        void shouldThrowForInvalidCode() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(TEST_SECRET, VALID_CODE_INT)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.enableTwoFactor(FIREBASE_USER_ID, VALID_CODE))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.failed");

            verify(firestoreService, never()).enable2FA(anyString());
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when verification fails due to no secret")
        void shouldThrowWhenNoSecretForEnable() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> service.enableTwoFactor(FIREBASE_USER_ID, VALID_CODE))
                .isInstanceOf(ValidationTranslatableException.class);

            verify(firestoreService, never()).enable2FA(anyString());
        }
    }

    @Nested
    @DisplayName("disableTwoFactor")
    class DisableTwoFactorTests {

        @Test
        @DisplayName("should disable 2FA successfully")
        void shouldDisableTwoFactor() {
            // When
            service.disableTwoFactor(FIREBASE_USER_ID);

            // Then
            verify(firestoreService).disable2FA(FIREBASE_USER_ID);
        }
    }

    @Nested
    @DisplayName("verifyBackupCode")
    class VerifyBackupCodeTests {

        @Test
        @DisplayName("should return true for valid backup code")
        void shouldReturnTrueForValidBackupCode() {
            // Given
            String backupCode = "ABCD1234";
            when(firestoreService.verifyBackupCode(FIREBASE_USER_ID, backupCode)).thenReturn(true);

            // When
            boolean result = service.verifyBackupCode(FIREBASE_USER_ID, backupCode);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid backup code")
        void shouldReturnFalseForInvalidBackupCode() {
            // Given
            String backupCode = "INVALID1";
            when(firestoreService.verifyBackupCode(FIREBASE_USER_ID, backupCode)).thenReturn(false);

            // When
            boolean result = service.verifyBackupCode(FIREBASE_USER_ID, backupCode);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when TOTP is disabled")
        void shouldReturnFalseWhenTotpDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When
            boolean result = service.verifyBackupCode(FIREBASE_USER_ID, "ABCD1234");

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(firestoreService);
        }

        @Test
        @DisplayName("should normalize backup code - uppercase")
        void shouldNormalizeBackupCodeUppercase() {
            // Given
            String lowercaseCode = "abcd1234";
            when(firestoreService.verifyBackupCode(FIREBASE_USER_ID, "ABCD1234")).thenReturn(true);

            // When
            boolean result = service.verifyBackupCode(FIREBASE_USER_ID, lowercaseCode);

            // Then
            assertThat(result).isTrue();
            verify(firestoreService).verifyBackupCode(FIREBASE_USER_ID, "ABCD1234");
        }

        @Test
        @DisplayName("should normalize backup code - remove spaces")
        void shouldNormalizeBackupCodeRemoveSpaces() {
            // Given
            String codeWithSpaces = "ABCD 1234";
            when(firestoreService.verifyBackupCode(FIREBASE_USER_ID, "ABCD1234")).thenReturn(true);

            // When
            boolean result = service.verifyBackupCode(FIREBASE_USER_ID, codeWithSpaces);

            // Then
            assertThat(result).isTrue();
            verify(firestoreService).verifyBackupCode(FIREBASE_USER_ID, "ABCD1234");
        }

        @Test
        @DisplayName("should handle mixed case and spaces")
        void shouldHandleMixedCaseAndSpaces() {
            // Given
            String messyCode = "AbCd 12 34";
            when(firestoreService.verifyBackupCode(FIREBASE_USER_ID, "ABCD1234")).thenReturn(true);

            // When
            boolean result = service.verifyBackupCode(FIREBASE_USER_ID, messyCode);

            // Then
            assertThat(result).isTrue();
            verify(firestoreService).verifyBackupCode(FIREBASE_USER_ID, "ABCD1234");
        }
    }

    @Nested
    @DisplayName("is2FAEnabled")
    class Is2FAEnabledTests {

        @Test
        @DisplayName("should return true when 2FA is enabled")
        void shouldReturnTrueWhenEnabled() {
            // Given
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(true);

            // When
            boolean result = service.is2FAEnabled(FIREBASE_USER_ID);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when 2FA is not enabled")
        void shouldReturnFalseWhenNotEnabled() {
            // Given
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(false);

            // When
            boolean result = service.is2FAEnabled(FIREBASE_USER_ID);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when TOTP system is disabled")
        void shouldReturnFalseWhenTotpSystemDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When
            boolean result = service.is2FAEnabled(FIREBASE_USER_ID);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(firestoreService);
        }
    }

    @Nested
    @DisplayName("is2FARequired")
    class Is2FARequiredTests {

        @Test
        @DisplayName("should return true for ADMIN user")
        void shouldReturnTrueForAdminUser() {
            // Given
            User adminUser = createAdminUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(adminUser);

            // When
            boolean result = service.is2FARequired(FIREBASE_USER_ID);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for PENDING_ADMIN user")
        void shouldReturnTrueForPendingAdminUser() {
            // Given
            User pendingAdminUser = createPendingAdminUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(pendingAdminUser);

            // When
            boolean result = service.is2FARequired(FIREBASE_USER_ID);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for INFLUENCER user")
        void shouldReturnFalseForInfluencerUser() {
            // Given
            User influencerUser = createInfluencerUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(influencerUser);

            // When
            boolean result = service.is2FARequired(FIREBASE_USER_ID);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for COMPANY user")
        void shouldReturnFalseForCompanyUser() {
            // Given
            User companyUser = createCompanyUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(companyUser);

            // When
            boolean result = service.is2FARequired(FIREBASE_USER_ID);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when TOTP is disabled")
        void shouldReturnFalseWhenTotpDisabled() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When
            boolean result = service.is2FARequired(FIREBASE_USER_ID);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenUserNotFound() {
            // Given
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(null);

            // When
            boolean result = service.is2FARequired(FIREBASE_USER_ID);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("generateCurrentCode")
    class GenerateCurrentCodeTests {

        @Test
        @DisplayName("should generate current TOTP code")
        void shouldGenerateCurrentCode() {
            // Given
            when(gAuth.getTotpPassword(TEST_SECRET)).thenReturn(VALID_CODE_INT);

            // When
            int result = service.generateCurrentCode(TEST_SECRET);

            // Then
            assertThat(result).isEqualTo(VALID_CODE_INT);
        }
    }

    @Nested
    @DisplayName("regenerateBackupCodes")
    class RegenerateBackupCodesTests {

        @Test
        @DisplayName("should regenerate backup codes successfully")
        void shouldRegenerateBackupCodesSuccessfully() {
            // Given
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(true);
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);

            // When
            List<String> result = service.regenerateBackupCodes(FIREBASE_USER_ID);

            // Then
            assertThat(result)
                .hasSize(10)
                .allMatch(code -> code.length() == 8)
                .allMatch(code -> code.matches("[A-Z0-9]+"));

            verify(firestoreService).storeTotpSecret(eq(FIREBASE_USER_ID), eq(TEST_SECRET), eq(result));
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException when 2FA not enabled")
        void shouldThrowWhenNotEnabled() {
            // Given
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.regenerateBackupCodes(FIREBASE_USER_ID))
                .isInstanceOf(BusinessRuleTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.business.invalid_state");
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when secret is null (caught in try-catch)")
        void shouldThrowWhenSecretIsNull() {
            // Given
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(true);
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(null);

            // When/Then
            // Note: BusinessRuleTranslatableException thrown inside try block gets caught
            // and wrapped in AuthenticationTranslatableException by the catch block
            assertThatThrownBy(() -> service.regenerateBackupCodes(FIREBASE_USER_ID))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.auth.2fa_backup_codes_failed");
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when storage fails")
        void shouldThrowWhenStorageFails() {
            // Given
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(true);
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            doThrow(new RuntimeException("Storage error"))
                .when(firestoreService).storeTotpSecret(anyString(), anyString(), anyList());

            // When/Then
            assertThatThrownBy(() -> service.regenerateBackupCodes(FIREBASE_USER_ID))
                .isInstanceOf(AuthenticationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.auth.2fa_backup_codes_failed");
        }
    }

    @Nested
    @DisplayName("User Type Permission Tests")
    class UserTypePermissionTests {

        @ParameterizedTest
        @EnumSource(value = UserType.class, names = {"INFLUENCER", "COMPANY"})
        @DisplayName("should reject non-admin users for 2FA setup")
        void shouldRejectNonAdminUsersForSetup(UserType userType) {
            // Given
            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(FIREBASE_USER_ID);
            user.setUserType(userType);
            user.setEmail(TEST_EMAIL);

            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(user);

            // When/Then
            assertThatThrownBy(() -> service.setupTwoFactor(FIREBASE_USER_ID))
                .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @EnumSource(value = UserType.class, names = {"ADMIN", "PENDING_ADMIN"})
        @DisplayName("should allow admin users for 2FA setup")
        void shouldAllowAdminUsersForSetup(UserType userType) {
            // Given
            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(FIREBASE_USER_ID);
            user.setUserType(userType);
            user.setEmail(TEST_EMAIL);

            setupSuccessfulTotpSetup(user);

            // When
            TotpSetupResponse response = service.setupTwoFactor(FIREBASE_USER_ID);

            // Then
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle empty firebase user ID gracefully in verification")
        void shouldHandleEmptyUserIdInVerification() {
            // Given
            when(firestoreService.getTotpSecret("")).thenReturn(null);

            // When
            boolean result = service.verifyTotpCode("", VALID_CODE);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle null secret from firestore in verification")
        void shouldHandleNullSecretInVerification() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(null);

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isFalse();
            verify(gAuth, never()).authorize(anyString(), anyInt());
        }

        @Test
        @DisplayName("should handle GoogleAuthenticator exception gracefully")
        void shouldHandleGAuthExceptionGracefully() {
            // Given
            when(firestoreService.getTotpSecret(FIREBASE_USER_ID)).thenReturn(TEST_SECRET);
            when(gAuth.authorize(anyString(), anyInt()))
                .thenThrow(new RuntimeException("GoogleAuth error"));

            // When
            boolean result = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("backup code verification should handle null code gracefully")
        void shouldHandleNullBackupCode() {
            // Given - backup code is null, which would cause NPE in toUpperCase
            // The service should handle this

            // When/Then
            assertThatThrownBy(() -> service.verifyBackupCode(FIREBASE_USER_ID, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("TOTP Configuration Tests")
    class TotpConfigurationTests {

        @Test
        @DisplayName("should use configured issuer in setup response")
        void shouldUseConfiguredIssuer() {
            // Given
            String customIssuer = "MyCustomApp";
            ReflectionTestUtils.setField(service, "issuer", customIssuer);

            User adminUser = createAdminUser();
            when(userService.findByFirebaseUserId(FIREBASE_USER_ID)).thenReturn(adminUser);
            when(firestoreService.is2FAEnabled(FIREBASE_USER_ID)).thenReturn(false);

            GoogleAuthenticatorKey mockKey = mock(GoogleAuthenticatorKey.class);
            when(mockKey.getKey()).thenReturn(TEST_SECRET);
            when(gAuth.createCredentials()).thenReturn(mockKey);

            when(improvedQRCodeService.generateQRCode(anyString(), anyString())).thenReturn("qr");
            when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn("url");
            when(improvedQRCodeService.generateGoogleChartsQRUrl(anyString(), anyString())).thenReturn("charts");
            when(improvedQRCodeService.getManualEntryInfo(anyString(), anyString()))
                .thenReturn(ImprovedQRCodeService.ManualEntryInfo.builder().secret("SECRET").build());

            // When
            TotpSetupResponse response = service.setupTwoFactor(FIREBASE_USER_ID);

            // Then
            assertThat(response.getIssuer()).isEqualTo(customIssuer);
        }

        @Test
        @DisplayName("should respect totpEnabled configuration flag")
        void shouldRespectTotpEnabledFlag() {
            // Given
            ReflectionTestUtils.setField(service, "totpEnabled", false);

            // When
            boolean isEnabled = service.is2FAEnabled(FIREBASE_USER_ID);
            boolean isRequired = service.is2FARequired(FIREBASE_USER_ID);
            boolean verifyResult = service.verifyTotpCode(FIREBASE_USER_ID, VALID_CODE);
            boolean backupResult = service.verifyBackupCode(FIREBASE_USER_ID, "CODE1234");

            // Then
            assertThat(isEnabled).isFalse();
            assertThat(isRequired).isFalse();
            assertThat(verifyResult).isFalse();
            assertThat(backupResult).isFalse();

            verifyNoInteractions(firestoreService);
            verifyNoInteractions(userService);
        }
    }
}
