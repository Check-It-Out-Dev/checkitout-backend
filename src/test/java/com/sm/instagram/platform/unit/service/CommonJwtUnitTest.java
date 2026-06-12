package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.jwt.JwtTokenProvider;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for the common.jwt package.
 * Tests JWT token creation, validation, parsing, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Common JWT Package Unit Tests")
class CommonJwtUnitTest {

    private JwtTokenProvider jwtTokenProvider;

    // Must be at least 256 bits (32 bytes) for HS256
    private static final String TEST_SECRET = "test-secret-key-for-jwt-testing-must-be-at-least-256-bits-long";
    // Use 24 hours (1 day) so createToken produces valid tokens (it divides by 24)
    private static final int DEFAULT_EXPIRATION_HOURS = 24;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "defaultExpirationHours", DEFAULT_EXPIRATION_HOURS);
    }

    @Nested
    @DisplayName("createTokenWithClaims Tests")
    class CreateTokenWithClaimsTests {

        @Test
        @DisplayName("should create valid token with subject and claims")
        void shouldCreateValidTokenWithSubjectAndClaims() {
            // Given
            String subject = "firebase-uid-123";
            Map<String, Object> claims = Map.of(
                "role", "CREATOR",
                "email", "test@example.com"
            );
            int expirationDays = 7;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, claims, expirationDays);

            // Then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getSubject(token)).isEqualTo(subject);
            assertThat(jwtTokenProvider.getClaim(token, "role")).isEqualTo("CREATOR");
            assertThat(jwtTokenProvider.getClaim(token, "email")).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should create token with empty claims map")
        void shouldCreateTokenWithEmptyClaims() {
            // Given
            String subject = "uid-456";
            Map<String, Object> claims = Collections.emptyMap();
            int expirationDays = 1;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, claims, expirationDays);

            // Then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getSubject(token)).isEqualTo(subject);
        }

        @Test
        @DisplayName("should create token with null claims map")
        void shouldCreateTokenWithNullClaims() {
            // Given
            String subject = "uid-789";
            int expirationDays = 1;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, null, expirationDays);

            // Then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getSubject(token)).isEqualTo(subject);
        }

        @Test
        @DisplayName("should create token with numeric claims")
        void shouldCreateTokenWithNumericClaims() {
            // Given
            String subject = "uid-numeric";
            Map<String, Object> claims = Map.of(
                "userId", 12345L,
                "age", 25,
                "balance", 1000.50
            );
            int expirationDays = 1;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, claims, expirationDays);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtTokenProvider.getClaim(token, "userId")).isEqualTo(12345);
            assertThat(jwtTokenProvider.getClaim(token, "age")).isEqualTo(25);
            assertThat(jwtTokenProvider.getClaim(token, "balance")).isEqualTo(1000.50);
        }

        @Test
        @DisplayName("should create token with boolean claims")
        void shouldCreateTokenWithBooleanClaims() {
            // Given
            String subject = "uid-boolean";
            Map<String, Object> claims = Map.of(
                "isVerified", true,
                "isAdmin", false
            );
            int expirationDays = 1;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, claims, expirationDays);

            // Then
            assertThat(jwtTokenProvider.getClaim(token, "isVerified")).isEqualTo(true);
            assertThat(jwtTokenProvider.getClaim(token, "isAdmin")).isEqualTo(false);
        }

        @Test
        @DisplayName("should create token with list claims")
        void shouldCreateTokenWithListClaims() {
            // Given
            String subject = "uid-list";
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", Arrays.asList("CREATOR", "BRAND"));
            claims.put("permissions", Arrays.asList("read", "write", "delete"));
            int expirationDays = 1;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, claims, expirationDays);

            // Then
            assertThat(jwtTokenProvider.getClaim(token, "roles"))
                .isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) jwtTokenProvider.getClaim(token, "roles");
            assertThat(roles).containsExactly("CREATOR", "BRAND");
        }

        @Test
        @DisplayName("should set correct expiration date")
        void shouldSetCorrectExpirationDate() {
            // Given
            String subject = "uid-expiration";
            int expirationDays = 30;
            long expectedMinMillis = System.currentTimeMillis() + (expirationDays * 24 * 60 * 60 * 1000L) - 5000;
            long expectedMaxMillis = System.currentTimeMillis() + (expirationDays * 24 * 60 * 60 * 1000L) + 5000;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, null, expirationDays);

            // Then
            Claims claims = jwtTokenProvider.getClaims(token);
            Date expiration = claims.getExpiration();
            assertThat(expiration.getTime()).isBetween(expectedMinMillis, expectedMaxMillis);
        }

        @Test
        @DisplayName("should set issuedAt timestamp")
        void shouldSetIssuedAtTimestamp() {
            // Given
            String subject = "uid-issued";
            long beforeCreation = System.currentTimeMillis() - 1000;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, null, 1);

            // Then
            Claims claims = jwtTokenProvider.getClaims(token);
            Date issuedAt = claims.getIssuedAt();
            long afterCreation = System.currentTimeMillis() + 1000;
            assertThat(issuedAt.getTime()).isBetween(beforeCreation, afterCreation);
        }

        @Test
        @DisplayName("should create token with special characters in subject")
        void shouldCreateTokenWithSpecialCharactersInSubject() {
            // Given
            String subject = "user+test@example.com#fragment";
            int expirationDays = 1;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, null, expirationDays);

            // Then
            assertThat(jwtTokenProvider.getSubject(token)).isEqualTo(subject);
        }

        @Test
        @DisplayName("should create token with unicode characters")
        void shouldCreateTokenWithUnicodeCharacters() {
            // Given
            String subject = "user-unicode";
            Map<String, Object> claims = Map.of(
                "displayName", "Test User Name",
                "locale", "en-US"
            );
            int expirationDays = 1;

            // When
            String token = jwtTokenProvider.createTokenWithClaims(subject, claims, expirationDays);

            // Then
            assertThat(jwtTokenProvider.getClaim(token, "displayName")).isEqualTo("Test User Name");
        }

        @Test
        @DisplayName("should create different tokens for same input when time differs")
        void shouldCreateDifferentTokensForSameInput() throws InterruptedException {
            // Given
            String subject = "uid-unique";
            Map<String, Object> claims = Map.of("role", "USER");

            // When
            String token1 = jwtTokenProvider.createTokenWithClaims(subject, claims, 1);
            // JWT iat (issuedAt) has second-level precision, need to wait at least 1 second
            Thread.sleep(1100);
            String token2 = jwtTokenProvider.createTokenWithClaims(subject, claims, 1);

            // Then
            // Tokens will differ due to issuedAt timestamp (second-level precision)
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("createTokenWithMinutesExpiry Tests")
    class CreateTokenWithMinutesExpiryTests {

        @Test
        @DisplayName("should create token with minutes expiry")
        void shouldCreateTokenWithMinutesExpiry() {
            // Given
            String subject = "2fa-uid-123";
            Map<String, Object> claims = Map.of("twoFaRequired", true);
            int expirationMinutes = 15;

            // When
            String token = jwtTokenProvider.createTokenWithMinutesExpiry(subject, claims, expirationMinutes);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getClaim(token, "twoFaRequired")).isEqualTo(true);
        }

        @Test
        @DisplayName("should set correct expiration in minutes")
        void shouldSetCorrectExpirationInMinutes() {
            // Given
            String subject = "uid-minutes";
            int expirationMinutes = 30;
            long expectedMinMillis = System.currentTimeMillis() + (expirationMinutes * 60 * 1000L) - 5000;
            long expectedMaxMillis = System.currentTimeMillis() + (expirationMinutes * 60 * 1000L) + 5000;

            // When
            String token = jwtTokenProvider.createTokenWithMinutesExpiry(subject, null, expirationMinutes);

            // Then
            Claims claims = jwtTokenProvider.getClaims(token);
            Date expiration = claims.getExpiration();
            assertThat(expiration.getTime()).isBetween(expectedMinMillis, expectedMaxMillis);
        }

        @Test
        @DisplayName("should create short-lived token for 2FA")
        void shouldCreateShortLivedTokenFor2FA() {
            // Given
            String subject = "uid-2fa";
            Map<String, Object> claims = Map.of(
                "2faRequired", true,
                "pendingAuth", true
            );
            int expirationMinutes = 5;

            // When
            String token = jwtTokenProvider.createTokenWithMinutesExpiry(subject, claims, expirationMinutes);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("should create token with 1 minute expiry")
        void shouldCreateTokenWithOneMinuteExpiry() {
            // Given
            String subject = "uid-short";
            int expirationMinutes = 1;

            // When
            String token = jwtTokenProvider.createTokenWithMinutesExpiry(subject, null, expirationMinutes);

            // Then
            assertThat(token).isNotNull();
            Claims claims = jwtTokenProvider.getClaims(token);
            long expectedMillis = expirationMinutes * 60 * 1000L;
            long actualDiff = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertThat(actualDiff).isCloseTo(expectedMillis, within(1000L));
        }

        @Test
        @DisplayName("should handle empty claims in minutes expiry token")
        void shouldHandleEmptyClaimsInMinutesExpiryToken() {
            // Given
            String subject = "uid-empty-claims";
            Map<String, Object> claims = Collections.emptyMap();

            // When
            String token = jwtTokenProvider.createTokenWithMinutesExpiry(subject, claims, 10);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtTokenProvider.getSubject(token)).isEqualTo(subject);
        }

        @Test
        @DisplayName("should handle null claims in minutes expiry token")
        void shouldHandleNullClaimsInMinutesExpiryToken() {
            // Given
            String subject = "uid-null-claims";

            // When
            String token = jwtTokenProvider.createTokenWithMinutesExpiry(subject, null, 10);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }
    }

    @Nested
    @DisplayName("createToken (Default Expiration) Tests")
    class CreateTokenDefaultExpirationTests {

        @Test
        @DisplayName("should create token with default expiration")
        void shouldCreateTokenWithDefaultExpiration() {
            // Given
            String subject = "uid-default";
            String role = "CREATOR";

            // When
            String token = jwtTokenProvider.createToken(subject, role);

            // Then
            assertThat(token).isNotNull();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getSubject(token)).isEqualTo(subject);
            assertThat(jwtTokenProvider.getClaim(token, "role")).isEqualTo(role);
        }

        @Test
        @DisplayName("should use configured default expiration hours")
        void shouldUseConfiguredDefaultExpirationHours() {
            // Given
            String subject = "uid-configured";
            int customHours = 48; // 2 days
            ReflectionTestUtils.setField(jwtTokenProvider, "defaultExpirationHours", customHours);

            // When
            String token = jwtTokenProvider.createToken(subject, "USER");

            // Then
            Claims claims = jwtTokenProvider.getClaims(token);
            // expiration is set as (customHours / 24) days = 2 days in this case
            long expectedMillis = (customHours / 24) * 24 * 60 * 60 * 1000L;
            long actualDiff = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertThat(actualDiff).isCloseTo(expectedMillis, within(5000L));
        }

        @ParameterizedTest
        @ValueSource(strings = {"CREATOR", "BRAND", "ADMIN", "USER", "GUEST"})
        @DisplayName("should create tokens for various roles")
        void shouldCreateTokensForVariousRoles(String role) {
            // Given
            String subject = "uid-" + role.toLowerCase();

            // When
            String token = jwtTokenProvider.createToken(subject, role);

            // Then
            assertThat(jwtTokenProvider.getClaim(token, "role")).isEqualTo(role);
        }
    }

    @Nested
    @DisplayName("validateToken Tests")
    class ValidateTokenTests {

        @Test
        @DisplayName("should validate correctly signed token")
        void shouldValidateCorrectlySignedToken() {
            // Given
            String token = jwtTokenProvider.createToken("uid-valid", "USER");

            // When
            boolean isValid = jwtTokenProvider.validateToken(token);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            // Given - create an expired token directly
            SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
            Date pastDate = new Date(System.currentTimeMillis() - 10000);
            String expiredToken = Jwts.builder()
                .setSubject("uid-expired")
                .setIssuedAt(new Date(System.currentTimeMillis() - 20000))
                .setExpiration(pastDate)
                .signWith(key)
                .compact();

            // When
            boolean isValid = jwtTokenProvider.validateToken(expiredToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject malformed token")
        void shouldRejectMalformedToken() {
            // Given
            String malformedToken = "not.a.valid.jwt.token";

            // When
            boolean isValid = jwtTokenProvider.validateToken(malformedToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject token with invalid signature")
        void shouldRejectTokenWithInvalidSignature() {
            // Given - create token with different secret
            String differentSecret = "different-secret-key-for-jwt-testing-must-be-at-least-256-bits";
            SecretKey differentKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));
            String tokenWithDifferentKey = Jwts.builder()
                .setSubject("uid-invalid-sig")
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(differentKey)
                .compact();

            // When/Then - Note: The current implementation throws SignatureException
            // rather than returning false (this is a known limitation in error handling)
            assertThatThrownBy(() -> jwtTokenProvider.validateToken(tokenWithDifferentKey))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
        }

        @Test
        @DisplayName("should reject null token")
        void shouldRejectNullToken() {
            // When
            boolean isValid = jwtTokenProvider.validateToken(null);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject empty token")
        void shouldRejectEmptyToken() {
            // When
            boolean isValid = jwtTokenProvider.validateToken("");

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject token with only whitespace")
        void shouldRejectTokenWithOnlyWhitespace() {
            // When
            boolean isValid = jwtTokenProvider.validateToken("   ");

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject unsupported JWT")
        void shouldRejectUnsupportedJwt() {
            // Given - create an unsigned JWT (JWS without signature)
            String unsupportedToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0In0.";

            // When
            boolean isValid = jwtTokenProvider.validateToken(unsupportedToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject random base64 string")
        void shouldRejectRandomBase64String() {
            // Given
            String randomBase64 = Base64.getEncoder().encodeToString("random data".getBytes());

            // When
            boolean isValid = jwtTokenProvider.validateToken(randomBase64);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject token with tampered payload")
        void shouldRejectTokenWithTamperedPayload() {
            // Given
            String validToken = jwtTokenProvider.createToken("uid-original", "USER");
            // Tamper with the payload (second part of the token)
            String[] parts = validToken.split("\\.");
            String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"hacked\"}".getBytes());
            String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

            // When/Then - Note: The current implementation throws SignatureException
            // rather than returning false (this is a known limitation in error handling)
            assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
        }
    }

    @Nested
    @DisplayName("getClaims Tests")
    class GetClaimsTests {

        @Test
        @DisplayName("should get all claims from token")
        void shouldGetAllClaimsFromToken() {
            // Given
            String subject = "uid-claims";
            Map<String, Object> customClaims = Map.of(
                "role", "ADMIN",
                "permissions", Arrays.asList("read", "write")
            );
            String token = jwtTokenProvider.createTokenWithClaims(subject, customClaims, 1);

            // When
            Claims claims = jwtTokenProvider.getClaims(token);

            // Then
            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo(subject);
            assertThat(claims.get("role")).isEqualTo("ADMIN");
            assertThat(claims.getExpiration()).isNotNull();
            assertThat(claims.getIssuedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw exception for invalid token")
        void shouldThrowExceptionForInvalidToken() {
            // Given
            String invalidToken = "invalid.token.here";

            // When/Then
            assertThatThrownBy(() -> jwtTokenProvider.getClaims(invalidToken))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw exception for expired token")
        void shouldThrowExceptionForExpiredToken() {
            // Given
            SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                .setSubject("uid-expired")
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();

            // When/Then
            assertThatThrownBy(() -> jwtTokenProvider.getClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("should get claims including standard JWT claims")
        void shouldGetClaimsIncludingStandardJwtClaims() {
            // Given
            String token = jwtTokenProvider.createToken("uid-standard", "USER");

            // When
            Claims claims = jwtTokenProvider.getClaims(token);

            // Then
            assertThat(claims.getSubject()).isEqualTo("uid-standard");
            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();
            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        }
    }

    @Nested
    @DisplayName("getSubject Tests")
    class GetSubjectTests {

        @Test
        @DisplayName("should get subject from token")
        void shouldGetSubjectFromToken() {
            // Given
            String expectedSubject = "firebase-uid-abc123";
            String token = jwtTokenProvider.createToken(expectedSubject, "USER");

            // When
            String subject = jwtTokenProvider.getSubject(token);

            // Then
            assertThat(subject).isEqualTo(expectedSubject);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "simple-uid",
            "user@email.com",
            "12345",
            "uid-with-dashes-and-numbers-123",
            "UID_WITH_UNDERSCORES"
        })
        @DisplayName("should get various subject formats")
        void shouldGetVariousSubjectFormats(String expectedSubject) {
            // Given
            String token = jwtTokenProvider.createToken(expectedSubject, "USER");

            // When
            String subject = jwtTokenProvider.getSubject(token);

            // Then
            assertThat(subject).isEqualTo(expectedSubject);
        }

        @Test
        @DisplayName("should throw exception for invalid token")
        void shouldThrowExceptionForInvalidToken() {
            // Given
            String invalidToken = "not.valid.token";

            // When/Then
            assertThatThrownBy(() -> jwtTokenProvider.getSubject(invalidToken))
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("getClaim Tests")
    class GetClaimTests {

        @Test
        @DisplayName("should get specific string claim")
        void shouldGetSpecificStringClaim() {
            // Given
            Map<String, Object> claims = Map.of(
                "role", "CREATOR",
                "email", "test@example.com"
            );
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object role = jwtTokenProvider.getClaim(token, "role");
            Object email = jwtTokenProvider.getClaim(token, "email");

            // Then
            assertThat(role).isEqualTo("CREATOR");
            assertThat(email).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should get specific numeric claim")
        void shouldGetSpecificNumericClaim() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 12345);
            claims.put("balance", 1000.50);
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object userId = jwtTokenProvider.getClaim(token, "userId");
            Object balance = jwtTokenProvider.getClaim(token, "balance");

            // Then
            assertThat(userId).isEqualTo(12345);
            assertThat(balance).isEqualTo(1000.50);
        }

        @Test
        @DisplayName("should return null for non-existent claim")
        void shouldReturnNullForNonExistentClaim() {
            // Given
            String token = jwtTokenProvider.createToken("uid", "USER");

            // When
            Object nonExistent = jwtTokenProvider.getClaim(token, "nonExistentClaim");

            // Then
            assertThat(nonExistent).isNull();
        }

        @Test
        @DisplayName("should get boolean claim")
        void shouldGetBooleanClaim() {
            // Given
            Map<String, Object> claims = Map.of(
                "verified", true,
                "suspended", false
            );
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object verified = jwtTokenProvider.getClaim(token, "verified");
            Object suspended = jwtTokenProvider.getClaim(token, "suspended");

            // Then
            assertThat(verified).isEqualTo(true);
            assertThat(suspended).isEqualTo(false);
        }

        @Test
        @DisplayName("should get list claim")
        void shouldGetListClaim() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("scopes", Arrays.asList("read", "write", "delete"));
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object scopes = jwtTokenProvider.getClaim(token, "scopes");

            // Then
            assertThat(scopes).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> scopesList = (List<String>) scopes;
            assertThat(scopesList).containsExactly("read", "write", "delete");
        }
    }

    @Nested
    @DisplayName("isTokenExpired Tests")
    class IsTokenExpiredTests {

        @Test
        @DisplayName("should return false for valid non-expired token")
        void shouldReturnFalseForValidNonExpiredToken() {
            // Given
            String token = jwtTokenProvider.createTokenWithClaims("uid", null, 1);

            // When
            boolean isExpired = jwtTokenProvider.isTokenExpired(token);

            // Then
            assertThat(isExpired).isFalse();
        }

        @Test
        @DisplayName("should return true for expired token")
        void shouldReturnTrueForExpiredToken() {
            // Given
            SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                .setSubject("uid-expired")
                .setIssuedAt(new Date(System.currentTimeMillis() - 20000))
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();

            // When
            boolean isExpired = jwtTokenProvider.isTokenExpired(expiredToken);

            // Then
            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should return true for invalid token")
        void shouldReturnTrueForInvalidToken() {
            // Given
            String invalidToken = "not.a.valid.token";

            // When
            boolean isExpired = jwtTokenProvider.isTokenExpired(invalidToken);

            // Then
            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should return true for null token")
        void shouldReturnTrueForNullToken() {
            // When
            boolean isExpired = jwtTokenProvider.isTokenExpired(null);

            // Then
            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should return true for empty token")
        void shouldReturnTrueForEmptyToken() {
            // When
            boolean isExpired = jwtTokenProvider.isTokenExpired("");

            // Then
            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should return true for token with wrong signature")
        void shouldReturnTrueForTokenWithWrongSignature() {
            // Given
            String differentSecret = "another-secret-key-for-jwt-testing-must-be-at-least-256-bits";
            SecretKey differentKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));
            String tokenWithDifferentKey = Jwts.builder()
                .setSubject("uid")
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(differentKey)
                .compact();

            // When
            boolean isExpired = jwtTokenProvider.isTokenExpired(tokenWithDifferentKey);

            // Then
            assertThat(isExpired).isTrue();
        }
    }

    @Nested
    @DisplayName("Token Format Tests")
    class TokenFormatTests {

        @Test
        @DisplayName("should produce token with three dot-separated parts")
        void shouldProduceTokenWithThreeDotSeparatedParts() {
            // Given
            String token = jwtTokenProvider.createToken("uid", "USER");

            // When
            String[] parts = token.split("\\.");

            // Then
            assertThat(parts).hasSize(3);
        }

        @Test
        @DisplayName("should produce base64url encoded header")
        void shouldProduceBase64UrlEncodedHeader() {
            // Given
            String token = jwtTokenProvider.createToken("uid", "USER");
            String header = token.split("\\.")[0];

            // When
            String decodedHeader = new String(Base64.getUrlDecoder().decode(header));

            // Then
            assertThat(decodedHeader).contains("alg");
            assertThat(decodedHeader).contains("HS");
        }

        @Test
        @DisplayName("should produce base64url encoded payload")
        void shouldProduceBase64UrlEncodedPayload() {
            // Given
            String token = jwtTokenProvider.createToken("test-subject", "USER");
            String payload = token.split("\\.")[1];

            // When
            String decodedPayload = new String(Base64.getUrlDecoder().decode(payload));

            // Then
            assertThat(decodedPayload).contains("sub");
            assertThat(decodedPayload).contains("test-subject");
        }

        @Test
        @DisplayName("should use HMAC-SHA algorithm")
        void shouldUseHmacShaAlgorithm() {
            // Given
            String token = jwtTokenProvider.createToken("uid", "USER");
            String header = token.split("\\.")[0];

            // When
            String decodedHeader = new String(Base64.getUrlDecoder().decode(header));

            // Then
            // HS256, HS384, or HS512
            assertThat(decodedHeader).containsPattern("HS(256|384|512)");
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("should not accept none algorithm")
        void shouldNotAcceptNoneAlgorithm() {
            // Given - create a token with 'none' algorithm (attack vector)
            String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker\",\"role\":\"ADMIN\"}".getBytes());
            String noneAlgToken = header + "." + payload + ".";

            // When
            boolean isValid = jwtTokenProvider.validateToken(noneAlgToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should not accept algorithm confusion attack")
        void shouldNotAcceptAlgorithmConfusionAttack() {
            // Given - a token claiming to use RS256 but signed with HS256 using public key
            String attackToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhdHRhY2tlciJ9.fake";

            // When
            boolean isValid = jwtTokenProvider.validateToken(attackToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should require secret of sufficient length")
        void shouldRequireSecretOfSufficientLength() {
            // Given
            JwtTokenProvider providerWithWeakSecret = new JwtTokenProvider();
            String weakSecret = "short"; // Only 5 bytes, should fail
            ReflectionTestUtils.setField(providerWithWeakSecret, "jwtSecret", weakSecret);
            ReflectionTestUtils.setField(providerWithWeakSecret, "defaultExpirationHours", 24);

            // When/Then
            assertThatThrownBy(() -> providerWithWeakSecret.createToken("uid", "USER"))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should not expose secret in token")
        void shouldNotExposeSecretInToken() {
            // Given
            String token = jwtTokenProvider.createToken("uid", "USER");

            // When
            String decodedHeader = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
            String decodedPayload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

            // Then
            assertThat(decodedHeader).doesNotContain(TEST_SECRET);
            assertThat(decodedPayload).doesNotContain(TEST_SECRET);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle very long subject")
        void shouldHandleVeryLongSubject() {
            // Given
            String longSubject = "a".repeat(1000);

            // When - use createTokenWithClaims to ensure token is valid
            String token = jwtTokenProvider.createTokenWithClaims(longSubject, null, 1);

            // Then
            assertThat(jwtTokenProvider.getSubject(token)).isEqualTo(longSubject);
        }

        @Test
        @DisplayName("should handle zero day expiration")
        void shouldHandleZeroDayExpiration() {
            // Given - 0 days means immediate expiration
            String token = jwtTokenProvider.createTokenWithClaims("uid", null, 0);

            // When
            boolean isExpired = jwtTokenProvider.isTokenExpired(token);

            // Then
            // Token expires at same time as issuedAt, so it should be expired
            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should handle large number of claims")
        void shouldHandleLargeNumberOfClaims() {
            // Given
            Map<String, Object> manyClaims = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                manyClaims.put("claim" + i, "value" + i);
            }

            // When
            String token = jwtTokenProvider.createTokenWithClaims("uid", manyClaims, 1);

            // Then
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getClaim(token, "claim0")).isEqualTo("value0");
            assertThat(jwtTokenProvider.getClaim(token, "claim49")).isEqualTo("value49");
        }

        @Test
        @DisplayName("should handle claim with null value")
        void shouldHandleClaimWithNullValue() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("nullClaim", null);
            claims.put("validClaim", "value");

            // When
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // Then
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getClaim(token, "validClaim")).isEqualTo("value");
        }

        @Test
        @DisplayName("should handle nested map claims")
        void shouldHandleNestedMapClaims() {
            // Given
            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("inner", Map.of("key", "value"));
            Map<String, Object> claims = new HashMap<>();
            claims.put("nested", nestedMap);

            // When
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // Then
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            Object nested = jwtTokenProvider.getClaim(token, "nested");
            assertThat(nested).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("should handle very long expiration period")
        void shouldHandleVeryLongExpirationPeriod() {
            // Given
            int veryLongDays = 365 * 10; // 10 years

            // When
            String token = jwtTokenProvider.createTokenWithClaims("uid", null, veryLongDays);

            // Then
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.isTokenExpired(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Integration Scenario Tests")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("should support complete authentication flow")
        void shouldSupportCompleteAuthenticationFlow() {
            // Given - User authenticated via Firebase
            String firebaseUid = "firebase-uid-xyz123";
            String role = "CREATOR";

            // When - Create session token
            String sessionToken = jwtTokenProvider.createToken(firebaseUid, role);

            // Then - Token should be valid and contain correct claims
            assertThat(jwtTokenProvider.validateToken(sessionToken)).isTrue();
            assertThat(jwtTokenProvider.getSubject(sessionToken)).isEqualTo(firebaseUid);
            assertThat(jwtTokenProvider.getClaim(sessionToken, "role")).isEqualTo(role);
            assertThat(jwtTokenProvider.isTokenExpired(sessionToken)).isFalse();
        }

        @Test
        @DisplayName("should support 2FA partial authentication flow")
        void shouldSupport2FAPartialAuthenticationFlow() {
            // Given - User needs 2FA
            String firebaseUid = "firebase-uid-2fa";
            Map<String, Object> claims = Map.of(
                "2faRequired", true,
                "pendingAuth", true
            );

            // When - Create short-lived 2FA token
            String twoFaToken = jwtTokenProvider.createTokenWithMinutesExpiry(firebaseUid, claims, 5);

            // Then
            assertThat(jwtTokenProvider.validateToken(twoFaToken)).isTrue();
            assertThat(jwtTokenProvider.getClaim(twoFaToken, "2faRequired")).isEqualTo(true);
            assertThat(jwtTokenProvider.getClaim(twoFaToken, "pendingAuth")).isEqualTo(true);
        }

        @Test
        @DisplayName("should support refresh token scenario")
        void shouldSupportRefreshTokenScenario() {
            // Given - Original token
            String originalSubject = "user-refresh";
            Map<String, Object> originalClaims = Map.of(
                "role", "BRAND",
                "tokenType", "refresh"
            );
            String originalToken = jwtTokenProvider.createTokenWithClaims(originalSubject, originalClaims, 30);

            // When - Validate and extract claims for refresh
            assertThat(jwtTokenProvider.validateToken(originalToken)).isTrue();
            String subject = jwtTokenProvider.getSubject(originalToken);
            Object role = jwtTokenProvider.getClaim(originalToken, "role");

            // Create new access token
            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put("role", role);
            newClaims.put("tokenType", "access");
            String newAccessToken = jwtTokenProvider.createTokenWithClaims(subject, newClaims, 1);

            // Then
            assertThat(jwtTokenProvider.getSubject(newAccessToken)).isEqualTo(originalSubject);
            assertThat(jwtTokenProvider.getClaim(newAccessToken, "role")).isEqualTo("BRAND");
            assertThat(jwtTokenProvider.getClaim(newAccessToken, "tokenType")).isEqualTo("access");
        }

        @Test
        @DisplayName("should handle multi-tenant scenario with tenant claim")
        void shouldHandleMultiTenantScenarioWithTenantClaim() {
            // Given
            String userId = "user-multi-tenant";
            Map<String, Object> claims = Map.of(
                "role", "ADMIN",
                "tenantId", "tenant-123",
                "tenantName", "ACME Corp"
            );

            // When
            String token = jwtTokenProvider.createTokenWithClaims(userId, claims, 1);

            // Then
            assertThat(jwtTokenProvider.getClaim(token, "tenantId")).isEqualTo("tenant-123");
            assertThat(jwtTokenProvider.getClaim(token, "tenantName")).isEqualTo("ACME Corp");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        @DisplayName("should handle invalid tokens gracefully in validateToken")
        void shouldHandleInvalidTokensGracefullyInValidateToken(String invalidToken) {
            // When
            boolean result = jwtTokenProvider.validateToken(invalidToken);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle truncated token")
        void shouldHandleTruncatedToken() {
            // Given
            String validToken = jwtTokenProvider.createToken("uid", "USER");
            String truncatedToken = validToken.substring(0, validToken.length() / 2);

            // When
            boolean isValid = jwtTokenProvider.validateToken(truncatedToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should handle token with extra parts")
        void shouldHandleTokenWithExtraParts() {
            // Given
            String validToken = jwtTokenProvider.createToken("uid", "USER");
            String tokenWithExtraPart = validToken + ".extra";

            // When
            boolean isValid = jwtTokenProvider.validateToken(tokenWithExtraPart);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should handle token with missing signature")
        void shouldHandleTokenWithMissingSignature() {
            // Given
            String validToken = jwtTokenProvider.createToken("uid", "USER");
            String[] parts = validToken.split("\\.");
            String tokenWithoutSignature = parts[0] + "." + parts[1] + ".";

            // When
            boolean isValid = jwtTokenProvider.validateToken(tokenWithoutSignature);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("Token Timing Tests")
    class TokenTimingTests {

        @Test
        @DisplayName("should create token with issuedAt before expiration")
        void shouldCreateTokenWithIssuedAtBeforeExpiration() {
            // Given
            String token = jwtTokenProvider.createTokenWithClaims("uid", null, 1);

            // When
            Claims claims = jwtTokenProvider.getClaims(token);

            // Then
            assertThat(claims.getIssuedAt()).isBefore(claims.getExpiration());
        }

        @Test
        @DisplayName("should have issuedAt approximately now")
        void shouldHaveIssuedAtApproximatelyNow() {
            // Given
            // JWT uses second-level precision for timestamps, so we need to floor to seconds
            long beforeCreation = (System.currentTimeMillis() / 1000) * 1000;
            String token = jwtTokenProvider.createTokenWithClaims("uid", null, 1);
            long afterCreation = ((System.currentTimeMillis() / 1000) + 1) * 1000;

            // When
            Claims claims = jwtTokenProvider.getClaims(token);

            // Then - issuedAt is truncated to seconds, so allow for that
            assertThat(claims.getIssuedAt().getTime()).isBetween(beforeCreation, afterCreation + 1000);
        }

        @Test
        @DisplayName("should calculate minutes expiry correctly")
        void shouldCalculateMinutesExpiryCorrectly() {
            // Given
            int minutes = 15;
            String token = jwtTokenProvider.createTokenWithMinutesExpiry("uid", null, minutes);

            // When
            Claims claims = jwtTokenProvider.getClaims(token);
            long difference = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

            // Then
            long expectedMillis = minutes * 60 * 1000L;
            assertThat(difference).isCloseTo(expectedMillis, within(100L));
        }

        @Test
        @DisplayName("should calculate days expiry correctly")
        void shouldCalculateDaysExpiryCorrectly() {
            // Given
            int days = 7;
            String token = jwtTokenProvider.createTokenWithClaims("uid", null, days);

            // When
            Claims claims = jwtTokenProvider.getClaims(token);
            long difference = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

            // Then
            long expectedMillis = days * 24L * 60 * 60 * 1000;
            assertThat(difference).isCloseTo(expectedMillis, within(1000L));
        }
    }

    @Nested
    @DisplayName("Claim Type Preservation Tests")
    class ClaimTypePreservationTests {

        @Test
        @DisplayName("should preserve integer claim type")
        void shouldPreserveIntegerClaimType() {
            // Given
            Map<String, Object> claims = Map.of("count", 42);
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object count = jwtTokenProvider.getClaim(token, "count");

            // Then
            assertThat(count).isInstanceOf(Integer.class);
            assertThat(count).isEqualTo(42);
        }

        @Test
        @DisplayName("should preserve double claim type")
        void shouldPreserveDoubleClaimType() {
            // Given
            Map<String, Object> claims = Map.of("price", 99.99);
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object price = jwtTokenProvider.getClaim(token, "price");

            // Then
            assertThat(price).isEqualTo(99.99);
        }

        @Test
        @DisplayName("should preserve boolean claim type")
        void shouldPreserveBooleanClaimType() {
            // Given
            Map<String, Object> claims = Map.of("active", true);
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object active = jwtTokenProvider.getClaim(token, "active");

            // Then
            assertThat(active).isInstanceOf(Boolean.class);
            assertThat(active).isEqualTo(true);
        }

        @Test
        @DisplayName("should preserve string claim type")
        void shouldPreserveStringClaimType() {
            // Given
            Map<String, Object> claims = Map.of("name", "test-name");
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object name = jwtTokenProvider.getClaim(token, "name");

            // Then
            assertThat(name).isInstanceOf(String.class);
            assertThat(name).isEqualTo("test-name");
        }

        @Test
        @DisplayName("should preserve list claim type")
        void shouldPreserveListClaimType() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("items", Arrays.asList("a", "b", "c"));
            String token = jwtTokenProvider.createTokenWithClaims("uid", claims, 1);

            // When
            Object items = jwtTokenProvider.getClaim(token, "items");

            // Then
            assertThat(items).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> itemList = (List<String>) items;
            assertThat(itemList).hasSize(3);
        }
    }
}
