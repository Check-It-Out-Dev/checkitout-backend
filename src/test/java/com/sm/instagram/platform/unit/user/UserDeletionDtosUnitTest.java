package com.sm.instagram.platform.unit.user;

import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.user.dto.DeletionBlocker;
import com.sm.instagram.platform.user.dto.DeletionBlockerCategory;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for User Deletion DTOs.
 */
@DisplayName("User Deletion DTOs Unit Tests")
@ExtendWith(MockitoExtension.class)
class UserDeletionDtosUnitTest {

    @Mock
    private DictionaryService dictionaryService;

    // ==================== DeletionBlockerCategory Tests ====================

    @Nested
    @DisplayName("DeletionBlockerCategory Tests")
    class DeletionBlockerCategoryTests {

        @Test
        @DisplayName("should have exactly 9 enum values")
        void shouldHaveExactlyNineEnumValues() {
            assertThat(DeletionBlockerCategory.values()).hasSize(9);
        }

        @ParameterizedTest
        @EnumSource(DeletionBlockerCategory.class)
        @DisplayName("should have non-null colorTheme for all values")
        void shouldHaveNonNullColorThemeForAllValues(DeletionBlockerCategory category) {
            assertThat(category.getColorTheme()).isNotNull();
            assertThat(category.getColorTheme()).isNotEmpty();
        }

        @ParameterizedTest
        @EnumSource(DeletionBlockerCategory.class)
        @DisplayName("should have non-null icon for all values")
        void shouldHaveNonNullIconForAllValues(DeletionBlockerCategory category) {
            assertThat(category.getIcon()).isNotNull();
            assertThat(category.getIcon()).isNotEmpty();
        }

        @Test
        @DisplayName("ACTIVE_OPPORTUNITIES should have correct properties")
        void activeOpportunitiesShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.ACTIVE_OPPORTUNITIES;

            assertThat(category.getColorTheme()).isEqualTo("warning");
            assertThat(category.getIcon()).isEqualTo("briefcase");
        }

        @Test
        @DisplayName("PENDING_OPPORTUNITIES should have correct properties")
        void pendingOpportunitiesShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.PENDING_OPPORTUNITIES;

