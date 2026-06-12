package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.user.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserService.update() method.
 * Tests user updates with various scenarios including permissions, critical fields, and addresses.
 *
 * Note: Tests involving owner profile updates that trigger Firebase claim updates are tested
 * via admin updates (which don't trigger Firebase). Owner-triggered re-validation requires
 * Firebase mocking which is outside the scope of service integration tests.
 */
@DisplayName("UserService - Update Operations")
class UserService_Update_IntegrationTest extends UserServiceIntegrationTestBase {

    @Nested
    @DisplayName("update() - Permission Checks")
    class UpdatePermissions {

        @Test
        @DisplayName("Admin can update any user")
        void adminUpdatesAnyUser() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn updateDto = createUpdateDto(testInfluencer);
            updateDto.setFirstName("AdminUpdated");

            // When
            User result = userService.update(testInfluencer.getId(), updateDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("AdminUpdated");
        }

        @Test
        @DisplayName("Admin updates user's non-critical field")
        void adminUpdatesNonCriticalField() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn updateDto = createUpdateDto(testInfluencer);
            updateDto.setProfilePicture("https://cdn.example.com/new-avatar.jpg");

            // When
            User result = userService.update(testInfluencer.getId(), updateDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProfilePicture()).isEqualTo("https://cdn.example.com/new-avatar.jpg");
        }

        @Test
        @DisplayName("Non-owner cannot update other user")
        void nonOwnerCannotUpdateOther() {
            // Given
            User otherInfluencer = createUserWithStatus("other-inf-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            authenticateAs(testInfluencer);
            UserDtoIn updateDto = createUpdateDto(otherInfluencer);

            // When/Then
            assertThatThrownBy(() -> userService.update(otherInfluencer.getId(), updateDto))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Company cannot update other company")
        void companyCannotUpdateOtherCompany() {
            // Given
            User otherCompany = createUserWithStatus("other-company-" + System.currentTimeMillis(),
                    UserType.COMPANY, AccountStatus.ACTIVE);
            authenticateAs(testCompany);
            UserDtoIn updateDto = createUpdateDto(otherCompany);

            // When/Then
            assertThatThrownBy(() -> userService.update(otherCompany.getId(), updateDto))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("update() - Critical Field Changes via Admin")
    class UpdateCriticalFieldsViaAdmin {

        @Test
        @DisplayName("Admin updates critical field (firstName) - status stays same")
        void adminUpdatesCriticalFieldStatusUnchanged() {
            // Given
            testInfluencer.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(testInfluencer);

            authenticateAs(testAdmin);
            UserDtoIn updateDto = createUpdateDto(testInfluencer);
            updateDto.setFirstName("AdminChangedName");

            // When
            User result = userService.update(testInfluencer.getId(), updateDto);

            // Then
            assertThat(result.getFirstName()).isEqualTo("AdminChangedName");
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        // Removed: adminUpdatesEmailStatusUnchanged - requires real Firebase to sync email change.
        // Integration tests use fake UIDs that don't exist in Firebase.
        // Admin email update is covered by E2E profile-critical-consolidated.feature (TEST 3).

        @Test
        @DisplayName("Admin updates phone (critical) - status stays same")
        void adminUpdatesPhoneStatusUnchanged() {
            // Given
            testInfluencer.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(testInfluencer);

            authenticateAs(testAdmin);
            UserDtoIn updateDto = createUpdateDto(testInfluencer);
            updateDto.setPhoneNumber("+48555666777");

            // When
            User result = userService.update(testInfluencer.getId(), updateDto);

            // Then
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("Admin updates non-critical field - status unchanged")
        void adminUpdatesNonCriticalFieldStatusUnchanged() {
            // Given
            testInfluencer.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(testInfluencer);

            authenticateAs(testAdmin);
            UserDtoIn updateDto = createUpdateDto(testInfluencer);
            updateDto.setProfilePicture("https://cdn.example.com/avatar.jpg");

            // When
            User result = userService.update(testInfluencer.getId(), updateDto);

            // Then
            assertThat(result.getProfilePicture()).isEqualTo("https://cdn.example.com/avatar.jpg");
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("update() - Address Operations")
    class UpdateAddresses {

        @Test
        @DisplayName("Update with new addresses replaces old ones")
        void updateWithNewAddresses() {
            // Given
            User userWithAddress = createCompleteUserProfile("user-with-addr-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            authenticateAs(testAdmin);

            UserDtoIn updateDto = createUpdateDto(userWithAddress);
            AddressDtoIn newAddress = createValidAddressDto(true, "MAIN");
            newAddress.setStreet("New Street 999");
            updateDto.setAddresses(List.of(newAddress));

            // When
            User result = userService.update(userWithAddress.getId(), updateDto);

            // Then
            assertThat(result.getAddresses()).hasSize(1);
            assertThat(result.getAddresses().get(0).getStreet()).isEqualTo("New Street 999");
        }

        @Test
        @DisplayName("Update with empty addresses list clears addresses")
        void updateWithEmptyAddressesClears() {
            // Given
            User userWithAddress = createCompleteUserProfile("user-clear-addr-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            assertThat(userWithAddress.getAddresses()).isNotEmpty();

            authenticateAs(testAdmin);
            UserDtoIn updateDto = createUpdateDto(userWithAddress);
            updateDto.setAddresses(List.of()); // Empty list

            // When
            User result = userService.update(userWithAddress.getId(), updateDto);

            // Then
            assertThat(result.getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("Update with multiple addresses")
        void updateWithMultipleAddresses() {
            // Given
            User user = createUserWithStatus("multi-addr-" + System.currentTimeMillis(),
                    UserType.COMPANY, AccountStatus.ACTIVE);
            authenticateAs(testAdmin);

            AddressDtoIn primaryAddress = createValidAddressDto(true, "MAIN");
            AddressDtoIn secondaryAddress = createValidAddressDto(false, "SECONDARY");
            secondaryAddress.setStreet("Secondary Street");

            UserDtoIn updateDto = createUpdateDto(user);
            updateDto.setAddresses(List.of(primaryAddress, secondaryAddress));

            // When
            User result = userService.update(user.getId(), updateDto);

            // Then
            assertThat(result.getAddresses()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("updateAsDto()")
    class UpdateAsDto {

        @Test
        @DisplayName("updateAsDto returns DTO")
        void updateAsDtoReturnsDto() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext();
            UserDtoIn updateDto = createUpdateDto(testInfluencer);
            updateDto.setFirstName("UpdatedForDto");

            // When
            UserDtoOut result = userService.updateAsDto(testInfluencer.getId(), updateDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("UpdatedForDto");
        }
    }

    // Helper methods

    private UserDtoIn createUpdateDto(User existingUser) {
        UserDtoIn dto = new UserDtoIn();
        dto.setUserType(existingUser.getUserType());
        dto.setEmail(existingUser.getEmail());
        dto.setAccountStatus(existingUser.getAccountStatus());
        return dto;
    }

    private AddressDtoIn createValidAddressDto(boolean isPrimary, String addressType) {
        AddressDtoIn dto = new AddressDtoIn();
        dto.setStreet("Update Street " + System.currentTimeMillis());
        dto.setCity("Warsaw");
        dto.setPostalCode("00-001");
        dto.setCountry("Poland");
        dto.setState("Mazowieckie");
        dto.setAddressType(addressType);
        dto.setPrimary(isPrimary);
        return dto;
    }
}
