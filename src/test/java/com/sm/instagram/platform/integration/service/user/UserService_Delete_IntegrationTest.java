package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.user.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserService.delete() and deletePermanently() methods.
 * Tests soft delete (archival) and hard delete scenarios with permission checks.
 */
@DisplayName("UserService - Delete Operations")
class UserService_Delete_IntegrationTest extends UserServiceIntegrationTestBase {

    @Autowired
    private UserAccountOrchestrator userAccountOrchestrator;

    @Nested
    @DisplayName("delete() - Soft Delete")
    class SoftDelete {

        @Test
        @DisplayName("Admin can soft delete any user")
        void adminSoftDeletesAnyUser() {
            // Given
            User userToDelete = createUserWithStatus("delete-test-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            Long userId = userToDelete.getId();
            authenticateAs(testAdmin);

            // When
            userService.delete(userId);

            // Then
            User deletedUser = userRepository.findById(userId).orElseThrow();
            assertThat(deletedUser.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
        }

        @Test
        @DisplayName("Non-owner cannot soft delete other user")
        void nonOwnerCannotSoftDeleteOther() {
            // Given
            User otherUser = createUserWithStatus("other-delete-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> userService.delete(otherUser.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Soft delete increments token version")
        void softDeleteIncrementsTokenVersion() {
            // Given
            User userToDelete = createUserWithStatus("token-version-test-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            Long userId = userToDelete.getId();
            Long originalTokenVersion = userToDelete.getTokenVersion();
            authenticateAs(testAdmin);

            // When
            userService.delete(userId);

            // Then
            User deletedUser = userRepository.findById(userId).orElseThrow();
            assertThat(deletedUser.getTokenVersion()).isGreaterThan(originalTokenVersion);
        }
    }

    @Nested
    @DisplayName("delete() - Company Archival")
    class CompanyArchival {

        @Test
        @DisplayName("Company soft delete anonymizes data")
        void companySoftDeleteAnonymizesData() {
            // Given
            User company = createUserWithStatus("company-delete-" + System.currentTimeMillis(),
                    UserType.COMPANY, AccountStatus.ACTIVE);
            company.setFirstName("OriginalFirst");
            company.setLastName("OriginalLast");
            company.setPhoneNumber("+48123456789");
            userRepository.save(company);

            Long userId = company.getId();
            authenticateAs(testAdmin);

            // When
            userService.delete(userId);

            // Then
            User deletedCompany = userRepository.findById(userId).orElseThrow();
            assertThat(deletedCompany.getFirstName()).isEqualTo("N/A");
            assertThat(deletedCompany.getLastName()).isEqualTo("N/A");
            assertThat(deletedCompany.getPhoneNumber()).isNull();
            assertThat(deletedCompany.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
        }
    }

    @Nested
    @DisplayName("deletePermanently() - Hard Delete")
    class HardDelete {

        @Test
        @DisplayName("Admin can permanently delete user")
        void adminPermanentlyDeletesUser() {
            // Given
            User userToDelete = createUserWithStatus("perm-delete-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            Long userId = userToDelete.getId();
            authenticateAs(testAdmin);

            // When
            userService.deletePermanently(userId);

            // Then
            assertThat(userRepository.findById(userId)).isEmpty();
        }

        @Test
        @DisplayName("Non-admin cannot permanently delete")
        void nonAdminCannotPermanentlyDelete() {
            // Given
            User userToDelete = createUserWithStatus("non-admin-perm-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> userService.deletePermanently(userToDelete.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Company cannot permanently delete even self")
        void companyCannotPermanentlyDeleteSelf() {
            // Given - company tries to permanently delete their own account
            authenticateAs(testCompany);

            // When/Then - only soft delete is allowed for self
            assertThatThrownBy(() -> userService.deletePermanently(testCompany.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("Deletion Eligibility")
    class DeletionEligibility {

        @Test
        @DisplayName("User without blockers can be deleted")
        void userWithoutBlockersCanBeDeleted() {
            // Given
            User user = createUserWithStatus("eligible-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testAdmin);
            setUpMockHttpContext();

            // When
            var eligibility = userService.checkDeletionEligibilityById(user.getId());

            // Then
            assertThat(eligibility.isCanSoftDelete()).isTrue();
        }

        @Test
        @DisplayName("checkMyDeletionEligibility returns current user's eligibility")
        void checkMyDeletionEligibility() {
            // Given
            User user = createUserWithStatus("my-eligibility-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(user);
            setUpMockHttpContext();

            // When
            var eligibility = userService.checkMyDeletionEligibility();

            // Then
            assertThat(eligibility).isNotNull();
            assertThat(eligibility.getUserId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("checkDeletionEligibilityById - admin only")
        void checkDeletionEligibilityByIdAdminOnly() {
            // Given
            User targetUser = createUserWithStatus("target-eligibility-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testCompany);
            setUpMockHttpContext();

            // When/Then
            assertThatThrownBy(() -> userService.checkDeletionEligibilityById(targetUser.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }
}
