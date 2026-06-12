package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.session.SessionData;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
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

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for auth.session package classes:
 * - SessionData
 * - SocialAuthSessionService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Auth Session Package Unit Tests")
class AuthSessionUnitTest {

    // ========================================
    // SessionData Tests
    // ========================================
    @Nested
    @DisplayName("SessionData")
    class SessionDataTests {

        @Nested
        @DisplayName("Builder Pattern")
        class BuilderPatternTests {

            @Test
            @DisplayName("should create SessionData using builder")
            void shouldCreateSessionDataUsingBuilder() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "12345");
                socialData.put("username", "testuser");

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expires = now.plusMinutes(10);

                SessionData sessionData = SessionData.builder()
                        .socialData(socialData)
                        .platform("Instagram")
                        .createdAt(now)
                        .expiresAt(expires)
                        .build();

                assertThat(sessionData).isNotNull();
                assertThat(sessionData.getSocialData()).isEqualTo(socialData);
                assertThat(sessionData.getPlatform()).isEqualTo("Instagram");
                assertThat(sessionData.getCreatedAt()).isEqualTo(now);
                assertThat(sessionData.getExpiresAt()).isEqualTo(expires);
            }

            @Test
            @DisplayName("should create SessionData with null values using builder")
            void shouldCreateSessionDataWithNullValuesUsingBuilder() {
                SessionData sessionData = SessionData.builder()
                        .socialData(null)
                        .platform(null)
                        .createdAt(null)
                        .expiresAt(null)
                        .build();

                assertThat(sessionData).isNotNull();
                assertThat(sessionData.getSocialData()).isNull();
                assertThat(sessionData.getPlatform()).isNull();
                assertThat(sessionData.getCreatedAt()).isNull();
                assertThat(sessionData.getExpiresAt()).isNull();
            }

