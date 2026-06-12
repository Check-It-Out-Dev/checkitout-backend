package com.sm.instagram.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * GeoIP Configuration Properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "geoip")
public class GeoIpConfiguration {
    
    private Cache cache = new Cache();
    private ImpossibleTravel impossibleTravel = new ImpossibleTravel();
    private Cleanup cleanup = new Cleanup();
    
    @Data
    public static class Cache {
        private boolean enabled = true;
        private int ttlDays = 30;
    }
    
    @Data
    public static class ImpossibleTravel {
        private int speedKmh = 1000;  // 1000 km/h threshold
    }
    
    @Data
    public static class Cleanup {
        private String cron = "0 0 4 * * SUN";  // Sunday 4 AM
    }
}
