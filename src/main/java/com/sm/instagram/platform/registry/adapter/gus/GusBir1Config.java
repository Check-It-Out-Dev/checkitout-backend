package com.sm.instagram.platform.registry.adapter.gus;

import com.sm.instagram.platform.registry.config.RegistryProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the GUS BIR 1.1 SOAP client.
 * Creates a dedicated RestTemplate with SOAP-appropriate timeouts.
 */
@Configuration
public class GusBir1Config {

    @Bean("gusBir1RestTemplate")
    public RestTemplate gusBir1RestTemplate(RestTemplateBuilder builder, RegistryProperties props) {
        return builder
                .setConnectTimeout(Duration.ofMillis(props.getGus().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getGus().getReadTimeoutMs()))
                .build();
    }
}
