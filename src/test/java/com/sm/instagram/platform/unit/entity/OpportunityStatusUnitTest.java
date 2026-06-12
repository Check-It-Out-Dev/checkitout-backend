package com.sm.instagram.platform.unit.entity;

import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.dictionary.DictionaryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OpportunityStatus State Machine Unit Tests")
class OpportunityStatusUnitTest {

    // ========== ENUM VALUES EXISTENCE TESTS ==========

    @Nested
    @DisplayName("Enum Values Existence")
    class EnumValuesExistence {

        @Test
        @DisplayName("Should have exactly 12 status values")
        void shouldHaveExactly12StatusValues() {
            assertThat(OpportunityStatus.values()).hasSize(12);
        }

        @ParameterizedTest(name = "Should contain status: {0}")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("Should contain all expected status values")
        void shouldContainAllExpectedStatusValues(OpportunityStatus status) {
            assertThat(status).isNotNull();
        }

        @Test
        @DisplayName("Should contain APPLIED status")
        void shouldContainAppliedStatus() {
            assertThat(OpportunityStatus.valueOf("APPLIED")).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("Should contain ACCEPTED_BY_COMPANY status")
        void shouldContainAcceptedByCompanyStatus() {
            assertThat(OpportunityStatus.valueOf("ACCEPTED_BY_COMPANY")).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        }

        @Test
        @DisplayName("Should contain REJECTED_BY_COMPANY status")
        void shouldContainRejectedByCompanyStatus() {
            assertThat(OpportunityStatus.valueOf("REJECTED_BY_COMPANY")).isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
        }

        @Test
        @DisplayName("Should contain ACCEPTED_BY_INFLUENCER status")
        void shouldContainAcceptedByInfluencerStatus() {
            assertThat(OpportunityStatus.valueOf("ACCEPTED_BY_INFLUENCER")).isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("Should contain REJECTED_BY_INFLUENCER status")
        void shouldContainRejectedByInfluencerStatus() {
            assertThat(OpportunityStatus.valueOf("REJECTED_BY_INFLUENCER")).isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("Should contain CONTENT_SEND_TO_ACCEPT status")
        void shouldContainContentSendToAcceptStatus() {
            assertThat(OpportunityStatus.valueOf("CONTENT_SEND_TO_ACCEPT")).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("Should contain CONTENT_APPROVED status")
        void shouldContainContentApprovedStatus() {
            assertThat(OpportunityStatus.valueOf("CONTENT_APPROVED")).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }

        @Test
        @DisplayName("Should contain CONTENT_REJECTED status")
        void shouldContainContentRejectedStatus() {
            assertThat(OpportunityStatus.valueOf("CONTENT_REJECTED")).isEqualTo(OpportunityStatus.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("Should contain CONTENT_POSTED status")
        void shouldContainContentPostedStatus() {
            assertThat(OpportunityStatus.valueOf("CONTENT_POSTED")).isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("Should contain CONTENT_POSTED_REJECTED status")
        void shouldContainContentPostedRejectedStatus() {
            assertThat(OpportunityStatus.valueOf("CONTENT_POSTED_REJECTED")).isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);
        }

        @Test
        @DisplayName("Should contain TO_BE_PAID status")
        void shouldContainToBePaidStatus() {
            assertThat(OpportunityStatus.valueOf("TO_BE_PAID")).isEqualTo(OpportunityStatus.TO_BE_PAID);
        }

        @Test
        @DisplayName("Should contain DONE status")
        void shouldContainDoneStatus() {
            assertThat(OpportunityStatus.valueOf("DONE")).isEqualTo(OpportunityStatus.DONE);
        }
    }

    // ========== METADATA TESTS ==========

    @Nested
    @DisplayName("Enum Metadata")
    class EnumMetadata {

        @ParameterizedTest(name = "Status {0} should have non-null colorTheme")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("All statuses should have non-null colorTheme")
        void allStatusesShouldHaveColorTheme(OpportunityStatus status) {
            assertThat(status.getColorTheme()).isNotNull().isNotBlank();
        }

        @ParameterizedTest(name = "Status {0} should have non-null icon")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("All statuses should have non-null icon")
        void allStatusesShouldHaveIcon(OpportunityStatus status) {
            assertThat(status.getIcon()).isNotNull().isNotBlank();
        }

        @ParameterizedTest(name = "Status {0} should have non-empty aliases")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("All statuses should have non-empty aliases list")
        void allStatusesShouldHaveAliases(OpportunityStatus status) {
            assertThat(status.getAliases()).isNotNull().isNotEmpty();
        }

        @ParameterizedTest(name = "Status {0} should have non-null description")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("All statuses should have non-null description")
        void allStatusesShouldHaveDescription(OpportunityStatus status) {
            assertThat(status.getDescription()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("APPLIED should have correct metadata")
        void appliedShouldHaveCorrectMetadata() {
            OpportunityStatus status = OpportunityStatus.APPLIED;
            assertThat(status.getColorTheme()).isEqualTo("primary");
            assertThat(status.getIcon()).isEqualTo("user");
            assertThat(status.getAliases()).contains("waiting");
        }

        @Test
        @DisplayName("DONE should have correct metadata")
        void doneShouldHaveCorrectMetadata() {
            OpportunityStatus status = OpportunityStatus.DONE;
            assertThat(status.getColorTheme()).isEqualTo("success");
            assertThat(status.getIcon()).isEqualTo("check-circle-2");
            assertThat(status.getAliases()).containsExactlyInAnyOrder("completed", "finished");
        }
    }

    // ========== JSON SERIALIZATION TESTS ==========

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerialization {

        @ParameterizedTest(name = "getValue() should return name for {0}")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("getValue() should return enum name")
        void getValueShouldReturnEnumName(OpportunityStatus status) {
            assertThat(status.getValue()).isEqualTo(status.name());
        }

        @ParameterizedTest(name = "fromString() should parse uppercase {0}")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("fromString() should parse uppercase values")
        void fromStringShouldParseUppercase(OpportunityStatus status) {
            assertThat(OpportunityStatus.fromString(status.name())).isEqualTo(status);
        }

        @Test
        @DisplayName("fromString() should parse lowercase value")
        void fromStringShouldParseLowercase() {
            assertThat(OpportunityStatus.fromString("applied")).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("fromString() should parse mixed case value")
        void fromStringShouldParseMixedCase() {
            assertThat(OpportunityStatus.fromString("ApPlIeD")).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("fromString() should throw for invalid value")
        void fromStringShouldThrowForInvalidValue() {
            assertThatThrownBy(() -> OpportunityStatus.fromString("INVALID_STATUS"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========== TERMINAL STATUS TESTS ==========

    @Nested
    @DisplayName("Terminal Status Checks")
    class TerminalStatusChecks {

        @ParameterizedTest(name = "{0} should be terminal status")
        @EnumSource(value = OpportunityStatus.class, names = {"REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER", "DONE"})
        @DisplayName("Terminal statuses should return true for isTerminalStatus()")
        void terminalStatusesShouldReturnTrue(OpportunityStatus status) {
            assertThat(status.isTerminalStatus()).isTrue();
        }

        @ParameterizedTest(name = "{0} should NOT be terminal status")
        @EnumSource(value = OpportunityStatus.class, names = {
                "APPLIED", "ACCEPTED_BY_COMPANY", "ACCEPTED_BY_INFLUENCER",
                "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED", "CONTENT_REJECTED",
                "CONTENT_POSTED", "CONTENT_POSTED_REJECTED", "TO_BE_PAID"
        })
        @DisplayName("Non-terminal statuses should return false for isTerminalStatus()")
        void nonTerminalStatusesShouldReturnFalse(OpportunityStatus status) {
            assertThat(status.isTerminalStatus()).isFalse();
        }
    }

    // ========== SUCCESSFUL COMPLETION TESTS ==========

    @Nested
    @DisplayName("Successful Completion Checks")
    class SuccessfulCompletionChecks {

        @Test
        @DisplayName("DONE should be successful completion")
        void doneShouldBeSuccessfulCompletion() {
            assertThat(OpportunityStatus.DONE.isSuccessfulCompletion()).isTrue();
        }

        @ParameterizedTest(name = "{0} should NOT be successful completion")
        @EnumSource(value = OpportunityStatus.class, names = {
                "APPLIED", "ACCEPTED_BY_COMPANY", "REJECTED_BY_COMPANY",
                "ACCEPTED_BY_INFLUENCER", "REJECTED_BY_INFLUENCER",
                "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED", "CONTENT_REJECTED",
                "CONTENT_POSTED", "CONTENT_POSTED_REJECTED", "TO_BE_PAID"
        })
        @DisplayName("Non-DONE statuses should return false for isSuccessfulCompletion()")
        void nonDoneStatusesShouldReturnFalse(OpportunityStatus status) {
            assertThat(status.isSuccessfulCompletion()).isFalse();
        }
    }

    // ========== ACTIVE STATUSES TESTS ==========

    @Nested
    @DisplayName("Active Statuses")
    class ActiveStatuses {

        @Test
        @DisplayName("getActiveStatuses() should return 9 statuses")
        void getActiveStatusesShouldReturn9Statuses() {
            assertThat(OpportunityStatus.getActiveStatuses().count()).isEqualTo(9);
        }

        @Test
        @DisplayName("getActiveStatuses() should not contain terminal statuses")
        void getActiveStatusesShouldNotContainTerminalStatuses() {
            List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().toList();
            assertThat(activeStatuses)
                    .doesNotContain(OpportunityStatus.REJECTED_BY_COMPANY)
                    .doesNotContain(OpportunityStatus.REJECTED_BY_INFLUENCER)
                    .doesNotContain(OpportunityStatus.DONE);
        }

        @Test
        @DisplayName("getActiveStatuses() should contain all non-terminal statuses")
        void getActiveStatusesShouldContainAllNonTerminalStatuses() {
            List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().toList();
            assertThat(activeStatuses).containsExactlyInAnyOrder(
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                    OpportunityStatus.CONTENT_APPROVED,
                    OpportunityStatus.CONTENT_REJECTED,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.CONTENT_POSTED_REJECTED,
                    OpportunityStatus.TO_BE_PAID
            );
        }
    }

    // ========== COMPLETED STATUSES TESTS ==========

    @Nested
    @DisplayName("Completed Statuses")
    class CompletedStatuses {

        @Test
        @DisplayName("getCompletedStatuses() should return 3 statuses")
        void getCompletedStatusesShouldReturn3Statuses() {
            assertThat(OpportunityStatus.getCompletedStatuses().count()).isEqualTo(3);
        }

        @Test
        @DisplayName("getCompletedStatuses() should contain only terminal statuses")
        void getCompletedStatusesShouldContainOnlyTerminalStatuses() {
            List<OpportunityStatus> completedStatuses = OpportunityStatus.getCompletedStatuses().toList();
            assertThat(completedStatuses).containsExactlyInAnyOrder(
                    OpportunityStatus.REJECTED_BY_COMPANY,
                    OpportunityStatus.REJECTED_BY_INFLUENCER,
                    OpportunityStatus.DONE
            );
        }
    }

    // ========== CAN TRANSITION TO TESTS ==========

    @Nested
    @DisplayName("canTransitionTo() Valid Transitions")
    class CanTransitionToValidTransitions {

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
        @DisplayName("ACCEPTED_BY_INFLUENCER can transition to CONTENT_SEND_TO_ACCEPT")
        void acceptedByInfluencerCanTransitionToContentSendToAccept() {
            assertThat(OpportunityStatus.ACCEPTED_BY_INFLUENCER.canTransitionTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT)).isTrue();
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
        @DisplayName("CONTENT_APPROVED can transition to CONTENT_POSTED")
        void contentApprovedCanTransitionToContentPosted() {
            assertThat(OpportunityStatus.CONTENT_APPROVED.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isTrue();
        }

        @Test
        @DisplayName("CONTENT_REJECTED can transition to CONTENT_SEND_TO_ACCEPT")
        void contentRejectedCanTransitionToContentSendToAccept() {
            assertThat(OpportunityStatus.CONTENT_REJECTED.canTransitionTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT)).isTrue();
        }

        @Test
        @DisplayName("CONTENT_REJECTED can transition to REJECTED_BY_INFLUENCER")
        void contentRejectedCanTransitionToRejectedByInfluencer() {
            assertThat(OpportunityStatus.CONTENT_REJECTED.canTransitionTo(OpportunityStatus.REJECTED_BY_INFLUENCER)).isTrue();
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
        @DisplayName("TO_BE_PAID can transition to DONE")
        void toBePaidCanTransitionToDone() {
            assertThat(OpportunityStatus.TO_BE_PAID.canTransitionTo(OpportunityStatus.DONE)).isTrue();
        }
    }

    @Nested
    @DisplayName("canTransitionTo() Invalid Transitions")
    class CanTransitionToInvalidTransitions {

        @ParameterizedTest(name = "REJECTED_BY_COMPANY cannot transition to {0}")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("Terminal status REJECTED_BY_COMPANY cannot transition anywhere")
        void rejectedByCompanyCannotTransitionAnywhere(OpportunityStatus target) {
            assertThat(OpportunityStatus.REJECTED_BY_COMPANY.canTransitionTo(target)).isFalse();
        }

        @ParameterizedTest(name = "REJECTED_BY_INFLUENCER cannot transition to {0}")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("Terminal status REJECTED_BY_INFLUENCER cannot transition anywhere")
        void rejectedByInfluencerCannotTransitionAnywhere(OpportunityStatus target) {
            assertThat(OpportunityStatus.REJECTED_BY_INFLUENCER.canTransitionTo(target)).isFalse();
        }

        @ParameterizedTest(name = "DONE cannot transition to {0}")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("Terminal status DONE cannot transition anywhere")
        void doneCannotTransitionAnywhere(OpportunityStatus target) {
            assertThat(OpportunityStatus.DONE.canTransitionTo(target)).isFalse();
        }

        @Test
        @DisplayName("APPLIED cannot transition to DONE directly")
        void appliedCannotTransitionToDone() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.DONE)).isFalse();
        }

        @Test
        @DisplayName("APPLIED cannot transition to CONTENT_POSTED directly")
        void appliedCannotTransitionToContentPosted() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isFalse();
        }

        @Test
        @DisplayName("CONTENT_APPROVED cannot transition to REJECTED_BY_COMPANY")
        void contentApprovedCannotTransitionToRejectedByCompany() {
            assertThat(OpportunityStatus.CONTENT_APPROVED.canTransitionTo(OpportunityStatus.REJECTED_BY_COMPANY)).isFalse();
        }

        @Test
        @DisplayName("TO_BE_PAID cannot transition backward to CONTENT_POSTED")
        void toBePaidCannotTransitionBackward() {
            assertThat(OpportunityStatus.TO_BE_PAID.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isFalse();
        }
    }

    // ========== GET POSSIBLE TRANSITIONS TESTS ==========

    @Nested
    @DisplayName("getPossibleTransitions()")
    class GetPossibleTransitions {

        @Test
        @DisplayName("APPLIED has 2 possible transitions")
        void appliedHas2PossibleTransitions() {
            List<OpportunityStatus> transitions = OpportunityStatus.APPLIED.getPossibleTransitions();
            assertThat(transitions).hasSize(2)
                    .containsExactlyInAnyOrder(OpportunityStatus.ACCEPTED_BY_COMPANY, OpportunityStatus.REJECTED_BY_COMPANY);
        }

        @Test
        @DisplayName("ACCEPTED_BY_COMPANY has 2 possible transitions")
        void acceptedByCompanyHas2PossibleTransitions() {
            List<OpportunityStatus> transitions = OpportunityStatus.ACCEPTED_BY_COMPANY.getPossibleTransitions();
            assertThat(transitions).hasSize(2)
                    .containsExactlyInAnyOrder(OpportunityStatus.ACCEPTED_BY_INFLUENCER, OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("ACCEPTED_BY_INFLUENCER has 1 possible transition")
        void acceptedByInfluencerHas1PossibleTransition() {
            List<OpportunityStatus> transitions = OpportunityStatus.ACCEPTED_BY_INFLUENCER.getPossibleTransitions();
            assertThat(transitions).hasSize(1)
                    .containsExactly(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("CONTENT_SEND_TO_ACCEPT has 2 possible transitions")
        void contentSendToAcceptHas2PossibleTransitions() {
            List<OpportunityStatus> transitions = OpportunityStatus.CONTENT_SEND_TO_ACCEPT.getPossibleTransitions();
            assertThat(transitions).hasSize(2)
                    .containsExactlyInAnyOrder(OpportunityStatus.CONTENT_APPROVED, OpportunityStatus.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("CONTENT_APPROVED has 1 possible transition")
        void contentApprovedHas1PossibleTransition() {
            List<OpportunityStatus> transitions = OpportunityStatus.CONTENT_APPROVED.getPossibleTransitions();
            assertThat(transitions).hasSize(1)
                    .containsExactly(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("CONTENT_REJECTED has 2 possible transitions")
        void contentRejectedHas2PossibleTransitions() {
            List<OpportunityStatus> transitions = OpportunityStatus.CONTENT_REJECTED.getPossibleTransitions();
            assertThat(transitions).hasSize(2)
                    .containsExactlyInAnyOrder(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("CONTENT_POSTED has 2 possible transitions")
        void contentPostedHas2PossibleTransitions() {
            List<OpportunityStatus> transitions = OpportunityStatus.CONTENT_POSTED.getPossibleTransitions();
            assertThat(transitions).hasSize(2)
                    .containsExactlyInAnyOrder(OpportunityStatus.TO_BE_PAID, OpportunityStatus.CONTENT_POSTED_REJECTED);
        }

        @Test
        @DisplayName("CONTENT_POSTED_REJECTED has 1 possible transition")
        void contentPostedRejectedHas1PossibleTransition() {
            List<OpportunityStatus> transitions = OpportunityStatus.CONTENT_POSTED_REJECTED.getPossibleTransitions();
            assertThat(transitions).hasSize(1)
                    .containsExactly(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("TO_BE_PAID has 1 possible transition")
        void toBePaidHas1PossibleTransition() {
            List<OpportunityStatus> transitions = OpportunityStatus.TO_BE_PAID.getPossibleTransitions();
            assertThat(transitions).hasSize(1)
                    .containsExactly(OpportunityStatus.DONE);
        }

        @ParameterizedTest(name = "{0} has 0 possible transitions")
        @EnumSource(value = OpportunityStatus.class, names = {"REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER", "DONE"})
        @DisplayName("Terminal statuses have no possible transitions")
        void terminalStatusesHaveNoPossibleTransitions(OpportunityStatus status) {
            assertThat(status.getPossibleTransitions()).isEmpty();
        }
    }

    // ========== GET NEXT STATUS TESTS ==========

    @Nested
    @DisplayName("getNextStatus() - Accept Path")
    class GetNextStatusAcceptPath {

        @Test
        @DisplayName("APPLIED + accept = ACCEPTED_BY_COMPANY")
        void appliedAcceptReturnsAcceptedByCompany() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, true))
                    .isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        }

        @Test
        @DisplayName("ACCEPTED_BY_COMPANY + accept = ACCEPTED_BY_INFLUENCER")
        void acceptedByCompanyAcceptReturnsAcceptedByInfluencer() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_COMPANY, true))
                    .isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("ACCEPTED_BY_INFLUENCER + accept = CONTENT_SEND_TO_ACCEPT")
        void acceptedByInfluencerAcceptReturnsContentSendToAccept() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER, true))
                    .isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("CONTENT_SEND_TO_ACCEPT + accept = CONTENT_APPROVED")
        void contentSendToAcceptAcceptReturnsContentApproved() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, true))
                    .isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }

        @Test
        @DisplayName("CONTENT_APPROVED + accept = CONTENT_POSTED")
        void contentApprovedAcceptReturnsContentPosted() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_APPROVED, true))
                    .isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("CONTENT_POSTED + accept = TO_BE_PAID")
        void contentPostedAcceptReturnsToBePaid() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED, true))
                    .isEqualTo(OpportunityStatus.TO_BE_PAID);
        }

        @Test
        @DisplayName("TO_BE_PAID + accept = DONE")
        void toBePaidAcceptReturnsDone() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, true))
                    .isEqualTo(OpportunityStatus.DONE);
        }

        @Test
        @DisplayName("CONTENT_REJECTED + accept = CONTENT_SEND_TO_ACCEPT (resubmit)")
        void contentRejectedAcceptReturnsContentSendToAccept() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_REJECTED, true))
                    .isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("CONTENT_POSTED_REJECTED + accept = CONTENT_POSTED")
        void contentPostedRejectedAcceptReturnsContentPosted() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED_REJECTED, true))
                    .isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }
    }

