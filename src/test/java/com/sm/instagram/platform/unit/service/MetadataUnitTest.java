package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.metadata.EnrichableEnum;
import com.sm.instagram.platform.common.metadata.EnumMetadataController;
import com.sm.instagram.platform.common.metadata.StatusMetadata;
import com.sm.instagram.platform.consent.ConsentAction;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.user.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for common.metadata package classes.
 * Tests EnumMetadataController, StatusMetadata, and EnrichableEnum interface behavior.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Metadata Package Unit Tests")
class MetadataUnitTest {

    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private EnumMetadataController controller;

    @BeforeEach
    void setUp() {
        controller = new EnumMetadataController(dictionaryService);

        // Setup security context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("test-firebase-uid");
        SecurityContextHolder.setContext(securityContext);

        // Default dictionary service behavior
        when(dictionaryService.getTranslation(anyString(), anyString())).thenReturn(Optional.empty());
    }

    // ========================================================================
    // StatusMetadata Builder Tests
    // ========================================================================

    @Nested
    @DisplayName("StatusMetadata Builder Tests")
    class StatusMetadataBuilderTests {

        @Test
        @DisplayName("should build StatusMetadata with all fields")
        void shouldBuildStatusMetadataWithAllFields() {
            // Given/When
            StatusMetadata metadata = StatusMetadata.builder()
                    .value("TEST_STATUS")
                    .label("Test Label")
                    .description("Test Description")
                    .colorTheme("primary")
                    .icon("check-circle")
                    .aliases(List.of("alias1", "alias2"))
                    .possibleTransitions(List.of("NEXT_STATUS"))
                    .isTerminal(false)
                    .isSuccessful(true)
                    .canLogin(true)
                    .isActive(true)
                    .isPositiveAction(true)
                    .isNegativeAction(false)
                    .isModificationAction(false)
                    .build();

            // Then
            assertThat(metadata.getValue()).isEqualTo("TEST_STATUS");
            assertThat(metadata.getLabel()).isEqualTo("Test Label");
            assertThat(metadata.getDescription()).isEqualTo("Test Description");
            assertThat(metadata.getColorTheme()).isEqualTo("primary");
            assertThat(metadata.getIcon()).isEqualTo("check-circle");
            assertThat(metadata.getAliases()).containsExactly("alias1", "alias2");
            assertThat(metadata.getPossibleTransitions()).containsExactly("NEXT_STATUS");
            assertThat(metadata.getIsTerminal()).isFalse();
            assertThat(metadata.getIsSuccessful()).isTrue();
            assertThat(metadata.getCanLogin()).isTrue();
            assertThat(metadata.getIsActive()).isTrue();
            assertThat(metadata.getIsPositiveAction()).isTrue();
            assertThat(metadata.getIsNegativeAction()).isFalse();
            assertThat(metadata.getIsModificationAction()).isFalse();
        }

        @Test
        @DisplayName("should build StatusMetadata with minimal fields")
        void shouldBuildStatusMetadataWithMinimalFields() {
            // Given/When
            StatusMetadata metadata = StatusMetadata.builder()
                    .value("SIMPLE")
                    .build();

            // Then
            assertThat(metadata.getValue()).isEqualTo("SIMPLE");
            assertThat(metadata.getLabel()).isNull();
            assertThat(metadata.getDescription()).isNull();
            assertThat(metadata.getColorTheme()).isNull();
            assertThat(metadata.getIcon()).isNull();
            assertThat(metadata.getAliases()).isNull();
            assertThat(metadata.getPossibleTransitions()).isNull();
            assertThat(metadata.getIsTerminal()).isNull();
        }

        @Test
        @DisplayName("should build StatusMetadata with empty lists")
        void shouldBuildStatusMetadataWithEmptyLists() {
            // Given/When
            StatusMetadata metadata = StatusMetadata.builder()
                    .value("EMPTY_LISTS")
                    .aliases(List.of())
                    .possibleTransitions(List.of())
                    .build();

            // Then
            assertThat(metadata.getAliases()).isEmpty();
            assertThat(metadata.getPossibleTransitions()).isEmpty();
        }

        @Test
        @DisplayName("should build StatusMetadata for opportunity status context")
        void shouldBuildStatusMetadataForOpportunityStatusContext() {
            // Given/When
            StatusMetadata metadata = StatusMetadata.builder()
                    .value("APPLIED")
                    .label("Applied")
                    .description("Influencer has applied")
                    .colorTheme("primary")
                    .icon("user")
                    .isTerminal(false)
                    .isSuccessful(false)
                    .build();

            // Then
            assertThat(metadata.getValue()).isEqualTo("APPLIED");
            assertThat(metadata.getIsTerminal()).isFalse();
            assertThat(metadata.getIsSuccessful()).isFalse();
            assertThat(metadata.getCanLogin()).isNull(); // Not relevant for opportunity status
        }

