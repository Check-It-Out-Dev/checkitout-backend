package com.sm.instagram.platform.registry.adapter.gus;

import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import com.sm.instagram.platform.registry.port.CompanyRegistryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GUS BIR 1.1 (REGON Database) adapter — primary company data source.
 * Implements the company registry port using raw SOAP over HTTP.
 * <p>
 * Four-step SOAP flow:
 * <ol>
 *   <li>Zaloguj (login) — authenticate with API key → session ID</li>
 *   <li>DaneSzukajPodmioty (search) — search by NIP → REGON + entity type</li>
 *   <li>DanePobierzPelnyRaport (full report) — full data by REGON</li>
 *   <li>Wyloguj (logout) — release session</li>
 * </ol>
 */
@Slf4j
@Component
public class GusBir1RegistryAdapter implements CompanyRegistryPort {

    private final RestTemplate restTemplate;
    private final RegistryProperties registryProperties;

    public GusBir1RegistryAdapter(
            @Qualifier("gusBir1RestTemplate") RestTemplate restTemplate,
            RegistryProperties registryProperties) {
        this.restTemplate = restTemplate;
        this.registryProperties = registryProperties;
    }

    @Override
    public CompanyRegistryData lookupByNip(String nip) {
        if (!registryProperties.getGus().isEnabled()) {
            log.debug("GUS BIR1 adapter is disabled, returning empty result");
            return CompanyRegistryData.builder().found(false).build();
        }

        String serviceUrl = registryProperties.getGus().getActiveUrl();
        String sessionId = null;

        try {
            // Step 1: Login
            sessionId = login(serviceUrl);
            if (sessionId == null || sessionId.isBlank()) {
                throw new ExternalServiceException("error.registry.gus_login_failed", "GUS_BIR1", "Zaloguj");
            }
            log.debug("GUS BIR1: Login successful, session obtained");

            // Step 2: Search by NIP
            Map<String, String> searchResult = search(serviceUrl, sessionId, nip);
            if (searchResult.isEmpty()) {
                log.info("GUS BIR1: NIP {} not found", maskNip(nip));
                return CompanyRegistryData.builder().found(false).build();
            }

            String regon = searchResult.getOrDefault("Regon", searchResult.get("Regon9"));
            String entityType = searchResult.get("Typ"); // "P" = prawna, "F" = fizyczna

            // Step 3: Full report
            String reportType = determineReportType(entityType);
            Map<String, String> fullReport = fetchFullReport(serviceUrl, sessionId, regon, reportType);

            // Step 3b: PKD codes (separate report)
            List<Map<String, Object>> pkdCodes = fetchPkdCodes(serviceUrl, sessionId, regon, entityType);

            // Merge search + full report + PKD data
            return buildCompanyRegistryData(searchResult, fullReport, nip, pkdCodes);

        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("GUS BIR1 lookup failed for NIP: {}. Error: {}", maskNip(nip), e.getMessage(), e);
            throw new ExternalServiceException("error.registry.service_unavailable", "GUS_BIR1", "lookupByNip", 503);
        } finally {
            // Step 4: Logout (always, even on failure)
            if (sessionId != null) {
                tryLogout(serviceUrl, sessionId);
            }
        }
    }

    private String login(String serviceUrl) {
        String apiKey = registryProperties.getGus().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalServiceException("error.registry.gus_key_missing", "GUS_BIR1", "Zaloguj");
        }

