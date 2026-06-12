package com.sm.instagram.platform.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Validates MaxMind GeoIP functionality on application startup.
 * Ensures the GeoIP database is loaded and working correctly.
 * Non-fatal - application continues even if GeoIP validation fails.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MaxMindStartupValidator {
    
    private final GeoLocationService geoLocationService;
    
    @EventListener(ApplicationReadyEvent.class)
    public void validateGeoIpOnStartup() {
        log.info("========================================");
        log.info("Starting MaxMind GeoIP validation...");
        log.info("========================================");
        
        try {
            // Get server's external IP
            String externalIp = getExternalIp();
            log.info("Server external IP: {}", externalIp);
            
            // Test GeoIP lookup with a 5-second timeout
            GeoLocation location = geoLocationService.getLocation(externalIp, 5000);
            
            if (location == null || !location.isKnown()) {
                log.warn("⚠️ GeoIP validation WARNING: Could not determine location for IP: {}", externalIp);
                log.warn("⚠️ GeoIP service may not be fully functional");
                // Don't fail startup, just warn
            } else {
                log.info("✅ GeoIP validation SUCCESS:");
                log.info("  📍 IP: {}", location.getIp());
                log.info("  🌍 Country: {} ({})", location.getCountry(), location.getCountryCode());
                log.info("  🏙️ City: {}", location.getCity());
                log.info("  📍 Region: {}", location.getRegion());
                log.info("  📐 Coordinates: {}, {}", location.getLatitude(), location.getLongitude());
                
                if (location.getIspName() != null) {
                    log.info("  🌐 ISP: {}", location.getIspName());
                }
                
                if (location.getIsVpn() != null && location.getIsVpn()) {
                    log.info("  🔒 VPN detected");
                }
                
                if (location.getIsProxy() != null && location.getIsProxy()) {
                    log.info("  🔄 Proxy detected");
                }
                
                // Test localhost (should return unknown)
                GeoLocation localhost = geoLocationService.getLocation("127.0.0.1", 1000);
                if (localhost.isKnown()) {
                    log.warn("⚠️ GeoIP incorrectly resolved localhost - check configuration");
                } else {
                    log.info("✅ Localhost correctly returns unknown location");
                }
                
                // Test a known public IP (Google DNS)
                GeoLocation googleDns = geoLocationService.getLocation("8.8.8.8", 2000);
                if (googleDns != null && googleDns.isKnown()) {
                    log.info("✅ Known IP test (8.8.8.8): {} ({})", 
                        googleDns.getCity() != null ? googleDns.getCity() : "N/A", 
                        googleDns.getCountryCode());
                }
                
                // Log metrics
                log.info("📊 GeoIP Metrics: {}", geoLocationService.getMetrics());
            }
            
            log.info("========================================");
            log.info("GeoIP validation completed");
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("❌ GeoIP validation error: {}", e.getMessage());
            log.error("❌ GeoIP service may not be available", e);
            // Don't fail startup, service can work without GeoIP
            log.info("⚠️ Application will continue without GeoIP functionality");
        }
    }
    
    /**
     * Get the external IP address of this server.
     * Tries multiple services for redundancy.
     */
    private String getExternalIp() {
        // Try multiple services for redundancy
        String[] services = {
            "https://api.ipify.org",
            "https://ipinfo.io/ip", 
            "https://checkip.amazonaws.com",
            "https://icanhazip.com"
        };
        
        for (String service : services) {
            try {
                URL url = URI.create(service).toURL();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(url.openStream()))) {
                    String ip = br.readLine();
                    if (ip != null) {
                        ip = ip.trim();
                        if (isValidIp(ip)) {
                            log.debug("Got external IP from {}: {}", service, ip);
                            return ip;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to get IP from {}: {}", service, e.getMessage());
            }
        }
        
        // Fallback to local IP
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            log.warn("Could not determine external IP, using local IP: {}", localIp);
            return localIp;
        } catch (Exception e) {
            log.error("Failed to determine any IP address", e);
            return "unknown";
        }
    }
    
    /**
     * Validate IP address format.
     */
    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // Check for IPv4
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            try {
                for (String part : parts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        return false;
                    }
                }
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        // Basic IPv6 check (contains colons)
        if (ip.contains(":")) {
            return true; // Simple validation for IPv6
        }
        
        return false;
    }
}
