package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.user.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserService.save() method.
 * Tests user creation with various scenarios including permissions, addresses, and validation.
 */
@DisplayName("UserService - Save Operations")
class UserService_Save_IntegrationTest extends UserServiceIntegrationTestBase {

    @Nested
    @DisplayName("save() - Status Setting")
    class SaveStatusSetting {

        @Test
        @DisplayName("Admin creates INFLUENCER - status stays ACTIVE")
        void adminCreatesInfluencerWithActiveStatus() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn userDto = createValidUserDto(UserType.INFLUENCER, AccountStatus.ACTIVE);

            // When
            User result = userService.save(userDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(result.getUserType()).isEqualTo(UserType.INFLUENCER);
        }

        @Test
        @DisplayName("Admin creates COMPANY - status stays ACTIVE")
        void adminCreatesCompanyWithActiveStatus() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn userDto = createValidUserDto(UserType.COMPANY, AccountStatus.ACTIVE);

            // When
            User result = userService.save(userDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(result.getUserType()).isEqualTo(UserType.COMPANY);
        }

        @Test
        @DisplayName("Non-admin creates self - status forced to IN_VALIDATION")
        void nonAdminCreatesUserInValidationStatus() {
            // Given
            authenticateAs(testInfluencer);
            UserDtoIn userDto = createValidUserDto(UserType.INFLUENCER, AccountStatus.ACTIVE);

            // When
            User result = userService.save(userDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }
    }

    @Nested
    @DisplayName("save() - With Addresses")
    class SaveWithAddresses {

        @Test
        @DisplayName("Create user with address DTOs")
        void createWithAddressDtos() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn userDto = createValidUserDto(UserType.INFLUENCER, AccountStatus.ACTIVE);

            AddressDtoIn addressDto = createValidAddressDto(true, "MAIN");
            userDto.setAddresses(List.of(addressDto));

            // When
            User result = userService.save(userDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAddresses()).hasSize(1);
            assertThat(result.getAddresses().get(0).getStreet()).isEqualTo(addressDto.getStreet());
            assertThat(result.getAddresses().get(0).isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Create user with multiple addresses")
        void createWithMultipleAddresses() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn userDto = createValidUserDto(UserType.COMPANY, AccountStatus.ACTIVE);

            AddressDtoIn primaryAddress = createValidAddressDto(true, "MAIN");
            AddressDtoIn secondaryAddress = createValidAddressDto(false, "SECONDARY");
            secondaryAddress.setStreet("Secondary Street 456");

            userDto.setAddresses(List.of(primaryAddress, secondaryAddress));

            // When
            User result = userService.save(userDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAddresses()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("save() - Admin vs Non-Admin Behavior")
    class SaveAdminVsNonAdmin {

        @Test
        @DisplayName("Admin can specify ACTIVE status")
        void adminCanSpecifyActiveStatus() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn userDto = createValidUserDto(UserType.INFLUENCER, AccountStatus.ACTIVE);

            // When
            User result = userService.save(userDto);

            // Then
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("Admin can specify IN_VALIDATION status")
        void adminCanSpecifyInValidationStatus() {
            // Given
            authenticateAs(testAdmin);
            UserDtoIn userDto = createValidUserDto(UserType.INFLUENCER, AccountStatus.IN_VALIDATION);

            // When
            User result = userService.save(userDto);

            // Then
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
        }
    }

    @Nested
    @DisplayName("saveAsDto()")
    class SaveAsDto {

        @Test
        @DisplayName("saveAsDto returns DTO with ID")
        void saveAsDtoReturnsDto() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext();
            UserDtoIn userDto = createValidUserDto(UserType.INFLUENCER, AccountStatus.ACTIVE);

            // When
            UserDtoOut result = userService.saveAsDto(userDto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getEmail()).isEqualTo(userDto.getEmail());
        }
    }

    // Helper methods

    private UserDtoIn createValidUserDto(UserType userType, AccountStatus accountStatus) {
        UserDtoIn dto = new UserDtoIn();
        dto.setUserType(userType);
        dto.setEmail("test-" + System.currentTimeMillis() + "@example.com");
        dto.setFirstName("Test");
        dto.setLastName("User");
        dto.setAccountStatus(accountStatus);
        dto.setPhoneNumber("+48123456789");
        return dto;
    }

    private AddressDtoIn createValidAddressDto(boolean isPrimary, String addressType) {
        AddressDtoIn dto = new AddressDtoIn();
        dto.setStreet("Test Street " + System.currentTimeMillis());
        dto.setCity("Warsaw");
        dto.setPostalCode("00-001");
        dto.setCountry("Poland");
        dto.setState("Mazowieckie");
        dto.setAddressType(addressType);
        dto.setPrimary(isPrimary);
        return dto;
    }
}
