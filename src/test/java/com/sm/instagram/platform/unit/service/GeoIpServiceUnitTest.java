package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.*;
import com.sm.instagram.platform.common.security.GeoLocation;
import com.sm.instagram.platform.common.security.GeoIpStorageService;
import com.sm.instagram.platform.common.security.geoip.*;
import com.sm.instagram.platform.common.utils.HashingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GeoIP services.
 * Tests GeoLocationFacade, TravelPatternService, InMemoryGeoLocationCache,
 * RedisGeoLocationCache, and MaxMindDatabaseService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GeoIP Service Unit Tests")
class GeoIpServiceUnitTest {

    // ========================================================================
    // TravelPatternService Tests
    // ========================================================================
    @Nested
    @DisplayName("TravelPatternService Tests")
    class TravelPatternServiceTests {

        private TravelPatternService travelPatternService;

        @BeforeEach
        void setUp() {
            travelPatternService = new TravelPatternService();
            ReflectionTestUtils.setField(travelPatternService, "maxTravelSpeedKmh", 500);
            ReflectionTestUtils.setField(travelPatternService, "minMinutesForCheck", 10);
        }

        @Nested
        @DisplayName("calculateDistance")
        class CalculateDistanceTests {

            @Test
            @DisplayName("should calculate distance between two known locations")
            void shouldCalculateDistanceBetweenTwoKnownLocations() {
                // Given - New York to London (approx 5570 km)
                GeoLocation newYork = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation london = createLocation("GB", "London", 51.5074, -0.1278);

                // When
                double distance = travelPatternService.calculateDistance(newYork, london);

                // Then - Allow 5% tolerance for calculation
                assertThat(distance).isCloseTo(5570, within(300.0));
            }

            @Test
            @DisplayName("should return zero for same location")
            void shouldReturnZeroForSameLocation() {
                // Given
                GeoLocation location = createLocation("US", "New York", 40.7128, -74.0060);

                // When
                double distance = travelPatternService.calculateDistance(location, location);

                // Then
                assertThat(distance).isEqualTo(0.0);
            }

            @Test
            @DisplayName("should return zero for null locations")
            void shouldReturnZeroForNullLocations() {
                // Given
                GeoLocation location = createLocation("US", "New York", 40.7128, -74.0060);

                // When/Then
                assertThat(travelPatternService.calculateDistance(null, location)).isEqualTo(0.0);
                assertThat(travelPatternService.calculateDistance(location, null)).isEqualTo(0.0);
                assertThat(travelPatternService.calculateDistance(null, null)).isEqualTo(0.0);
            }

            @ParameterizedTest
            @CsvSource({
                "52.5200, 13.4050, 48.8566, 2.3522, 878",    // Berlin to Paris
                "35.6762, 139.6503, 37.5665, 126.9780, 1160", // Tokyo to Seoul
                "34.0522, -118.2437, 37.7749, -122.4194, 559" // LA to San Francisco
            })
            @DisplayName("should calculate correct distances for various city pairs")
            void shouldCalculateCorrectDistancesForVariousCityPairs(
                    double lat1, double lon1, double lat2, double lon2, double expectedKm) {
                // Given
                GeoLocation from = createLocation("XX", "City1", lat1, lon1);
                GeoLocation to = createLocation("XX", "City2", lat2, lon2);

                // When
                double distance = travelPatternService.calculateDistance(from, to);

                // Then - Allow 10% tolerance
                assertThat(distance).isCloseTo(expectedKm, within(expectedKm * 0.10));
            }
        }

        @Nested
        @DisplayName("isImpossibleTravel")
        class IsImpossibleTravelTests {

            @Test
            @DisplayName("should detect impossible travel for high speed")
            void shouldDetectImpossibleTravelForHighSpeed() {
                // Given - 5000 km in 30 minutes = 10000 km/h (way over 500 km/h limit)
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("GB", "London", 51.5074, -0.1278);

                // When
                boolean impossible = travelPatternService.isImpossibleTravel(from, to, 30);

                // Then
                assertThat(impossible).isTrue();
            }

            @Test
            @DisplayName("should allow reasonable travel speed")
            void shouldAllowReasonableTravelSpeed() {
                // Given - 500 km in 120 minutes = 250 km/h (under 500 km/h limit)
                GeoLocation from = createLocation("DE", "Berlin", 52.5200, 13.4050);
                GeoLocation to = createLocation("DE", "Munich", 48.1351, 11.5820); // ~500km

                // When
                boolean impossible = travelPatternService.isImpossibleTravel(from, to, 120);

                // Then
                assertThat(impossible).isFalse();
            }

            @Test
            @DisplayName("should return false for null locations")
            void shouldReturnFalseForNullLocations() {
                // Given
                GeoLocation location = createLocation("US", "Test", 40.0, -74.0);

                // When/Then
                assertThat(travelPatternService.isImpossibleTravel(null, location, 30)).isFalse();
                assertThat(travelPatternService.isImpossibleTravel(location, null, 30)).isFalse();
            }

            @Test
            @DisplayName("should return false for unknown locations")
            void shouldReturnFalseForUnknownLocations() {
                // Given
                GeoLocation known = createLocation("US", "Test", 40.0, -74.0);
                GeoLocation unknown = GeoLocation.unknown("1.1.1.1");

                // When/Then
                assertThat(travelPatternService.isImpossibleTravel(known, unknown, 30)).isFalse();
            }

            @Test
            @DisplayName("should detect country jump for short sessions")
            void shouldDetectCountryJumpForShortSessions() {
                // Given - Different countries, session age under minMinutesForCheck
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("GB", "London", 51.5074, -0.1278);

                // When - Only 5 minutes elapsed (under the 10 minute threshold)
                boolean impossible = travelPatternService.isImpossibleTravel(from, to, 5);

                // Then - Should still detect country jump
                assertThat(impossible).isTrue();
            }

            @Test
            @DisplayName("should not flag same country travel when speed is physically possible")
            void shouldNotFlagSameCountryTravelWhenSpeedIsPossible() {
                // Given - Same country, NYC to Boston (~306km)
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("US", "Boston", 42.3601, -71.0589);

                // When - 45 minutes elapsed (~408 km/h, under 500 km/h threshold)
                boolean impossible = travelPatternService.isImpossibleTravel(from, to, 45);

                // Then - Should not flag since speed is physically possible
                assertThat(impossible).isFalse();
            }

            @Test
            @DisplayName("should flag same country travel when speed is physically impossible")
            void shouldFlagSameCountryTravelWhenSpeedIsImpossible() {
                // Given - Same country, NYC to Boston (~306km)
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("US", "Boston", 42.3601, -71.0589);

                // When - Only 5 minutes elapsed (~3672 km/h, way over 500 km/h threshold)
                boolean impossible = travelPatternService.isImpossibleTravel(from, to, 5);

                // Then - Should flag as impossible travel (physics check)
                assertThat(impossible).isTrue();
            }
        }

