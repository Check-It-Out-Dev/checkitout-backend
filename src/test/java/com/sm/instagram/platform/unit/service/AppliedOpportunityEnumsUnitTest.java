package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.ContentApprovalStatus;
import com.sm.instagram.platform.appliedopportunities
        .OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Applied Opportunity enums.
 * Tests state machine logic, transitions, and enum properties.
 */
@DisplayName("Applied Opportunity Enums Unit Tests")
class AppliedOpportunityEnumsUnitTest {

    // ==================== OpportunityStatus Enum Tests ====================

    @Nested
    @DisplayName("OpportunityStatus Enum")
    class OpportunityStatusEnumTests {

        @Test
        @DisplayName("should have exactly 12 status values")
        void shouldHaveExactlyTwelveStatusValues() {
            assertThat(OpportunityStatus.values()).hasSize(12);
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should be able to valueOf all statuses")
        void shouldBeAbleToValueOfAllStatuses(OpportunityStatus status) {
            assertThat(OpportunityStatus.valueOf(status.name())).isEqualTo(status);
        }

        @Nested
        @DisplayName("Enum Properties")
        class EnumPropertiesTests {

            @ParameterizedTest
            @EnumSource(OpportunityStatus.class)
            @DisplayName("all statuses should have non-null colorTheme")
            void allStatusesShouldHaveNonNullColorTheme(OpportunityStatus status) {
                assertThat(status.getColorTheme()).isNotBlank();
            }

            @ParameterizedTest
            @EnumSource(OpportunityStatus.class)
            @DisplayName("all statuses should have non-null icon")
            void allStatusesShouldHaveNonNullIcon(OpportunityStatus status) {
                assertThat(status.getIcon()).isNotBlank();
            }

            @ParameterizedTest
            @EnumSource(OpportunityStatus.class)
            @DisplayName("all statuses should have non-empty aliases")
            void allStatusesShouldHaveNonEmptyAliases(OpportunityStatus status) {
                assertThat(status.getAliases()).isNotEmpty();
            }

            @ParameterizedTest
            @EnumSource(OpportunityStatus.class)
            @DisplayName("all statuses should have description")
            void allStatusesShouldHaveDescription(OpportunityStatus status) {
                assertThat(status.getDescription()).isNotBlank();
            }

            @ParameterizedTest
            @EnumSource(OpportunityStatus.class)
            @DisplayName("getValue should return enum name")
            void getValueShouldReturnEnumName(OpportunityStatus status) {
                assertThat(status.getValue()).isEqualTo(status.name());
            }

            @Test
            @DisplayName("APPLIED should have primary color theme")
            void appliedShouldHavePrimaryColorTheme() {
                assertThat(OpportunityStatus.APPLIED.getColorTheme()).isEqualTo("primary");
            }

            @Test
            @DisplayName("DONE should have success color theme")
            void doneShouldHaveSuccessColorTheme() {
                assertThat(OpportunityStatus.DONE.getColorTheme()).isEqualTo("success");
            }

            @Test
            @DisplayName("REJECTED_BY_COMPANY should have danger color theme")
            void rejectedByCompanyShouldHaveDangerColorTheme() {
                assertThat(OpportunityStatus.REJECTED_BY_COMPANY.getColorTheme()).isEqualTo("danger");
            }
        }

        @Nested
        @DisplayName("fromString method")
        class FromStringTests {

            @ParameterizedTest
            @CsvSource({
                    "APPLIED, APPLIED",
                    "applied, APPLIED",
                    "Applied, APPLIED",
                    "DONE, DONE",
                    "done, DONE"
            })
            @DisplayName("should parse various case formats")
            void shouldParseVariousCaseFormats(String input, OpportunityStatus expected) {
                assertThat(OpportunityStatus.fromString(input)).isEqualTo(expected);
            }

            @Test
            @DisplayName("should throw exception for invalid value")
            void shouldThrowExceptionForInvalidValue() {
                assertThatThrownBy(() -> OpportunityStatus.fromString("INVALID"))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        @DisplayName("getActiveStatuses method")
        class GetActiveStatusesTests {

            @Test
            @DisplayName("should not include REJECTED_BY_COMPANY")
            void shouldNotIncludeRejectedByCompany() {
                List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().collect(Collectors.toList());
                assertThat(activeStatuses).doesNotContain(OpportunityStatus.REJECTED_BY_COMPANY);
            }

            @Test
            @DisplayName("should not include REJECTED_BY_INFLUENCER")
            void shouldNotIncludeRejectedByInfluencer() {
                List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().collect(Collectors.toList());
                assertThat(activeStatuses).doesNotContain(OpportunityStatus.REJECTED_BY_INFLUENCER);
            }

            @Test
            @DisplayName("should not include DONE")
            void shouldNotIncludeDone() {
                List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().collect(Collectors.toList());
                assertThat(activeStatuses).doesNotContain(OpportunityStatus.DONE);
            }

            @Test
            @DisplayName("should include APPLIED")
            void shouldIncludeApplied() {
                List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().collect(Collectors.toList());
                assertThat(activeStatuses).contains(OpportunityStatus.APPLIED);
            }

            @Test
            @DisplayName("should include CONTENT_SEND_TO_ACCEPT")
            void shouldIncludeContentSendToAccept() {
                List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().collect(Collectors.toList());
                assertThat(activeStatuses).contains(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            }
        }

        @Nested
        @DisplayName("getCompletedStatuses method")
        class GetCompletedStatusesTests {

            @Test
            @DisplayName("should include REJECTED_BY_COMPANY")
            void shouldIncludeRejectedByCompany() {
                List<OpportunityStatus> completedStatuses = OpportunityStatus.getCompletedStatuses().collect(Collectors.toList());
                assertThat(completedStatuses).contains(OpportunityStatus.REJECTED_BY_COMPANY);
            }

            @Test
            @DisplayName("should include REJECTED_BY_INFLUENCER")
            void shouldIncludeRejectedByInfluencer() {
                List<OpportunityStatus> completedStatuses = OpportunityStatus.getCompletedStatuses().collect(Collectors.toList());
                assertThat(completedStatuses).contains(OpportunityStatus.REJECTED_BY_INFLUENCER);
            }

            @Test
            @DisplayName("should include DONE")
            void shouldIncludeDone() {
                List<OpportunityStatus> completedStatuses = OpportunityStatus.getCompletedStatuses().collect(Collectors.toList());
                assertThat(completedStatuses).contains(OpportunityStatus.DONE);
            }

            @Test
            @DisplayName("should not include APPLIED")
            void shouldNotIncludeApplied() {
                List<OpportunityStatus> completedStatuses = OpportunityStatus.getCompletedStatuses().collect(Collectors.toList());
                assertThat(completedStatuses).doesNotContain(OpportunityStatus.APPLIED);
            }
        }

        @Nested
        @DisplayName("isTerminalStatus method")
        class IsTerminalStatusTests {

            @Test
            @DisplayName("REJECTED_BY_COMPANY should be terminal")
            void rejectedByCompanyShouldBeTerminal() {
                assertThat(OpportunityStatus.REJECTED_BY_COMPANY.isTerminalStatus()).isTrue();
            }

            @Test
            @DisplayName("REJECTED_BY_INFLUENCER should be terminal")
            void rejectedByInfluencerShouldBeTerminal() {
                assertThat(OpportunityStatus.REJECTED_BY_INFLUENCER.isTerminalStatus()).isTrue();
            }

            @Test
            @DisplayName("DONE should be terminal")
            void doneShouldBeTerminal() {
                assertThat(OpportunityStatus.DONE.isTerminalStatus()).isTrue();
            }

            @ParameterizedTest
            @EnumSource(value = OpportunityStatus.class, names = {"APPLIED", "ACCEPTED_BY_COMPANY",
                    "ACCEPTED_BY_INFLUENCER", "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED",
                    "CONTENT_REJECTED", "CONTENT_POSTED", "CONTENT_POSTED_REJECTED", "TO_BE_PAID"})
            @DisplayName("non-terminal statuses should not be terminal")
            void nonTerminalStatusesShouldNotBeTerminal(OpportunityStatus status) {
                assertThat(status.isTerminalStatus()).isFalse();
            }
        }

        @Nested
        @DisplayName("isSuccessfulCompletion method")
        class IsSuccessfulCompletionTests {

            @Test
            @DisplayName("DONE should be successful completion")
            void doneShouldBeSuccessfulCompletion() {
                assertThat(OpportunityStatus.DONE.isSuccessfulCompletion()).isTrue();
            }

            @ParameterizedTest
            @EnumSource(value = OpportunityStatus.class, names = {"APPLIED", "ACCEPTED_BY_COMPANY",
                    "REJECTED_BY_COMPANY", "ACCEPTED_BY_INFLUENCER", "REJECTED_BY_INFLUENCER",
                    "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED", "CONTENT_REJECTED",
                    "CONTENT_POSTED", "CONTENT_POSTED_REJECTED", "TO_BE_PAID"})
            @DisplayName("non-DONE statuses should not be successful completion")
            void nonDoneStatusesShouldNotBeSuccessfulCompletion(OpportunityStatus status) {
                assertThat(status.isSuccessfulCompletion()).isFalse();
            }
        }

        @Nested
        @DisplayName("canTransitionTo method")
        class CanTransitionToTests {

            @Test
            @DisplayName("APPLIED can transition to ACCEPTED_BY_COMPANY")
            void appliedCanTransitionToAcceptedByCompany() {
                assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.ACCEPTED_BY_COMPANY)).isTrue();
            }

            @Test
            @DisplayName("APPLIED can transition to REJECTED_BY_COMPANY")
            void appliedCanTransitionToRejectedByCompany() {
                assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.REJECTED_BY_COMPANY)).isTrue();
            }

            @Test
            @DisplayName("APPLIED cannot transition to DONE")
            void appliedCannotTransitionToDone() {
                assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.DONE)).isFalse();
            }

