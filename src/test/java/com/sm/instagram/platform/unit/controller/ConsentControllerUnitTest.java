package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.consent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConsentController and ConsentAdminController.
 * Pure Mockito tests without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Consent Controllers Unit Tests")
class ConsentControllerUnitTest {

    @Mock
    private ConsentService consentService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private ConsentController consentController;
    private ConsentAdminController consentAdminController;

    @BeforeEach
    void setUp() {
        consentController = new ConsentController(consentService);
        consentAdminController = new ConsentAdminController(consentService);
    }

    private void setupSecurityContext(String firebaseUid) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(firebaseUid);
        SecurityContextHolder.setContext(securityContext);
    }

    private void setupSecurityContextWithNullAuthentication() {
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);
    }

    private void setupSecurityContextWithNullPrincipal() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);
    }

    // ========== CONSENT CONTROLLER TESTS ==========

    @Nested
    @DisplayName("ConsentController - GET /consent/my")
    class GetMyConsentsTests {

        @Test
        @DisplayName("should return user consents when authenticated")
        void shouldReturnUserConsentsWhenAuthenticated() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserCurrentConsentDtoOut consent1 = createUserCurrentConsentDtoOut(1L, "MARKETING", true);
            UserCurrentConsentDtoOut consent2 = createUserCurrentConsentDtoOut(2L, "ANALYTICS", false);
            when(consentService.getMyAvailableConsents()).thenReturn(List.of(consent1, consent2));

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getConsentGiven()).isTrue();
            assertThat(response.getBody().get(1).getConsentGiven()).isFalse();
            verify(consentService).getMyAvailableConsents();
        }

        @Test
        @DisplayName("should return empty list when no consents available")
        void shouldReturnEmptyListWhenNoConsentsAvailable() {
            // Given
            setupSecurityContext("user-firebase-uid");
            when(consentService.getMyAvailableConsents()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();

            // When/Then
            assertThatThrownBy(() -> consentController.getMyConsents())
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when principal is null")
        void shouldThrowExceptionWhenPrincipalIsNull() {
            // Given
            setupSecurityContextWithNullPrincipal();

            // When/Then
            assertThatThrownBy(() -> consentController.getMyConsents())
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should return single consent when only one available")
        void shouldReturnSingleConsentWhenOnlyOneAvailable() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserCurrentConsentDtoOut consent = createUserCurrentConsentDtoOut(1L, "TERMS_OF_SERVICE", true);
            when(consentService.getMyAvailableConsents()).thenReturn(List.of(consent));

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should handle service exception gracefully")
        void shouldHandleServiceExceptionGracefully() {
            // Given
            setupSecurityContext("user-firebase-uid");
            when(consentService.getMyAvailableConsents())
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));

            // When/Then
            assertThatThrownBy(() -> consentController.getMyConsents())
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ConsentController - POST /consent/my")
    class RecordMyConsentTests {

        @Test
        @DisplayName("should record consent when valid data provided")
        void shouldRecordConsentWhenValidDataProvided() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = createUserConsentDtoIn("MARKETING", true);
            UserConsentDtoOut dtoOut = createUserConsentDtoOut(1L, true);
            when(consentService.recordMyConsent(any(UserConsentDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<UserConsentDtoOut> response = consentController.recordMyConsent(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getConsentGiven()).isTrue();
            verify(consentService).recordMyConsent(dtoIn);
        }

        @Test
        @DisplayName("should record consent withdrawal when consentGiven is false")
        void shouldRecordConsentWithdrawalWhenConsentGivenIsFalse() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = createUserConsentDtoIn("MARKETING", false);
            UserConsentDtoOut dtoOut = createUserConsentDtoOut(1L, false);
            when(consentService.recordMyConsent(any(UserConsentDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<UserConsentDtoOut> response = consentController.recordMyConsent(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getConsentGiven()).isFalse();
        }

        @Test
        @DisplayName("should throw ValidationException when dtoIn is null")
        void shouldThrowValidationExceptionWhenDtoInIsNull() {
            // Given
            setupSecurityContext("user-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is null")
        void shouldThrowValidationExceptionWhenConsentTypeIsNull() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType(null);
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is empty")
        void shouldThrowValidationExceptionWhenConsentTypeIsEmpty() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("");
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is whitespace only")
        void shouldThrowValidationExceptionWhenConsentTypeIsWhitespace() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("   ");
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentGiven is null")
        void shouldThrowValidationExceptionWhenConsentGivenIsNull() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("MARKETING");
            dtoIn.setConsentGiven(null);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType has invalid format - lowercase")
        void shouldThrowValidationExceptionWhenConsentTypeHasInvalidFormatLowercase() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("marketing");
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType has invalid format - numbers")
        void shouldThrowValidationExceptionWhenConsentTypeHasNumbers() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("MARKETING123");
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType has special characters")
        void shouldThrowValidationExceptionWhenConsentTypeHasSpecialChars() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("MARKETING-EMAILS");
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should accept valid consentType with underscores")
        void shouldAcceptValidConsentTypeWithUnderscores() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = createUserConsentDtoIn("MARKETING_EMAILS", true);
            UserConsentDtoOut dtoOut = createUserConsentDtoOut(1L, true);
            when(consentService.recordMyConsent(any(UserConsentDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<UserConsentDtoOut> response = consentController.recordMyConsent(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();
            UserConsentDtoIn dtoIn = createUserConsentDtoIn("MARKETING", true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should handle service exception during consent recording")
        void shouldHandleServiceExceptionDuringConsentRecording() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = createUserConsentDtoIn("UNKNOWN_TYPE", true);
            when(consentService.recordMyConsent(any(UserConsentDtoIn.class)))
                    .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Consent version"));

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ConsentController - GET /consent/my/history/{consentType}")
    class GetMyConsentHistoryTests {

        @Test
        @DisplayName("should return consent history for valid consentType")
        void shouldReturnConsentHistoryForValidConsentType() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoOut consent1 = createUserConsentDtoOut(1L, true);
            UserConsentDtoOut consent2 = createUserConsentDtoOut(2L, false);
            when(consentService.getMyConsentHistory("MARKETING")).thenReturn(List.of(consent1, consent2));

            // When
            ResponseEntity<List<UserConsentDtoOut>> response = consentController.getMyConsentHistory("MARKETING");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            verify(consentService).getMyConsentHistory("MARKETING");
        }

        @Test
        @DisplayName("should return empty list when no consent history exists")
        void shouldReturnEmptyListWhenNoHistoryExists() {
            // Given
            setupSecurityContext("user-firebase-uid");
            when(consentService.getMyConsentHistory("MARKETING")).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserConsentDtoOut>> response = consentController.getMyConsentHistory("MARKETING");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is null")
        void shouldThrowValidationExceptionWhenConsentTypeIsNull() {
            // Given
            setupSecurityContext("user-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentController.getMyConsentHistory(null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is empty")
        void shouldThrowValidationExceptionWhenConsentTypeIsEmpty() {
            // Given
            setupSecurityContext("user-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentController.getMyConsentHistory(""))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is whitespace")
        void shouldThrowValidationExceptionWhenConsentTypeIsWhitespace() {
            // Given
            setupSecurityContext("user-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentController.getMyConsentHistory("   "))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();

            // When/Then
            assertThatThrownBy(() -> consentController.getMyConsentHistory("MARKETING"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should return history ordered by date")
        void shouldReturnHistoryOrderedByDate() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoOut consent1 = createUserConsentDtoOut(1L, true);
            consent1.setCreatedAt(LocalDateTime.now().minusDays(10));
            UserConsentDtoOut consent2 = createUserConsentDtoOut(2L, false);
            consent2.setCreatedAt(LocalDateTime.now().minusDays(5));
            UserConsentDtoOut consent3 = createUserConsentDtoOut(3L, true);
            consent3.setCreatedAt(LocalDateTime.now());
            when(consentService.getMyConsentHistory("MARKETING")).thenReturn(List.of(consent1, consent2, consent3));

            // When
            ResponseEntity<List<UserConsentDtoOut>> response = consentController.getMyConsentHistory("MARKETING");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(3);
        }
    }

    // ========== CONSENT ADMIN CONTROLLER TESTS ==========

    @Nested
    @DisplayName("ConsentAdminController - GET /admin/consent/definitions")
    class GetAllConsentDefinitionsTests {

        @Test
        @DisplayName("should return all consent definitions for admin")
        void shouldReturnAllConsentDefinitionsForAdmin() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentDefinitionDtoOut def1 = createConsentDefinitionDtoOut(1L, "MARKETING");
            ConsentDefinitionDtoOut def2 = createConsentDefinitionDtoOut(2L, "ANALYTICS");
            when(consentService.getAllConsentDefinitions()).thenReturn(List.of(def1, def2));

            // When
            ResponseEntity<List<ConsentDefinitionDtoOut>> response = consentAdminController.getAllConsentDefinitions();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            verify(consentService).getAllConsentDefinitions();
        }

        @Test
        @DisplayName("should return empty list when no definitions exist")
        void shouldReturnEmptyListWhenNoDefinitionsExist() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            when(consentService.getAllConsentDefinitions()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<ConsentDefinitionDtoOut>> response = consentAdminController.getAllConsentDefinitions();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getAllConsentDefinitions())
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should return definitions ordered by consent type")
        void shouldReturnDefinitionsOrderedByConsentType() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentDefinitionDtoOut def1 = createConsentDefinitionDtoOut(1L, "ANALYTICS");
            ConsentDefinitionDtoOut def2 = createConsentDefinitionDtoOut(2L, "MARKETING");
            ConsentDefinitionDtoOut def3 = createConsentDefinitionDtoOut(3L, "TERMS_OF_SERVICE");
            when(consentService.getAllConsentDefinitions()).thenReturn(List.of(def1, def2, def3));

            // When
            ResponseEntity<List<ConsentDefinitionDtoOut>> response = consentAdminController.getAllConsentDefinitions();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("ConsentAdminController - POST /admin/consent/definitions")
    class CreateConsentDefinitionTests {

        @Test
        @DisplayName("should create consent definition when valid data provided")
        void shouldCreateConsentDefinitionWhenValidDataProvided() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentDefinitionDtoIn dtoIn = createConsentDefinitionDtoIn("MARKETING", "Marketing Consent");
            ConsentDefinitionDtoOut dtoOut = createConsentDefinitionDtoOut(1L, "MARKETING");
            when(consentService.createConsentDefinition(any(ConsentDefinitionDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<ConsentDefinitionDtoOut> response = consentAdminController.createConsentDefinition(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getConsentType()).isEqualTo("MARKETING");
            verify(consentService).createConsentDefinition(dtoIn);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();
            ConsentDefinitionDtoIn dtoIn = createConsentDefinitionDtoIn("MARKETING", "Marketing Consent");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.createConsentDefinition(dtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should create definition with all optional fields")
        void shouldCreateDefinitionWithAllOptionalFields() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentDefinitionDtoIn dtoIn = createConsentDefinitionDtoIn("MARKETING", "Marketing Consent");
            dtoIn.setDescription("Consent for marketing communications");
            dtoIn.setRegulationReference("GDPR Article 6");
            dtoIn.setIsActive(true);
            ConsentDefinitionDtoOut dtoOut = createConsentDefinitionDtoOut(1L, "MARKETING");
            dtoOut.setDescription("Consent for marketing communications");
            when(consentService.createConsentDefinition(any(ConsentDefinitionDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<ConsentDefinitionDtoOut> response = consentAdminController.createConsentDefinition(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getDescription()).isEqualTo("Consent for marketing communications");
        }

        @Test
        @DisplayName("should create inactive definition when isActive is false")
        void shouldCreateInactiveDefinitionWhenIsActiveFalse() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentDefinitionDtoIn dtoIn = createConsentDefinitionDtoIn("LEGACY_CONSENT", "Legacy Consent");
            dtoIn.setIsActive(false);
            ConsentDefinitionDtoOut dtoOut = createConsentDefinitionDtoOut(1L, "LEGACY_CONSENT");
            dtoOut.setIsActive(false);
            when(consentService.createConsentDefinition(any(ConsentDefinitionDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<ConsentDefinitionDtoOut> response = consentAdminController.createConsentDefinition(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("ConsentAdminController - POST /admin/consent/versions")
    class CreateConsentVersionTests {

        @Test
        @DisplayName("should create consent version when valid data provided")
        void shouldCreateConsentVersionWhenValidDataProvided() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentVersionDtoIn dtoIn = createConsentVersionDtoIn(1L, "1.0", "I agree to the terms");
            ConsentVersionDtoOut dtoOut = createConsentVersionDtoOut(1L, "1.0");
            when(consentService.createConsentVersion(any(ConsentVersionDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<ConsentVersionDtoOut> response = consentAdminController.createConsentVersion(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getVersion()).isEqualTo("1.0");
            verify(consentService).createConsentVersion(dtoIn);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();
            ConsentVersionDtoIn dtoIn = createConsentVersionDtoIn(1L, "1.0", "I agree to the terms");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.createConsentVersion(dtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should create version with policy URL")
        void shouldCreateVersionWithPolicyUrl() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentVersionDtoIn dtoIn = createConsentVersionDtoIn(1L, "2.0", "Updated consent text");
            dtoIn.setPolicyUrl("https://example.com/privacy-policy");
            ConsentVersionDtoOut dtoOut = createConsentVersionDtoOut(1L, "2.0");
            dtoOut.setPolicyUrl("https://example.com/privacy-policy");
            when(consentService.createConsentVersion(any(ConsentVersionDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<ConsentVersionDtoOut> response = consentAdminController.createConsentVersion(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getPolicyUrl()).isEqualTo("https://example.com/privacy-policy");
        }

        @Test
        @DisplayName("should create version with effective dates")
        void shouldCreateVersionWithEffectiveDates() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentVersionDtoIn dtoIn = createConsentVersionDtoIn(1L, "3.0", "New consent text");
            LocalDateTime effectiveFrom = LocalDateTime.now().plusDays(7);
            LocalDateTime effectiveUntil = LocalDateTime.now().plusYears(1);
            dtoIn.setEffectiveFrom(effectiveFrom);
            dtoIn.setEffectiveUntil(effectiveUntil);
            ConsentVersionDtoOut dtoOut = createConsentVersionDtoOut(1L, "3.0");
            dtoOut.setEffectiveFrom(effectiveFrom);
            dtoOut.setEffectiveUntil(effectiveUntil);
            when(consentService.createConsentVersion(any(ConsentVersionDtoIn.class))).thenReturn(dtoOut);

            // When
            ResponseEntity<ConsentVersionDtoOut> response = consentAdminController.createConsentVersion(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getEffectiveFrom()).isEqualTo(effectiveFrom);
            assertThat(response.getBody().getEffectiveUntil()).isEqualTo(effectiveUntil);
        }
    }

    @Nested
    @DisplayName("ConsentAdminController - GET /admin/consent/users/{userId}")
    class GetUserConsentsTests {

        @Test
        @DisplayName("should return user consents when userId is valid")
        void shouldReturnUserConsentsWhenUserIdIsValid() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            UserCurrentConsentDtoOut consent1 = createUserCurrentConsentDtoOut(100L, "MARKETING", true);
            UserCurrentConsentDtoOut consent2 = createUserCurrentConsentDtoOut(100L, "ANALYTICS", false);
            when(consentService.getUserCurrentConsentsForUser(100L)).thenReturn(List.of(consent1, consent2));

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentAdminController.getUserConsents(100L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            verify(consentService).getUserCurrentConsentsForUser(100L);
        }

        @Test
        @DisplayName("should return empty list when user has no consents")
        void shouldReturnEmptyListWhenUserHasNoConsents() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            when(consentService.getUserCurrentConsentsForUser(100L)).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentAdminController.getUserConsents(100L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ValidationException when userId is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsents(null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when userId is zero")
        void shouldThrowValidationExceptionWhenUserIdIsZero() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsents(0L))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when userId is negative")
        void shouldThrowValidationExceptionWhenUserIdIsNegative() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsents(-1L))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsents(100L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should handle large user IDs")
        void shouldHandleLargeUserIds() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            Long largeUserId = Long.MAX_VALUE - 1;
            when(consentService.getUserCurrentConsentsForUser(largeUserId)).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentAdminController.getUserConsents(largeUserId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("ConsentAdminController - GET /admin/consent/users/{userId}/history/{consentType}")
    class GetUserConsentHistoryTests {

        @Test
        @DisplayName("should return user consent history for valid params")
        void shouldReturnUserConsentHistoryForValidParams() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            UserConsentDtoOut consent1 = createUserConsentDtoOut(1L, true);
            UserConsentDtoOut consent2 = createUserConsentDtoOut(2L, false);
            when(consentService.getUserConsentHistoryForUser(100L, "MARKETING")).thenReturn(List.of(consent1, consent2));

            // When
            ResponseEntity<List<UserConsentDtoOut>> response = consentAdminController.getUserConsentHistory(100L, "MARKETING");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            verify(consentService).getUserConsentHistoryForUser(100L, "MARKETING");
        }

        @Test
        @DisplayName("should return empty list when no history exists")
        void shouldReturnEmptyListWhenNoHistoryExists() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            when(consentService.getUserConsentHistoryForUser(100L, "MARKETING")).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserConsentDtoOut>> response = consentAdminController.getUserConsentHistory(100L, "MARKETING");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ValidationException when userId is null")
        void shouldThrowValidationExceptionWhenUserIdIsNull() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(null, "MARKETING"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when userId is zero")
        void shouldThrowValidationExceptionWhenUserIdIsZero() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(0L, "MARKETING"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when userId is negative")
        void shouldThrowValidationExceptionWhenUserIdIsNegative() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(-5L, "MARKETING"))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is null")
        void shouldThrowValidationExceptionWhenConsentTypeIsNull() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(100L, null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is empty")
        void shouldThrowValidationExceptionWhenConsentTypeIsEmpty() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(100L, ""))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationException when consentType is whitespace")
        void shouldThrowValidationExceptionWhenConsentTypeIsWhitespace() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(100L, "   "))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            // Given
            setupSecurityContextWithNullAuthentication();

            // When/Then
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(100L, "MARKETING"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should validate userId before consentType")
        void shouldValidateUserIdBeforeConsentType() {
            // Given
            setupSecurityContext("admin-firebase-uid");

            // When/Then - userId validation should fail first
            assertThatThrownBy(() -> consentAdminController.getUserConsentHistory(-1L, null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ========== EDGE CASE TESTS ==========

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCaseTests {

        @Test
        @DisplayName("ConsentController should handle very long firebase UID")
        void shouldHandleVeryLongFirebaseUid() {
            // Given
            String longUid = "a".repeat(1000);
            setupSecurityContext(longUid);
            when(consentService.getMyAvailableConsents()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("ConsentAdminController should handle very large number of definitions")
        void shouldHandleVeryLargeNumberOfDefinitions() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            List<ConsentDefinitionDtoOut> manyDefinitions = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                manyDefinitions.add(createConsentDefinitionDtoOut((long) i, "TYPE_" + i));
            }
            when(consentService.getAllConsentDefinitions()).thenReturn(manyDefinitions);

            // When
            ResponseEntity<List<ConsentDefinitionDtoOut>> response = consentAdminController.getAllConsentDefinitions();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(100);
        }

        @Test
        @DisplayName("should handle special characters in firebase UID")
        void shouldHandleSpecialCharactersInFirebaseUid() {
            // Given
            setupSecurityContext("user+email@domain.com");
            when(consentService.getMyAvailableConsents()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle unicode in firebase UID")
        void shouldHandleUnicodeInFirebaseUid() {
            // Given
            setupSecurityContext("user_uid_\u00e9\u00e8\u00ea");
            when(consentService.getMyAvailableConsents()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Controller Construction Tests")
    class ConstructorTests {

        @Test
        @DisplayName("ConsentController should be instantiated with ConsentService")
        void consentControllerShouldBeInstantiatedWithConsentService() {
            // Given
            ConsentService mockService = mock(ConsentService.class);

            // When
            ConsentController controller = new ConsentController(mockService);

            // Then
            assertThat(controller).isNotNull();
        }

        @Test
        @DisplayName("ConsentAdminController should be instantiated with ConsentService")
        void consentAdminControllerShouldBeInstantiatedWithConsentService() {
            // Given
            ConsentService mockService = mock(ConsentService.class);

            // When
            ConsentAdminController controller = new ConsentAdminController(mockService);

            // Then
            assertThat(controller).isNotNull();
        }
    }

    @Nested
    @DisplayName("Concurrent Request Simulation")
    class ConcurrencyTests {

        @Test
        @DisplayName("should handle multiple sequential requests")
        void shouldHandleMultipleSequentialRequests() {
            // Given
            setupSecurityContext("user-firebase-uid");
            when(consentService.getMyAvailableConsents())
                    .thenReturn(List.of(createUserCurrentConsentDtoOut(1L, "MARKETING", true)));

            // When/Then
            for (int i = 0; i < 10; i++) {
                ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }

            verify(consentService, times(10)).getMyAvailableConsents();
        }

        @Test
        @DisplayName("should handle requests with different security contexts")
        void shouldHandleRequestsWithDifferentSecurityContexts() {
            // Given - First user
            setupSecurityContext("user1-firebase-uid");
            when(consentService.getMyAvailableConsents())
                    .thenReturn(List.of(createUserCurrentConsentDtoOut(1L, "MARKETING", true)));

            ResponseEntity<List<UserCurrentConsentDtoOut>> response1 = consentController.getMyConsents();
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Given - Second user
            setupSecurityContext("user2-firebase-uid");
            when(consentService.getMyAvailableConsents())
                    .thenReturn(List.of(createUserCurrentConsentDtoOut(2L, "ANALYTICS", false)));

            ResponseEntity<List<UserCurrentConsentDtoOut>> response2 = consentController.getMyConsents();
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Then
            verify(consentService, times(2)).getMyAvailableConsents();
        }
    }

    @Nested
    @DisplayName("Response Entity Structure Tests")
    class ResponseStructureTests {

        @Test
        @DisplayName("getMyConsents should return OK status")
        void getMyConsentsShouldReturnOkStatus() {
            // Given
            setupSecurityContext("user-firebase-uid");
            when(consentService.getMyAvailableConsents()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentController.getMyConsents();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getStatusCodeValue()).isEqualTo(200);
        }

        @Test
        @DisplayName("recordMyConsent should return OK status")
        void recordMyConsentShouldReturnOkStatus() {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = createUserConsentDtoIn("MARKETING", true);
            when(consentService.recordMyConsent(any())).thenReturn(createUserConsentDtoOut(1L, true));

            // When
            ResponseEntity<UserConsentDtoOut> response = consentController.recordMyConsent(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("getAllConsentDefinitions should return OK status")
        void getAllConsentDefinitionsShouldReturnOkStatus() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            when(consentService.getAllConsentDefinitions()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<ConsentDefinitionDtoOut>> response = consentAdminController.getAllConsentDefinitions();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("createConsentDefinition should return OK status")
        void createConsentDefinitionShouldReturnOkStatus() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentDefinitionDtoIn dtoIn = createConsentDefinitionDtoIn("TEST", "Test");
            when(consentService.createConsentDefinition(any())).thenReturn(createConsentDefinitionDtoOut(1L, "TEST"));

            // When
            ResponseEntity<ConsentDefinitionDtoOut> response = consentAdminController.createConsentDefinition(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("createConsentVersion should return OK status")
        void createConsentVersionShouldReturnOkStatus() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            ConsentVersionDtoIn dtoIn = createConsentVersionDtoIn(1L, "1.0", "Text");
            when(consentService.createConsentVersion(any())).thenReturn(createConsentVersionDtoOut(1L, "1.0"));

            // When
            ResponseEntity<ConsentVersionDtoOut> response = consentAdminController.createConsentVersion(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("getUserConsents should return OK status")
        void getUserConsentsShouldReturnOkStatus() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            when(consentService.getUserCurrentConsentsForUser(anyLong())).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserCurrentConsentDtoOut>> response = consentAdminController.getUserConsents(100L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("getUserConsentHistory should return OK status")
        void getUserConsentHistoryShouldReturnOkStatus() {
            // Given
            setupSecurityContext("admin-firebase-uid");
            when(consentService.getUserConsentHistoryForUser(anyLong(), anyString())).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<UserConsentDtoOut>> response = consentAdminController.getUserConsentHistory(100L, "MARKETING");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Consent Type Format Validation Tests")
    class ConsentTypeFormatValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {"MARKETING", "ANALYTICS", "TERMS_OF_SERVICE", "DATA_PROCESSING", "A", "ABC_DEF_GHI"})
        @DisplayName("should accept valid consent type formats")
        void shouldAcceptValidConsentTypeFormats(String consentType) {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = createUserConsentDtoIn(consentType, true);
            when(consentService.recordMyConsent(any())).thenReturn(createUserConsentDtoOut(1L, true));

            // When
            ResponseEntity<UserConsentDtoOut> response = consentController.recordMyConsent(dtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @ParameterizedTest
        @ValueSource(strings = {"marketing", "Marketing", "MARKETING123", "MARKETING-TYPE", "MARKETING TYPE", "MARKETING.TYPE"})
        @DisplayName("should reject invalid consent type formats")
        void shouldRejectInvalidConsentTypeFormats(String consentType) {
            // Given
            setupSecurityContext("user-firebase-uid");
            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType(consentType);
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> consentController.recordMyConsent(dtoIn))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ========== HELPER METHODS ==========

    private UserCurrentConsentDtoOut createUserCurrentConsentDtoOut(Long userId, String consentType, boolean consentGiven) {
        UserCurrentConsentDtoOut dto = new UserCurrentConsentDtoOut();
        dto.setUserId(userId);
        ConsentTypeDtoOut consentTypeDtoOut = new ConsentTypeDtoOut();
        consentTypeDtoOut.setValue(consentType);
        consentTypeDtoOut.setLabel(consentType);
        dto.setConsentType(consentTypeDtoOut);
        dto.setConsentGiven(consentGiven);
        dto.setCurrentVersion("1.0");
        dto.setLastUpdated(LocalDateTime.now());
        return dto;
    }

    private UserConsentDtoIn createUserConsentDtoIn(String consentType, Boolean consentGiven) {
        UserConsentDtoIn dto = new UserConsentDtoIn();
        dto.setConsentType(consentType);
        dto.setConsentGiven(consentGiven);
        dto.setCollectionMethod("web_form");
        return dto;
    }

    private UserConsentDtoOut createUserConsentDtoOut(Long id, boolean consentGiven) {
        UserConsentDtoOut dto = new UserConsentDtoOut();
        dto.setId(id);
        dto.setConsentGiven(consentGiven);
        dto.setCreatedAt(LocalDateTime.now());

        ConsentActionDtoOut actionDto = new ConsentActionDtoOut();
        actionDto.setValue(consentGiven ? "GRANTED" : "WITHDRAWN");
        dto.setAction(actionDto);

        return dto;
    }

    private ConsentDefinitionDtoIn createConsentDefinitionDtoIn(String consentType, String name) {
        ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
        dto.setConsentType(consentType);
        dto.setName(name);
        dto.setIsActive(true);
        return dto;
    }

    private ConsentDefinitionDtoOut createConsentDefinitionDtoOut(Long id, String consentType) {
        ConsentDefinitionDtoOut dto = new ConsentDefinitionDtoOut();
        dto.setId(id);
        dto.setConsentType(consentType);
        dto.setName(consentType + " Consent");
        dto.setIsActive(true);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }

    private ConsentVersionDtoIn createConsentVersionDtoIn(Long definitionId, String version, String consentText) {
        ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
        dto.setConsentDefinitionId(definitionId);
        dto.setVersion(version);
        dto.setConsentText(consentText);
        dto.setEffectiveFrom(LocalDateTime.now());
        return dto;
    }

    private ConsentVersionDtoOut createConsentVersionDtoOut(Long id, String version) {
        ConsentVersionDtoOut dto = new ConsentVersionDtoOut();
        dto.setId(id);
        dto.setVersion(version);
        dto.setConsentDefinitionId(1L);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }
}
