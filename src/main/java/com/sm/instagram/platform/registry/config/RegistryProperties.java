package com.sm.instagram.platform.registry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "registry")
public class RegistryProperties {

    private GusConfig gus = new GusConfig();
    private VatConfig vat = new VatConfig();
    private CeidgConfig ceidg = new CeidgConfig();
    private CacheConfig cache = new CacheConfig();

    @Data
    public static class GusConfig {
        private String productionUrl = "https://wyszukiwarkaregon.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc";
        private String testUrl = "https://wyszukiwarkaregontest.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc";
        private String apiKey;
        private String environment = "test";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
        private boolean enabled = true;

        public String getActiveUrl() {
            return "production".equalsIgnoreCase(environment) ? productionUrl : testUrl;
        }
    }

    @Data
    public static class VatConfig {
        private String baseUrl = "https://wl-api.mf.gov.pl";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
        private boolean enabled = true;
    }

    @Data
    public static class CeidgConfig {
        private String baseUrl = "https://dane.biznes.gov.pl/api/ceidg/v2";
        private String testBaseUrl = "https://test-dane.biznes.gov.pl/api/ceidg/v2";
        private String apiKey;
        private String environment = "production";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
        private boolean enabled = true;

        public String getActiveBaseUrl() {
            return "production".equalsIgnoreCase(environment) ? baseUrl : testBaseUrl;
        }
    }

    @Data
    public static class CacheConfig {
        private int ttlMinutes = 15;
    }
}
