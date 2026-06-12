package com.sm.instagram.platform.integration.service.address;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.address.AddressDtoOut;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AddressService opportunity address operations.
 * Tests CRUD operations for partnership opportunity addresses.
 */
@DisplayName("AddressService - Opportunity Address Operations")
class AddressService_OpportunityAddresses_IntegrationTest extends AddressServiceIntegrationTestBase {

    @Nested
    @DisplayName("findAddressesByOpportunityId()")
    class FindAddressesByOpportunityId {

        @Test
        @DisplayName("Opportunity owner can find addresses by opportunity ID")
        void opportunityOwnerFindsAddresses() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testCompany);

            // When
            List<Address> result = addressService.findAddressesByOpportunityId(opportunity.getId());

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getId()).isEqualTo(opportunity.getAddress().getId());
        }

        @Test
        @DisplayName("Admin can find addresses for any opportunity")
        void adminFindsOpportunityAddresses() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testAdmin);

            // When
            List<Address> result = addressService.findAddressesByOpportunityId(opportunity.getId());

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Non-owner cannot find addresses for other company's opportunity")
        void nonOwnerCannotFindOpportunityAddresses() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(secondCompany);

            // When/Then
            assertThatThrownBy(() -> addressService.findAddressesByOpportunityId(opportunity.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Opportunity not found throws ResourceNotFoundException")
        void opportunityNotFoundThrowsException() {
            // Given
            authenticateAs(testAdmin);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> addressService.findAddressesByOpportunityId(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("createAddressForOpportunity()")
    class CreateAddressForOpportunity {

        @Test
        @DisplayName("Opportunity owner can create address for their opportunity")
        void ownerCreatesOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testCompany);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When
            Address result = addressService.createAddressForOpportunity(opportunity.getId(), dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getStreet()).isEqualTo(dto.getStreet());
        }

        @Test
        @DisplayName("Admin can create address for any opportunity")
        void adminCreatesOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testAdmin);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When
            Address result = addressService.createAddressForOpportunity(opportunity.getId(), dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
        }

        @Test
        @DisplayName("Non-owner cannot create address for other company's opportunity")
        void nonOwnerCannotCreateOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(secondCompany);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When/Then
            assertThatThrownBy(() -> addressService.createAddressForOpportunity(opportunity.getId(), dto))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("findAddressesByOpportunityIdAsDto()")
    class FindAddressesByOpportunityIdAsDto {

        @Test
        @DisplayName("Returns opportunity addresses as DTOs")
        void returnsAddressesAsDtos() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testCompany);

            // When
            List<AddressDtoOut> result = addressService.findAddressesByOpportunityIdAsDto(opportunity.getId());

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getId()).isEqualTo(opportunity.getAddress().getId());
        }
    }

    @Nested
    @DisplayName("createAddressForOpportunityAsDto()")
    class CreateAddressForOpportunityAsDto {

        @Test
        @DisplayName("Creates opportunity address and returns as DTO")
        void createsAddressAndReturnsAsDto() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testCompany);
            AddressDtoIn dto = createAddressDtoIn(true, "MAIN");

            // When
            AddressDtoOut result = addressService.createAddressForOpportunityAsDto(opportunity.getId(), dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getStreet()).isEqualTo(dto.getStreet());
        }
    }
}
