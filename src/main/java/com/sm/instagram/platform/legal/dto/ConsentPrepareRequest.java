package com.sm.instagram.platform.legal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request to prepare an HMAC-signed consent cookie.
 * Called by the FE before registration to set consent proof cookies
 * that survive OAuth redirects.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConsentPrepareRequest {

    @Schema(allowableValues = {"COOKIE_POLICY", "TERMS_OF_SERVICE", "PRIVACY_POLICY",
            "SUBSCRIPTION_ACTIVATION_CONSENT", "DATA_RETENTION_POLICY"}, description = "LegalDocumentType enum name")
    @NotBlank(message = "{validation.consent.documentType.required}")
    private String documentType;

    @NotNull(message = "{validation.consent.version.required}")
    private Integer version;

    private String documentHash;

    @Valid
    private ConsentProofDtoIn proof;
}