            @Test
            @DisplayName("ACCEPTED_BY_COMPANY can transition to ACCEPTED_BY_INFLUENCER")
            void acceptedByCompanyCanTransitionToAcceptedByInfluencer() {
                assertThat(OpportunityStatus.ACCEPTED_BY_COMPANY.canTransitionTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER)).isTrue();
            }

            @Test
            @DisplayName("ACCEPTED_BY_COMPANY can transition to REJECTED_BY_INFLUENCER")
            void acceptedByCompanyCanTransitionToRejectedByInfluencer() {
                assertThat(OpportunityStatus.ACCEPTED_BY_COMPANY.canTransitionTo(OpportunityStatus.REJECTED_BY_INFLUENCER)).isTrue();
            }

            @Test
            @DisplayName("ACCEPTED_BY_INFLUENCER can only transition to CONTENT_SEND_TO_ACCEPT")
            void acceptedByInfluencerCanOnlyTransitionToContentSendToAccept() {
                assertThat(OpportunityStatus.ACCEPTED_BY_INFLUENCER.canTransitionTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT)).isTrue();
                assertThat(OpportunityStatus.ACCEPTED_BY_INFLUENCER.canTransitionTo(OpportunityStatus.DONE)).isFalse();
            }

            @Test
            @DisplayName("CONTENT_SEND_TO_ACCEPT can transition to CONTENT_APPROVED")
            void contentSendToAcceptCanTransitionToContentApproved() {
                assertThat(OpportunityStatus.CONTENT_SEND_TO_ACCEPT.canTransitionTo(OpportunityStatus.CONTENT_APPROVED)).isTrue();
            }

            @Test
            @DisplayName("CONTENT_SEND_TO_ACCEPT can transition to CONTENT_REJECTED")
            void contentSendToAcceptCanTransitionToContentRejected() {
                assertThat(OpportunityStatus.CONTENT_SEND_TO_ACCEPT.canTransitionTo(OpportunityStatus.CONTENT_REJECTED)).isTrue();
            }

            @Test
            @DisplayName("CONTENT_REJECTED can transition to CONTENT_SEND_TO_ACCEPT (resubmit)")
            void contentRejectedCanTransitionToContentSendToAccept() {
                assertThat(OpportunityStatus.CONTENT_REJECTED.canTransitionTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT)).isTrue();
            }

            @Test
            @DisplayName("CONTENT_REJECTED can transition to REJECTED_BY_INFLUENCER (resign)")
            void contentRejectedCanTransitionToRejectedByInfluencer() {
                assertThat(OpportunityStatus.CONTENT_REJECTED.canTransitionTo(OpportunityStatus.REJECTED_BY_INFLUENCER)).isTrue();
            }

            @Test
            @DisplayName("CONTENT_APPROVED can only transition to CONTENT_POSTED")
            void contentApprovedCanOnlyTransitionToContentPosted() {
                assertThat(OpportunityStatus.CONTENT_APPROVED.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isTrue();
                assertThat(OpportunityStatus.CONTENT_APPROVED.canTransitionTo(OpportunityStatus.DONE)).isFalse();
            }

            @Test
            @DisplayName("CONTENT_POSTED can transition to TO_BE_PAID")
            void contentPostedCanTransitionToToBePaid() {
                assertThat(OpportunityStatus.CONTENT_POSTED.canTransitionTo(OpportunityStatus.TO_BE_PAID)).isTrue();
            }

            @Test
            @DisplayName("CONTENT_POSTED can transition to CONTENT_POSTED_REJECTED")
            void contentPostedCanTransitionToContentPostedRejected() {
                assertThat(OpportunityStatus.CONTENT_POSTED.canTransitionTo(OpportunityStatus.CONTENT_POSTED_REJECTED)).isTrue();
            }

            @Test
            @DisplayName("CONTENT_POSTED_REJECTED can transition to CONTENT_POSTED")
            void contentPostedRejectedCanTransitionToContentPosted() {
                assertThat(OpportunityStatus.CONTENT_POSTED_REJECTED.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isTrue();
            }

            @Test
            @DisplayName("TO_BE_PAID can only transition to DONE")
            void toBePaidCanOnlyTransitionToDone() {
                assertThat(OpportunityStatus.TO_BE_PAID.canTransitionTo(OpportunityStatus.DONE)).isTrue();
                assertThat(OpportunityStatus.TO_BE_PAID.canTransitionTo(OpportunityStatus.APPLIED)).isFalse();
            }

            @Test
            @DisplayName("terminal statuses cannot transition")
            void terminalStatusesCannotTransition() {
                assertThat(OpportunityStatus.REJECTED_BY_COMPANY.canTransitionTo(OpportunityStatus.APPLIED)).isFalse();
                assertThat(OpportunityStatus.REJECTED_BY_INFLUENCER.canTransitionTo(OpportunityStatus.APPLIED)).isFalse();
                assertThat(OpportunityStatus.DONE.canTransitionTo(OpportunityStatus.APPLIED)).isFalse();
            }
        }

        @Nested
        @DisplayName("getNextStatus method")
        class GetNextStatusTests {

            @Test
            @DisplayName("APPLIED with accept returns ACCEPTED_BY_COMPANY")
            void appliedWithAcceptReturnsAcceptedByCompany() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, true))
                        .isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
            }

            @Test
            @DisplayName("APPLIED with reject returns REJECTED_BY_COMPANY")
            void appliedWithRejectReturnsRejectedByCompany() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, false))
                        .isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
            }

            @Test
            @DisplayName("ACCEPTED_BY_COMPANY with accept returns ACCEPTED_BY_INFLUENCER")
            void acceptedByCompanyWithAcceptReturnsAcceptedByInfluencer() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_COMPANY, true))
                        .isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
            }

            @Test
            @DisplayName("ACCEPTED_BY_COMPANY with reject returns REJECTED_BY_INFLUENCER")
            void acceptedByCompanyWithRejectReturnsRejectedByInfluencer() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_COMPANY, false))
                        .isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
            }

            @Test
            @DisplayName("ACCEPTED_BY_INFLUENCER with accept returns CONTENT_SEND_TO_ACCEPT")
            void acceptedByInfluencerWithAcceptReturnsContentSendToAccept() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER, true))
                        .isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            }

            @Test
            @DisplayName("ACCEPTED_BY_INFLUENCER with reject throws exception")
            void acceptedByInfluencerWithRejectThrowsException() {
                assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER, false))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Cannot reject opportunity at this stage");
            }

            @Test
            @DisplayName("CONTENT_SEND_TO_ACCEPT with accept returns CONTENT_APPROVED")
            void contentSendToAcceptWithAcceptReturnsContentApproved() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, true))
                        .isEqualTo(OpportunityStatus.CONTENT_APPROVED);
            }

            @Test
            @DisplayName("CONTENT_SEND_TO_ACCEPT with reject returns CONTENT_REJECTED")
            void contentSendToAcceptWithRejectReturnsContentRejected() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, false))
                        .isEqualTo(OpportunityStatus.CONTENT_REJECTED);
            }

            @Test
            @DisplayName("CONTENT_APPROVED with accept returns CONTENT_POSTED")
            void contentApprovedWithAcceptReturnsContentPosted() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_APPROVED, true))
                        .isEqualTo(OpportunityStatus.CONTENT_POSTED);
            }

            @Test
            @DisplayName("CONTENT_APPROVED with reject throws exception")
            void contentApprovedWithRejectThrowsException() {
                assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_APPROVED, false))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Cannot set status to 'rejected' at this stage");
            }

            @Test
            @DisplayName("CONTENT_REJECTED with accept returns CONTENT_SEND_TO_ACCEPT")
            void contentRejectedWithAcceptReturnsContentSendToAccept() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_REJECTED, true))
                        .isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            }

            @Test
            @DisplayName("CONTENT_REJECTED with reject returns REJECTED_BY_INFLUENCER")
            void contentRejectedWithRejectReturnsRejectedByInfluencer() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_REJECTED, false))
                        .isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
            }

            @Test
            @DisplayName("CONTENT_POSTED with accept returns TO_BE_PAID")
            void contentPostedWithAcceptReturnsToBePaid() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED, true))
                        .isEqualTo(OpportunityStatus.TO_BE_PAID);
            }

            @Test
            @DisplayName("CONTENT_POSTED with reject returns CONTENT_POSTED_REJECTED")
            void contentPostedWithRejectReturnsContentPostedRejected() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED, false))
                        .isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);
            }

            @Test
            @DisplayName("CONTENT_POSTED_REJECTED with accept returns CONTENT_POSTED")
            void contentPostedRejectedWithAcceptReturnsContentPosted() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED_REJECTED, true))
                        .isEqualTo(OpportunityStatus.CONTENT_POSTED);
            }

            @Test
            @DisplayName("CONTENT_POSTED_REJECTED with reject throws exception")
            void contentPostedRejectedWithRejectThrowsException() {
                assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED_REJECTED, false))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("TO_BE_PAID with accept returns DONE")
            void toBePaidWithAcceptReturnsDone() {
                assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, true))
                        .isEqualTo(OpportunityStatus.DONE);
            }

            @Test
            @DisplayName("TO_BE_PAID with reject throws exception")
            void toBePaidWithRejectThrowsException() {
                assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, false))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Cannot reject opportunity at this stage");
            }

            @Test
            @DisplayName("terminal statuses throw exception")
            void terminalStatusesThrowException() {
                assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.DONE, true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("does not allow further transitions");

                assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.REJECTED_BY_COMPANY, true))
                        .isInstanceOf(IllegalStateException.class);

                assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.REJECTED_BY_INFLUENCER, true))
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @Nested
        @DisplayName("getPossibleTransitions method")
        class GetPossibleTransitionsTests {

            @Test
            @DisplayName("APPLIED should have 2 possible transitions")
            void appliedShouldHaveTwoPossibleTransitions() {
                List<OpportunityStatus> transitions = OpportunityStatus.APPLIED.getPossibleTransitions();
                assertThat(transitions).hasSize(2);
                assertThat(transitions).containsExactlyInAnyOrder(
                        OpportunityStatus.ACCEPTED_BY_COMPANY,
                        OpportunityStatus.REJECTED_BY_COMPANY
                );
            }

            @Test
            @DisplayName("ACCEPTED_BY_INFLUENCER should have 1 possible transition")
            void acceptedByInfluencerShouldHaveOnePossibleTransition() {
                List<OpportunityStatus> transitions = OpportunityStatus.ACCEPTED_BY_INFLUENCER.getPossibleTransitions();
                assertThat(transitions).hasSize(1);
                assertThat(transitions).containsExactly(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            }

            @Test
            @DisplayName("DONE should have no possible transitions")
            void doneShouldHaveNoPossibleTransitions() {
                List<OpportunityStatus> transitions = OpportunityStatus.DONE.getPossibleTransitions();
                assertThat(transitions).isEmpty();
            }

            @Test
            @DisplayName("CONTENT_REJECTED should have 2 possible transitions")
            void contentRejectedShouldHaveTwoPossibleTransitions() {
                List<OpportunityStatus> transitions = OpportunityStatus.CONTENT_REJECTED.getPossibleTransitions();
                assertThat(transitions).hasSize(2);
                assertThat(transitions).containsExactlyInAnyOrder(
                        OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                        OpportunityStatus.REJECTED_BY_INFLUENCER
                );
            }
        }
    }

    // ==================== ContentApprovalStatus Enum Tests ====================

    @Nested
    @DisplayName("ContentApprovalStatus Enum")
    class ContentApprovalStatusEnumTests {

        @Test
        @DisplayName("should have exactly 5 status values")
        void shouldHaveExactlyFiveStatusValues() {
            assertThat(ContentApprovalStatus.values()).hasSize(5);
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("should be able to valueOf all statuses")
        void shouldBeAbleToValueOfAllStatuses(ContentApprovalStatus status) {
            assertThat(ContentApprovalStatus.valueOf(status.name())).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(ContentApprovalStatus.class)
        @DisplayName("getValue should return enum name (UPPERCASE)")
        void getValueShouldReturnEnumName(ContentApprovalStatus status) {
            assertThat(status.getValue()).isEqualTo(status.name());
        }

        @ParameterizedTest
        @CsvSource({
                "PENDING, pending",
                "APPROVED, approved",
                "REJECTED, rejected",
                "NEEDS_REVISION, needs_revision",
                "SUBMITTED, submitted"
        })
        @DisplayName("toString should return value")
        void toStringShouldReturnValue(ContentApprovalStatus status, String expectedValue) {
            assertThat(status.toString()).isEqualTo(expectedValue);
        }
    }

    // ==================== RateStatus Enum Tests ====================

    @Nested
    @DisplayName("RateStatus Enum")
    class RateStatusEnumTests {

        @Test
        @DisplayName("should have exactly 3 status values")
        void shouldHaveExactlyThreeStatusValues() {
            assertThat(RateStatus.values()).hasSize(3);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("should be able to valueOf all statuses")
        void shouldBeAbleToValueOfAllStatuses(RateStatus status) {
            assertThat(RateStatus.valueOf(status.name())).isEqualTo(status);
        }

        @Nested
        @DisplayName("Enum Properties")
        class EnumPropertiesTests {

            @Test
            @DisplayName("DEFAULT should have secondary color theme")
            void defaultShouldHaveSecondaryColorTheme() {
                assertThat(RateStatus.DEFAULT.getColorTheme()).isEqualTo("secondary");
            }

            @Test
            @DisplayName("POSITIVE should have success color theme")
            void positiveShouldHaveSuccessColorTheme() {
                assertThat(RateStatus.POSITIVE.getColorTheme()).isEqualTo("success");
            }

            @Test
            @DisplayName("NEGATIVE should have danger color theme")
            void negativeShouldHaveDangerColorTheme() {
                assertThat(RateStatus.NEGATIVE.getColorTheme()).isEqualTo("danger");
            }

            @Test
            @DisplayName("DEFAULT should have circle icon")
            void defaultShouldHaveCircleIcon() {
                assertThat(RateStatus.DEFAULT.getIcon()).isEqualTo("circle");
            }

            @Test
            @DisplayName("POSITIVE should have thumbs-up icon")
            void positiveShouldHaveThumbsUpIcon() {
                assertThat(RateStatus.POSITIVE.getIcon()).isEqualTo("thumbs-up");
            }

            @Test
            @DisplayName("NEGATIVE should have thumbs-down icon")
            void negativeShouldHaveThumbsDownIcon() {
                assertThat(RateStatus.NEGATIVE.getIcon()).isEqualTo("thumbs-down");
            }

            @ParameterizedTest
            @EnumSource(RateStatus.class)
            @DisplayName("all statuses should have non-empty aliases")
            void allStatusesShouldHaveNonEmptyAliases(RateStatus status) {
                assertThat(status.getAliases()).isNotEmpty();
            }

            @ParameterizedTest
            @EnumSource(RateStatus.class)
            @DisplayName("getValue should return enum name")
            void getValueShouldReturnEnumName(RateStatus status) {
                assertThat(status.getValue()).isEqualTo(status.name());
            }
        }

        @Nested
        @DisplayName("fromString method")
        class FromStringTests {

            @ParameterizedTest
            @CsvSource({
                    "DEFAULT, DEFAULT",
                    "default, DEFAULT",
                    "Default, DEFAULT",
                    "POSITIVE, POSITIVE",
                    "positive, POSITIVE",
                    "NEGATIVE, NEGATIVE",
                    "negative, NEGATIVE"
            })
            @DisplayName("should parse various case formats")
            void shouldParseVariousCaseFormats(String input, RateStatus expected) {
                assertThat(RateStatus.fromString(input)).isEqualTo(expected);
            }

            @Test
            @DisplayName("should throw exception for invalid value")
            void shouldThrowExceptionForInvalidValue() {
                assertThatThrownBy(() -> RateStatus.fromString("INVALID"))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        @DisplayName("Aliases")
        class AliasesTests {

            @Test
            @DisplayName("DEFAULT should have neutral alias")
            void defaultShouldHaveNeutralAlias() {
                assertThat(RateStatus.DEFAULT.getAliases()).contains("neutral");
            }

            @Test
            @DisplayName("POSITIVE should have good and liked aliases")
            void positiveShouldHaveGoodAndLikedAliases() {
                assertThat(RateStatus.POSITIVE.getAliases()).containsExactlyInAnyOrder("good", "liked");
            }

            @Test
            @DisplayName("NEGATIVE should have bad and disliked aliases")
            void negativeShouldHaveBadAndDislikedAliases() {
                assertThat(RateStatus.NEGATIVE.getAliases()).containsExactlyInAnyOrder("bad", "disliked");
            }
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("State Machine Integration")
    class StateMachineIntegrationTests {

        @Test
        @DisplayName("complete successful workflow from APPLIED to DONE")
        void completeSuccessfulWorkflowFromAppliedToDone() {
            OpportunityStatus status = OpportunityStatus.APPLIED;

            // Company accepts
            assertThat(status.canTransitionTo(OpportunityStatus.ACCEPTED_BY_COMPANY)).isTrue();
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);

            // Influencer accepts
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);

            // Influencer submits content
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);

            // Company approves content
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_APPROVED);

            // Influencer posts content
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_POSTED);

            // Company verifies posted content
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.TO_BE_PAID);

            // Payment completed
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.DONE);

            assertThat(status.isTerminalStatus()).isTrue();
            assertThat(status.isSuccessfulCompletion()).isTrue();
        }

        @Test
        @DisplayName("workflow with content rejection and resubmission")
        void workflowWithContentRejectionAndResubmission() {
            OpportunityStatus status = OpportunityStatus.CONTENT_SEND_TO_ACCEPT;

            // Company rejects content
            status = OpportunityStatus.getNextStatus(status, false);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_REJECTED);

            // Influencer resubmits content
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);

            // Company approves this time
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }

        @Test
        @DisplayName("workflow with posted content rejection and re-posting")
        void workflowWithPostedContentRejectionAndRePosting() {
            OpportunityStatus status = OpportunityStatus.CONTENT_POSTED;

            // Company rejects posted content
            status = OpportunityStatus.getNextStatus(status, false);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);

            // Influencer re-posts
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.CONTENT_POSTED);

            // Company approves
            status = OpportunityStatus.getNextStatus(status, true);
            assertThat(status).isEqualTo(OpportunityStatus.TO_BE_PAID);
        }
    }
}
