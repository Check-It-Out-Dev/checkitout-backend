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

    /** Hash of the document the visitor actually agreed to; it is signed into the cookie. */
    @NotBlank(message = "{validation.consent.documentHash.required}")
    private String documentHash;

    /**
     * {@code @NotNull} as well as {@code @Valid}: cascading validation has nothing to say about a
     * null, so `{"proof": null}` passed the boundary and the service dereferenced it. Schemathesis
     * found it on the first authenticated run.
     */
    @NotNull(message = "{validation.consent.proof.required}")
    @Valid
    private ConsentProofDtoIn proof;
}
