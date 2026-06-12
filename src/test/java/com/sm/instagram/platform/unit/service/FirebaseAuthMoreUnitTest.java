package com.sm.instagram.platform.unit.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.sm.instagram.model.firestore.InstagramUserDocument;
import com.sm.instagram.model.firestore.TotpSecretDocument;
import com.sm.instagram.platform.auth.exceptions.TwoFactorAuthException;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.security.TokenEncryptionService;
import com.sm.instagram.platform.common.security.TotpEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for Firebase authentication services:
 * - FirestoreService: Instagram user data storage and retrieval
 * - TotpFirestoreService: TOTP/2FA secret management
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Firebase Authentication Additional Unit Tests")
class FirebaseAuthMoreUnitTest {

    // =========================================================================
    // FIRESTORE SERVICE TESTS
    // =========================================================================
    @Nested
    @DisplayName("FirestoreService Tests")
    class FirestoreServiceTests {

        @Mock
        private Firestore firestore;

        @Mock
        private TokenEncryptionService tokenEncryptionService;

        @Mock
        private CollectionReference collectionReference;

        @Mock
        private DocumentReference documentReference;

        @Mock
        private ApiFuture<WriteResult> writeResultFuture;

        @Mock
        private ApiFuture<DocumentSnapshot> documentSnapshotFuture;

        @Mock
        private WriteResult writeResult;

        @Mock
        private DocumentSnapshot documentSnapshot;

        @InjectMocks
        private FirestoreService firestoreService;

        private static final String TEST_FIREBASE_UID = "firebase-uid-123";
        private static final String TEST_INSTAGRAM_ID = "insta-456";
        private static final String TEST_ACCESS_TOKEN = "access-token-xyz";
        private static final String TEST_ENCRYPTED_TOKEN = "encrypted-token-abc";
        private static final String TEST_USERNAME = "test_user";

        @BeforeEach
        void setUp() {
            when(firestore.collection("instagramUsers")).thenReturn(collectionReference);
            when(collectionReference.document(anyString())).thenReturn(documentReference);
            when(tokenEncryptionService.encryptToken(anyString())).thenReturn(TEST_ENCRYPTED_TOKEN);
            when(tokenEncryptionService.decryptToken(anyString())).thenReturn(TEST_ACCESS_TOKEN);
        }

        @Nested
        @DisplayName("storeInstagramUserData Tests")
        class StoreInstagramUserDataTests {

