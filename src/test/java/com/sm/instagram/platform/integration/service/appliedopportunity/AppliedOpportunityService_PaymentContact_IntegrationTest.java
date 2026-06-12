package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.PaymentContactDto;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService.getPaymentContact() method.
 * Tests payment contact visibility based on status, role, and user preferences.
 *
 * <p>Payment contact is only available at:
 * <ul>
 *   <li>TO_BE_PAID status</li>
 *   <li>DONE status</li>
 * </ul>
 *
 * <p>Access rules:
 * <ul>
 *   <li>Influencer: sees company contact</li>
 *   <li>Company: sees influencer contact</li>
 *   <li>Admin: sees influencer contact (default)</li>
 *   <li>Others: denied</li>
 * </ul>
 */
@DisplayName("AppliedOpportunityService - Payment Contact")
class AppliedOpportunityService_PaymentContact_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    // =========================================================================
    // Influencer Access Tests
    // =========================================================================

    @Nested
    @DisplayName("Influencer Access")
    class InfluencerAccessTests {

        @Test
        @DisplayName("Influencer can get company contact at TO_BE_PAID status")
        void influencerCanGetCompanyContactAtToBePaid() {
            // Set up user preferences for the company (sharePhone enabled)
            setUpUserPreferences(testCompany, true);

            authenticateAs(testInfluencer);
            AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);

            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

            assertThat(contact).isNotNull();
            assertThat(contact.name()).isEqualTo(testCompany.getName());
            assertThat(contact.email()).isEqualTo(testCompany.getEmail());
        }

        @Test
        @DisplayName("Influencer can get company contact at DONE status")
        void influencerCanGetCompanyContactAtDone() {
            setUpUserPreferences(testCompany, true);

            authenticateAs(testInfluencer);
            AppliedOpportunity application = createCompletedOpportunity(testInfluencer, testPartnershipOpportunity);

            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

            assertThat(contact).isNotNull();
            assertThat(contact.name()).isEqualTo(testCompany.getName());
            assertThat(contact.email()).isEqualTo(testCompany.getEmail());
        }
    }

    // =========================================================================
    // Company Access Tests
    // =========================================================================

    @Nested
    @DisplayName("Company Access")
    class CompanyAccessTests {

        @Test
        @DisplayName("Company can get influencer contact at TO_BE_PAID status")
        void companyCanGetInfluencerContactAtToBePaid() {
            // Set up user preferences for the influencer (sharePhone enabled)
            setUpUserPreferences(testInfluencer, true);

            authenticateAs(testInfluencer);
            AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);

            authenticateAs(testCompany);
            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

            assertThat(contact).isNotNull();
            assertThat(contact.name()).isEqualTo(testInfluencer.getName());
            assertThat(contact.email()).isEqualTo(testInfluencer.getEmail());
        }

        @Test
        @DisplayName("Company can get influencer contact at DONE status")
        void companyCanGetInfluencerContactAtDone() {
            setUpUserPreferences(testInfluencer, true);

            authenticateAs(testInfluencer);
            AppliedOpportunity application = createCompletedOpportunity(testInfluencer, testPartnershipOpportunity);

            authenticateAs(testCompany);
            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

            assertThat(contact).isNotNull();
            assertThat(contact.name()).isEqualTo(testInfluencer.getName());
            assertThat(contact.email()).isEqualTo(testInfluencer.getEmail());
        }
    }

    // =========================================================================
    // Admin Access Tests
    // =========================================================================

    @Test
    @DisplayName("Admin can get contact at payment stage")
    void adminCanGetContactAtPaymentStage() {
        setUpUserPreferences(testInfluencer, true);

        authenticateAs(testInfluencer);
        AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);

        authenticateAs(testAdmin);
        PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

        // Admin sees influencer contact by default
        assertThat(contact).isNotNull();
        assertThat(contact.name()).isEqualTo(testInfluencer.getName());
        assertThat(contact.email()).isEqualTo(testInfluencer.getEmail());
    }

    // =========================================================================
    // Status Restriction Tests
    // =========================================================================

    @Nested
    @DisplayName("Status Restrictions")
    class StatusRestrictionTests {

        @Test
        @DisplayName("Cannot get contact before payment stage (APPLIED)")
        void cannotGetContactBeforePaymentStage() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );
            Long id = application.getId();

            assertThatThrownBy(() -> appliedOpportunityService.getPaymentContact(id))
                    .isInstanceOf(InsufficientPermissionsException.class)
                    .satisfies(ex -> {
                        InsufficientPermissionsException e = (InsufficientPermissionsException) ex;
                        assertThat(e.getResource()).containsIgnoringCase("payment stage");
                    });
        }

        @Test
        @DisplayName("Cannot get contact at ACCEPTED_BY_COMPANY status")
        void cannotGetContactAtAcceptedByCompany() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
            );
            Long id = application.getId();

            assertThatThrownBy(() -> appliedOpportunityService.getPaymentContact(id))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Cannot get contact at CONTENT_APPROVED status")
        void cannotGetContactAtContentApproved() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_APPROVED)
            );
            Long id = application.getId();

            assertThatThrownBy(() -> appliedOpportunityService.getPaymentContact(id))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =========================================================================
    // Permission Tests
    // =========================================================================

    @Test
    @DisplayName("Non-party user cannot get contact")
    void nonPartyCannotGetContact() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);
        Long id = application.getId();

        // secondCompany is not the owner of the opportunity
        authenticateAs(secondCompany);

        assertThatThrownBy(() -> appliedOpportunityService.getPaymentContact(id))
                .isInstanceOf(InsufficientPermissionsException.class)
                .satisfies(ex -> {
                    InsufficientPermissionsException e = (InsufficientPermissionsException) ex;
                    assertThat(e.getResource()).containsIgnoringCase("not party");
                });
    }

    @Test
    @DisplayName("Other influencer cannot get contact")
    void otherInfluencerCannotGetContact() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);
        Long id = application.getId();

        // secondInfluencer is not the applicant
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);

        assertThatThrownBy(() -> appliedOpportunityService.getPaymentContact(id))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    // =========================================================================
    // Phone Privacy Tests
    // =========================================================================

    @Nested
    @DisplayName("Phone Privacy")
    class PhonePrivacyTests {

        @Test
        @DisplayName("Phone is hidden if user has not enabled sharing")
        void phoneHiddenIfNotShared() {
            // Set up preferences WITHOUT phone sharing
            setUpUserPreferences(testInfluencer, false);

            authenticateAs(testInfluencer);
            AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);

            authenticateAs(testCompany);
            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

            assertThat(contact).isNotNull();
            assertThat(contact.name()).isEqualTo(testInfluencer.getName());
            assertThat(contact.email()).isEqualTo(testInfluencer.getEmail());
            assertThat(contact.phone()).isNull(); // Phone should be hidden
        }

        @Test
        @DisplayName("Phone is visible if user has enabled sharing")
        void phoneVisibleIfShared() {
            // Set a phone number and enable sharing
            testInfluencer.setPhoneNumber("+48123456789");
            userRepository.save(testInfluencer);
            setUpUserPreferences(testInfluencer, true);

            authenticateAs(testInfluencer);
            AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);

            authenticateAs(testCompany);
            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

            assertThat(contact).isNotNull();
            assertThat(contact.phone()).isEqualTo("+48123456789");
        }

        @Test
        @DisplayName("Phone is null when no preferences exist")
        void phoneNullWhenNoPreferences() {
            // Don't set up any preferences - simulate missing record
            authenticateAs(testInfluencer);
            AppliedOpportunity application = createPaymentPendingOpportunity(testInfluencer, testPartnershipOpportunity);

            authenticateAs(testCompany);
            PaymentContactDto contact = appliedOpportunityService.getPaymentContact(application.getId());

            assertThat(contact).isNotNull();
            // Without preferences, phone should not be shared
            assertThat(contact.phone()).isNull();
        }
    }
}
