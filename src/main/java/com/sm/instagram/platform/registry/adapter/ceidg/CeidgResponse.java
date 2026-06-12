package com.sm.instagram.platform.registry.adapter.ceidg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Jackson mapping for CEIDG API v2 response.
 * Endpoint: GET https://dane.biznes.gov.pl/api/ceidg/v2/firmy?nip={nip}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CeidgResponse {

    /** CEIDG v2 wraps results in a "firmy" array */
    @JsonProperty("firmy")
    private List<Firma> firmy;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Firma {
        private String id;
        private String nazwa;
        private String nip;
        private String regon;
        private String status;

        @JsonProperty("dataRozpoczeciaDzialalnosci")
        private String startDate;

        @JsonProperty("dataZakonczeniaDzialalnosci")
        private String endDate;

        @JsonProperty("dataZawieszeniaDzialalnosci")
        private String suspensionDate;

        @JsonProperty("dataWznowieniaDzialalnosci")
        private String resumptionDate;

        private Wlasciciel wlasciciel;
        private Adres adresGlownegoMiejscaWykonywaniaDzialalnosci;
        private Adres adresDoDoreczen;

        @JsonProperty("pkd")
        private List<Pkd> pkd;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Wlasciciel {
        private String imie;
        private String nazwisko;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Adres {
        private String ulica;
        private String budynek;
        private String lokal;
        private String miasto;
        private String kodPocztowy;
        private String kraj;
        private String wojewodztwo;
        private String powiat;
        private String gmina;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pkd {
        private String kod;
        private String nazwa;
        private Boolean przewazajace;
    }
}