            @Test
            @DisplayName("should store Instagram user data successfully")
            void shouldStoreInstagramUserDataSuccessfully() throws Exception {
                // Given
                Map<String, Object> instagramData = createValidInstagramData();
                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                // When
                firestoreService.storeInstagramUserData(instagramData, TEST_FIREBASE_UID);

                // Then
                verify(documentReference).set(any(InstagramUserDocument.class));
                verify(tokenEncryptionService).encryptToken(TEST_ACCESS_TOKEN);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when instagramData is null")
            void shouldThrowValidationExceptionWhenInstagramDataIsNull() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.storeInstagramUserData(null, TEST_FIREBASE_UID))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when user_id is missing")
            void shouldThrowValidationExceptionWhenUserIdIsMissing() {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("access_token", TEST_ACCESS_TOKEN);

                // When/Then
                assertThatThrownBy(() -> firestoreService.storeInstagramUserData(data, TEST_FIREBASE_UID))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when firebaseUserId is null")
            void shouldThrowValidationExceptionWhenFirebaseUserIdIsNull() {
                // Given
                Map<String, Object> data = createValidInstagramData();

                // When/Then
                assertThatThrownBy(() -> firestoreService.storeInstagramUserData(data, null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when firebaseUserId is empty")
            void shouldThrowValidationExceptionWhenFirebaseUserIdIsEmpty() {
                // Given
                Map<String, Object> data = createValidInstagramData();

                // When/Then
                assertThatThrownBy(() -> firestoreService.storeInstagramUserData(data, ""))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw NetworkTranslatableException when Firestore write interrupted")
            void shouldThrowNetworkExceptionWhenInterrupted() throws Exception {
                // Given
                Map<String, Object> instagramData = createValidInstagramData();
                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenThrow(new InterruptedException("Interrupted"));

                // When/Then
                assertThatThrownBy(() -> firestoreService.storeInstagramUserData(instagramData, TEST_FIREBASE_UID))
                        .isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should throw NetworkTranslatableException when Firestore write fails with ExecutionException")
            void shouldThrowNetworkExceptionWhenExecutionFails() throws Exception {
                // Given
                Map<String, Object> instagramData = createValidInstagramData();
                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When/Then
                assertThatThrownBy(() -> firestoreService.storeInstagramUserData(instagramData, TEST_FIREBASE_UID))
                        .isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should handle nested user data correctly")
            void shouldHandleNestedUserDataCorrectly() throws Exception {
                // Given
                Map<String, Object> instagramData = createValidInstagramData();
                Map<String, Object> userData = new HashMap<>();
                userData.put("account_type", "BUSINESS");
                userData.put("id", TEST_INSTAGRAM_ID);
                userData.put("username", TEST_USERNAME);
                userData.put("followers_count", 1000L);
                userData.put("media_count", 50L);
                instagramData.put("user", userData);

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> docCaptor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(instagramData, TEST_FIREBASE_UID);

                // Then
                verify(documentReference).set(docCaptor.capture());
                InstagramUserDocument capturedDoc = docCaptor.getValue();
                assertThat(capturedDoc.getUser()).isNotNull();
                assertThat(capturedDoc.getUser().getAccount_type()).isEqualTo("BUSINESS");
            }

            @Test
            @DisplayName("should handle permissions list correctly")
            void shouldHandlePermissionsListCorrectly() throws Exception {
                // Given
                Map<String, Object> instagramData = createValidInstagramData();
                List<String> permissions = Arrays.asList("instagram_basic", "instagram_content_publish");
                instagramData.put("permissions", permissions);

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> docCaptor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(instagramData, TEST_FIREBASE_UID);

                // Then
                verify(documentReference).set(docCaptor.capture());
                InstagramUserDocument capturedDoc = docCaptor.getValue();
                assertThat(capturedDoc.getPermissions()).containsExactly("instagram_basic", "instagram_content_publish");
            }

            @Test
            @DisplayName("should handle followers count as Number")
            void shouldHandleFollowersCountAsNumber() throws Exception {
                // Given
                Map<String, Object> instagramData = createValidInstagramData();
                instagramData.put("followers_count", 5000);

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> docCaptor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(instagramData, TEST_FIREBASE_UID);

                // Then
                verify(documentReference).set(docCaptor.capture());
                InstagramUserDocument capturedDoc = docCaptor.getValue();
                assertThat(capturedDoc.getFollowers_count()).isEqualTo(5000L);
            }

            @Test
            @DisplayName("should handle missing optional fields gracefully")
            void shouldHandleMissingOptionalFieldsGracefully() throws Exception {
                // Given - minimal data with only required fields
                Map<String, Object> instagramData = new HashMap<>();
                instagramData.put("user_id", TEST_INSTAGRAM_ID);

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                // When
                firestoreService.storeInstagramUserData(instagramData, TEST_FIREBASE_UID);

                // Then
                verify(documentReference).set(any(InstagramUserDocument.class));
            }
        }

        @Nested
        @DisplayName("getInstagramUserDataByFirebaseUid Tests")
        class GetInstagramUserDataByFirebaseUidTests {

            @Test
            @DisplayName("should retrieve Instagram user data by Firebase UID")
            void shouldRetrieveInstagramUserDataByFirebaseUid() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Map<String, Object> data = new HashMap<>();
                data.put("user_id", TEST_INSTAGRAM_ID);
                data.put("access_token", TEST_ENCRYPTED_TOKEN);
                data.put("username", TEST_USERNAME);
                when(documentSnapshot.getData()).thenReturn(data);

                // When
                Map<String, Object> result = firestoreService.getInstagramUserDataByFirebaseUid(TEST_FIREBASE_UID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.get("instagramId")).isEqualTo(TEST_INSTAGRAM_ID);
                assertThat(result.get("access_token")).isEqualTo(TEST_ACCESS_TOKEN);
                verify(tokenEncryptionService).decryptToken(TEST_ENCRYPTED_TOKEN);
            }

            @Test
            @DisplayName("should return null when document does not exist")
            void shouldReturnNullWhenDocumentDoesNotExist() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                Map<String, Object> result = firestoreService.getInstagramUserDataByFirebaseUid(TEST_FIREBASE_UID);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when firebaseUid is null")
            void shouldThrowValidationExceptionWhenFirebaseUidIsNull() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.getInstagramUserDataByFirebaseUid(null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when firebaseUid is empty")
            void shouldThrowValidationExceptionWhenFirebaseUidIsEmpty() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.getInstagramUserDataByFirebaseUid("   "))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw NetworkTranslatableException when interrupted")
            void shouldThrowNetworkExceptionWhenInterrupted() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenThrow(new InterruptedException("Interrupted"));

                // When/Then
                assertThatThrownBy(() -> firestoreService.getInstagramUserDataByFirebaseUid(TEST_FIREBASE_UID))
                        .isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should remove access token when decryption fails")
            void shouldRemoveAccessTokenWhenDecryptionFails() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Map<String, Object> data = new HashMap<>();
                data.put("user_id", TEST_INSTAGRAM_ID);
                data.put("access_token", TEST_ENCRYPTED_TOKEN);
                when(documentSnapshot.getData()).thenReturn(data);
                when(tokenEncryptionService.decryptToken(anyString())).thenThrow(new RuntimeException("Decryption failed"));

                // When
                Map<String, Object> result = firestoreService.getInstagramUserDataByFirebaseUid(TEST_FIREBASE_UID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.containsKey("access_token")).isFalse();
            }

            @Test
            @DisplayName("should handle null access token gracefully")
            void shouldHandleNullAccessTokenGracefully() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Map<String, Object> data = new HashMap<>();
                data.put("user_id", TEST_INSTAGRAM_ID);
                data.put("access_token", null);
                when(documentSnapshot.getData()).thenReturn(data);

                // When
                Map<String, Object> result = firestoreService.getInstagramUserDataByFirebaseUid(TEST_FIREBASE_UID);

                // Then
                assertThat(result).isNotNull();
                verify(tokenEncryptionService, never()).decryptToken(anyString());
            }
        }

        @Nested
        @DisplayName("getInstagramUserData Tests")
        class GetInstagramUserDataTests {

            @Test
            @DisplayName("should retrieve Instagram user data by Instagram user ID")
            void shouldRetrieveInstagramUserDataByInstagramId() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Map<String, Object> data = new HashMap<>();
                data.put("user_id", TEST_INSTAGRAM_ID);
                data.put("access_token", TEST_ENCRYPTED_TOKEN);
                when(documentSnapshot.getData()).thenReturn(data);

                // When
                Map<String, Object> result = firestoreService.getInstagramUserData(TEST_INSTAGRAM_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.get("access_token")).isEqualTo(TEST_ACCESS_TOKEN);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when instagramUserId is null")
            void shouldThrowValidationExceptionWhenInstagramUserIdIsNull() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.getInstagramUserData(null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when instagramUserId is empty")
            void shouldThrowValidationExceptionWhenInstagramUserIdIsEmpty() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.getInstagramUserData("  "))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should return null when document does not exist")
            void shouldReturnNullWhenDocumentDoesNotExist() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                Map<String, Object> result = firestoreService.getInstagramUserData(TEST_INSTAGRAM_ID);

                // Then
                assertThat(result).isNull();
            }
        }

        @Nested
        @DisplayName("updateAccessToken Tests")
        class UpdateAccessTokenTests {

            @Test
            @DisplayName("should update access token successfully")
            void shouldUpdateAccessTokenSuccessfully() throws Exception {
                // Given
                long tokenCreatedAt = System.currentTimeMillis() / 1000;
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                // When
                firestoreService.updateAccessToken(TEST_FIREBASE_UID, "new-token", tokenCreatedAt);

                // Then
                verify(tokenEncryptionService).encryptToken("new-token");
                verify(documentReference).update(anyMap());
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when firebaseUid is null")
            void shouldThrowValidationExceptionWhenFirebaseUidIsNull() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.updateAccessToken(null, "token", 12345L))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when firebaseUid is empty")
            void shouldThrowValidationExceptionWhenFirebaseUidIsEmpty() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.updateAccessToken("  ", "token", 12345L))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when newAccessToken is null")
            void shouldThrowValidationExceptionWhenNewAccessTokenIsNull() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.updateAccessToken(TEST_FIREBASE_UID, null, 12345L))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationTranslatableException when newAccessToken is empty")
            void shouldThrowValidationExceptionWhenNewAccessTokenIsEmpty() {
                // When/Then
                assertThatThrownBy(() -> firestoreService.updateAccessToken(TEST_FIREBASE_UID, "  ", 12345L))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw NetworkTranslatableException when update fails")
            void shouldThrowNetworkExceptionWhenUpdateFails() throws Exception {
                // Given
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When/Then
                assertThatThrownBy(() -> firestoreService.updateAccessToken(TEST_FIREBASE_UID, "token", 12345L))
                        .isInstanceOf(NetworkTranslatableException.class);
            }

            @Test
            @DisplayName("should include tokenRefreshedAt in update")
            void shouldIncludeTokenRefreshedAtInUpdate() throws Exception {
                // Given
                long tokenCreatedAt = System.currentTimeMillis() / 1000;
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

                // When
                firestoreService.updateAccessToken(TEST_FIREBASE_UID, "new-token", tokenCreatedAt);

                // Then
                verify(documentReference).update(captor.capture());
                Map<String, Object> updates = captor.getValue();
                assertThat(updates).containsKey("tokenRefreshedAt");
                assertThat(updates).containsKey("lastUpdated");
                assertThat(updates).containsKey("access_token");
            }
        }

        @Nested
        @DisplayName("getDecryptedAccessToken Tests")
        class GetDecryptedAccessTokenTests {

            @Test
            @DisplayName("should return decrypted access token")
            void shouldReturnDecryptedAccessToken() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Map<String, Object> data = new HashMap<>();
                data.put("user_id", TEST_INSTAGRAM_ID);
                data.put("access_token", TEST_ENCRYPTED_TOKEN);
                when(documentSnapshot.getData()).thenReturn(data);

                // When
                String result = firestoreService.getDecryptedAccessToken(TEST_FIREBASE_UID);

                // Then
                assertThat(result).isEqualTo(TEST_ACCESS_TOKEN);
            }

            @Test
            @DisplayName("should return null when no data exists")
            void shouldReturnNullWhenNoDataExists() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                String result = firestoreService.getDecryptedAccessToken(TEST_FIREBASE_UID);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return null when access_token field is missing")
            void shouldReturnNullWhenAccessTokenFieldIsMissing() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                Map<String, Object> data = new HashMap<>();
                data.put("user_id", TEST_INSTAGRAM_ID);
                // No access_token field
                when(documentSnapshot.getData()).thenReturn(data);

                // When
                String result = firestoreService.getDecryptedAccessToken(TEST_FIREBASE_UID);

                // Then
                assertThat(result).isNull();
            }
        }

