package com.sm.instagram.platform.consent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserConsentDtoIn {

    @NotBlank(message = "{validation.userConsent.consentType.required}")
    private String consentType;

    @NotNull(message = "{validation.userConsent.consentGiven.required}")
    private Boolean consentGiven;

    private String userAgent;
    private String collectionMethod = "web_form";
}
