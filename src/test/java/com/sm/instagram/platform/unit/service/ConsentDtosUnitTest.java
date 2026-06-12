package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.consent.*;
import com.sm.instagram.platform.dictionary.DictionaryService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Consent DTOs and Entities.
 * Tests validation, builders, getters/setters, equals/hashCode, and toString.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Consent DTOs Unit Tests")
class ConsentDtosUnitTest {

    private Validator validator;

    @Mock
    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ConsentDefinition Entity Tests ====================

    @Nested
    @DisplayName("ConsentDefinition Entity Tests")
    class ConsentDefinitionEntityTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            ConsentDefinition entity = new ConsentDefinition();

            assertThat(entity.getId()).isNull();
            assertThat(entity.getConsentType()).isNull();
            assertThat(entity.getName()).isNull();
            assertThat(entity.getDescription()).isNull();
            assertThat(entity.getRegulationReference()).isNull();
            assertThat(entity.getIsActive()).isTrue(); // Default value is true
            assertThat(entity.getVersions()).isNotNull().isEmpty();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
            assertThat(entity.getUpdaterId()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            List<ConsentVersion> versions = new ArrayList<>();

            ConsentDefinition entity = new ConsentDefinition(
                    1L, "marketing", "Marketing Consent", "Description",
                    "GDPR Art. 6(1)(a)", true, 1, versions, now, now, "admin"
            );

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getConsentType()).isEqualTo("marketing");
            assertThat(entity.getName()).isEqualTo("Marketing Consent");
            assertThat(entity.getDescription()).isEqualTo("Description");
            assertThat(entity.getRegulationReference()).isEqualTo("GDPR Art. 6(1)(a)");
            assertThat(entity.getIsActive()).isTrue();
            assertThat(entity.getVersions()).isSameAs(versions);
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getUpdatedAt()).isEqualTo(now);
            assertThat(entity.getUpdaterId()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            ConsentDefinition entity = new ConsentDefinition();
            LocalDateTime now = LocalDateTime.now();
            List<ConsentVersion> versions = new ArrayList<>();

            entity.setId(1L);
            entity.setConsentType("analytics");
            entity.setName("Analytics Consent");
            entity.setDescription("Consent for analytics");
            entity.setRegulationReference("GDPR Art. 6(1)(a)");
            entity.setIsActive(false);
            entity.setVersions(versions);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setUpdaterId("system");

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getConsentType()).isEqualTo("analytics");
            assertThat(entity.getName()).isEqualTo("Analytics Consent");
            assertThat(entity.getDescription()).isEqualTo("Consent for analytics");
            assertThat(entity.getRegulationReference()).isEqualTo("GDPR Art. 6(1)(a)");
            assertThat(entity.getIsActive()).isFalse();
            assertThat(entity.getVersions()).isSameAs(versions);
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getUpdatedAt()).isEqualTo(now);
            assertThat(entity.getUpdaterId()).isEqualTo("system");
        }

        @Test
        @DisplayName("should pass validation with valid fields")
        void shouldPassValidationWithValidFields() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when consentType is blank")
        void shouldFailValidationWhenConsentTypeIsBlank() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("");
            entity.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail validation when consentType is null")
        void shouldFailValidationWhenConsentTypeIsNull() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType(null);
            entity.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail validation when consentType exceeds max length")
        void shouldFailValidationWhenConsentTypeExceedsMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("A".repeat(101));
            entity.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should accept consentType at max length")
        void shouldAcceptConsentTypeAtMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("A".repeat(100));
            entity.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations.stream().filter(v -> v.getPropertyPath().toString().equals("consentType"))).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when name is blank")
        void shouldFailValidationWhenNameIsBlank() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("");

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should fail validation when name exceeds max length")
        void shouldFailValidationWhenNameExceedsMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("A".repeat(256));

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should accept name at max length")
        void shouldAcceptNameAtMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("A".repeat(255));

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations.stream().filter(v -> v.getPropertyPath().toString().equals("name"))).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when description exceeds max length")
        void shouldFailValidationWhenDescriptionExceedsMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("Marketing");
            entity.setDescription("A".repeat(1001));

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
        }

        @Test
        @DisplayName("should accept description at max length")
        void shouldAcceptDescriptionAtMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("Marketing");
            entity.setDescription("A".repeat(1000));

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations.stream().filter(v -> v.getPropertyPath().toString().equals("description"))).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when regulationReference exceeds max length")
        void shouldFailValidationWhenRegulationReferenceExceedsMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("Marketing");
            entity.setRegulationReference("A".repeat(101));

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("regulationReference"));
        }

        @Test
        @DisplayName("should fail validation when updaterId exceeds max length")
        void shouldFailValidationWhenUpdaterIdExceedsMaxLength() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setConsentType("marketing");
            entity.setName("Marketing");
            entity.setUpdaterId("A".repeat(256));

            Set<ConstraintViolation<ConsentDefinition>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("updaterId"));
        }

        @Test
        @DisplayName("should implement UpdaterTracking interface")
        void shouldImplementUpdaterTrackingInterface() {
            ConsentDefinition entity = new ConsentDefinition();
            entity.setUpdaterId("user@example.com");

            assertThat(entity.getUpdaterId()).isEqualTo("user@example.com");
        }
    }

    // ==================== ConsentDefinitionDtoIn Tests ====================

    @Nested
    @DisplayName("ConsentDefinitionDtoIn Tests")
    class ConsentDefinitionDtoInTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();

            assertThat(dto.getConsentType()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getRegulationReference()).isNull();
            assertThat(dto.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("marketing");
            dto.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields populated")
        void shouldPassValidationWithAllFields() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("analytics");
            dto.setName("Analytics Consent");
            dto.setDescription("Consent for analytics cookies");
            dto.setRegulationReference("GDPR Art. 6(1)(a)");
            dto.setIsActive(false);

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when consentType is blank")
        void shouldFailWhenConsentTypeIsBlank() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("");
            dto.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail when consentType is null")
        void shouldFailWhenConsentTypeIsNull() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType(null);
            dto.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail when consentType exceeds max length")
        void shouldFailWhenConsentTypeExceedsMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("A".repeat(101));
            dto.setName("Marketing Consent");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail when name is blank")
        void shouldFailWhenNameIsBlank() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("marketing");
            dto.setName("");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should fail when name exceeds max length")
        void shouldFailWhenNameExceedsMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("marketing");
            dto.setName("A".repeat(256));

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should accept name at max length")
        void shouldAcceptNameAtMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("marketing");
            dto.setName("A".repeat(255));

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations.stream().filter(v -> v.getPropertyPath().toString().equals("name"))).isEmpty();
        }

        @Test
        @DisplayName("should fail when description exceeds max length")
        void shouldFailWhenDescriptionExceedsMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("marketing");
            dto.setName("Marketing");
            dto.setDescription("A".repeat(1001));

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
        }

        @Test
        @DisplayName("should fail when regulationReference exceeds max length")
        void shouldFailWhenRegulationReferenceExceedsMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("marketing");
            dto.setName("Marketing");
            dto.setRegulationReference("A".repeat(101));

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("regulationReference"));
        }

        @Test
        @DisplayName("should accept null optional fields")
        void shouldAcceptNullOptionalFields() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("marketing");
            dto.setName("Marketing");
            dto.setDescription(null);
            dto.setRegulationReference(null);

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== ConsentDefinitionDtoOut Tests ====================

    @Nested
    @DisplayName("ConsentDefinitionDtoOut Tests")
    class ConsentDefinitionDtoOutTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            ConsentDefinitionDtoOut dto = new ConsentDefinitionDtoOut();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getConsentType()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getRegulationReference()).isNull();
            assertThat(dto.getIsActive()).isNull();
            assertThat(dto.getVersions()).isNull();
            assertThat(dto.getCreatedAt()).isNull();
            assertThat(dto.getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            ConsentDefinitionDtoOut dto = new ConsentDefinitionDtoOut();
            LocalDateTime now = LocalDateTime.now();
            List<ConsentVersionDtoOut> versions = new ArrayList<>();

            dto.setId(1L);
            dto.setConsentType("marketing");
            dto.setName("Marketing Consent");
            dto.setDescription("Description");
            dto.setRegulationReference("GDPR");
            dto.setIsActive(true);
            dto.setVersions(versions);
            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getConsentType()).isEqualTo("marketing");
            assertThat(dto.getName()).isEqualTo("Marketing Consent");
            assertThat(dto.getDescription()).isEqualTo("Description");
            assertThat(dto.getRegulationReference()).isEqualTo("GDPR");
            assertThat(dto.getIsActive()).isTrue();
            assertThat(dto.getVersions()).isSameAs(versions);
            assertThat(dto.getCreatedAt()).isEqualTo(now);
            assertThat(dto.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should handle versions list")
        void shouldHandleVersionsList() {
            ConsentDefinitionDtoOut dto = new ConsentDefinitionDtoOut();
            ConsentVersionDtoOut version1 = new ConsentVersionDtoOut();
            version1.setId(1L);
            version1.setVersion("1.0");
            ConsentVersionDtoOut version2 = new ConsentVersionDtoOut();
            version2.setId(2L);
            version2.setVersion("2.0");

            dto.setVersions(List.of(version1, version2));

            assertThat(dto.getVersions()).hasSize(2);
            assertThat(dto.getVersions().get(0).getVersion()).isEqualTo("1.0");
            assertThat(dto.getVersions().get(1).getVersion()).isEqualTo("2.0");
        }
    }

    // ==================== ConsentVersion Entity Tests ====================

    @Nested
    @DisplayName("ConsentVersion Entity Tests")
    class ConsentVersionEntityTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            ConsentVersion entity = new ConsentVersion();

            assertThat(entity.getId()).isNull();
            assertThat(entity.getConsentDefinition()).isNull();
            assertThat(entity.getVersion()).isNull();
            assertThat(entity.getConsentText()).isNull();
            assertThat(entity.getPolicyUrl()).isNull();
            assertThat(entity.getEffectiveFrom()).isNull();
            assertThat(entity.getEffectiveUntil()).isNull();
            assertThat(entity.getUserConsents()).isNotNull().isEmpty();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdaterId()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            ConsentDefinition definition = new ConsentDefinition();
            List<UserConsent> userConsents = new ArrayList<>();

            ConsentVersion entity = new ConsentVersion(
                    1L, definition, "1.0", "Consent text",
                    "https://example.com/policy", now, now.plusYears(1),
                    userConsents, now, "admin"
            );

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getConsentDefinition()).isSameAs(definition);
            assertThat(entity.getVersion()).isEqualTo("1.0");
            assertThat(entity.getConsentText()).isEqualTo("Consent text");
            assertThat(entity.getPolicyUrl()).isEqualTo("https://example.com/policy");
            assertThat(entity.getEffectiveFrom()).isEqualTo(now);
            assertThat(entity.getEffectiveUntil()).isEqualTo(now.plusYears(1));
            assertThat(entity.getUserConsents()).isSameAs(userConsents);
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getUpdaterId()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should pass validation with valid fields")
        void shouldPassValidationWithValidFields() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("1.0");
            entity.setConsentText("Consent text");
            entity.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when version is blank")
        void shouldFailValidationWhenVersionIsBlank() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("");
            entity.setConsentText("Text");
            entity.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("version"));
        }

        @Test
        @DisplayName("should fail validation when version exceeds max length")
        void shouldFailValidationWhenVersionExceedsMaxLength() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("A".repeat(51));
            entity.setConsentText("Text");
            entity.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("version"));
        }

        @Test
        @DisplayName("should accept version at max length")
        void shouldAcceptVersionAtMaxLength() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("A".repeat(50));
            entity.setConsentText("Text");
            entity.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations.stream().filter(v -> v.getPropertyPath().toString().equals("version"))).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when consentText is blank")
        void shouldFailValidationWhenConsentTextIsBlank() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("1.0");
            entity.setConsentText("");
            entity.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentText"));
        }

        @Test
        @DisplayName("should fail validation when effectiveFrom is null")
        void shouldFailValidationWhenEffectiveFromIsNull() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("1.0");
            entity.setConsentText("Text");
            entity.setEffectiveFrom(null);

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("effectiveFrom"));
        }

        @Test
        @DisplayName("should fail validation when policyUrl exceeds max length")
        void shouldFailValidationWhenPolicyUrlExceedsMaxLength() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("1.0");
            entity.setConsentText("Text");
            entity.setEffectiveFrom(LocalDateTime.now());
            entity.setPolicyUrl("A".repeat(501));

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("policyUrl"));
        }

        @Test
        @DisplayName("should accept policyUrl at max length")
        void shouldAcceptPolicyUrlAtMaxLength() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("1.0");
            entity.setConsentText("Text");
            entity.setEffectiveFrom(LocalDateTime.now());
            entity.setPolicyUrl("A".repeat(500));

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations.stream().filter(v -> v.getPropertyPath().toString().equals("policyUrl"))).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when updaterId exceeds max length")
        void shouldFailValidationWhenUpdaterIdExceedsMaxLength() {
            ConsentVersion entity = new ConsentVersion();
            entity.setVersion("1.0");
            entity.setConsentText("Text");
            entity.setEffectiveFrom(LocalDateTime.now());
            entity.setUpdaterId("A".repeat(256));

            Set<ConstraintViolation<ConsentVersion>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("updaterId"));
        }

        @Test
        @DisplayName("should implement UpdaterTracking interface")
        void shouldImplementUpdaterTrackingInterface() {
            ConsentVersion entity = new ConsentVersion();
            entity.setUpdaterId("user@example.com");

            assertThat(entity.getUpdaterId()).isEqualTo("user@example.com");
        }
    }

    // ==================== ConsentVersionDtoIn Tests ====================

    @Nested
    @DisplayName("ConsentVersionDtoIn Tests")
    class ConsentVersionDtoInTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();

            assertThat(dto.getConsentDefinitionId()).isNull();
            assertThat(dto.getVersion()).isNull();
            assertThat(dto.getConsentText()).isNull();
            assertThat(dto.getPolicyUrl()).isNull();
            assertThat(dto.getEffectiveFrom()).isNull();
            assertThat(dto.getEffectiveUntil()).isNull();
        }

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("I agree to receive marketing communications");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields")
        void shouldPassValidationWithAllFields() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("2.0");
            dto.setConsentText("Updated consent text");
            dto.setPolicyUrl("https://example.com/privacy");
            dto.setEffectiveFrom(LocalDateTime.now());
            dto.setEffectiveUntil(LocalDateTime.now().plusYears(1));

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when consentDefinitionId is null")
        void shouldFailWhenConsentDefinitionIdIsNull() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(null);
            dto.setVersion("1.0");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentDefinitionId"));
        }

        @Test
        @DisplayName("should fail when version is blank")
        void shouldFailWhenVersionIsBlank() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("version"));
        }

        @Test
        @DisplayName("should fail when version exceeds max length")
        void shouldFailWhenVersionExceedsMaxLength() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("A".repeat(51));
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("version"));
        }

        @Test
        @DisplayName("should fail when consentText is blank")
        void shouldFailWhenConsentTextIsBlank() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentText"));
        }

        @Test
        @DisplayName("should fail when effectiveFrom is null")
        void shouldFailWhenEffectiveFromIsNull() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(null);

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("effectiveFrom"));
        }

        @Test
        @DisplayName("should fail when policyUrl exceeds max length")
        void shouldFailWhenPolicyUrlExceedsMaxLength() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now());
            dto.setPolicyUrl("A".repeat(501));

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("policyUrl"));
        }

        @Test
        @DisplayName("should accept null effectiveUntil")
        void shouldAcceptNullEffectiveUntil() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now());
            dto.setEffectiveUntil(null);

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 100L, Long.MAX_VALUE})
        @DisplayName("should accept various consent definition IDs")
        void shouldAcceptVariousConsentDefinitionIds(Long id) {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(id);
            dto.setVersion("1.0");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== ConsentVersionDtoOut Tests ====================

    @Nested
    @DisplayName("ConsentVersionDtoOut Tests")
    class ConsentVersionDtoOutTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            ConsentVersionDtoOut dto = new ConsentVersionDtoOut();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getConsentDefinitionId()).isNull();
            assertThat(dto.getVersion()).isNull();
            assertThat(dto.getConsentText()).isNull();
            assertThat(dto.getPolicyUrl()).isNull();
            assertThat(dto.getEffectiveFrom()).isNull();
            assertThat(dto.getEffectiveUntil()).isNull();
            assertThat(dto.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            ConsentVersionDtoOut dto = new ConsentVersionDtoOut();
            LocalDateTime now = LocalDateTime.now();

            dto.setId(1L);
            dto.setConsentDefinitionId(2L);
            dto.setVersion("1.0");
            dto.setConsentText("Consent text");
            dto.setPolicyUrl("https://example.com/policy");
            dto.setEffectiveFrom(now);
            dto.setEffectiveUntil(now.plusYears(1));
            dto.setCreatedAt(now);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getConsentDefinitionId()).isEqualTo(2L);
            assertThat(dto.getVersion()).isEqualTo("1.0");
            assertThat(dto.getConsentText()).isEqualTo("Consent text");
            assertThat(dto.getPolicyUrl()).isEqualTo("https://example.com/policy");
            assertThat(dto.getEffectiveFrom()).isEqualTo(now);
            assertThat(dto.getEffectiveUntil()).isEqualTo(now.plusYears(1));
            assertThat(dto.getCreatedAt()).isEqualTo(now);
        }
    }

    // ==================== UserConsent Entity Tests ====================

    @Nested
    @DisplayName("UserConsent Entity Tests")
    class UserConsentEntityTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            UserConsent entity = new UserConsent();

            assertThat(entity.getId()).isNull();
            assertThat(entity.getUser()).isNull();
            assertThat(entity.getConsentVersion()).isNull();
            assertThat(entity.getAction()).isNull();
            assertThat(entity.getConsentGiven()).isNull();
            assertThat(entity.getIpAddress()).isNull();
            assertThat(entity.getUserAgent()).isNull();
            assertThat(entity.getCollectionMethod()).isEqualTo("web_form"); // Default value
            assertThat(entity.getLegalBasis()).isEqualTo("consent"); // Default value
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdaterId()).isNull();
        }

        @Test
        @DisplayName("should set default collection method and legal basis")
        void shouldSetDefaultCollectionMethodAndLegalBasis() {
            UserConsent entity = new UserConsent();
            entity.setCollectionMethod("web_form");
            entity.setLegalBasis("consent");

            assertThat(entity.getCollectionMethod()).isEqualTo("web_form");
            assertThat(entity.getLegalBasis()).isEqualTo("consent");
        }

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            UserConsent entity = new UserConsent();
            entity.setAction(ConsentAction.GRANTED);
            entity.setConsentGiven(true);

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when action is null")
        void shouldFailValidationWhenActionIsNull() {
            UserConsent entity = new UserConsent();
            entity.setAction(null);
            entity.setConsentGiven(true);

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("action"));
        }

        @Test
        @DisplayName("should fail validation when consentGiven is null")
        void shouldFailValidationWhenConsentGivenIsNull() {
            UserConsent entity = new UserConsent();
            entity.setAction(ConsentAction.GRANTED);
            entity.setConsentGiven(null);

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentGiven"));
        }

        @Test
        @DisplayName("should fail validation when userAgent exceeds max length")
        void shouldFailValidationWhenUserAgentExceedsMaxLength() {
            UserConsent entity = new UserConsent();
            entity.setAction(ConsentAction.GRANTED);
            entity.setConsentGiven(true);
            entity.setUserAgent("A".repeat(1001));

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("userAgent"));
        }

        @Test
        @DisplayName("should accept userAgent at max length")
        void shouldAcceptUserAgentAtMaxLength() {
            UserConsent entity = new UserConsent();
            entity.setAction(ConsentAction.GRANTED);
            entity.setConsentGiven(true);
            entity.setUserAgent("A".repeat(1000));

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations.stream().filter(v -> v.getPropertyPath().toString().equals("userAgent"))).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when collectionMethod exceeds max length")
        void shouldFailValidationWhenCollectionMethodExceedsMaxLength() {
            UserConsent entity = new UserConsent();
            entity.setAction(ConsentAction.GRANTED);
            entity.setConsentGiven(true);
            entity.setCollectionMethod("A".repeat(101));

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("collectionMethod"));
        }

        @Test
        @DisplayName("should fail validation when legalBasis exceeds max length")
        void shouldFailValidationWhenLegalBasisExceedsMaxLength() {
            UserConsent entity = new UserConsent();
            entity.setAction(ConsentAction.GRANTED);
            entity.setConsentGiven(true);
            entity.setLegalBasis("A".repeat(101));

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("legalBasis"));
        }

        @Test
        @DisplayName("should fail validation when updaterId exceeds max length")
        void shouldFailValidationWhenUpdaterIdExceedsMaxLength() {
            UserConsent entity = new UserConsent();
            entity.setAction(ConsentAction.GRANTED);
            entity.setConsentGiven(true);
            entity.setUpdaterId("A".repeat(256));

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("updaterId"));
        }

        @Test
        @DisplayName("should implement UpdaterTracking interface")
        void shouldImplementUpdaterTrackingInterface() {
            UserConsent entity = new UserConsent();
            entity.setUpdaterId("user@example.com");

            assertThat(entity.getUpdaterId()).isEqualTo("user@example.com");
        }

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("should accept all ConsentAction values")
        void shouldAcceptAllConsentActionValues(ConsentAction action) {
            UserConsent entity = new UserConsent();
            entity.setAction(action);
            entity.setConsentGiven(true);

            Set<ConstraintViolation<UserConsent>> violations = validator.validate(entity);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== UserConsentDtoIn Tests ====================

    @Nested
    @DisplayName("UserConsentDtoIn Tests")
    class UserConsentDtoInTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            UserConsentDtoIn dto = new UserConsentDtoIn();

            assertThat(dto.getConsentType()).isNull();
            assertThat(dto.getConsentGiven()).isNull();
            assertThat(dto.getUserAgent()).isNull();
            assertThat(dto.getCollectionMethod()).isEqualTo("web_form");
        }

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("marketing");
            dto.setConsentGiven(true);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields")
        void shouldPassValidationWithAllFields() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("analytics");
            dto.setConsentGiven(false);
            dto.setUserAgent("Mozilla/5.0");
            dto.setCollectionMethod("api");

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when consentType is blank")
        void shouldFailWhenConsentTypeIsBlank() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("");
            dto.setConsentGiven(true);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail when consentType is null")
        void shouldFailWhenConsentTypeIsNull() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType(null);
            dto.setConsentGiven(true);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail when consentGiven is null")
        void shouldFailWhenConsentGivenIsNull() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("marketing");
            dto.setConsentGiven(null);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentGiven"));
        }

        @Test
        @DisplayName("should accept null optional fields")
        void shouldAcceptNullOptionalFields() {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("marketing");
            dto.setConsentGiven(true);
            dto.setUserAgent(null);
            dto.setCollectionMethod(null);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("should accept both true and false for consentGiven")
        void shouldAcceptBothBooleanValues(boolean consentGiven) {
            UserConsentDtoIn dto = new UserConsentDtoIn();
            dto.setConsentType("marketing");
            dto.setConsentGiven(consentGiven);

            Set<ConstraintViolation<UserConsentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== UserConsentDtoOut Tests ====================

    @Nested
    @DisplayName("UserConsentDtoOut Tests")
    class UserConsentDtoOutTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            UserConsentDtoOut dto = new UserConsentDtoOut();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getConsentVersion()).isNull();
            assertThat(dto.getAction()).isNull();
            assertThat(dto.getConsentGiven()).isNull();
            assertThat(dto.getCollectionMethod()).isNull();
            assertThat(dto.getLegalBasis()).isNull();
            assertThat(dto.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            UserConsentDtoOut dto = new UserConsentDtoOut();
            LocalDateTime now = LocalDateTime.now();
            ConsentVersionDtoOut versionDto = new ConsentVersionDtoOut();
            ConsentActionDtoOut actionDto = ConsentActionDtoOut.builder().value("GRANTED").build();
            CollectionMethodDtoOut methodDto = CollectionMethodDtoOut.builder().value("web_form").build();
            LegalBasisDtoOut basisDto = LegalBasisDtoOut.builder().value("consent").build();

            dto.setId(1L);
            dto.setUserId(100L);
            dto.setConsentVersion(versionDto);
            dto.setAction(actionDto);
            dto.setConsentGiven(true);
            dto.setCollectionMethod(methodDto);
            dto.setLegalBasis(basisDto);
            dto.setCreatedAt(now);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUserId()).isEqualTo(100L);
            assertThat(dto.getConsentVersion()).isSameAs(versionDto);
            assertThat(dto.getAction()).isSameAs(actionDto);
            assertThat(dto.getConsentGiven()).isTrue();
            assertThat(dto.getCollectionMethod()).isSameAs(methodDto);
            assertThat(dto.getLegalBasis()).isSameAs(basisDto);
            assertThat(dto.getCreatedAt()).isEqualTo(now);
        }
    }

    // ==================== ConsentAction Enum Tests ====================

    @Nested
    @DisplayName("ConsentAction Enum Tests")
    class ConsentActionEnumTests {

        @Test
        @DisplayName("GRANTED should have correct properties")
        void grantedShouldHaveCorrectProperties() {
            ConsentAction action = ConsentAction.GRANTED;

            assertThat(action.getColorTheme()).isEqualTo("success");
            assertThat(action.getIcon()).isEqualTo("check-circle");
            assertThat(action.getAliases()).containsExactly("accepted", "approved", "given");
            assertThat(action.getDescription()).isEqualTo("Zgoda została udzielona przez użytkownika");
            assertThat(action.isPositiveAction()).isTrue();
            assertThat(action.isNegativeAction()).isFalse();
            assertThat(action.isModificationAction()).isFalse();
        }

        @Test
        @DisplayName("WITHDRAWN should have correct properties")
        void withdrawnShouldHaveCorrectProperties() {
            ConsentAction action = ConsentAction.WITHDRAWN;

            assertThat(action.getColorTheme()).isEqualTo("danger");
            assertThat(action.getIcon()).isEqualTo("x-circle");
            assertThat(action.getAliases()).containsExactly("revoked", "removed", "denied");
            assertThat(action.getDescription()).isEqualTo("Zgoda została wycofana przez użytkownika");
            assertThat(action.isPositiveAction()).isFalse();
            assertThat(action.isNegativeAction()).isTrue();
            assertThat(action.isModificationAction()).isFalse();
        }

        @Test
        @DisplayName("UPDATED should have correct properties")
        void updatedShouldHaveCorrectProperties() {
            ConsentAction action = ConsentAction.UPDATED;

            assertThat(action.getColorTheme()).isEqualTo("warning");
            assertThat(action.getIcon()).isEqualTo("edit");
            assertThat(action.getAliases()).containsExactly("modified", "changed");
            assertThat(action.getDescription()).isEqualTo("Zgoda została zaktualizowana");
            assertThat(action.isPositiveAction()).isFalse();
            assertThat(action.isNegativeAction()).isFalse();
            assertThat(action.isModificationAction()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"GRANTED", "granted", "Granted", "GrAnTeD"})
        @DisplayName("should parse from string case-insensitively")
        void shouldParseFromStringCaseInsensitively(String value) {
            ConsentAction action = ConsentAction.fromString(value);
            assertThat(action).isEqualTo(ConsentAction.GRANTED);
        }

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("getValue should return enum name")
        void getValueShouldReturnEnumName(ConsentAction action) {
            assertThat(action.getValue()).isEqualTo(action.name());
        }

        @Test
        @DisplayName("should get label from dictionary service")
        void shouldGetLabelFromDictionaryService() {
            when(dictionaryService.getTranslation(eq("CONSENT_ACTION_GRANTED"), anyString()))
                    .thenReturn(Optional.of("Udzielona"));

            String label = ConsentAction.GRANTED.getLabel(dictionaryService, Locale.forLanguageTag("pl"));

            assertThat(label).isEqualTo("Udzielona");
        }

        @Test
        @DisplayName("should return enum name as fallback when translation missing")
        void shouldReturnEnumNameAsFallbackWhenTranslationMissing() {
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String label = ConsentAction.GRANTED.getLabel(dictionaryService, Locale.ENGLISH);

            assertThat(label).isEqualTo("GRANTED");
        }

        @Test
        @DisplayName("should get description from dictionary service")
        void shouldGetDescriptionFromDictionaryService() {
            when(dictionaryService.getTranslation(eq("CONSENT_ACTION_GRANTED_DESC"), anyString()))
                    .thenReturn(Optional.of("Consent was granted by user"));

            String description = ConsentAction.GRANTED.getDescription(dictionaryService, Locale.ENGLISH);

            assertThat(description).isEqualTo("Consent was granted by user");
        }

        @Test
        @DisplayName("should return default description when translation missing")
        void shouldReturnDefaultDescriptionWhenTranslationMissing() {
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String description = ConsentAction.GRANTED.getDescription(dictionaryService, Locale.ENGLISH);

            assertThat(description).isEqualTo("Zgoda została udzielona przez użytkownika");
        }

        @Test
        @DisplayName("should have exactly three values")
        void shouldHaveExactlyThreeValues() {
            assertThat(ConsentAction.values()).hasSize(3);
        }
    }

    // ==================== ConsentActionDtoOut Tests ====================

    @Nested
    @DisplayName("ConsentActionDtoOut Tests")
    class ConsentActionDtoOutTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            ConsentActionDtoOut dto = new ConsentActionDtoOut();

            assertThat(dto.getValue()).isNull();
            assertThat(dto.getLabel()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getOriginalLabel()).isNull();
            assertThat(dto.getColorTheme()).isNull();
            assertThat(dto.getIcon()).isNull();
            assertThat(dto.isPositiveAction()).isFalse();
            assertThat(dto.isNegativeAction()).isFalse();
            assertThat(dto.isModificationAction()).isFalse();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            ConsentActionDtoOut dto = new ConsentActionDtoOut(
                    "GRANTED", "Granted", "Description", "GRANTED",
                    "success", "check-circle", true, false, false
            );

            assertThat(dto.getValue()).isEqualTo("GRANTED");
            assertThat(dto.getLabel()).isEqualTo("Granted");
            assertThat(dto.getDescription()).isEqualTo("Description");
            assertThat(dto.getOriginalLabel()).isEqualTo("GRANTED");
            assertThat(dto.getColorTheme()).isEqualTo("success");
            assertThat(dto.getIcon()).isEqualTo("check-circle");
            assertThat(dto.isPositiveAction()).isTrue();
            assertThat(dto.isNegativeAction()).isFalse();
            assertThat(dto.isModificationAction()).isFalse();
        }

        @Test
        @DisplayName("should create with builder")
        void shouldCreateWithBuilder() {
            ConsentActionDtoOut dto = ConsentActionDtoOut.builder()
                    .value("WITHDRAWN")
                    .label("Withdrawn")
                    .description("Consent was withdrawn")
                    .originalLabel("WITHDRAWN")
                    .colorTheme("danger")
                    .icon("x-circle")
                    .isPositiveAction(false)
                    .isNegativeAction(true)
                    .isModificationAction(false)
                    .build();

            assertThat(dto.getValue()).isEqualTo("WITHDRAWN");
            assertThat(dto.getLabel()).isEqualTo("Withdrawn");
            assertThat(dto.getDescription()).isEqualTo("Consent was withdrawn");
            assertThat(dto.getColorTheme()).isEqualTo("danger");
            assertThat(dto.getIcon()).isEqualTo("x-circle");
            assertThat(dto.isNegativeAction()).isTrue();
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            ConsentActionDtoOut dto1 = ConsentActionDtoOut.builder()
                    .value("GRANTED")
                    .label("Granted")
                    .build();
            ConsentActionDtoOut dto2 = ConsentActionDtoOut.builder()
                    .value("GRANTED")
                    .label("Granted")
                    .build();
            ConsentActionDtoOut dto3 = ConsentActionDtoOut.builder()
                    .value("WITHDRAWN")
                    .label("Withdrawn")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            ConsentActionDtoOut dto1 = ConsentActionDtoOut.builder()
                    .value("GRANTED")
                    .label("Granted")
                    .build();
            ConsentActionDtoOut dto2 = ConsentActionDtoOut.builder()
                    .value("GRANTED")
                    .label("Granted")
                    .build();

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            ConsentActionDtoOut dto = ConsentActionDtoOut.builder()
                    .value("GRANTED")
                    .label("Granted")
                    .build();

            String toString = dto.toString();
            assertThat(toString).contains("GRANTED");
            assertThat(toString).contains("Granted");
        }
    }

    // ==================== ConsentTypeDtoOut Tests ====================

    @Nested
    @DisplayName("ConsentTypeDtoOut Tests")
    class ConsentTypeDtoOutTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            ConsentTypeDtoOut dto = new ConsentTypeDtoOut();

            assertThat(dto.getValue()).isNull();
            assertThat(dto.getLabel()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getOriginalLabel()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            ConsentTypeDtoOut dto = new ConsentTypeDtoOut(
                    "marketing", "Marketing Communications",
                    "Consent for marketing emails", "marketing"
            );

            assertThat(dto.getValue()).isEqualTo("marketing");
            assertThat(dto.getLabel()).isEqualTo("Marketing Communications");
            assertThat(dto.getDescription()).isEqualTo("Consent for marketing emails");
            assertThat(dto.getOriginalLabel()).isEqualTo("marketing");
        }

        @Test
        @DisplayName("should create with builder")
        void shouldCreateWithBuilder() {
            ConsentTypeDtoOut dto = ConsentTypeDtoOut.builder()
                    .value("analytics")
                    .label("Analytics")
                    .description("Analytics consent")
                    .originalLabel("analytics")
                    .build();

            assertThat(dto.getValue()).isEqualTo("analytics");
            assertThat(dto.getLabel()).isEqualTo("Analytics");
            assertThat(dto.getDescription()).isEqualTo("Analytics consent");
            assertThat(dto.getOriginalLabel()).isEqualTo("analytics");
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            ConsentTypeDtoOut dto1 = ConsentTypeDtoOut.builder()
                    .value("marketing")
                    .label("Marketing")
                    .build();
            ConsentTypeDtoOut dto2 = ConsentTypeDtoOut.builder()
                    .value("marketing")
                    .label("Marketing")
                    .build();
            ConsentTypeDtoOut dto3 = ConsentTypeDtoOut.builder()
                    .value("analytics")
                    .label("Analytics")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            ConsentTypeDtoOut dto1 = ConsentTypeDtoOut.builder()
                    .value("marketing")
                    .build();
            ConsentTypeDtoOut dto2 = ConsentTypeDtoOut.builder()
                    .value("marketing")
                    .build();

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            ConsentTypeDtoOut dto = ConsentTypeDtoOut.builder()
                    .value("cookies")
                    .label("Cookies")
                    .build();

            String toString = dto.toString();
            assertThat(toString).contains("cookies");
            assertThat(toString).contains("Cookies");
        }
    }

    // ==================== LegalBasisDtoOut Tests ====================

    @Nested
    @DisplayName("LegalBasisDtoOut Tests")
    class LegalBasisDtoOutTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            LegalBasisDtoOut dto = new LegalBasisDtoOut();

            assertThat(dto.getValue()).isNull();
            assertThat(dto.getLabel()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getOriginalLabel()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            LegalBasisDtoOut dto = new LegalBasisDtoOut(
                    "consent", "Consent", "Based on user consent", "consent"
            );

            assertThat(dto.getValue()).isEqualTo("consent");
            assertThat(dto.getLabel()).isEqualTo("Consent");
            assertThat(dto.getDescription()).isEqualTo("Based on user consent");
            assertThat(dto.getOriginalLabel()).isEqualTo("consent");
        }

        @Test
        @DisplayName("should create with builder")
        void shouldCreateWithBuilder() {
            LegalBasisDtoOut dto = LegalBasisDtoOut.builder()
                    .value("legitimate_interest")
                    .label("Legitimate Interest")
                    .description("Based on legitimate interest")
                    .originalLabel("legitimate_interest")
                    .build();

            assertThat(dto.getValue()).isEqualTo("legitimate_interest");
            assertThat(dto.getLabel()).isEqualTo("Legitimate Interest");
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            LegalBasisDtoOut dto1 = LegalBasisDtoOut.builder().value("consent").build();
            LegalBasisDtoOut dto2 = LegalBasisDtoOut.builder().value("consent").build();
            LegalBasisDtoOut dto3 = LegalBasisDtoOut.builder().value("contract").build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            LegalBasisDtoOut dto1 = LegalBasisDtoOut.builder().value("consent").build();
            LegalBasisDtoOut dto2 = LegalBasisDtoOut.builder().value("consent").build();

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            LegalBasisDtoOut dto = LegalBasisDtoOut.builder()
                    .value("consent")
                    .label("Consent")
                    .build();

            String toString = dto.toString();
            assertThat(toString).contains("consent");
        }
    }

    // ==================== CollectionMethodDtoOut Tests ====================

    @Nested
    @DisplayName("CollectionMethodDtoOut Tests")
    class CollectionMethodDtoOutTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            CollectionMethodDtoOut dto = new CollectionMethodDtoOut();

            assertThat(dto.getValue()).isNull();
            assertThat(dto.getLabel()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getOriginalLabel()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            CollectionMethodDtoOut dto = new CollectionMethodDtoOut(
                    "web_form", "Web Form", "Collected via web form", "web_form"
            );

            assertThat(dto.getValue()).isEqualTo("web_form");
            assertThat(dto.getLabel()).isEqualTo("Web Form");
            assertThat(dto.getDescription()).isEqualTo("Collected via web form");
            assertThat(dto.getOriginalLabel()).isEqualTo("web_form");
        }

        @Test
        @DisplayName("should create with builder")
        void shouldCreateWithBuilder() {
            CollectionMethodDtoOut dto = CollectionMethodDtoOut.builder()
                    .value("api")
                    .label("API")
                    .description("Collected via API")
                    .originalLabel("api")
                    .build();

            assertThat(dto.getValue()).isEqualTo("api");
            assertThat(dto.getLabel()).isEqualTo("API");
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            CollectionMethodDtoOut dto1 = CollectionMethodDtoOut.builder().value("api").build();
            CollectionMethodDtoOut dto2 = CollectionMethodDtoOut.builder().value("api").build();
            CollectionMethodDtoOut dto3 = CollectionMethodDtoOut.builder().value("import").build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            CollectionMethodDtoOut dto1 = CollectionMethodDtoOut.builder().value("api").build();
            CollectionMethodDtoOut dto2 = CollectionMethodDtoOut.builder().value("api").build();

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            CollectionMethodDtoOut dto = CollectionMethodDtoOut.builder()
                    .value("web_form")
                    .label("Web Form")
                    .build();

            String toString = dto.toString();
            assertThat(toString).contains("web_form");
        }
    }

    // ==================== UserCurrentConsent Entity Tests ====================

    @Nested
    @DisplayName("UserCurrentConsent Entity Tests")
    class UserCurrentConsentEntityTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            UserCurrentConsent entity = new UserCurrentConsent();

            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getConsentDefinitionId()).isNull();
            assertThat(entity.getUser()).isNull();
            assertThat(entity.getConsentDefinition()).isNull();
            assertThat(entity.getConsentVersion()).isNull();
            assertThat(entity.getConsentGiven()).isNull();
            assertThat(entity.getGrantedAt()).isNull();
            assertThat(entity.getWithdrawnAt()).isNull();
            assertThat(entity.getLastUpdated()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();

            UserCurrentConsent entity = new UserCurrentConsent(
                    1L, 2L, null, null, null, true, now, null, now
            );

            assertThat(entity.getUserId()).isEqualTo(1L);
            assertThat(entity.getConsentDefinitionId()).isEqualTo(2L);
            assertThat(entity.getConsentGiven()).isTrue();
            assertThat(entity.getGrantedAt()).isEqualTo(now);
            assertThat(entity.getWithdrawnAt()).isNull();
            assertThat(entity.getLastUpdated()).isEqualTo(now);
        }

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            UserCurrentConsent entity = new UserCurrentConsent();
            LocalDateTime now = LocalDateTime.now();

            entity.setUserId(100L);
            entity.setConsentDefinitionId(200L);
            entity.setConsentGiven(false);
            entity.setGrantedAt(now.minusDays(30));
            entity.setWithdrawnAt(now);
            entity.setLastUpdated(now);

            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getConsentDefinitionId()).isEqualTo(200L);
            assertThat(entity.getConsentGiven()).isFalse();
            assertThat(entity.getGrantedAt()).isEqualTo(now.minusDays(30));
            assertThat(entity.getWithdrawnAt()).isEqualTo(now);
            assertThat(entity.getLastUpdated()).isEqualTo(now);
        }

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            UserCurrentConsent entity = new UserCurrentConsent();
            entity.setConsentGiven(true);
            entity.setLastUpdated(LocalDateTime.now());

            Set<ConstraintViolation<UserCurrentConsent>> violations = validator.validate(entity);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail validation when consentGiven is null")
        void shouldFailValidationWhenConsentGivenIsNull() {
            UserCurrentConsent entity = new UserCurrentConsent();
            entity.setConsentGiven(null);
            entity.setLastUpdated(LocalDateTime.now());

            Set<ConstraintViolation<UserCurrentConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentGiven"));
        }

        @Test
        @DisplayName("should fail validation when lastUpdated is null")
        void shouldFailValidationWhenLastUpdatedIsNull() {
            UserCurrentConsent entity = new UserCurrentConsent();
            entity.setConsentGiven(true);
            entity.setLastUpdated(null);

            Set<ConstraintViolation<UserCurrentConsent>> violations = validator.validate(entity);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("lastUpdated"));
        }
    }

    // ==================== UserCurrentConsentDtoOut Tests ====================

    @Nested
    @DisplayName("UserCurrentConsentDtoOut Tests")
    class UserCurrentConsentDtoOutTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            UserCurrentConsentDtoOut dto = new UserCurrentConsentDtoOut();

            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getConsentType()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getConsentGiven()).isNull();
            assertThat(dto.getCurrentVersion()).isNull();
            assertThat(dto.getConsentText()).isNull();
            assertThat(dto.getPolicyUrl()).isNull();
            assertThat(dto.getGrantedAt()).isNull();
            assertThat(dto.getWithdrawnAt()).isNull();
            assertThat(dto.getLastUpdated()).isNull();
        }

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            UserCurrentConsentDtoOut dto = new UserCurrentConsentDtoOut();
            LocalDateTime now = LocalDateTime.now();
            ConsentTypeDtoOut consentType = ConsentTypeDtoOut.builder()
                    .value("marketing")
                    .label("Marketing")
                    .build();

            dto.setUserId(1L);
            dto.setConsentType(consentType);
            dto.setName("Marketing Consent");
            dto.setDescription("Consent for marketing emails");
            dto.setConsentGiven(true);
            dto.setCurrentVersion("2.0");
            dto.setConsentText("I agree...");
            dto.setPolicyUrl("https://example.com/policy");
            dto.setGrantedAt(now);
            dto.setWithdrawnAt(null);
            dto.setLastUpdated(now);

            assertThat(dto.getUserId()).isEqualTo(1L);
            assertThat(dto.getConsentType()).isSameAs(consentType);
            assertThat(dto.getName()).isEqualTo("Marketing Consent");
            assertThat(dto.getDescription()).isEqualTo("Consent for marketing emails");
            assertThat(dto.getConsentGiven()).isTrue();
            assertThat(dto.getCurrentVersion()).isEqualTo("2.0");
            assertThat(dto.getConsentText()).isEqualTo("I agree...");
            assertThat(dto.getPolicyUrl()).isEqualTo("https://example.com/policy");
            assertThat(dto.getGrantedAt()).isEqualTo(now);
            assertThat(dto.getWithdrawnAt()).isNull();
            assertThat(dto.getLastUpdated()).isEqualTo(now);
        }
    }

    // ==================== UserCurrentConsentId Tests ====================

    @Nested
    @DisplayName("UserCurrentConsentId Tests")
    class UserCurrentConsentIdTests {

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            UserCurrentConsentId id = new UserCurrentConsentId();

            // Default constructor - fields would be null but we can't access them directly
            assertThat(id).isNotNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            UserCurrentConsentId id = new UserCurrentConsentId(1L, 2L);

            assertThat(id).isNotNull();
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            UserCurrentConsentId id1 = new UserCurrentConsentId(1L, 2L);
            UserCurrentConsentId id2 = new UserCurrentConsentId(1L, 2L);
            UserCurrentConsentId id3 = new UserCurrentConsentId(1L, 3L);
            UserCurrentConsentId id4 = new UserCurrentConsentId(2L, 2L);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1).isNotEqualTo(id3);
            assertThat(id1).isNotEqualTo(id4);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            UserCurrentConsentId id1 = new UserCurrentConsentId(1L, 2L);
            UserCurrentConsentId id2 = new UserCurrentConsentId(1L, 2L);

            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should handle null values in equals")
        void shouldHandleNullValuesInEquals() {
            UserCurrentConsentId id1 = new UserCurrentConsentId(null, null);
            UserCurrentConsentId id2 = new UserCurrentConsentId(null, null);
            UserCurrentConsentId id3 = new UserCurrentConsentId(1L, 2L);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1).isNotEqualTo(id3);
        }

        @Test
        @DisplayName("should not equal null")
        void shouldNotEqualNull() {
            UserCurrentConsentId id = new UserCurrentConsentId(1L, 2L);

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not equal different type")
        void shouldNotEqualDifferentType() {
            UserCurrentConsentId id = new UserCurrentConsentId(1L, 2L);

            assertThat(id).isNotEqualTo("not an id");
        }

        @Test
        @DisplayName("should equal itself")
        void shouldEqualItself() {
            UserCurrentConsentId id = new UserCurrentConsentId(1L, 2L);

            assertThat(id).isEqualTo(id);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle unicode in consent text")
        void shouldHandleUnicodeInConsentText() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("Wyrazzam zgode na przetwarzanie: aecloszzn");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle empty versions list")
        void shouldHandleEmptyVersionsList() {
            ConsentDefinitionDtoOut dto = new ConsentDefinitionDtoOut();
            dto.setVersions(new ArrayList<>());

            assertThat(dto.getVersions()).isEmpty();
        }

        @Test
        @DisplayName("should handle whitespace-only fields")
        void shouldHandleWhitespaceOnlyFields() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("   ");
            dto.setName("   ");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should handle very long consent text")
        void shouldHandleVeryLongConsentText() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("A".repeat(10000));
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle past effective dates")
        void shouldHandlePastEffectiveDates() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now().minusYears(5));

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle future effective dates")
        void shouldHandleFutureEffectiveDates() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("Text");
            dto.setEffectiveFrom(LocalDateTime.now().plusYears(5));
            dto.setEffectiveUntil(LocalDateTime.now().plusYears(10));

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle all ConsentAction aliases")
        void shouldHandleAllConsentActionAliases() {
            assertThat(ConsentAction.GRANTED.getAliases()).hasSize(3);
            assertThat(ConsentAction.WITHDRAWN.getAliases()).hasSize(3);
            assertThat(ConsentAction.UPDATED.getAliases()).hasSize(2);
        }

        @Test
        @DisplayName("should validate mutual exclusivity of action types")
        void shouldValidateMutualExclusivityOfActionTypes() {
            for (ConsentAction action : ConsentAction.values()) {
                int trueCount = 0;
                if (action.isPositiveAction()) trueCount++;
                if (action.isNegativeAction()) trueCount++;
                if (action.isModificationAction()) trueCount++;

                assertThat(trueCount).isLessThanOrEqualTo(1);
            }
        }

        @Test
        @DisplayName("should handle special characters in regulation reference")
        void shouldHandleSpecialCharactersInRegulationReference() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("gdpr_marketing");
            dto.setName("GDPR Marketing");
            dto.setRegulationReference("GDPR Art. 6(1)(a), Art. 7 - Recital (32)");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }
}
