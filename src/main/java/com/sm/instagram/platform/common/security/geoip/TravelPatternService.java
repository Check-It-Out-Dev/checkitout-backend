package com.sm.instagram.platform.common.security.geoip;

import com.sm.instagram.platform.common.security.GeoLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible ONLY for travel pattern analysis and impossible travel detection.
 * This is shared by both Redis and in-memory GeoLocation implementations.
 * 
 * Responsibilities:
 * - Calculate distance between locations
 * - Detect impossible travel based on speed
 * - Detect country jumps
 * - Create travel event records
 * 
 * Single Responsibility: Travel pattern analysis
 */
@Slf4j
@Service
public class TravelPatternService {
    
    @Value("${geoip.impossible-travel.speed-kmh:500}")
    private int maxTravelSpeedKmh;
    
    @Value("${geoip.impossible-travel.min-minutes:10}")
    private int minMinutesForCheck;
    
    /**
     * Check if travel between two locations is impossible based on tiered detection.
     *
     * <p>Tiered detection approach:
     * <ul>
     *   <li><b>TIER 1 - Same City:</b> Always allow (most common legitimate case)</li>
     *   <li><b>TIER 2 - Same Country:</b> Standard speed check, no grace period</li>
     *   <li><b>TIER 3 - Different Country:</b> Strict check - country jump within grace period
     *       is always blocked, otherwise speed check applies</li>
     * </ul>
     */
    public boolean isImpossibleTravel(GeoLocation from, GeoLocation to, long minutesElapsed) {
        if (from == null || to == null || !from.isKnown() || !to.isKnown()) {
            return false;  // Unknown locations = fail open
        }

        // TIER 1: Same city = Always allow
        if (isSameCity(from, to)) {
            log.debug("Same city detected: {} - allowing", from.getCity());
            return false;
        }

        // TIER 2: Same country = Standard speed check (no grace period)
        if (isCountryMatch(from, to)) {
            double distance = calculateDistance(from, to);
            double speedKmh = (distance / Math.max(minutesElapsed, 1)) * 60;

            if (speedKmh > maxTravelSpeedKmh) {
                log.warn("GDPR: Operation=detectImpossibleTravel, FirebaseUID=SECURITY_CHECK, DataAccessed=geo_location,travel_speed, Purpose=fraud_detection");
                log.warn("Domestic impossible travel: {} km in {} min = {} km/h (max: {} km/h)",
                    String.format("%.0f", distance),
                    minutesElapsed,
                    String.format("%.0f", speedKmh),
                    maxTravelSpeedKmh
                );
                return true;
            }
            return false;
        }

        // TIER 3: Different country = Strict check
        double distance = calculateDistance(from, to);
        double speedKmh = (distance / Math.max(minutesElapsed, 1)) * 60;

        // Country jump within grace period = ALWAYS BLOCK
        if (minutesElapsed < minMinutesForCheck) {
            log.warn("GDPR: Operation=detectCountryJump, FirebaseUID=SECURITY_CHECK, DataAccessed=geo_location, Purpose=fraud_detection, FromCountry={}, ToCountry={}",
                from.getCountryCode(), to.getCountryCode());
            log.warn("International country jump: {} to {} in {} min (< {} min grace)",
                from.getCountryCode(), to.getCountryCode(), minutesElapsed, minMinutesForCheck);
            return true;
        }

        // Speed check for international travel with longer time intervals
        if (speedKmh > maxTravelSpeedKmh) {
            log.warn("GDPR: Operation=detectImpossibleTravel, FirebaseUID=SECURITY_CHECK, DataAccessed=geo_location,travel_speed, Purpose=fraud_detection");
            log.warn("International impossible travel: {} km in {} min = {} km/h (max: {} km/h)",
                String.format("%.0f", distance),
                minutesElapsed,
                String.format("%.0f", speedKmh),
                maxTravelSpeedKmh
            );
            return true;
        }

        return false;
    }
    
    /**
     * Async version of impossible travel check.
     */
    public CompletableFuture<Boolean> checkImpossibleTravelAsync(
            GeoLocation from, 
            GeoLocation to, 
            long minutesElapsed) {
        
        return CompletableFuture.supplyAsync(() -> 
            isImpossibleTravel(from, to, minutesElapsed)
        );
    }
    
    /**
     * Detect if there's a country jump.
     */
    public boolean isCountryJump(GeoLocation from, GeoLocation to) {
        if (from == null || to == null) {
            return false;
        }
        
        String fromCountry = from.getCountryCode();
        String toCountry = to.getCountryCode();
        
        if (fromCountry == null || toCountry == null) {
            return false;
        }
        
        return !fromCountry.equalsIgnoreCase(toCountry);
    }

    /**
     * Check if two countries match (same country code).
     */
    public boolean isCountryMatch(GeoLocation from, GeoLocation to) {
        if (from == null || to == null) {
            return false;
        }
        String fromCountry = from.getCountryCode();
        String toCountry = to.getCountryCode();
        if (fromCountry == null || toCountry == null) {
            return false;
        }
        return fromCountry.equalsIgnoreCase(toCountry);
    }

    /**
     * Check if two locations are in the same city.
     * Uses city name + country match, with distance fallback for unknown cities.
     *
     * <p>Tiered approach:
     * <ul>
     *   <li>If both have city names and same country: compare city names</li>
     *   <li>If city unknown but same country and &lt;50km apart: treat as same city</li>
     * </ul>
     */
    public boolean isSameCity(GeoLocation from, GeoLocation to) {
        if (from == null || to == null || !from.isKnown() || !to.isKnown()) {
            return false;
        }

        // Named city match (require same country to avoid "Springfield, USA" vs "Springfield, UK")
        if (from.getCity() != null && to.getCity() != null
                && from.getCity().equalsIgnoreCase(to.getCity())
                && isCountryMatch(from, to)) {
            return true;
        }

        // Proximity fallback: <50km within same country = "same city" equivalent
        if (isCountryMatch(from, to)) {
            double distance = calculateDistance(from, to);
            return distance < 50.0;
        }

        return false;
    }

