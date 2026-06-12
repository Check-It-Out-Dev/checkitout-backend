package com.sm.instagram.platform.unit.controller.publicconfig;

import com.sm.instagram.platform.common.publicconfig.PublicConfigController;
import com.sm.instagram.platform.common.publicconfig.PublicConfigDto;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicConfigControllerUnitTest {

    @Mock private AppPaymentsProperties appPaymentsProperties;
    @InjectMocks private PublicConfigController controller;

    @Test
    @DisplayName("returns paymentsEnabled=true when toggle is on")
    void returnsTrueWhenEnabled() {
        when(appPaymentsProperties.isEnabled()).thenReturn(true);

        ResponseEntity<PublicConfigDto> response = controller.getConfig();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentsEnabled()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .contains("no-store");
    }

    @Test
    @DisplayName("returns paymentsEnabled=false when toggle is off")
    void returnsFalseWhenDisabled() {
        when(appPaymentsProperties.isEnabled()).thenReturn(false);

        ResponseEntity<PublicConfigDto> response = controller.getConfig();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentsEnabled()).isFalse();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .contains("no-store");
    }
}
