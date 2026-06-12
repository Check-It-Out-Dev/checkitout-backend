package com.sm.instagram.platform.integration.service.address;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AddressService.findById() and ownership validation.
 * Tests permission checks, data retrieval, and error handling.
 */
@DisplayName("AddressService - FindById Operations")
class AddressService_FindById_IntegrationTest extends AddressServiceIntegrationTestBase {

    @Nested
    @DisplayName("findById() - User Addresses")
    class FindByIdUserAddresses {

        @Test
        @DisplayName("Admin can find any user's address by ID")
        void adminFindsAnyUserAddress() {
            // Given
            Address userAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testAdmin);

            // When
            Address result = addressService.findById(userAddress.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userAddress.getId());
            assertThat(result.getStreet()).isEqualTo(userAddress.getStreet());
        }

        @Test
        @DisplayName("Owner can find own address by ID")
        void ownerFindsOwnAddress() {
            // Given
            Address userAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            Address result = addressService.findById(userAddress.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userAddress.getId());
        }

        @Test
        @DisplayName("Non-owner cannot find other user's address")
        void nonOwnerCannotFindOtherUserAddress() {
            // Given
            Address companyAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> addressService.findById(companyAddress.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Company cannot find other company's address")
        void companyCannotFindOtherCompanyAddress() {
            // Given
            Address companyAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(secondCompany);

            // When/Then
            assertThatThrownBy(() -> addressService.findById(companyAddress.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Address not found throws ResourceNotFoundException")
        void addressNotFoundThrowsException() {
            // Given
            authenticateAs(testAdmin);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> addressService.findById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findById() - Opportunity Addresses")
    class FindByIdOpportunityAddresses {

        @Test
        @DisplayName("Opportunity owner can find address associated with their opportunity")
        void opportunityOwnerFindsOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            Address opportunityAddress = opportunity.getAddress();
            authenticateAs(testCompany);

            // When
            Address result = addressService.findById(opportunityAddress.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(opportunityAddress.getId());
        }

        @Test
        @DisplayName("Admin can find any opportunity address")
        void adminFindsOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            Address opportunityAddress = opportunity.getAddress();
            authenticateAs(testAdmin);

            // When
            Address result = addressService.findById(opportunityAddress.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(opportunityAddress.getId());
        }

        @Test
        @DisplayName("Non-owner cannot find other company's opportunity address")
        void nonOwnerCannotFindOpportunityAddress() {
            // Given - Create an address specifically for the opportunity (not linked to user)
            Address opportunityAddress = new Address();
            opportunityAddress.setStreet("Business Only Street");
            opportunityAddress.setCity("Warsaw");
            opportunityAddress.setPostalCode("00-003");
            opportunityAddress.setCountry("Poland");
            opportunityAddress.setAddressType("MAIN");
            opportunityAddress.setPrimary(true);
            opportunityAddress.setUser(testCompany); // Must have user for validation to work
            Address savedAddress = addressRepository.save(opportunityAddress);
            Long addressId = savedAddress.getId();

            createTestOpportunityWithAddress(testCompany, savedAddress);
            authenticateAs(secondCompany);

            // When/Then
            assertThatThrownBy(() -> addressService.findById(addressId))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }
}