        @Nested
        @DisplayName("isCountryJump")
        class IsCountryJumpTests {

            @Test
            @DisplayName("should detect country jump between different countries")
            void shouldDetectCountryJumpBetweenDifferentCountries() {
                // Given
                GeoLocation us = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation gb = createLocation("GB", "London", 51.5074, -0.1278);

                // When
                boolean jump = travelPatternService.isCountryJump(us, gb);

                // Then
                assertThat(jump).isTrue();
            }

            @Test
            @DisplayName("should not detect country jump for same country")
            void shouldNotDetectCountryJumpForSameCountry() {
                // Given
                GeoLocation nyc = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation la = createLocation("US", "Los Angeles", 34.0522, -118.2437);

                // When
                boolean jump = travelPatternService.isCountryJump(nyc, la);

                // Then
                assertThat(jump).isFalse();
            }

            @Test
            @DisplayName("should be case insensitive for country codes")
            void shouldBeCaseInsensitiveForCountryCodes() {
                // Given
                GeoLocation lower = GeoLocation.builder()
                        .countryCode("us")
                        .build();
                GeoLocation upper = GeoLocation.builder()
                        .countryCode("US")
                        .build();

                // When
                boolean jump = travelPatternService.isCountryJump(lower, upper);

                // Then
                assertThat(jump).isFalse();
            }

            @Test
            @DisplayName("should return false for null country codes")
            void shouldReturnFalseForNullCountryCodes() {
                // Given
                GeoLocation noCountry = GeoLocation.builder().build();
                GeoLocation withCountry = createLocation("US", "Test", 40.0, -74.0);

                // When/Then
                assertThat(travelPatternService.isCountryJump(noCountry, withCountry)).isFalse();
                assertThat(travelPatternService.isCountryJump(withCountry, noCountry)).isFalse();
            }
        }

        @Nested
        @DisplayName("calculateSpeed")
        class CalculateSpeedTests {

            @Test
            @DisplayName("should calculate speed correctly")
            void shouldCalculateSpeedCorrectly() {
                // Given - 600 km in 60 minutes = 600 km/h
                GeoLocation from = createLocation("DE", "Berlin", 52.5200, 13.4050);
                GeoLocation to = createLocation("DE", "Munich", 48.1351, 11.5820);

                // When
                double speed = travelPatternService.calculateSpeed(from, to, 60);

                // Then - Berlin to Munich is ~500km, so speed should be ~500 km/h
                assertThat(speed).isCloseTo(500.0, within(100.0));
            }

            @Test
            @DisplayName("should return zero for zero elapsed time")
            void shouldReturnZeroForZeroElapsedTime() {
                // Given
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("US", "Boston", 42.3601, -71.0589);

                // When
                double speed = travelPatternService.calculateSpeed(from, to, 0);

                // Then
                assertThat(speed).isEqualTo(0.0);
            }

            @Test
            @DisplayName("should return zero for negative elapsed time")
            void shouldReturnZeroForNegativeElapsedTime() {
                // Given
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("US", "Boston", 42.3601, -71.0589);

                // When
                double speed = travelPatternService.calculateSpeed(from, to, -10);

                // Then
                assertThat(speed).isEqualTo(0.0);
            }
        }

        @Nested
        @DisplayName("getTravelRiskScore")
        class GetTravelRiskScoreTests {

            @Test
            @DisplayName("should return high score for impossible travel with country jump")
            void shouldReturnHighScoreForImpossibleTravelWithCountryJump() {
                // Given - Cross-country impossible travel
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("GB", "London", 51.5074, -0.1278);

                // When - 30 minutes for ~5500km = ~11000 km/h
                int score = travelPatternService.getTravelRiskScore(from, to, 30);

                // Then - Should be 100 (50 for country jump + 50 for impossible travel)
                assertThat(score).isEqualTo(100);
            }

            @Test
            @DisplayName("should return moderate score for fast but possible travel")
            void shouldReturnModerateScoreForFastButPossibleTravel() {
                // Given - Same country, fast travel
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("US", "Chicago", 41.8781, -87.6298); // ~1150km

                // When - 150 minutes for 1150km = ~460 km/h (92% of max)
                int score = travelPatternService.getTravelRiskScore(from, to, 150);

                // Then - Should be 30 (very fast travel, 80-100% of max)
                assertThat(score).isEqualTo(30);
            }

            @Test
            @DisplayName("should return zero for unknown locations")
            void shouldReturnZeroForUnknownLocations() {
                // Given
                GeoLocation known = createLocation("US", "Test", 40.0, -74.0);
                GeoLocation unknown = GeoLocation.unknown("1.1.1.1");

                // When
                int score = travelPatternService.getTravelRiskScore(known, unknown, 30);

                // Then
                assertThat(score).isEqualTo(0);
            }

            @Test
            @DisplayName("should add risk for VPN usage")
            void shouldAddRiskForVpnUsage() {
                // Given - Same location but with VPN
                GeoLocation from = GeoLocation.builder()
                        .countryCode("US")
                        .country("United States")
                        .city("Test")
                        .latitude(40.0)
                        .longitude(-74.0)
                        .isVpn(true)
                        .isProxy(false)
                        .isTor(false)
                        .build();
                GeoLocation to = GeoLocation.builder()
                        .countryCode("US")
                        .country("United States")
                        .city("Test2")
                        .latitude(40.1)
                        .longitude(-74.1)
                        .isVpn(false)
                        .isProxy(false)
                        .isTor(false)
                        .build();

                // When
                int score = travelPatternService.getTravelRiskScore(from, to, 60);

                // Then - Should include VPN risk (20)
                assertThat(score).isGreaterThanOrEqualTo(20);
            }

            @Test
            @DisplayName("should add risk for Tor usage")
            void shouldAddRiskForTorUsage() {
                // Given
                GeoLocation from = GeoLocation.builder()
                        .countryCode("US")
                        .country("United States")
                        .city("Test")
                        .latitude(40.0)
                        .longitude(-74.0)
                        .isVpn(false)
                        .isProxy(false)
                        .isTor(true)
                        .build();
                GeoLocation to = GeoLocation.builder()
                        .countryCode("US")
                        .country("United States")
                        .city("Test2")
                        .latitude(40.1)
                        .longitude(-74.1)
                        .isVpn(false)
                        .isProxy(false)
                        .isTor(false)
                        .build();

                // When
                int score = travelPatternService.getTravelRiskScore(from, to, 60);

                // Then - Should include Tor risk (25)
                assertThat(score).isGreaterThanOrEqualTo(25);
            }
        }

        @Nested
        @DisplayName("createTravelEvent")
        class CreateTravelEventTests {

