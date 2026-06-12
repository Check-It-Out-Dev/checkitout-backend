package com.sm.instagram.platform.unit.service;

import com.google.cloud.Timestamp;
import com.sm.instagram.model.firestore.InstagramUserDocument;
import com.sm.instagram.model.firestore.InstagramUserDocument.InstagramUserData;
import com.sm.instagram.model.firestore.TotpSecretDocument;
import com.sm.instagram.model.firestore.TotpSecretDocument.BackupCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Firestore model classes.
 * Tests constructors, getters, setters, builders, equals/hashCode, and toString.
 */
@DisplayName("Firestore Models Unit Tests")
class FirestoreModelsUnitTest {

    // ==================== InstagramUserDocument Tests ====================

    @Nested
    @DisplayName("InstagramUserDocument")
    class InstagramUserDocumentTests {

        @Nested
        @DisplayName("Constructor Tests")
        class ConstructorTests {

            @Test
            @DisplayName("should create instance with no-args constructor")
            void shouldCreateInstanceWithNoArgsConstructor() {
                // When
                InstagramUserDocument document = new InstagramUserDocument();

                // Then
                assertThat(document).isNotNull();
                assertThat(document.getAccess_token()).isNull();
                assertThat(document.getFirebaseUserId()).isNull();
                assertThat(document.getFollowers_count()).isNull();
                assertThat(document.getLastUpdated()).isNull();
                assertThat(document.getPermissions()).isNull();
                assertThat(document.getProfile_picture_url()).isNull();
                assertThat(document.getProvider()).isNull();
                assertThat(document.getUser()).isNull();
                assertThat(document.getUser_id()).isNull();
                assertThat(document.getUsername()).isNull();
            }

            @Test
            @DisplayName("should create instance with all-args constructor")
            void shouldCreateInstanceWithAllArgsConstructor() {
                // Given
                String accessToken = "access-token-123";
                String firebaseUserId = "firebase-uid-456";
                Long followersCount = 10000L;
                Long lastUpdated = System.currentTimeMillis() / 1000;
                List<String> permissions = Arrays.asList("pages_read_engagement", "instagram_basic");
                String profilePictureUrl = "https://example.com/profile.jpg";
                String provider = "instagram";
                InstagramUserData userData = new InstagramUserData();
                String userId = "ig-user-789";
                String username = "test_user";

                // When
                InstagramUserDocument document = new InstagramUserDocument(
                        accessToken, firebaseUserId, followersCount, lastUpdated,
                        permissions, profilePictureUrl, provider, userData, userId, username
                );

                // Then
                assertThat(document.getAccess_token()).isEqualTo(accessToken);
                assertThat(document.getFirebaseUserId()).isEqualTo(firebaseUserId);
                assertThat(document.getFollowers_count()).isEqualTo(followersCount);
                assertThat(document.getLastUpdated()).isEqualTo(lastUpdated);
                assertThat(document.getPermissions()).isEqualTo(permissions);
                assertThat(document.getProfile_picture_url()).isEqualTo(profilePictureUrl);
                assertThat(document.getProvider()).isEqualTo(provider);
                assertThat(document.getUser()).isEqualTo(userData);
                assertThat(document.getUser_id()).isEqualTo(userId);
                assertThat(document.getUsername()).isEqualTo(username);
            }
        }

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should create instance using builder")
            void shouldCreateInstanceUsingBuilder() {
                // Given
                String accessToken = "encrypted-access-token";
                String firebaseUserId = "firebase-123";
                Long followersCount = 5000L;
                Long lastUpdated = 1704067200L;
                List<String> permissions = Arrays.asList("pages_show_list", "instagram_manage_insights");
                String profilePictureUrl = "https://cdn.example.com/avatar.png";
                String provider = "instagram_business";
                InstagramUserData userData = InstagramUserData.builder()
                        .account_type("BUSINESS")
                        .username("business_account")
                        .build();
                String userId = "12345678";
                String username = "business_account";

                // When
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .access_token(accessToken)
                        .firebaseUserId(firebaseUserId)
                        .followers_count(followersCount)
                        .lastUpdated(lastUpdated)
                        .permissions(permissions)
                        .profile_picture_url(profilePictureUrl)
                        .provider(provider)
                        .user(userData)
                        .user_id(userId)
                        .username(username)
                        .build();

                // Then
                assertThat(document.getAccess_token()).isEqualTo(accessToken);
                assertThat(document.getFirebaseUserId()).isEqualTo(firebaseUserId);
                assertThat(document.getFollowers_count()).isEqualTo(followersCount);
                assertThat(document.getLastUpdated()).isEqualTo(lastUpdated);
                assertThat(document.getPermissions()).isEqualTo(permissions);
                assertThat(document.getProfile_picture_url()).isEqualTo(profilePictureUrl);
                assertThat(document.getProvider()).isEqualTo(provider);
                assertThat(document.getUser()).isEqualTo(userData);
                assertThat(document.getUser_id()).isEqualTo(userId);
                assertThat(document.getUsername()).isEqualTo(username);
            }

