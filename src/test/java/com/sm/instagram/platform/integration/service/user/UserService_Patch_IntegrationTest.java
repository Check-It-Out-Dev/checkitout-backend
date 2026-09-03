package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for UserService.patch() method.
 * Tests partial updates with various fields, permissions, and restricted field handling.
 *
 * <p>The avatar is uploadId-only (pentest 3.1): {@code profilePicture} takes a
 * tracked upload id the caller owns, resolved to an own-bucket URL by the
 * (stubbed) {@code SignedUrlService} in {@link UserServiceIntegrationTestBase}
 * — {@link #AVATAR_UPLOAD_ID} → {@link #AVATAR_RESOLVED_URL}.
 *
 * Note: Tests involving owner patches that trigger Firebase claim updates via status changes
 * are tested through admin-only scenarios. Owner-triggered re-validation requires
 * Firebase mocking which is outside the scope of service integration tests.
 */
@DisplayName("UserService - Patch Operations")
class UserService_Patch_IntegrationTest extends UserServiceIntegrationTestBase {

    @Nested
    @DisplayName("patch() - Admin-Only Fields")
    class PatchAdminOnlyFields {

        @Test
        @DisplayName("Admin can patch accountStatus (same status - no Firebase trigger)")
        void adminPatchesAccountStatusSameValue() {
            // Given - Note: Actual status CHANGES trigger Firebase claim updates which aren't mocked
            // in integration tests. This test verifies admin has permission to patch accountStatus.
            testInfluencer.setAccountStatus(AccountStatus.IN_VALIDATION);
            userRepository.save(testInfluencer);

            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("accountStatus", "IN_VALIDATION"); // Same status - no Firebase call

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }

        @Test
        @DisplayName("Non-admin cannot patch accountStatus - throws exception")
        void nonAdminCannotPatchAccountStatus() {
            // Given
            authenticateAs(testInfluencer);
            Map<String, Object> updates = new HashMap<>();
            updates.put("accountStatus", "ACTIVE");

            // When/Then - patching someone else's status
            User otherUser = createUserWithStatus("other-user-" + System.currentTimeMillis(),
                    UserType.COMPANY, AccountStatus.IN_VALIDATION);

            // First, patch self - this should throw because non-admin cannot change accountStatus
            assertThatThrownBy(() -> userService.patch(testInfluencer.getId(), updates))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Admin can patch userType (same type - no Firebase trigger)")
        void adminPatchesUserTypeSameValue() {
            // Given - Note: Actual userType CHANGES trigger Firebase claim updates which aren't mocked
            // in integration tests. This test verifies admin has permission to patch userType.
            User influencer = createUserWithStatus("usertype-test-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("userType", "INFLUENCER"); // Same type - no Firebase call

            // When
            User result = userService.patch(influencer.getId(), updates);

            // Then
            assertThat(result.getUserType()).isEqualTo(UserType.INFLUENCER);
        }

        @Test
        @DisplayName("Non-admin cannot patch userType")
        void nonAdminCannotPatchUserType() {
            // Given
            authenticateAs(testInfluencer);
            Map<String, Object> updates = new HashMap<>();
            updates.put("userType", "COMPANY");

            // When/Then
            assertThatThrownBy(() -> userService.patch(testInfluencer.getId(), updates))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Admin can patch noteFromAdmin")
        void adminPatchesNoteFromAdmin() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("noteFromAdmin", "This is an admin note");

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then
            assertThat(result.getNoteFromAdmin()).isEqualTo("This is an admin note");
        }

        @Test
        @DisplayName("Non-admin patch of noteFromAdmin is silently ignored")
        void nonAdminNoteFromAdminIgnored() {
            // Given
            testInfluencer.setNoteFromAdmin("Original note");
            userRepository.save(testInfluencer);

            authenticateAs(testInfluencer);
            Map<String, Object> updates = new HashMap<>();
            updates.put("noteFromAdmin", "Should be ignored");
            updates.put("profilePicture", AVATAR_UPLOAD_ID); // valid upload → BE-derived URL

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then - noteFromAdmin unchanged, other field updated
            assertThat(result.getNoteFromAdmin()).isEqualTo("Original note");
            assertThat(result.getProfilePicture()).isEqualTo(AVATAR_RESOLVED_URL);
        }
    }

    @Nested
    @DisplayName("patch() - Regular Fields via Admin")
    class PatchRegularFieldsViaAdmin {

        @Test
        @DisplayName("Admin patches profilePicture — uploadId resolved with the caller's uid")
        void adminPatchesProfilePicture() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("profilePicture", AVATAR_UPLOAD_ID);

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then — the stored URL is the BE-derived one, and the resolver
            // was called with the AUTHENTICATED caller's uid (audit G1).
            assertThat(result.getProfilePicture()).isEqualTo(AVATAR_RESOLVED_URL);
            verify(signedUrlService).resolveOwnedUpload(eq(testAdmin.getFirebaseUserId()), eq(AVATAR_UPLOAD_ID));
        }

        @Test
        @DisplayName("A non-owned / unknown uploadId is rejected — avatar unchanged (pentest 3.1)")
        void profilePictureUnknownUploadRejected() {
            // The resolver rejects an upload the caller doesn't own; the reject
            // surfaces as a failed patch, so the avatar can never point at
            // another user's file or an arbitrary in-bucket path.
            authenticateAs(testAdmin);
            when(signedUrlService.resolveOwnedUpload(any(), eq("not-my-upload")))
                    .thenThrow(new ValidationTranslatableException("error.attachment.unknown_upload"));
            Map<String, Object> updates = new HashMap<>();
            updates.put("profilePicture", "not-my-upload");

            // When / Then
            assertThatThrownBy(() -> userService.patch(testInfluencer.getId(), updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Blank profilePicture clears the avatar")
        void blankProfilePictureClearsAvatar() {
            // Given — a pre-existing avatar (set directly on the entity).
            testInfluencer.setProfilePicture(AVATAR_RESOLVED_URL);
            userRepository.save(testInfluencer);

            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("profilePicture", "");

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then
            assertThat(result.getProfilePicture()).isNull();
        }

        @Test
        @DisplayName("Admin patches firstName (critical field)")
        void adminPatchesFirstName() {
            // Given
            testInfluencer.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(testInfluencer);

            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("firstName", "NewFirstName");

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then
            assertThat(result.getFirstName()).isEqualTo("NewFirstName");
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE); // Admin - no status change
        }

        @Test
        @DisplayName("Admin patches lastName (critical field)")
        void adminPatchesLastName() {
            // Given
            testInfluencer.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(testInfluencer);

            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastName", "NewLastName");

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then
            assertThat(result.getLastName()).isEqualTo("NewLastName");
        }

        // Removed: adminPatchesEmail - requires real Firebase to sync email change.
        // Integration tests use fake UIDs that don't exist in Firebase.
        // Email patch is covered by E2E profile-critical-consolidated.feature (TEST 3).

        @Test
        @DisplayName("Admin patches phoneNumber (critical field)")
        void adminPatchesPhoneNumber() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("phoneNumber", "+48111222333");

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then
            assertThat(result.getPhoneNumber()).isEqualTo("+48111222333");
        }

        @Test
        @DisplayName("Admin patches company-specific field (companyDescription)")
        void adminPatchesCompanyDescription() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("companyDescription", "Updated company description");

            // When
            User result = userService.patch(testCompany.getId(), updates);

            // Then
            assertThat(result.getCompanyDescription()).isEqualTo("Updated company description");
        }

        @Test
        @DisplayName("Admin patches NIP (critical field for company)")
        void adminPatchesNip() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("nip", "1234567890");

            // When
            User result = userService.patch(testCompany.getId(), updates);

            // Then
            assertThat(result.getNip()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("Admin patches multiple fields at once")
        void adminPatchesMultipleFields() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("firstName", "Multi");
            updates.put("lastName", "Patch");
            updates.put("profilePicture", AVATAR_UPLOAD_ID);

            // When
            User result = userService.patch(testInfluencer.getId(), updates);

            // Then
            assertThat(result.getFirstName()).isEqualTo("Multi");
            assertThat(result.getLastName()).isEqualTo("Patch");
            assertThat(result.getProfilePicture()).isEqualTo(AVATAR_RESOLVED_URL);
        }
    }

    @Nested
    @DisplayName("patch() - Validation Errors")
    class PatchValidationErrors {

        @Test
        @DisplayName("Unknown field throws ValidationTranslatableException")
        void unknownFieldThrowsException() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("unknownField", "value");

            // When/Then
            assertThatThrownBy(() -> userService.patch(testInfluencer.getId(), updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("patch() - Permission Checks")
    class PatchPermissions {

        @Test
        @DisplayName("Admin can patch any user")
        void adminCanPatchAnyUser() {
            // Given
            authenticateAs(testAdmin);
            Map<String, Object> updates = new HashMap<>();
            updates.put("profilePicture", AVATAR_UPLOAD_ID);

            // When
            User result = userService.patch(testCompany.getId(), updates);

            // Then
            assertThat(result.getProfilePicture()).isEqualTo(AVATAR_RESOLVED_URL);
        }

        @Test
        @DisplayName("Non-owner cannot patch other user")
        void nonOwnerCannotPatchOther() {
            // Given
            User otherUser = createUserWithStatus("other-patch-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testInfluencer);
            Map<String, Object> updates = new HashMap<>();
            updates.put("profilePicture", "https://cdn.example.com/attempt.jpg");

            // When/Then
            assertThatThrownBy(() -> userService.patch(otherUser.getId(), updates))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("patchAsDto()")
    class PatchAsDto {

        @Test
        @DisplayName("patchAsDto returns DTO")
        void patchAsDtoReturnsDto() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext();
            Map<String, Object> updates = new HashMap<>();
            updates.put("firstName", "DtoPatched");

            // When
            UserDtoOut result = userService.patchAsDto(testInfluencer.getId(), updates);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("DtoPatched");
        }
    }
}