            @Test
            @DisplayName("should create travel event with all required fields")
            void shouldCreateTravelEventWithAllRequiredFields() {
                // Given
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("GB", "London", 51.5074, -0.1278);

                // When
                Map<String, Object> event = travelPatternService.createTravelEvent(123L, from, to, 60);

                // Then
                assertThat(event).containsKey("userId");
                assertThat(event).containsKey("timestamp");
                assertThat(event).containsKey("date");
                assertThat(event).containsKey("fromCountry");
                assertThat(event).containsKey("toCountry");
                assertThat(event).containsKey("distanceKm");
                assertThat(event).containsKey("timeMinutes");
                assertThat(event).containsKey("speedKmh");
                assertThat(event).containsKey("impossible");
                assertThat(event).containsKey("countryJump");

                assertThat(event.get("userId")).isEqualTo(123L);
                assertThat(event.get("fromCountry")).isEqualTo("US");
                assertThat(event.get("toCountry")).isEqualTo("GB");
                assertThat(event.get("timeMinutes")).isEqualTo(60L);
                assertThat(event.get("countryJump")).isEqualTo(true);
            }

            @Test
            @DisplayName("should mark impossible travel correctly")
            void shouldMarkImpossibleTravelCorrectly() {
                // Given - Impossible travel
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("JP", "Tokyo", 35.6762, 139.6503);

                // When - 60 minutes for ~10000km = ~10000 km/h
                Map<String, Object> event = travelPatternService.createTravelEvent(1L, from, to, 60);

                // Then
                assertThat(event.get("impossible")).isEqualTo(true);
            }
        }

        @Nested
        @DisplayName("analyzeTravelPattern")
        class AnalyzeTravelPatternTests {

            @Test
            @DisplayName("should return CRITICAL risk level for impossible travel with country jump")
            void shouldReturnCriticalRiskLevelForImpossibleTravelWithCountryJump() {
                // Given
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("GB", "London", 51.5074, -0.1278);

                // When
                TravelPatternService.TravelAnalysis analysis =
                        travelPatternService.analyzeTravelPattern(from, to, 30);

                // Then
                assertThat(analysis.getRiskLevel()).isEqualTo("CRITICAL");
                assertThat(analysis.getRiskScore()).isEqualTo(100);
                assertThat(analysis.isImpossible()).isTrue();
                assertThat(analysis.isCountryJump()).isTrue();
            }

            @Test
            @DisplayName("should return MINIMAL risk level for normal travel")
            void shouldReturnMinimalRiskLevelForNormalTravel() {
                // Given - Short distance, reasonable time
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("US", "Newark", 40.7357, -74.1724);

                // When - 30 minutes for ~15km
                TravelPatternService.TravelAnalysis analysis =
                        travelPatternService.analyzeTravelPattern(from, to, 30);

                // Then
                assertThat(analysis.getRiskLevel()).isEqualTo("MINIMAL");
                assertThat(analysis.getRiskScore()).isLessThan(20);
                assertThat(analysis.isImpossible()).isFalse();
                assertThat(analysis.isCountryJump()).isFalse();
            }

            @Test
            @DisplayName("should include all analysis fields")
            void shouldIncludeAllAnalysisFields() {
                // Given
                GeoLocation from = createLocation("US", "Test1", 40.0, -74.0);
                GeoLocation to = createLocation("US", "Test2", 41.0, -75.0);

                // When
                TravelPatternService.TravelAnalysis analysis =
                        travelPatternService.analyzeTravelPattern(from, to, 60);

                // Then
                assertThat(analysis.getDistanceKm()).isGreaterThan(0);
                assertThat(analysis.getSpeedKmh()).isGreaterThan(0);
                assertThat(analysis.getTimeMinutes()).isEqualTo(60);
                assertThat(analysis.getFromLocation()).isEqualTo(from);
                assertThat(analysis.getToLocation()).isEqualTo(to);
                assertThat(analysis.getRiskLevel()).isNotNull();
            }
        }

        @Nested
        @DisplayName("Async Operations")
        class AsyncOperationsTests {

            @Test
            @DisplayName("should check impossible travel asynchronously")
            void shouldCheckImpossibleTravelAsynchronously() throws Exception {
                // Given
                GeoLocation from = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation to = createLocation("GB", "London", 51.5074, -0.1278);

                // When
                CompletableFuture<Boolean> future =
                        travelPatternService.checkImpossibleTravelAsync(from, to, 30);
                Boolean result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertThat(result).isTrue();
            }
        }
    }

    // ========================================================================
    // InMemoryGeoLocationCache Tests
    // ========================================================================
    @Nested
    @DisplayName("InMemoryGeoLocationCache Tests")
    class InMemoryGeoLocationCacheTests {

        private InMemoryGeoLocationCache cache;

        @BeforeEach
        void setUp() {
            cache = new InMemoryGeoLocationCache();
            ReflectionTestUtils.setField(cache, "cacheTtlDays", 7);
            ReflectionTestUtils.setField(cache, "maxEntries", 100);
        }

        @Nested
        @DisplayName("get and put")
        class GetAndPutTests {

            @Test
            @DisplayName("should cache and retrieve location")
            void shouldCacheAndRetrieveLocation() {
                // Given
                String ip = "192.168.1.1";
                GeoLocation location = createLocation("US", "New York", 40.7128, -74.0060);

                // When
                cache.put(ip, location);
                GeoLocation retrieved = cache.get(ip);

                // Then
                assertThat(retrieved).isNotNull();
                assertThat(retrieved.getCountryCode()).isEqualTo("US");
                assertThat(retrieved.getCity()).isEqualTo("New York");
            }

