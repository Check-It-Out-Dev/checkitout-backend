package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.auth.controller.TestAuthController;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.sandbox.SandboxPersonaPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /test/auth/provision-totp} gives an actor a known TOTP secret so a scenario can
 * generate a valid code without a Google credential anywhere in reach.
 *
 * <p>Three things are worth holding still. It must refuse on the public sandbox -- every other
 * endpoint on this controller is shaped by who may sign in, and this one would let a visitor plant a
 * second factor on somebody else's persona. It must be idempotent, because re-running the seeder
 * must not rotate a secret a suite has already read. And it must go through
 * {@link TotpFirestoreService} rather than write Firestore itself, so the ciphertext is whatever the
 * configured cipher produces -- Cloud KMS where a credential exists, the local AES-GCM cipher where
 * {@code gcp.kms.enabled=false}, which is the case this exists for.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestAuthProvisionTotpUnitTest {

    private static final String UID = "85VJgS6shAWTqby4rHypN355RWv2";
    /** RFC 4226/6238 standard vector; the suite computes codes from the same string. */
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Mock
    private SandboxPersonaPolicy sandboxPersonaPolicy;

    @Mock
    private TotpFirestoreService totpFirestoreService;

    @InjectMocks
    private TestAuthController controller;

    @Test
    @DisplayName("the public sandbox does not have this endpoint at all")
    void refusedWhenTheSandboxGuardIsOn() {
        when(sandboxPersonaPolicy.isEnabled()).thenReturn(true);

        ResponseEntity<?> response = controller.provisionTotp(
                new TestAuthController.ProvisionTotpRequest(UID, SECRET));

        // 404 rather than 403: a visitor should not learn that provisioning exists here.
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(totpFirestoreService, never()).storeTotpSecret(anyString(), anyString(), any());
        verify(totpFirestoreService, never()).enable2FA(anyString());
    }

    @Test
    @DisplayName("provisions through the application's own service, and enables 2FA")
    void provisionsThroughTheService() {
        when(sandboxPersonaPolicy.isEnabled()).thenReturn(false);
        when(totpFirestoreService.totpSecretExists(UID)).thenReturn(false);

        ResponseEntity<?> response = controller.provisionTotp(
                new TestAuthController.ProvisionTotpRequest(UID, SECRET));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).asString().contains("provisioned=true");
        verify(totpFirestoreService).storeTotpSecret(UID, SECRET, List.of("11111111", "22222222", "33333333"));
        verify(totpFirestoreService).enable2FA(UID);
    }

    @Test
    @DisplayName("a secret already in place is left alone, so re-seeding cannot rotate it")
    void idempotent() {
        when(sandboxPersonaPolicy.isEnabled()).thenReturn(false);
        when(totpFirestoreService.totpSecretExists(UID)).thenReturn(true);

        ResponseEntity<?> response = controller.provisionTotp(
                new TestAuthController.ProvisionTotpRequest(UID, SECRET));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).asString().contains("provisioned=false");
        verify(totpFirestoreService, never()).storeTotpSecret(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a request without a uid or a secret is a bad request, not a silent no-op")
    void rejectsIncompleteRequests() {
        when(sandboxPersonaPolicy.isEnabled()).thenReturn(false);

        assertThat(controller.provisionTotp(new TestAuthController.ProvisionTotpRequest("  ", SECRET))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(controller.provisionTotp(new TestAuthController.ProvisionTotpRequest(UID, null))
                .getStatusCode().value()).isEqualTo(400);
        verify(totpFirestoreService, never()).storeTotpSecret(anyString(), anyString(), any());
    }
}
