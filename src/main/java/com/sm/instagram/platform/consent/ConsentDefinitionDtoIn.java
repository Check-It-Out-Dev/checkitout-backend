package com.sm.instagram.platform.consent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsentDefinitionDtoIn {

    @Schema(description = "Consent type identifier. Must be uppercase with underscores (e.g. TERMS_OF_SERVICE, PRIVACY_POLICY, MARKETING_EMAILS, DATA_PROCESSING).",
            example = "TERMS_OF_SERVICE", pattern = "^[A-Z_]+$")
    @NotBlank(message = "{validation.consent.consentType.required}")
    @Size(max = 100, message = "{validation.consent.consentType.size}")
    private String consentType;

    @NotBlank(message = "{validation.consent.name.required}")
    @Size(max = 255, message = "{validation.consent.name.size}")
    private String name;

    @Size(max = 1000, message = "{validation.consent.description.size}")
    private String description;

    @Size(max = 100, message = "{validation.consent.regulationReference.size}")
    private String regulationReference;

    private Boolean isActive = true;
}