            @Test
            @DisplayName("should return null for cache miss")
            void shouldReturnNullForCacheMiss() {
                // When
                GeoLocation result = cache.get("1.2.3.4");

                // Then
                assertThat(result).isNull();
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should handle null and empty IP gracefully")
            void shouldHandleNullAndEmptyIpGracefully(String ip) {
                // When
                GeoLocation result = cache.get(ip);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should not put null location")
            void shouldNotPutNullLocation() {
                // Given
                String ip = "1.1.1.1";

                // When
                cache.put(ip, null);

                // Then
                assertThat(cache.get(ip)).isNull();
            }

            @Test
            @DisplayName("should track cache hits and misses")
            void shouldTrackCacheHitsAndMisses() {
                // Given
                String ip = "1.1.1.1";
                GeoLocation location = createLocation("US", "Test", 40.0, -74.0);
                cache.put(ip, location);

                // When
                cache.get(ip);  // Hit
                cache.get(ip);  // Hit
                cache.get("2.2.2.2");  // Miss
                cache.get("3.3.3.3");  // Miss

                // Then
                Map<String, Object> metrics = cache.getMetrics();
                assertThat(metrics.get("cacheHits")).isEqualTo(2L);
                assertThat(metrics.get("cacheMisses")).isEqualTo(2L);
            }
        }

        @Nested
        @DisplayName("Async Operations")
        class AsyncOperationsTests {

            @Test
            @DisplayName("should get location asynchronously")
            void shouldGetLocationAsynchronously() throws Exception {
                // Given
                String ip = "1.1.1.1";
                GeoLocation location = createLocation("US", "Test", 40.0, -74.0);
                cache.put(ip, location);

                // When
                CompletableFuture<GeoLocation> future = cache.getAsync(ip);
                GeoLocation result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getCountryCode()).isEqualTo("US");
            }

            @Test
            @DisplayName("should put location asynchronously")
            void shouldPutLocationAsynchronously() throws Exception {
                // Given
                String ip = "1.1.1.1";
                GeoLocation location = createLocation("GB", "London", 51.5074, -0.1278);

                // When
                CompletableFuture<Void> future = cache.putAsync(ip, location);
                future.get(5, TimeUnit.SECONDS);

                // Then
                assertThat(cache.get(ip)).isNotNull();
            }
        }

        @Nested
        @DisplayName("evict and clear")
        class EvictAndClearTests {

            @Test
            @DisplayName("should evict specific entry")
            void shouldEvictSpecificEntry() {
                // Given
                cache.put("1.1.1.1", createLocation("US", "Test1", 40.0, -74.0));
                cache.put("2.2.2.2", createLocation("GB", "Test2", 51.0, -0.1));

                // When
                cache.evict("1.1.1.1");

                // Then
                assertThat(cache.get("1.1.1.1")).isNull();
                assertThat(cache.get("2.2.2.2")).isNotNull();
            }

            @Test
            @DisplayName("should handle eviction of null IP gracefully")
            void shouldHandleEvictionOfNullIpGracefully() {
                // When/Then - Should not throw
                cache.evict(null);
            }

            @Test
            @DisplayName("should clear all entries")
            void shouldClearAllEntries() {
                // Given
                cache.put("1.1.1.1", createLocation("US", "Test1", 40.0, -74.0));
                cache.put("2.2.2.2", createLocation("GB", "Test2", 51.0, -0.1));
                cache.put("3.3.3.3", createLocation("DE", "Test3", 52.0, 13.0));

                // When
                cache.clear();

                // Then
                assertThat(cache.get("1.1.1.1")).isNull();
                assertThat(cache.get("2.2.2.2")).isNull();
                assertThat(cache.get("3.3.3.3")).isNull();
            }
        }

        @Nested
        @DisplayName("recordTravelPattern")
        class RecordTravelPatternTests {

            @Test
            @DisplayName("should record travel pattern")
            void shouldRecordTravelPattern() {
                // Given
                Map<String, Object> event = new HashMap<>();
                event.put("fromCountry", "US");
                event.put("toCountry", "GB");
                event.put("impossible", false);

                // When
                cache.recordTravelPattern(123L, event);

                // Then
                Map<String, Object> metrics = cache.getMetrics();
                assertThat((int) metrics.get("travelPatternCount")).isGreaterThanOrEqualTo(1);
            }

            @Test
            @DisplayName("should track impossible travel count")
            void shouldTrackImpossibleTravelCount() {
                // Given
                Map<String, Object> impossibleEvent = new HashMap<>();
                impossibleEvent.put("impossible", true);

                Map<String, Object> normalEvent = new HashMap<>();
                normalEvent.put("impossible", false);

                // When
                cache.recordTravelPattern(1L, impossibleEvent);
                cache.recordTravelPattern(2L, normalEvent);
                cache.recordTravelPattern(3L, impossibleEvent);

                // Then
                Map<String, Object> metrics = cache.getMetrics();
                assertThat(metrics.get("impossibleTravelCount")).isEqualTo(2L);
            }

            @Test
            @DisplayName("should handle null userId gracefully")
            void shouldHandleNullUserIdGracefully() {
                // Given
                Map<String, Object> event = new HashMap<>();

                // When/Then - Should not throw
                cache.recordTravelPattern(null, event);
            }

            @Test
            @DisplayName("should handle null event gracefully")
            void shouldHandleNullEventGracefully() {
                // When/Then - Should not throw
                cache.recordTravelPattern(123L, null);
            }
        }

        @Nested
        @DisplayName("getMetrics")
        class GetMetricsTests {

            @Test
            @DisplayName("should return all required metrics")
            void shouldReturnAllRequiredMetrics() {
                // When
                Map<String, Object> metrics = cache.getMetrics();

                // Then
                assertThat(metrics).containsKeys(
                        "cacheHits",
                        "cacheMisses",
                        "totalLookups",
                        "hitRate",
                        "cacheType",
                        "maxEntries",
                        "ttlDays",
                        "currentSize",
                        "travelPatternCount",
                        "impossibleTravelCount"
                );
            }

            @Test
            @DisplayName("should calculate hit rate correctly")
            void shouldCalculateHitRateCorrectly() {
                // Given
                String ip = "1.1.1.1";
                cache.put(ip, createLocation("US", "Test", 40.0, -74.0));
                cache.get(ip);  // Hit
                cache.get(ip);  // Hit
                cache.get("2.2.2.2");  // Miss
                cache.get("3.3.3.3");  // Miss

                // When
                Map<String, Object> metrics = cache.getMetrics();

                // Then - 2 hits out of 4 lookups = 0.5
                assertThat((double) metrics.get("hitRate")).isCloseTo(0.5, within(0.01));
            }
        }

        @Nested
        @DisplayName("isAvailable and getCacheType")
        class AvailabilityTests {

            @Test
            @DisplayName("should always be available")
            void shouldAlwaysBeAvailable() {
                assertThat(cache.isAvailable()).isTrue();
            }

            @Test
            @DisplayName("should return correct cache type")
            void shouldReturnCorrectCacheType() {
                assertThat(cache.getCacheType()).isEqualTo("in-memory");
            }
        }

        @Nested
        @DisplayName("Locking")
        class LockingTests {

            @Test
            @DisplayName("should acquire and release lock")
            void shouldAcquireAndReleaseLock() {
                // When
                boolean acquired = cache.tryLock("test_lock", 5);

                // Then
                assertThat(acquired).isTrue();

                // Cleanup
                cache.releaseLock("test_lock");
            }

            @Test
            @DisplayName("should release non-existent lock gracefully")
            void shouldReleaseNonExistentLockGracefully() {
                // When/Then - Should not throw
                cache.releaseLock("non_existent_lock");
            }
        }

        @Nested
        @DisplayName("Cache Size Eviction")
        class CacheSizeEvictionTests {