            @Test
            @DisplayName("should create empty instance using builder")
            void shouldCreateEmptyInstanceUsingBuilder() {
                // When
                InstagramUserDocument document = InstagramUserDocument.builder().build();

                // Then
                assertThat(document).isNotNull();
                assertThat(document.getAccess_token()).isNull();
                assertThat(document.getFirebaseUserId()).isNull();
            }

            @Test
            @DisplayName("should allow partial builder usage")
            void shouldAllowPartialBuilderUsage() {
                // When
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .username("partial_user")
                        .provider("instagram")
                        .build();

                // Then
                assertThat(document.getUsername()).isEqualTo("partial_user");
                assertThat(document.getProvider()).isEqualTo("instagram");
                assertThat(document.getAccess_token()).isNull();
                assertThat(document.getFollowers_count()).isNull();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            @Test
            @DisplayName("should set and get access_token")
            void shouldSetAndGetAccessToken() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                String accessToken = "new-access-token";

                // When
                document.setAccess_token(accessToken);

                // Then
                assertThat(document.getAccess_token()).isEqualTo(accessToken);
            }

            @Test
            @DisplayName("should set and get firebaseUserId")
            void shouldSetAndGetFirebaseUserId() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                String firebaseUserId = "firebase-user-id";

                // When
                document.setFirebaseUserId(firebaseUserId);

                // Then
                assertThat(document.getFirebaseUserId()).isEqualTo(firebaseUserId);
            }

            @Test
            @DisplayName("should set and get followers_count")
            void shouldSetAndGetFollowersCount() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                Long followersCount = 25000L;

                // When
                document.setFollowers_count(followersCount);

