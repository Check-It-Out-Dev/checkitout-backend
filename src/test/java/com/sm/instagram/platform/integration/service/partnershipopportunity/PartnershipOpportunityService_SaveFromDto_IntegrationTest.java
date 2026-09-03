package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoIn;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoOut;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityPhotoDtoIn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for PartnershipOpportunityService.saveFromDto() method.
 */
@DisplayName("PartnershipOpportunityService - saveFromDto")
class PartnershipOpportunityService_SaveFromDto_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can create opportunity for any company")
    void adminCanCreateOpportunityForAnyCompany() {
        authenticateAs(testAdmin);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        PartnershipOpportunity result = partnershipOpportunityService.saveFromDto(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getCompany().getId()).isEqualTo(testCompany.getId());
    }

    @Test
    @DisplayName("Company can create opportunity for itself")
    void companyCanCreateOpportunityForItself() {
        authenticateAs(testCompany);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        PartnershipOpportunity result = partnershipOpportunityService.saveFromDto(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getCompany().getId()).isEqualTo(testCompany.getId());
    }

    @Test
    @DisplayName("Company cannot create opportunity for different company")
    void companyCannotCreateOpportunityForDifferentCompany() {
        authenticateAs(testCompany);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(secondCompany);

        assertThatThrownBy(() -> partnershipOpportunityService.saveFromDto(dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Influencer cannot create opportunity")
    void influencerCannotCreateOpportunity() {
        authenticateAs(testInfluencer);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        assertThatThrownBy(() -> partnershipOpportunityService.saveFromDto(dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Cannot create opportunity for non-existent company (MappingException)")
    void cannotCreateOpportunityForNonExistentCompany() {
        authenticateAs(testAdmin);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setCompany(999999L);

        // ModelMapper throws MappingException when UserConverter can't find the user
        assertThatThrownBy(() -> partnershipOpportunityService.saveFromDto(dto))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Create opportunity with address ID reference")
    void createOpportunityWithAddressIdReference() {
        authenticateAs(testCompany);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setAddressId(testAddress.getId());

        PartnershipOpportunity result = partnershipOpportunityService.saveFromDto(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getAddress()).isNotNull();
    }

    @Test
    @DisplayName("Create opportunity with embedded address DTO")
    void createOpportunityWithEmbeddedAddressDto() {
        authenticateAs(testCompany);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        AddressDtoIn addressDto = new AddressDtoIn();
        addressDto.setStreet("New Street 456");
        addressDto.setCity("Krakow");
        addressDto.setPostalCode("30-001");
        addressDto.setCountry("Poland");
        addressDto.setAddressType("MAIN");
        dto.setAddress(addressDto);

        PartnershipOpportunity result = partnershipOpportunityService.saveFromDto(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getAddress()).isNotNull();
        assertThat(result.getAddress().getStreet()).isEqualTo("New Street 456");
    }

    @Test
    @DisplayName("Create opportunity with photos")
    void createOpportunityWithPhotos() {
        authenticateAs(testCompany);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);

        List<PartnershipOpportunityPhotoDtoIn> photos = new ArrayList<>();
        PartnershipOpportunityPhotoDtoIn photo1 = new PartnershipOpportunityPhotoDtoIn();
        photo1.setUploadId("upload-1"); // URL is BE-derived (stub in base)
        photo1.setOrderNumber(1);
        photo1.setIsCover(true);
        photos.add(photo1);

        PartnershipOpportunityPhotoDtoIn photo2 = new PartnershipOpportunityPhotoDtoIn();
        photo2.setUploadId("upload-2");
        photo2.setOrderNumber(2);
        photo2.setIsCover(false);
        photos.add(photo2);

        dto.setPhotos(photos);

        PartnershipOpportunity result = partnershipOpportunityService.saveFromDto(dto);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getPhotos()).hasSize(2);
        // SECURITY (audit G1): the resolver must be called with the
        // AUTHENTICATED caller's uid (not the campaign's company id) — this
        // locks the ownership contract so a regression that passed the wrong
        // id (weakening the owner check) fails here.
        verify(signedUrlService).resolveOwnedUpload(eq(testCompany.getFirebaseUserId()), eq("upload-1"));
        verify(signedUrlService).resolveOwnedUpload(eq(testCompany.getFirebaseUserId()), eq("upload-2"));
    }

    @Test
    @DisplayName("Create opportunity without company ID (null) throws exception")
    void createOpportunityWithoutCompanyIdThrows() {
        authenticateAs(testAdmin);
        PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
        dto.setCompany(null);

        assertThatThrownBy(() -> partnershipOpportunityService.saveFromDto(dto))
                .isInstanceOf(Exception.class);
    }

    @Nested
    @DisplayName("DTO Return Methods (require HTTP context)")
    class DtoReturnTests {

        @Test
        @DisplayName("saveFromDtoAsDto saves and returns DTO with mock HTTP context")
        void saveFromDtoAsDtoSavesAndReturnsDto() {
            authenticateAs(testCompany);
            setUpMockHttpContext();  // Enable HTTP context for getLocaleFromRequest()

            PartnershipOpportunityDtoIn dto = createTestOpportunityDto(testCompany);
            dto.setAddressId(testAddress.getId());

            PartnershipOpportunityDtoOut result = partnershipOpportunityService.saveFromDtoAsDto(dto);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getTitle()).isEqualTo(dto.getTitle());
        }
    }
}
