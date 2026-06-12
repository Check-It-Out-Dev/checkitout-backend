package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressSourceType;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoOut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for PartnershipOpportunityService.patch() method.
 */
@DisplayName("PartnershipOpportunityService - patch")
class PartnershipOpportunityService_Patch_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can patch any opportunity")
    void adminCanPatchAnyOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(testAdmin);
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Admin Patched Title");

        PartnershipOpportunity result = partnershipOpportunityService.patch(opportunityId, updates);

        assertThat(result.getTitle()).isEqualTo("Admin Patched Title");
    }

    @Test
    @DisplayName("Company can patch own opportunity without active applications")
    void companyCanPatchOwnOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Company Patched Title");

        PartnershipOpportunity result = partnershipOpportunityService.patch(opportunityId, updates);

        assertThat(result.getTitle()).isEqualTo("Company Patched Title");
    }

    @Test
    @DisplayName("Company cannot patch other company's opportunity")
    void companyCannotPatchOtherCompanyOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(secondCompany);
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Unauthorized Patch");

        assertThatThrownBy(() -> partnershipOpportunityService.patch(opportunityId, updates))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Company cannot patch opportunity with active applications")
    void companyCannotPatchOpportunityWithActiveApplications() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        createAppliedOpportunity(opportunity, testInfluencer, OpportunityStatus.ACCEPTED_BY_COMPANY);
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Blocked Patch");

        assertThatThrownBy(() -> partnershipOpportunityService.patch(opportunityId, updates))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Patch with addressId for address without user association throws error")
    void patchWithAddressIdWithoutUserAssociationThrows() {
        // The service validates that addresses with a user cannot be reassigned
        // This tests the validation branch - addresses without user should work,
        // but addresses with an existing user association will throw
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        // Create address with user association (testAddress already has testCompany as user)
        // This should throw because the address is already associated
        Map<String, Object> updates = new HashMap<>();
        updates.put("addressId", testAddress.getId());

        // Since testAddress is already associated with testCompany, this should throw
        assertThatThrownBy(() -> partnershipOpportunityService.patch(opportunityId, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Address is already associated with a user");
    }

    @Test
    @DisplayName("Patch with address map creates/updates address")
    void patchWithAddressMapUpdatesAddress() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("street", "Patched Street 111");
        addressMap.put("city", "Wroclaw");
        addressMap.put("postalCode", "50-001");
        addressMap.put("country", "Poland");

        Map<String, Object> updates = new HashMap<>();
        updates.put("address", addressMap);

        PartnershipOpportunity result = partnershipOpportunityService.patch(opportunityId, updates);

        assertThat(result.getAddress()).isNotNull();
        assertThat(result.getAddress().getStreet()).isEqualTo("Patched Street 111");
    }

    @Test
    @DisplayName("Patch with photos list updates photos")
    void patchWithPhotosListUpdatesPhotos() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        List<Map<String, Object>> photos = new ArrayList<>();
        Map<String, Object> photo1 = new HashMap<>();
        photo1.put("url", "https://example.com/patched-photo.jpg");
        photo1.put("orderNumber", 1);
        photo1.put("isCover", true);
        photos.add(photo1);

        Map<String, Object> updates = new HashMap<>();
        updates.put("photos", photos);

        PartnershipOpportunity result = partnershipOpportunityService.patch(opportunityId, updates);

        assertThat(result.getPhotos()).hasSize(1);
        assertThat(result.getPhotos().get(0).getUrl()).isEqualTo("https://example.com/patched-photo.jpg");
    }

    @Test
    @DisplayName("Patch ignores restricted fields (id, createdTime, lastUpdateTime)")
    void patchIgnoresRestrictedFields() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long originalId = opportunity.getId();
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("id", 999999L);
        updates.put("createdTime", LocalDateTime.now().minusYears(10));
        updates.put("lastUpdateTime", LocalDateTime.now().minusYears(10));
        updates.put("title", "Valid Patch");

        PartnershipOpportunity result = partnershipOpportunityService.patch(opportunityId, updates);

        assertThat(result.getId()).isEqualTo(originalId);
        assertThat(result.getTitle()).isEqualTo("Valid Patch");
    }

    @Test
    @DisplayName("Patch skips non-existent fields without error")
    void patchSkipsNonExistentFields() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("nonExistentField", "value");
        updates.put("title", "Valid Patch");

        PartnershipOpportunity result = partnershipOpportunityService.patch(opportunityId, updates);

        assertThat(result.getTitle()).isEqualTo("Valid Patch");
    }

    @Test
    @DisplayName("Patch multiple fields at once")
    void patchMultipleFieldsAtOnce() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Multi-Patched Title");
        updates.put("details", "Multi-Patched Details");
        updates.put("compensationAmountMin", 200);
        updates.put("compensationAmountMax", 1000);
        updates.put("active", false);

        PartnershipOpportunity result = partnershipOpportunityService.patch(opportunityId, updates);

        assertThat(result.getTitle()).isEqualTo("Multi-Patched Title");
        assertThat(result.getDetails()).isEqualTo("Multi-Patched Details");
        assertThat(result.getCompensationAmountMin()).isEqualTo(200);
        assertThat(result.getCompensationAmountMax()).isEqualTo(1000);
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("Patch with too many photos throws validation exception")
    void patchWithTooManyPhotosThrowsValidation() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        List<Map<String, Object>> photos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> photo = new HashMap<>();
            photo.put("url", "https://example.com/photo" + i + ".jpg");
            photo.put("orderNumber", i);
            photo.put("isCover", i == 0);
            photos.add(photo);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("photos", photos);

        assertThatThrownBy(() -> partnershipOpportunityService.patch(opportunityId, updates))
                .isInstanceOf(ValidationTranslatableException.class);
    }

    @Test
    @DisplayName("Patch with invalid address format throws exception")
    void patchWithInvalidAddressFormatThrows() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("address", "invalid string instead of map");

        assertThatThrownBy(() -> partnershipOpportunityService.patch(opportunityId, updates))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Patch with invalid photo format throws exception")
    void patchWithInvalidPhotoFormatThrows() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("photos", "invalid string instead of list");

        assertThatThrownBy(() -> partnershipOpportunityService.patch(opportunityId, updates))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("patchAsDto patches and returns DTO")
    void patchAsDtoPatchesAndReturnsDto() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Patched via DTO");

        // Use the explicit Locale version to avoid method ambiguity
        PartnershipOpportunityDtoOut result = partnershipOpportunityService.patchAsDto(opportunityId, updates, Locale.ENGLISH);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Patched via DTO");
    }
}