        @Test
        @DisplayName("should build StatusMetadata for account status context")
        void shouldBuildStatusMetadataForAccountStatusContext() {
            // Given/When
            StatusMetadata metadata = StatusMetadata.builder()
                    .value("ACTIVE")
                    .label("Active")
                    .description("Account is active")
                    .colorTheme("success")
                    .icon("user-check")
                    .canLogin(true)
                    .isActive(true)
                    .isTerminal(false)
                    .build();

            // Then
            assertThat(metadata.getValue()).isEqualTo("ACTIVE");
            assertThat(metadata.getCanLogin()).isTrue();
            assertThat(metadata.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("should build StatusMetadata for consent action context")
        void shouldBuildStatusMetadataForConsentActionContext() {
            // Given/When
            StatusMetadata metadata = StatusMetadata.builder()
                    .value("GRANTED")
                    .label("Granted")
                    .description("Consent was granted")
                    .colorTheme("success")
                    .icon("check-circle")
                    .isPositiveAction(true)
                    .isNegativeAction(false)
                    .isModificationAction(false)
                    .build();

            // Then
            assertThat(metadata.getValue()).isEqualTo("GRANTED");
            assertThat(metadata.getIsPositiveAction()).isTrue();
            assertThat(metadata.getIsNegativeAction()).isFalse();
            assertThat(metadata.getIsModificationAction()).isFalse();
        }
    }

    // ========================================================================
    // OpportunityStatus Enum Tests
    // ========================================================================

    @Nested
    @DisplayName("OpportunityStatus Enum Tests")
    class OpportunityStatusEnumTests {

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should have non-null color theme for all statuses")
        void shouldHaveNonNullColorThemeForAllStatuses(OpportunityStatus status) {
            assertThat(status.getColorTheme()).isNotNull().isNotEmpty();
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should have non-null icon for all statuses")
        void shouldHaveNonNullIconForAllStatuses(OpportunityStatus status) {
            assertThat(status.getIcon()).isNotNull().isNotEmpty();
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should have non-null aliases for all statuses")
        void shouldHaveNonNullAliasesForAllStatuses(OpportunityStatus status) {
            assertThat(status.getAliases()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should have non-null description for all statuses")
        void shouldHaveNonNullDescriptionForAllStatuses(OpportunityStatus status) {
            assertThat(status.getDescription()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should identify terminal statuses correctly")
        void shouldIdentifyTerminalStatusesCorrectly() {
            assertThat(OpportunityStatus.REJECTED_BY_COMPANY.isTerminalStatus()).isTrue();
            assertThat(OpportunityStatus.REJECTED_BY_INFLUENCER.isTerminalStatus()).isTrue();
            assertThat(OpportunityStatus.DONE.isTerminalStatus()).isTrue();

            assertThat(OpportunityStatus.APPLIED.isTerminalStatus()).isFalse();
            assertThat(OpportunityStatus.CONTENT_APPROVED.isTerminalStatus()).isFalse();
        }

        @Test
        @DisplayName("should identify successful completion correctly")
        void shouldIdentifySuccessfulCompletionCorrectly() {
            assertThat(OpportunityStatus.DONE.isSuccessfulCompletion()).isTrue();

            assertThat(OpportunityStatus.REJECTED_BY_COMPANY.isSuccessfulCompletion()).isFalse();
            assertThat(OpportunityStatus.REJECTED_BY_INFLUENCER.isSuccessfulCompletion()).isFalse();
            assertThat(OpportunityStatus.APPLIED.isSuccessfulCompletion()).isFalse();
        }

        @Test
        @DisplayName("should get active statuses stream")
        void shouldGetActiveStatusesStream() {
            // When
            List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().toList();

            // Then
            assertThat(activeStatuses)
                    .doesNotContain(OpportunityStatus.REJECTED_BY_COMPANY)
                    .doesNotContain(OpportunityStatus.REJECTED_BY_INFLUENCER)
                    .doesNotContain(OpportunityStatus.DONE);

            assertThat(activeStatuses).contains(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("should get completed statuses stream")
        void shouldGetCompletedStatusesStream() {
            // When
            List<OpportunityStatus> completedStatuses = OpportunityStatus.getCompletedStatuses().toList();

            // Then
            assertThat(completedStatuses)
                    .contains(OpportunityStatus.REJECTED_BY_COMPANY)
                    .contains(OpportunityStatus.REJECTED_BY_INFLUENCER)
                    .contains(OpportunityStatus.DONE);

            assertThat(completedStatuses).doesNotContain(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("should get possible transitions from APPLIED")
        void shouldGetPossibleTransitionsFromApplied() {
            // When
            List<OpportunityStatus> transitions = OpportunityStatus.APPLIED.getPossibleTransitions();

            // Then
            assertThat(transitions)
                    .contains(OpportunityStatus.ACCEPTED_BY_COMPANY)
                    .contains(OpportunityStatus.REJECTED_BY_COMPANY);
        }

        @Test
        @DisplayName("should have empty transitions for terminal statuses")
        void shouldHaveEmptyTransitionsForTerminalStatuses() {
            assertThat(OpportunityStatus.DONE.getPossibleTransitions()).isEmpty();
            assertThat(OpportunityStatus.REJECTED_BY_COMPANY.getPossibleTransitions()).isEmpty();
            assertThat(OpportunityStatus.REJECTED_BY_INFLUENCER.getPossibleTransitions()).isEmpty();
        }

        @Test
        @DisplayName("should parse status from string")
        void shouldParseStatusFromString() {
            assertThat(OpportunityStatus.fromString("APPLIED")).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(OpportunityStatus.fromString("applied")).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(OpportunityStatus.fromString("Applied")).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("should get JSON value")
        void shouldGetJsonValue() {
            assertThat(OpportunityStatus.APPLIED.getValue()).isEqualTo("APPLIED");
            assertThat(OpportunityStatus.DONE.getValue()).isEqualTo("DONE");
        }

        @Test
        @DisplayName("should validate transitions correctly")
        void shouldValidateTransitionsCorrectly() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.ACCEPTED_BY_COMPANY)).isTrue();
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.DONE)).isFalse();

            assertThat(OpportunityStatus.CONTENT_APPROVED.canTransitionTo(OpportunityStatus.CONTENT_POSTED)).isTrue();
            assertThat(OpportunityStatus.CONTENT_APPROVED.canTransitionTo(OpportunityStatus.APPLIED)).isFalse();
        }

        @Test
        @DisplayName("should get label from dictionary service")
        void shouldGetLabelFromDictionaryService() {
            // Given
            when(dictionaryService.getTranslation("OPPORTUNITY_STATUS_APPLIED", "en"))
                    .thenReturn(Optional.of("Applied Status"));

            // When
            String label = OpportunityStatus.APPLIED.getLabel(dictionaryService, Locale.ENGLISH);

            // Then
            assertThat(label).isEqualTo("Applied Status");
        }

        @Test
        @DisplayName("should fall back to name when translation not found")
        void shouldFallBackToNameWhenTranslationNotFound() {
            // Given
            when(dictionaryService.getTranslation(anyString(), anyString())).thenReturn(Optional.empty());

            // When
            String label = OpportunityStatus.APPLIED.getLabel(dictionaryService, Locale.ENGLISH);

            // Then
            assertThat(label).isEqualTo("APPLIED");
        }

        @Test
        @DisplayName("should get description from dictionary service")
        void shouldGetDescriptionFromDictionaryService() {
            // Given
            when(dictionaryService.getTranslation("OPPORTUNITY_STATUS_APPLIED_DESC", "en"))
                    .thenReturn(Optional.of("Custom description"));

            // When
            String description = OpportunityStatus.APPLIED.getDescription(dictionaryService, Locale.ENGLISH);

            // Then
            assertThat(description).isEqualTo("Custom description");
        }

        @Test
        @DisplayName("should fall back to default description when translation not found")
        void shouldFallBackToDefaultDescriptionWhenTranslationNotFound() {
            // Given
            when(dictionaryService.getTranslation(anyString(), anyString())).thenReturn(Optional.empty());

            // When
            String description = OpportunityStatus.APPLIED.getDescription(dictionaryService, Locale.ENGLISH);

            // Then
            assertThat(description).isEqualTo(OpportunityStatus.APPLIED.getDescription());
        }
    }

    // ========================================================================
    // AccountStatus Enum Tests
    // ========================================================================

    @Nested
    @DisplayName("AccountStatus Enum Tests")
    class AccountStatusEnumTests {

        @ParameterizedTest
        @EnumSource(AccountStatus.class)
        @DisplayName("should have non-null color theme for all statuses")
        void shouldHaveNonNullColorThemeForAllStatuses(AccountStatus status) {
            assertThat(status.getColorTheme()).isNotNull().isNotEmpty();
        }

        @ParameterizedTest
        @EnumSource(AccountStatus.class)
        @DisplayName("should have non-null icon for all statuses")
        void shouldHaveNonNullIconForAllStatuses(AccountStatus status) {
            assertThat(status.getIcon()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should identify active status correctly")
        void shouldIdentifyActiveStatusCorrectly() {
            assertThat(AccountStatus.ACTIVE.isActive()).isTrue();

            assertThat(AccountStatus.INACTIVE.isActive()).isFalse();
            assertThat(AccountStatus.IN_VALIDATION.isActive()).isFalse();
            assertThat(AccountStatus.BANNED.isActive()).isFalse();
        }

        @Test
        @DisplayName("should identify login capability correctly")
        void shouldIdentifyLoginCapabilityCorrectly() {
            assertThat(AccountStatus.ACTIVE.canLogin()).isTrue();
            assertThat(AccountStatus.IN_VALIDATION.canLogin()).isTrue();

            assertThat(AccountStatus.INACTIVE.canLogin()).isFalse();
            assertThat(AccountStatus.BANNED.canLogin()).isFalse();
            assertThat(AccountStatus.DELETED.canLogin()).isFalse();
        }

        @Test
        @DisplayName("should identify terminal statuses correctly")
        void shouldIdentifyTerminalStatusesCorrectly() {
            assertThat(AccountStatus.DELETED.isTerminal()).isTrue();

            assertThat(AccountStatus.BANNED.isTerminal()).isFalse();
            assertThat(AccountStatus.ACTIVE.isTerminal()).isFalse();
            assertThat(AccountStatus.INACTIVE.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("should parse status from string")
        void shouldParseStatusFromString() {
            assertThat(AccountStatus.fromString("ACTIVE")).isEqualTo(AccountStatus.ACTIVE);
            assertThat(AccountStatus.fromString("active")).isEqualTo(AccountStatus.ACTIVE);
            assertThat(AccountStatus.fromString("Active")).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("should get JSON value")
        void shouldGetJsonValue() {
            assertThat(AccountStatus.ACTIVE.getValue()).isEqualTo("ACTIVE");
            assertThat(AccountStatus.BANNED.getValue()).isEqualTo("BANNED");
        }

        @Test
        @DisplayName("should validate transitions correctly")
        void shouldValidateTransitionsCorrectly() {
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.INACTIVE)).isTrue();
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.BANNED)).isTrue();
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.IN_VALIDATION)).isFalse();

            assertThat(AccountStatus.DELETED.canTransitionTo(AccountStatus.ACTIVE)).isFalse();
        }

        @Test
        @DisplayName("should get possible transitions from ACTIVE")
        void shouldGetPossibleTransitionsFromActive() {
            // When
            List<AccountStatus> transitions = AccountStatus.ACTIVE.getPossibleTransitions();

            // Then
            assertThat(transitions)
                    .contains(AccountStatus.INACTIVE)
                    .contains(AccountStatus.TO_BE_DELETED)
                    .contains(AccountStatus.BANNED);
        }

        @Test
        @DisplayName("should have no transitions from DELETED")
        void shouldHaveNoTransitionsFromDeleted() {
            assertThat(AccountStatus.DELETED.getPossibleTransitions()).isEmpty();
        }

        @Test
        @DisplayName("should get label from dictionary service")
        void shouldGetLabelFromDictionaryService() {
            // Given
            when(dictionaryService.getTranslation("ACCOUNT_STATUS_ACTIVE", "en"))
                    .thenReturn(Optional.of("Active Account"));

            // When
            String label = AccountStatus.ACTIVE.getLabel(dictionaryService, Locale.ENGLISH);

            // Then
            assertThat(label).isEqualTo("Active Account");
        }
    }

    // ========================================================================
    // ConsentAction Enum Tests
    // ========================================================================

    @Nested
    @DisplayName("ConsentAction Enum Tests")
    class ConsentActionEnumTests {

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("should have non-null color theme for all actions")
        void shouldHaveNonNullColorThemeForAllActions(ConsentAction action) {
            assertThat(action.getColorTheme()).isNotNull().isNotEmpty();
        }

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("should have non-null icon for all actions")
        void shouldHaveNonNullIconForAllActions(ConsentAction action) {
            assertThat(action.getIcon()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should identify positive action correctly")
        void shouldIdentifyPositiveActionCorrectly() {
            assertThat(ConsentAction.GRANTED.isPositiveAction()).isTrue();
            assertThat(ConsentAction.WITHDRAWN.isPositiveAction()).isFalse();
            assertThat(ConsentAction.UPDATED.isPositiveAction()).isFalse();
        }

        @Test
        @DisplayName("should identify negative action correctly")
        void shouldIdentifyNegativeActionCorrectly() {
            assertThat(ConsentAction.WITHDRAWN.isNegativeAction()).isTrue();
            assertThat(ConsentAction.GRANTED.isNegativeAction()).isFalse();
            assertThat(ConsentAction.UPDATED.isNegativeAction()).isFalse();
        }

        @Test
        @DisplayName("should identify modification action correctly")
        void shouldIdentifyModificationActionCorrectly() {
            assertThat(ConsentAction.UPDATED.isModificationAction()).isTrue();
            assertThat(ConsentAction.GRANTED.isModificationAction()).isFalse();
            assertThat(ConsentAction.WITHDRAWN.isModificationAction()).isFalse();
        }

        @Test
        @DisplayName("should parse action from string")
        void shouldParseActionFromString() {
            assertThat(ConsentAction.fromString("GRANTED")).isEqualTo(ConsentAction.GRANTED);
            assertThat(ConsentAction.fromString("granted")).isEqualTo(ConsentAction.GRANTED);
            assertThat(ConsentAction.fromString("Granted")).isEqualTo(ConsentAction.GRANTED);
        }

        @Test
        @DisplayName("should get JSON value")
        void shouldGetJsonValue() {
            assertThat(ConsentAction.GRANTED.getValue()).isEqualTo("GRANTED");
            assertThat(ConsentAction.WITHDRAWN.getValue()).isEqualTo("WITHDRAWN");
        }

        @Test
        @DisplayName("should get label from dictionary service")
        void shouldGetLabelFromDictionaryService() {
            // Given
            when(dictionaryService.getTranslation("CONSENT_ACTION_GRANTED", "en"))
                    .thenReturn(Optional.of("Consent Granted"));

            // When
            String label = ConsentAction.GRANTED.getLabel(dictionaryService, Locale.ENGLISH);

            // Then
            assertThat(label).isEqualTo("Consent Granted");
        }

        @Test
        @DisplayName("should have aliases for all actions")
        void shouldHaveAliasesForAllActions() {
            assertThat(ConsentAction.GRANTED.getAliases()).isNotEmpty();
            assertThat(ConsentAction.WITHDRAWN.getAliases()).isNotEmpty();
            assertThat(ConsentAction.UPDATED.getAliases()).isNotEmpty();
        }
    }

    // ========================================================================
    // EnumMetadataController Tests
    // ========================================================================

    @Nested
    @DisplayName("EnumMetadataController - getOpportunityStatuses")
    class GetOpportunityStatusesTests {

        @Test
        @DisplayName("should return all opportunity statuses")
        void shouldReturnAllOpportunityStatuses() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getOpportunityStatuses("en");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(OpportunityStatus.values().length);
        }

        @Test
        @DisplayName("should return metadata with correct structure for each status")
        void shouldReturnMetadataWithCorrectStructure() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getOpportunityStatuses("en");

            // Then
            StatusMetadata appliedMetadata = response.getBody().stream()
                    .filter(m -> "APPLIED".equals(m.getValue()))
                    .findFirst()
                    .orElseThrow();

            assertThat(appliedMetadata.getColorTheme()).isEqualTo("primary");
            assertThat(appliedMetadata.getIcon()).isEqualTo("user");
            assertThat(appliedMetadata.getAliases()).contains("waiting");
            assertThat(appliedMetadata.getIsTerminal()).isFalse();
            assertThat(appliedMetadata.getIsSuccessful()).isFalse();
        }

        @Test
        @DisplayName("should include possible transitions for each status")
        void shouldIncludePossibleTransitionsForEachStatus() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getOpportunityStatuses("en");

            // Then
            StatusMetadata appliedMetadata = response.getBody().stream()
                    .filter(m -> "APPLIED".equals(m.getValue()))
                    .findFirst()
                    .orElseThrow();

            assertThat(appliedMetadata.getPossibleTransitions())
                    .contains("ACCEPTED_BY_COMPANY", "REJECTED_BY_COMPANY");
        }

        @ParameterizedTest
        @ValueSource(strings = {"en", "pl", "de", "fr"})
        @DisplayName("should accept different language parameters")
        void shouldAcceptDifferentLanguageParameters(String lang) {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getOpportunityStatuses(lang);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotEmpty();
        }

        @Test
        @DisplayName("should use default language when not specified")
        void shouldUseDefaultLanguageWhenNotSpecified() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getOpportunityStatuses("en");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("EnumMetadataController - getAccountStatuses")
    class GetAccountStatusesTests {

        @Test
        @DisplayName("should return all account statuses")
        void shouldReturnAllAccountStatuses() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getAccountStatuses("en");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(AccountStatus.values().length);
        }

        @Test
        @DisplayName("should include canLogin and isActive for account statuses")
        void shouldIncludeCanLoginAndIsActive() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getAccountStatuses("en");

            // Then
            StatusMetadata activeMetadata = response.getBody().stream()
                    .filter(m -> "ACTIVE".equals(m.getValue()))
                    .findFirst()
                    .orElseThrow();

            assertThat(activeMetadata.getCanLogin()).isTrue();
            assertThat(activeMetadata.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("should mark terminal statuses correctly")
        void shouldMarkTerminalStatusesCorrectly() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getAccountStatuses("en");

            // Then
            StatusMetadata deletedMetadata = response.getBody().stream()
                    .filter(m -> "DELETED".equals(m.getValue()))
                    .findFirst()
                    .orElseThrow();

            assertThat(deletedMetadata.getIsTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("EnumMetadataController - getConsentActions")
    class GetConsentActionsTests {

        @Test
        @DisplayName("should return all consent actions")
        void shouldReturnAllConsentActions() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getConsentActions("en");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(ConsentAction.values().length);
        }

        @Test
        @DisplayName("should include action type flags")
        void shouldIncludeActionTypeFlags() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getConsentActions("en");

            // Then
            StatusMetadata grantedMetadata = response.getBody().stream()
                    .filter(m -> "GRANTED".equals(m.getValue()))
                    .findFirst()
                    .orElseThrow();

            assertThat(grantedMetadata.getIsPositiveAction()).isTrue();
            assertThat(grantedMetadata.getIsNegativeAction()).isFalse();
            assertThat(grantedMetadata.getIsModificationAction()).isFalse();
        }

        @Test
        @DisplayName("should mark WITHDRAWN as negative action")
        void shouldMarkWithdrawnAsNegativeAction() {
            // When
            ResponseEntity<List<StatusMetadata>> response = controller.getConsentActions("en");

            // Then
            StatusMetadata withdrawnMetadata = response.getBody().stream()
                    .filter(m -> "WITHDRAWN".equals(m.getValue()))
                    .findFirst()
                    .orElseThrow();

            assertThat(withdrawnMetadata.getIsNegativeAction()).isTrue();
            assertThat(withdrawnMetadata.getIsPositiveAction()).isFalse();
        }
    }

    @Nested
    @DisplayName("EnumMetadataController - getOpportunityStatusTransitions")
    class GetOpportunityStatusTransitionsTests {

        @Test
        @DisplayName("should return transitions for valid status")
        void shouldReturnTransitionsForValidStatus() {
            // When
            ResponseEntity<List<String>> response = controller.getOpportunityStatusTransitions("APPLIED");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .contains("ACCEPTED_BY_COMPANY", "REJECTED_BY_COMPANY");
        }

        @Test
        @DisplayName("should handle lowercase status")
        void shouldHandleLowercaseStatus() {
            // When
            ResponseEntity<List<String>> response = controller.getOpportunityStatusTransitions("applied");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotEmpty();
        }

        @Test
        @DisplayName("should return empty list for terminal status")
        void shouldReturnEmptyListForTerminalStatus() {
            // When
            ResponseEntity<List<String>> response = controller.getOpportunityStatusTransitions("DONE");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid status")
        void shouldThrowValidationExceptionForInvalidStatus() {
            // When/Then
            assertThatThrownBy(() -> controller.getOpportunityStatusTransitions("INVALID_STATUS"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("EnumMetadataController - getAccountStatusTransitions")
    class GetAccountStatusTransitionsTests {

        @Test
        @DisplayName("should return transitions for valid status")
        void shouldReturnTransitionsForValidStatus() {
            // When
            ResponseEntity<List<String>> response = controller.getAccountStatusTransitions("ACTIVE");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .contains("INACTIVE", "TO_BE_DELETED", "BANNED");
        }

        @Test
        @DisplayName("should return empty list for DELETED status")
        void shouldReturnEmptyListForDeletedStatus() {
            // When
            ResponseEntity<List<String>> response = controller.getAccountStatusTransitions("DELETED");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid status")
        void shouldThrowValidationExceptionForInvalidStatus() {
            // When/Then
            assertThatThrownBy(() -> controller.getAccountStatusTransitions("NOT_A_STATUS"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("EnumMetadataController - getActiveOpportunityStatuses")
    class GetActiveOpportunityStatusesTests {

        @Test
        @DisplayName("should return active opportunity statuses")
        void shouldReturnActiveOpportunityStatuses() {
            // When
            ResponseEntity<List<String>> response = controller.getActiveOpportunityStatuses();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .contains("APPLIED", "ACCEPTED_BY_COMPANY")
                    .doesNotContain("DONE", "REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER");
        }

        @Test
        @DisplayName("should not include terminal statuses in active list")
        void shouldNotIncludeTerminalStatusesInActiveList() {
            // When
            ResponseEntity<List<String>> response = controller.getActiveOpportunityStatuses();

            // Then
            assertThat(response.getBody())
                    .doesNotContain("DONE")
                    .doesNotContain("REJECTED_BY_COMPANY")
                    .doesNotContain("REJECTED_BY_INFLUENCER");
        }
    }

    @Nested
    @DisplayName("EnumMetadataController - getCompletedOpportunityStatuses")
    class GetCompletedOpportunityStatusesTests {

        @Test
        @DisplayName("should return completed opportunity statuses")
        void shouldReturnCompletedOpportunityStatuses() {
            // When
            ResponseEntity<List<String>> response = controller.getCompletedOpportunityStatuses();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .contains("DONE", "REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER");
        }

        @Test
        @DisplayName("should not include active statuses in completed list")
        void shouldNotIncludeActiveStatusesInCompletedList() {
            // When
            ResponseEntity<List<String>> response = controller.getCompletedOpportunityStatuses();

            // Then
            assertThat(response.getBody())
                    .doesNotContain("APPLIED")
                    .doesNotContain("CONTENT_APPROVED");
        }
    }

    @Nested
    @DisplayName("EnumMetadataController - Authentication")
    class AuthenticationTests {

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when no authentication")
        void shouldThrowAuthenticationExceptionWhenNoAuthentication() {
            // Given
            SecurityContextHolder.clearContext();
            SecurityContext emptyContext = mock(SecurityContext.class);
            when(emptyContext.getAuthentication()).thenReturn(null);
            SecurityContextHolder.setContext(emptyContext);

            // When/Then
            assertThatThrownBy(() -> controller.getOpportunityStatuses("en"))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw AuthenticationTranslatableException when authentication has no principal")
        void shouldThrowAuthenticationExceptionWhenNoPrincipal() {
            // Given
            Authentication authWithNoPrincipal = mock(Authentication.class);
            when(authWithNoPrincipal.getPrincipal()).thenReturn(null);
            when(securityContext.getAuthentication()).thenReturn(authWithNoPrincipal);

            // When/Then
            assertThatThrownBy(() -> controller.getAccountStatuses("en"))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }
    }

    // ========================================================================
    // EnrichableEnum Interface Tests
    // ========================================================================

    @Nested
    @DisplayName("EnrichableEnum Interface Default Methods")
    class EnrichableEnumDefaultMethodsTests {

        // Create a test implementation of EnrichableEnum
        private static class TestEnrichableEnum implements EnrichableEnum {
            @Override
            public String name() {
                return "TEST";
            }

            @Override
            public String getLabel(MessageSource messageSource, Locale locale) {
                return "Test Label";
            }

            @Override
            public String getDescription(MessageSource messageSource, Locale locale) {
                return "Test Description";
            }

            @Override
            public String getColorTheme() {
                return "primary";
            }

            @Override
            public String getIcon() {
                return "test-icon";
            }

            @Override
            public List<String> getAliases() {
                return List.of("alias1");
            }
        }

        @Test
        @DisplayName("should return false for canTransitionTo by default")
        void shouldReturnFalseForCanTransitionToByDefault() {
            // Given
            TestEnrichableEnum testEnum = new TestEnrichableEnum();
            TestEnrichableEnum otherEnum = new TestEnrichableEnum();

            // When/Then
            assertThat(testEnum.canTransitionTo(otherEnum)).isFalse();
        }

        @Test
        @DisplayName("should return empty list for getPossibleTransitions by default")
        void shouldReturnEmptyListForGetPossibleTransitionsByDefault() {
            // Given
            TestEnrichableEnum testEnum = new TestEnrichableEnum();

            // When/Then
            assertThat(testEnum.getPossibleTransitions()).isEmpty();
        }

        @Test
        @DisplayName("should return true for isTerminal by default when no transitions")
        void shouldReturnTrueForIsTerminalByDefault() {
            // Given
            TestEnrichableEnum testEnum = new TestEnrichableEnum();

            // When/Then
            assertThat(testEnum.isTerminal()).isTrue();
        }
    }

    // ========================================================================
    // OpportunityStatus getNextStatus Tests
    // ========================================================================

    @Nested
    @DisplayName("OpportunityStatus getNextStatus Tests")
    class OpportunityStatusGetNextStatusTests {

        @Test
        @DisplayName("should transition APPLIED to ACCEPTED_BY_COMPANY when accept=true")
        void shouldTransitionAppliedToAcceptedByCompany() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, true))
                    .isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        }

        @Test
        @DisplayName("should transition APPLIED to REJECTED_BY_COMPANY when accept=false")
        void shouldTransitionAppliedToRejectedByCompany() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, false))
                    .isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
        }

        @Test
        @DisplayName("should transition ACCEPTED_BY_COMPANY to ACCEPTED_BY_INFLUENCER when accept=true")
        void shouldTransitionAcceptedByCompanyToAcceptedByInfluencer() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_COMPANY, true))
                    .isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("should transition CONTENT_SEND_TO_ACCEPT to CONTENT_APPROVED when accept=true")
        void shouldTransitionContentSendToAcceptToContentApproved() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, true))
                    .isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }

        @Test
        @DisplayName("should transition CONTENT_SEND_TO_ACCEPT to CONTENT_REJECTED when accept=false")
        void shouldTransitionContentSendToAcceptToContentRejected() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, false))
                    .isEqualTo(OpportunityStatus.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("should transition TO_BE_PAID to DONE when accept=true")
        void shouldTransitionToBePaidToDone() {
            assertThat(OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, true))
                    .isEqualTo(OpportunityStatus.DONE);
        }

        @Test
        @DisplayName("should throw exception when rejecting at TO_BE_PAID stage")
        void shouldThrowExceptionWhenRejectingAtToBePaidStage() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot reject");
        }

        @Test
        @DisplayName("should throw exception from terminal status")
        void shouldThrowExceptionFromTerminalStatus() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.DONE, true))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ========================================================================
    // Integration-like tests for StatusMetadata with real enum values
    // ========================================================================

    @Nested
    @DisplayName("StatusMetadata Integration Tests")
    class StatusMetadataIntegrationTests {

        @Test
        @DisplayName("should create valid metadata for all OpportunityStatus values")
        void shouldCreateValidMetadataForAllOpportunityStatusValues() {
            for (OpportunityStatus status : OpportunityStatus.values()) {
                StatusMetadata metadata = StatusMetadata.builder()
                        .value(status.name())
                        .label(status.getLabel(dictionaryService, Locale.ENGLISH))
                        .description(status.getDescription(dictionaryService, Locale.ENGLISH))
                        .colorTheme(status.getColorTheme())
                        .icon(status.getIcon())
                        .aliases(status.getAliases())
                        .possibleTransitions(status.getPossibleTransitions().stream()
                                .map(Enum::name)
                                .toList())
                        .isTerminal(status.isTerminalStatus())
                        .isSuccessful(status.isSuccessfulCompletion())
                        .build();

                assertThat(metadata.getValue()).isEqualTo(status.name());
                assertThat(metadata.getColorTheme()).isNotNull();
                assertThat(metadata.getIcon()).isNotNull();
            }
        }

        @Test
        @DisplayName("should create valid metadata for all AccountStatus values")
        void shouldCreateValidMetadataForAllAccountStatusValues() {
            for (AccountStatus status : AccountStatus.values()) {
                StatusMetadata metadata = StatusMetadata.builder()
                        .value(status.name())
                        .label(status.getLabel(dictionaryService, Locale.ENGLISH))
                        .description(status.getDescription(dictionaryService, Locale.ENGLISH))
                        .colorTheme(status.getColorTheme())
                        .icon(status.getIcon())
                        .aliases(status.getAliases())
                        .canLogin(status.canLogin())
                        .isActive(status.isActive())
                        .isTerminal(status.isTerminal())
                        .build();

                assertThat(metadata.getValue()).isEqualTo(status.name());
                assertThat(metadata.getColorTheme()).isNotNull();
                assertThat(metadata.getIcon()).isNotNull();
            }
        }

        @Test
        @DisplayName("should create valid metadata for all ConsentAction values")
        void shouldCreateValidMetadataForAllConsentActionValues() {
            for (ConsentAction action : ConsentAction.values()) {
                StatusMetadata metadata = StatusMetadata.builder()
                        .value(action.name())
                        .label(action.getLabel(dictionaryService, Locale.ENGLISH))
                        .description(action.getDescription(dictionaryService, Locale.ENGLISH))
                        .colorTheme(action.getColorTheme())
                        .icon(action.getIcon())
                        .aliases(action.getAliases())
                        .isPositiveAction(action.isPositiveAction())
                        .isNegativeAction(action.isNegativeAction())
                        .isModificationAction(action.isModificationAction())
                        .build();

                assertThat(metadata.getValue()).isEqualTo(action.name());
                assertThat(metadata.getColorTheme()).isNotNull();
                assertThat(metadata.getIcon()).isNotNull();
            }
        }
    }
}
