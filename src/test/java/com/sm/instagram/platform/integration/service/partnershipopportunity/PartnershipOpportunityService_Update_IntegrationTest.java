package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.address.AddressSourceType;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoIn;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoOut;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityPhoto;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityPhotoDtoIn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for PartnershipOpportunityService.update() method.
 */
@DisplayName("PartnershipOpportunityService - update")
class PartnershipOpportunityService_Update_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can update any opportunity")
    void adminCanUpdateAnyOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(testAdmin);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setTitle("Admin Updated Title");

        PartnershipOpportunity result = partnershipOpportunityService.update(opportunityId, dto);

        assertThat(result.getTitle()).isEqualTo("Admin Updated Title");
    }

    @Test
    @DisplayName("Company can update own opportunity without active applications")
    void companyCanUpdateOwnOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setTitle("Company Updated Title");

        PartnershipOpportunity result = partnershipOpportunityService.update(opportunityId, dto);

        assertThat(result.getTitle()).isEqualTo("Company Updated Title");
    }

    @Test
    @DisplayName("Company cannot update other company's opportunity")
    void companyCannotUpdateOtherCompanyOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(secondCompany);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(secondCompany);

        assertThatThrownBy(() -> partnershipOpportunityService.update(opportunityId, dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Company cannot update opportunity with active applications")
    void companyCannotUpdateOpportunityWithActiveApplications() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        createAppliedOpportunity(opportunity, testInfluencer, OpportunityStatus.ACCEPTED_BY_COMPANY);
        Long opportunityId = opportunity.getId();

        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        assertThatThrownBy(() -> partnershipOpportunityService.update(opportunityId, dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Admin can update opportunity even with active applications")
    void adminCanUpdateOpportunityWithActiveApplications() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        createAppliedOpportunity(opportunity, testInfluencer, OpportunityStatus.ACCEPTED_BY_COMPANY);
        Long opportunityId = opportunity.getId();

        authenticateAs(testAdmin);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setTitle("Admin Updated Despite Active Apps");

        PartnershipOpportunity result = partnershipOpportunityService.update(opportunityId, dto);

        assertThat(result.getTitle()).isEqualTo("Admin Updated Despite Active Apps");
    }

    @Test
    @DisplayName("Influencer cannot update opportunity")
    void influencerCannotUpdateOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(testInfluencer);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        assertThatThrownBy(() -> partnershipOpportunityService.update(opportunityId, dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Update non-existent opportunity throws ResourceNotFoundException")
    void updateNonExistentOpportunityThrows() {
        authenticateAs(testAdmin);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        assertThatThrownBy(() -> partnershipOpportunityService.update(999999L, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Update with new address reference")
    void updateWithNewAddressReference() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Address newAddress = new Address();
        newAddress.setStreet("New Street 789");
        newAddress.setCity("Gdansk");
        newAddress.setPostalCode("80-001");
        newAddress.setCountry("Poland");
        newAddress.setAddressType("MAIN");
        newAddress.setSourceType(AddressSourceType.CUSTOM);
        newAddress.setUser(testCompany);
        newAddress = addressRepository.save(newAddress);

        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setAddressId(newAddress.getId());

        PartnershipOpportunity result = partnershipOpportunityService.update(opportunityId, dto);

        assertThat(result.getAddress().getStreet()).isEqualTo("New Street 789");
    }

    @Test
    @DisplayName("Update with embedded address DTO")
    void updateWithEmbeddedAddressDto() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        AddressDtoIn addressDto = new AddressDtoIn();
        addressDto.setStreet("Updated Street 101");
        addressDto.setCity("Poznan");
        addressDto.setPostalCode("60-001");
        addressDto.setCountry("Poland");
        addressDto.setAddressType("MAIN");
        dto.setAddress(addressDto);

        PartnershipOpportunity result = partnershipOpportunityService.update(opportunityId, dto);

        assertThat(result.getAddress()).isNotNull();
        assertThat(result.getAddress().getStreet()).isEqualTo("Updated Street 101");
    }

    @Test
    @DisplayName("Update preserves existing address when not provided")
    void updatePreservesExistingAddressWhenNotProvided() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
        opportunity = saveOpportunity(opportunity);
        Long opportunityId = opportunity.getId();

        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setAddress(null);
        dto.setAddressId(null);

        PartnershipOpportunity result = partnershipOpportunityService.update(opportunityId, dto);

        assertThat(result.getAddress()).isNotNull();
    }

    @Test
    @DisplayName("Update photos replaces existing photos")
    void updatePhotosReplacesExistingPhotos() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
        PartnershipOpportunityPhoto oldPhoto = new PartnershipOpportunityPhoto();
        oldPhoto.setUrl("https://example.com/old-photo.jpg");
        oldPhoto.setOrderNumber(1);
        oldPhoto.setIsCover(true);
        oldPhoto.setPartnershipOpportunity(opportunity);
        opportunity.setPhotos(new ArrayList<>(List.of(oldPhoto)));
        opportunity = saveOpportunity(opportunity);
        Long opportunityId = opportunity.getId();

        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        List<PartnershipOpportunityPhotoDtoIn> newPhotos = new ArrayList<>();
        PartnershipOpportunityPhotoDtoIn newPhoto = new PartnershipOpportunityPhotoDtoIn();
        newPhoto.setUrl("https://example.com/new-photo.jpg");
        newPhoto.setOrderNumber(1);
        newPhoto.setIsCover(true);
        newPhotos.add(newPhoto);
        dto.setPhotos(newPhotos);

        PartnershipOpportunity result = partnershipOpportunityService.update(opportunityId, dto);

        assertThat(result.getPhotos()).hasSize(1);
        assertThat(result.getPhotos().get(0).getUrl()).isEqualTo("https://example.com/new-photo.jpg");
    }

    @Nested
    @DisplayName("DTO Return Methods (require HTTP context)")
    class DtoReturnTests {

        @Test
        @DisplayName("updateAsDto updates and returns DTO with mock HTTP context")
        void updateAsDtoUpdatesAndReturnsDto() {
            authenticateAs(testCompany);
            setUpMockHttpContext();  // Enable HTTP context for getLocaleFromRequest()

            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));

            PartnershipOpportunityDtoIn updateDto = createTestOpportunityDto(testCompany);
            updateDto.setTitle("Updated via DTO");

            PartnershipOpportunityDtoOut result = partnershipOpportunityService.updateAsDto(
                    opportunity.getId(),
                    updateDto
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(opportunity.getId());
            assertThat(result.getTitle()).isEqualTo("Updated via DTO");
        }
    }
}