        String envelope = String.format(GusBir1SoapTemplates.ZALOGUJ, apiKey);
        String response = executeSoapCall(serviceUrl, GusBir1SoapTemplates.ACTION_ZALOGUJ, envelope);
        return GusBir1ResponseParser.extractSessionId(response);
    }

    private Map<String, String> search(String serviceUrl, String sessionId, String nip) {
        String envelope = String.format(GusBir1SoapTemplates.DANE_SZUKAJ_PODMIOTY, nip);
        String response = executeSoapCallWithSession(serviceUrl, GusBir1SoapTemplates.ACTION_DANE_SZUKAJ, envelope, sessionId);

        if (GusBir1ResponseParser.isSoapFault(response)) {
            String fault = GusBir1ResponseParser.extractFaultMessage(response);
            log.warn("GUS BIR1 search returned SOAP fault: {}", fault);
            return Map.of();
        }

        return GusBir1ResponseParser.extractSearchResult(response);
    }

    private Map<String, String> fetchFullReport(String serviceUrl, String sessionId, String regon, String reportType) {
        if (regon == null || regon.isBlank()) {
            log.warn("GUS BIR1: Cannot fetch full report without REGON");
            return Map.of();
        }

        String envelope = String.format(GusBir1SoapTemplates.DANE_POBIERZ_PELNY_RAPORT, regon, reportType);
        String response = executeSoapCallWithSession(serviceUrl, GusBir1SoapTemplates.ACTION_DANE_PELNY_RAPORT, envelope, sessionId);

        if (GusBir1ResponseParser.isSoapFault(response)) {
            String fault = GusBir1ResponseParser.extractFaultMessage(response);
            log.warn("GUS BIR1 full report returned SOAP fault: {}", fault);
            return Map.of();
        }

        return GusBir1ResponseParser.extractFullReport(response);
    }

    private List<Map<String, Object>> fetchPkdCodes(String serviceUrl, String sessionId, String regon, String entityType) {
        try {
            String pkdReportType = "F".equalsIgnoreCase(entityType)
                    ? GusBir1SoapTemplates.REPORT_OSOBA_FIZYCZNA_PKD
                    : GusBir1SoapTemplates.REPORT_OSOBA_PRAWNA_PKD;

            String envelope = String.format(GusBir1SoapTemplates.DANE_POBIERZ_PELNY_RAPORT, regon, pkdReportType);
            String response = executeSoapCallWithSession(serviceUrl, GusBir1SoapTemplates.ACTION_DANE_PELNY_RAPORT, envelope, sessionId);

            if (GusBir1ResponseParser.isSoapFault(response)) {
                log.warn("GUS BIR1 PKD report returned SOAP fault: {}", GusBir1ResponseParser.extractFaultMessage(response));
                return List.of();
            }

            return GusBir1ResponseParser.extractPkdCodes(response);
        } catch (Exception e) {
            log.warn("GUS BIR1 PKD fetch failed (non-blocking): {}", e.getMessage());
            return List.of();
        }
    }

    private void tryLogout(String serviceUrl, String sessionId) {
        try {
            String envelope = String.format(GusBir1SoapTemplates.WYLOGUJ, sessionId);
            executeSoapCall(serviceUrl, GusBir1SoapTemplates.ACTION_WYLOGUJ, envelope);
            log.debug("GUS BIR1: Session logged out");
        } catch (Exception e) {
            log.warn("GUS BIR1: Logout failed (non-critical): {}", e.getMessage());
        }
    }

    /** SOAP 1.2 Content-Type (not text/xml which is SOAP 1.1) */
    private static final MediaType SOAP12_CONTENT_TYPE = MediaType.valueOf("application/soap+xml;charset=UTF-8");

    private String executeSoapCall(String serviceUrl, String soapAction, String envelope) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(SOAP12_CONTENT_TYPE);
        headers.set("SOAPAction", soapAction);

        HttpEntity<String> request = new HttpEntity<>(envelope, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(serviceUrl, request, String.class);
            return unwrapMtom(response.getBody());
        } catch (RestClientException e) {
            log.error("GUS BIR1 SOAP call failed. Action: {}, Error: {}", soapAction, e.getMessage());
            throw new ExternalServiceException("error.registry.service_unavailable", "GUS_BIR1", soapAction, e);
        }
    }

    private String executeSoapCallWithSession(String serviceUrl, String soapAction, String envelope, String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(SOAP12_CONTENT_TYPE);
        headers.set("SOAPAction", soapAction);
        headers.set("sid", sessionId);

        HttpEntity<String> request = new HttpEntity<>(envelope, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(serviceUrl, request, String.class);
            return unwrapMtom(response.getBody());
        } catch (RestClientException e) {
            log.error("GUS BIR1 SOAP call failed. Action: {}, Error: {}", soapAction, e.getMessage());
            throw new ExternalServiceException("error.registry.service_unavailable", "GUS_BIR1", soapAction, e);
        }
    }

    /**
     * GUS BIR1 returns MTOM-wrapped responses (multipart/related with XOP).
     * Extract the SOAP XML envelope from the MTOM wrapper.
     */
    private String unwrapMtom(String body) {
        if (body == null) return null;
        // If response is already plain XML, return as-is
        String trimmed = body.trim();
        if (trimmed.startsWith("<")) return trimmed;
        // MTOM: extract the XML between the MIME boundaries
        int xmlStart = body.indexOf("<s:Envelope");
        if (xmlStart == -1) xmlStart = body.indexOf("<soap:Envelope");
        if (xmlStart == -1) xmlStart = body.indexOf("<?xml");
        if (xmlStart == -1) {
            log.warn("GUS BIR1: Could not find SOAP envelope in MTOM response");
            return body;
        }
        int xmlEnd = body.lastIndexOf("</s:Envelope>");
        if (xmlEnd == -1) xmlEnd = body.lastIndexOf("</soap:Envelope>");
        if (xmlEnd != -1) {
            xmlEnd += (body.charAt(xmlEnd + 2) == 's' ? "</s:Envelope>".length() : "</soap:Envelope>".length());
            return body.substring(xmlStart, xmlEnd);
        }
        return body.substring(xmlStart);
    }

    /**
     * Determines the report type based on GUS entity type code.
     * "P" = osoba prawna (legal person) → BIR11OsPrawna
     * "F" = osoba fizyczna (natural person/JDG) → BIR11OsFizycznaDzialalnoscCeidg
     */
    private String determineReportType(String entityType) {
        if ("F".equalsIgnoreCase(entityType)) {
            return GusBir1SoapTemplates.REPORT_OSOBA_FIZYCZNA_CEIDG;
        }
        return GusBir1SoapTemplates.REPORT_OSOBA_PRAWNA;
    }

    private CompanyRegistryData buildCompanyRegistryData(
            Map<String, String> searchResult,
            Map<String, String> fullReport,
            String nip,
            List<Map<String, Object>> pkdCodes) {

        // Merge both maps for raw response storage
        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("searchResult", searchResult);
        rawResponse.put("fullReport", fullReport);

        // Determine field prefix based on entity type
        // "P" = prawna (legal person) → praw_ prefix
        // "F" = fizyczna (sole proprietor/JDG) → fiz_ prefix (CEIDG report uses fiz_/fizC_)
        String type = searchResult.get("Typ");
        boolean isFizyczna = "F".equalsIgnoreCase(type);
        String prefix = isFizyczna ? "fiz_" : "praw_";

        // JDG reports (BIR11OsFizycznaDzialalnoscCeidg) don't have legal form symbol fields.
        // For JDG: basic form = "9" (from entity type), registry type from fizC_ prefix.
        // For legal persons: legal form codes come from praw_ prefixed fields.
        String basicLegalFormCode;
        String specificLegalFormCode;
        String legalFormName;
        String registryType;
        String krs;

        if (isFizyczna) {
            basicLegalFormCode = "9"; // Convention: code "9" = sole proprietor
            specificLegalFormCode = "099"; // Convention: code "099" = JDG
            legalFormName = "OSOBA FIZYCZNA PROWADZĄCA DZIAŁALNOŚĆ GOSPODARCZĄ";
            registryType = getField(fullReport, "fizC_RodzajRejestru_Nazwa");
            krs = null; // JDG entities don't have KRS numbers
        } else {
            basicLegalFormCode = getField(fullReport, "praw_podstawowaFormaPrawna_Symbol");
            specificLegalFormCode = getField(fullReport, "praw_szczegolnaFormaPrawna_Symbol");
            legalFormName = getField(fullReport, "praw_szczegolnaFormaPrawna_Nazwa");
            registryType = getField(fullReport, "praw_rodzajRejestruEwidencji_Nazwa");
            krs = getField(fullReport, "praw_numerWRejestrzeEwidencji");
        }

        return CompanyRegistryData.builder()
                .found(true)
                .nip(nip)
                .regon(getField(searchResult, "Regon"))
                .krs(krs)
                .companyName(resolveCompanyName(searchResult, fullReport, prefix))
                .shortName(getField(fullReport, prefix + "nazwaSkrocona"))
                .basicLegalFormCode(basicLegalFormCode)
                .specificLegalFormCode(specificLegalFormCode)
                .legalFormName(legalFormName)
                .registryType(registryType)
                .street(getField(fullReport, prefix + "adSiedzUlica_Nazwa"))
                .buildingNumber(getField(fullReport, prefix + "adSiedzNumerNieruchomosci"))
                .apartmentNumber(getField(fullReport, prefix + "adSiedzNumerLokalu"))
                .city(getField(fullReport, prefix + "adSiedzMiejscowosc_Nazwa"))
                .postalCode(getField(fullReport, prefix + "adSiedzKodPocztowy"))
                .voivodeship(getField(fullReport, prefix + "adSiedzWojewodztwo_Nazwa"))
                .activityStartDate(getField(fullReport, prefix + "dataRozpoczeciaDzialalnosci"))
                .activityEndDate(getField(fullReport, prefix + "dataZakonczeniaDzialalnosci"))
                .suspensionDate(getField(fullReport, prefix + "dataZawieszeniaDzialalnosci"))
                .bankruptcyDate(getField(fullReport, prefix + "dataOrzeczeniaOUpadlosci"))
                .pkdMainCode(extractPkdMainCode(pkdCodes))
                .pkdMainDescription(extractPkdMainDescription(pkdCodes))
                .pkdCodes(pkdCodes.isEmpty() ? null : pkdCodes)
                .rawResponse(rawResponse)
                .build();
    }

    private String extractPkdMainCode(List<Map<String, Object>> pkdCodes) {
        return pkdCodes.stream()
                .filter(pkd -> Boolean.TRUE.equals(pkd.get("isPrimary")))
                .map(pkd -> (String) pkd.get("code"))
                .findFirst()
                .orElse(null);
    }

    private String extractPkdMainDescription(List<Map<String, Object>> pkdCodes) {
        return pkdCodes.stream()
                .filter(pkd -> Boolean.TRUE.equals(pkd.get("isPrimary")))
                .map(pkd -> (String) pkd.get("description"))
                .findFirst()
                .orElse(null);
    }

    private String resolveCompanyName(Map<String, String> search, Map<String, String> report, String prefix) {
        // Prefer full report name, fallback to search result name
        String name = getField(report, prefix + "nazwa");
        if (name == null || name.isBlank()) {
            name = getField(search, "Nazwa");
        }
        return name;
    }

    private String getField(Map<String, String> map, String key) {
        if (map == null) return null;
        String value = map.get(key);
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private String maskNip(String nip) {
        if (nip == null || nip.length() < 4) return "***";
        return nip.substring(0, 3) + "*******";
    }
}
