package com.sm.instagram.platform.legal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsentRecordAdminDtoOut {
    private Long id;
    private Long userId;
    @Schema(allowableValues = {"COOKIE_POLICY", "TERMS_OF_SERVICE", "PRIVACY_POLICY",
            "SUBSCRIPTION_ACTIVATION_CONSENT", "DATA_RETENTION_POLICY"}, description = "LegalDocumentType enum name")
    private String documentType;
    private Integer documentVersion;
    private LocalDateTime timestamp;
    @Schema(allowableValues = {"REGISTRATION", "OAUTH_REGISTRATION", "SOCIAL_REGISTRATION", "LOGIN_PROMPT",
            "COOKIE_BANNER", "SETTINGS", "ACCOUNT_DELETION", "COMPANY_DATA_FORM", "SUBSCRIPTION_PURCHASE"},
            description = "ConsentSource enum name")
    private String source;
    private Boolean isTrusted;
    private String ipAddress;
    private String userAgent;
    private boolean wasAnonymous;
}