            @Test
            @DisplayName("should evict oldest entry when max size reached")
            void shouldEvictOldestEntryWhenMaxSizeReached() throws Exception {
                // Given - Set max entries to 3
                ReflectionTestUtils.setField(cache, "maxEntries", 3);

                // When - Add 4 entries
                cache.put("1.1.1.1", createLocation("US", "First", 40.0, -74.0));
                Thread.sleep(10); // Ensure different timestamps
                cache.put("2.2.2.2", createLocation("GB", "Second", 51.0, -0.1));
                Thread.sleep(10);
                cache.put("3.3.3.3", createLocation("DE", "Third", 52.0, 13.0));
                Thread.sleep(10);
                cache.put("4.4.4.4", createLocation("FR", "Fourth", 48.0, 2.0));

                // Then - First entry should be evicted
                Map<String, Object> metrics = cache.getMetrics();
                assertThat((int) metrics.get("currentSize")).isLessThanOrEqualTo(3);
            }
        }

        @Nested
        @DisplayName("Concurrent Access")
        class ConcurrentAccessTests {

            @Test
            @DisplayName("should handle concurrent cache operations safely")
            void shouldHandleConcurrentCacheOperationsSafely() throws Exception {
                // Given
                int threadCount = 10;
                int operationsPerThread = 100;
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch endLatch = new CountDownLatch(threadCount);
                AtomicInteger successCount = new AtomicInteger(0);

                // When
                for (int i = 0; i < threadCount; i++) {
                    final int threadId = i;
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < operationsPerThread; j++) {
                                String ip = "192.168." + threadId + "." + j;
                                GeoLocation location = createLocation("US", "City" + j, 40.0 + j, -74.0 - j);

                                cache.put(ip, location);
                                cache.get(ip);

                                if (j % 5 == 0) {
                                    cache.evict(ip);
                                }

                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Ignore
                        } finally {
                            endLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                boolean completed = endLatch.await(30, TimeUnit.SECONDS);
                executor.shutdown();

                // Then
                assertThat(completed).isTrue();
                assertThat(successCount.get()).isEqualTo(threadCount * operationsPerThread);
            }
        }
    }

    // ========================================================================
    // RedisGeoLocationCache Tests
    // ========================================================================
    @Nested
    @DisplayName("RedisGeoLocationCache Tests")
    class RedisGeoLocationCacheTests {

        @Mock
        private RedisTemplate<String, String> redisTemplate;

        @Mock
        private ValueOperations<String, String> valueOperations;

        private RedisGeoLocationCache cache;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
            objectMapper = new ObjectMapper();
            cache = new RedisGeoLocationCache(redisTemplate, objectMapper);
            ReflectionTestUtils.setField(cache, "cacheTtlDays", 7);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Nested
        @DisplayName("get")
        class GetTests {

            @Test
            @DisplayName("should retrieve cached location from Redis")
            void shouldRetrieveCachedLocationFromRedis() throws Exception {
                // Given
                String ip = "1.1.1.1";
                String key = HashingUtil.generateRedisKey("geo", ip);
                // Create JSON string that ObjectMapper can parse back to GeoLocation
                String json = "{\"ip\":\"1.1.1.1\",\"country\":\"United States\",\"countryCode\":\"US\"," +
                        "\"city\":\"New York\",\"latitude\":40.7128,\"longitude\":-74.006," +
                        "\"isVpn\":false,\"isTor\":false,\"isProxy\":false}";

                when(valueOperations.get(key)).thenReturn(json);

                // When
                GeoLocation result = cache.get(ip);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getCountryCode()).isEqualTo("US");
                assertThat(result.getCity()).isEqualTo("New York");
            }

            @Test
            @DisplayName("should return null for cache miss")
            void shouldReturnNullForCacheMiss() {
                // Given
                String ip = "1.1.1.1";
                String key = HashingUtil.generateRedisKey("geo", ip);

                when(valueOperations.get(key)).thenReturn(null);

                // When
                GeoLocation result = cache.get(ip);

                // Then
                assertThat(result).isNull();
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should return null for null or empty IP")
            void shouldReturnNullForNullOrEmptyIp(String ip) {
                // When
                GeoLocation result = cache.get(ip);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should handle Redis exception gracefully")
            void shouldHandleRedisExceptionGracefully() {
                // Given
                String ip = "1.1.1.1";
                String key = HashingUtil.generateRedisKey("geo", ip);

                when(valueOperations.get(key)).thenThrow(new RuntimeException("Redis error"));

                // When
                GeoLocation result = cache.get(ip);

                // Then
                assertThat(result).isNull();
            }
        }

        @Nested
        @DisplayName("put")
        class PutTests {

            @Test
            @DisplayName("should store location in Redis with TTL")
            void shouldStoreLocationInRedisWithTtl() throws Exception {
                // Given
                String ip = "1.1.1.1";
                String key = HashingUtil.generateRedisKey("geo", ip);
                GeoLocation location = createLocation("US", "Test", 40.0, -74.0);

                // When
                cache.put(ip, location);

                // Then
                verify(valueOperations).set(eq(key), anyString(), eq(7L), eq(TimeUnit.DAYS));
            }

            @Test
            @DisplayName("should not store null location")
            void shouldNotStoreNullLocation() {
                // When
                cache.put("1.1.1.1", null);

                // Then
                verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
            }

            @Test
            @DisplayName("should not store for null IP")
            void shouldNotStoreForNullIp() {
                // When
                cache.put(null, createLocation("US", "Test", 40.0, -74.0));

                // Then
                verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
            }
        }

        @Nested
        @DisplayName("evict")
        class EvictTests {

            @Test
            @DisplayName("should delete key from Redis")
            void shouldDeleteKeyFromRedis() {
                // Given
                String ip = "1.1.1.1";
                String key = HashingUtil.generateRedisKey("geo", ip);

                // When
                cache.evict(ip);

                // Then
                verify(redisTemplate).delete(key);
            }

            @Test
            @DisplayName("should handle null IP gracefully")
            void shouldHandleNullIpGracefully() {
                // When
                cache.evict(null);

                // Then
                verify(redisTemplate, never()).delete(anyString());
            }
        }

        @Nested
        @DisplayName("getMetrics")
        class GetMetricsTests {

            @Test
            @DisplayName("should return metrics map")
            void shouldReturnMetricsMap() {
                // When
                Map<String, Object> metrics = cache.getMetrics();

                // Then
                assertThat(metrics).containsKeys(
                        "cacheHits",
                        "cacheMisses",
                        "totalLookups",
                        "hitRate",
                        "cacheType",
                        "ttlDays"
                );
                assertThat(metrics.get("cacheType")).isEqualTo("redis");
            }
        }

        @Nested
        @DisplayName("isAvailable")
        class IsAvailableTests {

            @Test
            @DisplayName("should return true when Redis is available")
            void shouldReturnTrueWhenRedisIsAvailable() {
                // Given
                when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

                // When
                boolean available = cache.isAvailable();

                // Then
                assertThat(available).isTrue();
            }

            @Test
            @DisplayName("should return false when Redis throws exception")
            void shouldReturnFalseWhenRedisThrowsException() {
                // Given
                when(redisTemplate.execute(any(RedisCallback.class)))
                        .thenThrow(new RuntimeException("Connection failed"));

                // When
                boolean available = cache.isAvailable();

                // Then
                assertThat(available).isFalse();
            }
        }

        @Nested
        @DisplayName("Locking")
        class LockingTests {

            @Test
            @DisplayName("should acquire lock using Redis SETNX")
            void shouldAcquireLockUsingRedisSetnx() {
                // Given
                when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                        .thenReturn(true);

                // When
                boolean acquired = cache.tryLock("test_lock", 60);

                // Then
                assertThat(acquired).isTrue();
                verify(valueOperations).setIfAbsent(
                        eq("geo:lock:test_lock"),
                        anyString(),
                        eq(60L),
                        eq(TimeUnit.SECONDS)
                );
            }

            @Test
            @DisplayName("should fail to acquire lock when already held")
            void shouldFailToAcquireLockWhenAlreadyHeld() {
                // Given
                when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                        .thenReturn(false);

                // When
                boolean acquired = cache.tryLock("test_lock", 60);

                // Then
                assertThat(acquired).isFalse();
            }

            @Test
            @DisplayName("should release lock by deleting key")
            void shouldReleaseLockByDeletingKey() {
                // When
                cache.releaseLock("test_lock");

                // Then
                verify(redisTemplate).delete("geo:lock:test_lock");
            }
        }
    }