        @Nested
        @DisplayName("storeSocialConnection Tests (Deprecated)")
        class StoreSocialConnectionTests {

            @Test
            @DisplayName("deprecated method should log warning and not store data")
            void deprecatedMethodShouldLogWarningAndNotStoreData() {
                // Given
                Map<String, Object> socialData = new HashMap<>();
                socialData.put("key", "value");

                // When
                firestoreService.storeSocialConnection(TEST_FIREBASE_UID, "instagram", TEST_INSTAGRAM_ID, socialData);

                // Then - no interaction with Firestore since method is deprecated
                verify(documentReference, never()).set(any());
            }
        }

        private Map<String, Object> createValidInstagramData() {
            Map<String, Object> data = new HashMap<>();
            data.put("user_id", TEST_INSTAGRAM_ID);
            data.put("access_token", TEST_ACCESS_TOKEN);
            data.put("username", TEST_USERNAME);
            return data;
        }
    }

    // =========================================================================
    // TOTP FIRESTORE SERVICE TESTS
    // =========================================================================
    @Nested
    @DisplayName("TotpFirestoreService Tests")
    class TotpFirestoreServiceTests {

        @Mock
        private Firestore firestore;

        @Mock
        private TotpEncryptionService encryptionService;

        @Mock
        private CollectionReference collectionReference;

