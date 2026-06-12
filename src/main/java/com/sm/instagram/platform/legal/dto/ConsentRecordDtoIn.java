package com.sm.instagram.platform.legal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConsentRecordDtoIn {

    @Schema(allowableValues = {"COOKIE_POLICY", "TERMS_OF_SERVICE", "PRIVACY_POLICY",
            "SUBSCRIPTION_ACTIVATION_CONSENT", "DATA_RETENTION_POLICY"}, description = "LegalDocumentType enum name")
    @NotBlank(message = "{validation.consent.documentType.required}")
    private String documentType;

    @Schema(allowableValues = {"GRANTED", "WITHDRAWN", "UPDATED"}, description = "ConsentAction enum name")
    @NotBlank(message = "{validation.consent.action.required}")
    private String action;

    @Valid
    private ConsentProofDtoIn proof;
}
