package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.SessionValidationUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionValidationUtils.
 * These are pure unit tests - no Spring context needed.
 *
 * Uses fixed Instant values for deterministic testing.
 */
@DisplayName("SessionValidationUtils")
class SessionValidationUtilsUnitTest {

    @Nested
    @DisplayName("isSessionExpired")
    class IsSessionExpired {

        @Test
        @DisplayName("should return false when session is not expired")
        void isSessionExpired_notExpired_returnsFalse() {
            // Given - session created 1 hour ago with 24-hour max age
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            Duration maxAge = Duration.ofHours(24);

            // When
            boolean result = SessionValidationUtils.isSessionExpired(createdAt, maxAge);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when session is expired")
        void isSessionExpired_expired_returnsTrue() {
            // Given - session created 25 hours ago with 24-hour max age
            Instant createdAt = Instant.now().minus(Duration.ofHours(25));
            Duration maxAge = Duration.ofHours(24);

            // When
            boolean result = SessionValidationUtils.isSessionExpired(createdAt, maxAge);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when createdAt is null")
        void isSessionExpired_nullCreatedAt_returnsTrue() {
            // Given
            Instant createdAt = null;
            Duration maxAge = Duration.ofHours(24);

            // When
            boolean result = SessionValidationUtils.isSessionExpired(createdAt, maxAge);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when session just expired")
        void isSessionExpired_justExpired_returnsTrue() {
            // Given - session created exactly at max age boundary plus 1 second
            Instant createdAt = Instant.now().minus(Duration.ofSeconds(3601));
            Duration maxAge = Duration.ofHours(1);

            // When
            boolean result = SessionValidationUtils.isSessionExpired(createdAt, maxAge);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("isSameUserAgent")
    class IsSameUserAgent {

        @Test
        @DisplayName("should return true when user agents are identical")
        void isSameUserAgent_identical_returnsTrue() {
            // Given
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

            // When
            boolean result = SessionValidationUtils.isSameUserAgent(userAgent, userAgent);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when user agents are different")
        void isSameUserAgent_differentBrowser_returnsFalse() {
            // Given
            String stored = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0";
            String current = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Firefox/121.0";

            // When
            boolean result = SessionValidationUtils.isSameUserAgent(stored, current);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when stored user agent is null")
        void isSameUserAgent_storedNull_returnsFalse() {
            // Given
            String stored = null;
            String current = "Mozilla/5.0";

            // When
            boolean result = SessionValidationUtils.isSameUserAgent(stored, current);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when current user agent is null")
        void isSameUserAgent_currentNull_returnsFalse() {
            // Given
            String stored = "Mozilla/5.0";
            String current = null;

            // When
            boolean result = SessionValidationUtils.isSameUserAgent(stored, current);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isSameCountry")
    class IsSameCountry {

        @Test
        @DisplayName("should return true when countries are the same")
        void isSameCountry_sameCountry_returnsTrue() {
            // Given
            String stored = "PL";
            String current = "PL";

            // When
            boolean result = SessionValidationUtils.isSameCountry(stored, current);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when countries match case-insensitively")
        void isSameCountry_caseInsensitive_returnsTrue() {
            // Given
            String stored = "PL";
            String current = "pl";

            // When
            boolean result = SessionValidationUtils.isSameCountry(stored, current);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when countries are different")
        void isSameCountry_differentCountry_returnsFalse() {
            // Given
            String stored = "PL";
            String current = "DE";

            // When
            boolean result = SessionValidationUtils.isSameCountry(stored, current);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when both countries are null")
        void isSameCountry_bothNull_returnsTrue() {
            // Given
            String stored = null;
            String current = null;

            // When
            boolean result = SessionValidationUtils.isSameCountry(stored, current);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when only stored country is null")
        void isSameCountry_storedNull_returnsFalse() {
            // Given
            String stored = null;
            String current = "PL";

            // When
            boolean result = SessionValidationUtils.isSameCountry(stored, current);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when only current country is null")
        void isSameCountry_currentNull_returnsFalse() {
            // Given
            String stored = "PL";
            String current = null;

            // When
            boolean result = SessionValidationUtils.isSameCountry(stored, current);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isSessionTooOld")
    class IsSessionTooOld {

        @Test
        @DisplayName("should return false when session is within age limit")
        void isSessionTooOld_withinLimit_returnsFalse() {
            // Given - session created 1 hour ago with 2-hour max age
            Instant createdAt = Instant.now().minus(Duration.ofHours(1));
            long maxAgeSeconds = 7200; // 2 hours

            // When
            boolean result = SessionValidationUtils.isSessionTooOld(createdAt, maxAgeSeconds);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when session exceeds age limit")
        void isSessionTooOld_exceedsLimit_returnsTrue() {
            // Given - session created 3 hours ago with 2-hour max age
            Instant createdAt = Instant.now().minus(Duration.ofHours(3));
            long maxAgeSeconds = 7200; // 2 hours

            // When
            boolean result = SessionValidationUtils.isSessionTooOld(createdAt, maxAgeSeconds);

            // Then
            assertThat(result).isTrue();
        }
    }
}
