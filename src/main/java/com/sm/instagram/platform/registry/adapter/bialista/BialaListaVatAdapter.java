package com.sm.instagram.platform.registry.adapter.bialista;

import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.port.VatRegistryPort;
import com.sm.instagram.platform.registry.port.VatStatusData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * Biała Lista (White List) VAT status adapter.
 * REST API from the Polish Ministry of Finance.
 * Public, no auth required. Rate limit: 300 queries/day.
 * <p>
 * Non-blocking: if the API is unavailable or rate-limited, returns an empty result
 * rather than failing the entire lookup (per spec: "Biała Lista daily limit reached → log warning, proceed without VAT data").
 */
@Slf4j
@Component
public class BialaListaVatAdapter implements VatRegistryPort {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;
    private final RegistryProperties registryProperties;

    public BialaListaVatAdapter(
            @Qualifier("bialaListaRestTemplate") RestTemplate restTemplate,
            RegistryProperties registryProperties) {
        this.restTemplate = restTemplate;
        this.registryProperties = registryProperties;
    }

    @Override
    public VatStatusData lookupVatStatus(String nip) {
        if (!registryProperties.getVat().isEnabled()) {
            log.debug("Biała Lista adapter is disabled, returning empty result");
            return VatStatusData.builder().found(false).build();
        }

        String date = LocalDate.now().format(DATE_FORMAT);
        String url = registryProperties.getVat().getBaseUrl() + "/api/search/nip/" + nip + "?date=" + date;

        try {
            log.info("Querying Biała Lista for NIP: {}", maskNip(nip));
            ResponseEntity<BialaListaResponse> response = restTemplate.getForEntity(url, BialaListaResponse.class);

            if (response.getBody() == null || response.getBody().getResult() == null
                    || response.getBody().getResult().getSubject() == null) {
                log.info("Biała Lista returned no subject data for NIP: {}", maskNip(nip));
                return VatStatusData.builder().found(false).checkedAt(LocalDate.now()).build();
            }

            BialaListaResponse.Subject subject = response.getBody().getResult().getSubject();

            return VatStatusData.builder()
                    .found(true)
                    .statusVat(subject.getStatusVat())
                    .normalizedStatus(VatStatusData.normalizeVatStatus(subject.getStatusVat()))
                    .accountNumbers(subject.getAccountNumbers() != null ? subject.getAccountNumbers() : Collections.emptyList())
                    .name(subject.getName())
                    .nip(subject.getNip())
                    .regon(subject.getRegon())
                    .registrationDate(subject.getRegistrationLegalDate())
                    .checkedAt(LocalDate.now())
                    .build();

        } catch (HttpClientErrorException.NotFound e) {
            log.info("Biała Lista: NIP {} not found (404)", maskNip(nip));
            return VatStatusData.builder().found(false).checkedAt(LocalDate.now()).build();

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Biała Lista daily rate limit reached (300/day). Proceeding without VAT data for NIP: {}", maskNip(nip));
            return VatStatusData.builder().found(false).checkedAt(LocalDate.now()).build();

        } catch (RestClientException e) {
            log.warn("Biała Lista API call failed for NIP: {}. Error: {}. Proceeding without VAT data.", maskNip(nip), e.getMessage());
            return VatStatusData.builder().found(false).checkedAt(LocalDate.now()).build();
        }
    }

    private String maskNip(String nip) {
        if (nip == null || nip.length() < 4) return "***";
        return nip.substring(0, 3) + "*******";
    }
}
