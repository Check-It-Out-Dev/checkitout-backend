package com.sm.instagram.platform.legal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class AnonymousConsentDtoIn {

    @NotBlank(message = "{validation.consent.documentName.required}")
    private String documentName;

    private String userAgent;

    @NotBlank(message = "{validation.consent.language.required}")
    private String language;

    private Boolean isTrusted;

    private Map<String, Boolean> categories;
}
