package com.sm.instagram.platform.registry.dto;

import com.sm.instagram.platform.registry.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NipLookupResponse {

    private String nip;
    private String regon;
    private String krs;
    private String companyName;
    private CompanyType companyType;
    private String legalFormName;

    // Address
    private String street;
    private String buildingNumber;
    private String apartmentNumber;
    private String city;
    private String postalCode;
    private String voivodeship;

    // PKD
    private String pkdMainCode;
    private String pkdMainDescription;
    private List<Map<String, Object>> pkdCodes;

    // VAT status
    private String vatStatus;
    private List<String> bankAccounts;

    // JDG-specific
    private String ownerName;

    // Status flags
    private boolean companyActive;
    private boolean companySuspended;

    // Data sources
    private boolean sourceGus;
    private boolean sourceVat;
    private boolean sourceCeidg;
}
