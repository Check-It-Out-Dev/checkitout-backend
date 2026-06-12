package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.security.GeoLocation;
import com.sm.instagram.platform.common.security.GeoLocationGdprService;
import com.sm.instagram.platform.common.security.GeoLocationService;
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
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GeoLocation services.
 * Tests GeoLocationService interface, GeoLocationGdprService, and GeoLocation entity.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GeoLocation Service Unit Tests")
class GeoLocationServiceUnitTest {

    // ========================================================================
    // GeoLocation Entity Tests
    // ========================================================================
    @Nested
    @DisplayName("GeoLocation Entity Tests")
    class GeoLocationEntityTests {

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should create GeoLocation with all fields using builder")
            void shouldCreateGeoLocationWithAllFieldsUsingBuilder() {
                // When
                GeoLocation location = GeoLocation.builder()
                        .ip("192.168.1.1")
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
                assertThat(location.getIp()).isEqualTo("192.168.1.1");
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

            @Test
            @DisplayName("should create GeoLocation with minimal fields")
            void shouldCreateGeoLocationWithMinimalFields() {
                // When
                GeoLocation location = GeoLocation.builder()
                        .ip("1.1.1.1")
                        .country("Unknown")
                        .countryCode("XX")
                        .build();

                // Then
                assertThat(location.getIp()).isEqualTo("1.1.1.1");
                assertThat(location.getCountry()).isEqualTo("Unknown");
                assertThat(location.getCountryCode()).isEqualTo("XX");
                assertThat(location.getCity()).isNull();
                assertThat(location.getLatitude()).isNull();
            }

            @Test
            @DisplayName("should create empty GeoLocation using no-args constructor")
            void shouldCreateEmptyGeoLocationUsingNoArgsConstructor() {
                // When
                GeoLocation location = new GeoLocation();

                // Then
                assertThat(location.getIp()).isNull();
                assertThat(location.getCountry()).isNull();
                assertThat(location.getCountryCode()).isNull();
            }

            @Test
            @DisplayName("should create GeoLocation using all-args constructor")
            void shouldCreateGeoLocationUsingAllArgsConstructor() {
                // When
                GeoLocation location = new GeoLocation(
                        "1.1.1.1",
                        "United States",
                        "US",
                        "New York",
                        "NY",
                        40.7128,
                        -74.0060,
                        "10001",
                        "America/New_York",
                        "Cloudflare",
                        false,
                        false,
                        false
                );

                // Then
                assertThat(location.getIp()).isEqualTo("1.1.1.1");
                assertThat(location.getCountry()).isEqualTo("United States");
                assertThat(location.getCountryCode()).isEqualTo("US");
                assertThat(location.getCity()).isEqualTo("New York");
                assertThat(location.getRegion()).isEqualTo("NY");
            }
        }

        @Nested
        @DisplayName("Unknown Location Factory Tests")
        class UnknownLocationFactoryTests {

            @Test
            @DisplayName("should create unknown location with IP address")
            void shouldCreateUnknownLocationWithIpAddress() {
                // When
                GeoLocation unknown = GeoLocation.unknown("192.168.1.1");

                // Then
                assertThat(unknown.getIp()).isEqualTo("192.168.1.1");
                assertThat(unknown.getCountry()).isEqualTo("Unknown");
                assertThat(unknown.getCountryCode()).isEqualTo("XX");
                assertThat(unknown.getCity()).isEqualTo("Unknown");
                assertThat(unknown.getRegion()).isEqualTo("Unknown");
                assertThat(unknown.getLatitude()).isEqualTo(0.0);
                assertThat(unknown.getLongitude()).isEqualTo(0.0);
            }

            @Test
            @DisplayName("should create unknown location with null IP")
            void shouldCreateUnknownLocationWithNullIp() {
                // When
                GeoLocation unknown = GeoLocation.unknown(null);

                // Then
                assertThat(unknown.getIp()).isNull();
                assertThat(unknown.getCountry()).isEqualTo("Unknown");
                assertThat(unknown.getCountryCode()).isEqualTo("XX");
            }

            @Test
            @DisplayName("should create unknown location with empty IP")
            void shouldCreateUnknownLocationWithEmptyIp() {
                // When
                GeoLocation unknown = GeoLocation.unknown("");

                // Then
                assertThat(unknown.getIp()).isEmpty();
                assertThat(unknown.getCountry()).isEqualTo("Unknown");
            }

            @ParameterizedTest
            @ValueSource(strings = {"1.1.1.1", "8.8.8.8", "192.168.0.1", "2001:db8::1"})
            @DisplayName("should create unknown location for various IP formats")
            void shouldCreateUnknownLocationForVariousIpFormats(String ip) {
                // When
                GeoLocation unknown = GeoLocation.unknown(ip);

                // Then
                assertThat(unknown.getIp()).isEqualTo(ip);
                assertThat(unknown.isKnown()).isFalse();
            }
        }

        @Nested
        @DisplayName("isKnown Method Tests")
        class IsKnownMethodTests {

            @Test
            @DisplayName("should return true for known location")
            void shouldReturnTrueForKnownLocation() {
                // Given
                GeoLocation location = GeoLocation.builder()
                        .country("United States")
                        .countryCode("US")
                        .build();

                // When/Then
                assertThat(location.isKnown()).isTrue();
            }