            assertThat(category.getColorTheme()).isEqualTo("info");
            assertThat(category.getIcon()).isEqualTo("clock");
        }

        @Test
        @DisplayName("ACTIVE_PARTNERSHIP_OPPORTUNITIES should have correct properties")
        void activePartnershipOpportunitiesShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES;

            assertThat(category.getColorTheme()).isEqualTo("warning");
            assertThat(category.getIcon()).isEqualTo("handshake");
        }

        @Test
        @DisplayName("OPPORTUNITIES_WITH_APPLICATIONS should have correct properties")
        void opportunitiesWithApplicationsShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.OPPORTUNITIES_WITH_APPLICATIONS;

            assertThat(category.getColorTheme()).isEqualTo("warning");
            assertThat(category.getIcon()).isEqualTo("users");
        }

        @Test
        @DisplayName("LAST_ADMIN should have correct properties")
        void lastAdminShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.LAST_ADMIN;

            assertThat(category.getColorTheme()).isEqualTo("danger");
            assertThat(category.getIcon()).isEqualTo("shield-alert");
        }

        @Test
        @DisplayName("OPEN_SUPPORT_TICKETS should have correct properties")
        void openSupportTicketsShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.OPEN_SUPPORT_TICKETS;

            assertThat(category.getColorTheme()).isEqualTo("warning");
            assertThat(category.getIcon()).isEqualTo("help-circle");
        }

        @Test
        @DisplayName("SUPPORT_TICKETS_HISTORY should have correct properties")
        void supportTicketsHistoryShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.SUPPORT_TICKETS_HISTORY;

            assertThat(category.getColorTheme()).isEqualTo("info");
            assertThat(category.getIcon()).isEqualTo("archive");
        }

        @Test
        @DisplayName("RECENT_ACTIVITY should have correct properties")
        void recentActivityShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.RECENT_ACTIVITY;

            assertThat(category.getColorTheme()).isEqualTo("info");
            assertThat(category.getIcon()).isEqualTo("activity");
        }

        @Test
        @DisplayName("DATA_RETENTION_REQUIRED should have correct properties")
        void dataRetentionRequiredShouldHaveCorrectProperties() {
            DeletionBlockerCategory category = DeletionBlockerCategory.DATA_RETENTION_REQUIRED;

            assertThat(category.getColorTheme()).isEqualTo("warning");
            assertThat(category.getIcon()).isEqualTo("database");
        }

        @Test
        @DisplayName("getLabel should return translated label when available")
        void getLabelShouldReturnTranslatedLabelWhenAvailable() {
            DeletionBlockerCategory category = DeletionBlockerCategory.LAST_ADMIN;
            when(dictionaryService.getTranslation(eq("DELETION_BLOCKER_LAST_ADMIN"), eq("en")))
                    .thenReturn(Optional.of("Last Administrator"));

            String label = category.getLabel(dictionaryService, Locale.ENGLISH);

            assertThat(label).isEqualTo("Last Administrator");
        }

        @Test
        @DisplayName("getLabel should return enum name when translation not available")
        void getLabelShouldReturnEnumNameWhenTranslationNotAvailable() {
            DeletionBlockerCategory category = DeletionBlockerCategory.LAST_ADMIN;
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String label = category.getLabel(dictionaryService, Locale.ENGLISH);

            assertThat(label).isEqualTo("LAST_ADMIN");
        }

        @Test
        @DisplayName("getDescription should return translated description when available")
        void getDescriptionShouldReturnTranslatedDescriptionWhenAvailable() {
            DeletionBlockerCategory category = DeletionBlockerCategory.ACTIVE_OPPORTUNITIES;
            when(dictionaryService.getTranslation(eq("DELETION_BLOCKER_ACTIVE_OPPORTUNITIES_DESC"), eq("de")))
                    .thenReturn(Optional.of("Sie haben aktive Opportunities"));

            String description = category.getDescription(dictionaryService, Locale.GERMAN);

            assertThat(description).isEqualTo("Sie haben aktive Opportunities");
        }

        @Test
        @DisplayName("getDescription should return enum name when translation not available")
        void getDescriptionShouldReturnEnumNameWhenTranslationNotAvailable() {
            DeletionBlockerCategory category = DeletionBlockerCategory.ACTIVE_OPPORTUNITIES;
            when(dictionaryService.getTranslation(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            String description = category.getDescription(dictionaryService, Locale.ENGLISH);

            assertThat(description).isEqualTo("ACTIVE_OPPORTUNITIES");
        }

        @ParameterizedTest
        @EnumSource(DeletionBlockerCategory.class)
        @DisplayName("getLabel should construct correct dictionary key for all categories")
        void getLabelShouldConstructCorrectDictionaryKeyForAllCategories(DeletionBlockerCategory category) {
            when(dictionaryService.getTranslation(eq("DELETION_BLOCKER_" + category.name()), eq("en")))
                    .thenReturn(Optional.of("Translated: " + category.name()));

            String label = category.getLabel(dictionaryService, Locale.ENGLISH);

            assertThat(label).isEqualTo("Translated: " + category.name());
        }

        @ParameterizedTest
        @EnumSource(DeletionBlockerCategory.class)
        @DisplayName("getDescription should construct correct dictionary key for all categories")
        void getDescriptionShouldConstructCorrectDictionaryKeyForAllCategories(DeletionBlockerCategory category) {
            when(dictionaryService.getTranslation(eq("DELETION_BLOCKER_" + category.name() + "_DESC"), eq("en")))
                    .thenReturn(Optional.of("Description: " + category.name()));

            String description = category.getDescription(dictionaryService, Locale.ENGLISH);

            assertThat(description).isEqualTo("Description: " + category.name());
        }

        @Test
        @DisplayName("should be retrievable by valueOf")
        void shouldBeRetrievableByValueOf() {
            assertThat(DeletionBlockerCategory.valueOf("LAST_ADMIN")).isEqualTo(DeletionBlockerCategory.LAST_ADMIN);
            assertThat(DeletionBlockerCategory.valueOf("ACTIVE_OPPORTUNITIES")).isEqualTo(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
        }
    }

    // ==================== DeletionBlocker Tests ====================

    @Nested
    @DisplayName("DeletionBlocker Tests")
    class DeletionBlockerTests {

        @Test
        @DisplayName("should create with builder and all fields")
        void shouldCreateWithBuilderAndAllFields() {
            List<Long> entityIds = Arrays.asList(1L, 2L, 3L);

            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("User has active opportunities")
                    .description("Complete or cancel opportunities before deletion")
                    .count(3)
                    .entityIds(entityIds)
                    .entityType("Opportunity")
                    .entityDescription("Active campaign opportunities")
                    .build();

            assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
            assertThat(blocker.getReason()).isEqualTo("User has active opportunities");
            assertThat(blocker.getDescription()).isEqualTo("Complete or cancel opportunities before deletion");
            assertThat(blocker.getCount()).isEqualTo(3);
            assertThat(blocker.getEntityIds()).containsExactly(1L, 2L, 3L);
            assertThat(blocker.getEntityType()).isEqualTo("Opportunity");
            assertThat(blocker.getEntityDescription()).isEqualTo("Active campaign opportunities");
        }

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            DeletionBlocker blocker = new DeletionBlocker();

            assertThat(blocker.getCategory()).isNull();
            assertThat(blocker.getReason()).isNull();
            assertThat(blocker.getDescription()).isNull();
            assertThat(blocker.getCount()).isNull();
            assertThat(blocker.getEntityIds()).isNull();
            assertThat(blocker.getEntityType()).isNull();
            assertThat(blocker.getEntityDescription()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            List<Long> entityIds = Arrays.asList(100L, 200L);

            DeletionBlocker blocker = new DeletionBlocker(
                    DeletionBlockerCategory.LAST_ADMIN,
                    "Last admin reason",
                    "Transfer admin rights",
                    1,
                    entityIds,
                    "Company",
                    "Companies where user is last admin"
            );

            assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.LAST_ADMIN);
            assertThat(blocker.getReason()).isEqualTo("Last admin reason");
            assertThat(blocker.getDescription()).isEqualTo("Transfer admin rights");
            assertThat(blocker.getCount()).isEqualTo(1);
            assertThat(blocker.getEntityIds()).containsExactly(100L, 200L);
            assertThat(blocker.getEntityType()).isEqualTo("Company");
            assertThat(blocker.getEntityDescription()).isEqualTo("Companies where user is last admin");
        }

        @Test
        @DisplayName("should set fields via setters")
        void shouldSetFieldsViaSetters() {
            DeletionBlocker blocker = new DeletionBlocker();
            List<Long> entityIds = Arrays.asList(5L, 6L);

            blocker.setCategory(DeletionBlockerCategory.OPEN_SUPPORT_TICKETS);
            blocker.setReason("Open tickets exist");
            blocker.setDescription("Close support tickets first");
            blocker.setCount(2);
            blocker.setEntityIds(entityIds);
            blocker.setEntityType("SupportTicket");
            blocker.setEntityDescription("Open support tickets");

            assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.OPEN_SUPPORT_TICKETS);
            assertThat(blocker.getReason()).isEqualTo("Open tickets exist");
            assertThat(blocker.getDescription()).isEqualTo("Close support tickets first");
            assertThat(blocker.getCount()).isEqualTo(2);
            assertThat(blocker.getEntityIds()).containsExactly(5L, 6L);
            assertThat(blocker.getEntityType()).isEqualTo("SupportTicket");
            assertThat(blocker.getEntityDescription()).isEqualTo("Open support tickets");
        }

        @Test
        @DisplayName("should handle null category")
        void shouldHandleNullCategory() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(null)
                    .reason("No category")
                    .build();

            assertThat(blocker.getCategory()).isNull();
            assertThat(blocker.getReason()).isEqualTo("No category");
        }

        @Test
        @DisplayName("should handle empty entity IDs list")
        void shouldHandleEmptyEntityIdsList() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.RECENT_ACTIVITY)
                    .entityIds(Collections.emptyList())
                    .count(0)
                    .build();

            assertThat(blocker.getEntityIds()).isEmpty();
            assertThat(blocker.getCount()).isZero();
        }

        @Test
        @DisplayName("should handle null entity IDs list")
        void shouldHandleNullEntityIdsList() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.DATA_RETENTION_REQUIRED)
                    .entityIds(null)
                    .build();

            assertThat(blocker.getEntityIds()).isNull();
        }

        @Test
        @DisplayName("should implement equals correctly for equal objects")
        void shouldImplementEqualsCorrectlyForEqualObjects() {
            List<Long> entityIds = Arrays.asList(1L, 2L);

            DeletionBlocker blocker1 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("Test reason")
                    .count(2)
                    .entityIds(entityIds)
                    .build();

            DeletionBlocker blocker2 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("Test reason")
                    .count(2)
                    .entityIds(entityIds)
                    .build();

            assertThat(blocker1).isEqualTo(blocker2);
        }

        @Test
        @DisplayName("should implement equals correctly for different objects")
        void shouldImplementEqualsCorrectlyForDifferentObjects() {
            DeletionBlocker blocker1 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("Reason 1")
                    .build();

            DeletionBlocker blocker2 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.LAST_ADMIN)
                    .reason("Reason 2")
                    .build();

            assertThat(blocker1).isNotEqualTo(blocker2);
        }

        @Test
        @DisplayName("should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            DeletionBlocker blocker1 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.OPEN_SUPPORT_TICKETS)
                    .count(5)
                    .build();

            DeletionBlocker blocker2 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.OPEN_SUPPORT_TICKETS)
                    .count(5)
                    .build();

            assertThat(blocker1.hashCode()).isEqualTo(blocker2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.LAST_ADMIN)
                    .reason("Last admin")
                    .count(1)
                    .build();

            String toString = blocker.toString();

            assertThat(toString).contains("DeletionBlocker");
            assertThat(toString).contains("LAST_ADMIN");
            assertThat(toString).contains("Last admin");
            assertThat(toString).contains("count=1");
        }

        @Test
        @DisplayName("should handle zero count")
        void shouldHandleZeroCount() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.RECENT_ACTIVITY)
                    .count(0)
                    .build();

            assertThat(blocker.getCount()).isZero();
        }

        @Test
        @DisplayName("should handle large count")
        void shouldHandleLargeCount() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.SUPPORT_TICKETS_HISTORY)
                    .count(Integer.MAX_VALUE)
                    .build();

            assertThat(blocker.getCount()).isEqualTo(Integer.MAX_VALUE);
        }
    }

    // ==================== DeletionEligibilityDto Tests ====================

    @Nested
    @DisplayName("DeletionEligibilityDto Tests")
    class DeletionEligibilityDtoTests {

        @Test
        @DisplayName("should create with builder and all fields")
        void shouldCreateWithBuilderAndAllFields() {
            DeletionBlocker softBlocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("Active opportunities")
                    .count(2)
                    .build();

            DeletionBlocker permanentBlocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.DATA_RETENTION_REQUIRED)
                    .reason("Data must be retained")
                    .count(1)
                    .build();

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(12345L)
                    .firebaseUserId("firebase-uid-123")
                    .userEmail("user@example.com")
                    .userType("INFLUENCER")
                    .canSoftDelete(false)
                    .canPermanentDelete(false)
                    .softDeleteBlockers(List.of(softBlocker))
                    .permanentDeleteBlockers(List.of(permanentBlocker))
                    .summary("User cannot be deleted due to active opportunities")
                    .build();

            assertThat(dto.getUserId()).isEqualTo(12345L);
            assertThat(dto.getFirebaseUserId()).isEqualTo("firebase-uid-123");
            assertThat(dto.getUserEmail()).isEqualTo("user@example.com");
            assertThat(dto.getUserType()).isEqualTo("INFLUENCER");
            assertThat(dto.isCanSoftDelete()).isFalse();
            assertThat(dto.isCanPermanentDelete()).isFalse();
            assertThat(dto.getSoftDeleteBlockers()).hasSize(1);
            assertThat(dto.getPermanentDeleteBlockers()).hasSize(1);
            assertThat(dto.getSummary()).isEqualTo("User cannot be deleted due to active opportunities");
        }

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            DeletionEligibilityDto dto = new DeletionEligibilityDto();

            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getFirebaseUserId()).isNull();
            assertThat(dto.getUserEmail()).isNull();
            assertThat(dto.getUserType()).isNull();
            assertThat(dto.isCanSoftDelete()).isFalse();
            assertThat(dto.isCanPermanentDelete()).isFalse();
            assertThat(dto.getSoftDeleteBlockers()).isNull();
            assertThat(dto.getPermanentDeleteBlockers()).isNull();
            assertThat(dto.getSummary()).isNull();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            List<DeletionBlocker> softBlockers = new ArrayList<>();
            List<DeletionBlocker> permanentBlockers = new ArrayList<>();

            DeletionEligibilityDto dto = new DeletionEligibilityDto(
                    99L,
                    "fb-123",
                    "test@test.com",
                    "COMPANY",
                    true,
                    false,
                    softBlockers,
                    permanentBlockers,
                    "Partial deletion allowed"
            );

            assertThat(dto.getUserId()).isEqualTo(99L);
            assertThat(dto.getFirebaseUserId()).isEqualTo("fb-123");
            assertThat(dto.getUserEmail()).isEqualTo("test@test.com");
            assertThat(dto.getUserType()).isEqualTo("COMPANY");
            assertThat(dto.isCanSoftDelete()).isTrue();
            assertThat(dto.isCanPermanentDelete()).isFalse();
            assertThat(dto.getSoftDeleteBlockers()).isEmpty();
            assertThat(dto.getPermanentDeleteBlockers()).isEmpty();
            assertThat(dto.getSummary()).isEqualTo("Partial deletion allowed");
        }

        @Test
        @DisplayName("should set fields via setters")
        void shouldSetFieldsViaSetters() {
            DeletionEligibilityDto dto = new DeletionEligibilityDto();
            List<DeletionBlocker> blockers = List.of(
                    DeletionBlocker.builder().category(DeletionBlockerCategory.LAST_ADMIN).build()
            );

            dto.setUserId(555L);
            dto.setFirebaseUserId("fb-555");
            dto.setUserEmail("setter@test.com");
            dto.setUserType("ADMIN");
            dto.setCanSoftDelete(true);
            dto.setCanPermanentDelete(true);
            dto.setSoftDeleteBlockers(blockers);
            dto.setPermanentDeleteBlockers(blockers);
            dto.setSummary("Set via setters");

            assertThat(dto.getUserId()).isEqualTo(555L);
            assertThat(dto.getFirebaseUserId()).isEqualTo("fb-555");
            assertThat(dto.getUserEmail()).isEqualTo("setter@test.com");
            assertThat(dto.getUserType()).isEqualTo("ADMIN");
            assertThat(dto.isCanSoftDelete()).isTrue();
            assertThat(dto.isCanPermanentDelete()).isTrue();
            assertThat(dto.getSoftDeleteBlockers()).hasSize(1);
            assertThat(dto.getPermanentDeleteBlockers()).hasSize(1);
            assertThat(dto.getSummary()).isEqualTo("Set via setters");
        }

        @Test
        @DisplayName("should handle eligible for deletion scenario")
        void shouldHandleEligibleForDeletionScenario() {
            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(1L)
                    .firebaseUserId("fb-1")
                    .userEmail("eligible@test.com")
                    .userType("INFLUENCER")
                    .canSoftDelete(true)
                    .canPermanentDelete(true)
                    .softDeleteBlockers(Collections.emptyList())
                    .permanentDeleteBlockers(Collections.emptyList())
                    .summary("User is eligible for deletion")
                    .build();

            assertThat(dto.isCanSoftDelete()).isTrue();
            assertThat(dto.isCanPermanentDelete()).isTrue();
            assertThat(dto.getSoftDeleteBlockers()).isEmpty();
            assertThat(dto.getPermanentDeleteBlockers()).isEmpty();
        }

        @Test
        @DisplayName("should handle blocked from all deletion scenario")
        void shouldHandleBlockedFromAllDeletionScenario() {
            DeletionBlocker lastAdminBlocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.LAST_ADMIN)
                    .reason("User is the last admin")
                    .count(1)
                    .entityIds(List.of(10L))
                    .build();

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(2L)
                    .canSoftDelete(false)
                    .canPermanentDelete(false)
                    .softDeleteBlockers(List.of(lastAdminBlocker))
                    .permanentDeleteBlockers(List.of(lastAdminBlocker))
                    .summary("Blocked: last admin")
                    .build();

            assertThat(dto.isCanSoftDelete()).isFalse();
            assertThat(dto.isCanPermanentDelete()).isFalse();
            assertThat(dto.getSoftDeleteBlockers()).hasSize(1);
            assertThat(dto.getSoftDeleteBlockers().get(0).getCategory()).isEqualTo(DeletionBlockerCategory.LAST_ADMIN);
        }

        @Test
        @DisplayName("should handle soft delete allowed but permanent blocked scenario")
        void shouldHandleSoftDeleteAllowedButPermanentBlockedScenario() {
            DeletionBlocker dataRetentionBlocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.DATA_RETENTION_REQUIRED)
                    .reason("Data must be retained for compliance")
                    .build();

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(3L)
                    .canSoftDelete(true)
                    .canPermanentDelete(false)
                    .softDeleteBlockers(Collections.emptyList())
                    .permanentDeleteBlockers(List.of(dataRetentionBlocker))
                    .summary("Soft delete allowed, permanent blocked")
                    .build();

            assertThat(dto.isCanSoftDelete()).isTrue();
            assertThat(dto.isCanPermanentDelete()).isFalse();
            assertThat(dto.getSoftDeleteBlockers()).isEmpty();
            assertThat(dto.getPermanentDeleteBlockers()).hasSize(1);
        }

        @Test
        @DisplayName("should handle multiple blockers scenario")
        void shouldHandleMultipleBlockersScenario() {
            DeletionBlocker blocker1 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .count(5)
                    .build();

            DeletionBlocker blocker2 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.OPEN_SUPPORT_TICKETS)
                    .count(2)
                    .build();

            DeletionBlocker blocker3 = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.PENDING_OPPORTUNITIES)
                    .count(3)
                    .build();

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(4L)
                    .canSoftDelete(false)
                    .canPermanentDelete(false)
                    .softDeleteBlockers(Arrays.asList(blocker1, blocker2, blocker3))
                    .permanentDeleteBlockers(Arrays.asList(blocker1, blocker2, blocker3))
                    .build();

            assertThat(dto.getSoftDeleteBlockers()).hasSize(3);
            assertThat(dto.getPermanentDeleteBlockers()).hasSize(3);
        }

        @Test
        @DisplayName("should handle null blocker lists")
        void shouldHandleNullBlockerLists() {
            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(5L)
                    .canSoftDelete(true)
                    .canPermanentDelete(true)
                    .softDeleteBlockers(null)
                    .permanentDeleteBlockers(null)
                    .build();

            assertThat(dto.getSoftDeleteBlockers()).isNull();
            assertThat(dto.getPermanentDeleteBlockers()).isNull();
        }

        @Test
        @DisplayName("should implement equals correctly for equal objects")
        void shouldImplementEqualsCorrectlyForEqualObjects() {
            DeletionEligibilityDto dto1 = DeletionEligibilityDto.builder()
                    .userId(100L)
                    .firebaseUserId("fb-100")
                    .userEmail("equal@test.com")
                    .userType("INFLUENCER")
                    .canSoftDelete(true)
                    .canPermanentDelete(false)
                    .summary("Test")
                    .build();

            DeletionEligibilityDto dto2 = DeletionEligibilityDto.builder()
                    .userId(100L)
                    .firebaseUserId("fb-100")
                    .userEmail("equal@test.com")
                    .userType("INFLUENCER")
                    .canSoftDelete(true)
                    .canPermanentDelete(false)
                    .summary("Test")
                    .build();

            assertThat(dto1).isEqualTo(dto2);
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }

        @Test
        @DisplayName("should implement equals correctly for different objects")
        void shouldImplementEqualsCorrectlyForDifferentObjects() {
            DeletionEligibilityDto dto1 = DeletionEligibilityDto.builder()
                    .userId(100L)
                    .userEmail("one@test.com")
                    .build();

            DeletionEligibilityDto dto2 = DeletionEligibilityDto.builder()
                    .userId(200L)
                    .userEmail("two@test.com")
                    .build();

            assertThat(dto1).isNotEqualTo(dto2);
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(777L)
                    .firebaseUserId("fb-777")
                    .userEmail("tostring@test.com")
                    .userType("COMPANY")
                    .canSoftDelete(true)
                    .canPermanentDelete(false)
                    .summary("ToString test")
                    .build();

            String toString = dto.toString();

            assertThat(toString).contains("DeletionEligibilityDto");
            assertThat(toString).contains("userId=777");
            assertThat(toString).contains("fb-777");
            assertThat(toString).contains("tostring@test.com");
            assertThat(toString).contains("COMPANY");
            assertThat(toString).contains("canSoftDelete=true");
            assertThat(toString).contains("canPermanentDelete=false");
        }

        @Test
        @DisplayName("should handle null userId")
        void shouldHandleNullUserId() {
            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(null)
                    .firebaseUserId("fb-only")
                    .build();

            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getFirebaseUserId()).isEqualTo("fb-only");
        }

        @Test
        @DisplayName("should handle all user types")
        void shouldHandleAllUserTypes() {
            String[] userTypes = {"INFLUENCER", "COMPANY", "ADMIN", "SUPPORT"};

            for (String userType : userTypes) {
                DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                        .userId(1L)
                        .userType(userType)
                        .build();

                assertThat(dto.getUserType()).isEqualTo(userType);
            }
        }

        @Test
        @DisplayName("should handle special characters in email")
        void shouldHandleSpecialCharactersInEmail() {
            String specialEmail = "user+tag@sub.domain.example.com";

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(1L)
                    .userEmail(specialEmail)
                    .build();

            assertThat(dto.getUserEmail()).isEqualTo(specialEmail);
        }

        @Test
        @DisplayName("should handle very long summary")
        void shouldHandleVeryLongSummary() {
            String longSummary = "S".repeat(5000);

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(1L)
                    .summary(longSummary)
                    .build();

            assertThat(dto.getSummary()).hasSize(5000);
        }
    }
}