    @Nested
    @DisplayName("getNextStatus() - Reject Path")
    class GetNextStatusRejectPath {

        @Test
        @DisplayName("APPLIED + reject = REJECTED_BY_COMPANY")
        void appliedRejectReturnsRejectedByCompany() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, false))
                    .isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
        }

        @Test
        @DisplayName("ACCEPTED_BY_COMPANY + reject = REJECTED_BY_INFLUENCER")
        void acceptedByCompanyRejectReturnsRejectedByInfluencer() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_COMPANY, false))
                    .isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("CONTENT_SEND_TO_ACCEPT + reject = CONTENT_REJECTED")
        void contentSendToAcceptRejectReturnsContentRejected() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, false))
                    .isEqualTo(OpportunityStatus.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("CONTENT_POSTED + reject = CONTENT_POSTED_REJECTED")
        void contentPostedRejectReturnsContentPostedRejected() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED, false))
                    .isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);
        }

        @Test
        @DisplayName("CONTENT_REJECTED + reject = REJECTED_BY_INFLUENCER (resign)")
        void contentRejectedRejectReturnsRejectedByInfluencer() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_REJECTED, false))
                    .isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }
    }

    @Nested
    @DisplayName("getNextStatus() - Invalid Transitions")
    class GetNextStatusInvalidTransitions {

        @Test
        @DisplayName("ACCEPTED_BY_INFLUENCER + reject throws IllegalArgumentException")
        void acceptedByInfluencerRejectThrows() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot reject opportunity at this stage");
        }

        @Test
        @DisplayName("CONTENT_APPROVED + reject throws IllegalArgumentException")
        void contentApprovedRejectThrows() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_APPROVED, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot set status to 'rejected' at this stage");
        }

        @Test
        @DisplayName("CONTENT_POSTED_REJECTED + reject throws IllegalArgumentException")
        void contentPostedRejectedRejectThrows() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED_REJECTED, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot set status to 'rejected' at this stage");
        }

        @Test
        @DisplayName("TO_BE_PAID + reject throws IllegalArgumentException")
        void toBePaidRejectThrows() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot reject opportunity at this stage");
        }

        @ParameterizedTest(name = "{0} + accept/reject throws IllegalStateException")
        @EnumSource(value = OpportunityStatus.class, names = {"REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER", "DONE"})
        @DisplayName("Terminal statuses throw IllegalStateException on getNextStatus")
        void terminalStatusesThrowOnGetNextStatus(OpportunityStatus status) {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(status, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not allow further transitions");

            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(status, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not allow further transitions");
        }
    }

    // ========== LOCALIZATION TESTS ==========

    @Nested
    @DisplayName("Localization Methods")
    class LocalizationMethods {

        private DictionaryService mockDictionaryService;

        @BeforeEach
        void setUp() {
            mockDictionaryService = mock(DictionaryService.class);
        }

        @Test
        @DisplayName("getLabel() returns translated label when available")
        void getLabelReturnsTranslatedLabel() {
            when(mockDictionaryService.getTranslation("OPPORTUNITY_STATUS_APPLIED", "en"))
                    .thenReturn(Optional.of("Applied"));

            String label = OpportunityStatus.APPLIED.getLabel(mockDictionaryService, Locale.ENGLISH);

            assertThat(label).isEqualTo("Applied");
        }

        @Test
        @DisplayName("getLabel() returns enum name when translation not available")
        void getLabelReturnsEnumNameWhenTranslationMissing() {
            when(mockDictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String label = OpportunityStatus.APPLIED.getLabel(mockDictionaryService, Locale.ENGLISH);

            assertThat(label).isEqualTo("APPLIED");
        }

        @Test
        @DisplayName("getDescription() returns translated description when available")
        void getDescriptionReturnsTranslatedDescription() {
            when(mockDictionaryService.getTranslation("OPPORTUNITY_STATUS_APPLIED_DESC", "en"))
                    .thenReturn(Optional.of("Custom description"));

            String description = OpportunityStatus.APPLIED.getDescription(mockDictionaryService, Locale.ENGLISH);

            assertThat(description).isEqualTo("Custom description");
        }

        @Test
        @DisplayName("getDescription() returns default description when translation not available")
        void getDescriptionReturnsDefaultWhenTranslationMissing() {
            when(mockDictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String description = OpportunityStatus.APPLIED.getDescription(mockDictionaryService, Locale.ENGLISH);

            assertThat(description).isEqualTo(OpportunityStatus.APPLIED.getDescription());
        }

        @ParameterizedTest(name = "{0} has proper localization key format")
        @EnumSource(OpportunityStatus.class)
        @DisplayName("All statuses use correct localization key format")
        void allStatusesUseCorrectLocalizationKeyFormat(OpportunityStatus status) {
            when(mockDictionaryService.getTranslation(eq("OPPORTUNITY_STATUS_" + status.name()), eq("en")))
                    .thenReturn(Optional.of("Test Label"));

            String label = status.getLabel(mockDictionaryService, Locale.ENGLISH);

            assertThat(label).isEqualTo("Test Label");
        }
    }

    // ========== FULL STATE MACHINE PATH TESTS ==========

    @Nested
    @DisplayName("Full State Machine Paths")
    class FullStateMachinePaths {

        @Test
        @DisplayName("Happy path: APPLIED -> ... -> DONE")
        void happyPathFromAppliedToDone() {
            OpportunityStatus current = OpportunityStatus.APPLIED;

            // Company accepts
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);

            // Influencer accepts
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);

            // Submit content
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);

            // Content approved
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.CONTENT_APPROVED);

            // Content posted
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.CONTENT_POSTED);

            // Verified and ready for payment
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.TO_BE_PAID);

            // Payment done
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.DONE);

            assertThat(current.isTerminalStatus()).isTrue();
            assertThat(current.isSuccessfulCompletion()).isTrue();
        }

        @Test
        @DisplayName("Path with content rejection and resubmission")
        void pathWithContentRejectionAndResubmission() {
            OpportunityStatus current = OpportunityStatus.APPLIED;

            // Progress to content submission
            current = OpportunityStatus.getNextStatus(current, true); // ACCEPTED_BY_COMPANY
            current = OpportunityStatus.getNextStatus(current, true); // ACCEPTED_BY_INFLUENCER
            current = OpportunityStatus.getNextStatus(current, true); // CONTENT_SEND_TO_ACCEPT

            // Content rejected
            current = OpportunityStatus.getNextStatus(current, false);
            assertThat(current).isEqualTo(OpportunityStatus.CONTENT_REJECTED);

            // Resubmit
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);

            // Continue to completion
            current = OpportunityStatus.getNextStatus(current, true); // CONTENT_APPROVED
            current = OpportunityStatus.getNextStatus(current, true); // CONTENT_POSTED
            current = OpportunityStatus.getNextStatus(current, true); // TO_BE_PAID
            current = OpportunityStatus.getNextStatus(current, true); // DONE

            assertThat(current.isSuccessfulCompletion()).isTrue();
        }

        @Test
        @DisplayName("Path with posted content rejection and re-posting")
        void pathWithPostedContentRejectionAndRePosting() {
            OpportunityStatus current = OpportunityStatus.APPLIED;

            // Progress to content posted
            current = OpportunityStatus.getNextStatus(current, true); // ACCEPTED_BY_COMPANY
            current = OpportunityStatus.getNextStatus(current, true); // ACCEPTED_BY_INFLUENCER
            current = OpportunityStatus.getNextStatus(current, true); // CONTENT_SEND_TO_ACCEPT
            current = OpportunityStatus.getNextStatus(current, true); // CONTENT_APPROVED
            current = OpportunityStatus.getNextStatus(current, true); // CONTENT_POSTED

            // Posted content rejected
            current = OpportunityStatus.getNextStatus(current, false);
            assertThat(current).isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);

            // Re-post
            current = OpportunityStatus.getNextStatus(current, true);
            assertThat(current).isEqualTo(OpportunityStatus.CONTENT_POSTED);

            // Continue to completion
            current = OpportunityStatus.getNextStatus(current, true); // TO_BE_PAID
            current = OpportunityStatus.getNextStatus(current, true); // DONE

            assertThat(current.isSuccessfulCompletion()).isTrue();
        }

        @Test
        @DisplayName("Early rejection by company")
        void earlyRejectionByCompany() {
            OpportunityStatus current = OpportunityStatus.APPLIED;

            current = OpportunityStatus.getNextStatus(current, false);
            assertThat(current).isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
            assertThat(current.isTerminalStatus()).isTrue();
            assertThat(current.isSuccessfulCompletion()).isFalse();
        }

        @Test
        @DisplayName("Influencer resigns after content rejection")
        void influencerResignsAfterContentRejection() {
            OpportunityStatus current = OpportunityStatus.APPLIED;

            // Progress to content rejection
            current = OpportunityStatus.getNextStatus(current, true); // ACCEPTED_BY_COMPANY
            current = OpportunityStatus.getNextStatus(current, true); // ACCEPTED_BY_INFLUENCER
            current = OpportunityStatus.getNextStatus(current, true); // CONTENT_SEND_TO_ACCEPT
            current = OpportunityStatus.getNextStatus(current, false); // CONTENT_REJECTED

            // Influencer resigns
            current = OpportunityStatus.getNextStatus(current, false);
            assertThat(current).isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
            assertThat(current.isTerminalStatus()).isTrue();
            assertThat(current.isSuccessfulCompletion()).isFalse();
        }
    }
}