            @Test
            @DisplayName("should return false when country is Unknown")
            void shouldReturnFalseWhenCountryIsUnknown() {
                // Given
                GeoLocation location = GeoLocation.builder()
                        .country("Unknown")
                        .countryCode("US")
                        .build();

                // When/Then
                assertThat(location.isKnown()).isFalse();
            }

            @Test
            @DisplayName("should return false when country code is XX")
            void shouldReturnFalseWhenCountryCodeIsXx() {
                // Given
                GeoLocation location = GeoLocation.builder()
                        .country("United States")
                        .countryCode("XX")
                        .build();

                // When/Then
                assertThat(location.isKnown()).isFalse();
            }

            @Test
            @DisplayName("should return false when both country and code are unknown")
            void shouldReturnFalseWhenBothCountryAndCodeAreUnknown() {
                // Given
                GeoLocation location = GeoLocation.builder()
                        .country("Unknown")
                        .countryCode("XX")
                        .build();

                // When/Then
                assertThat(location.isKnown()).isFalse();
            }

            @Test
            @DisplayName("should return false for null country")
            void shouldReturnFalseForNullCountry() {
                // Given
                GeoLocation location = GeoLocation.builder()
                        .country(null)
                        .countryCode("US")
                        .build();

                // When/Then
                assertThat(location.isKnown()).isTrue(); // null != "Unknown"
            }

            @Test
            @DisplayName("should return false for null country code")
            void shouldReturnFalseForNullCountryCode() {
                // Given
                GeoLocation location = GeoLocation.builder()
                        .country("United States")
                        .countryCode(null)
                        .build();

                // When/Then
                assertThat(location.isKnown()).isTrue(); // null != "XX"
            }
        }

        @Nested
        @DisplayName("Setter Tests")
        class SetterTests {

            @Test
            @DisplayName("should set and get IP address")
            void shouldSetAndGetIpAddress() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setIp("10.0.0.1");

                // Then
                assertThat(location.getIp()).isEqualTo("10.0.0.1");
            }

            @Test
            @DisplayName("should set and get country")
            void shouldSetAndGetCountry() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setCountry("Germany");

                // Then
                assertThat(location.getCountry()).isEqualTo("Germany");
            }

            @Test
            @DisplayName("should set and get country code")
            void shouldSetAndGetCountryCode() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setCountryCode("DE");

