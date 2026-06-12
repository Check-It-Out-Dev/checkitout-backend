package com.sm.instagram.platform.integration.service.address;

import com.sm.instagram.platform.address.Address;
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
 * Integration tests for AddressService primary address operations.
 * Tests primary flag management and related business logic.
 */
@DisplayName("AddressService - Primary Address Operations")
class AddressService_PrimaryAddress_IntegrationTest extends AddressServiceIntegrationTestBase {

    @Nested
    @DisplayName("findPrimaryAddressByUserId()")
    class FindPrimaryAddressByUserId {

        @Test
        @DisplayName("Owner can find their primary address")
        void ownerFindsPrimaryAddress() {
            // Given
            createAddressForUser(testCompany, false, "SECONDARY");
            Address primaryAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            Address result = addressService.findPrimaryAddressByUserId(testCompany.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(primaryAddress.getId());
            assertThat(result.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Admin can find any user's primary address")
        void adminFindsPrimaryAddress() {
            // Given
            Address primaryAddress = createAddressForUser(testInfluencer, true, "MAIN");
            authenticateAs(testAdmin);

            // When
            Address result = addressService.findPrimaryAddressByUserId(testInfluencer.getId());

            // Then
            assertThat(result.getId()).isEqualTo(primaryAddress.getId());
        }

        @Test
        @DisplayName("Non-owner cannot find other user's primary address")
        void nonOwnerCannotFindPrimaryAddress() {
            // Given
            createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> addressService.findPrimaryAddressByUserId(testCompany.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when no primary address exists")
        void throwsWhenNoPrimaryAddress() {
            // Given - user with no addresses
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> addressService.findPrimaryAddressByUserId(testInfluencer.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findPrimaryAddressByOpportunityId()")
    class FindPrimaryAddressByOpportunityId {

        @Test
        @DisplayName("Opportunity owner can find primary address")
        void ownerFindsPrimaryOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            // The address from createTestOpportunity is primary by default
            authenticateAs(testCompany);

            // When
            Address result = addressService.findPrimaryAddressByOpportunityId(opportunity.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(opportunity.getAddress().getId());
        }

        @Test
        @DisplayName("Admin can find any opportunity's primary address")
        void adminFindsPrimaryOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testAdmin);

            // When
            Address result = addressService.findPrimaryAddressByOpportunityId(opportunity.getId());

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Non-owner cannot find primary address for other company's opportunity")
        void nonOwnerCannotFindPrimaryOpportunityAddress() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(secondCompany);

            // When/Then
            assertThatThrownBy(() -> addressService.findPrimaryAddressByOpportunityId(opportunity.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("setAsPrimary()")
    class SetAsPrimary {

        @Test
        @DisplayName("Owner can set their address as primary")
        void ownerSetsAddressAsPrimary() {
            // Given
            Address oldPrimary = createAddressForUser(testCompany, true, "MAIN");
            Address newPrimary = createAddressForUser(testCompany, false, "SECONDARY");
            authenticateAs(testCompany);

            // When
            Address result = addressService.setAsPrimary(newPrimary.getId());

            // Then
            assertThat(result.isPrimary()).isTrue();

            // Verify old primary is no longer primary
            Address reloadedOldPrimary = addressRepository.findById(oldPrimary.getId()).orElseThrow();
            assertThat(reloadedOldPrimary.isPrimary()).isFalse();
        }

        @Test
        @DisplayName("Admin can set any address as primary")
        void adminSetsAddressAsPrimary() {
            // Given
            Address address = createAddressForUser(testInfluencer, false, "MAIN");
            authenticateAs(testAdmin);

            // When
            Address result = addressService.setAsPrimary(address.getId());

            // Then
            assertThat(result.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Non-owner cannot set other user's address as primary")
        void nonOwnerCannotSetAsPrimary() {
            // Given
            Address companyAddress = createAddressForUser(testCompany, false, "MAIN");
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> addressService.setAsPrimary(companyAddress.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Setting already primary address as primary is idempotent")
        void settingAlreadyPrimaryIsIdempotent() {
            // Given
            Address primaryAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            Address result = addressService.setAsPrimary(primaryAddress.getId());

            // Then
            assertThat(result.isPrimary()).isTrue();
            assertThat(result.getId()).isEqualTo(primaryAddress.getId());
        }
    }

    @Nested
    @DisplayName("setAsPrimaryAsDto()")
    class SetAsPrimaryAsDto {

        @Test
        @DisplayName("Sets address as primary and returns DTO")
        void setsAsPrimaryAndReturnsDto() {
            // Given
            Address address = createAddressForUser(testCompany, false, "MAIN");
            authenticateAs(testCompany);

            // When
            AddressDtoOut result = addressService.setAsPrimaryAsDto(address.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isPrimary()).isTrue();
            assertThat(result.getId()).isEqualTo(address.getId());
        }
    }

    @Nested
    @DisplayName("findPrimaryAddressByUserIdAsDto()")
    class FindPrimaryAddressByUserIdAsDto {

        @Test
        @DisplayName("Returns primary address as DTO")
        void returnsPrimaryAddressAsDto() {
            // Given
            Address primary = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            AddressDtoOut result = addressService.findPrimaryAddressByUserIdAsDto(testCompany.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(primary.getId());
            assertThat(result.isPrimary()).isTrue();
        }
    }

    @Nested
    @DisplayName("findPrimaryAddressByOpportunityIdAsDto()")
    class FindPrimaryAddressByOpportunityIdAsDto {

        @Test
        @DisplayName("Returns primary opportunity address as DTO")
        void returnsPrimaryOpportunityAddressAsDto() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testCompany);

            // When
            AddressDtoOut result = addressService.findPrimaryAddressByOpportunityIdAsDto(opportunity.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(opportunity.getAddress().getId());
        }
    }
}
