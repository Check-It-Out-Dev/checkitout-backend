package com.sm.instagram.platform.integration.service.address;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.address.AddressDtoOut;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AddressService user address operations.
 * Tests CRUD operations for user addresses.
 */
@DisplayName("AddressService - User Address Operations")
class AddressService_UserAddresses_IntegrationTest extends AddressServiceIntegrationTestBase {

    @Nested
    @DisplayName("findAddressesByUserId()")
    class FindAddressesByUserId {

        @Test
        @DisplayName("Owner can find all their addresses")
        void ownerFindsOwnAddresses() {
            // Given
            Address address1 = createAddressForUser(testCompany, true, "MAIN");
            Address address2 = createAddressForUser(testCompany, false, "SECONDARY");
            authenticateAs(testCompany);

            // When
            List<Address> result = addressService.findAddressesByUserId(testCompany.getId());

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Address::getId)
                    .containsExactlyInAnyOrder(address1.getId(), address2.getId());
        }

        @Test
        @DisplayName("Admin can find any user's addresses")
        void adminFindsAnyUserAddresses() {
            // Given
            Address address = createAddressForUser(testInfluencer, true, "MAIN");
            authenticateAs(testAdmin);

            // When
            List<Address> result = addressService.findAddressesByUserId(testInfluencer.getId());

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(address.getId());
        }

        @Test
        @DisplayName("Non-owner cannot find other user's addresses")
        void nonOwnerCannotFindOtherUserAddresses() {
            // Given
            createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> addressService.findAddressesByUserId(testCompany.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("User not found throws ResourceNotFoundException")
        void userNotFoundThrowsException() {
            // Given
            authenticateAs(testAdmin);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> addressService.findAddressesByUserId(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns empty list when user has no addresses")
        void returnsEmptyListWhenNoAddresses() {
            // Given
            authenticateAs(testInfluencer);

            // When
            List<Address> result = addressService.findAddressesByUserId(testInfluencer.getId());

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createAddressForUser()")
    class CreateAddressForUser {

        @Test
        @DisplayName("Owner can create address for themselves")
        void ownerCreatesOwnAddress() {
            // Given
            authenticateAs(testCompany);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When
            Address result = addressService.createAddressForUser(testCompany.getId(), dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getStreet()).isEqualTo(dto.getStreet());
            assertThat(result.getCity()).isEqualTo(dto.getCity());
            assertThat(result.getUser().getId()).isEqualTo(testCompany.getId());
        }

        @Test
        @DisplayName("Admin can create address for any user")
        void adminCreatesAddressForAnyUser() {
            // Given
            authenticateAs(testAdmin);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When
            Address result = addressService.createAddressForUser(testInfluencer.getId(), dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("Non-owner cannot create address for other user")
        void nonOwnerCannotCreateAddressForOtherUser() {
            // Given
            authenticateAs(testInfluencer);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When/Then
            assertThatThrownBy(() -> addressService.createAddressForUser(testCompany.getId(), dto))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("First address for user is automatically set as primary")
        void firstAddressIsAutomaticallyPrimary() {
            // Given
            authenticateAs(testCompany);
            AddressDtoIn dto = createAddressDtoIn(false, "MAIN"); // Not explicitly setting primary

            // When
            Address result = addressService.createAddressForUser(testCompany.getId(), dto);

            // Then
            assertThat(result.isPrimary()).isTrue();
        }
    }

    @Nested
    @DisplayName("findAddressesByUserIdAsDto()")
    class FindAddressesByUserIdAsDto {

        @Test
        @DisplayName("Returns addresses as DTOs")
        void returnsAddressesAsDtos() {
            // Given
            Address address = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            List<AddressDtoOut> result = addressService.findAddressesByUserIdAsDto(testCompany.getId());

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(address.getId());
            assertThat(result.get(0).getStreet()).isEqualTo(address.getStreet());
        }
    }

    @Nested
    @DisplayName("createAddressForUserAsDto()")
    class CreateAddressForUserAsDto {

        @Test
        @DisplayName("Creates address and returns as DTO")
        void createsAddressAndReturnsAsDto() {
            // Given
            authenticateAs(testCompany);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When
            AddressDtoOut result = addressService.createAddressForUserAsDto(testCompany.getId(), dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getStreet()).isEqualTo(dto.getStreet());
            assertThat(result.getUserId()).isEqualTo(testCompany.getId());
        }
    }
}
