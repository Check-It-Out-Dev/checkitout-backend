package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformDto;
import com.sm.instagram.platform.usersocialconnection.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Platform entity/DTO and UserSocialConnection DTOs.
 */
@DisplayName("Platform and Connection Unit Tests")
class PlatformAndConnectionUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ConnectionStatus Enum Tests ====================

    @Nested
    @DisplayName("ConnectionStatus Enum Tests")
    class ConnectionStatusEnumTests {

        @Test
        @DisplayName("should have 4 status values")
        void shouldHaveFourStatusValues() {
            assertThat(ConnectionStatus.values()).hasSize(4);
        }

        @Test
        @DisplayName("should contain expected values")
        void shouldContainExpectedValues() {
            assertThat(ConnectionStatus.values()).containsExactlyInAnyOrder(
                    ConnectionStatus.CONNECTED,
                    ConnectionStatus.EXPIRED,
                    ConnectionStatus.REVOKED,
                    ConnectionStatus.DISCONNECTED
            );
        }

        @ParameterizedTest
        @EnumSource(ConnectionStatus.class)
        @DisplayName("all statuses should have valid name")
        void allStatusesShouldHaveValidName(ConnectionStatus status) {
            assertThat(status.name()).isNotBlank();
        }

        @Test
        @DisplayName("valueOf should work for all statuses")
        void valueOfShouldWorkForAllStatuses() {
            assertThat(ConnectionStatus.valueOf("CONNECTED")).isEqualTo(ConnectionStatus.CONNECTED);
            assertThat(ConnectionStatus.valueOf("EXPIRED")).isEqualTo(ConnectionStatus.EXPIRED);
            assertThat(ConnectionStatus.valueOf("REVOKED")).isEqualTo(ConnectionStatus.REVOKED);
            assertThat(ConnectionStatus.valueOf("DISCONNECTED")).isEqualTo(ConnectionStatus.DISCONNECTED);
        }

        @Test
        @DisplayName("valueOf should throw for invalid status")
        void valueOfShouldThrowForInvalidStatus() {
            assertThatThrownBy(() -> ConnectionStatus.valueOf("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== Platform Entity Tests ====================

    @Nested
    @DisplayName("Platform Entity Tests")
    class PlatformEntityTests {

        @Test
        @DisplayName("should pass validation with valid data")
        void shouldPassValidationWithValidData() {
            Platform platform = new Platform();
            platform.setName("Instagram");
            platform.setLogoUrl("https://instagram.com/logo.png");
            platform.setActive(true);

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("should fail when name is blank")
        void shouldFailWhenNameIsBlank(String name) {
            Platform platform = new Platform();
            platform.setName(name);

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should fail when name exceeds max length")
        void shouldFailWhenNameExceedsMaxLength() {
            Platform platform = new Platform();
            platform.setName("A".repeat(256));

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com/logo.png",
                "https://cdn.example.com/path/to/logo.svg",
                "https://storage.googleapis.com/bucket/logo.gif"
        })
        @DisplayName("should accept valid HTTPS URLs for logoUrl")
        void shouldAcceptValidHttpsUrls(String url) {
            Platform platform = new Platform();
            platform.setName("Test Platform");
            platform.setLogoUrl(url);

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://example.com/logo.png",
                "ftp://example.com/logo.png",
                "file:///path/logo.png"
        })
        @DisplayName("should reject non-HTTPS URLs for logoUrl")
        void shouldRejectNonHttpsUrls(String url) {
            Platform platform = new Platform();
            platform.setName("Test Platform");
            platform.setLogoUrl(url);

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("logoUrl"));
        }

        @Test
        @DisplayName("should fail when logoUrl exceeds max length")
        void shouldFailWhenLogoUrlExceedsMaxLength() {
            Platform platform = new Platform();
            platform.setName("Test Platform");
            platform.setLogoUrl("https://example.com/" + "a".repeat(2030));

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("logoUrl"));
        }

        @Test
        @DisplayName("should accept null logoUrl")
        void shouldAcceptNullLogoUrl() {
            Platform platform = new Platform();
            platform.setName("Test Platform");
            platform.setLogoUrl(null);

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should use all-args constructor")
        void shouldUseAllArgsConstructor() {
            Platform platform = new Platform(1L, "Instagram", "https://example.com/logo.png",
                    true, new HashSet<>(), new HashSet<>(), null);

            assertThat(platform.getId()).isEqualTo(1L);
            assertThat(platform.getName()).isEqualTo("Instagram");
            assertThat(platform.getLogoUrl()).isEqualTo("https://example.com/logo.png");
            assertThat(platform.getActive()).isTrue();
            assertThat(platform.getContentTypes()).isEmpty();
            assertThat(platform.getPartnershipOpportunities()).isEmpty();
        }

        @Test
        @DisplayName("should use no-args constructor")
        void shouldUseNoArgsConstructor() {
            Platform platform = new Platform();
            platform.setName("TikTok");
            platform.setActive(false);

            assertThat(platform.getName()).isEqualTo("TikTok");
            assertThat(platform.getActive()).isFalse();
        }
    }

    // ==================== PlatformDto Tests ====================

    @Nested
    @DisplayName("PlatformDto Tests")
    class PlatformDtoTests {

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            PlatformDto dto = new PlatformDto();
            dto.setName("Instagram");
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields")
        void shouldPassValidationWithAllFields() {
            PlatformDto dto = new PlatformDto();
            dto.setId(1L);
            dto.setName("Instagram");
            dto.setLogoUrl("https://instagram.com/logo.png");
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should fail when name is blank")
        void shouldFailWhenNameIsBlank(String name) {
            PlatformDto dto = new PlatformDto();
            dto.setName(name);
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should fail when name exceeds max length")
        void shouldFailWhenNameExceedsMaxLength() {
            PlatformDto dto = new PlatformDto();
            dto.setName("A".repeat(256));
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        @DisplayName("should fail when active is null")
        void shouldFailWhenActiveIsNull() {
            PlatformDto dto = new PlatformDto();
            dto.setName("Test Platform");
            dto.setActive(null);
            dto.setContentTypes(new HashSet<>());

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("active"));
        }

        @Test
        @DisplayName("should fail when contentTypes is null")
        void shouldFailWhenContentTypesIsNull() {
            PlatformDto dto = new PlatformDto();
            dto.setName("Test Platform");
            dto.setActive(true);
            dto.setContentTypes(null);

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contentTypes"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com/logo.png",
                "https://cdn.example.com/path/to/logo.svg"
        })
        @DisplayName("should accept valid HTTPS URLs for logoUrl")
        void shouldAcceptValidHttpsUrls(String url) {
            PlatformDto dto = new PlatformDto();
            dto.setName("Test Platform");
            dto.setLogoUrl(url);
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when logoUrl exceeds max length")
        void shouldFailWhenLogoUrlExceedsMaxLength() {
            PlatformDto dto = new PlatformDto();
            dto.setName("Test Platform");
            dto.setLogoUrl("https://example.com/" + "a".repeat(2030));
            dto.setActive(true);
            dto.setContentTypes(new HashSet<>());

            Set<ConstraintViolation<PlatformDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("logoUrl"));
        }
    }

    // ==================== UserSocialConnectionDtoIn Tests ====================

    @Nested
    @DisplayName("UserSocialConnectionDtoIn Tests")
    class UserSocialConnectionDtoInTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getPlatform()).isNull();
            assertThat(dto.getSocialUserId()).isNull();
            assertThat(dto.getProfileUrl()).isNull();
            assertThat(dto.getProfilePictureUrl()).isNull();
            assertThat(dto.getDisplayName()).isNull();
            assertThat(dto.getEmail()).isNull();
            assertThat(dto.getNote()).isNull();
            assertThat(dto.getServiceType()).isNull();
            assertThat(dto.getFollowersCount()).isNull();
            assertThat(dto.getIsPrimary()).isNull();
            assertThat(dto.getConnectionStatus()).isNull();
        }

        @Test
        @DisplayName("should set all fields correctly")
        void shouldSetAllFieldsCorrectly() {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();
            dto.setId(1L);
            dto.setUserId(2L);
            dto.setPlatform(3L);
            dto.setSocialUserId("ig-user-123");
            dto.setProfileUrl("https://instagram.com/user");
            dto.setProfilePictureUrl("https://cdn.instagram.com/picture.jpg");
            dto.setDisplayName("John Doe");
            dto.setEmail("john@example.com");
            dto.setNote("VIP influencer");
            dto.setServiceType(1L);
            dto.setFollowersCount(50000);
            dto.setIsPrimary(true);
            dto.setConnectionStatus(ConnectionStatus.CONNECTED);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUserId()).isEqualTo(2L);
            assertThat(dto.getPlatform()).isEqualTo(3L);
            assertThat(dto.getSocialUserId()).isEqualTo("ig-user-123");
            assertThat(dto.getProfileUrl()).isEqualTo("https://instagram.com/user");
            assertThat(dto.getProfilePictureUrl()).isEqualTo("https://cdn.instagram.com/picture.jpg");
            assertThat(dto.getDisplayName()).isEqualTo("John Doe");
            assertThat(dto.getEmail()).isEqualTo("john@example.com");
            assertThat(dto.getNote()).isEqualTo("VIP influencer");
            assertThat(dto.getServiceType()).isEqualTo(1L);
            assertThat(dto.getFollowersCount()).isEqualTo(50000);
            assertThat(dto.getIsPrimary()).isTrue();
            assertThat(dto.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        }

        @ParameterizedTest
        @EnumSource(ConnectionStatus.class)
        @DisplayName("should accept all connection statuses")
        void shouldAcceptAllConnectionStatuses(ConnectionStatus status) {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();
            dto.setConnectionStatus(status);

            assertThat(dto.getConnectionStatus()).isEqualTo(status);
        }
    }

    // ==================== UserSocialConnectionDtoOut Tests ====================

    @Nested
    @DisplayName("UserSocialConnectionDtoOut Tests")
    class UserSocialConnectionDtoOutTests {

        @Test
        @DisplayName("should create with default timestamp values")
        void shouldCreateWithDefaultTimestampValues() {
            UserSocialConnectionDtoOut dto = new UserSocialConnectionDtoOut();

            assertThat(dto.getCreatedTime()).isNotNull();
            assertThat(dto.getLastUpdateTime()).isNotNull();
        }

        @Test
        @DisplayName("should set all fields correctly")
        void shouldSetAllFieldsCorrectly() {
            UserSocialConnectionDtoOut dto = new UserSocialConnectionDtoOut();
            LocalDateTime now = LocalDateTime.now();

            dto.setId(1L);
            dto.setUserId(2L);
            dto.setSocialUserId("ig-user-123");
            dto.setProfileUrl("https://instagram.com/user");
            dto.setProfilePictureUrl("https://cdn.instagram.com/picture.jpg");
            dto.setDisplayName("Jane Smith");
            dto.setEmail("jane@example.com");
            dto.setNote("Top influencer");
            dto.setFollowersCount(100000);
            dto.setIsPrimary(true);
            dto.setConnectionStatus(ConnectionStatus.CONNECTED);
            dto.setLastSyncTime(now);
            dto.setCreatedTime(now.minusDays(30));
            dto.setLastUpdateTime(now);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUserId()).isEqualTo(2L);
            assertThat(dto.getSocialUserId()).isEqualTo("ig-user-123");
            assertThat(dto.getProfileUrl()).isEqualTo("https://instagram.com/user");
            assertThat(dto.getProfilePictureUrl()).isEqualTo("https://cdn.instagram.com/picture.jpg");
            assertThat(dto.getDisplayName()).isEqualTo("Jane Smith");
            assertThat(dto.getEmail()).isEqualTo("jane@example.com");
            assertThat(dto.getNote()).isEqualTo("Top influencer");
            assertThat(dto.getFollowersCount()).isEqualTo(100000);
            assertThat(dto.getIsPrimary()).isTrue();
            assertThat(dto.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
            assertThat(dto.getLastSyncTime()).isEqualTo(now);
            assertThat(dto.getCreatedTime()).isEqualTo(now.minusDays(30));
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
        }

        @Test
        @DisplayName("should accept null optional fields")
        void shouldAcceptNullOptionalFields() {
            UserSocialConnectionDtoOut dto = new UserSocialConnectionDtoOut();
            dto.setPlatform(null);
            dto.setServiceType(null);
            dto.setLastSyncTime(null);

            assertThat(dto.getPlatform()).isNull();
            assertThat(dto.getServiceType()).isNull();
            assertThat(dto.getLastSyncTime()).isNull();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle zero followers count")
        void shouldHandleZeroFollowersCount() {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();
            dto.setFollowersCount(0);

            assertThat(dto.getFollowersCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle max followers count")
        void shouldHandleMaxFollowersCount() {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();
            dto.setFollowersCount(Integer.MAX_VALUE);

            assertThat(dto.getFollowersCount()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle Unicode in display name")
        void shouldHandleUnicodeInDisplayName() {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();
            dto.setDisplayName("Jan Kowalski \uD83C\uDF89");

            assertThat(dto.getDisplayName()).contains("Jan Kowalski");
        }

        @Test
        @DisplayName("should handle long URLs")
        void shouldHandleLongUrls() {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();
            String profileUrl = "https://instagram.com/" + "a".repeat(1000);
            String profilePictureUrl = "https://cdn.instagram.com/" + "b".repeat(1000);
            dto.setProfileUrl(profileUrl);
            dto.setProfilePictureUrl(profilePictureUrl);

            assertThat(dto.getProfileUrl()).isEqualTo(profileUrl);
            assertThat(dto.getProfilePictureUrl()).isEqualTo(profilePictureUrl);
            assertThat(dto.getProfileUrl()).hasSizeGreaterThan(1000);
            assertThat(dto.getProfilePictureUrl()).hasSizeGreaterThan(1000);
        }

        @Test
        @DisplayName("should handle connection status transitions")
        void shouldHandleConnectionStatusTransitions() {
            UserSocialConnectionDtoIn dto = new UserSocialConnectionDtoIn();

            // Simulate connection lifecycle
            dto.setConnectionStatus(ConnectionStatus.CONNECTED);
            assertThat(dto.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);

            // Token expires
            dto.setConnectionStatus(ConnectionStatus.EXPIRED);
            assertThat(dto.getConnectionStatus()).isEqualTo(ConnectionStatus.EXPIRED);

            // User revokes access
            dto.setConnectionStatus(ConnectionStatus.REVOKED);
            assertThat(dto.getConnectionStatus()).isEqualTo(ConnectionStatus.REVOKED);
        }

        @Test
        @DisplayName("should handle platform name at boundary")
        void shouldHandlePlatformNameAtBoundary() {
            Platform platform = new Platform();
            platform.setName("A".repeat(255)); // Exactly at max

            Set<ConstraintViolation<Platform>> violations = validator.validate(platform);
            assertThat(violations).isEmpty();
        }
    }
}
