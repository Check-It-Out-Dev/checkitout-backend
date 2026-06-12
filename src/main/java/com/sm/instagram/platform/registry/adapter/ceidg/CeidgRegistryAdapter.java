package com.sm.instagram.platform.registry.adapter.ceidg;

import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.port.SoleProprietorData;
import com.sm.instagram.platform.registry.port.SoleProprietorRegistryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CEIDG (Central Register and Information on Economic Activity) adapter.
 * REST API v2 — provides sole proprietor (JDG) data.
 * Auth: Bearer token from biznes.gov.pl portal.
 * Only called when GUS identifies the entity as a sole proprietorship.
 */
@Slf4j
@Component
public class CeidgRegistryAdapter implements SoleProprietorRegistryPort {

    private final RestTemplate restTemplate;
    private final RegistryProperties registryProperties;

    public CeidgRegistryAdapter(
            @Qualifier("ceidgRestTemplate") RestTemplate restTemplate,
            RegistryProperties registryProperties) {
        this.restTemplate = restTemplate;
        this.registryProperties = registryProperties;
    }

    @Override
    public SoleProprietorData lookupByNip(String nip) {
        if (!registryProperties.getCeidg().isEnabled()) {
            log.debug("CEIDG adapter is disabled, returning empty result");
            return SoleProprietorData.builder().found(false).build();
        }

        String apiKey = registryProperties.getCeidg().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("CEIDG API key is not configured. Skipping CEIDG lookup.");
            return SoleProprietorData.builder().found(false).build();
        }

        String baseUrl = registryProperties.getCeidg().getActiveBaseUrl();
        String url = baseUrl + "/firmy?nip=" + nip;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);

        try {
            log.info("Querying CEIDG for NIP: {}", maskNip(nip));
            ResponseEntity<CeidgResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), CeidgResponse.class);

            if (response.getBody() == null) {
                log.info("CEIDG returned null body for NIP: {}", maskNip(nip));
                return SoleProprietorData.builder().found(false).build();
            }

            List<CeidgResponse.Firma> firmy = response.getBody().getFirmy();
            if (firmy == null || firmy.isEmpty()) {
                log.info("CEIDG returned no firms for NIP: {}", maskNip(nip));
                return SoleProprietorData.builder().found(false).build();
            }

            CeidgResponse.Firma firma = firmy.getFirst();
            return mapToSoleProprietorData(firma);

        } catch (HttpClientErrorException.NotFound e) {
            log.info("CEIDG: NIP {} not found (404)", maskNip(nip));
            return SoleProprietorData.builder().found(false).build();

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("CEIDG authentication failed. Check CEIDG_APP_KEY configuration. Status: {}", e.getStatusCode());
            throw new ExternalServiceException("error.registry.ceidg_auth_failed", "CEIDG", "lookupByNip");

        } catch (RestClientException e) {
            log.error("CEIDG API call failed for NIP: {}. Error: {}", maskNip(nip), e.getMessage());
            throw new ExternalServiceException("error.registry.service_unavailable", "CEIDG", "lookupByNip", e);
        }
    }

    private SoleProprietorData mapToSoleProprietorData(CeidgResponse.Firma firma) {
        SoleProprietorData.SoleProprietorDataBuilder builder = SoleProprietorData.builder()
                .found(true)
                .businessName(firma.getNazwa())
                .nip(firma.getNip())
                .status(firma.getStatus())
                .startDate(firma.getStartDate())
                .endDate(firma.getEndDate());

        if (firma.getWlasciciel() != null) {
            builder.ownerFirstName(firma.getWlasciciel().getImie());
            builder.ownerLastName(firma.getWlasciciel().getNazwisko());
        }

        CeidgResponse.Adres adres = firma.getAdresGlownegoMiejscaWykonywaniaDzialalnosci();
        if (adres != null) {
            builder.street(adres.getUlica())
                    .buildingNumber(adres.getBudynek())
                    .apartmentNumber(adres.getLokal())
                    .city(adres.getMiasto())
                    .postalCode(adres.getKodPocztowy())
                    .voivodeship(adres.getWojewodztwo());
        }

        if (firma.getPkd() != null) {
            List<Map<String, Object>> pkdCodes = firma.getPkd().stream()
                    .map(pkd -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("code", pkd.getKod());
                        map.put("description", pkd.getNazwa());
                        map.put("isPrimary", Boolean.TRUE.equals(pkd.getPrzewazajace()));
                        return map;
                    })
                    .collect(Collectors.toList());
            builder.pkdCodes(pkdCodes);
        }

        return builder.build();
    }

    private String maskNip(String nip) {
        if (nip == null || nip.length() < 4) return "***";
        return nip.substring(0, 3) + "*******";
    }
}
