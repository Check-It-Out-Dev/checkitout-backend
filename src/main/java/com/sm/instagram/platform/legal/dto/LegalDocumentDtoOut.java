package com.sm.instagram.platform.legal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegalDocumentDtoOut {
    // Reference the shared LegalDocumentType enum component so the FE
    // generator reuses one TS enum across DTOs (docs-only; runtime String).
    @Schema(implementation = com.sm.instagram.platform.legal.LegalDocumentType.class,
            description = "LegalDocumentType enum name")
    private String type;
    private Integer version;
    private String contentHash;
    private String downloadUrl;
    private String effectiveFrom;
}
