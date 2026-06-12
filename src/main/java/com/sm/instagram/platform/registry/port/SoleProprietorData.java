package com.sm.instagram.platform.registry.port;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO returned by {@link SoleProprietorRegistryPort} implementations.
 * Contains JDG-specific data from CEIDG.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoleProprietorData {

    private boolean found;

    private String ownerFirstName;
    private String ownerLastName;
    private String businessName;
    private String nip;

    private String street;
    private String buildingNumber;
    private String apartmentNumber;
    private String city;
    private String postalCode;
    private String voivodeship;

    /** Business status: AKTYWNY, ZAWIESZONY, WYKRESLONY, etc. */
    private String status;

    private String startDate;
    private String endDate;

    /** PKD codes from CEIDG */
    private List<Map<String, Object>> pkdCodes;

    public String getOwnerFullName() {
        if (ownerFirstName == null && ownerLastName == null) return null;
        return ((ownerFirstName != null ? ownerFirstName : "") + " " +
                (ownerLastName != null ? ownerLastName : "")).trim();
    }
}
