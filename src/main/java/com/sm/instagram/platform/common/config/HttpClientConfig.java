package com.sm.instagram.platform.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import java.time.Duration;

/**
 * HTTP Client Configuration.
 * 
 * Configures RestTemplate for external HTTP calls.
 */
@Configuration
public class HttpClientConfig {
    
    /**
     * RestTemplate bean for HTTP requests.
     * 
     * Configured with:
     * - 10 second connection timeout
     * - 30 second read timeout
     * - Error handlers for proper exception propagation
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }
}