    // ========================================================================
    // GeoLocationFacade Tests
    // ========================================================================
    @Nested
    @DisplayName("GeoLocationFacade Tests")
    class GeoLocationFacadeTests {

        @Mock
        private MaxMindDatabaseService databaseService;

        @Mock
        private GeoLocationCache cache;

        @Mock
        private TravelPatternService travelPatternService;

        @Mock
        private GeoIpStorageService storageService;

        private GeoLocationFacade facade;

        @BeforeEach
        void setUp() {
            facade = new GeoLocationFacade(databaseService, cache, travelPatternService);
        }

        @Nested
        @DisplayName("getLocation")
        class GetLocationTests {

            @Test
            @DisplayName("should return cached location when available")
            void shouldReturnCachedLocationWhenAvailable() {
                // Given
                String ip = "1.1.1.1";
                GeoLocation cachedLocation = createLocation("US", "New York", 40.7128, -74.0060);

                when(cache.getAsync(ip))
                        .thenReturn(CompletableFuture.completedFuture(cachedLocation));

                // When
                GeoLocation result = facade.getLocation(ip, 5000);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getCountryCode()).isEqualTo("US");
                verify(databaseService, never()).lookupCityAsync(anyString());
            }

            @Test
            @DisplayName("should lookup in database on cache miss")
            void shouldLookupInDatabaseOnCacheMiss() {
                // Given
                String ip = "1.1.1.1";
                GeoLocation lookupResult = createLocation("GB", "London", 51.5074, -0.1278);

                when(cache.getAsync(ip))
                        .thenReturn(CompletableFuture.completedFuture(null));
                when(databaseService.lookupCityAsync(ip))
                        .thenReturn(CompletableFuture.completedFuture(null));

                // When
                GeoLocation result = facade.getLocation(ip, 5000);

                // Then
                verify(databaseService).lookupCityAsync(ip);
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should return unknown location for null or empty IP")
            void shouldReturnUnknownLocationForNullOrEmptyIp(String ip) {
                // When
                GeoLocation result = facade.getLocation(ip, 5000);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.isKnown()).isFalse();
            }

            @Test
            @DisplayName("should return unknown location on timeout")
            void shouldReturnUnknownLocationOnTimeout() {
                // Given
                String ip = "1.1.1.1";

                // Create a CompletableFuture that never completes
                CompletableFuture<GeoLocation> neverCompletes = new CompletableFuture<>();

                when(cache.getAsync(ip)).thenReturn(neverCompletes);

                // When - Use very short timeout
                GeoLocation result = facade.getLocation(ip, 1);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.isKnown()).isFalse();
            }
        }

        @Nested
        @DisplayName("checkImpossibleTravel")
        class CheckImpossibleTravelTests {

            @Test
            @DisplayName("should return false for same IP")
            void shouldReturnFalseForSameIp() throws Exception {
                // Given
                String ip = "1.1.1.1";

                // When
                CompletableFuture<Boolean> future =
                        facade.checkImpossibleTravel(123L, ip, ip, 30);
                Boolean result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertThat(result).isFalse();
                verify(cache, never()).getAsync(anyString());
            }

            @Test
            @DisplayName("should delegate to travel pattern service")
            void shouldDelegateToTravelPatternService() throws Exception {
                // Given
                String fromIp = "1.1.1.1";
                String toIp = "2.2.2.2";
                GeoLocation fromLocation = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation toLocation = createLocation("GB", "London", 51.5074, -0.1278);

                when(cache.getAsync(fromIp))
                        .thenReturn(CompletableFuture.completedFuture(fromLocation));
                when(cache.getAsync(toIp))
                        .thenReturn(CompletableFuture.completedFuture(toLocation));
                when(travelPatternService.isImpossibleTravel(fromLocation, toLocation, 30))
                        .thenReturn(true);
                when(travelPatternService.createTravelEvent(anyLong(), any(), any(), anyLong()))
                        .thenReturn(new HashMap<>());

                // When
                CompletableFuture<Boolean> future =
                        facade.checkImpossibleTravel(123L, fromIp, toIp, 30);
                Boolean result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertThat(result).isTrue();
                verify(travelPatternService).isImpossibleTravel(fromLocation, toLocation, 30);
            }

            @Test
            @DisplayName("should record travel pattern when userId provided")
            void shouldRecordTravelPatternWhenUserIdProvided() throws Exception {
                // Given
                String fromIp = "1.1.1.1";
                String toIp = "2.2.2.2";
                Long userId = 123L;
                GeoLocation fromLocation = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation toLocation = createLocation("US", "Boston", 42.3601, -71.0589);
                Map<String, Object> event = new HashMap<>();

                when(cache.getAsync(fromIp))
                        .thenReturn(CompletableFuture.completedFuture(fromLocation));
                when(cache.getAsync(toIp))
                        .thenReturn(CompletableFuture.completedFuture(toLocation));
                when(travelPatternService.isImpossibleTravel(any(), any(), anyLong()))
                        .thenReturn(false);
                when(travelPatternService.createTravelEvent(eq(userId), any(), any(), anyLong()))
                        .thenReturn(event);

                // When
                CompletableFuture<Boolean> future =
                        facade.checkImpossibleTravel(userId, fromIp, toIp, 30);
                future.get(5, TimeUnit.SECONDS);

                // Then
                verify(cache).recordTravelPattern(userId, event);
            }

