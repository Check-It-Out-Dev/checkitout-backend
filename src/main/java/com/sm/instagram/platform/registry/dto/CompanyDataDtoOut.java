package com.sm.instagram.platform.registry.dto;

import com.sm.instagram.platform.registry.CompanyData;
import com.sm.instagram.platform.registry.CompanyType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDataDtoOut {

    private Long id;
    private String nip;
    private String regon;
    private String krs;
    private String companyName;
    private CompanyType companyType;
    private String legalFormName;
    private Map<String, String> registeredAddress;
    private Map<String, String> correspondenceAddress;
    private List<Map<String, Object>> pkdCodes;
    @Schema(allowableValues = {"ACTIVE", "EXEMPT"}, nullable = true, description = "Normalized VAT status; null if not VAT-registered")
    private String vatStatus;
    private List<String> bankAccounts;
    private String ownerName;
    private Boolean dataVerified;
    private LocalDateTime registryDataFetchedAt;
    private LocalDateTime createdTime;

    public static CompanyDataDtoOut fromEntity(CompanyData entity) {
        if (entity == null) return null;
        return CompanyDataDtoOut.builder()
                .id(entity.getId())
                .nip(entity.getNip())
                .regon(entity.getRegon())
                .krs(entity.getKrs())
                .companyName(entity.getCompanyName())
                .companyType(entity.getCompanyType())
                .legalFormName(entity.getLegalFormName())
                .registeredAddress(entity.getRegisteredAddress())
                .correspondenceAddress(entity.getCorrespondenceAddress())
                .pkdCodes(entity.getPkdCodes())
                .vatStatus(entity.getVatStatus())
                .bankAccounts(entity.getBankAccounts())
                .ownerName(entity.getOwnerName())
                .dataVerified(entity.getDataVerified())
                .registryDataFetchedAt(entity.getRegistryDataFetchedAt())
                .createdTime(entity.getCreatedTime())
                .build();
    }
}
