package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityService.FollowerValidationResult;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.FollowerValidationException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService follower validation methods.
 * Tests: validateInfluencerEligibility() and validateFollowerRange() (called during save).
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Influencer must have a CONNECTED primary social connection</li>
 *   <li>Follower count must be >= followersMin</li>
 *   <li>Follower count must be <= followersMax (when followersMax > 0)</li>
 *   <li>When followersMax = 0, there is no upper limit</li>
 * </ul>
 */
@DisplayName("AppliedOpportunityService - Follower Validation")
class AppliedOpportunityService_FollowerValidation_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    // =========================================================================
    // validateInfluencerEligibility Tests
    // =========================================================================

    @Nested
    @DisplayName("validateInfluencerEligibility")
    class ValidateInfluencerEligibilityTests {

        @Test
        @DisplayName("Returns success when follower count is within valid range")
        void returnsSuccessForValidRange() {
            // testInfluencer has 5000 followers (set up in base class)
            // testPartnershipOpportunity requires 1000-100000 followers
            authenticateAs(testInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    testPartnershipOpportunity.getId()
            );

            assertThat(result.isValid()).isTrue();
            assertThat(result.getCurrentFollowerCount()).isEqualTo(5000);
            assertThat(result.getMessage()).contains("meets requirements");
        }

        @Test
        @DisplayName("Returns failure when below minimum followers")
        void returnsFailureBelowMinimum() {
            // Create opportunity requiring more followers than testInfluencer has
            PartnershipOpportunity highFollowerOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 10000, 50000);

            authenticateAs(testInfluencer);  // testInfluencer has 5000 followers

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    highFollowerOpportunity.getId()
            );

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("below");
            assertThat(result.getCurrentFollowerCount()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Returns failure when above maximum followers")
        void returnsFailureAboveMaximum() {
            // Create opportunity with low max follower limit
            PartnershipOpportunity lowFollowerOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 100, 1000);

            authenticateAs(testInfluencer);  // testInfluencer has 5000 followers (above max)

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    lowFollowerOpportunity.getId()
            );

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("exceeds");
            assertThat(result.getCurrentFollowerCount()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Returns success when followersMax is very high (effectively unlimited)")
        void returnsSuccessWhenMaxIsVeryHigh() {
            // Create opportunity with very high max (effectively unlimited)
            // Note: Entity validation requires followersMax >= followersMin, so we can't use 0
            PartnershipOpportunity unlimitedOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 1000, Integer.MAX_VALUE);  // Effectively unlimited

            authenticateAs(testInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    unlimitedOpportunity.getId()
            );

            assertThat(result.isValid()).isTrue();
            assertThat(result.getCurrentFollowerCount()).isEqualTo(5000);
        }

        @Test
        @DisplayName("Returns failure when no social connection exists")
        void returnsFailureWithoutSocialConnections() {
            // secondInfluencer has no social connections set up
            authenticateAs(secondInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    secondInfluencer.getId(),
                    testPartnershipOpportunity.getId()
            );

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("verify follower count");
            assertThat(result.getCurrentFollowerCount()).isNull();
        }

        @Test
        @DisplayName("Returns failure when social connection is expired")
        void returnsFailureWithExpiredConnection() {
            // Create an expired social connection for secondInfluencer
            createExpiredSocialConnection(secondInfluencer, 5000);

            authenticateAs(secondInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    secondInfluencer.getId(),
                    testPartnershipOpportunity.getId()
            );

            // Expired connection should not be used for validation
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("verify follower count");
        }
    }

    // =========================================================================
    // validateFollowerRange During Save Tests
    // =========================================================================

    @Nested
    @DisplayName("Follower Validation During Save")
    class ValidationDuringSaveTests {

        @Test
        @DisplayName("Save throws FollowerValidationException when below minimum")
        void saveThrowsWhenBelowMinimum() {
            PartnershipOpportunity highFollowerOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 10000, 50000);

            authenticateAs(testInfluencer);  // 5000 followers

            assertThatThrownBy(() ->
                    appliedOpportunityService.save(
                            createTestAppliedOpportunity(testInfluencer, highFollowerOpportunity, OpportunityStatus.APPLIED)
                    )
            ).isInstanceOf(FollowerValidationException.class)
                    .hasMessageContaining("below");
        }

        @Test
        @DisplayName("Save throws FollowerValidationException when above maximum")
        void saveThrowsWhenAboveMaximum() {
            PartnershipOpportunity lowFollowerOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 100, 1000);

            authenticateAs(testInfluencer);  // 5000 followers

            assertThatThrownBy(() ->
                    appliedOpportunityService.save(
                            createTestAppliedOpportunity(testInfluencer, lowFollowerOpportunity, OpportunityStatus.APPLIED)
                    )
            ).isInstanceOf(FollowerValidationException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("Save succeeds when follower count is valid")
        void saveSucceedsWhenValid() {
            authenticateAs(testInfluencer);

            var result = appliedOpportunityService.save(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
        }

        @Test
        @DisplayName("Admin bypasses follower validation")
        void adminBypassesFollowerValidation() {
            PartnershipOpportunity highFollowerOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 100000, 500000);  // testInfluencer only has 5000

            authenticateAs(testAdmin);

            // Admin should be able to create application regardless of follower count
            var result = appliedOpportunityService.save(
                    createTestAppliedOpportunity(testInfluencer, highFollowerOpportunity, OpportunityStatus.APPLIED)
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Exactly at minimum followers is valid")
        void exactlyAtMinimumIsValid() {
            // Create opportunity with min = 5000 (exactly testInfluencer's count)
            PartnershipOpportunity exactMinOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 5000, 100000);

            authenticateAs(testInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    exactMinOpportunity.getId()
            );

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Exactly at maximum followers is valid")
        void exactlyAtMaximumIsValid() {
            // Create opportunity with max = 5000 (exactly testInfluencer's count)
            PartnershipOpportunity exactMaxOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 1000, 5000);

            authenticateAs(testInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    exactMaxOpportunity.getId()
            );

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("One follower below minimum is invalid")
        void oneBelowMinimumIsInvalid() {
            // Create opportunity with min = 5001 (one more than testInfluencer's count)
            PartnershipOpportunity justAboveMinOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 5001, 100000);

            authenticateAs(testInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    justAboveMinOpportunity.getId()
            );

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("One follower above maximum is invalid")
        void oneAboveMaximumIsInvalid() {
            // Create opportunity with max = 4999 (one less than testInfluencer's count)
            PartnershipOpportunity justBelowMaxOpportunity = createPartnershipOpportunityWithFollowerRange(
                    testCompany, 1000, 4999);

            authenticateAs(testInfluencer);

            FollowerValidationResult result = appliedOpportunityService.validateInfluencerEligibility(
                    testInfluencer.getId(),
                    justBelowMaxOpportunity.getId()
            );

            assertThat(result.isValid()).isFalse();
        }
    }
}
