package com.sm.instagram.platform.registry.adapter.bialista;

import com.sm.instagram.platform.registry.config.RegistryProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the Biała Lista (White List) REST client.
 * Creates a dedicated RestTemplate with configured timeouts.
 */
@Configuration
public class BialaListaConfig {

    @Bean("bialaListaRestTemplate")
    public RestTemplate bialaListaRestTemplate(RestTemplateBuilder builder, RegistryProperties props) {
        return builder
                .setConnectTimeout(Duration.ofMillis(props.getVat().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getVat().getReadTimeoutMs()))
                .build();
    }
}
