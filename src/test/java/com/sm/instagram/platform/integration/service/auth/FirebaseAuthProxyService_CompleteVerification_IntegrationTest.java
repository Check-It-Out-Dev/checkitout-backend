package com.sm.instagram.platform.integration.service.auth;

import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.dto.AuthOperationResponse;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for completeVerification() — the oobCode-based email verification.
 * <p>
 * Validates that completeVerification() identifies users from the oobCode lookup
 * instead of SecurityContextHolder (NTF-001 fix). No active session required.
 * <p>
 * EmailVerificationService is @MockBean (inherited from base) — lookupOobCode() is stubbed.
 * FirebaseAuth is @MockBean (inherited from base) — updateUser() is stubbed.
 */
@DisplayName("FirebaseAuthProxyService.completeVerification Integration Tests")
class FirebaseAuthProxyService_CompleteVerification_IntegrationTest extends FirebaseAuthProxyServiceIntegrationTestBase {

    private User unverifiedInfluencer;

    @BeforeEach
    void setUpVerificationData() throws Exception {
        unverifiedInfluencer = createUnverifiedInfluencer(
                "uid-verify-" + System.nanoTime(),
                "verify-" + System.nanoTime() + "@test.com"
        );
        flushAndClear();

        // Default: FirebaseAuth.updateUser succeeds
        when(firebaseAuth.updateUser(any(UserRecord.UpdateRequest.class)))
                .thenReturn(mock(UserRecord.class));
    }

    /**
     * Stubs emailVerificationService.lookupOobCode() to return [firebaseUid, email].
     */
    private void stubOobCodeLookup(String oobCode, String firebaseUid, String email) {
        when(emailVerificationService.lookupOobCode(oobCode))
                .thenReturn(new String[]{firebaseUid, email});
    }

    @Nested
    @DisplayName("Successful Verification Without Session")
    class SuccessfulVerification {

        @Test
        @DisplayName("should verify email using oobCode lookup — no SecurityContext required")
        void shouldVerifyEmail_usingOobCodeLookup_noSessionRequired() {
            String oobCode = "oob-no-session-" + System.nanoTime();
            stubOobCodeLookup(oobCode, unverifiedInfluencer.getFirebaseUserId(), unverifiedInfluencer.getEmail());
            clearAuthentication();

            AuthOperationResponse response = firebaseAuthProxyService.completeVerification(oobCode, null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).containsEntry("email", unverifiedInfluencer.getEmail());
            assertThat(response.getData()).containsEntry("userType", "INFLUENCER");
        }

        @Test
        @DisplayName("should verify email and set password for influencer")
        void shouldVerifyAndSetPassword_forInfluencer() throws Exception {
            String oobCode = "oob-with-pwd-" + System.nanoTime();
            stubOobCodeLookup(oobCode, unverifiedInfluencer.getFirebaseUserId(), unverifiedInfluencer.getEmail());
            clearAuthentication();

            AuthOperationResponse response = firebaseAuthProxyService.completeVerification(oobCode, "SecurePass123!");

            assertThat(response.isSuccess()).isTrue();
            verify(firebaseAuth).updateUser(any(UserRecord.UpdateRequest.class));
        }

        @Test
        @DisplayName("should activate INFLUENCER from IN_VALIDATION on email verification")
        void shouldActivateInfluencer_fromInValidation() {
            String oobCode = "oob-activate-" + System.nanoTime();
            stubOobCodeLookup(oobCode, unverifiedInfluencer.getFirebaseUserId(), unverifiedInfluencer.getEmail());
            clearAuthentication();

            firebaseAuthProxyService.completeVerification(oobCode, "SecurePass123!");

            User reloaded = userRepository.findByFirebaseUserId(unverifiedInfluencer.getFirebaseUserId()).orElseThrow();
            assertThat(reloaded.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(reloaded.getEmailVerified()).isTrue();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should reject invalid oobCode (not in Redis)")
        void shouldReject_invalidOobCode() {
            when(emailVerificationService.lookupOobCode("nonexistent-oob-code"))
                    .thenReturn(null);
            clearAuthentication();

            assertThatThrownBy(() ->
                    firebaseAuthProxyService.completeVerification("nonexistent-oob-code", null)
            )
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }

        @Test
        @DisplayName("should reject when oobCode maps to non-existent user")
        void shouldReject_userNotFound() {
            String oobCode = "oob-no-user-" + System.nanoTime();
            stubOobCodeLookup(oobCode, "nonexistent-firebase-uid", "nobody@test.com");
            clearAuthentication();

            assertThatThrownBy(() ->
                    firebaseAuthProxyService.completeVerification(oobCode, null)
            )
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should reject when stored email doesn't match DB email")
        void shouldReject_emailMismatch() {
            String oobCode = "oob-mismatch-" + System.nanoTime();
            stubOobCodeLookup(oobCode, unverifiedInfluencer.getFirebaseUserId(), "wrong-email@test.com");
            clearAuthentication();

            assertThatThrownBy(() ->
                    firebaseAuthProxyService.completeVerification(oobCode, null)
            )
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasMessageContaining("error.auth.invalid_action_code");
        }
    }

    @Nested
    @DisplayName("Company Activation Gating")
    class CompanyActivation {

        @Test
        @DisplayName("should NOT activate COMPANY without company data verified")
        void shouldNotActivateCompany_withoutCompanyData() {
            User companyUser = createTestUser(
                    "uid-company-" + System.nanoTime(),
                    UserType.COMPANY,
                    "company-" + System.nanoTime() + "@test.com",
                    "Test", "Company",
                    AccountStatus.IN_VALIDATION
            );
            flushAndClear();

            String oobCode = "oob-company-" + System.nanoTime();
            stubOobCodeLookup(oobCode, companyUser.getFirebaseUserId(), companyUser.getEmail());
            clearAuthentication();

            firebaseAuthProxyService.completeVerification(oobCode, null);

            User reloaded = userRepository.findByFirebaseUserId(companyUser.getFirebaseUserId()).orElseThrow();
            assertThat(reloaded.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
            assertThat(reloaded.getEmailVerified()).isTrue();
        }
    }
}