            @Test
            @DisplayName("should not record travel pattern when userId is null")
            void shouldNotRecordTravelPatternWhenUserIdIsNull() throws Exception {
                // Given
                String fromIp = "1.1.1.1";
                String toIp = "2.2.2.2";
                GeoLocation fromLocation = createLocation("US", "New York", 40.7128, -74.0060);
                GeoLocation toLocation = createLocation("US", "Boston", 42.3601, -71.0589);

                when(cache.getAsync(fromIp))
                        .thenReturn(CompletableFuture.completedFuture(fromLocation));
                when(cache.getAsync(toIp))
                        .thenReturn(CompletableFuture.completedFuture(toLocation));
                when(travelPatternService.isImpossibleTravel(any(), any(), anyLong()))
                        .thenReturn(false);

                // When
                CompletableFuture<Boolean> future =
                        facade.checkImpossibleTravel(null, fromIp, toIp, 30);
                future.get(5, TimeUnit.SECONDS);

                // Then
                verify(cache, never()).recordTravelPattern(anyLong(), any());
            }
        }

        @Nested
        @DisplayName("getMetrics")
        class GetMetricsTests {

            @Test
            @DisplayName("should combine cache and database metrics")
            void shouldCombineCacheAndDatabaseMetrics() {
                // Given
                Map<String, Object> cacheMetrics = new HashMap<>();
                cacheMetrics.put("cacheHits", 100L);
                cacheMetrics.put("cacheMisses", 20L);

                MaxMindDatabaseService.DatabaseInfo dbInfo = MaxMindDatabaseService.DatabaseInfo.builder()
                        .ready(true)
                        .path("/path/to/db")
                        .sizeBytes(50000000L)
                        .build();

                when(cache.getMetrics()).thenReturn(cacheMetrics);
                when(databaseService.getDatabaseInfo()).thenReturn(dbInfo);
                when(cache.isAvailable()).thenReturn(true);

                // When
                Map<String, Object> metrics = facade.getMetrics();

                // Then
                assertThat(metrics).containsKey("cacheHits");
                assertThat(metrics).containsKey("databaseReady");
                assertThat(metrics).containsKey("databasePath");
                assertThat(metrics).containsKey("cacheAvailable");
                assertThat(metrics.get("databaseReady")).isEqualTo(true);
            }
        }

        @Nested
        @DisplayName("updateGeoIpDatabase")
        class UpdateGeoIpDatabaseTests {

            @Test
            @DisplayName("should acquire lock before updating database")
            void shouldAcquireLockBeforeUpdatingDatabase() {
                // Given
                when(databaseService.isDatabaseReady()).thenReturn(true);
                when(cache.tryLock("database_update", 600)).thenReturn(true);

                // When
                facade.updateGeoIpDatabase();

                // Then
                verify(cache).tryLock("database_update", 600);
                verify(databaseService).updateDatabase();
                verify(cache).releaseLock("database_update");
            }

            @Test
            @DisplayName("should skip update when lock not acquired")
            void shouldSkipUpdateWhenLockNotAcquired() {
                // Given
                when(databaseService.isDatabaseReady()).thenReturn(true);
                when(cache.tryLock("database_update", 600)).thenReturn(false);

                // When
                facade.updateGeoIpDatabase();

                // Then
                verify(databaseService, never()).updateDatabase();
            }
        }

        @Nested
        @DisplayName("cleanExpiredCache")
        class CleanExpiredCacheTests {

            @Test
            @DisplayName("should log metrics during cleanup")
            void shouldLogMetricsDuringCleanup() {
                // Given
                Map<String, Object> cacheMetrics = new HashMap<>();
                cacheMetrics.put("cacheHits", 100L);

                MaxMindDatabaseService.DatabaseInfo dbInfo = MaxMindDatabaseService.DatabaseInfo.builder()
                        .ready(true)
                        .path("/path/to/db")
                        .sizeBytes(50000000L)
                        .build();

                when(cache.getMetrics()).thenReturn(cacheMetrics);
                when(databaseService.getDatabaseInfo()).thenReturn(dbInfo);
                when(cache.isAvailable()).thenReturn(true);

                // When
                facade.cleanExpiredCache();

                // Then
                verify(cache).getMetrics();
            }
        }
    }

    // ========================================================================
    // GeoLocation Model Tests
    // ========================================================================
    @Nested
    @DisplayName("GeoLocation Model Tests")
    class GeoLocationModelTests {

        @Test
        @DisplayName("should create unknown location with correct defaults")
        void shouldCreateUnknownLocationWithCorrectDefaults() {
            // When
            GeoLocation unknown = GeoLocation.unknown("1.1.1.1");

            // Then
            assertThat(unknown.getIp()).isEqualTo("1.1.1.1");
            assertThat(unknown.getCountry()).isEqualTo("Unknown");
            assertThat(unknown.getCountryCode()).isEqualTo("XX");
            assertThat(unknown.getCity()).isEqualTo("Unknown");
            assertThat(unknown.isKnown()).isFalse();
        }

        @Test
        @DisplayName("should identify known location correctly")
        void shouldIdentifyKnownLocationCorrectly() {
            // Given
            GeoLocation known = GeoLocation.builder()
                    .country("United States")
                    .countryCode("US")
                    .city("New York")
                    .build();

            // Then
            assertThat(known.isKnown()).isTrue();
        }

        @Test
        @DisplayName("should identify unknown location by Unknown country")
        void shouldIdentifyUnknownLocationByUnknownCountry() {
            // Given
            GeoLocation unknown = GeoLocation.builder()
                    .country("Unknown")
                    .countryCode("US")
                    .build();

            // Then
            assertThat(unknown.isKnown()).isFalse();
        }

        @Test
        @DisplayName("should identify unknown location by XX country code")
        void shouldIdentifyUnknownLocationByXxCountryCode() {
            // Given
            GeoLocation unknown = GeoLocation.builder()
                    .country("Some Country")
                    .countryCode("XX")
                    .build();

            // Then
            assertThat(unknown.isKnown()).isFalse();
        }