                // Then
                assertThat(document.getFollowers_count()).isEqualTo(followersCount);
            }

            @Test
            @DisplayName("should set and get lastUpdated")
            void shouldSetAndGetLastUpdated() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                Long lastUpdated = 1704153600L;

                // When
                document.setLastUpdated(lastUpdated);

                // Then
                assertThat(document.getLastUpdated()).isEqualTo(lastUpdated);
            }

            @Test
            @DisplayName("should set and get permissions")
            void shouldSetAndGetPermissions() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                List<String> permissions = Arrays.asList("read", "write", "manage");

                // When
                document.setPermissions(permissions);

                // Then
                assertThat(document.getPermissions()).isEqualTo(permissions);
                assertThat(document.getPermissions()).hasSize(3);
            }

            @Test
            @DisplayName("should set and get profile_picture_url")
            void shouldSetAndGetProfilePictureUrl() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                String url = "https://instagram.com/profile/pic.jpg";

                // When
                document.setProfile_picture_url(url);

                // Then
                assertThat(document.getProfile_picture_url()).isEqualTo(url);
            }

            @Test
            @DisplayName("should set and get provider")
            void shouldSetAndGetProvider() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                String provider = "instagram";

                // When
                document.setProvider(provider);

                // Then
                assertThat(document.getProvider()).isEqualTo(provider);
            }

            @Test
            @DisplayName("should set and get user")
            void shouldSetAndGetUser() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                InstagramUserData userData = InstagramUserData.builder()
                        .username("nested_user")
                        .id("nested-123")
                        .build();

                // When
                document.setUser(userData);

                // Then
                assertThat(document.getUser()).isEqualTo(userData);
                assertThat(document.getUser().getUsername()).isEqualTo("nested_user");
            }

            @Test
            @DisplayName("should set and get user_id")
            void shouldSetAndGetUserId() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                String userId = "user-id-999";

                // When
                document.setUser_id(userId);

                // Then
                assertThat(document.getUser_id()).isEqualTo(userId);
            }

            @Test
            @DisplayName("should set and get username")
            void shouldSetAndGetUsername() {
                // Given
                InstagramUserDocument document = new InstagramUserDocument();
                String username = "insta_user";

                // When
                document.setUsername(username);

                // Then
                assertThat(document.getUsername()).isEqualTo(username);
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields are same")
            void shouldBeEqualWhenAllFieldsAreSame() {
                // Given
                InstagramUserDocument doc1 = InstagramUserDocument.builder()
                        .access_token("token")
                        .username("user1")
                        .user_id("123")
                        .build();

                InstagramUserDocument doc2 = InstagramUserDocument.builder()
                        .access_token("token")
                        .username("user1")
                        .user_id("123")
                        .build();

                // Then
                assertThat(doc1).isEqualTo(doc2);
                assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode());
            }

            @Test
            @DisplayName("should not be equal when fields differ")
            void shouldNotBeEqualWhenFieldsDiffer() {
                // Given
                InstagramUserDocument doc1 = InstagramUserDocument.builder()
                        .username("user1")
                        .build();

                InstagramUserDocument doc2 = InstagramUserDocument.builder()
                        .username("user2")
                        .build();

                // Then
                assertThat(doc1).isNotEqualTo(doc2);
            }

            @Test
            @DisplayName("should be equal to itself")
            void shouldBeEqualToItself() {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .username("test")
                        .build();

                // Then
                assertThat(document).isEqualTo(document);
            }

            @Test
            @DisplayName("should not be equal to null")
            void shouldNotBeEqualToNull() {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder().build();

                // Then
                assertThat(document).isNotEqualTo(null);
            }

            @Test
            @DisplayName("should not be equal to different type")
            void shouldNotBeEqualToDifferentType() {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder().build();

                // Then
                assertThat(document).isNotEqualTo("string");
            }
        }

        @Nested
        @DisplayName("ToString Tests")
        class ToStringTests {

            @Test
            @DisplayName("should include field values in toString")
            void shouldIncludeFieldValuesInToString() {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .username("test_user")
                        .user_id("12345")
                        .provider("instagram")
                        .build();

                // When
                String result = document.toString();

                // Then
                assertThat(result).contains("test_user");
                assertThat(result).contains("12345");
                assertThat(result).contains("instagram");
            }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCaseTests {

            @Test
            @DisplayName("should handle empty permissions list")
            void shouldHandleEmptyPermissionsList() {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .permissions(Collections.emptyList())
                        .build();

                // Then
                assertThat(document.getPermissions()).isEmpty();
            }

            @Test
            @DisplayName("should handle null permissions")
            void shouldHandleNullPermissions() {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .permissions(null)
                        .build();

                // Then
                assertThat(document.getPermissions()).isNull();
            }

            @Test
            @DisplayName("should handle zero followers count")
            void shouldHandleZeroFollowersCount() {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .followers_count(0L)
                        .build();

                // Then
                assertThat(document.getFollowers_count()).isZero();
            }

            @ParameterizedTest
            @ValueSource(longs = {0L, 1L, 1000000L, Long.MAX_VALUE})
            @DisplayName("should accept various followers count values")
            void shouldAcceptVariousFollowersCountValues(Long count) {
                // Given
                InstagramUserDocument document = InstagramUserDocument.builder()
                        .followers_count(count)
                        .build();

                // Then
                assertThat(document.getFollowers_count()).isEqualTo(count);
            }
        }
    }

    // ==================== InstagramUserData (Nested) Tests ====================

    @Nested
    @DisplayName("InstagramUserData (Nested)")
    class InstagramUserDataTests {

        @Nested
        @DisplayName("Constructor Tests")
        class ConstructorTests {

            @Test
            @DisplayName("should create instance with no-args constructor")
            void shouldCreateInstanceWithNoArgsConstructor() {
                // When
                InstagramUserData userData = new InstagramUserData();

                // Then
                assertThat(userData).isNotNull();
                assertThat(userData.getAccount_type()).isNull();
                assertThat(userData.getFollowers_count()).isNull();
                assertThat(userData.getId()).isNull();
                assertThat(userData.getMedia_count()).isNull();
                assertThat(userData.getUsername()).isNull();
            }

            @Test
            @DisplayName("should create instance with all-args constructor")
            void shouldCreateInstanceWithAllArgsConstructor() {
                // Given
                String accountType = "BUSINESS";
                Long followersCount = 50000L;
                String id = "ig-123456";
                Long mediaCount = 150L;
                String username = "business_user";

                // When
                InstagramUserData userData = new InstagramUserData(
                        accountType, followersCount, id, mediaCount, username
                );

                // Then
                assertThat(userData.getAccount_type()).isEqualTo(accountType);
                assertThat(userData.getFollowers_count()).isEqualTo(followersCount);
                assertThat(userData.getId()).isEqualTo(id);
                assertThat(userData.getMedia_count()).isEqualTo(mediaCount);
                assertThat(userData.getUsername()).isEqualTo(username);
            }
        }

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should create instance using builder")
            void shouldCreateInstanceUsingBuilder() {
                // When
                InstagramUserData userData = InstagramUserData.builder()
                        .account_type("PERSONAL")
                        .followers_count(1000L)
                        .id("user-id-abc")
                        .media_count(50L)
                        .username("personal_account")
                        .build();

                // Then
                assertThat(userData.getAccount_type()).isEqualTo("PERSONAL");
                assertThat(userData.getFollowers_count()).isEqualTo(1000L);
                assertThat(userData.getId()).isEqualTo("user-id-abc");
                assertThat(userData.getMedia_count()).isEqualTo(50L);
                assertThat(userData.getUsername()).isEqualTo("personal_account");
            }

            @Test
            @DisplayName("should allow partial builder usage")
            void shouldAllowPartialBuilderUsage() {
                // When
                InstagramUserData userData = InstagramUserData.builder()
                        .username("partial_user")
                        .build();

                // Then
                assertThat(userData.getUsername()).isEqualTo("partial_user");
                assertThat(userData.getAccount_type()).isNull();
                assertThat(userData.getFollowers_count()).isNull();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            @Test
            @DisplayName("should set and get account_type")
            void shouldSetAndGetAccountType() {
                // Given
                InstagramUserData userData = new InstagramUserData();

                // When
                userData.setAccount_type("CREATOR");

                // Then
                assertThat(userData.getAccount_type()).isEqualTo("CREATOR");
            }

            @Test
            @DisplayName("should set and get followers_count")
            void shouldSetAndGetFollowersCount() {
                // Given
                InstagramUserData userData = new InstagramUserData();

                // When
                userData.setFollowers_count(99999L);

                // Then
                assertThat(userData.getFollowers_count()).isEqualTo(99999L);
            }

            @Test
            @DisplayName("should set and get id")
            void shouldSetAndGetId() {
                // Given
                InstagramUserData userData = new InstagramUserData();

                // When
                userData.setId("instagram-id-xyz");

                // Then
                assertThat(userData.getId()).isEqualTo("instagram-id-xyz");
            }

            @Test
            @DisplayName("should set and get media_count")
            void shouldSetAndGetMediaCount() {
                // Given
                InstagramUserData userData = new InstagramUserData();

                // When
                userData.setMedia_count(500L);

                // Then
                assertThat(userData.getMedia_count()).isEqualTo(500L);
            }

            @Test
            @DisplayName("should set and get username")
            void shouldSetAndGetUsername() {
                // Given
                InstagramUserData userData = new InstagramUserData();

                // When
                userData.setUsername("my_username");

                // Then
                assertThat(userData.getUsername()).isEqualTo("my_username");
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields are same")
            void shouldBeEqualWhenAllFieldsAreSame() {
                // Given
                InstagramUserData data1 = InstagramUserData.builder()
                        .account_type("BUSINESS")
                        .username("same_user")
                        .id("123")
                        .build();

                InstagramUserData data2 = InstagramUserData.builder()
                        .account_type("BUSINESS")
                        .username("same_user")
                        .id("123")
                        .build();

                // Then
                assertThat(data1).isEqualTo(data2);
                assertThat(data1.hashCode()).isEqualTo(data2.hashCode());
            }

            @Test
            @DisplayName("should not be equal when fields differ")
            void shouldNotBeEqualWhenFieldsDiffer() {
                // Given
                InstagramUserData data1 = InstagramUserData.builder()
                        .username("user1")
                        .build();

                InstagramUserData data2 = InstagramUserData.builder()
                        .username("user2")
                        .build();

                // Then
                assertThat(data1).isNotEqualTo(data2);
            }
        }

        @Nested
        @DisplayName("Account Type Scenarios")
        class AccountTypeScenarios {

            @ParameterizedTest
            @ValueSource(strings = {"BUSINESS", "PERSONAL", "CREATOR", "MEDIA_CREATOR"})
            @DisplayName("should accept various account types")
            void shouldAcceptVariousAccountTypes(String accountType) {
                // Given
                InstagramUserData userData = InstagramUserData.builder()
                        .account_type(accountType)
                        .build();

                // Then
                assertThat(userData.getAccount_type()).isEqualTo(accountType);
            }
        }
    }

    // ==================== TotpSecretDocument Tests ====================

    @Nested
    @DisplayName("TotpSecretDocument")
    class TotpSecretDocumentTests {

        @Nested
        @DisplayName("Constructor Tests")
        class ConstructorTests {

            @Test
            @DisplayName("should create instance with no-args constructor")
            void shouldCreateInstanceWithNoArgsConstructor() {
                // When
                TotpSecretDocument document = new TotpSecretDocument();

                // Then
                assertThat(document).isNotNull();
                assertThat(document.getEncryptedSecret()).isNull();
                assertThat(document.getEnabled()).isNull();
                assertThat(document.getSetupAt()).isNull();
                assertThat(document.getVerifiedAt()).isNull();
                assertThat(document.getDisabledAt()).isNull();
                assertThat(document.getLastUsedAt()).isNull();
                assertThat(document.getBackupCodes()).isNull();
            }

            @Test
            @DisplayName("should create instance with all-args constructor")
            void shouldCreateInstanceWithAllArgsConstructor() {
                // Given
                String encryptedSecret = "encrypted-totp-secret";
                Boolean enabled = true;
                Timestamp setupAt = Timestamp.now();
                Timestamp verifiedAt = Timestamp.now();
                Timestamp disabledAt = null;
                Timestamp lastUsedAt = Timestamp.now();
                BackupCodes backupCodes = new BackupCodes();

                // When
                TotpSecretDocument document = new TotpSecretDocument(
                        encryptedSecret, enabled, setupAt, verifiedAt, disabledAt, lastUsedAt, backupCodes
                );

                // Then
                assertThat(document.getEncryptedSecret()).isEqualTo(encryptedSecret);
                assertThat(document.getEnabled()).isTrue();
                assertThat(document.getSetupAt()).isEqualTo(setupAt);
                assertThat(document.getVerifiedAt()).isEqualTo(verifiedAt);
                assertThat(document.getDisabledAt()).isNull();
                assertThat(document.getLastUsedAt()).isEqualTo(lastUsedAt);
                assertThat(document.getBackupCodes()).isEqualTo(backupCodes);
            }
        }

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should create instance using builder")
            void shouldCreateInstanceUsingBuilder() {
                // Given
                Timestamp now = Timestamp.now();
                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("encrypted-backup-codes")
                        .generatedAt(now)
                        .build();

                // When
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("secret-123")
                        .enabled(true)
                        .setupAt(now)
                        .verifiedAt(now)
                        .lastUsedAt(now)
                        .backupCodes(backupCodes)
                        .build();

                // Then
                assertThat(document.getEncryptedSecret()).isEqualTo("secret-123");
                assertThat(document.getEnabled()).isTrue();
                assertThat(document.getSetupAt()).isEqualTo(now);
                assertThat(document.getVerifiedAt()).isEqualTo(now);
                assertThat(document.getLastUsedAt()).isEqualTo(now);
                assertThat(document.getBackupCodes()).isEqualTo(backupCodes);
            }

            @Test
            @DisplayName("should create empty instance using builder")
            void shouldCreateEmptyInstanceUsingBuilder() {
                // When
                TotpSecretDocument document = TotpSecretDocument.builder().build();

                // Then
                assertThat(document).isNotNull();
                assertThat(document.getEncryptedSecret()).isNull();
                assertThat(document.getEnabled()).isNull();
            }

            @Test
            @DisplayName("should allow partial builder usage")
            void shouldAllowPartialBuilderUsage() {
                // When
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .enabled(false)
                        .build();

                // Then
                assertThat(document.getEnabled()).isFalse();
                assertThat(document.getEncryptedSecret()).isNull();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            @Test
            @DisplayName("should set and get encryptedSecret")
            void shouldSetAndGetEncryptedSecret() {
                // Given
                TotpSecretDocument document = new TotpSecretDocument();
                String secret = "encrypted-secret-value";

                // When
                document.setEncryptedSecret(secret);

                // Then
                assertThat(document.getEncryptedSecret()).isEqualTo(secret);
            }

            @Test
            @DisplayName("should set and get enabled")
            void shouldSetAndGetEnabled() {
                // Given
                TotpSecretDocument document = new TotpSecretDocument();

                // When
                document.setEnabled(true);

                // Then
                assertThat(document.getEnabled()).isTrue();
            }

            @Test
            @DisplayName("should set and get setupAt")
            void shouldSetAndGetSetupAt() {
                // Given
                TotpSecretDocument document = new TotpSecretDocument();
                Timestamp setupAt = Timestamp.now();

                // When
                document.setSetupAt(setupAt);

                // Then
                assertThat(document.getSetupAt()).isEqualTo(setupAt);
            }

            @Test
            @DisplayName("should set and get verifiedAt")
            void shouldSetAndGetVerifiedAt() {
                // Given
                TotpSecretDocument document = new TotpSecretDocument();
                Timestamp verifiedAt = Timestamp.now();

                // When
                document.setVerifiedAt(verifiedAt);

                // Then
                assertThat(document.getVerifiedAt()).isEqualTo(verifiedAt);
            }

            @Test
            @DisplayName("should set and get disabledAt")
            void shouldSetAndGetDisabledAt() {
                // Given
                TotpSecretDocument document = new TotpSecretDocument();
                Timestamp disabledAt = Timestamp.now();

                // When
                document.setDisabledAt(disabledAt);

                // Then
                assertThat(document.getDisabledAt()).isEqualTo(disabledAt);
            }

            @Test
            @DisplayName("should set and get lastUsedAt")
            void shouldSetAndGetLastUsedAt() {
                // Given
                TotpSecretDocument document = new TotpSecretDocument();
                Timestamp lastUsedAt = Timestamp.now();

                // When
                document.setLastUsedAt(lastUsedAt);

                // Then
                assertThat(document.getLastUsedAt()).isEqualTo(lastUsedAt);
            }

            @Test
            @DisplayName("should set and get backupCodes")
            void shouldSetAndGetBackupCodes() {
                // Given
                TotpSecretDocument document = new TotpSecretDocument();
                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("codes-xyz")
                        .build();

                // When
                document.setBackupCodes(backupCodes);

                // Then
                assertThat(document.getBackupCodes()).isEqualTo(backupCodes);
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields are same")
            void shouldBeEqualWhenAllFieldsAreSame() {
                // Given
                TotpSecretDocument doc1 = TotpSecretDocument.builder()
                        .encryptedSecret("secret")
                        .enabled(true)
                        .build();

                TotpSecretDocument doc2 = TotpSecretDocument.builder()
                        .encryptedSecret("secret")
                        .enabled(true)
                        .build();

                // Then
                assertThat(doc1).isEqualTo(doc2);
                assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode());
            }

            @Test
            @DisplayName("should not be equal when fields differ")
            void shouldNotBeEqualWhenFieldsDiffer() {
                // Given
                TotpSecretDocument doc1 = TotpSecretDocument.builder()
                        .enabled(true)
                        .build();

                TotpSecretDocument doc2 = TotpSecretDocument.builder()
                        .enabled(false)
                        .build();

                // Then
                assertThat(doc1).isNotEqualTo(doc2);
            }

            @Test
            @DisplayName("should be equal to itself")
            void shouldBeEqualToItself() {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("test")
                        .build();

                // Then
                assertThat(document).isEqualTo(document);
            }

            @Test
            @DisplayName("should not be equal to null")
            void shouldNotBeEqualToNull() {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder().build();

                // Then
                assertThat(document).isNotEqualTo(null);
            }
        }

        @Nested
        @DisplayName("ToString Tests")
        class ToStringTests {

            @Test
            @DisplayName("should include field values in toString")
            void shouldIncludeFieldValuesInToString() {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("secret-value")
                        .enabled(true)
                        .build();

                // When
                String result = document.toString();

                // Then
                assertThat(result).contains("secret-value");
                assertThat(result).contains("true");
            }
        }

        @Nested
        @DisplayName("State Transition Scenarios")
        class StateTransitionScenarios {

            @Test
            @DisplayName("should represent setup state (enabled null, verified null)")
            void shouldRepresentSetupState() {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("pending-secret")
                        .setupAt(Timestamp.now())
                        .build();

                // Then
                assertThat(document.getEnabled()).isNull();
                assertThat(document.getVerifiedAt()).isNull();
                assertThat(document.getSetupAt()).isNotNull();
            }

            @Test
            @DisplayName("should represent enabled and verified state")
            void shouldRepresentEnabledAndVerifiedState() {
                // Given
                Timestamp now = Timestamp.now();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("active-secret")
                        .enabled(true)
                        .setupAt(now)
                        .verifiedAt(now)
                        .build();

                // Then
                assertThat(document.getEnabled()).isTrue();
                assertThat(document.getVerifiedAt()).isNotNull();
                assertThat(document.getDisabledAt()).isNull();
            }

            @Test
            @DisplayName("should represent disabled state")
            void shouldRepresentDisabledState() {
                // Given
                Timestamp now = Timestamp.now();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("disabled-secret")
                        .enabled(false)
                        .disabledAt(now)
                        .build();

                // Then
                assertThat(document.getEnabled()).isFalse();
                assertThat(document.getDisabledAt()).isNotNull();
            }
        }
    }

    // ==================== BackupCodes (Nested) Tests ====================

    @Nested
    @DisplayName("BackupCodes (Nested)")
    class BackupCodesTests {

        @Nested
        @DisplayName("Constructor Tests")
        class ConstructorTests {

            @Test
            @DisplayName("should create instance with no-args constructor")
            void shouldCreateInstanceWithNoArgsConstructor() {
                // When
                BackupCodes backupCodes = new BackupCodes();

                // Then
                assertThat(backupCodes).isNotNull();
                assertThat(backupCodes.getEncryptedCodes()).isNull();
                assertThat(backupCodes.getUsedCodes()).isNotNull().isEmpty();
                assertThat(backupCodes.getGeneratedAt()).isNull();
                assertThat(backupCodes.getLastUsedAt()).isNull();
            }

            @Test
            @DisplayName("should create instance with all-args constructor")
            void shouldCreateInstanceWithAllArgsConstructor() {
                // Given
                String encryptedCodes = "encrypted-backup-codes-xyz";
                List<String> usedCodes = Arrays.asList("CODE1", "CODE2");
                Timestamp generatedAt = Timestamp.now();
                Timestamp lastUsedAt = Timestamp.now();

                // When
                BackupCodes backupCodes = new BackupCodes(
                        encryptedCodes, usedCodes, generatedAt, lastUsedAt
                );

                // Then
                assertThat(backupCodes.getEncryptedCodes()).isEqualTo(encryptedCodes);
                assertThat(backupCodes.getUsedCodes()).isEqualTo(usedCodes);
                assertThat(backupCodes.getGeneratedAt()).isEqualTo(generatedAt);
                assertThat(backupCodes.getLastUsedAt()).isEqualTo(lastUsedAt);
            }
        }

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should create instance using builder with default usedCodes")
            void shouldCreateInstanceUsingBuilderWithDefaultUsedCodes() {
                // When
                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .generatedAt(Timestamp.now())
                        .build();

                // Then
                assertThat(backupCodes.getEncryptedCodes()).isEqualTo("encrypted-codes");
                assertThat(backupCodes.getUsedCodes()).isNotNull().isEmpty();
            }

            @Test
            @DisplayName("should create instance using builder with custom usedCodes")
            void shouldCreateInstanceUsingBuilderWithCustomUsedCodes() {
                // Given
                List<String> usedCodes = Arrays.asList("USED1", "USED2", "USED3");

                // When
                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(usedCodes)
                        .build();

                // Then
                assertThat(backupCodes.getUsedCodes()).hasSize(3);
                assertThat(backupCodes.getUsedCodes()).containsExactly("USED1", "USED2", "USED3");
            }

            @Test
            @DisplayName("should allow partial builder usage")
            void shouldAllowPartialBuilderUsage() {
                // When
                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("partial-codes")
                        .build();

                // Then
                assertThat(backupCodes.getEncryptedCodes()).isEqualTo("partial-codes");
                assertThat(backupCodes.getGeneratedAt()).isNull();
                assertThat(backupCodes.getLastUsedAt()).isNull();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            @Test
            @DisplayName("should set and get encryptedCodes")
            void shouldSetAndGetEncryptedCodes() {
                // Given
                BackupCodes backupCodes = new BackupCodes();
                String codes = "encrypted-codes-value";

                // When
                backupCodes.setEncryptedCodes(codes);

                // Then
                assertThat(backupCodes.getEncryptedCodes()).isEqualTo(codes);
            }

            @Test
            @DisplayName("should set and get usedCodes")
            void shouldSetAndGetUsedCodes() {
                // Given
                BackupCodes backupCodes = new BackupCodes();
                List<String> usedCodes = Arrays.asList("ABC123", "DEF456");

                // When
                backupCodes.setUsedCodes(usedCodes);

                // Then
                assertThat(backupCodes.getUsedCodes()).isEqualTo(usedCodes);
                assertThat(backupCodes.getUsedCodes()).hasSize(2);
            }

            @Test
            @DisplayName("should set and get generatedAt")
            void shouldSetAndGetGeneratedAt() {
                // Given
                BackupCodes backupCodes = new BackupCodes();
                Timestamp generatedAt = Timestamp.now();

                // When
                backupCodes.setGeneratedAt(generatedAt);

                // Then
                assertThat(backupCodes.getGeneratedAt()).isEqualTo(generatedAt);
            }

            @Test
            @DisplayName("should set and get lastUsedAt")
            void shouldSetAndGetLastUsedAt() {
                // Given
                BackupCodes backupCodes = new BackupCodes();
                Timestamp lastUsedAt = Timestamp.now();

                // When
                backupCodes.setLastUsedAt(lastUsedAt);

                // Then
                assertThat(backupCodes.getLastUsedAt()).isEqualTo(lastUsedAt);
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields are same")
            void shouldBeEqualWhenAllFieldsAreSame() {
                // Given
                Timestamp now = Timestamp.now();
                BackupCodes codes1 = BackupCodes.builder()
                        .encryptedCodes("codes")
                        .generatedAt(now)
                        .build();

                BackupCodes codes2 = BackupCodes.builder()
                        .encryptedCodes("codes")
                        .generatedAt(now)
                        .build();

                // Then
                assertThat(codes1).isEqualTo(codes2);
                assertThat(codes1.hashCode()).isEqualTo(codes2.hashCode());
            }

            @Test
            @DisplayName("should not be equal when fields differ")
            void shouldNotBeEqualWhenFieldsDiffer() {
                // Given
                BackupCodes codes1 = BackupCodes.builder()
                        .encryptedCodes("codes1")
                        .build();

                BackupCodes codes2 = BackupCodes.builder()
                        .encryptedCodes("codes2")
                        .build();

                // Then
                assertThat(codes1).isNotEqualTo(codes2);
            }
        }

        @Nested
        @DisplayName("UsedCodes Default Value Tests")
        class UsedCodesDefaultValueTests {

            @Test
            @DisplayName("should have empty usedCodes by default with no-args constructor")
            void shouldHaveEmptyUsedCodesByDefaultWithNoArgsConstructor() {
                // When
                BackupCodes backupCodes = new BackupCodes();

                // Then
                assertThat(backupCodes.getUsedCodes()).isNotNull();
                assertThat(backupCodes.getUsedCodes()).isEmpty();
                assertThat(backupCodes.getUsedCodes()).isInstanceOf(ArrayList.class);
            }

            @Test
            @DisplayName("should have empty usedCodes by default with builder")
            void shouldHaveEmptyUsedCodesByDefaultWithBuilder() {
                // When
                BackupCodes backupCodes = BackupCodes.builder().build();

                // Then
                assertThat(backupCodes.getUsedCodes()).isNotNull();
                assertThat(backupCodes.getUsedCodes()).isEmpty();
            }

            @Test
            @DisplayName("should be able to add to usedCodes list")
            void shouldBeAbleToAddToUsedCodesList() {
                // Given
                BackupCodes backupCodes = new BackupCodes();

                // When
                backupCodes.getUsedCodes().add("NEW_CODE");

                // Then
                assertThat(backupCodes.getUsedCodes()).hasSize(1);
                assertThat(backupCodes.getUsedCodes()).contains("NEW_CODE");
            }

            @Test
            @DisplayName("should handle replacing usedCodes with null")
            void shouldHandleReplacingUsedCodesWithNull() {
                // Given
                BackupCodes backupCodes = new BackupCodes();
                backupCodes.getUsedCodes().add("CODE1");

                // When
                backupCodes.setUsedCodes(null);

                // Then
                assertThat(backupCodes.getUsedCodes()).isNull();
            }
        }

        @Nested
        @DisplayName("Backup Codes Usage Scenarios")
        class BackupCodesUsageScenarios {

            @Test
            @DisplayName("should track multiple used codes")
            void shouldTrackMultipleUsedCodes() {
                // Given
                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("all-encrypted-codes")
                        .usedCodes(new ArrayList<>(Arrays.asList("CODE1", "CODE2")))
                        .generatedAt(Timestamp.now())
                        .build();

                // When
                backupCodes.getUsedCodes().add("CODE3");
                backupCodes.setLastUsedAt(Timestamp.now());

                // Then
                assertThat(backupCodes.getUsedCodes()).hasSize(3);
                assertThat(backupCodes.getUsedCodes()).containsExactly("CODE1", "CODE2", "CODE3");
                assertThat(backupCodes.getLastUsedAt()).isNotNull();
            }

            @Test
            @DisplayName("should represent freshly generated codes")
            void shouldRepresentFreshlyGeneratedCodes() {
                // Given
                Timestamp now = Timestamp.now();
                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("fresh-encrypted-codes")
                        .generatedAt(now)
                        .build();

                // Then
                assertThat(backupCodes.getUsedCodes()).isEmpty();
                assertThat(backupCodes.getLastUsedAt()).isNull();
                assertThat(backupCodes.getGeneratedAt()).isEqualTo(now);
            }

            @Test
            @DisplayName("should represent partially used codes")
            void shouldRepresentPartiallyUsedCodes() {
                // Given
                Timestamp generatedAt = Timestamp.ofTimeSecondsAndNanos(1704067200L, 0);
                Timestamp lastUsedAt = Timestamp.ofTimeSecondsAndNanos(1704153600L, 0);

                BackupCodes backupCodes = BackupCodes.builder()
                        .encryptedCodes("partially-used-codes")
                        .usedCodes(Arrays.asList("USED1", "USED2", "USED3"))
                        .generatedAt(generatedAt)
                        .lastUsedAt(lastUsedAt)
                        .build();

                // Then
                assertThat(backupCodes.getUsedCodes()).hasSize(3);
                assertThat(backupCodes.getLastUsedAt().compareTo(backupCodes.getGeneratedAt()))
                        .isGreaterThan(0);
            }
        }
    }

    // ==================== Integration Scenarios ====================

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("should create complete InstagramUserDocument with nested user data")
        void shouldCreateCompleteInstagramUserDocumentWithNestedUserData() {
            // Given
            InstagramUserData userData = InstagramUserData.builder()
                    .account_type("BUSINESS")
                    .followers_count(100000L)
                    .id("ig-biz-123")
                    .media_count(500L)
                    .username("business_influencer")
                    .build();

            // When
            InstagramUserDocument document = InstagramUserDocument.builder()
                    .access_token("encrypted-long-lived-token")
                    .firebaseUserId("firebase-uid-xyz")
                    .followers_count(100000L)
                    .lastUpdated(System.currentTimeMillis() / 1000)
                    .permissions(Arrays.asList(
                            "instagram_basic",
                            "instagram_content_publish",
                            "instagram_manage_insights",
                            "pages_read_engagement"
                    ))
                    .profile_picture_url("https://instagram.com/profiles/business_influencer.jpg")
                    .provider("instagram")
                    .user(userData)
                    .user_id("ig-biz-123")
                    .username("business_influencer")
                    .build();

            // Then
            assertThat(document.getUser()).isNotNull();
            assertThat(document.getUser().getAccount_type()).isEqualTo("BUSINESS");
            assertThat(document.getUser().getFollowers_count()).isEqualTo(document.getFollowers_count());
            assertThat(document.getUser().getUsername()).isEqualTo(document.getUsername());
            assertThat(document.getPermissions()).hasSize(4);
        }

        @Test
        @DisplayName("should create complete TotpSecretDocument with nested backup codes")
        void shouldCreateCompleteTotpSecretDocumentWithNestedBackupCodes() {
            // Given
            Timestamp setupTime = Timestamp.now();
            BackupCodes backupCodes = BackupCodes.builder()
                    .encryptedCodes("bcrypt-encrypted-codes-array")
                    .usedCodes(new ArrayList<>())
                    .generatedAt(setupTime)
                    .build();

            // When
            TotpSecretDocument document = TotpSecretDocument.builder()
                    .encryptedSecret("kms-encrypted-totp-secret")
                    .enabled(true)
                    .setupAt(setupTime)
                    .verifiedAt(setupTime)
                    .backupCodes(backupCodes)
                    .build();

            // Then
            assertThat(document.getBackupCodes()).isNotNull();
            assertThat(document.getBackupCodes().getUsedCodes()).isEmpty();
            assertThat(document.getEnabled()).isTrue();
            assertThat(document.getSetupAt()).isEqualTo(document.getBackupCodes().getGeneratedAt());
        }

        @Test
        @DisplayName("should handle TOTP lifecycle: setup -> verify -> use -> disable")
        void shouldHandleTotpLifecycle() {
            // Setup phase
            Timestamp setupTime = Timestamp.ofTimeSecondsAndNanos(1704067200L, 0);
            BackupCodes backupCodes = BackupCodes.builder()
                    .encryptedCodes("initial-backup-codes")
                    .generatedAt(setupTime)
                    .build();

            TotpSecretDocument document = TotpSecretDocument.builder()
                    .encryptedSecret("secret")
                    .setupAt(setupTime)
                    .backupCodes(backupCodes)
                    .build();

            assertThat(document.getEnabled()).isNull();
            assertThat(document.getVerifiedAt()).isNull();

            // Verify phase
            Timestamp verifyTime = Timestamp.ofTimeSecondsAndNanos(1704067300L, 0);
            document.setEnabled(true);
            document.setVerifiedAt(verifyTime);

            assertThat(document.getEnabled()).isTrue();
            assertThat(document.getVerifiedAt()).isNotNull();

            // Use phase
            Timestamp useTime = Timestamp.ofTimeSecondsAndNanos(1704153600L, 0);
            document.setLastUsedAt(useTime);
            document.getBackupCodes().getUsedCodes().add("BACKUP1");
            document.getBackupCodes().setLastUsedAt(useTime);

            assertThat(document.getLastUsedAt()).isNotNull();
            assertThat(document.getBackupCodes().getUsedCodes()).hasSize(1);

            // Disable phase
            Timestamp disableTime = Timestamp.ofTimeSecondsAndNanos(1704240000L, 0);
            document.setEnabled(false);
            document.setDisabledAt(disableTime);

            assertThat(document.getEnabled()).isFalse();
            assertThat(document.getDisabledAt()).isNotNull();
        }
    }
}
