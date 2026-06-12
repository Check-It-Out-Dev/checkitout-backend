package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsentVersionDtoOut {
    private Long id;
    private Long consentDefinitionId;
    private String version;
    private String consentText;
    private String policyUrl;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private LocalDateTime createdAt;
}