                // Then
                assertThat(location.getCountryCode()).isEqualTo("DE");
            }

            @Test
            @DisplayName("should set and get city")
            void shouldSetAndGetCity() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setCity("Berlin");

                // Then
                assertThat(location.getCity()).isEqualTo("Berlin");
            }

            @Test
            @DisplayName("should set and get region")
            void shouldSetAndGetRegion() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setRegion("Brandenburg");

                // Then
                assertThat(location.getRegion()).isEqualTo("Brandenburg");
            }

            @Test
            @DisplayName("should set and get latitude")
            void shouldSetAndGetLatitude() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setLatitude(52.5200);

                // Then
                assertThat(location.getLatitude()).isEqualTo(52.5200);
            }

            @Test
            @DisplayName("should set and get longitude")
            void shouldSetAndGetLongitude() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setLongitude(13.4050);

                // Then
                assertThat(location.getLongitude()).isEqualTo(13.4050);
            }

            @Test
            @DisplayName("should set and get postal code")
            void shouldSetAndGetPostalCode() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setPostalCode("10115");

                // Then
                assertThat(location.getPostalCode()).isEqualTo("10115");
            }

            @Test
            @DisplayName("should set and get timezone")
            void shouldSetAndGetTimezone() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setTimezone("Europe/Berlin");

                // Then
                assertThat(location.getTimezone()).isEqualTo("Europe/Berlin");
            }

            @Test
            @DisplayName("should set and get ISP name")
            void shouldSetAndGetIspName() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setIspName("Deutsche Telekom");

                // Then
                assertThat(location.getIspName()).isEqualTo("Deutsche Telekom");
            }

            @Test
            @DisplayName("should set and get VPN flag")
            void shouldSetAndGetVpnFlag() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setIsVpn(true);

                // Then
                assertThat(location.getIsVpn()).isTrue();
            }

            @Test
            @DisplayName("should set and get Tor flag")
            void shouldSetAndGetTorFlag() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setIsTor(true);

                // Then
                assertThat(location.getIsTor()).isTrue();
            }

            @Test
            @DisplayName("should set and get Proxy flag")
            void shouldSetAndGetProxyFlag() {
                // Given
                GeoLocation location = new GeoLocation();

                // When
                location.setIsProxy(true);

                // Then
                assertThat(location.getIsProxy()).isTrue();
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsAndHashCodeTests {

            @Test
            @DisplayName("should be equal for same data")
            void shouldBeEqualForSameData() {
                // Given
                GeoLocation location1 = GeoLocation.builder()
                        .ip("1.1.1.1")
                        .country("United States")
                        .countryCode("US")
                        .build();

                GeoLocation location2 = GeoLocation.builder()
                        .ip("1.1.1.1")
                        .country("United States")
                        .countryCode("US")
                        .build();

                // Then
                assertThat(location1).isEqualTo(location2);
                assertThat(location1.hashCode()).isEqualTo(location2.hashCode());
            }

            @Test
            @DisplayName("should not be equal for different data")
            void shouldNotBeEqualForDifferentData() {
                // Given
                GeoLocation location1 = GeoLocation.builder()
                        .ip("1.1.1.1")
                        .country("United States")
                        .countryCode("US")
                        .build();

                GeoLocation location2 = GeoLocation.builder()
                        .ip("2.2.2.2")
                        .country("United Kingdom")
                        .countryCode("GB")
                        .build();

                // Then
                assertThat(location1).isNotEqualTo(location2);
            }

            @Test
            @DisplayName("should have toString implementation")
            void shouldHaveToStringImplementation() {
                // Given
                GeoLocation location = GeoLocation.builder()
                        .ip("1.1.1.1")
                        .country("United States")
                        .countryCode("US")
                        .build();

                // When
                String toString = location.toString();

                // Then
                assertThat(toString).contains("1.1.1.1");
                assertThat(toString).contains("United States");
                assertThat(toString).contains("US");
            }
        }

        @Nested
        @DisplayName("Edge Cases Tests")
        class EdgeCasesTests {

            @Test
            @DisplayName("should handle extreme latitude values")
            void shouldHandleExtremeLatitudeValues() {
                // When
                GeoLocation northPole = GeoLocation.builder()
                        .latitude(90.0)
                        .longitude(0.0)
                        .build();

                GeoLocation southPole = GeoLocation.builder()
                        .latitude(-90.0)
                        .longitude(0.0)
                        .build();

                // Then
                assertThat(northPole.getLatitude()).isEqualTo(90.0);
                assertThat(southPole.getLatitude()).isEqualTo(-90.0);
            }

            @Test
            @DisplayName("should handle extreme longitude values")
            void shouldHandleExtremeLongitudeValues() {
                // When
                GeoLocation east = GeoLocation.builder()
                        .latitude(0.0)
                        .longitude(180.0)
                        .build();

                GeoLocation west = GeoLocation.builder()
                        .latitude(0.0)
                        .longitude(-180.0)
                        .build();

                // Then
                assertThat(east.getLongitude()).isEqualTo(180.0);
                assertThat(west.getLongitude()).isEqualTo(-180.0);
            }

            @Test
            @DisplayName("should handle zero coordinates")
            void shouldHandleZeroCoordinates() {
                // When
                GeoLocation nullIsland = GeoLocation.builder()
                        .latitude(0.0)
                        .longitude(0.0)
                        .build();

                // Then
                assertThat(nullIsland.getLatitude()).isEqualTo(0.0);
                assertThat(nullIsland.getLongitude()).isEqualTo(0.0);
            }

            @Test
            @DisplayName("should handle very long city names")
            void shouldHandleVeryLongCityNames() {
                // Given
                String longCityName = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch";

                // When
                GeoLocation location = GeoLocation.builder()
                        .city(longCityName)
                        .build();

                // Then
                assertThat(location.getCity()).isEqualTo(longCityName);
            }

            @Test
            @DisplayName("should handle special characters in city name")
            void shouldHandleSpecialCharactersInCityName() {
                // Given
                String cityWithSpecialChars = "Sao Paulo";

                // When
                GeoLocation location = GeoLocation.builder()
                        .city(cityWithSpecialChars)
                        .build();

                // Then
                assertThat(location.getCity()).isEqualTo(cityWithSpecialChars);
            }
        }
    }

    // ========================================================================
    // GeoLocationGdprService Tests
    // ========================================================================
    @Nested
    @DisplayName("GeoLocationGdprService Tests")
    class GeoLocationGdprServiceTests {

        @Mock
        private RedisTemplate<String, String> redisTemplate;

        @Mock
        private ObjectMapper objectMapper;

        @Mock
        private ValueOperations<String, String> valueOperations;

        @Mock
        private ListOperations<String, String> listOperations;

        private GeoLocationGdprService gdprService;

        @BeforeEach
        void setUp() {
            gdprService = new GeoLocationGdprService(redisTemplate, objectMapper);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(redisTemplate.opsForList()).thenReturn(listOperations);
        }

        @Nested
        @DisplayName("deleteIpData Tests")
        class DeleteIpDataTests {

            @Test
            @DisplayName("should return zero for null IP")
            void shouldReturnZeroForNullIp() {
                // When
                int deleted = gdprService.deleteIpData(null);

                // Then
                assertThat(deleted).isZero();
                verify(redisTemplate, never()).keys(anyString());
            }

            @Test
            @DisplayName("should return zero for empty IP")
            void shouldReturnZeroForEmptyIp() {
                // When
                int deleted = gdprService.deleteIpData("");

                // Then
                assertThat(deleted).isZero();
                verify(redisTemplate, never()).keys(anyString());
            }

            @Test
            @DisplayName("should delete IP data and return count")
            void shouldDeleteIpDataAndReturnCount() {
                // Given
                String ip = "192.168.1.1";
                Set<String> keys = new HashSet<>();
                keys.add("geo:ip:192_168_1_1:data1");
                keys.add("geo:ip:192_168_1_1:data2");

                when(redisTemplate.keys(anyString())).thenReturn(keys);
                when(redisTemplate.delete(keys)).thenReturn(2L);

                // When
                int deleted = gdprService.deleteIpData(ip);

                // Then
                assertThat(deleted).isEqualTo(2);
                verify(redisTemplate).delete(keys);
            }

            @Test
            @DisplayName("should return zero when no keys found")
            void shouldReturnZeroWhenNoKeysFound() {
                // Given
                when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

                // When
                int deleted = gdprService.deleteIpData("192.168.1.1");

                // Then
                assertThat(deleted).isZero();
                verify(redisTemplate, never()).delete(anySet());
            }

            @Test
            @DisplayName("should handle null keys response")
            void shouldHandleNullKeysResponse() {
                // Given
                when(redisTemplate.keys(anyString())).thenReturn(null);

                // When
                int deleted = gdprService.deleteIpData("192.168.1.1");

                // Then
                assertThat(deleted).isZero();
            }

            @Test
            @DisplayName("should handle null delete response")
            void shouldHandleNullDeleteResponse() {
                // Given
                Set<String> keys = new HashSet<>();
                keys.add("geo:ip:192_168_1_1:data1");

                when(redisTemplate.keys(anyString())).thenReturn(keys);
                when(redisTemplate.delete(keys)).thenReturn(null);

                // When
                int deleted = gdprService.deleteIpData("192.168.1.1");

                // Then
                assertThat(deleted).isZero();
            }
        }

        @Nested
        @DisplayName("deleteUserLocationData Tests")
        class DeleteUserLocationDataTests {

            @Test
            @DisplayName("should return zero for null userId")
            void shouldReturnZeroForNullUserId() {
                // When
                int deleted = gdprService.deleteUserLocationData(null);

                // Then
                assertThat(deleted).isZero();
                verify(redisTemplate, never()).keys(anyString());
            }

            @Test
            @DisplayName("should delete user location data and return count")
            void shouldDeleteUserLocationDataAndReturnCount() {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-15");
                keys.add("geo:travel:123:2024-01-16");
                keys.add("geo:travel:123:2024-01-17");

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(redisTemplate.delete(keys)).thenReturn(3L);

                // When
                int deleted = gdprService.deleteUserLocationData(userId);

                // Then
                assertThat(deleted).isEqualTo(3);
                verify(redisTemplate).delete(keys);
            }

            @Test
            @DisplayName("should return zero when no user data found")
            void shouldReturnZeroWhenNoUserDataFound() {
                // Given
                when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

                // When
                int deleted = gdprService.deleteUserLocationData(456L);

                // Then
                assertThat(deleted).isZero();
            }
        }

        @Nested
        @DisplayName("exportUserLocationData Tests")
        class ExportUserLocationDataTests {

            @Test
            @DisplayName("should return empty map for null userId")
            void shouldReturnEmptyMapForNullUserId() {
                // When
                Map<String, Object> result = gdprService.exportUserLocationData(null);

                // Then
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should export user location data with required fields")
            void shouldExportUserLocationDataWithRequiredFields() throws Exception {
                // Given
                Long userId = 123L;
                Set<String> keys = Collections.emptySet();

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);

                // When
                Map<String, Object> result = gdprService.exportUserLocationData(userId);

                // Then
                assertThat(result).containsKey("userId");
                assertThat(result).containsKey("exportDate");
                assertThat(result).containsKey("dataController");
                assertThat(result).containsKey("travelPatterns");
                assertThat(result).containsKey("totalRecords");

                assertThat(result.get("userId")).isEqualTo(userId);
                assertThat(result.get("dataController")).isEqualTo("CheckItOut Platform");
            }

            @Test
            @DisplayName("should include travel pattern data in export")
            void shouldIncludeTravelPatternDataInExport() throws Exception {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-15");

                List<String> events = Arrays.asList(
                        "{\"fromIp\":\"1.1.1.1\",\"toIp\":\"2.2.2.2\",\"speed\":100}"
                );

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(listOperations.range("geo:travel:123:2024-01-15", 0, -1)).thenReturn(events);

                Map<String, Object> parsedEvent = new HashMap<>();
                parsedEvent.put("fromIp", "1.1.1.1");
                parsedEvent.put("toIp", "2.2.2.2");
                parsedEvent.put("speed", 100);

                when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(parsedEvent);

                // When
                Map<String, Object> result = gdprService.exportUserLocationData(userId);

                // Then
                assertThat(result.get("totalRecords")).isEqualTo(1);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> travelPatterns =
                        (List<Map<String, Object>>) result.get("travelPatterns");
                assertThat(travelPatterns).hasSize(1);
            }

            @Test
            @DisplayName("should mask IP addresses in export")
            void shouldMaskIpAddressesInExport() throws Exception {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-15");

                List<String> events = Arrays.asList("{\"fromIp\":\"192.168.1.1\",\"toIp\":\"10.0.0.1\"}");

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(events);

                Map<String, Object> parsedEvent = new HashMap<>();
                parsedEvent.put("fromIp", "192.168.1.1");
                parsedEvent.put("toIp", "10.0.0.1");

                when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(parsedEvent);

                // When
                Map<String, Object> result = gdprService.exportUserLocationData(userId);

                // Then
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> travelPatterns =
                        (List<Map<String, Object>>) result.get("travelPatterns");

                assertThat(travelPatterns).isNotEmpty();
                assertThat(travelPatterns.get(0).get("fromIp").toString()).contains("xxx");
                assertThat(travelPatterns.get(0).get("toIp").toString()).contains("xxx");
            }

            @Test
            @DisplayName("should handle JSON parsing errors gracefully")
            void shouldHandleJsonParsingErrorsGracefully() throws Exception {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-15");

                List<String> events = Arrays.asList("invalid json");

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(events);
                when(objectMapper.readValue("invalid json", Map.class))
                        .thenThrow(mock(JsonProcessingException.class));

                // When
                Map<String, Object> result = gdprService.exportUserLocationData(userId);

                // Then
                assertThat(result.get("totalRecords")).isEqualTo(0);
            }

            @Test
            @DisplayName("should handle null events list")
            void shouldHandleNullEventsList() {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-15");

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(null);

                // When
                Map<String, Object> result = gdprService.exportUserLocationData(userId);

                // Then
                assertThat(result.get("totalRecords")).isEqualTo(0);
            }
        }

        @Nested
        @DisplayName("getDataRetentionSummary Tests")
        class GetDataRetentionSummaryTests {

            @Test
            @DisplayName("should return summary for user with data")
            void shouldReturnSummaryForUserWithData() {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-15");
                keys.add("geo:travel:123:2024-01-16");

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(listOperations.size("geo:travel:123:2024-01-15")).thenReturn(5L);
                when(listOperations.size("geo:travel:123:2024-01-16")).thenReturn(3L);

                // When
                Map<String, Object> summary = gdprService.getDataRetentionSummary(userId);

                // Then
                assertThat(summary).containsKey("userId");
                assertThat(summary).containsKey("timestamp");
                assertThat(summary).containsKey("travelPatternRecords");
                assertThat(summary).containsKey("retentionPeriod");
                assertThat(summary).containsKey("automaticDeletion");
                assertThat(summary).containsKey("gdprCompliant");

                assertThat(summary.get("userId")).isEqualTo(userId);
                assertThat(summary.get("travelPatternRecords")).isEqualTo(8);
                assertThat(summary.get("retentionPeriod")).isEqualTo("30 days");
                assertThat(summary.get("automaticDeletion")).isEqualTo(true);
                assertThat(summary.get("gdprCompliant")).isEqualTo(true);
            }

            @Test
            @DisplayName("should include oldest and newest data dates")
            void shouldIncludeOldestAndNewestDataDates() {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-10");
                keys.add("geo:travel:123:2024-01-15");
                keys.add("geo:travel:123:2024-01-20");

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(listOperations.size(anyString())).thenReturn(1L);

                // When
                Map<String, Object> summary = gdprService.getDataRetentionSummary(userId);

                // Then
                assertThat(summary).containsKey("oldestData");
                assertThat(summary).containsKey("newestData");
                assertThat(summary.get("oldestData")).isEqualTo("2024-01-10");
                assertThat(summary.get("newestData")).isEqualTo("2024-01-20");
            }

            @Test
            @DisplayName("should handle empty keys")
            void shouldHandleEmptyKeys() {
                // Given
                Long userId = 123L;

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(Collections.emptySet());

                // When
                Map<String, Object> summary = gdprService.getDataRetentionSummary(userId);

                // Then
                assertThat(summary.get("travelPatternRecords")).isEqualTo(0);
                assertThat(summary).doesNotContainKey("oldestData");
            }

            @Test
            @DisplayName("should handle null list size")
            void shouldHandleNullListSize() {
                // Given
                Long userId = 123L;
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:2024-01-15");

                when(redisTemplate.keys("geo:travel:123:*")).thenReturn(keys);
                when(listOperations.size(anyString())).thenReturn(null);

                // When
                Map<String, Object> summary = gdprService.getDataRetentionSummary(userId);

                // Then
                assertThat(summary.get("travelPatternRecords")).isEqualTo(0);
            }
        }

        @Nested
        @DisplayName("anonymizeOldData Tests")
        class AnonymizeOldDataTests {

            @Test
            @DisplayName("should delete data older than specified days")
            void shouldDeleteDataOlderThanSpecifiedDays() {
                // Given
                int olderThanDays = 30;
                LocalDate cutoffDate = LocalDate.now().minusDays(olderThanDays);
                String oldDateKey = "geo:travel:123:" + cutoffDate.minusDays(5);
                String recentDateKey = "geo:travel:123:" + LocalDate.now();

                Set<String> keys = new HashSet<>();
                keys.add(oldDateKey);
                keys.add(recentDateKey);

                when(redisTemplate.keys("geo:travel:*")).thenReturn(keys);
                when(redisTemplate.delete(oldDateKey)).thenReturn(true);

                // When
                int anonymized = gdprService.anonymizeOldData(olderThanDays);

                // Then
                assertThat(anonymized).isEqualTo(1);
                verify(redisTemplate).delete(oldDateKey);
                verify(redisTemplate, never()).delete(recentDateKey);
            }

            @Test
            @DisplayName("should return zero when no old data exists")
            void shouldReturnZeroWhenNoOldDataExists() {
                // Given
                String recentKey = "geo:travel:123:" + LocalDate.now();
                Set<String> keys = new HashSet<>();
                keys.add(recentKey);

                when(redisTemplate.keys("geo:travel:*")).thenReturn(keys);

                // When
                int anonymized = gdprService.anonymizeOldData(30);

                // Then
                assertThat(anonymized).isZero();
            }

            @Test
            @DisplayName("should handle invalid date format in key")
            void shouldHandleInvalidDateFormatInKey() {
                // Given
                Set<String> keys = new HashSet<>();
                keys.add("geo:travel:123:invalid-date");

                when(redisTemplate.keys("geo:travel:*")).thenReturn(keys);

                // When
                int anonymized = gdprService.anonymizeOldData(30);

                // Then
                assertThat(anonymized).isZero();
            }

            @Test
            @DisplayName("should handle null keys")
            void shouldHandleNullKeys() {
                // Given
                when(redisTemplate.keys("geo:travel:*")).thenReturn(null);

                // When
                int anonymized = gdprService.anonymizeOldData(30);

                // Then
                assertThat(anonymized).isZero();
            }
        }

        @Nested
        @DisplayName("getComplianceStatus Tests")
        class GetComplianceStatusTests {

            @Test
            @DisplayName("should return complete compliance status")
            void shouldReturnCompleteComplianceStatus() {
                // When
                Map<String, Object> status = gdprService.getComplianceStatus();

                // Then
                assertThat(status).containsKey("gdprCompliant");
                assertThat(status).containsKey("dataMinimization");
                assertThat(status).containsKey("purposeLimitation");
                assertThat(status).containsKey("storageHasUserData");
                assertThat(status).containsKey("automaticExpiration");
                assertThat(status).containsKey("expirationDays");
                assertThat(status).containsKey("travelDataRetention");
                assertThat(status).containsKey("dataCategories");
                assertThat(status).containsKey("implementedRights");
                assertThat(status).containsKey("auditingEnabled");
                assertThat(status).containsKey("encryptionAtRest");
                assertThat(status).containsKey("encryptionInTransit");

                assertThat(status.get("gdprCompliant")).isEqualTo(true);
                assertThat(status.get("dataMinimization")).isEqualTo(true);
                assertThat(status.get("expirationDays")).isEqualTo(7);
                assertThat(status.get("travelDataRetention")).isEqualTo(30);
            }

            @Test
            @DisplayName("should include implemented rights")
            void shouldIncludeImplementedRights() {
                // When
                Map<String, Object> status = gdprService.getComplianceStatus();

                // Then
                @SuppressWarnings("unchecked")
                List<String> rights = (List<String>) status.get("implementedRights");

                assertThat(rights).contains("Right to erasure (Article 17)");
                assertThat(rights).contains("Right to data portability (Article 20)");
                assertThat(rights).contains("Right to be informed (Article 13)");
                assertThat(rights).contains("Data minimization (Article 5)");
                assertThat(rights).contains("Storage limitation (Article 5)");
            }

            @Test
            @DisplayName("should include data categories with TTL")
            void shouldIncludeDataCategoriesWithTtl() {
                // When
                Map<String, Object> status = gdprService.getComplianceStatus();

                // Then
                @SuppressWarnings("unchecked")
                Map<String, String> categories = (Map<String, String>) status.get("dataCategories");

                assertThat(categories).containsKey("ipLocation");
                assertThat(categories).containsKey("travelPatterns");
                assertThat(categories).containsKey("impossibleTravel");

                assertThat(categories.get("ipLocation")).isEqualTo("7 days TTL");
                assertThat(categories.get("travelPatterns")).isEqualTo("30 days TTL");
            }
        }

        @Nested
        @DisplayName("Private Method Tests (via Reflection)")
        class PrivateMethodTests {

            @Test
            @DisplayName("should mask IPv4 address correctly")
            void shouldMaskIpv4AddressCorrectly() throws Exception {
                // Given
                Method maskIpMethod = GeoLocationGdprService.class
                        .getDeclaredMethod("maskIp", String.class);
                maskIpMethod.setAccessible(true);

                // When
                String masked = (String) maskIpMethod.invoke(gdprService, "192.168.1.100");

                // Then
                assertThat(masked).isEqualTo("192.168.1.xxx");
            }

            @Test
            @DisplayName("should mask IPv6 address correctly")
            void shouldMaskIpv6AddressCorrectly() throws Exception {
                // Given
                Method maskIpMethod = GeoLocationGdprService.class
                        .getDeclaredMethod("maskIp", String.class);
                maskIpMethod.setAccessible(true);

                // When
                String masked = (String) maskIpMethod.invoke(gdprService, "2001:db8:85a3::8a2e:370:7334");

                // Then
                assertThat(masked).endsWith(":xxxx");
            }

            @Test
            @DisplayName("should return unknown for null IP")
            void shouldReturnUnknownForNullIp() throws Exception {
                // Given
                Method maskIpMethod = GeoLocationGdprService.class
                        .getDeclaredMethod("maskIp", String.class);
                maskIpMethod.setAccessible(true);

                // When
                String masked = (String) maskIpMethod.invoke(gdprService, (Object) null);

                // Then
                assertThat(masked).isEqualTo("unknown");
            }

            @Test
            @DisplayName("should return masked for IP without separators")
            void shouldReturnMaskedForIpWithoutSeparators() throws Exception {
                // Given
                Method maskIpMethod = GeoLocationGdprService.class
                        .getDeclaredMethod("maskIp", String.class);
                maskIpMethod.setAccessible(true);

                // When
                String masked = (String) maskIpMethod.invoke(gdprService, "localhost");

                // Then
                assertThat(masked).isEqualTo("masked");
            }

            @Test
            @DisplayName("should mask sensitive data correctly")
            void shouldMaskSensitiveDataCorrectly() throws Exception {
                // Given
                Method maskMethod = GeoLocationGdprService.class
                        .getDeclaredMethod("maskSensitiveData", String.class);
                maskMethod.setAccessible(true);

                // When
                String masked = (String) maskMethod.invoke(gdprService, "1234567890");

                // Then
                assertThat(masked).isEqualTo("12***90");
            }

            @Test
            @DisplayName("should return stars for short data")
            void shouldReturnStarsForShortData() throws Exception {
                // Given
                Method maskMethod = GeoLocationGdprService.class
                        .getDeclaredMethod("maskSensitiveData", String.class);
                maskMethod.setAccessible(true);

                // When
                String masked = (String) maskMethod.invoke(gdprService, "123");

                // Then
                assertThat(masked).isEqualTo("****");
            }

            @Test
            @DisplayName("should return stars for null data")
            void shouldReturnStarsForNullData() throws Exception {
                // Given
                Method maskMethod = GeoLocationGdprService.class
                        .getDeclaredMethod("maskSensitiveData", String.class);
                maskMethod.setAccessible(true);

                // When
                String masked = (String) maskMethod.invoke(gdprService, (Object) null);

                // Then
                assertThat(masked).isEqualTo("****");
            }
        }

        @Nested
        @DisplayName("Audit Logging Tests")
        class AuditLoggingTests {

            @Test
            @DisplayName("should audit deletion operation")
            void shouldAuditDeletionOperation() throws Exception {
                // Given
                Set<String> keys = new HashSet<>();
                keys.add("geo:ip:192_168_1_1:data1");

                when(redisTemplate.keys(anyString())).thenReturn(keys);
                when(redisTemplate.delete(keys)).thenReturn(1L);
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");

                // When
                gdprService.deleteIpData("192.168.1.1");

                // Then
                verify(valueOperations).set(
                        argThat(key -> key.startsWith("geo:audit:deletion:")),
                        anyString()
                );
                verify(redisTemplate).expire(
                        argThat(key -> key.startsWith("geo:audit:deletion:")),
                        eq(90L),
                        eq(TimeUnit.DAYS)
                );
            }

            @Test
            @DisplayName("should handle audit write errors gracefully")
            void shouldHandleAuditWriteErrorsGracefully() throws Exception {
                // Given
                Set<String> keys = new HashSet<>();
                keys.add("geo:ip:192_168_1_1:data1");

                when(redisTemplate.keys(anyString())).thenReturn(keys);
                when(redisTemplate.delete(keys)).thenReturn(1L);
                when(objectMapper.writeValueAsString(any()))
                        .thenThrow(mock(JsonProcessingException.class));

                // When/Then - Should not throw
                assertThatCode(() -> gdprService.deleteIpData("192.168.1.1"))
                        .doesNotThrowAnyException();
            }
        }
    }

    // ========================================================================
    // GeoLocationService Interface Tests
    // ========================================================================
    @Nested
    @DisplayName("GeoLocationService Interface Tests")
    class GeoLocationServiceInterfaceTests {

        @Nested
        @DisplayName("Default Method Tests")
        class DefaultMethodTests {

            @Test
            @DisplayName("updateGeoIpDatabase should have default implementation")
            void updateGeoIpDatabaseShouldHaveDefaultImplementation() {
                // Given
                GeoLocationService service = new TestGeoLocationService();

                // When/Then - Should not throw
                assertThatCode(service::updateGeoIpDatabase)
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("cleanExpiredCache should have default implementation")
            void cleanExpiredCacheShouldHaveDefaultImplementation() {
                // Given
                GeoLocationService service = new TestGeoLocationService();

                // When/Then - Should not throw
                assertThatCode(service::cleanExpiredCache)
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("Abstract Method Signature Tests")
        class AbstractMethodSignatureTests {

            @Test
            @DisplayName("getLocationAsync should return CompletableFuture")
            void getLocationAsyncShouldReturnCompletableFuture() throws Exception {
                // Given
                GeoLocationService service = new TestGeoLocationService();

                // When
                CompletableFuture<GeoLocation> future = service.getLocationAsync("1.1.1.1");

                // Then
                assertThat(future).isNotNull();
                GeoLocation result = future.get(1, TimeUnit.SECONDS);
                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("getLocation should return GeoLocation with timeout")
            void getLocationShouldReturnGeoLocationWithTimeout() {
                // Given
                GeoLocationService service = new TestGeoLocationService();

                // When
                GeoLocation result = service.getLocation("1.1.1.1", 5000);

                // Then
                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("checkImpossibleTravel should return CompletableFuture Boolean")
            void checkImpossibleTravelShouldReturnCompletableFutureBoolean() throws Exception {
                // Given
                GeoLocationService service = new TestGeoLocationService();

                // When
                CompletableFuture<Boolean> future =
                        service.checkImpossibleTravel(123L, "1.1.1.1", "2.2.2.2", 30);

                // Then
                assertThat(future).isNotNull();
                Boolean result = future.get(1, TimeUnit.SECONDS);
                assertThat(result).isNotNull();
            }

            @Test
            @DisplayName("getMetrics should return Map")
            void getMetricsShouldReturnMap() {
                // Given
                GeoLocationService service = new TestGeoLocationService();

                // When
                Map<String, Object> metrics = service.getMetrics();

                // Then
                assertThat(metrics).isNotNull();
            }
        }
    }

    // ========================================================================
    // IP Address Format Tests
    // ========================================================================
    @Nested
    @DisplayName("IP Address Format Tests")
    class IpAddressFormatTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "0.0.0.0",
                "255.255.255.255",
                "192.168.1.1",
                "10.0.0.1",
                "172.16.0.1",
                "8.8.8.8",
                "1.1.1.1"
        })
        @DisplayName("should handle various IPv4 address formats")
        void shouldHandleVariousIpv4AddressFormats(String ip) {
            // When
            GeoLocation location = GeoLocation.unknown(ip);

            // Then
            assertThat(location.getIp()).isEqualTo(ip);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "::1",
                "::ffff:192.168.1.1",
                "2001:db8::1",
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                "fe80::1",
                "fd00::1"
        })
        @DisplayName("should handle various IPv6 address formats")
        void shouldHandleVariousIpv6AddressFormats(String ip) {
            // When
            GeoLocation location = GeoLocation.unknown(ip);

            // Then
            assertThat(location.getIp()).isEqualTo(ip);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should handle null and empty IP addresses")
        void shouldHandleNullAndEmptyIpAddresses(String ip) {
            // When
            GeoLocation location = GeoLocation.unknown(ip);

            // Then
            assertThat(location.getCountry()).isEqualTo("Unknown");
            assertThat(location.getCountryCode()).isEqualTo("XX");
        }
    }

    // ========================================================================
    // Country Code Tests
    // ========================================================================
    @Nested
    @DisplayName("Country Code Tests")
    class CountryCodeTests {

        @ParameterizedTest
        @CsvSource({
                "US, United States",
                "GB, United Kingdom",
                "DE, Germany",
                "FR, France",
                "JP, Japan",
                "CN, China",
                "AU, Australia",
                "CA, Canada",
                "BR, Brazil",
                "IN, India"
        })
        @DisplayName("should accept valid country codes")
        void shouldAcceptValidCountryCodes(String code, String country) {
            // When
            GeoLocation location = GeoLocation.builder()
                    .countryCode(code)
                    .country(country)
                    .build();

            // Then
            assertThat(location.getCountryCode()).isEqualTo(code);
            assertThat(location.getCountry()).isEqualTo(country);
            assertThat(location.isKnown()).isTrue();
        }

        @Test
        @DisplayName("should identify XX as unknown country code")
        void shouldIdentifyXxAsUnknownCountryCode() {
            // Given
            GeoLocation location = GeoLocation.builder()
                    .countryCode("XX")
                    .country("Some Country")
                    .build();

            // Then
            assertThat(location.isKnown()).isFalse();
        }
    }

    // ========================================================================
    // Timezone Tests
    // ========================================================================
    @Nested
    @DisplayName("Timezone Tests")
    class TimezoneTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "America/New_York",
                "Europe/London",
                "Europe/Berlin",
                "Asia/Tokyo",
                "Australia/Sydney",
                "UTC",
                "GMT"
        })
        @DisplayName("should accept valid timezone identifiers")
        void shouldAcceptValidTimezoneIdentifiers(String timezone) {
            // When
            GeoLocation location = GeoLocation.builder()
                    .timezone(timezone)
                    .build();

            // Then
            assertThat(location.getTimezone()).isEqualTo(timezone);
        }
    }

    // ========================================================================
    // VPN/Tor/Proxy Flag Tests
    // ========================================================================
    @Nested
    @DisplayName("VPN/Tor/Proxy Flag Tests")
    class VpnTorProxyFlagTests {

        @Test
        @DisplayName("should handle all flags as false")
        void shouldHandleAllFlagsAsFalse() {
            // When
            GeoLocation location = GeoLocation.builder()
                    .isVpn(false)
                    .isTor(false)
                    .isProxy(false)
                    .build();

            // Then
            assertThat(location.getIsVpn()).isFalse();
            assertThat(location.getIsTor()).isFalse();
            assertThat(location.getIsProxy()).isFalse();
        }

        @Test
        @DisplayName("should handle all flags as true")
        void shouldHandleAllFlagsAsTrue() {
            // When
            GeoLocation location = GeoLocation.builder()
                    .isVpn(true)
                    .isTor(true)
                    .isProxy(true)
                    .build();

            // Then
            assertThat(location.getIsVpn()).isTrue();
            assertThat(location.getIsTor()).isTrue();
            assertThat(location.getIsProxy()).isTrue();
        }

        @Test
        @DisplayName("should handle null flags")
        void shouldHandleNullFlags() {
            // When
            GeoLocation location = GeoLocation.builder().build();

            // Then
            assertThat(location.getIsVpn()).isNull();
            assertThat(location.getIsTor()).isNull();
            assertThat(location.getIsProxy()).isNull();
        }

        @Test
        @DisplayName("should handle mixed flag states")
        void shouldHandleMixedFlagStates() {
            // When
            GeoLocation location = GeoLocation.builder()
                    .isVpn(true)
                    .isTor(false)
                    .isProxy(null)
                    .build();

            // Then
            assertThat(location.getIsVpn()).isTrue();
            assertThat(location.getIsTor()).isFalse();
            assertThat(location.getIsProxy()).isNull();
        }
    }

    // ========================================================================
    // Helper Classes
    // ========================================================================

    /**
     * Test implementation of GeoLocationService for interface testing.
     */
    private static class TestGeoLocationService implements GeoLocationService {

        @Override
        public CompletableFuture<GeoLocation> getLocationAsync(String ip) {
            return CompletableFuture.completedFuture(
                    GeoLocation.builder()
                            .ip(ip)
                            .country("Test Country")
                            .countryCode("TC")
                            .build()
            );
        }

        @Override
        public GeoLocation getLocation(String ip, long timeoutMs) {
            return GeoLocation.builder()
                    .ip(ip)
                    .country("Test Country")
                    .countryCode("TC")
                    .build();
        }

        @Override
        public CompletableFuture<Boolean> checkImpossibleTravel(
                Long userId, String fromIp, String toIp, long minutesElapsed) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("lookups", 0);
            metrics.put("cacheHits", 0);
            return metrics;
        }
    }
}
