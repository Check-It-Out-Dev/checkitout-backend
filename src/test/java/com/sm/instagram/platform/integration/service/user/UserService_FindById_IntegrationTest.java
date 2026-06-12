package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserDtoOut;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserService.findById(), viewById(), and findByFirebaseUserId() methods.
 * Tests permission checks, data retrieval, and error handling.
 */
@DisplayName("UserService - FindById Operations")
class UserService_FindById_IntegrationTest extends UserServiceIntegrationTestBase {

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("Admin can find any user by ID")
        void adminFindsAnyUser() {
            // Given
            authenticateAs(testAdmin);

            // When
            User result = userService.findById(testInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testInfluencer.getId());
            assertThat(result.getFirebaseUserId()).isEqualTo(testInfluencer.getFirebaseUserId());
        }

        @Test
        @DisplayName("Company can find self by ID")
        void companyFindsSelf() {
            // Given
            authenticateAs(testCompany);

            // When
            User result = userService.findById(testCompany.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testCompany.getId());
        }

        @Test
        @DisplayName("Influencer can find self by ID")
        void influencerFindsSelf() {
            // Given
            authenticateAs(testInfluencer);

            // When
            User result = userService.findById(testInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("Company cannot find other company by ID")
        void companyCannotFindOtherCompany() {
            // Given
            User otherCompany = createUserWithStatus("other-company-uid", UserType.COMPANY, AccountStatus.ACTIVE);
            authenticateAs(testCompany);

            // When/Then
            assertThatThrownBy(() -> userService.findById(otherCompany.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Influencer cannot find company by ID")
        void influencerCannotFindCompany() {
            // Given
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> userService.findById(testCompany.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("User not found throws ResourceNotFoundException")
        void userNotFoundThrowsException() {
            // Given
            authenticateAs(testAdmin);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> userService.findById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("viewById()")
    class ViewById {

        @Test
        @DisplayName("Any authenticated user can view user by ID (public read)")
        void anyUserCanViewById() {
            // Given
            authenticateAs(testInfluencer);

            // When - viewing a company (different user type)
            User result = userService.viewById(testCompany.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testCompany.getId());
        }

        @Test
        @DisplayName("Influencer can view other influencer (public profile)")
        void influencerCanViewOtherInfluencer() {
            // Given
            User otherInfluencer = createUserWithStatus("other-influencer-uid", UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testInfluencer);

            // When
            User result = userService.viewById(otherInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(otherInfluencer.getId());
        }

        @Test
        @DisplayName("Company can view influencer profile")
        void companyCanViewInfluencer() {
            // Given
            authenticateAs(testCompany);

            // When
            User result = userService.viewById(testInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("viewById throws ResourceNotFoundException for non-existent user")
        void viewByIdUserNotFound() {
            // Given
            authenticateAs(testInfluencer);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> userService.viewById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByFirebaseUserId()")
    class FindByFirebaseUserId {

        @Test
        @DisplayName("Owner can find self by Firebase UID")
        void ownerFindsSelfByFirebaseUid() {
            // Given
            authenticateAs(testInfluencer);

            // When
            User result = userService.findByFirebaseUserId(testInfluencer.getFirebaseUserId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirebaseUserId()).isEqualTo(testInfluencer.getFirebaseUserId());
        }

        @Test
        @DisplayName("Admin can find any user by Firebase UID")
        void adminFindsAnyUserByFirebaseUid() {
            // Given
            authenticateAs(testAdmin);

            // When
            User result = userService.findByFirebaseUserId(testCompany.getFirebaseUserId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirebaseUserId()).isEqualTo(testCompany.getFirebaseUserId());
        }

        @Test
        @DisplayName("Non-owner cannot find other user by Firebase UID")
        void nonOwnerCannotFindOtherUser() {
            // Given
            authenticateAs(testCompany);

            // When/Then
            assertThatThrownBy(() -> userService.findByFirebaseUserId(testInfluencer.getFirebaseUserId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Firebase UID not found throws ResourceNotFoundException")
        void firebaseUidNotFoundThrowsException() {
            // Given
            authenticateAs(testAdmin);

            // When/Then
            assertThatThrownBy(() -> userService.findByFirebaseUserId("non-existent-firebase-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByIdAsDto()")
    class FindByIdAsDto {

        @Test
        @DisplayName("Admin can find user as DTO")
        void adminFindsUserAsDto() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext();

            // When
            UserDtoOut result = userService.findByIdAsDto(testInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("Owner can find self as DTO")
        void ownerFindsSelfAsDto() {
            // Given
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            // When
            UserDtoOut result = userService.findByIdAsDto(testInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testInfluencer.getId());
        }
    }

    @Nested
    @DisplayName("viewByIdAsDto()")
    class ViewByIdAsDto {

        @Test
        @DisplayName("Any user can view user profile as DTO")
        void anyUserCanViewAsDto() {
            // Given
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            // When
            UserDtoOut result = userService.viewByIdAsDto(testCompany.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testCompany.getId());
        }
    }
}
