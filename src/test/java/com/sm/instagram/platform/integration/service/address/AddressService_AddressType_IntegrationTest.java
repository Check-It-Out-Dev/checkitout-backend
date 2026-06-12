package com.sm.instagram.platform.integration.service.address;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.address.AddressDtoOut;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AddressService address type operations.
 * Tests MAIN/SECONDARY type management and related business logic.
 */
@DisplayName("AddressService - Address Type Operations")
class AddressService_AddressType_IntegrationTest extends AddressServiceIntegrationTestBase {

    @Nested
    @DisplayName("findAddressesByUserIdAndType()")
    class FindAddressesByUserIdAndType {

        @Test
        @DisplayName("Owner can find addresses by type")
        void ownerFindsAddressesByType() {
            // Given
            Address mainAddress = createAddressForUser(testCompany, true, "MAIN");
            Address secondaryAddress = createAddressForUser(testCompany, false, "SECONDARY");
            authenticateAs(testCompany);

            // When
            List<Address> mainResults = addressService.findAddressesByUserIdAndType(testCompany.getId(), "MAIN");
            List<Address> secondaryResults = addressService.findAddressesByUserIdAndType(testCompany.getId(), "SECONDARY");

            // Then
            assertThat(mainResults).hasSize(1);
            assertThat(mainResults.get(0).getId()).isEqualTo(mainAddress.getId());

            assertThat(secondaryResults).hasSize(1);
            assertThat(secondaryResults.get(0).getId()).isEqualTo(secondaryAddress.getId());
        }

        @Test
        @DisplayName("Admin can find any user's addresses by type")
        void adminFindsAddressesByType() {
            // Given
            Address mainAddress = createAddressForUser(testInfluencer, true, "MAIN");
            authenticateAs(testAdmin);

            // When
            List<Address> result = addressService.findAddressesByUserIdAndType(testInfluencer.getId(), "MAIN");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(mainAddress.getId());
        }

        @Test
        @DisplayName("Non-owner cannot find other user's addresses by type")
        void nonOwnerCannotFindAddressesByType() {
            // Given
            createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> addressService.findAddressesByUserIdAndType(testCompany.getId(), "MAIN"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Returns empty list when no addresses of type exist")
        void returnsEmptyListWhenNoAddressesOfType() {
            // Given
            createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            List<Address> result = addressService.findAddressesByUserIdAndType(testCompany.getId(), "SECONDARY");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAddressesByOpportunityIdAndType()")
    class FindAddressesByOpportunityIdAndType {

        @Test
        @DisplayName("Opportunity owner can find addresses by type")
        void ownerFindsOpportunityAddressesByType() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testCompany);

            // When
            List<Address> result = addressService.findAddressesByOpportunityIdAndType(opportunity.getId(), "MAIN");

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Admin can find opportunity addresses by type")
        void adminFindsOpportunityAddressesByType() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testAdmin);

            // When
            List<Address> result = addressService.findAddressesByOpportunityIdAndType(opportunity.getId(), "MAIN");

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Non-owner cannot find other company's opportunity addresses by type")
        void nonOwnerCannotFindOpportunityAddressesByType() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(secondCompany);

            // When/Then
            assertThatThrownBy(() -> addressService.findAddressesByOpportunityIdAndType(opportunity.getId(), "MAIN"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("Address Type Management on Update")
    class AddressTypeManagementOnUpdate {

        @Test
        @DisplayName("Setting address to MAIN demotes existing MAIN to SECONDARY")
        void settingMainDemotesExistingMain() {
            // Given
            Address existingMain = createAddressForUser(testCompany, true, "MAIN");
            Address secondary = createAddressForUser(testCompany, false, "SECONDARY");
            authenticateAs(testCompany);

            AddressDtoIn dto = new AddressDtoIn();
            dto.setStreet(secondary.getStreet());
            dto.setCity(secondary.getCity());
            dto.setPostalCode(secondary.getPostalCode());
            dto.setCountry(secondary.getCountry());
            dto.setState(secondary.getState());
            dto.setAddressType("MAIN"); // Changing to MAIN
            dto.setPrimary(secondary.isPrimary());

            // When
            Address updatedSecondary = addressService.update(secondary.getId(), dto);

            // Then
            assertThat(updatedSecondary.getAddressType()).isEqualTo("MAIN");

            // Verify old MAIN is now SECONDARY
            Address reloadedExistingMain = addressRepository.findById(existingMain.getId()).orElseThrow();
            assertThat(reloadedExistingMain.getAddressType()).isEqualTo("SECONDARY");
        }
    }

    @Nested
    @DisplayName("findAddressesByUserIdAndTypeAsDto()")
    class FindAddressesByUserIdAndTypeAsDto {

        @Test
        @DisplayName("Returns addresses by type as DTOs")
        void returnsAddressesByTypeAsDtos() {
            // Given
            Address mainAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            List<AddressDtoOut> result = addressService.findAddressesByUserIdAndTypeAsDto(testCompany.getId(), "MAIN");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(mainAddress.getId());
            assertThat(result.get(0).getAddressType()).isEqualTo("MAIN");
        }
    }

    @Nested
    @DisplayName("findAddressesByOpportunityIdAndTypeAsDto()")
    class FindAddressesByOpportunityIdAndTypeAsDto {

        @Test
        @DisplayName("Returns opportunity addresses by type as DTOs")
        void returnsOpportunityAddressesByTypeAsDtos() {
            // Given
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            authenticateAs(testCompany);

            // When
            List<AddressDtoOut> result = addressService.findAddressesByOpportunityIdAndTypeAsDto(opportunity.getId(), "MAIN");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getAddressType()).isEqualTo("MAIN");
        }
    }
}
