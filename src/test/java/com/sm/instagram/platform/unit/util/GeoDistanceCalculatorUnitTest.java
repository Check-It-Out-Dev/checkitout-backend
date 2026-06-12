package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.GeoDistanceCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for GeoDistanceCalculator.
 *
 * Tests the Haversine formula implementation for calculating
 * great-circle distances between geographic coordinates.
 *
 * Uses known city coordinates and distances for verification.
 */
@DisplayName("GeoDistanceCalculator Unit Tests")
class GeoDistanceCalculatorUnitTest {

    // Known city coordinates (WGS84)
    // New York City: 40.7128 N, 74.0060 W
    private static final double NYC_LAT = 40.7128;
    private static final double NYC_LON = -74.0060;

    // Los Angeles: 34.0522 N, 118.2437 W
    private static final double LA_LAT = 34.0522;
    private static final double LA_LON = -118.2437;

    // London: 51.5074 N, 0.1278 W
    private static final double LONDON_LAT = 51.5074;
    private static final double LONDON_LON = -0.1278;

    // Sydney: 33.8688 S, 151.2093 E
    private static final double SYDNEY_LAT = -33.8688;
    private static final double SYDNEY_LON = 151.2093;

    // Tokyo: 35.6762 N, 139.6503 E
    private static final double TOKYO_LAT = 35.6762;
    private static final double TOKYO_LON = 139.6503;

    // Budapest: 47.4979 N, 19.0402 E
    private static final double BUDAPEST_LAT = 47.4979;
    private static final double BUDAPEST_LON = 19.0402;

    // Tolerance for distance calculations (1% error margin for Haversine approximation)
    private static final double DISTANCE_TOLERANCE_PERCENT = 0.01;

    @Nested
    @DisplayName("calculateDistanceKm tests")
    class CalculateDistanceKmTests {

        @Test
        @DisplayName("Same point returns zero distance")
        void calculateDistanceKm_samePoint_returnsZero() {
            // Given: Same coordinates
            double lat = 40.7128;
            double lon = -74.0060;

            // When: Calculate distance
            double distance = GeoDistanceCalculator.calculateDistanceKm(lat, lon, lat, lon);

            // Then: Distance should be zero
            assertThat(distance).isEqualTo(0.0);
        }

        @Test
        @DisplayName("NYC to LA returns approximately 3944 km")
        void calculateDistanceKm_knownCities_returnsCorrectDistance() {
            // Given: NYC and LA coordinates
            // Known distance: approximately 3944 km (great-circle distance)

            // When: Calculate distance
            double distance = GeoDistanceCalculator.calculateDistanceKm(
                    NYC_LAT, NYC_LON, LA_LAT, LA_LON);

            // Then: Distance should be approximately 3944 km (within 1%)
            assertThat(distance).isCloseTo(3944.0, within(3944.0 * DISTANCE_TOLERANCE_PERCENT));
        }

        @Test
        @DisplayName("London to NYC returns approximately 5570 km")
        void calculateDistanceKm_transatlantic_returnsCorrectDistance() {
            // Given: London and NYC coordinates
            // Known distance: approximately 5570 km

            // When: Calculate distance
            double distance = GeoDistanceCalculator.calculateDistanceKm(
                    LONDON_LAT, LONDON_LON, NYC_LAT, NYC_LON);

            // Then: Distance should be approximately 5570 km
            assertThat(distance).isCloseTo(5570.0, within(5570.0 * DISTANCE_TOLERANCE_PERCENT));
        }

        @Test
        @DisplayName("Negative latitude (Southern Hemisphere) calculates correctly")
        void calculateDistanceKm_negativeLatitude_handlesCorrectly() {
            // Given: Sydney (Southern Hemisphere) to Tokyo
            // Known distance: approximately 7823 km

            // When: Calculate distance
            double distance = GeoDistanceCalculator.calculateDistanceKm(
                    SYDNEY_LAT, SYDNEY_LON, TOKYO_LAT, TOKYO_LON);

            // Then: Distance should be approximately 7823 km
            assertThat(distance).isCloseTo(7823.0, within(7823.0 * DISTANCE_TOLERANCE_PERCENT));
        }

