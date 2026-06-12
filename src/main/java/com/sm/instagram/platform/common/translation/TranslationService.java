package com.sm.instagram.platform.common.translation;

import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Service for translating non-enum entities like service types, content types, and currencies.
 * Uses the dictionary system for translations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final DictionaryService dictionaryService;

    /**
     * Translate a service type name
     *
     * @param serviceTypeName The original service type name (e.g., "Restauracja")
     * @param locale          The target locale
     * @return Translated name or original if translation not found
     */
    public String translateServiceType(String serviceTypeName, Locale locale) {
        log.debug("GDPR: Operation=translateServiceType, FirebaseUID=TRANSLATION_SERVICE, DataAccessed=translation_data, Purpose=localization");
        String key = generateServiceTypeKey(serviceTypeName);
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(serviceTypeName);
    }

    /**
     * Get service type description
     *
     * @param serviceTypeName The original service type name
     * @param locale          The target locale
     * @return Translated description or original description if not found
     */
    public String getServiceTypeDescription(String serviceTypeName, Locale locale) {
        String key = generateServiceTypeKey(serviceTypeName) + "_DESC";
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(null); // Return null if not found, so we can fallback to original
    }

    /**
     * Translate a content type name
     *
     * @param contentTypeName The original content type name (e.g., "photo")
     * @param locale          The target locale
     * @return Translated name or original if translation not found
     */
    public String translateContentType(String contentTypeName, Locale locale) {
        log.debug("GDPR: Operation=translateContentType, FirebaseUID=TRANSLATION_SERVICE, DataAccessed=translation_data, Purpose=localization");
        String key = "CONTENT_TYPE_" + contentTypeName.toUpperCase();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(contentTypeName);
    }

    /**
     * Translate a currency ISO code to full name
     *
     * @param isoCode The ISO code (e.g., "EUR", "PLN")
     * @param locale  The target locale
     * @return Translated currency name or ISO code if translation not found
     */
    public String translateCurrency(String isoCode, Locale locale) {
        log.debug("GDPR: Operation=translateCurrency, FirebaseUID=TRANSLATION_SERVICE, DataAccessed=translation_data, Purpose=localization");
        String key = "CURRENCY_" + isoCode.toUpperCase();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(isoCode);
    }

    /**
     * Translate a service category
     *
     * @param categoryName The original category name (e.g., "Gastronomia")
     * @param locale       The target locale
     * @return Translated category name or original if translation not found
     */
    public String translateServiceCategory(String categoryName, Locale locale) {
        String key = "SERVICE_CATEGORY_" + normalizeKey(categoryName);
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(categoryName);
    }

    /**
     * Translate compensation type enum
     *
     * @param compensationType The compensation type enum value
     * @param locale           The target locale
     * @return Translated compensation type or original if translation not found
     */
    public String translateCompensationType(String compensationType, Locale locale) {
        if (compensationType == null) return null;
        String key = "COMPENSATION_TYPE_" + normalizeKey(compensationType.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(compensationType);
    }

    /**
     * Translate user type enum
     *
     * @param userType The user type enum value
     * @param locale   The target locale
     * @return Translated user type or original if translation not found
     */
    public String translateUserType(String userType, Locale locale) {
        if (userType == null) return null;
        String key = "USER_TYPE_" + normalizeKey(userType.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(userType);
    }

    /**
     * Translate account status enum
     *
     * @param accountStatus The account status enum value
     * @param locale        The target locale
     * @return Translated account status or original if translation not found
     */
    public String translateAccountStatus(String accountStatus, Locale locale) {
        if (accountStatus == null) return null;
        String key = "ACCOUNT_STATUS_" + normalizeKey(accountStatus.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(accountStatus);
    }

    /**
     * Translate opportunity status enum
     *
     * @param opportunityStatus The opportunity status enum value
     * @param locale            The target locale
     * @return Translated opportunity status or original if translation not found
     */
    public String translateOpportunityStatus(String opportunityStatus, Locale locale) {
        if (opportunityStatus == null) return null;
        String key = "OPPORTUNITY_STATUS_" + normalizeKey(opportunityStatus.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(opportunityStatus);
    }

    /**
     * Translate rate status enum
     *
     * @param rateStatus The rate status enum value
     * @param locale     The target locale
     * @return Translated rate status or original if translation not found
     */
    public String translateRateStatus(String rateStatus, Locale locale) {
        if (rateStatus == null) return null;
        String key = "RATE_STATUS_" + normalizeKey(rateStatus.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(rateStatus);
    }

    /**
     * Translate platform name
     *
     * @param platformName The platform name
     * @param locale       The target locale
     * @return Translated platform name or original if translation not found
     */
    public String translatePlatform(String platformName, Locale locale) {
        if (platformName == null) return null;
        String key = "PLATFORM_" + normalizeKey(platformName);
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(platformName);
    }

    // ============================================================================
    // CONSENT-RELATED TRANSLATION METHODS
    // ============================================================================

    /**
     * Translate consent action enum
     *
     * @param consentAction The consent action enum value (GRANTED, WITHDRAWN, UPDATED)
     * @param locale        The target locale
     * @return Translated consent action or original if translation not found
     */
    public String translateConsentAction(String consentAction, Locale locale) {
        if (consentAction == null) return null;
        String key = "CONSENT_ACTION_" + normalizeKey(consentAction.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(consentAction);
    }

    /**
     * Translate consent type
     *
     * @param consentType The consent type (marketing, analytics, cookies)
     * @param locale      The target locale
     * @return Translated consent type or original if translation not found
     */
    public String translateConsentType(String consentType, Locale locale) {
        if (consentType == null) return null;
        String key = "CONSENT_TYPE_" + normalizeKey(consentType.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(consentType);
    }

    /**
     * Translate collection method
     *
     * @param collectionMethod The collection method (web_form, api, import)
     * @param locale           The target locale
     * @return Translated collection method or original if translation not found
     */
    public String translateCollectionMethod(String collectionMethod, Locale locale) {
        if (collectionMethod == null) return null;
        String key = "COLLECTION_METHOD_" + normalizeKey(collectionMethod.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(collectionMethod);
    }

    /**
     * Translate legal basis
     *
     * @param legalBasis The legal basis (consent, legitimate_interest)
     * @param locale     The target locale
     * @return Translated legal basis or original if translation not found
     */
    public String translateLegalBasis(String legalBasis, Locale locale) {
        if (legalBasis == null) return null;
        String key = "LEGAL_BASIS_" + normalizeKey(legalBasis.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(legalBasis);
    }

    /**
     * Translate deletion blocker reason
     *
     * @param blockerReason The deletion blocker reason
     * @param locale        The target locale
     * @return Translated deletion blocker or original if translation not found
     */
    public String translateDeletionBlocker(String blockerReason, Locale locale) {
        if (blockerReason == null) return null;
        String key = "DELETION_BLOCKER_" + normalizeKey(blockerReason.toUpperCase());
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(blockerReason);
    }

    /**
     * Helper method to generate standardized dictionary keys for service types
     */
    private String generateServiceTypeKey(String serviceTypeName) {
        return "SERVICE_TYPE_" + normalizeKey(serviceTypeName);
    }

    /**
     * Normalize string to create a valid dictionary key
     * Converts to uppercase and replaces spaces with underscores
     */
    private String normalizeKey(String text) {
        return text.toUpperCase()
                .replace(" ", "_")
                .replace("Ż", "Z")
                .replace("Ó", "O")
                .replace("Ł", "L")
                .replace("Ć", "C")
                .replace("Ę", "E")
                .replace("Ś", "S")
                .replace("Ą", "A")
                .replace("Ź", "Z")
                .replace("Ń", "N");
    }
}
