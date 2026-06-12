package com.sm.instagram.platform.registry.adapter.bialista;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Jackson mapping for Biała Lista (White List) API response.
 * Endpoint: GET https://wl-api.mf.gov.pl/api/search/nip/{nip}?date={yyyy-MM-dd}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BialaListaResponse {

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Subject subject;
        private String requestId;
        private String requestDateTime;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subject {
        private String name;
        private String nip;
        private String regon;

        @JsonProperty("statusVat")
        private String statusVat;

        private String krs;

        @JsonProperty("residenceAddress")
        private String residenceAddress;

        @JsonProperty("workingAddress")
        private String workingAddress;

        @JsonProperty("accountNumbers")
        private List<String> accountNumbers;

        @JsonProperty("registrationLegalDate")
        private String registrationLegalDate;

        @JsonProperty("registrationDenialDate")
        private String registrationDenialDate;

        @JsonProperty("registrationDenialBasis")
        private String registrationDenialBasis;

        @JsonProperty("restorationDate")
        private String restorationDate;

        @JsonProperty("restorationBasis")
        private String restorationBasis;

        @JsonProperty("removalDate")
        private String removalDate;

        @JsonProperty("removalBasis")
        private String removalBasis;

        @JsonProperty("hasVirtualAccounts")
        private Boolean hasVirtualAccounts;
    }
}
