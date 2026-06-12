package com.sm.instagram.platform.config;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @PostConstruct
    public void configureEnumsAsRef() {
        ModelResolver.enumsAsRef = true;
    }

    @Bean
    public OpenAPI checkItOutOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CheckItOut Platform API")
                        .description("Backend API for the CheckItOut influencer-brand collaboration platform")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CheckItOut Development Team")))
                .servers(List.of(
                        new Server().url("/api").description("Default server")));
    }
}
