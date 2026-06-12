package com.sm.instagram.platform.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GeoLocation information for IP addresses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocation {
    private String ip;
    private String country;
    private String countryCode;
    private String city;
    private String region;
    private Double latitude;
    private Double longitude;
    private String postalCode;
    private String timezone;
    private String ispName;
    private Boolean isVpn;
    private Boolean isTor;
    private Boolean isProxy;
    
    /**
     * Create an unknown location for when lookup fails.
     */
    public static GeoLocation unknown(String ip) {
        return GeoLocation.builder()
            .ip(ip)
            .country("Unknown")
            .countryCode("XX")
            .city("Unknown")
            .region("Unknown")
            .latitude(0.0)
            .longitude(0.0)
            .build();
    }
    
    /**
     * Check if this is a valid, known location.
     */
    public boolean isKnown() {
        return !"Unknown".equals(country) && !"XX".equals(countryCode);
    }
}
