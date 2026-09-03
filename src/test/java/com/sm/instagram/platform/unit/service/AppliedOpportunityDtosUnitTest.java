package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Applied Opportunity DTOs.
 * Tests validation, default values, and record behavior.
 */
@DisplayName("Applied Opportunity DTOs Unit Tests")
class AppliedOpportunityDtosUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== AppliedOpportunityDtoIn Tests ====================

    @Nested
    @DisplayName("AppliedOpportunityDtoIn Tests")
    class AppliedOpportunityDtoInTests {

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(1L);

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields populated")
        void shouldPassValidationWithAllFields() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setInfluencer(1L);
            dto.setPartnershipOpportunity(2L);
            dto.setNote("Test note");
            dto.setOpportunityStatus(OpportunityStatus.APPLIED);
            dto.setExecutionDate(LocalDateTime.now().plusDays(7));
            dto.setRateStatus(RateStatus.DEFAULT);
            dto.setCompanyRateStatus(RateStatus.POSITIVE);

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when partnershipOpportunity is null")
        void shouldFailWhenPartnershipOpportunityIsNull() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(null);

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("partnershipOpportunity"));
        }

        @Test
        @DisplayName("should accept note at max length")
        void shouldAcceptNoteAtMaxLength() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(1L);
            dto.setNote("A".repeat(500));

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should reject note exceeding max length")
        void shouldRejectNoteExceedingMaxLength() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(1L);
            dto.setNote("A".repeat(501));

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("note"));
        }

        @Test
        @DisplayName("should accept null optional fields")
        void shouldAcceptNullOptionalFields() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(1L);
            dto.setInfluencer(null);
            dto.setNote(null);
            dto.setOpportunityStatus(null);
            dto.setExecutionDate(null);
            dto.setRateStatus(null);
            dto.setCompanyRateStatus(null);

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 100L, Long.MAX_VALUE})
        @DisplayName("should accept various partnership opportunity IDs")
        void shouldAcceptVariousPartnershipOpportunityIds(Long id) {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(id);

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== AppliedOpportunityContentDtoIn Tests ====================

    @Nested
    @DisplayName("AppliedOpportunityContentDtoIn Tests")
    class AppliedOpportunityContentDtoInTests {

        @Test
        @DisplayName("should pass validation with required fields")
        void shouldPassValidationWithRequiredFields() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(2L);

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with all fields populated")
        void shouldPassValidationWithAllFields() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(2L);
            dto.setContentCount(5);
            // Pentest 3.5: content urls are Vimeo-only (@VimeoUrls).
            dto.setUrls(Arrays.asList("https://vimeo.com/100000001", "https://vimeo.com/100000002"));
            dto.setDescription("Content description");
            dto.setTags("#fashion #style");
            dto.setSocialMediaLink("https://instagram.com/user/post");
            dto.setContentCreationDate(LocalDateTime.now());
            dto.setSubmissionDate(LocalDateTime.now());

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should fail when appliedOpportunityId is null")
        void shouldFailWhenAppliedOpportunityIdIsNull() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(null);
            dto.setContentTypeId(1L);

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("appliedOpportunityId"));
        }

        @Test
        @DisplayName("should fail when contentTypeId is null")
        void shouldFailWhenContentTypeIdIsNull() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(null);

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contentTypeId"));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 100})
        @DisplayName("should accept positive content count")
        void shouldAcceptPositiveContentCount(int count) {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setContentCount(count);

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("should reject non-positive content count")
        void shouldRejectNonPositiveContentCount(int count) {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setContentCount(count);

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contentCount"));
        }

        @Test
        @DisplayName("should accept null content count")
        void shouldAcceptNullContentCount() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setContentCount(null);

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should accept description at max length")
        void shouldAcceptDescriptionAtMaxLength() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setDescription("A".repeat(1000));

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should reject description exceeding max length")
        void shouldRejectDescriptionExceedingMaxLength() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setDescription("A".repeat(1001));

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
        }

        @Test
        @DisplayName("should accept tags at max length")
        void shouldAcceptTagsAtMaxLength() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setTags("A".repeat(500));

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should reject tags exceeding max length")
        void shouldRejectTagsExceedingMaxLength() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setTags("A".repeat(501));

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tags"));
        }

        @Test
        @DisplayName("should accept socialMediaLink at max length")
        void shouldAcceptSocialMediaLinkAtMaxLength() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            // Must also satisfy @SocialPostUrl — a valid Instagram link padded
            // to exactly 1000 chars ("https://instagram.com/p/" is 24).
            dto.setSocialMediaLink("https://instagram.com/p/" + "a".repeat(976));

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should accept Instagram and TikTok publication links")
        void shouldAcceptSocialHosts() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setSocialMediaLink("https://instagram.com/p/abc123");
            assertThat(validator.validate(dto)).isEmpty();

            dto.setSocialMediaLink("https://www.tiktok.com/@user/video/123");
            assertThat(validator.validate(dto)).isEmpty();
        }

        @Test
        @DisplayName("should reject socialMediaLink on non-social hosts (pentest 3.5 follow-up)")
        void shouldRejectForeignSocialMediaLink() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setSocialMediaLink("http://localhost:8000/malicious_file.html");

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("socialMediaLink"));
        }

        @Test
        @DisplayName("should reject socialMediaLink exceeding max length")
        void shouldRejectSocialMediaLinkExceedingMaxLength() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setSocialMediaLink("A".repeat(1001));

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("socialMediaLink"));
        }

        @Test
        @DisplayName("should handle list of URLs")
        void shouldHandleListOfUrls() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            // Pentest 3.5: content urls are Vimeo-only (@VimeoUrls).
            dto.setUrls(Arrays.asList(
                    "https://vimeo.com/1",
                    "https://player.vimeo.com/video/2",
                    "https://www.vimeo.com/3"
            ));

            assertThat(dto.getUrls()).hasSize(3);
            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }
    }

    // ==================== PaymentContactDto Record Tests ====================

    @Nested
    @DisplayName("PaymentContactDto Record Tests")
    class PaymentContactDtoTests {

        @Test
        @DisplayName("should create record with all fields")
        void shouldCreateRecordWithAllFields() {
            PaymentContactDto dto = new PaymentContactDto(
                    "John Doe",
                    "john@example.com",
                    "+48123456789",
                    "https://example.com/profile.jpg"
            );

            assertThat(dto.name()).isEqualTo("John Doe");
            assertThat(dto.email()).isEqualTo("john@example.com");
            assertThat(dto.phone()).isEqualTo("+48123456789");
            assertThat(dto.profilePicture()).isEqualTo("https://example.com/profile.jpg");
        }

        @Test
        @DisplayName("should allow null fields")
        void shouldAllowNullFields() {
            PaymentContactDto dto = new PaymentContactDto(null, null, null, null);

            assertThat(dto.name()).isNull();
            assertThat(dto.email()).isNull();
            assertThat(dto.phone()).isNull();
            assertThat(dto.profilePicture()).isNull();
        }

        @Test
        @DisplayName("should create with only email for privacy")
        void shouldCreateWithOnlyEmailForPrivacy() {
            PaymentContactDto dto = new PaymentContactDto(
                    "Jane Smith",
                    "jane@example.com",
                    null, // Phone hidden for privacy
                    "https://example.com/jane.jpg"
            );

            assertThat(dto.name()).isEqualTo("Jane Smith");
            assertThat(dto.email()).isEqualTo("jane@example.com");
            assertThat(dto.phone()).isNull();
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            PaymentContactDto dto1 = new PaymentContactDto("John", "john@test.com", null, null);
            PaymentContactDto dto2 = new PaymentContactDto("John", "john@test.com", null, null);
            PaymentContactDto dto3 = new PaymentContactDto("Jane", "jane@test.com", null, null);

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            PaymentContactDto dto1 = new PaymentContactDto("John", "john@test.com", null, null);
            PaymentContactDto dto2 = new PaymentContactDto("John", "john@test.com", null, null);

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            PaymentContactDto dto = new PaymentContactDto("John", "john@test.com", "+48123", null);

            String toString = dto.toString();
            assertThat(toString).contains("John");
            assertThat(toString).contains("john@test.com");
        }
    }

    // ==================== AppliedOpportunityStatisticsDto Tests ====================

    @Nested
    @DisplayName("AppliedOpportunityStatisticsDto Tests")
    class AppliedOpportunityStatisticsDtoTests {

        @Test
        @DisplayName("should create via builder")
        void shouldCreateViaBuilder() {
            AppliedOpportunityStatisticsDto dto = AppliedOpportunityStatisticsDto.builder()
                    .inProgress(5L)
                    .newOpportunities(3L)
                    .done(10L)
                    .total(18L)
                    .build();

            assertThat(dto.getInProgress()).isEqualTo(5L);
            assertThat(dto.getNewOpportunities()).isEqualTo(3L);
            assertThat(dto.getDone()).isEqualTo(10L);
            assertThat(dto.getTotal()).isEqualTo(18L);
        }

        @Test
        @DisplayName("should create with all zeros")
        void shouldCreateWithAllZeros() {
            AppliedOpportunityStatisticsDto dto = AppliedOpportunityStatisticsDto.builder()
                    .inProgress(0L)
                    .newOpportunities(0L)
                    .done(0L)
                    .total(0L)
                    .build();

            assertThat(dto.getInProgress()).isEqualTo(0L);
            assertThat(dto.getNewOpportunities()).isEqualTo(0L);
            assertThat(dto.getDone()).isEqualTo(0L);
            assertThat(dto.getTotal()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should allow null fields")
        void shouldAllowNullFields() {
            AppliedOpportunityStatisticsDto dto = AppliedOpportunityStatisticsDto.builder()
                    .build();

            assertThat(dto.getInProgress()).isNull();
            assertThat(dto.getNewOpportunities()).isNull();
            assertThat(dto.getDone()).isNull();
            assertThat(dto.getTotal()).isNull();
        }

        @Test
        @DisplayName("should handle large numbers")
        void shouldHandleLargeNumbers() {
            AppliedOpportunityStatisticsDto dto = AppliedOpportunityStatisticsDto.builder()
                    .inProgress(Long.MAX_VALUE / 4)
                    .newOpportunities(Long.MAX_VALUE / 4)
                    .done(Long.MAX_VALUE / 4)
                    .total(Long.MAX_VALUE)
                    .build();

            assertThat(dto.getTotal()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should use setters")
        void shouldUseSetters() {
            AppliedOpportunityStatisticsDto dto = AppliedOpportunityStatisticsDto.builder().build();
            dto.setInProgress(10L);
            dto.setNewOpportunities(5L);
            dto.setDone(20L);
            dto.setTotal(35L);

            assertThat(dto.getInProgress()).isEqualTo(10L);
            assertThat(dto.getNewOpportunities()).isEqualTo(5L);
            assertThat(dto.getDone()).isEqualTo(20L);
            assertThat(dto.getTotal()).isEqualTo(35L);
        }
    }

    // ==================== AppliedOpportunityDtoOut Tests ====================

    @Nested
    @DisplayName("AppliedOpportunityDtoOut Tests")
    class AppliedOpportunityDtoOutTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            AppliedOpportunityDtoOut dto = new AppliedOpportunityDtoOut();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getOpportunityStatus()).isNull();
            assertThat(dto.getRateStatus()).isNull();
            assertThat(dto.getCompanyRateStatus()).isNull();
            assertThat(dto.getExecutionDate()).isNull();
            assertThat(dto.getCreatedTime()).isNull();
            assertThat(dto.getNote()).isNull();
            assertThat(dto.getInfluencer()).isNull();
            assertThat(dto.getPartnershipOpportunity()).isNull();
            assertThat(dto.getContentSubmissions()).isNull();
        }

        @Test
        @DisplayName("should set all fields correctly")
        void shouldSetAllFieldsCorrectly() {
            AppliedOpportunityDtoOut dto = new AppliedOpportunityDtoOut();
            LocalDateTime now = LocalDateTime.now();

            dto.setId(1L);
            dto.setExecutionDate(now.plusDays(7));
            dto.setCreatedTime(now);
            dto.setNote("Test note");
            dto.setLastUpdateTime(now.plusHours(1));
            dto.setUpdater("admin@example.com");

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getExecutionDate()).isEqualTo(now.plusDays(7));
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getNote()).isEqualTo("Test note");
            assertThat(dto.getLastUpdateTime()).isEqualTo(now.plusHours(1));
            assertThat(dto.getUpdater()).isEqualTo("admin@example.com");
        }

        @Test
        @DisplayName("should handle content submissions list")
        void shouldHandleContentSubmissionsList() {
            AppliedOpportunityDtoOut dto = new AppliedOpportunityDtoOut();
            AppliedOpportunityContentDtoOut content1 = new AppliedOpportunityContentDtoOut();
            content1.setId(1L);
            AppliedOpportunityContentDtoOut content2 = new AppliedOpportunityContentDtoOut();
            content2.setId(2L);

            dto.setContentSubmissions(List.of(content1, content2));

            assertThat(dto.getContentSubmissions()).hasSize(2);
            assertThat(dto.getContentSubmissions().get(0).getId()).isEqualTo(1L);
        }
    }

    // ==================== AppliedOpportunityContentDtoOut Tests ====================

    @Nested
    @DisplayName("AppliedOpportunityContentDtoOut Tests")
    class AppliedOpportunityContentDtoOutTests {

        @Test
        @DisplayName("should create with default values")
        void shouldCreateWithDefaultValues() {
            AppliedOpportunityContentDtoOut dto = new AppliedOpportunityContentDtoOut();

            assertThat(dto.getId()).isNull();
        }

        @Test
        @DisplayName("should set id correctly")
        void shouldSetIdCorrectly() {
            AppliedOpportunityContentDtoOut dto = new AppliedOpportunityContentDtoOut();
            dto.setId(123L);

            assertThat(dto.getId()).isEqualTo(123L);
        }
    }

    // ==================== OpportunityStatusDtoOut Tests ====================

    @Nested
    @DisplayName("OpportunityStatusDtoOut Tests")
    class OpportunityStatusDtoOutTests {

        @Test
        @DisplayName("should create with builder and all fields")
        void shouldCreateWithBuilderAndAllFields() {
            OpportunityStatusDtoOut dto = OpportunityStatusDtoOut.builder()
                    .value("APPLIED")
                    .label("Applied")
                    .description("Application submitted")
                    .originalLabel("APPLIED")
                    .colorTheme("info")
                    .icon("clock")
                    .aliases(Arrays.asList("submitted", "pending"))
                    .possibleTransitions(Arrays.asList("CONTENT_APPROVED", "REJECTED"))
                    .isTerminal(false)
                    .isSuccessful(false)
                    .build();

            assertThat(dto.getValue()).isEqualTo("APPLIED");
            assertThat(dto.getLabel()).isEqualTo("Applied");
            assertThat(dto.getDescription()).isEqualTo("Application submitted");
            assertThat(dto.getOriginalLabel()).isEqualTo("APPLIED");
            assertThat(dto.getColorTheme()).isEqualTo("info");
            assertThat(dto.getIcon()).isEqualTo("clock");
            assertThat(dto.getAliases()).containsExactly("submitted", "pending");
            assertThat(dto.getPossibleTransitions()).containsExactly("CONTENT_APPROVED", "REJECTED");
            assertThat(dto.isTerminal()).isFalse();
            assertThat(dto.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            OpportunityStatusDtoOut dto = new OpportunityStatusDtoOut();

            assertThat(dto.getValue()).isNull();
            assertThat(dto.getLabel()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getOriginalLabel()).isNull();
            assertThat(dto.getColorTheme()).isNull();
            assertThat(dto.getIcon()).isNull();
            assertThat(dto.getAliases()).isNull();
            assertThat(dto.getPossibleTransitions()).isNull();
            assertThat(dto.isTerminal()).isFalse();
            assertThat(dto.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            OpportunityStatusDtoOut dto = new OpportunityStatusDtoOut(
                    "COMPLETED", "Completed", "Work completed", "COMPLETED",
                    "success", "check-circle",
                    Arrays.asList("done", "finished"),
                    List.of(),
                    true, true
            );

            assertThat(dto.getValue()).isEqualTo("COMPLETED");
            assertThat(dto.getLabel()).isEqualTo("Completed");
            assertThat(dto.isTerminal()).isTrue();
            assertThat(dto.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("should handle null lists")
        void shouldHandleNullLists() {
            OpportunityStatusDtoOut dto = OpportunityStatusDtoOut.builder()
                    .value("TEST")
                    .aliases(null)
                    .possibleTransitions(null)
                    .build();

            assertThat(dto.getAliases()).isNull();
            assertThat(dto.getPossibleTransitions()).isNull();
        }

        @Test
        @DisplayName("should handle empty lists")
        void shouldHandleEmptyLists() {
            OpportunityStatusDtoOut dto = OpportunityStatusDtoOut.builder()
                    .value("TEST")
                    .aliases(List.of())
                    .possibleTransitions(List.of())
                    .build();

            assertThat(dto.getAliases()).isEmpty();
            assertThat(dto.getPossibleTransitions()).isEmpty();
        }

        @Test
        @DisplayName("should handle terminal and successful status")
        void shouldHandleTerminalAndSuccessfulStatus() {
            OpportunityStatusDtoOut terminalSuccess = OpportunityStatusDtoOut.builder()
                    .value("COMPLETED")
                    .isTerminal(true)
                    .isSuccessful(true)
                    .build();

            OpportunityStatusDtoOut terminalFailure = OpportunityStatusDtoOut.builder()
                    .value("REJECTED")
                    .isTerminal(true)
                    .isSuccessful(false)
                    .build();

            OpportunityStatusDtoOut nonTerminal = OpportunityStatusDtoOut.builder()
                    .value("IN_PROGRESS")
                    .isTerminal(false)
                    .isSuccessful(false)
                    .build();

            assertThat(terminalSuccess.isTerminal()).isTrue();
            assertThat(terminalSuccess.isSuccessful()).isTrue();

            assertThat(terminalFailure.isTerminal()).isTrue();
            assertThat(terminalFailure.isSuccessful()).isFalse();

            assertThat(nonTerminal.isTerminal()).isFalse();
            assertThat(nonTerminal.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("should set fields via setters")
        void shouldSetFieldsViaSetters() {
            OpportunityStatusDtoOut dto = new OpportunityStatusDtoOut();
            dto.setValue("APPLIED");
            dto.setLabel("Applied");
            dto.setDescription("Application submitted");
            dto.setOriginalLabel("APPLIED");
            dto.setColorTheme("info");
            dto.setIcon("clock");
            dto.setAliases(Arrays.asList("a1", "a2"));
            dto.setPossibleTransitions(Arrays.asList("t1", "t2"));
            dto.setTerminal(true);
            dto.setSuccessful(true);

            assertThat(dto.getValue()).isEqualTo("APPLIED");
            assertThat(dto.getLabel()).isEqualTo("Applied");
            assertThat(dto.getDescription()).isEqualTo("Application submitted");
            assertThat(dto.getOriginalLabel()).isEqualTo("APPLIED");
            assertThat(dto.getColorTheme()).isEqualTo("info");
            assertThat(dto.getIcon()).isEqualTo("clock");
            assertThat(dto.getAliases()).containsExactly("a1", "a2");
            assertThat(dto.getPossibleTransitions()).containsExactly("t1", "t2");
            assertThat(dto.isTerminal()).isTrue();
            assertThat(dto.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            OpportunityStatusDtoOut dto1 = OpportunityStatusDtoOut.builder()
                    .value("APPLIED")
                    .label("Applied")
                    .isTerminal(false)
                    .build();
            OpportunityStatusDtoOut dto2 = OpportunityStatusDtoOut.builder()
                    .value("APPLIED")
                    .label("Applied")
                    .isTerminal(false)
                    .build();
            OpportunityStatusDtoOut dto3 = OpportunityStatusDtoOut.builder()
                    .value("COMPLETED")
                    .label("Completed")
                    .isTerminal(true)
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            OpportunityStatusDtoOut dto1 = OpportunityStatusDtoOut.builder()
                    .value("APPLIED")
                    .label("Applied")
                    .build();
            OpportunityStatusDtoOut dto2 = OpportunityStatusDtoOut.builder()
                    .value("APPLIED")
                    .label("Applied")
                    .build();

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            OpportunityStatusDtoOut dto = OpportunityStatusDtoOut.builder()
                    .value("APPLIED")
                    .label("Applied")
                    .colorTheme("info")
                    .build();

            String toString = dto.toString();
            assertThat(toString).contains("APPLIED");
            assertThat(toString).contains("Applied");
            assertThat(toString).contains("info");
        }
    }

    // ==================== RateStatusDtoOut Tests ====================

    @Nested
    @DisplayName("RateStatusDtoOut Tests")
    class RateStatusDtoOutTests {

        @Test
        @DisplayName("should create with builder and all fields")
        void shouldCreateWithBuilderAndAllFields() {
            RateStatusDtoOut dto = RateStatusDtoOut.builder()
                    .value("POSITIVE")
                    .label("Positive")
                    .originalLabel("POSITIVE")
                    .colorTheme("success")
                    .icon("thumbs-up")
                    .build();

            assertThat(dto.getValue()).isEqualTo("POSITIVE");
            assertThat(dto.getLabel()).isEqualTo("Positive");
            assertThat(dto.getOriginalLabel()).isEqualTo("POSITIVE");
            assertThat(dto.getColorTheme()).isEqualTo("success");
            assertThat(dto.getIcon()).isEqualTo("thumbs-up");
        }

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            RateStatusDtoOut dto = new RateStatusDtoOut();

            assertThat(dto.getValue()).isNull();
            assertThat(dto.getLabel()).isNull();
            assertThat(dto.getOriginalLabel()).isNull();
            assertThat(dto.getColorTheme()).isNull();
            assertThat(dto.getIcon()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            RateStatusDtoOut dto = new RateStatusDtoOut(
                    "NEGATIVE", "Negative", "NEGATIVE", "danger", "thumbs-down"
            );

            assertThat(dto.getValue()).isEqualTo("NEGATIVE");
            assertThat(dto.getLabel()).isEqualTo("Negative");
            assertThat(dto.getOriginalLabel()).isEqualTo("NEGATIVE");
            assertThat(dto.getColorTheme()).isEqualTo("danger");
            assertThat(dto.getIcon()).isEqualTo("thumbs-down");
        }

        @Test
        @DisplayName("should handle partial fields")
        void shouldHandlePartialFields() {
            RateStatusDtoOut dto = RateStatusDtoOut.builder()
                    .value("DEFAULT")
                    .label("Default")
                    .build();

            assertThat(dto.getValue()).isEqualTo("DEFAULT");
            assertThat(dto.getLabel()).isEqualTo("Default");
            assertThat(dto.getOriginalLabel()).isNull();
            assertThat(dto.getColorTheme()).isNull();
            assertThat(dto.getIcon()).isNull();
        }

        @Test
        @DisplayName("should handle null fields")
        void shouldHandleNullFields() {
            RateStatusDtoOut dto = RateStatusDtoOut.builder()
                    .value(null)
                    .label(null)
                    .originalLabel(null)
                    .colorTheme(null)
                    .icon(null)
                    .build();

            assertThat(dto.getValue()).isNull();
            assertThat(dto.getLabel()).isNull();
            assertThat(dto.getOriginalLabel()).isNull();
            assertThat(dto.getColorTheme()).isNull();
            assertThat(dto.getIcon()).isNull();
        }

        @Test
        @DisplayName("should set fields via setters")
        void shouldSetFieldsViaSetters() {
            RateStatusDtoOut dto = new RateStatusDtoOut();
            dto.setValue("POSITIVE");
            dto.setLabel("Positive");
            dto.setOriginalLabel("POSITIVE");
            dto.setColorTheme("success");
            dto.setIcon("thumbs-up");

            assertThat(dto.getValue()).isEqualTo("POSITIVE");
            assertThat(dto.getLabel()).isEqualTo("Positive");
            assertThat(dto.getOriginalLabel()).isEqualTo("POSITIVE");
            assertThat(dto.getColorTheme()).isEqualTo("success");
            assertThat(dto.getIcon()).isEqualTo("thumbs-up");
        }

        @Test
        @DisplayName("should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            RateStatusDtoOut dto1 = RateStatusDtoOut.builder()
                    .value("POSITIVE")
                    .label("Positive")
                    .build();
            RateStatusDtoOut dto2 = RateStatusDtoOut.builder()
                    .value("POSITIVE")
                    .label("Positive")
                    .build();
            RateStatusDtoOut dto3 = RateStatusDtoOut.builder()
                    .value("NEGATIVE")
                    .label("Negative")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1).isNotEqualTo(dto3);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            RateStatusDtoOut dto1 = RateStatusDtoOut.builder()
                    .value("DEFAULT")
                    .colorTheme("secondary")
                    .build();
            RateStatusDtoOut dto2 = RateStatusDtoOut.builder()
                    .value("DEFAULT")
                    .colorTheme("secondary")
                    .build();

            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            RateStatusDtoOut dto = RateStatusDtoOut.builder()
                    .value("POSITIVE")
                    .label("Positive")
                    .colorTheme("success")
                    .build();

            String toString = dto.toString();
            assertThat(toString).contains("POSITIVE");
            assertThat(toString).contains("Positive");
            assertThat(toString).contains("success");
        }

        @Test
        @DisplayName("should create for all rate status values")
        void shouldCreateForAllRateStatusValues() {
            RateStatusDtoOut defaultStatus = RateStatusDtoOut.builder()
                    .value("DEFAULT")
                    .label("Default")
                    .colorTheme("secondary")
                    .icon("minus")
                    .build();

            RateStatusDtoOut positiveStatus = RateStatusDtoOut.builder()
                    .value("POSITIVE")
                    .label("Positive")
                    .colorTheme("success")
                    .icon("thumbs-up")
                    .build();

            RateStatusDtoOut negativeStatus = RateStatusDtoOut.builder()
                    .value("NEGATIVE")
                    .label("Negative")
                    .colorTheme("danger")
                    .icon("thumbs-down")
                    .build();

            assertThat(defaultStatus.getValue()).isEqualTo("DEFAULT");
            assertThat(positiveStatus.getValue()).isEqualTo("POSITIVE");
            assertThat(negativeStatus.getValue()).isEqualTo("NEGATIVE");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty strings in note")
        void shouldHandleEmptyStringsInNote() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(1L);
            dto.setNote("");

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle whitespace-only note")
        void shouldHandleWhitespaceOnlyNote() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(1L);
            dto.setNote("   ");

            Set<ConstraintViolation<AppliedOpportunityDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle unicode in content description")
        void shouldHandleUnicodeInContentDescription() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setDescription("Content with emojis: \uD83D\uDE00\uD83C\uDF89\uD83D\uDC4D and Polish: ąęćłóśżź");

            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle empty URL list")
        void shouldHandleEmptyUrlList() {
            AppliedOpportunityContentDtoIn dto = new AppliedOpportunityContentDtoIn();
            dto.setAppliedOpportunityId(1L);
            dto.setContentTypeId(1L);
            dto.setUrls(List.of());

            assertThat(dto.getUrls()).isEmpty();
            Set<ConstraintViolation<AppliedOpportunityContentDtoIn>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should handle statistics with mismatched total")
        void shouldHandleStatisticsWithMismatchedTotal() {
            // Total doesn't have to equal sum of parts - this is just a DTO
            AppliedOpportunityStatisticsDto dto = AppliedOpportunityStatisticsDto.builder()
                    .inProgress(10L)
                    .newOpportunities(5L)
                    .done(3L)
                    .total(100L) // Intentionally doesn't match
                    .build();

            // DTO doesn't validate business logic
            assertThat(dto.getTotal()).isEqualTo(100L);
        }
    }
}
