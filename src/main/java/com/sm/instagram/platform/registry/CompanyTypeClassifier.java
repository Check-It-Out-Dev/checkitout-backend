package com.sm.instagram.platform.registry;

import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import org.springframework.stereotype.Component;

/**
 * Classifies company type based on GUS BIR1 legal form codes.
 * <p>
 * GUS basic legal form codes (praw_podstawowaFormaPrawna_Symbol):
 * <ul>
 *   <li>"9" = sole proprietor (JDG / osoba fizyczna prowadząca działalność)</li>
 *   <li>"1" = legal person (osoba prawna)</li>
 *   <li>"2" = organizational unit without legal personality</li>
 * </ul>
 * <p>
 * GUS specific legal form codes (praw_szczegolnaFormaPrawna_Symbol):
 * <ul>
 *   <li>"099" = JDG (sole proprietorship)</li>
 *   <li>"117" = sp. z o.o.</li>
 *   <li>"116" = S.A.</li>
 *   <li>"120" = sp. komandytowa</li>
 *   <li>"115" = sp. jawna</li>
 *   <li>"118" = sp. komandytowo-akcyjna</li>
 *   <li>"019" = spółka cywilna (civil partnership)</li>
 * </ul>
 */
@Component
public class CompanyTypeClassifier {

    /**
     * Classifies the company type from GUS BIR1 data.
     *
     * @param data company registry data from GUS
     * @return classified company type
     */
    public CompanyType classify(CompanyRegistryData data) {
        if (data == null) return CompanyType.OTHER_KRS;

        // First check basic form — "9" or entity type "F" means JDG
        String basicForm = data.getBasicLegalFormCode();
        if ("9".equals(basicForm)) {
            return CompanyType.JDG;
        }

        // Check specific form code for KRS entities
        String specificForm = data.getSpecificLegalFormCode();
        if (specificForm != null) {
            return switch (specificForm) {
                case "099" -> CompanyType.JDG;
                case "117" -> CompanyType.SP_ZOO;
                case "116" -> CompanyType.SA;
                case "120", "118" -> CompanyType.SP_K;
                case "115" -> CompanyType.SP_J;
                default -> CompanyType.OTHER_KRS;
            };
        }

        // Fallback: check registry type name
        String registryType = data.getRegistryType();
        if (registryType != null && registryType.toUpperCase().contains("CEIDG")) {
            return CompanyType.JDG;
        }

        return CompanyType.OTHER_KRS;
    }

    /**
     * Checks if a company is active based on its registry data.
     * A company is considered inactive if it has an end date, suspension date,
     * or bankruptcy date set.
     */
    public boolean isActive(CompanyRegistryData data) {
        if (data == null) return false;
        return isNullOrEmpty(data.getActivityEndDate())
                && isNullOrEmpty(data.getBankruptcyDate());
    }

    /**
     * Checks if a company is suspended but not permanently closed.
     */
    public boolean isSuspended(CompanyRegistryData data) {
        if (data == null) return false;
        return !isNullOrEmpty(data.getSuspensionDate())
                && isNullOrEmpty(data.getActivityEndDate());
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.isBlank();
    }
}