        @Mock
        private DocumentReference documentReference;

        @Mock
        private CollectionReference auditCollectionReference;

        @Mock
        private DocumentReference auditDocumentReference;

        @Mock
        private ApiFuture<WriteResult> writeResultFuture;

        @Mock
        private ApiFuture<DocumentSnapshot> documentSnapshotFuture;

        @Mock
        private ApiFuture<QuerySnapshot> querySnapshotFuture;

        @Mock
        private WriteResult writeResult;

        @Mock
        private DocumentSnapshot documentSnapshot;

        @Mock
        private QuerySnapshot querySnapshot;

        @InjectMocks
        private TotpFirestoreService totpFirestoreService;

        private static final String TEST_USER_ID = "user-123";
        private static final String TEST_SECRET = "JBSWY3DPEHPK3PXP";
        private static final String TEST_ENCRYPTED_SECRET = "encrypted-secret-xyz";
        private static final String TEST_BACKUP_CODE = "ABCD1234";

        @BeforeEach
        void setUp() {
            when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
            when(collectionReference.document(anyString())).thenReturn(documentReference);
            when(documentReference.collection("auditLog")).thenReturn(auditCollectionReference);
            when(auditCollectionReference.document(anyString())).thenReturn(auditDocumentReference);
            when(encryptionService.encryptTotpSecret(anyString())).thenReturn(TEST_ENCRYPTED_SECRET);
            when(encryptionService.decryptTotpSecret(anyString())).thenReturn(TEST_SECRET);
            when(encryptionService.encryptBackupCodes(anyList())).thenReturn("encrypted-backup-codes");
        }

        @Nested
        @DisplayName("storeTotpSecret Tests")
        class StoreTotpSecretTests {

