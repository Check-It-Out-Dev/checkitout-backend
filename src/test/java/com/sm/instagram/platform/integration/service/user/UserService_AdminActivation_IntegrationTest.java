package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Regression test for the admin-logout-on-activation bug (2026-05-31).
 *
 * <p><b>Reproduction</b> (BE log {@code REQ-d92c36d8}): an admin set a user {@code IN_VALIDATION ->
 * ACTIVE}; the deferred Firebase claim-write for the <i>target</i> user failed (target
 * {@code E2E_CONSENT_…} absent from Firebase); {@code UserService} rethrew it as
 * {@code AuthenticationTranslatableException} -> HTTP <b>401</b> -> the FE logged the <i>admin</i>
 * out. Because the throw happened inside the {@code @Transactional} patch (after
 * {@code saveAndFlush}), the activation was also rolled back — the target silently stayed
 * {@code IN_VALIDATION}.
 *
 * <p>A target-side Firebase claim failure is NOT the admin's authentication failure. Postgres is the
 * source of truth (the target's bumped tokenVersion forces a claim re-sync on their next token
 * refresh), so the deferred Firebase write must be best-effort: it must neither fail the admin's
 * request (401/logout) nor roll back the activation.
 *
 * <p>This reproduces faithfully with no mocking: the test user does not exist in Firebase, so the
 * real {@code setCustomUserClaims} call fails — the exact prod condition. (Mocking {@code FirebaseAuth}
 * is not viable: creating the real bean is what initializes {@code FirebaseApp}, which {@code Firestore}
 * depends on, so a {@code @MockBean} collapses the security context.) The pre-existing
 * {@code UserService_Patch} suite deliberately used <i>same-status</i> patches to avoid the Firebase
 * claim-write, so the failing-claim-write path had ZERO coverage — this fills that gap.
 */
@DisplayName("UserService - Admin activation (Firebase claim-write failure regression)")
class UserService_AdminActivation_IntegrationTest extends UserServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin activating a user whose Firebase claim-write fails: admin is NOT 401'd and the activation persists")
    void adminActivationToleratesFirebaseClaimWriteFailure() {
        authenticateAs(testAdmin);
        // Random Firebase UID that does not exist in Firebase -> the deferred setCustomUserClaims fails.
        User target = createUserWithStatus(
                "ACTIVATE-" + UUID.randomUUID(), UserType.INFLUENCER, AccountStatus.IN_VALIDATION);

        Map<String, Object> updates = new HashMap<>();
        updates.put("accountStatus", "ACTIVE");

        // Must NOT throw (AuthenticationTranslatableException -> 401 -> admin logout). RED before the fix.
        assertThatNoException().isThrownBy(() -> userService.patch(target.getId(), updates));

        // Activation must persist despite the best-effort Firebase claim failure (PG is the source of truth).
        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
