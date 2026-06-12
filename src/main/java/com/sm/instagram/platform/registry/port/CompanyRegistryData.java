package com.sm.instagram.platform.registry.port;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Unified DTO returned by {@link CompanyRegistryPort} implementations.
 * Contains all company identification and address data from the registry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRegistryData {

    private boolean found;

    private String nip;
    private String regon;
    private String krs;
    private String companyName;
    private String shortName;

    /** GUS basic legal form symbol (e.g., "9" = sole proprietor, "1" = legal person) */
    private String basicLegalFormCode;

    /** GUS specific legal form symbol (e.g., "117" = sp. z o.o., "116" = S.A.) */
    private String specificLegalFormCode;

    private String legalFormName;

    /** "CEIDG" or "REJESTR PRZEDSIEBIORCOW" (KRS) */
    private String registryType;

    private String street;
    private String buildingNumber;
    private String apartmentNumber;
    private String city;
    private String postalCode;
    private String voivodeship;

    private String activityStartDate;
    private String activityEndDate;
    private String suspensionDate;
    private String bankruptcyDate;

    /** Primary PKD code */
    private String pkdMainCode;
    private String pkdMainDescription;

    /** All PKD codes: list of {code, description, isPrimary} */
    private List<Map<String, Object>> pkdCodes;

    /** Full raw response from the registry for audit */
    private Map<String, Object> rawResponse;
}
