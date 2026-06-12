package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.consent.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Consent and Address DTOs.
 */
@DisplayName("Consent and Address Unit Tests")
class ConsentAndAddressUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ConsentAction Enum Tests ====================

    @Nested
    @DisplayName("ConsentAction Enum Tests")
    class ConsentActionEnumTests {

        @Test
        @DisplayName("should have 3 action values")
        void shouldHaveThreeActionValues() {
            assertThat(ConsentAction.values()).hasSize(3);
        }

        @Test
        @DisplayName("should contain expected values")
        void shouldContainExpectedValues() {
            assertThat(ConsentAction.values()).containsExactlyInAnyOrder(
                    ConsentAction.GRANTED,
                    ConsentAction.WITHDRAWN,
                    ConsentAction.UPDATED
            );
        }

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("all actions should have non-null colorTheme")
        void allActionsShouldHaveNonNullColorTheme(ConsentAction action) {
            assertThat(action.getColorTheme()).isNotBlank();
        }

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("all actions should have non-null icon")
        void allActionsShouldHaveNonNullIcon(ConsentAction action) {
            assertThat(action.getIcon()).isNotBlank();
        }

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("all actions should have aliases")
        void allActionsShouldHaveAliases(ConsentAction action) {
            assertThat(action.getAliases()).isNotEmpty();
        }

        @ParameterizedTest
        @EnumSource(ConsentAction.class)
        @DisplayName("all actions should have description")
        void allActionsShouldHaveDescription(ConsentAction action) {
            assertThat(action.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("GRANTED should have correct properties")
        void grantedShouldHaveCorrectProperties() {
            ConsentAction action = ConsentAction.GRANTED;
            assertThat(action.getColorTheme()).isEqualTo("success");
            assertThat(action.getIcon()).isEqualTo("check-circle");
            assertThat(action.getAliases()).contains("accepted", "approved", "given");
        }

        @Test
        @DisplayName("WITHDRAWN should have correct properties")
        void withdrawnShouldHaveCorrectProperties() {
            ConsentAction action = ConsentAction.WITHDRAWN;
            assertThat(action.getColorTheme()).isEqualTo("danger");
            assertThat(action.getIcon()).isEqualTo("x-circle");
            assertThat(action.getAliases()).contains("revoked", "removed", "denied");
        }

        @Test
        @DisplayName("UPDATED should have correct properties")
        void updatedShouldHaveCorrectProperties() {
            ConsentAction action = ConsentAction.UPDATED;
            assertThat(action.getColorTheme()).isEqualTo("warning");
            assertThat(action.getIcon()).isEqualTo("edit");
            assertThat(action.getAliases()).contains("modified", "changed");
        }

        @Nested
        @DisplayName("Action Type Helper Methods")
        class ActionTypeHelperMethodsTests {

            @Test
            @DisplayName("only GRANTED should be positive action")
            void onlyGrantedShouldBePositiveAction() {
                assertThat(ConsentAction.GRANTED.isPositiveAction()).isTrue();
                assertThat(ConsentAction.WITHDRAWN.isPositiveAction()).isFalse();
                assertThat(ConsentAction.UPDATED.isPositiveAction()).isFalse();
            }

            @Test
            @DisplayName("only WITHDRAWN should be negative action")
            void onlyWithdrawnShouldBeNegativeAction() {
                assertThat(ConsentAction.WITHDRAWN.isNegativeAction()).isTrue();
                assertThat(ConsentAction.GRANTED.isNegativeAction()).isFalse();
                assertThat(ConsentAction.UPDATED.isNegativeAction()).isFalse();
            }

            @Test
            @DisplayName("only UPDATED should be modification action")
            void onlyUpdatedShouldBeModificationAction() {
                assertThat(ConsentAction.UPDATED.isModificationAction()).isTrue();
                assertThat(ConsentAction.GRANTED.isModificationAction()).isFalse();
                assertThat(ConsentAction.WITHDRAWN.isModificationAction()).isFalse();
            }
        }

        @Nested
        @DisplayName("JSON Serialization Tests")
        class JsonSerializationTests {

            @ParameterizedTest
            @ValueSource(strings = {"GRANTED", "granted", "Granted"})
            @DisplayName("fromString should be case insensitive for GRANTED")
            void fromStringShouldBeCaseInsensitiveForGranted(String input) {
                assertThat(ConsentAction.fromString(input)).isEqualTo(ConsentAction.GRANTED);
            }

            @ParameterizedTest
            @ValueSource(strings = {"WITHDRAWN", "withdrawn", "Withdrawn"})
            @DisplayName("fromString should be case insensitive for WITHDRAWN")
            void fromStringShouldBeCaseInsensitiveForWithdrawn(String input) {
                assertThat(ConsentAction.fromString(input)).isEqualTo(ConsentAction.WITHDRAWN);
            }

            @Test
            @DisplayName("fromString should throw for invalid value")
            void fromStringShouldThrowForInvalidValue() {
                assertThatThrownBy(() -> ConsentAction.fromString("INVALID"))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @ParameterizedTest
            @EnumSource(ConsentAction.class)
            @DisplayName("getValue should return name")
            void getValueShouldReturnName(ConsentAction action) {
                assertThat(action.getValue()).isEqualTo(action.name());
            }
        }
    }

    // ==================== ConsentDefinitionDtoIn Tests ====================

    @Nested
    @DisplayName("ConsentDefinitionDtoIn Tests")
    class ConsentDefinitionDtoInTests {

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("MARKETING");
            dto.setName("Marketing consent");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields")
        void shouldPassValidationWithAllFields() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("MARKETING");
            dto.setName("Marketing consent");
            dto.setDescription("Consent for marketing communications");
            dto.setRegulationReference("GDPR Art. 6(1)(a)");
            dto.setIsActive(true);

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail when consentType is blank")
        void shouldFailWhenConsentTypeIsBlank(String consentType) {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType(consentType);
            dto.setName("Test consent");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @Test
        @DisplayName("should fail when consentType exceeds max length")
        void shouldFailWhenConsentTypeExceedsMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("A".repeat(101));
            dto.setName("Test consent");

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentType"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail when name is blank")
        void shouldFailWhenNameIsBlank(String name) {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("MARKETING");
            dto.setName(name);

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should fail when name exceeds max length")
        void shouldFailWhenNameExceedsMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("MARKETING");
            dto.setName("A".repeat(256));

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should fail when description exceeds max length")
        void shouldFailWhenDescriptionExceedsMaxLength() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("MARKETING");
            dto.setName("Test consent");
            dto.setDescription("A".repeat(1001));

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
        }

        @Test
        @DisplayName("should have default isActive as true")
        void shouldHaveDefaultIsActiveAsTrue() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();

            assertThat(dto.getIsActive()).isTrue();
        }
    }

    // ==================== ConsentVersionDtoIn Tests ====================

    @Nested
    @DisplayName("ConsentVersionDtoIn Tests")
    class ConsentVersionDtoInTests {

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("I agree to the terms and conditions");
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
            dto.setConsentText("I agree to the updated terms and conditions");
            dto.setPolicyUrl("https://example.com/privacy-policy");
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
            dto.setConsentText("Consent text");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("consentDefinitionId"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail when version is blank")
        void shouldFailWhenVersionIsBlank(String version) {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion(version);
            dto.setConsentText("Consent text");
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
            dto.setConsentText("Consent text");
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("version"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail when consentText is blank")
        void shouldFailWhenConsentTextIsBlank(String text) {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText(text);
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
            dto.setConsentText("Consent text");
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
            dto.setConsentText("Consent text");
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
            dto.setConsentText("Consent text");
            dto.setEffectiveFrom(LocalDateTime.now());
            dto.setEffectiveUntil(null);

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== AddressDtoIn Tests ====================

    @Nested
    @DisplayName("AddressDtoIn Tests")
    class AddressDtoInTests {

        private AddressDtoIn createValidAddressDtoIn() {
            AddressDtoIn dto = new AddressDtoIn();
            dto.setStreet("Main Street 123");
            dto.setCity("Warsaw");
            dto.setPostalCode("00-001");
            dto.setCountry("Poland");
            dto.setAddressType("MAIN");
            return dto;
        }

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            AddressDtoIn dto = createValidAddressDtoIn();

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields")
        void shouldPassValidationWithAllFields() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setId(1L);
            dto.setUserId(2L);
            dto.setPartnershipOpportunityId(3L);
            dto.setState("Mazowieckie");
            dto.setAdditionalInfo("Apartment 5");
            dto.setPrimary(true);

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail when street is blank")
        void shouldFailWhenStreetIsBlank(String street) {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setStreet(street);

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("street"));
        }

        @Test
        @DisplayName("should fail when street exceeds max length")
        void shouldFailWhenStreetExceedsMaxLength() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setStreet("A".repeat(256));

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("street"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail when city is blank")
        void shouldFailWhenCityIsBlank(String city) {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setCity(city);

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("city"));
        }

        @Test
        @DisplayName("should fail when city exceeds max length")
        void shouldFailWhenCityExceedsMaxLength() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setCity("A".repeat(101));

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("city"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail when postalCode is blank")
        void shouldFailWhenPostalCodeIsBlank(String postalCode) {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setPostalCode(postalCode);

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("postalCode"));
        }

        @Test
        @DisplayName("should fail when postalCode exceeds max length")
        void shouldFailWhenPostalCodeExceedsMaxLength() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setPostalCode("A".repeat(21));

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("postalCode"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail when country is blank")
        void shouldFailWhenCountryIsBlank(String country) {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setCountry(country);

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("country"));
        }

        @Test
        @DisplayName("should fail when country exceeds max length")
        void shouldFailWhenCountryExceedsMaxLength() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setCountry("A".repeat(101));

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("country"));
        }

        @Test
        @DisplayName("should accept null state")
        void shouldAcceptNullState() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setState(null);

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when state exceeds max length")
        void shouldFailWhenStateExceedsMaxLength() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setState("A".repeat(101));

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("state"));
        }

        @Test
        @DisplayName("should fail when additionalInfo exceeds max length")
        void shouldFailWhenAdditionalInfoExceedsMaxLength() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setAdditionalInfo("A".repeat(256));

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("additionalInfo"));
        }

        @Test
        @DisplayName("should have default addressType as MAIN")
        void shouldHaveDefaultAddressTypeAsMain() {
            AddressDtoIn dto = new AddressDtoIn();

            assertThat(dto.getAddressType()).isEqualTo("MAIN");
        }

        @Test
        @DisplayName("should have default isPrimary as false")
        void shouldHaveDefaultIsPrimaryAsFalse() {
            AddressDtoIn dto = new AddressDtoIn();

            assertThat(dto.isPrimary()).isFalse();
        }

        @Test
        @DisplayName("should fail when addressType exceeds max length")
        void shouldFailWhenAddressTypeExceedsMaxLength() {
            AddressDtoIn dto = createValidAddressDtoIn();
            dto.setAddressType("A".repeat(51));

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("addressType"));
        }

        @Test
        @DisplayName("should use all-args constructor")
        void shouldUseAllArgsConstructor() {
            AddressDtoIn dto = new AddressDtoIn(
                    1L, 2L, 3L, "Test Street", "Test City",
                    "12345", "Test Country", "Test State",
                    "Additional Info", "DELIVERY", true
            );

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUserId()).isEqualTo(2L);
            assertThat(dto.getPartnershipOpportunityId()).isEqualTo(3L);
            assertThat(dto.getStreet()).isEqualTo("Test Street");
            assertThat(dto.getCity()).isEqualTo("Test City");
            assertThat(dto.getPostalCode()).isEqualTo("12345");
            assertThat(dto.getCountry()).isEqualTo("Test Country");
            assertThat(dto.getState()).isEqualTo("Test State");
            assertThat(dto.getAdditionalInfo()).isEqualTo("Additional Info");
            assertThat(dto.getAddressType()).isEqualTo("DELIVERY");
            assertThat(dto.isPrimary()).isTrue();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle Unicode characters in address")
        void shouldHandleUnicodeCharactersInAddress() {
            AddressDtoIn dto = new AddressDtoIn();
            dto.setStreet("Marszałkowska 123");
            dto.setCity("Łódź");
            dto.setPostalCode("90-001");
            dto.setCountry("Polska");
            dto.setAddressType("MAIN");

            Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle various postal code formats")
        void shouldHandleVariousPostalCodeFormats() {
            String[] postalCodes = {"00-001", "12345", "SW1A 1AA", "10001-1234"};

            for (String postalCode : postalCodes) {
                AddressDtoIn dto = new AddressDtoIn();
                dto.setStreet("Test Street");
                dto.setCity("Test City");
                dto.setPostalCode(postalCode);
                dto.setCountry("Test Country");
                dto.setAddressType("MAIN");

                Set<ConstraintViolation<AddressDtoIn>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle consent version with very long text")
        void shouldHandleConsentVersionWithVeryLongText() {
            ConsentVersionDtoIn dto = new ConsentVersionDtoIn();
            dto.setConsentDefinitionId(1L);
            dto.setVersion("1.0");
            dto.setConsentText("A".repeat(10000)); // Very long consent text
            dto.setEffectiveFrom(LocalDateTime.now());

            Set<ConstraintViolation<ConsentVersionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle consent definition at field boundaries")
        void shouldHandleConsentDefinitionAtFieldBoundaries() {
            ConsentDefinitionDtoIn dto = new ConsentDefinitionDtoIn();
            dto.setConsentType("A".repeat(100)); // Exactly at max
            dto.setName("B".repeat(255)); // Exactly at max
            dto.setDescription("C".repeat(1000)); // Exactly at max
            dto.setRegulationReference("D".repeat(100)); // Exactly at max

            Set<ConstraintViolation<ConsentDefinitionDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }
}
