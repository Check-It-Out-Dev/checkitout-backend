package com.sm.instagram.platform.registry.adapter.ceidg;

import com.sm.instagram.platform.registry.config.RegistryProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the CEIDG REST client.
 * Creates a dedicated RestTemplate with configured timeouts.
 */
@Configuration
public class CeidgConfig {

    @Bean("ceidgRestTemplate")
    public RestTemplate ceidgRestTemplate(RestTemplateBuilder builder, RegistryProperties props) {
        return builder
                .setConnectTimeout(Duration.ofMillis(props.getCeidg().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getCeidg().getReadTimeoutMs()))
                .build();
    }
}
