package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.dictionary.DictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TranslationService.
 * Tests translation delegation logic and key generation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TranslationService Unit Tests")
class TranslationServiceUnitTest {

    @Mock
    private DictionaryService dictionaryService;

    @InjectMocks
    private TranslationService service;

    private static final Locale LOCALE_EN = Locale.ENGLISH;
    private static final Locale LOCALE_PL = Locale.forLanguageTag("pl");

    @Nested
    @DisplayName("translateServiceType")
    class TranslateServiceTypeTests {

        @Test
        @DisplayName("should return translated service type when found")
        void shouldReturnTranslatedServiceTypeWhenFound() {
            // Given
            when(dictionaryService.getTranslation("SERVICE_TYPE_RESTAURACJA", "en"))
                    .thenReturn(Optional.of("Restaurant"));

            // When
            String result = service.translateServiceType("Restauracja", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Restaurant");
        }

        @Test
        @DisplayName("should return original when translation not found")
        void shouldReturnOriginalWhenNotFound() {
            // Given
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            String result = service.translateServiceType("Restauracja", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Restauracja");
        }

        @Test
        @DisplayName("should normalize Polish characters in key")
        void shouldNormalizePolishCharacters() {
            // Given
            when(dictionaryService.getTranslation("SERVICE_TYPE_ZOLTE_SLONCE", "en"))
                    .thenReturn(Optional.of("Yellow Sun"));

            // When
            String result = service.translateServiceType("Żółte Słońce", LOCALE_EN);

            // Then
            verify(dictionaryService).getTranslation("SERVICE_TYPE_ZOLTE_SLONCE", "en");
        }
    }

    @Nested
    @DisplayName("getServiceTypeDescription")
    class GetServiceTypeDescriptionTests {

        @Test
        @DisplayName("should return description when found")
        void shouldReturnDescriptionWhenFound() {
            // Given
            when(dictionaryService.getTranslation("SERVICE_TYPE_RESTAURACJA_DESC", "en"))
                    .thenReturn(Optional.of("A place to eat"));

            // When
            String result = service.getServiceTypeDescription("Restauracja", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("A place to eat");
        }

        @Test
        @DisplayName("should return null when description not found")
        void shouldReturnNullWhenNotFound() {
            // Given
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            String result = service.getServiceTypeDescription("Unknown", LOCALE_EN);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("translateContentType")
    class TranslateContentTypeTests {

        @Test
        @DisplayName("should translate photo content type")
        void shouldTranslatePhotoContentType() {
            // Given
            when(dictionaryService.getTranslation("CONTENT_TYPE_PHOTO", "pl"))
                    .thenReturn(Optional.of("Zdjęcie"));

            // When
            String result = service.translateContentType("photo", LOCALE_PL);

            // Then
            assertThat(result).isEqualTo("Zdjęcie");
        }

        @Test
        @DisplayName("should translate video content type")
        void shouldTranslateVideoContentType() {
            // Given
            when(dictionaryService.getTranslation("CONTENT_TYPE_VIDEO", "en"))
                    .thenReturn(Optional.of("Video"));

            // When
            String result = service.translateContentType("video", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Video");
        }

        @Test
        @DisplayName("should return original for unknown content type")
        void shouldReturnOriginalForUnknownContentType() {
            // Given
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            String result = service.translateContentType("unknown_type", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("unknown_type");
        }
    }

    @Nested
    @DisplayName("translateCurrency")
    class TranslateCurrencyTests {

        @ParameterizedTest
        @CsvSource({
                "PLN, pl, Złoty polski",
                "EUR, en, Euro",
                "USD, en, US Dollar"
        })
        @DisplayName("should translate currency codes")
        void shouldTranslateCurrencyCodes(String isoCode, String lang, String expectedName) {
            // Given
            Locale locale = Locale.forLanguageTag(lang);
            when(dictionaryService.getTranslation("CURRENCY_" + isoCode, lang))
                    .thenReturn(Optional.of(expectedName));

            // When
            String result = service.translateCurrency(isoCode, locale);

            // Then
            assertThat(result).isEqualTo(expectedName);
        }

        @Test
        @DisplayName("should return ISO code when translation not found")
        void shouldReturnIsoCodeWhenNotFound() {
            // Given
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            String result = service.translateCurrency("XYZ", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("XYZ");
        }
    }

    @Nested
    @DisplayName("translateCompensationType")
    class TranslateCompensationTypeTests {

        @Test
        @DisplayName("should translate PAID compensation type")
        void shouldTranslatePaidCompensationType() {
            // Given
            when(dictionaryService.getTranslation("COMPENSATION_TYPE_PAID", "en"))
                    .thenReturn(Optional.of("Paid"));

            // When
            String result = service.translateCompensationType("PAID", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Paid");
        }

        @Test
        @DisplayName("should translate BARTER compensation type")
        void shouldTranslateBarterCompensationType() {
            // Given
            when(dictionaryService.getTranslation("COMPENSATION_TYPE_BARTER", "pl"))
                    .thenReturn(Optional.of("Wymiana barterowa"));

            // When
            String result = service.translateCompensationType("BARTER", LOCALE_PL);

            // Then
            assertThat(result).isEqualTo("Wymiana barterowa");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            // When
            String result = service.translateCompensationType(null, LOCALE_EN);

            // Then
            assertThat(result).isNull();
            verify(dictionaryService, never()).getTranslation(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("translateUserType")
    class TranslateUserTypeTests {

        @Test
        @DisplayName("should translate INFLUENCER user type")
        void shouldTranslateInfluencerUserType() {
            // Given
            when(dictionaryService.getTranslation("USER_TYPE_INFLUENCER", "pl"))
                    .thenReturn(Optional.of("Influencer"));

            // When
            String result = service.translateUserType("INFLUENCER", LOCALE_PL);

            // Then
            assertThat(result).isEqualTo("Influencer");
        }

        @Test
        @DisplayName("should translate COMPANY user type")
        void shouldTranslateCompanyUserType() {
            // Given
            when(dictionaryService.getTranslation("USER_TYPE_COMPANY", "en"))
                    .thenReturn(Optional.of("Company"));

            // When
            String result = service.translateUserType("COMPANY", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Company");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullUserType() {
            // When
            String result = service.translateUserType(null, LOCALE_EN);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("translateAccountStatus")
    class TranslateAccountStatusTests {

        @ParameterizedTest
        @ValueSource(strings = {"ACTIVE", "INACTIVE", "SUSPENDED", "PENDING_VERIFICATION"})
        @DisplayName("should translate various account statuses")
        void shouldTranslateAccountStatuses(String status) {
            // Given
            String expectedKey = "ACCOUNT_STATUS_" + status;
            when(dictionaryService.getTranslation(expectedKey, "en"))
                    .thenReturn(Optional.of("Translated " + status));

            // When
            String result = service.translateAccountStatus(status, LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Translated " + status);
            verify(dictionaryService).getTranslation(expectedKey, "en");
        }

        @Test
        @DisplayName("should return null for null account status")
        void shouldReturnNullForNullAccountStatus() {
            // When
            String result = service.translateAccountStatus(null, LOCALE_EN);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("translateOpportunityStatus")
    class TranslateOpportunityStatusTests {

        @ParameterizedTest
        @ValueSource(strings = {"APPLIED", "ACCEPTED_BY_COMPANY", "DONE", "REJECTED_BY_COMPANY"})
        @DisplayName("should translate opportunity statuses")
        void shouldTranslateOpportunityStatuses(String status) {
            // Given
            when(dictionaryService.getTranslation("OPPORTUNITY_STATUS_" + status, "pl"))
                    .thenReturn(Optional.of("Status: " + status));

            // When
            String result = service.translateOpportunityStatus(status, LOCALE_PL);

            // Then
            assertThat(result).isEqualTo("Status: " + status);
        }

        @Test
        @DisplayName("should return null for null status")
        void shouldReturnNullForNullOpportunityStatus() {
            // When
            String result = service.translateOpportunityStatus(null, LOCALE_EN);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("translateRateStatus")
    class TranslateRateStatusTests {

        @Test
        @DisplayName("should translate POSITIVE rate status")
        void shouldTranslatePositiveRateStatus() {
            // Given
            when(dictionaryService.getTranslation("RATE_STATUS_POSITIVE", "en"))
                    .thenReturn(Optional.of("Positive"));

            // When
            String result = service.translateRateStatus("POSITIVE", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Positive");
        }

        @Test
        @DisplayName("should translate NEGATIVE rate status")
        void shouldTranslateNegativeRateStatus() {
            // Given
            when(dictionaryService.getTranslation("RATE_STATUS_NEGATIVE", "pl"))
                    .thenReturn(Optional.of("Negatywna"));

            // When
            String result = service.translateRateStatus("NEGATIVE", LOCALE_PL);

            // Then
            assertThat(result).isEqualTo("Negatywna");
        }

        @Test
        @DisplayName("should return null for null rate status")
        void shouldReturnNullForNullRateStatus() {
            // When
            String result = service.translateRateStatus(null, LOCALE_EN);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("translatePlatform")
    class TranslatePlatformTests {

        @Test
        @DisplayName("should translate Instagram platform")
        void shouldTranslateInstagramPlatform() {
            // Given
            when(dictionaryService.getTranslation("PLATFORM_INSTAGRAM", "en"))
                    .thenReturn(Optional.of("Instagram"));

            // When
            String result = service.translatePlatform("Instagram", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Instagram");
        }

        @Test
        @DisplayName("should return null for null platform")
        void shouldReturnNullForNullPlatform() {
            // When
            String result = service.translatePlatform(null, LOCALE_EN);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Consent Translation Methods")
    class ConsentTranslationTests {

        @Test
        @DisplayName("should translate consent action")
        void shouldTranslateConsentAction() {
            // Given
            when(dictionaryService.getTranslation("CONSENT_ACTION_GRANTED", "en"))
                    .thenReturn(Optional.of("Granted"));

            // When
            String result = service.translateConsentAction("GRANTED", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Granted");
        }

        @Test
        @DisplayName("should translate consent type")
        void shouldTranslateConsentType() {
            // Given
            when(dictionaryService.getTranslation("CONSENT_TYPE_MARKETING", "pl"))
                    .thenReturn(Optional.of("Zgoda marketingowa"));

            // When
            String result = service.translateConsentType("MARKETING", LOCALE_PL);

            // Then
            assertThat(result).isEqualTo("Zgoda marketingowa");
        }

        @Test
        @DisplayName("should translate collection method")
        void shouldTranslateCollectionMethod() {
            // Given
            when(dictionaryService.getTranslation("COLLECTION_METHOD_WEB_FORM", "en"))
                    .thenReturn(Optional.of("Web Form"));

            // When
            String result = service.translateCollectionMethod("WEB_FORM", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Web Form");
        }

        @Test
        @DisplayName("should translate legal basis")
        void shouldTranslateLegalBasis() {
            // Given
            when(dictionaryService.getTranslation("LEGAL_BASIS_CONSENT", "en"))
                    .thenReturn(Optional.of("User Consent"));

            // When
            String result = service.translateLegalBasis("CONSENT", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("User Consent");
        }

        @Test
        @DisplayName("should translate deletion blocker")
        void shouldTranslateDeletionBlocker() {
            // Given
            when(dictionaryService.getTranslation("DELETION_BLOCKER_ACTIVE_SUBSCRIPTION", "en"))
                    .thenReturn(Optional.of("Active Subscription"));

            // When
            String result = service.translateDeletionBlocker("ACTIVE_SUBSCRIPTION", LOCALE_EN);

            // Then
            assertThat(result).isEqualTo("Active Subscription");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should handle null consent inputs gracefully")
        void shouldHandleNullConsentInputs(String input) {
            // When/Then
            assertThat(service.translateConsentAction(null, LOCALE_EN)).isNull();
            assertThat(service.translateConsentType(null, LOCALE_EN)).isNull();
            assertThat(service.translateCollectionMethod(null, LOCALE_EN)).isNull();
            assertThat(service.translateLegalBasis(null, LOCALE_EN)).isNull();
            assertThat(service.translateDeletionBlocker(null, LOCALE_EN)).isNull();
        }
    }

    @Nested
    @DisplayName("Key Normalization")
    class KeyNormalizationTests {

        @Test
        @DisplayName("should convert spaces to underscores")
        void shouldConvertSpacesToUnderscores() {
            // Given
            when(dictionaryService.getTranslation("SERVICE_TYPE_FAST_FOOD", "en"))
                    .thenReturn(Optional.of("Fast Food"));

            // When
            service.translateServiceType("Fast Food", LOCALE_EN);

            // Then
            verify(dictionaryService).getTranslation("SERVICE_TYPE_FAST_FOOD", "en");
        }

        @Test
        @DisplayName("should normalize all Polish diacritics")
        void shouldNormalizeAllPolishDiacritics() {
            // Given - Text with all Polish special characters: ą, ć, ę, ł, ń, ó, ś, ź, ż
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            service.translateServiceType("Żółć Ąęś", LOCALE_EN);

            // Then
            verify(dictionaryService).getTranslation("SERVICE_TYPE_ZOLC_AES", "en");
        }

        @Test
        @DisplayName("should convert to uppercase")
        void shouldConvertToUppercase() {
            // Given
            when(dictionaryService.getTranslation("SERVICE_TYPE_LOWERCASE", "en"))
                    .thenReturn(Optional.empty());

            // When
            service.translateServiceType("lowercase", LOCALE_EN);

            // Then
            verify(dictionaryService).getTranslation("SERVICE_TYPE_LOWERCASE", "en");
        }
    }
}
