package com.sm.instagram.platform.unit.registry;

import com.sm.instagram.platform.registry.CompanyType;
import com.sm.instagram.platform.registry.CompanyTypeClassifier;
import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CompanyTypeClassifier.
 * Tests GUS BIR1 legal form code to CompanyType mapping.
 */
@DisplayName("CompanyTypeClassifier")
class CompanyTypeClassifierUnitTest {

    private CompanyTypeClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new CompanyTypeClassifier();
    }

    @Nested
    @DisplayName("classify")
    class Classify {

        @Test
        @DisplayName("should classify basic form code '9' as JDG")
        void shouldClassifyBasicForm9AsJdg() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .basicLegalFormCode("9")
                    .build();

            assertThat(classifier.classify(data)).isEqualTo(CompanyType.JDG);
        }

        @Test
        @DisplayName("should classify specific form code '099' as JDG")
        void shouldClassifySpecificForm099AsJdg() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .basicLegalFormCode("1")
                    .specificLegalFormCode("099")
                    .build();

            assertThat(classifier.classify(data)).isEqualTo(CompanyType.JDG);
        }

        @ParameterizedTest
        @CsvSource({
                "117, SP_ZOO",
                "116, SA",
                "120, SP_K",
                "118, SP_K",
                "115, SP_J"
        })
        @DisplayName("should classify KRS entity specific form codes correctly")
        void shouldClassifyKrsEntities(String specificFormCode, CompanyType expectedType) {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .basicLegalFormCode("1")
                    .specificLegalFormCode(specificFormCode)
                    .build();

            assertThat(classifier.classify(data)).isEqualTo(expectedType);
        }

        @Test
        @DisplayName("should fallback to OTHER_KRS for unknown specific form code")
        void shouldFallbackToOtherKrsForUnknownCode() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .basicLegalFormCode("1")
                    .specificLegalFormCode("999")
                    .build();

            assertThat(classifier.classify(data)).isEqualTo(CompanyType.OTHER_KRS);
        }

        @Test
        @DisplayName("should classify CEIDG registry type as JDG")
        void shouldClassifyCeidgRegistryTypeAsJdg() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .basicLegalFormCode("2")
                    .registryType("CEIDG")
                    .build();

            assertThat(classifier.classify(data)).isEqualTo(CompanyType.JDG);
        }

        @Test
        @DisplayName("should classify CEIDG registry type case-insensitively")
        void shouldClassifyCeidgCaseInsensitively() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .registryType("ceidg")
                    .build();

            assertThat(classifier.classify(data)).isEqualTo(CompanyType.JDG);
        }

        @Test
        @DisplayName("should return OTHER_KRS for null data")
        void shouldReturnOtherKrsForNullData() {
            assertThat(classifier.classify(null)).isEqualTo(CompanyType.OTHER_KRS);
        }

        @Test
        @DisplayName("should return OTHER_KRS when no codes or registry type set")
        void shouldReturnOtherKrsWhenNoCodesSet() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .build();

            assertThat(classifier.classify(data)).isEqualTo(CompanyType.OTHER_KRS);
        }

        @Test
        @DisplayName("basic form '9' takes precedence over specific form code")
        void basicFormTakesPrecedenceOverSpecificForm() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .basicLegalFormCode("9")
                    .specificLegalFormCode("117") // sp. z o.o. code
                    .build();

            // Basic form "9" means JDG, regardless of specific form code
            assertThat(classifier.classify(data)).isEqualTo(CompanyType.JDG);
        }
    }

    @Nested
    @DisplayName("isActive")
    class IsActive {

        @Test
        @DisplayName("should return true when no end or bankruptcy date")
        void shouldReturnTrueWhenNoEndOrBankruptcyDate() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .build();

            assertThat(classifier.isActive(data)).isTrue();
        }

        @Test
        @DisplayName("should return false when activity end date is set")
        void shouldReturnFalseWhenActivityEndDateSet() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .activityEndDate("2024-01-01")
                    .build();

            assertThat(classifier.isActive(data)).isFalse();
        }

        @Test
        @DisplayName("should return false when bankruptcy date is set")
        void shouldReturnFalseWhenBankruptcyDateSet() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .bankruptcyDate("2024-06-15")
                    .build();

            assertThat(classifier.isActive(data)).isFalse();
        }

        @Test
        @DisplayName("should return true when only suspension date is set")
        void shouldReturnTrueWhenOnlySuspensionDateSet() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .suspensionDate("2024-03-01")
                    .build();

            // Suspended companies are still "active" (not permanently closed)
            assertThat(classifier.isActive(data)).isTrue();
        }

        @Test
        @DisplayName("should return false for null data")
        void shouldReturnFalseForNullData() {
            assertThat(classifier.isActive(null)).isFalse();
        }

        @Test
        @DisplayName("should treat blank end date as not set")
        void shouldTreatBlankEndDateAsNotSet() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .activityEndDate("   ")
                    .build();

            assertThat(classifier.isActive(data)).isTrue();
        }
    }

    @Nested
    @DisplayName("isSuspended")
    class IsSuspended {

        @Test
        @DisplayName("should return true when suspension date is set and no end date")
        void shouldReturnTrueWhenSuspendedWithoutEnd() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .suspensionDate("2024-03-01")
                    .build();

            assertThat(classifier.isSuspended(data)).isTrue();
        }

        @Test
        @DisplayName("should return false when both suspension and end dates are set")
        void shouldReturnFalseWhenSuspendedAndEnded() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .suspensionDate("2024-03-01")
                    .activityEndDate("2024-06-01")
                    .build();

            assertThat(classifier.isSuspended(data)).isFalse();
        }

        @Test
        @DisplayName("should return false when no suspension date")
        void shouldReturnFalseWhenNotSuspended() {
            CompanyRegistryData data = CompanyRegistryData.builder()
                    .found(true)
                    .build();

            assertThat(classifier.isSuspended(data)).isFalse();
        }

        @Test
        @DisplayName("should return false for null data")
        void shouldReturnFalseForNullData() {
            assertThat(classifier.isSuspended(null)).isFalse();
        }
    }
}
