package com.sm.instagram.platform.registry.dto;

import com.sm.instagram.platform.registry.CompanyType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDataConfirmResponse {

    private Long companyDataId;
    private String nip;
    private String companyName;
    private CompanyType companyType;
    private boolean activated;
    @Schema(allowableValues = {"INACTIVE", "IN_VALIDATION", "ACTIVE", "BANNED",
            "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS", "TO_BE_DELETED", "DELETED"}, description = "AccountStatus enum name")
    private String accountStatus;
    private String message;
}