        @Test
        @DisplayName("should build location with all fields")
        void shouldBuildLocationWithAllFields() {
            // When
            GeoLocation location = GeoLocation.builder()
                    .ip("1.1.1.1")
                    .country("United States")
                    .countryCode("US")
                    .city("New York")
                    .region("New York")
                    .latitude(40.7128)
                    .longitude(-74.0060)
                    .postalCode("10001")
                    .timezone("America/New_York")
                    .ispName("Cloudflare")
                    .isVpn(false)
                    .isTor(false)
                    .isProxy(false)
                    .build();

            // Then
            assertThat(location.getIp()).isEqualTo("1.1.1.1");
            assertThat(location.getCountry()).isEqualTo("United States");
            assertThat(location.getCountryCode()).isEqualTo("US");
            assertThat(location.getCity()).isEqualTo("New York");
            assertThat(location.getRegion()).isEqualTo("New York");
            assertThat(location.getLatitude()).isEqualTo(40.7128);
            assertThat(location.getLongitude()).isEqualTo(-74.0060);
            assertThat(location.getPostalCode()).isEqualTo("10001");
            assertThat(location.getTimezone()).isEqualTo("America/New_York");
            assertThat(location.getIspName()).isEqualTo("Cloudflare");
            assertThat(location.getIsVpn()).isFalse();
            assertThat(location.getIsTor()).isFalse();
            assertThat(location.getIsProxy()).isFalse();
        }
    }

    // ========================================================================
    // MaxMindDatabaseService Tests (limited - no actual database)
    // ========================================================================
    @Nested
    @DisplayName("MaxMindDatabaseService Tests")
    class MaxMindDatabaseServiceTests {

        @Nested
        @DisplayName("DatabaseInfo")
        class DatabaseInfoTests {

            @Test
            @DisplayName("should build DatabaseInfo correctly")
            void shouldBuildDatabaseInfoCorrectly() {
                // When
                MaxMindDatabaseService.DatabaseInfo info = MaxMindDatabaseService.DatabaseInfo.builder()
                        .exists(true)
                        .path("/path/to/db.mmdb")
                        .sizeBytes(50000000L)
                        .lastModified(System.currentTimeMillis())
                        .ready(true)
                        .build();

                // Then
                assertThat(info.isExists()).isTrue();
                assertThat(info.getPath()).isEqualTo("/path/to/db.mmdb");
                assertThat(info.getSizeBytes()).isEqualTo(50000000L);
                assertThat(info.isReady()).isTrue();
            }
        }
    }

    // ========================================================================
    // Edge Cases and Error Handling
    // ========================================================================
    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Nested
        @DisplayName("IP Address Handling")
        class IpAddressHandlingTests {

            @Test
            @DisplayName("should handle IPv4 addresses")
            void shouldHandleIpv4Addresses() {
                // Given
                InMemoryGeoLocationCache cache = new InMemoryGeoLocationCache();
                ReflectionTestUtils.setField(cache, "cacheTtlDays", 7);
                ReflectionTestUtils.setField(cache, "maxEntries", 100);

                String ipv4 = "192.168.1.1";
                GeoLocation location = createLocation("US", "Test", 40.0, -74.0);

                // When
                cache.put(ipv4, location);
                GeoLocation retrieved = cache.get(ipv4);

                // Then
                assertThat(retrieved).isNotNull();
            }

            @Test
            @DisplayName("should handle IPv6 addresses")
            void shouldHandleIpv6Addresses() {
                // Given
                InMemoryGeoLocationCache cache = new InMemoryGeoLocationCache();
                ReflectionTestUtils.setField(cache, "cacheTtlDays", 7);
                ReflectionTestUtils.setField(cache, "maxEntries", 100);

                String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
                GeoLocation location = createLocation("GB", "Test", 51.0, -0.1);

                // When
                cache.put(ipv6, location);
                GeoLocation retrieved = cache.get(ipv6);

                // Then
                assertThat(retrieved).isNotNull();
            }

            @ParameterizedTest
            @ValueSource(strings = {
                "127.0.0.1",
                "10.0.0.1",
                "172.16.0.1",
                "192.168.0.1",
                "::1",
                "fe80::1"
            })
            @DisplayName("should handle private and loopback addresses")
            void shouldHandlePrivateAndLoopbackAddresses(String ip) {
                // Given
                InMemoryGeoLocationCache cache = new InMemoryGeoLocationCache();
                ReflectionTestUtils.setField(cache, "cacheTtlDays", 7);
                ReflectionTestUtils.setField(cache, "maxEntries", 100);

                GeoLocation location = createLocation("XX", "Private", 0.0, 0.0);

                // When
                cache.put(ip, location);
                GeoLocation retrieved = cache.get(ip);

                // Then
                assertThat(retrieved).isNotNull();
            }
        }

        @Nested
        @DisplayName("Coordinate Edge Cases")
        class CoordinateEdgeCasesTests {

            private TravelPatternService service;

            @BeforeEach
            void setUp() {
                service = new TravelPatternService();
                ReflectionTestUtils.setField(service, "maxTravelSpeedKmh", 500);
                ReflectionTestUtils.setField(service, "minMinutesForCheck", 10);
            }

            @Test
            @DisplayName("should handle antipodal points (maximum distance)")
            void shouldHandleAntipodalPoints() {
                // Given - Points on opposite sides of Earth
                GeoLocation north = createLocation("XX", "North Pole", 90.0, 0.0);
                GeoLocation south = createLocation("XX", "South Pole", -90.0, 0.0);

                // When
                double distance = service.calculateDistance(north, south);

                // Then - Should be approximately 20000 km (half Earth circumference)
                assertThat(distance).isCloseTo(20000.0, within(500.0));
            }

            @Test
            @DisplayName("should handle international date line crossing")
            void shouldHandleInternationalDateLineCrossing() {
                // Given
                GeoLocation east = createLocation("XX", "East", 0.0, 179.0);
                GeoLocation west = createLocation("XX", "West", 0.0, -179.0);

                // When
                double distance = service.calculateDistance(east, west);

                // Then - Should be a short distance (crossing date line)
                assertThat(distance).isLessThan(500.0);
            }

            @Test
            @DisplayName("should handle equator crossing")
            void shouldHandleEquatorCrossing() {
                // Given
                GeoLocation north = createLocation("XX", "North", 1.0, 0.0);
                GeoLocation south = createLocation("XX", "South", -1.0, 0.0);

                // When
                double distance = service.calculateDistance(north, south);

                // Then - Should be approximately 222 km (2 degrees latitude)
                assertThat(distance).isCloseTo(222.0, within(10.0));
            }
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private static GeoLocation createLocation(String countryCode, String city, double lat, double lon) {
        return GeoLocation.builder()
                .country(getCountryName(countryCode))
                .countryCode(countryCode)
                .city(city)
                .latitude(lat)
                .longitude(lon)
                .isVpn(false)
                .isProxy(false)
                .isTor(false)
                .build();
    }

    private static String getCountryName(String code) {
        switch (code) {
            case "US": return "United States";
            case "GB": return "United Kingdom";
            case "DE": return "Germany";
            case "FR": return "France";
            case "JP": return "Japan";
            default: return "Country " + code;
        }
    }
}
