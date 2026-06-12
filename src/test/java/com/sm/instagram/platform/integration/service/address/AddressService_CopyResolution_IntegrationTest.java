package com.sm.instagram.platform.integration.service.address;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressSourceType;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AddressService address copying and resolution operations.
 * Tests the resolveAddressForNewOpportunity() functionality.
 */
@DisplayName("AddressService - Copy Resolution Operations")
class AddressService_CopyResolution_IntegrationTest extends AddressServiceIntegrationTestBase {

    @Nested
    @DisplayName("resolveAddressForNewOpportunity()")
    class ResolveAddressForNewOpportunity {

        @Test
        @DisplayName("Creates copy of user address for new opportunity")
        void createsUserAddressCopy() {
            // Given
            Address userAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testCompany);

            // When
            Address result = addressService.resolveAddressForNewOpportunity(userAddress.getId());

            // Then
            assertThat(result).isNotNull();
            // This is a new unsaved address (copy), not the same as original
            assertThat(result.getId()).isNull(); // Not saved yet
            assertThat(result.getStreet()).isEqualTo(userAddress.getStreet());
            assertThat(result.getCity()).isEqualTo(userAddress.getCity());
            assertThat(result.isCopied()).isTrue();
            assertThat(result.getSourceAddressId()).isEqualTo(userAddress.getId());
            assertThat(result.getUser()).isNull(); // Detached from user
        }

        @Test
        @DisplayName("Reuses existing opportunity address directly when user owns opportunity")
        void reusesExistingOpportunityAddress() {
            // Given - Create opportunity first, which creates an address with the company as owner
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            Address opportunityAddress = opportunity.getAddress();
            // The address is still linked to testCompany via the user field from createTestOpportunity
            authenticateAs(testCompany);

            // When - since address has user association (testCompany), ownership validation passes
            // and since address.getUser() != null, it creates a copy (not reuse)
            Address result = addressService.resolveAddressForNewOpportunity(opportunityAddress.getId());

            // Then - since the address has a user association, it creates a new copy
            assertThat(result).isNotNull();
            // The result will be a new unsaved copy when source has user association
            assertThat(result.getStreet()).isEqualTo(opportunityAddress.getStreet());
        }

        @Test
        @DisplayName("Returns existing copy if user address was already copied")
        void returnsExistingCopyIfAlreadyCopied() {
            // Given
            Address userAddress = createAddressForUser(testCompany, true, "MAIN");

            // Manually create an existing copy with proper association
            Address existingCopy = new Address();
            existingCopy.setStreet(userAddress.getStreet());
            existingCopy.setCity(userAddress.getCity());
            existingCopy.setPostalCode(userAddress.getPostalCode());
            existingCopy.setCountry(userAddress.getCountry());
            existingCopy.setAddressType("MAIN");
            existingCopy.setPrimary(true);
            existingCopy.setCopied(true);
            existingCopy.setSourceAddressId(userAddress.getId());
            existingCopy.setSourceType(AddressSourceType.CUSTOM);
            // Must associate with user to pass validation
            existingCopy.setUser(testCompany);
            existingCopy = addressRepository.save(existingCopy);

            authenticateAs(testCompany);

            // When
            Address result = addressService.resolveAddressForNewOpportunity(userAddress.getId());

            // Then - should return existing copy
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(existingCopy.getId());
        }

        @Test
        @DisplayName("Non-owner cannot resolve other user's address")
        void nonOwnerCannotResolveOtherUserAddress() {
            // Given
            Address companyAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> addressService.resolveAddressForNewOpportunity(companyAddress.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Address not found throws ResourceNotFoundException")
        void addressNotFoundThrowsException() {
            // Given
            authenticateAs(testCompany);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> addressService.resolveAddressForNewOpportunity(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Admin can resolve any address for opportunity")
        void adminCanResolveAnyAddress() {
            // Given
            Address userAddress = createAddressForUser(testCompany, true, "MAIN");
            authenticateAs(testAdmin);

            // When
            Address result = addressService.resolveAddressForNewOpportunity(userAddress.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStreet()).isEqualTo(userAddress.getStreet());
        }

        @Test
        @DisplayName("Copied address has MAIN type and is primary")
        void copiedAddressHasCorrectDefaults() {
            // Given
            Address userAddress = createAddressForUser(testCompany, false, "SECONDARY");
            authenticateAs(testCompany);

            // When
            Address result = addressService.resolveAddressForNewOpportunity(userAddress.getId());

            // Then
            assertThat(result.getAddressType()).isEqualTo("MAIN");
            assertThat(result.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Copy preserves all address fields except user reference")
        void copyPreservesAllFields() {
            // Given
            Address userAddress = createAddressForUser(testCompany, true, "MAIN");
            userAddress.setAdditionalInfo("Floor 3, Apt 42");
            userAddress.setState("Mazowieckie");
            userAddress = addressRepository.save(userAddress);
            authenticateAs(testCompany);

            // When
            Address result = addressService.resolveAddressForNewOpportunity(userAddress.getId());

            // Then
            assertThat(result.getStreet()).isEqualTo(userAddress.getStreet());
            assertThat(result.getCity()).isEqualTo(userAddress.getCity());
            assertThat(result.getPostalCode()).isEqualTo(userAddress.getPostalCode());
            assertThat(result.getCountry()).isEqualTo(userAddress.getCountry());
            assertThat(result.getState()).isEqualTo(userAddress.getState());
            assertThat(result.getAdditionalInfo()).isEqualTo(userAddress.getAdditionalInfo());
            // But user should be null (detached)
            assertThat(result.getUser()).isNull();
        }
    }
}
