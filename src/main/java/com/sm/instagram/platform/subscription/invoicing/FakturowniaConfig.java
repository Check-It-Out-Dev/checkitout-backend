package com.sm.instagram.platform.subscription.invoicing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(FakturowniaProperties.class)
public class FakturowniaConfig {

    @Bean("fakturowniaRestTemplate")
    public RestTemplate fakturowniaRestTemplate(RestTemplateBuilder builder, FakturowniaProperties props) {
        return builder
                .setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .build();
    }
}