        @Test
        @DisplayName("Negative longitude (Western Hemisphere) calculates correctly")
        void calculateDistanceKm_negativeLongitude_handlesCorrectly() {
            // Given: NYC (negative longitude) to Budapest (positive longitude)
            // Known distance: approximately 7008 km

            // When: Calculate distance
            double distance = GeoDistanceCalculator.calculateDistanceKm(
                    NYC_LAT, NYC_LON, BUDAPEST_LAT, BUDAPEST_LON);

            // Then: Distance should be approximately 7008 km
            assertThat(distance).isCloseTo(7008.0, within(7008.0 * DISTANCE_TOLERANCE_PERCENT));
        }

        @Test
        @DisplayName("Antipodal points return approximately half Earth circumference")
        void calculateDistanceKm_antipodes_returnsHalfEarthCircumference() {
            // Given: Two antipodal points (opposite sides of Earth)
            // North Pole to South Pole (approximately 20,015 km)
            double northPoleLat = 90.0;
            double northPoleLon = 0.0;
            double southPoleLat = -90.0;
            double southPoleLon = 0.0;

            // When: Calculate distance
            double distance = GeoDistanceCalculator.calculateDistanceKm(
                    northPoleLat, northPoleLon, southPoleLat, southPoleLon);

            // Then: Distance should be approximately 20,015 km (half of Earth's circumference)
            // Earth's circumference is approximately 40,030 km
            assertThat(distance).isCloseTo(20015.0, within(20015.0 * DISTANCE_TOLERANCE_PERCENT));
        }

        @Test
        @DisplayName("Distance calculation is symmetric (A to B equals B to A)")
        void calculateDistanceKm_symmetric_returnsEqualDistance() {
            // Given: Two points

            // When: Calculate distance both ways
            double distanceAtoB = GeoDistanceCalculator.calculateDistanceKm(
                    NYC_LAT, NYC_LON, LONDON_LAT, LONDON_LON);
            double distanceBtoA = GeoDistanceCalculator.calculateDistanceKm(
                    LONDON_LAT, LONDON_LON, NYC_LAT, NYC_LON);

            // Then: Both distances should be equal
            assertThat(distanceAtoB).isEqualTo(distanceBtoA);
        }
    }

    @Nested
    @DisplayName("isWithinRadius tests")
    class IsWithinRadiusTests {

        @Test
        @DisplayName("Points within radius returns true")
        void isWithinRadius_withinRadius_returnsTrue() {
            // Given: Two points approximately 100 km apart
            // Using points within the same metropolitan area
            double lat1 = 40.7128;  // NYC
            double lon1 = -74.0060;
            double lat2 = 40.0583;  // Trenton, NJ (approximately 80 km from NYC)
            double lon2 = -74.4057;

            // When: Check if within 150 km radius
            boolean result = GeoDistanceCalculator.isWithinRadius(lat1, lon1, lat2, lon2, 150.0);

            // Then: Should be within radius
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Points outside radius returns false")
        void isWithinRadius_outsideRadius_returnsFalse() {
            // Given: NYC to LA (approximately 3944 km apart)

            // When: Check if within 100 km radius
            boolean result = GeoDistanceCalculator.isWithinRadius(
                    NYC_LAT, NYC_LON, LA_LAT, LA_LON, 100.0);

            // Then: Should be outside radius
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Points exactly on boundary returns true (boundary is inclusive)")
        void isWithinRadius_exactlyOnBoundary_returnsTrue() {
            // Given: Same point with zero distance

            // When: Check if within 0 km radius
            boolean result = GeoDistanceCalculator.isWithinRadius(
                    NYC_LAT, NYC_LON, NYC_LAT, NYC_LON, 0.0);

            // Then: Should be exactly on boundary (distance = 0 <= radius = 0)
            assertThat(result).isTrue();
        }
    }
}
