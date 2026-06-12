package com.sm.instagram.platform.consent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ConsentVersionDtoIn {

    @NotNull(message = "{validation.consentVersion.consentDefinitionId.required}")
    private Long consentDefinitionId;

    @NotBlank(message = "{validation.consentVersion.version.required}")
    @Size(max = 50, message = "{validation.consentVersion.version.size}")
    private String version;

    @NotBlank(message = "{validation.consentVersion.consentText.required}")
    private String consentText;

    @Size(max = 500, message = "{validation.consentVersion.policyUrl.size}")
    private String policyUrl;

    @NotNull(message = "{validation.consentVersion.effectiveFrom.required}")
    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveUntil;
}