            @Test
            @DisplayName("should store TOTP secret successfully")
            void shouldStoreTotpSecretSuccessfully() throws Exception {
                // Given
                List<String> backupCodes = Arrays.asList("CODE1", "CODE2", "CODE3");
                when(documentReference.set(any(TotpSecretDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.storeTotpSecret(TEST_USER_ID, TEST_SECRET, backupCodes);

                // Then
                verify(encryptionService).encryptTotpSecret(TEST_SECRET);
                verify(encryptionService).encryptBackupCodes(backupCodes);
                verify(documentReference).set(any(TotpSecretDocument.class));
            }

            @Test
            @DisplayName("should set enabled to false on initial store")
            void shouldSetEnabledToFalseOnInitialStore() throws Exception {
                // Given
                List<String> backupCodes = Arrays.asList("CODE1", "CODE2");
                when(documentReference.set(any(TotpSecretDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                ArgumentCaptor<TotpSecretDocument> captor = ArgumentCaptor.forClass(TotpSecretDocument.class);

                // When
                totpFirestoreService.storeTotpSecret(TEST_USER_ID, TEST_SECRET, backupCodes);

                // Then
                verify(documentReference).set(captor.capture());
                TotpSecretDocument doc = captor.getValue();
                assertThat(doc.getEnabled()).isFalse();
            }

            @Test
            @DisplayName("should throw BusinessRuleTranslatableException when storage fails")
            void shouldThrowBusinessRuleExceptionWhenStorageFails() throws Exception {
                // Given
                List<String> backupCodes = Arrays.asList("CODE1");
                when(documentReference.set(any(TotpSecretDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When/Then
                assertThatThrownBy(() -> totpFirestoreService.storeTotpSecret(TEST_USER_ID, TEST_SECRET, backupCodes))
                        .isInstanceOf(BusinessRuleTranslatableException.class);
            }

            @Test
            @DisplayName("should log audit event on successful storage")
            void shouldLogAuditEventOnSuccessfulStorage() throws Exception {
                // Given
                List<String> backupCodes = Arrays.asList("CODE1");
                when(documentReference.set(any(TotpSecretDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.storeTotpSecret(TEST_USER_ID, TEST_SECRET, backupCodes);

                // Then
                verify(auditDocumentReference).set(argThat((Map<String, Object> map) ->
                    "TOTP_SETUP".equals(map.get("action")) && "SUCCESS".equals(map.get("result"))
                ));
            }
        }

        @Nested
        @DisplayName("getTotpSecret Tests")
        class GetTotpSecretTests {

            @Test
            @DisplayName("should return decrypted TOTP secret")
            void shouldReturnDecryptedTotpSecret() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(documentReference.update(anyString(), any())).thenReturn(writeResultFuture);

                // When
                String result = totpFirestoreService.getTotpSecret(TEST_USER_ID);

                // Then
                assertThat(result).isEqualTo(TEST_SECRET);
                verify(encryptionService).decryptTotpSecret(TEST_ENCRYPTED_SECRET);
            }

            @Test
            @DisplayName("should return null when document does not exist")
            void shouldReturnNullWhenDocumentDoesNotExist() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                String result = totpFirestoreService.getTotpSecret(TEST_USER_ID);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return null when encrypted secret is null")
            void shouldReturnNullWhenEncryptedSecretIsNull() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(null)
                        .enabled(true)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                String result = totpFirestoreService.getTotpSecret(TEST_USER_ID);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return null on exception")
            void shouldReturnNullOnException() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When
                String result = totpFirestoreService.getTotpSecret(TEST_USER_ID);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should update lastUsedAt when retrieving secret")
            void shouldUpdateLastUsedAtWhenRetrievingSecret() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(documentReference.update(anyString(), any())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.getTotpSecret(TEST_USER_ID);

                // Then
                verify(documentReference).update(eq("lastUsedAt"), any(Timestamp.class));
            }
        }

        @Nested
        @DisplayName("is2FAEnabled Tests")
        class Is2FAEnabledTests {

            @Test
            @DisplayName("should return true when 2FA is enabled")
            void shouldReturnTrueWhen2FAIsEnabled() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                boolean result = totpFirestoreService.is2FAEnabled(TEST_USER_ID);

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false when 2FA is not enabled")
            void shouldReturnFalseWhen2FAIsNotEnabled() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(false)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                boolean result = totpFirestoreService.is2FAEnabled(TEST_USER_ID);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when document does not exist")
            void shouldReturnFalseWhenDocumentDoesNotExist() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                boolean result = totpFirestoreService.is2FAEnabled(TEST_USER_ID);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should throw TwoFactorAuthException on Firestore failure")
            void shouldThrowTwoFactorAuthExceptionOnFirestoreFailure() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When/Then
                assertThatThrownBy(() -> totpFirestoreService.is2FAEnabled(TEST_USER_ID))
                        .isInstanceOf(TwoFactorAuthException.class)
                        .hasMessageContaining("Unable to verify 2FA status");
            }

            @Test
            @DisplayName("should return false when enabled field is null")
            void shouldReturnFalseWhenEnabledFieldIsNull() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(null)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                boolean result = totpFirestoreService.is2FAEnabled(TEST_USER_ID);

                // Then
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("totpSecretExists Tests")
        class TotpSecretExistsTests {

            @Test
            @DisplayName("should return true when document exists")
            void shouldReturnTrueWhenDocumentExists() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);

                // When
                boolean result = totpFirestoreService.totpSecretExists(TEST_USER_ID);

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false when document does not exist")
            void shouldReturnFalseWhenDocumentDoesNotExist() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                boolean result = totpFirestoreService.totpSecretExists(TEST_USER_ID);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false on exception")
            void shouldReturnFalseOnException() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When
                boolean result = totpFirestoreService.totpSecretExists(TEST_USER_ID);

                // Then
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("enable2FA Tests")
        class Enable2FATests {

            @Test
            @DisplayName("should enable 2FA successfully")
            void shouldEnable2FASuccessfully() throws Exception {
                // Given
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.enable2FA(TEST_USER_ID);

                // Then
                verify(documentReference).update(argThat((Map<String, Object> map) ->
                    Boolean.TRUE.equals(map.get("enabled")) && map.containsKey("verifiedAt")
                ));
            }

            @Test
            @DisplayName("should log audit event on successful enable")
            void shouldLogAuditEventOnSuccessfulEnable() throws Exception {
                // Given
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.enable2FA(TEST_USER_ID);

                // Then
                verify(auditDocumentReference).set(argThat((Map<String, Object> map) ->
                    "2FA_ENABLED".equals(map.get("action")) && "SUCCESS".equals(map.get("result"))
                ));
            }

            @Test
            @DisplayName("should throw BusinessRuleTranslatableException when enable fails")
            void shouldThrowBusinessRuleExceptionWhenEnableFails() throws Exception {
                // Given
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When/Then
                assertThatThrownBy(() -> totpFirestoreService.enable2FA(TEST_USER_ID))
                        .isInstanceOf(BusinessRuleTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("disable2FA Tests")
        class Disable2FATests {

            @Test
            @DisplayName("should disable 2FA successfully")
            void shouldDisable2FASuccessfully() throws Exception {
                // Given
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.disable2FA(TEST_USER_ID);

                // Then
                verify(documentReference).update(argThat((Map<String, Object> map) ->
                    Boolean.FALSE.equals(map.get("enabled")) && map.containsKey("disabledAt")
                ));
            }

            @Test
            @DisplayName("should log audit event on successful disable")
            void shouldLogAuditEventOnSuccessfulDisable() throws Exception {
                // Given
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.disable2FA(TEST_USER_ID);

                // Then
                verify(auditDocumentReference).set(argThat((Map<String, Object> map) ->
                    "2FA_DISABLED".equals(map.get("action")) && "SUCCESS".equals(map.get("result"))
                ));
            }

            @Test
            @DisplayName("should throw BusinessRuleTranslatableException when disable fails")
            void shouldThrowBusinessRuleExceptionWhenDisableFails() throws Exception {
                // Given
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When/Then
                assertThatThrownBy(() -> totpFirestoreService.disable2FA(TEST_USER_ID))
                        .isInstanceOf(BusinessRuleTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("verifyBackupCode Tests")
        class VerifyBackupCodeTests {

            @Test
            @DisplayName("should verify valid backup code successfully")
            void shouldVerifyValidBackupCodeSuccessfully() throws Exception {
                // Given
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(new ArrayList<>())
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(encryptionService.verifyBackupCode(TEST_BACKUP_CODE, "encrypted-codes")).thenReturn(true);
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                boolean result = totpFirestoreService.verifyBackupCode(TEST_USER_ID, TEST_BACKUP_CODE);

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false for already used backup code")
            void shouldReturnFalseForAlreadyUsedBackupCode() throws Exception {
                // Given
                List<String> usedCodes = new ArrayList<>();
                usedCodes.add(TEST_BACKUP_CODE);

                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(usedCodes)
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                boolean result = totpFirestoreService.verifyBackupCode(TEST_USER_ID, TEST_BACKUP_CODE);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when document does not exist")
            void shouldReturnFalseWhenDocumentDoesNotExist() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                boolean result = totpFirestoreService.verifyBackupCode(TEST_USER_ID, TEST_BACKUP_CODE);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when backupCodes is null")
            void shouldReturnFalseWhenBackupCodesIsNull() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(null)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                boolean result = totpFirestoreService.verifyBackupCode(TEST_USER_ID, TEST_BACKUP_CODE);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when encryptedCodes is null")
            void shouldReturnFalseWhenEncryptedCodesIsNull() throws Exception {
                // Given
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes(null)
                        .usedCodes(new ArrayList<>())
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                boolean result = totpFirestoreService.verifyBackupCode(TEST_USER_ID, TEST_BACKUP_CODE);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should mark code as used after successful verification")
            void shouldMarkCodeAsUsedAfterSuccessfulVerification() throws Exception {
                // Given
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(new ArrayList<>())
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(encryptionService.verifyBackupCode(TEST_BACKUP_CODE, "encrypted-codes")).thenReturn(true);
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

                // When
                totpFirestoreService.verifyBackupCode(TEST_USER_ID, TEST_BACKUP_CODE);

                // Then
                verify(documentReference).update(captor.capture());
                Map<String, Object> updates = captor.getValue();
                assertThat(updates).containsKey("backupCodes.usedCodes");
                assertThat(updates).containsKey("backupCodes.lastUsedAt");
            }

            @Test
            @DisplayName("should log BACKUP_CODE_USED audit event on success")
            void shouldLogBackupCodeUsedAuditEventOnSuccess() throws Exception {
                // Given
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(new ArrayList<>())
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(encryptionService.verifyBackupCode(TEST_BACKUP_CODE, "encrypted-codes")).thenReturn(true);
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.verifyBackupCode(TEST_USER_ID, TEST_BACKUP_CODE);

                // Then
                verify(auditDocumentReference).set(argThat((Map<String, Object> map) ->
                    "BACKUP_CODE_USED".equals(map.get("action")) && "SUCCESS".equals(map.get("result"))
                ));
            }
        }

        @Nested
        @DisplayName("getBackupCodesInfo Tests")
        class GetBackupCodesInfoTests {

            @Test
            @DisplayName("should return backup codes info successfully")
            void shouldReturnBackupCodesInfoSuccessfully() throws Exception {
                // Given
                List<String> usedCodes = Arrays.asList("CODE1", "CODE2");
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(usedCodes)
                        .generatedAt(Timestamp.now())
                        .lastUsedAt(Timestamp.now())
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                Map<String, Object> result = totpFirestoreService.getBackupCodesInfo(TEST_USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.get("totalCodes")).isEqualTo(10);
                assertThat(result.get("usedCodes")).isEqualTo(2);
                assertThat(result.get("remainingCodes")).isEqualTo(8);
            }

            @Test
            @DisplayName("should return null when document does not exist")
            void shouldReturnNullWhenDocumentDoesNotExist() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(false);

                // When
                Map<String, Object> result = totpFirestoreService.getBackupCodesInfo(TEST_USER_ID);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return null when backupCodes is null")
            void shouldReturnNullWhenBackupCodesIsNull() throws Exception {
                // Given
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(null)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                Map<String, Object> result = totpFirestoreService.getBackupCodesInfo(TEST_USER_ID);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should handle null usedCodes list")
            void shouldHandleNullUsedCodesList() throws Exception {
                // Given
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(null)
                        .generatedAt(Timestamp.now())
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret(TEST_ENCRYPTED_SECRET)
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);

                // When
                Map<String, Object> result = totpFirestoreService.getBackupCodesInfo(TEST_USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.get("usedCodes")).isEqualTo(0);
                assertThat(result.get("remainingCodes")).isEqualTo(10);
            }
        }

        @Nested
        @DisplayName("logAuditEvent Tests")
        class LogAuditEventTests {

            @Test
            @DisplayName("should log audit event with all fields")
            void shouldLogAuditEventWithAllFields() {
                // Given
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.logAuditEvent(TEST_USER_ID, "TEST_ACTION", "SUCCESS", "192.168.1.1", "Mozilla/5.0");

                // Then
                verify(auditDocumentReference).set(argThat((Map<String, Object> map) ->
                    "TEST_ACTION".equals(map.get("action")) &&
                    "SUCCESS".equals(map.get("result")) &&
                    "192.168.1.1".equals(map.get("ipAddress")) &&
                    "Mozilla/5.0".equals(map.get("userAgent")) &&
                    map.containsKey("timestamp")
                ));
            }

            @Test
            @DisplayName("should log audit event without optional fields")
            void shouldLogAuditEventWithoutOptionalFields() {
                // Given
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                totpFirestoreService.logAuditEvent(TEST_USER_ID, "TEST_ACTION", "FAILED", null, null);

                // Then
                verify(auditDocumentReference).set(argThat((Map<String, Object> map) ->
                    "TEST_ACTION".equals(map.get("action")) &&
                    "FAILED".equals(map.get("result")) &&
                    !map.containsKey("ipAddress") &&
                    !map.containsKey("userAgent")
                ));
            }

            @Test
            @DisplayName("should not throw when audit logging fails")
            void shouldNotThrowWhenAuditLoggingFails() {
                // Given
                when(auditDocumentReference.set(anyMap())).thenThrow(new RuntimeException("Audit failed"));

                // When/Then - should not throw
                assertThatCode(() ->
                    totpFirestoreService.logAuditEvent(TEST_USER_ID, "TEST", "SUCCESS", null, null)
                ).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("deleteTotpData Tests")
        class DeleteTotpDataTests {

            @Test
            @DisplayName("should delete TOTP data successfully")
            void shouldDeleteTotpDataSuccessfully() throws Exception {
                // Given
                when(auditCollectionReference.limit(anyInt())).thenReturn(mock(Query.class));
                Query mockQuery = mock(Query.class);
                when(auditCollectionReference.limit(10)).thenReturn(mockQuery);
                when(mockQuery.get()).thenReturn(querySnapshotFuture);
                when(querySnapshotFuture.get()).thenReturn(querySnapshot);
                when(querySnapshot.getDocuments()).thenReturn(Collections.emptyList());
                when(documentReference.delete()).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);

                // When
                totpFirestoreService.deleteTotpData(TEST_USER_ID);

                // Then
                verify(documentReference).delete();
            }

            @Test
            @DisplayName("should throw BusinessRuleTranslatableException when delete fails")
            void shouldThrowBusinessRuleExceptionWhenDeleteFails() throws Exception {
                // Given
                Query mockQuery = mock(Query.class);
                when(auditCollectionReference.limit(10)).thenReturn(mockQuery);
                when(mockQuery.get()).thenReturn(querySnapshotFuture);
                when(querySnapshotFuture.get()).thenReturn(querySnapshot);
                when(querySnapshot.getDocuments()).thenReturn(Collections.emptyList());
                when(documentReference.delete()).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When/Then
                assertThatThrownBy(() -> totpFirestoreService.deleteTotpData(TEST_USER_ID))
                        .isInstanceOf(BusinessRuleTranslatableException.class);
            }

            @Test
            @DisplayName("should delete audit logs before main document")
            void shouldDeleteAuditLogsBeforeMainDocument() throws Exception {
                // Given
                QueryDocumentSnapshot auditDoc = mock(QueryDocumentSnapshot.class);
                DocumentReference auditRef = mock(DocumentReference.class);
                when(auditDoc.getReference()).thenReturn(auditRef);
                when(auditRef.delete()).thenReturn(writeResultFuture);

                Query mockQuery = mock(Query.class);
                when(auditCollectionReference.limit(10)).thenReturn(mockQuery);
                when(mockQuery.get()).thenReturn(querySnapshotFuture);
                // First call returns one audit doc, second call returns empty
                when(querySnapshotFuture.get()).thenReturn(querySnapshot);
                when(querySnapshot.getDocuments())
                        .thenReturn(Collections.singletonList(auditDoc))
                        .thenReturn(Collections.emptyList());
                when(documentReference.delete()).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);

                // When
                totpFirestoreService.deleteTotpData(TEST_USER_ID);

                // Then
                verify(auditRef).delete();
                verify(documentReference).delete();
            }
        }
    }

    // =========================================================================
    // ADDITIONAL EDGE CASE TESTS
    // =========================================================================
    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesTests {

        @Nested
        @DisplayName("FirestoreService Edge Cases")
        class FirestoreServiceEdgeCases {

            @Mock
            private Firestore firestore;

            @Mock
            private TokenEncryptionService tokenEncryptionService;

            @Mock
            private CollectionReference collectionReference;

            @Mock
            private DocumentReference documentReference;

            @Mock
            private ApiFuture<WriteResult> writeResultFuture;

            @Mock
            private WriteResult writeResult;

            @InjectMocks
            private FirestoreService firestoreService;

            @BeforeEach
            void setUp() {
                when(firestore.collection("instagramUsers")).thenReturn(collectionReference);
                when(collectionReference.document(anyString())).thenReturn(documentReference);
            }

            @Test
            @DisplayName("should handle empty permissions list")
            void shouldHandleEmptyPermissionsList() throws Exception {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", "user123");
                data.put("permissions", Collections.emptyList());

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> captor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(data, "firebase-uid");

                // Then
                verify(documentReference).set(captor.capture());
                assertThat(captor.getValue().getPermissions()).isEmpty();
            }

            @Test
            @DisplayName("should handle non-list permissions object")
            void shouldHandleNonListPermissionsObject() throws Exception {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", "user123");
                data.put("permissions", "not-a-list");

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> captor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(data, "firebase-uid");

                // Then
                verify(documentReference).set(captor.capture());
                assertThat(captor.getValue().getPermissions()).isEmpty();
            }

            @Test
            @DisplayName("should handle non-map user object")
            void shouldHandleNonMapUserObject() throws Exception {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", "user123");
                data.put("user", "not-a-map");

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> captor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(data, "firebase-uid");

                // Then
                verify(documentReference).set(captor.capture());
                assertThat(captor.getValue().getUser()).isNull();
            }

            @Test
            @DisplayName("should handle Long followers count in nested user")
            void shouldHandleLongFollowersCountInNestedUser() throws Exception {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", "user123");

                Map<String, Object> userData = new HashMap<>();
                userData.put("followers_count", 1000000L);
                data.put("user", userData);

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> captor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(data, "firebase-uid");

                // Then
                verify(documentReference).set(captor.capture());
                assertThat(captor.getValue().getUser().getFollowers_count()).isEqualTo(1000000L);
            }

            @Test
            @DisplayName("should handle Integer followers count in nested user")
            void shouldHandleIntegerFollowersCountInNestedUser() throws Exception {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", "user123");

                Map<String, Object> userData = new HashMap<>();
                userData.put("followers_count", 5000);
                data.put("user", userData);

                when(documentReference.set(any(InstagramUserDocument.class))).thenReturn(writeResultFuture);
                when(writeResultFuture.get()).thenReturn(writeResult);
                when(writeResult.getUpdateTime()).thenReturn(Timestamp.now());

                ArgumentCaptor<InstagramUserDocument> captor = ArgumentCaptor.forClass(InstagramUserDocument.class);

                // When
                firestoreService.storeInstagramUserData(data, "firebase-uid");

                // Then
                verify(documentReference).set(captor.capture());
                assertThat(captor.getValue().getUser().getFollowers_count()).isEqualTo(5000L);
            }
        }

        @Nested
        @DisplayName("TotpFirestoreService Edge Cases")
        class TotpFirestoreServiceEdgeCases {

            @Mock
            private Firestore firestore;

            @Mock
            private TotpEncryptionService encryptionService;

            @Mock
            private CollectionReference collectionReference;

            @Mock
            private DocumentReference documentReference;

            @Mock
            private CollectionReference auditCollectionReference;

            @Mock
            private DocumentReference auditDocumentReference;

            @Mock
            private ApiFuture<DocumentSnapshot> documentSnapshotFuture;

            @Mock
            private ApiFuture<WriteResult> writeResultFuture;

            @Mock
            private DocumentSnapshot documentSnapshot;

            @InjectMocks
            private TotpFirestoreService totpFirestoreService;

            @BeforeEach
            void setUp() {
                when(firestore.collection("totpSecrets")).thenReturn(collectionReference);
                when(collectionReference.document(anyString())).thenReturn(documentReference);
                when(documentReference.collection("auditLog")).thenReturn(auditCollectionReference);
                when(auditCollectionReference.document(anyString())).thenReturn(auditDocumentReference);
            }

            @Test
            @DisplayName("should handle null usedCodes when verifying backup code")
            void shouldHandleNullUsedCodesWhenVerifyingBackupCode() throws Exception {
                // Given
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(null)
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("secret")
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(encryptionService.verifyBackupCode("CODE123", "encrypted-codes")).thenReturn(true);
                when(documentReference.update(anyMap())).thenReturn(writeResultFuture);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                boolean result = totpFirestoreService.verifyBackupCode("user123", "CODE123");

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false when verification fails")
            void shouldReturnFalseWhenVerificationFails() throws Exception {
                // Given
                TotpSecretDocument.BackupCodes backupCodes = TotpSecretDocument.BackupCodes.builder()
                        .encryptedCodes("encrypted-codes")
                        .usedCodes(new ArrayList<>())
                        .build();
                TotpSecretDocument document = TotpSecretDocument.builder()
                        .encryptedSecret("secret")
                        .enabled(true)
                        .backupCodes(backupCodes)
                        .build();

                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(document);
                when(encryptionService.verifyBackupCode("WRONG_CODE", "encrypted-codes")).thenReturn(false);
                when(auditDocumentReference.set(anyMap())).thenReturn(writeResultFuture);

                // When
                boolean result = totpFirestoreService.verifyBackupCode("user123", "WRONG_CODE");

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false on exception during verification")
            void shouldReturnFalseOnExceptionDuringVerification() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When
                boolean result = totpFirestoreService.verifyBackupCode("user123", "CODE123");

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return null for backup codes info on exception")
            void shouldReturnNullForBackupCodesInfoOnException() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenThrow(new ExecutionException("Failed", new RuntimeException()));

                // When
                Map<String, Object> result = totpFirestoreService.getBackupCodesInfo("user123");

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should return null when document is null in getTotpSecret")
            void shouldReturnNullWhenDocumentIsNullInGetTotpSecret() throws Exception {
                // Given
                when(documentReference.get()).thenReturn(documentSnapshotFuture);
                when(documentSnapshotFuture.get()).thenReturn(documentSnapshot);
                when(documentSnapshot.exists()).thenReturn(true);
                when(documentSnapshot.toObject(TotpSecretDocument.class)).thenReturn(null);

                // When
                String result = totpFirestoreService.getTotpSecret("user123");

                // Then
                assertThat(result).isNull();
            }
        }
    }
}