    /**
     * Calculate distance between two locations using Haversine formula.
     * Returns distance in kilometers.
     */
    public double calculateDistance(GeoLocation from, GeoLocation to) {
        if (from == null || to == null) {
            return 0;
        }
        
        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(to.getLatitude() - from.getLatitude());
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(from.getLatitude())) * 
                   Math.cos(Math.toRadians(to.getLatitude())) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
    
    /**
     * Calculate travel speed in km/h.
     */
    public double calculateSpeed(GeoLocation from, GeoLocation to, long minutesElapsed) {
        if (minutesElapsed <= 0) {
            return 0;
        }
        
        double distance = calculateDistance(from, to);
        return (distance / minutesElapsed) * 60;
    }
    
    /**
     * Create a travel event record for tracking.
     * This returns a Map that can be serialized to any storage.
     */
    public Map<String, Object> createTravelEvent(
            Long userId,
            GeoLocation from, 
            GeoLocation to, 
            long minutesElapsed) {
        
        log.info("GDPR: Operation=createTravelEvent, FirebaseUID={}, DataAccessed=geo_location,travel_pattern, Purpose=security_monitoring", 
            userId != null ? userId.toString() : "UNKNOWN");
        
        double distance = calculateDistance(from, to);
        double speedKmh = calculateSpeed(from, to, minutesElapsed);
        boolean impossible = isImpossibleTravel(from, to, minutesElapsed);
        boolean countryJump = isCountryJump(from, to);
        
        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("timestamp", Instant.now().toEpochMilli());
        event.put("date", LocalDate.now().toString());
        event.put("fromCountry", from.getCountryCode());
        event.put("fromCity", from.getCity());
        event.put("toCountry", to.getCountryCode());
        event.put("toCity", to.getCity());
        event.put("distanceKm", Math.round(distance));
        event.put("timeMinutes", minutesElapsed);
        event.put("speedKmh", Math.round(speedKmh));
        event.put("impossible", impossible);
        event.put("countryJump", countryJump);
        
        return event;
    }
    
    /**
     * Get travel risk score (0-100).
     * Higher score means higher risk.
     */
    public int getTravelRiskScore(GeoLocation from, GeoLocation to, long minutesElapsed) {
        if (from == null || to == null || !from.isKnown() || !to.isKnown()) {
            return 0;
        }
        
        int score = 0;
        
        // Country jump is high risk
        if (isCountryJump(from, to)) {
            score += 50;
        }
        
        // Calculate speed-based risk
        double speedKmh = calculateSpeed(from, to, minutesElapsed);
        if (speedKmh > maxTravelSpeedKmh) {
            score += 50;  // Impossible travel
        } else if (speedKmh > maxTravelSpeedKmh * 0.8) {
            score += 30;  // Very fast travel
        } else if (speedKmh > maxTravelSpeedKmh * 0.6) {
            score += 20;  // Fast travel
        }
        
        // VPN/Proxy adds risk
        if (from.getIsVpn() || to.getIsVpn()) {
            score += 20;
        }
        
        if (from.getIsProxy() || to.getIsProxy()) {
            score += 15;
        }
        
        if (from.getIsTor() || to.getIsTor()) {
            score += 25;
        }
        
        return Math.min(score, 100);
    }
    
    /**
     * Analyze travel pattern and provide detailed analysis.
     */
    public TravelAnalysis analyzeTravelPattern(
            GeoLocation from, 
            GeoLocation to, 
            long minutesElapsed) {
        
        log.debug("GDPR: Operation=analyzeTravelPattern, FirebaseUID=ANALYSIS, DataAccessed=geo_location,travel_metrics, Purpose=risk_assessment");
        
        double distance = calculateDistance(from, to);
        double speedKmh = calculateSpeed(from, to, minutesElapsed);
        boolean impossible = isImpossibleTravel(from, to, minutesElapsed);
        boolean countryJump = isCountryJump(from, to);
        int riskScore = getTravelRiskScore(from, to, minutesElapsed);
        
        String riskLevel;
        if (riskScore >= 80) {
            riskLevel = "CRITICAL";
        } else if (riskScore >= 60) {
            riskLevel = "HIGH";
        } else if (riskScore >= 40) {
            riskLevel = "MEDIUM";
        } else if (riskScore >= 20) {
            riskLevel = "LOW";
        } else {
            riskLevel = "MINIMAL";
        }
        
        return TravelAnalysis.builder()
            .distanceKm(distance)
            .speedKmh(speedKmh)
            .timeMinutes(minutesElapsed)
            .impossible(impossible)
            .countryJump(countryJump)
            .riskScore(riskScore)
            .riskLevel(riskLevel)
            .fromLocation(from)
            .toLocation(to)
            .build();
    }
    
    /**
     * Travel analysis result.
     */
    @lombok.Builder
    @lombok.Data
    public static class TravelAnalysis {
        private double distanceKm;
        private double speedKmh;
        private long timeMinutes;
        private boolean impossible;
        private boolean countryJump;
        private int riskScore;
        private String riskLevel;
        private GeoLocation fromLocation;
        private GeoLocation toLocation;
    }
}
