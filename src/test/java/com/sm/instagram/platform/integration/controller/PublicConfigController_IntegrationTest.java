package com.sm.instagram.platform.integration.controller;

import com.sm.instagram.platform.common.publicconfig.PublicConfigDto;
import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-level integration tests for {@code GET /api/public-config}.
 *
 * <p>Proves <b>I2 (Endpoint partition)</b> from the {@code paymentsToggle} formal model
 * over the real Spring Security filter chain. The endpoint is anonymous (no JWT, no
 * session), must be reachable in both toggle states, and must return the correct
 * {@code paymentsEnabled} value plus {@code Cache-Control: no-store}.
 *
 * <p>Together with {@code PaymentsTogglePresence_IntegrationTest} (bean-level proof) and
 * the unit tests, this gives end-to-end coverage of the public-config endpoint in both
 * toggle states.
 */
@DisplayName("PublicConfigController HTTP integration tests — both toggle states")
class PublicConfigController_IntegrationTest {

    @Nested
    @DisplayName("When app.payments.enabled = true")
    @TestPropertySource(properties = "app.payments.enabled=true")
    class WhenPaymentsEnabled extends BaseServiceIntegrationTest {

        @LocalServerPort
        private int port;

        @Autowired
        private TestRestTemplate restTemplate;

        @Test
        @DisplayName("returns paymentsEnabled=true with no-store cache header")
        void returnsTrue() {
            ResponseEntity<PublicConfigDto> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/public-config",
                    PublicConfigDto.class);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().paymentsEnabled()).isTrue();
            assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                    .as("Cache-Control header must be no-store so FE picks up toggle flips immediately")
                    .contains("no-store");
        }

        @Test
        @DisplayName("is reachable anonymously (no JWT cookie)")
        void reachableAnonymously() {
            // No auth, no cookies, no headers beyond TestRestTemplate defaults
            ResponseEntity<PublicConfigDto> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/public-config",
                    PublicConfigDto.class);

            // Critical: must NOT be 401 or 403. Must be 200.
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @Nested
    @DisplayName("When app.payments.enabled = false")
    @TestPropertySource(properties = "app.payments.enabled=false")
    class WhenPaymentsDisabled extends BaseServiceIntegrationTest {

        @LocalServerPort
        private int port;

        @Autowired
        private TestRestTemplate restTemplate;

        @Test
        @DisplayName("returns paymentsEnabled=false with no-store cache header")
        void returnsFalse() {
            ResponseEntity<PublicConfigDto> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/public-config",
                    PublicConfigDto.class);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().paymentsEnabled()).isFalse();
            assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                    .contains("no-store");
        }

        @Test
        @DisplayName("is reachable anonymously (no JWT cookie)")
        void reachableAnonymously() {
            ResponseEntity<PublicConfigDto> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/public-config",
                    PublicConfigDto.class);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }
}
