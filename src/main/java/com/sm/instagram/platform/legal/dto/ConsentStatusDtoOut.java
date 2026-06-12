package com.sm.instagram.platform.legal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsentStatusDtoOut {
    private Boolean newestConsentsAccepted;
    private Integer daysToAcceptNewTerms;
    private List<ConsentRecordSummary> records;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ConsentRecordSummary {
        @Schema(allowableValues = {"COOKIE_POLICY", "TERMS_OF_SERVICE", "PRIVACY_POLICY",
                "SUBSCRIPTION_ACTIVATION_CONSENT", "DATA_RETENTION_POLICY"}, description = "LegalDocumentType enum name")
        private String documentType;
        private Integer acceptedVersion;
        private Integer latestVersion;
        private Boolean isUpToDate;
        private String acceptedAt;
    }
}
