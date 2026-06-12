package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserCurrentConsentDtoOut {
    private Long userId;
    private ConsentTypeDtoOut consentType;    // Translated Consent Type
    private String name;
    private String description;
    private Boolean consentGiven;
    private String currentVersion;
    private String consentText;
    private String policyUrl;
    private LocalDateTime grantedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime lastUpdated;
}