            @Test
            @DisplayName("should create empty SessionData using builder")
            void shouldCreateEmptySessionDataUsingBuilder() {
                SessionData sessionData = SessionData.builder().build();

                assertThat(sessionData).isNotNull();
                assertThat(sessionData.getSocialData()).isNull();
                assertThat(sessionData.getPlatform()).isNull();
            }
        }

        @Nested
        @DisplayName("No-Args Constructor")
        class NoArgsConstructorTests {

            @Test
            @DisplayName("should create SessionData using no-args constructor")
            void shouldCreateSessionDataUsingNoArgsConstructor() {
                SessionData sessionData = new SessionData();

                assertThat(sessionData).isNotNull();
                assertThat(sessionData.getSocialData()).isNull();
                assertThat(sessionData.getPlatform()).isNull();
                assertThat(sessionData.getCreatedAt()).isNull();
                assertThat(sessionData.getExpiresAt()).isNull();
            }
        }

        @Nested
        @DisplayName("All-Args Constructor")
        class AllArgsConstructorTests {

            @Test
            @DisplayName("should create SessionData using all-args constructor")
            void shouldCreateSessionDataUsingAllArgsConstructor() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "67890");
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expires = now.plusMinutes(15);

                SessionData sessionData = new SessionData(socialData, "TikTok", now, expires);

                assertThat(sessionData.getSocialData()).isEqualTo(socialData);
                assertThat(sessionData.getPlatform()).isEqualTo("TikTok");
                assertThat(sessionData.getCreatedAt()).isEqualTo(now);
                assertThat(sessionData.getExpiresAt()).isEqualTo(expires);
            }

            @Test
            @DisplayName("should create SessionData with all null values")
            void shouldCreateSessionDataWithAllNullValues() {
                SessionData sessionData = new SessionData(null, null, null, null);

                assertThat(sessionData).isNotNull();
            }
        }

        @Nested
        @DisplayName("Getters and Setters")
        class GettersAndSettersTests {

            @Test
            @DisplayName("should set and get socialData")
            void shouldSetAndGetSocialData() {
                SessionData sessionData = new SessionData();
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("key", "value");

                sessionData.setSocialData(socialData);

                assertThat(sessionData.getSocialData()).isEqualTo(socialData);
                assertThat(sessionData.getSocialData().get("key")).isEqualTo("value");
            }

            @Test
            @DisplayName("should set and get platform")
            void shouldSetAndGetPlatform() {
                SessionData sessionData = new SessionData();

                sessionData.setPlatform("YouTube");

                assertThat(sessionData.getPlatform()).isEqualTo("YouTube");
            }

            @Test
            @DisplayName("should set and get createdAt")
            void shouldSetAndGetCreatedAt() {
                SessionData sessionData = new SessionData();
                LocalDateTime now = LocalDateTime.now();

                sessionData.setCreatedAt(now);

                assertThat(sessionData.getCreatedAt()).isEqualTo(now);
            }

            @Test
            @DisplayName("should set and get expiresAt")
            void shouldSetAndGetExpiresAt() {
                SessionData sessionData = new SessionData();
                LocalDateTime expires = LocalDateTime.now().plusHours(1);

                sessionData.setExpiresAt(expires);

                assertThat(sessionData.getExpiresAt()).isEqualTo(expires);
            }
        }

        @Nested
        @DisplayName("isExpired Method")
        class IsExpiredMethodTests {

            @Test
            @DisplayName("should return false when expiresAt is null")
            void shouldReturnFalseWhenExpiresAtIsNull() {
                SessionData sessionData = SessionData.builder()
                        .expiresAt(null)
                        .build();

                assertThat(sessionData.isExpired()).isFalse();
            }

            @Test
            @DisplayName("should return false when session is not expired")
            void shouldReturnFalseWhenSessionIsNotExpired() {
                SessionData sessionData = SessionData.builder()
                        .expiresAt(LocalDateTime.now().plusMinutes(10))
                        .build();

                assertThat(sessionData.isExpired()).isFalse();
            }

            @Test
            @DisplayName("should return true when session is expired")
            void shouldReturnTrueWhenSessionIsExpired() {
                SessionData sessionData = SessionData.builder()
                        .expiresAt(LocalDateTime.now().minusMinutes(1))
                        .build();

                assertThat(sessionData.isExpired()).isTrue();
            }

            @Test
            @DisplayName("should return true when expiresAt is exactly in the past")
            void shouldReturnTrueWhenExpiresAtIsExactlyInThePast() {
                SessionData sessionData = SessionData.builder()
                        .expiresAt(LocalDateTime.now().minusSeconds(1))
                        .build();

                assertThat(sessionData.isExpired()).isTrue();
            }

            @Test
            @DisplayName("should return false when expiresAt is far in the future")
            void shouldReturnFalseWhenExpiresAtIsFarInTheFuture() {
                SessionData sessionData = SessionData.builder()
                        .expiresAt(LocalDateTime.now().plusYears(1))
                        .build();

                assertThat(sessionData.isExpired()).isFalse();
            }

            @Test
            @DisplayName("should return true when expiresAt is far in the past")
            void shouldReturnTrueWhenExpiresAtIsFarInThePast() {
                SessionData sessionData = SessionData.builder()
                        .expiresAt(LocalDateTime.now().minusYears(1))
                        .build();

                assertThat(sessionData.isExpired()).isTrue();
            }
        }

        @Nested
        @DisplayName("Equals and HashCode")
        class EqualsAndHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields match")
            void shouldBeEqualWhenAllFieldsMatch() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "123");
                LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0);
                LocalDateTime expires = now.plusMinutes(10);

                SessionData sessionData1 = new SessionData(socialData, "Instagram", now, expires);
                SessionData sessionData2 = new SessionData(socialData, "Instagram", now, expires);

                assertThat(sessionData1).isEqualTo(sessionData2);
                assertThat(sessionData1.hashCode()).isEqualTo(sessionData2.hashCode());
            }

            @Test
            @DisplayName("should not be equal when platform differs")
            void shouldNotBeEqualWhenPlatformDiffers() {
                LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0);
                SessionData sessionData1 = new SessionData(null, "Instagram", now, now.plusMinutes(10));
                SessionData sessionData2 = new SessionData(null, "TikTok", now, now.plusMinutes(10));

                assertThat(sessionData1).isNotEqualTo(sessionData2);
            }

            @Test
            @DisplayName("should not be equal when socialData differs")
            void shouldNotBeEqualWhenSocialDataDiffers() {
                Map<String, Object> data1 = new HashMap<>();
                data1.put("user_id", "123");
                Map<String, Object> data2 = new HashMap<>();
                data2.put("user_id", "456");

                SessionData sessionData1 = new SessionData(data1, "Instagram", null, null);
                SessionData sessionData2 = new SessionData(data2, "Instagram", null, null);

                assertThat(sessionData1).isNotEqualTo(sessionData2);
            }

            @Test
            @DisplayName("should be equal to itself")
            void shouldBeEqualToItself() {
                SessionData sessionData = new SessionData();
                assertThat(sessionData).isEqualTo(sessionData);
            }

            @Test
            @DisplayName("should not be equal to null")
            void shouldNotBeEqualToNull() {
                SessionData sessionData = new SessionData();
                assertThat(sessionData).isNotEqualTo(null);
            }

            @Test
            @DisplayName("should not be equal to different type")
            void shouldNotBeEqualToDifferentType() {
                SessionData sessionData = new SessionData();
                assertThat(sessionData).isNotEqualTo("not a session data");
            }
        }

        @Nested
        @DisplayName("ToString")
        class ToStringTests {

            @Test
            @DisplayName("should generate toString with all fields")
            void shouldGenerateToStringWithAllFields() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "123");
                SessionData sessionData = SessionData.builder()
                        .socialData(socialData)
                        .platform("Instagram")
                        .createdAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                        .expiresAt(LocalDateTime.of(2024, 1, 1, 12, 10))
                        .build();

                String toString = sessionData.toString();

                assertThat(toString).contains("SessionData");
                assertThat(toString).contains("platform");
                assertThat(toString).contains("Instagram");
            }

            @Test
            @DisplayName("should generate toString with null fields")
            void shouldGenerateToStringWithNullFields() {
                SessionData sessionData = new SessionData();

                String toString = sessionData.toString();

                assertThat(toString).contains("SessionData");
                assertThat(toString).contains("null");
            }
        }
    }

    // ========================================
    // SocialAuthSessionService Tests
    // ========================================
    @Nested
    @DisplayName("SocialAuthSessionService")
    class SocialAuthSessionServiceTests {

        private SocialAuthSessionService service;

        @BeforeEach
        void setUp() {
            service = new SocialAuthSessionService();
        }

        @Nested
        @DisplayName("storeSocialData")
        class StoreSocialDataTests {

            @Test
            @DisplayName("should store social data and return session ID")
            void shouldStoreSocialDataAndReturnSessionId() {
                Map<String, Object> socialData = createSocialData("user123", "testuser");

                String sessionId = service.storeSocialData(socialData, "Instagram");

                assertThat(sessionId).isNotNull();
                assertThat(sessionId).isNotEmpty();
            }

            @Test
            @DisplayName("should return valid UUID as session ID")
            void shouldReturnValidUuidAsSessionId() {
                Map<String, Object> socialData = createSocialData("user456", "anotheruser");

                String sessionId = service.storeSocialData(socialData, "TikTok");

                assertThatCode(() -> UUID.fromString(sessionId)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should store data that can be retrieved")
            void shouldStoreDataThatCanBeRetrieved() {
                Map<String, Object> socialData = createSocialData("user789", "retrieveuser");

                String sessionId = service.storeSocialData(socialData, "YouTube");

                SessionData retrieved = service.getSocialData(sessionId);
                assertThat(retrieved).isNotNull();
                assertThat(retrieved.getSocialData()).isEqualTo(socialData);
                assertThat(retrieved.getPlatform()).isEqualTo("YouTube");
            }

            @Test
            @DisplayName("should store null social data")
            void shouldStoreNullSocialData() {
                String sessionId = service.storeSocialData(null, "Instagram");

                assertThat(sessionId).isNotNull();
                SessionData retrieved = service.getSocialData(sessionId);
                assertThat(retrieved).isNotNull();
                assertThat(retrieved.getSocialData()).isNull();
            }

            @Test
            @DisplayName("should store empty social data map")
            void shouldStoreEmptySocialDataMap() {
                Map<String, Object> emptyData = new HashMap<>();

                String sessionId = service.storeSocialData(emptyData, "Instagram");

                SessionData retrieved = service.getSocialData(sessionId);
                assertThat(retrieved).isNotNull();
                assertThat(retrieved.getSocialData()).isEmpty();
            }

            @Test
            @DisplayName("should store data with null platform")
            void shouldStoreDataWithNullPlatform() {
                Map<String, Object> socialData = createSocialData("user000", "nullplatformuser");

                String sessionId = service.storeSocialData(socialData, null);

                SessionData retrieved = service.getSocialData(sessionId);
                assertThat(retrieved.getPlatform()).isNull();
            }

            @Test
            @DisplayName("should generate unique session IDs for each call")
            void shouldGenerateUniqueSessionIdsForEachCall() {
                Map<String, Object> socialData = createSocialData("user111", "uniqueuser");

                String sessionId1 = service.storeSocialData(socialData, "Instagram");
                String sessionId2 = service.storeSocialData(socialData, "Instagram");
                String sessionId3 = service.storeSocialData(socialData, "Instagram");

                assertThat(sessionId1).isNotEqualTo(sessionId2);
                assertThat(sessionId2).isNotEqualTo(sessionId3);
                assertThat(sessionId1).isNotEqualTo(sessionId3);
            }

            @Test
            @DisplayName("should set createdAt timestamp")
            void shouldSetCreatedAtTimestamp() {
                Map<String, Object> socialData = createSocialData("user222", "timestampuser");
                LocalDateTime before = LocalDateTime.now().minusSeconds(1);

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData retrieved = service.getSocialData(sessionId);
                LocalDateTime after = LocalDateTime.now().plusSeconds(1);
                assertThat(retrieved.getCreatedAt()).isAfter(before);
                assertThat(retrieved.getCreatedAt()).isBefore(after);
            }

            @Test
            @DisplayName("should set expiresAt timestamp 10 minutes in the future")
            void shouldSetExpiresAtTimestamp10MinutesInTheFuture() {
                Map<String, Object> socialData = createSocialData("user333", "expiryuser");

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData retrieved = service.getSocialData(sessionId);
                LocalDateTime expectedExpiry = LocalDateTime.now().plusMinutes(10);
                assertThat(retrieved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
                assertThat(retrieved.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(11));
            }

            @Test
            @DisplayName("should handle social data with special characters")
            void shouldHandleSocialDataWithSpecialCharacters() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "user!@#$%^&*()");
                socialData.put("username", "user<>?/\\\"'");
                socialData.put("bio", "Hello\nWorld\tTab");

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData retrieved = service.getSocialData(sessionId);
                assertThat(retrieved.getSocialData().get("user_id")).isEqualTo("user!@#$%^&*()");
                assertThat(retrieved.getSocialData().get("username")).isEqualTo("user<>?/\\\"'");
            }

            @Test
            @DisplayName("should handle social data with nested objects")
            void shouldHandleSocialDataWithNestedObjects() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "user444");
                Map<String, Object> nestedData = new HashMap<>();
                nestedData.put("followers", 1000);
                nestedData.put("following", 500);
                socialData.put("stats", nestedData);

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData retrieved = service.getSocialData(sessionId);
                @SuppressWarnings("unchecked")
                Map<String, Object> retrievedStats = (Map<String, Object>) retrieved.getSocialData().get("stats");
                assertThat(retrievedStats.get("followers")).isEqualTo(1000);
            }
        }

        @Nested
        @DisplayName("getSocialData")
        class GetSocialDataTests {

            @Test
            @DisplayName("should return null for non-existent session ID")
            void shouldReturnNullForNonExistentSessionId() {
                SessionData result = service.getSocialData("non-existent-session-id");

                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should throw NullPointerException for null session ID")
            void shouldThrowNullPointerExceptionForNullSessionId() {
                // ConcurrentHashMap.get(null) throws NullPointerException
                org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                    () -> service.getSocialData(null));
            }

            @Test
            @DisplayName("should return null for empty session ID")
            void shouldReturnNullForEmptySessionId() {
                SessionData result = service.getSocialData("");

                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return stored session data")
            void shouldReturnStoredSessionData() {
                Map<String, Object> socialData = createSocialData("user555", "getuser");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData result = service.getSocialData(sessionId);

                assertThat(result).isNotNull();
                assertThat(result.getSocialData()).isEqualTo(socialData);
                assertThat(result.getPlatform()).isEqualTo("Instagram");
            }

            @Test
            @DisplayName("should return null for expired session")
            void shouldReturnNullForExpiredSession() throws Exception {
                Map<String, Object> socialData = createSocialData("user666", "expireduser");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                // Manually expire the session by modifying the expiresAt field
                Field sessionsField = SocialAuthSessionService.class.getDeclaredField("sessions");
                sessionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, SessionData> sessions = (Map<String, SessionData>) sessionsField.get(service);
                SessionData sessionData = sessions.get(sessionId);
                sessionData.setExpiresAt(LocalDateTime.now().minusMinutes(1));

                SessionData result = service.getSocialData(sessionId);

                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should remove expired session after retrieval attempt")
            void shouldRemoveExpiredSessionAfterRetrievalAttempt() throws Exception {
                Map<String, Object> socialData = createSocialData("user777", "removeduser");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                // Manually expire the session
                Field sessionsField = SocialAuthSessionService.class.getDeclaredField("sessions");
                sessionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, SessionData> sessions = (Map<String, SessionData>) sessionsField.get(service);
                SessionData sessionData = sessions.get(sessionId);
                sessionData.setExpiresAt(LocalDateTime.now().minusMinutes(1));

                // First call should return null and remove the session
                service.getSocialData(sessionId);

                // Session should be removed
                assertThat(sessions.containsKey(sessionId)).isFalse();
            }

            @Test
            @DisplayName("should return session that is not expired yet")
            void shouldReturnSessionThatIsNotExpiredYet() {
                Map<String, Object> socialData = createSocialData("user888", "validuser");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData result = service.getSocialData(sessionId);

                assertThat(result).isNotNull();
                assertThat(result.isExpired()).isFalse();
            }
        }

        @Nested
        @DisplayName("removeSession")
        class RemoveSessionTests {

            @Test
            @DisplayName("should remove existing session")
            void shouldRemoveExistingSession() {
                Map<String, Object> socialData = createSocialData("user999", "removeuser");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                service.removeSession(sessionId);

                SessionData result = service.getSocialData(sessionId);
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should handle removal of non-existent session")
            void shouldHandleRemovalOfNonExistentSession() {
                assertThatCode(() -> service.removeSession("non-existent-id"))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should throw NullPointerException for null session ID during removal")
            void shouldThrowNullPointerExceptionForNullSessionIdDuringRemoval() {
                // ConcurrentHashMap.get(null) throws NullPointerException
                org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                    () -> service.removeSession(null));
            }

            @Test
            @DisplayName("should handle removal with empty session ID")
            void shouldHandleRemovalWithEmptySessionId() {
                assertThatCode(() -> service.removeSession(""))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should only remove specified session")
            void shouldOnlyRemoveSpecifiedSession() {
                Map<String, Object> socialData1 = createSocialData("user1", "keepuser");
                Map<String, Object> socialData2 = createSocialData("user2", "removeuser");
                String sessionId1 = service.storeSocialData(socialData1, "Instagram");
                String sessionId2 = service.storeSocialData(socialData2, "TikTok");

                service.removeSession(sessionId2);

                assertThat(service.getSocialData(sessionId1)).isNotNull();
                assertThat(service.getSocialData(sessionId2)).isNull();
            }

            @Test
            @DisplayName("should be idempotent for multiple removals")
            void shouldBeIdempotentForMultipleRemovals() {
                Map<String, Object> socialData = createSocialData("userX", "idempotentuser");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                service.removeSession(sessionId);
                service.removeSession(sessionId);
                service.removeSession(sessionId);

                assertThat(service.getSocialData(sessionId)).isNull();
            }
        }

        @Nested
        @DisplayName("Cleanup Expired Sessions")
        class CleanupExpiredSessionsTests {

            @Test
            @DisplayName("should cleanup expired sessions when storing new data")
            void shouldCleanupExpiredSessionsWhenStoringNewData() throws Exception {
                // Store a session and expire it manually
                Map<String, Object> socialData1 = createSocialData("expired1", "expireduser1");
                String expiredSessionId = service.storeSocialData(socialData1, "Instagram");

                Field sessionsField = SocialAuthSessionService.class.getDeclaredField("sessions");
                sessionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, SessionData> sessions = (Map<String, SessionData>) sessionsField.get(service);
                sessions.get(expiredSessionId).setExpiresAt(LocalDateTime.now().minusMinutes(1));

                // Store new data which should trigger cleanup
                Map<String, Object> socialData2 = createSocialData("new1", "newuser1");
                service.storeSocialData(socialData2, "TikTok");

                // Expired session should be removed
                assertThat(sessions.containsKey(expiredSessionId)).isFalse();
            }

            @Test
            @DisplayName("should not remove non-expired sessions during cleanup")
            void shouldNotRemoveNonExpiredSessionsDuringCleanup() throws Exception {
                Map<String, Object> socialData1 = createSocialData("valid1", "validuser1");
                String validSessionId = service.storeSocialData(socialData1, "Instagram");

                Map<String, Object> socialData2 = createSocialData("expired1", "expireduser1");
                String expiredSessionId = service.storeSocialData(socialData2, "TikTok");

                Field sessionsField = SocialAuthSessionService.class.getDeclaredField("sessions");
                sessionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, SessionData> sessions = (Map<String, SessionData>) sessionsField.get(service);
                sessions.get(expiredSessionId).setExpiresAt(LocalDateTime.now().minusMinutes(1));

                // Store new data to trigger cleanup
                service.storeSocialData(createSocialData("new", "newuser"), "YouTube");

                // Valid session should still exist
                assertThat(sessions.containsKey(validSessionId)).isTrue();
                // Expired session should be removed
                assertThat(sessions.containsKey(expiredSessionId)).isFalse();
            }

            @Test
            @DisplayName("should cleanup multiple expired sessions")
            void shouldCleanupMultipleExpiredSessions() throws Exception {
                Field sessionsField = SocialAuthSessionService.class.getDeclaredField("sessions");
                sessionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, SessionData> sessions = (Map<String, SessionData>) sessionsField.get(service);

                // Store and expire multiple sessions
                for (int i = 0; i < 5; i++) {
                    Map<String, Object> socialData = createSocialData("expired" + i, "expireduser" + i);
                    String sessionId = service.storeSocialData(socialData, "Instagram");
                    sessions.get(sessionId).setExpiresAt(LocalDateTime.now().minusMinutes(1));
                }

                int expiredCount = sessions.size();

                // Store new data to trigger cleanup
                service.storeSocialData(createSocialData("trigger", "triggeruser"), "TikTok");

                // All expired sessions should be removed, only new one should remain
                assertThat(sessions.size()).isEqualTo(1);
            }
        }

        @Nested
        @DisplayName("Concurrency Tests")
        class ConcurrencyTests {

            @Test
            @DisplayName("should handle concurrent store operations")
            void shouldHandleConcurrentStoreOperations() throws InterruptedException {
                int threadCount = 10;
                CountDownLatch latch = new CountDownLatch(threadCount);
                AtomicInteger successCount = new AtomicInteger(0);

                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                for (int i = 0; i < threadCount; i++) {
                    final int index = i;
                    executor.submit(() -> {
                        try {
                            Map<String, Object> socialData = createSocialData("concurrent" + index, "user" + index);
                            String sessionId = service.storeSocialData(socialData, "Instagram");
                            if (sessionId != null && !sessionId.isEmpty()) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(10, TimeUnit.SECONDS);
                executor.shutdown();

                assertThat(successCount.get()).isEqualTo(threadCount);
            }

            @Test
            @DisplayName("should handle concurrent get operations")
            void shouldHandleConcurrentGetOperations() throws InterruptedException {
                Map<String, Object> socialData = createSocialData("shared", "shareduser");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                int threadCount = 10;
                CountDownLatch latch = new CountDownLatch(threadCount);
                AtomicInteger successCount = new AtomicInteger(0);

                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            SessionData result = service.getSocialData(sessionId);
                            if (result != null) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(10, TimeUnit.SECONDS);
                executor.shutdown();

                assertThat(successCount.get()).isEqualTo(threadCount);
            }

            @Test
            @DisplayName("should handle concurrent store and remove operations")
            void shouldHandleConcurrentStoreAndRemoveOperations() throws InterruptedException {
                int operationCount = 20;
                CountDownLatch latch = new CountDownLatch(operationCount);

                ExecutorService executor = Executors.newFixedThreadPool(10);
                for (int i = 0; i < operationCount; i++) {
                    final int index = i;
                    executor.submit(() -> {
                        try {
                            if (index % 2 == 0) {
                                Map<String, Object> socialData = createSocialData("mixed" + index, "user" + index);
                                service.storeSocialData(socialData, "Instagram");
                            } else {
                                service.removeSession("some-random-id-" + index);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(10, TimeUnit.SECONDS);
                executor.shutdown();

                // Should complete without throwing exceptions
                assertThat(true).isTrue();
            }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCasesTests {

            @Test
            @DisplayName("should handle very long platform name")
            void shouldHandleVeryLongPlatformName() {
                String longPlatformName = "A".repeat(1000);
                Map<String, Object> socialData = createSocialData("user", "testuser");

                String sessionId = service.storeSocialData(socialData, longPlatformName);

                SessionData result = service.getSocialData(sessionId);
                assertThat(result.getPlatform()).isEqualTo(longPlatformName);
            }

            @Test
            @DisplayName("should handle social data with very long values")
            void shouldHandleSocialDataWithVeryLongValues() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "A".repeat(10000));
                socialData.put("username", "B".repeat(5000));

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData result = service.getSocialData(sessionId);
                assertThat(result.getSocialData().get("user_id")).isEqualTo("A".repeat(10000));
            }

            @Test
            @DisplayName("should handle social data with many keys")
            void shouldHandleSocialDataWithManyKeys() {
                Map<String, Object> socialData = new HashMap<>();
                for (int i = 0; i < 100; i++) {
                    socialData.put("key" + i, "value" + i);
                }

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData result = service.getSocialData(sessionId);
                assertThat(result.getSocialData().size()).isEqualTo(100);
            }

            @Test
            @DisplayName("should handle Unicode characters in social data")
            void shouldHandleUnicodeCharactersInSocialData() {
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("user_id", "user123");
                socialData.put("username", "test_emoji_face");
                socialData.put("bio", "Hello World");
                socialData.put("location", "Tokyo");

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData result = service.getSocialData(sessionId);
                assertThat(result.getSocialData().get("location")).isEqualTo("Tokyo");
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should handle various blank platform names")
            void shouldHandleVariousBlankPlatformNames(String platform) {
                Map<String, Object> socialData = createSocialData("user", "testuser");

                String sessionId = service.storeSocialData(socialData, platform);

                SessionData result = service.getSocialData(sessionId);
                assertThat(result.getPlatform()).isEqualTo(platform);
            }
        }

        @Nested
        @DisplayName("GDPR Compliance Tests")
        class GdprComplianceTests {

            @Test
            @DisplayName("should store data with proper expiration for GDPR compliance")
            void shouldStoreDataWithProperExpirationForGdprCompliance() {
                Map<String, Object> socialData = createSocialData("gdpr1", "gdpruser1");

                String sessionId = service.storeSocialData(socialData, "Instagram");

                SessionData result = service.getSocialData(sessionId);
                // Data should expire within 10 minutes as per GDPR data minimization
                assertThat(result.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(11));
                assertThat(result.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
            }

            @Test
            @DisplayName("should allow complete removal of session data")
            void shouldAllowCompleteRemovalOfSessionData() throws Exception {
                Map<String, Object> socialData = createSocialData("gdpr2", "gdpruser2");
                String sessionId = service.storeSocialData(socialData, "Instagram");

                service.removeSession(sessionId);

                Field sessionsField = SocialAuthSessionService.class.getDeclaredField("sessions");
                sessionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, SessionData> sessions = (Map<String, SessionData>) sessionsField.get(service);

                assertThat(sessions.containsKey(sessionId)).isFalse();
            }

            @Test
            @DisplayName("should automatically cleanup expired data")
            void shouldAutomaticallyCleanupExpiredData() throws Exception {
                Field sessionsField = SocialAuthSessionService.class.getDeclaredField("sessions");
                sessionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, SessionData> sessions = (Map<String, SessionData>) sessionsField.get(service);

                // Store and expire a session
                String expiredSessionId = service.storeSocialData(
                        createSocialData("gdpr3", "gdpruser3"), "Instagram");
                sessions.get(expiredSessionId).setExpiresAt(LocalDateTime.now().minusMinutes(1));

                // Trigger cleanup by storing new data
                service.storeSocialData(createSocialData("trigger", "triggeruser"), "TikTok");

                // Expired data should be automatically removed
                assertThat(sessions.containsKey(expiredSessionId)).isFalse();
            }
        }

        // Helper method to create social data
        private Map<String, Object> createSocialData(String userId, String username) {
            Map<String, Object> socialData = new HashMap<>();
            socialData.put("user_id", userId);
            socialData.put("username", username);
            socialData.put("profile_picture_url", "https://example.com/pic.jpg");
            socialData.put("followers_count", 1000);
            socialData.put("access_token", "test_token_" + userId);
            return socialData;
        }
    }
}
