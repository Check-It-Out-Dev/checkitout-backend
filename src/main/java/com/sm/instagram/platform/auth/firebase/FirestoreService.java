package com.sm.instagram.platform.auth.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.sm.instagram.model.firestore.InstagramUserDocument;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.security.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for handling Firestore operations, specifically for storing Instagram user data.
 * This preserves the legacy behavior of storing all Instagram data including access tokens.
 *
 * Uses injected Firestore bean instead of FirestoreClient.getFirestore()
 * to ensure proper Spring lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirestoreService {

    private static final String INSTAGRAM_USERS_COLLECTION = "instagramUsers";

    private final Firestore firestore;
    private final TokenEncryptionService tokenEncryptionService;

    /**
     * Stores Instagram user data in Firestore using type-safe model.
     * Uses Firebase UID as document ID for direct access.
     *
     * @param instagramData The complete Instagram user data including access token
     * @param firebaseUserId The Firebase user ID (used as document ID)
     */
    public void storeInstagramUserData(Map<String, Object> instagramData, String firebaseUserId) {
        if (instagramData == null || !instagramData.containsKey("user_id")) {
            throw new ValidationTranslatableException("error.validation.required_field", "user_id");
        }
        
        if (firebaseUserId == null || firebaseUserId.isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "Firebase user ID");
        }
        
        String instagramUserId = String.valueOf(instagramData.get("user_id"));
        log.info("GDPR: Service=storeInstagramUserData, Operation=STORE_SOCIAL_DATA, FirebaseUID={}, InstagramID={}, DataFields=profile,permissions,access_token, Purpose=social_integration", 
            firebaseUserId, instagramUserId);

        try {
            // Using injected Firestore bean
            
            // Create type-safe model instead of HashMap
            InstagramUserDocument document = buildInstagramUserDocument(instagramData, firebaseUserId);
            
            // Store in Firestore using Firebase UID as document ID for direct access
            DocumentReference docRef = firestore.collection(INSTAGRAM_USERS_COLLECTION).document(firebaseUserId);
            ApiFuture<WriteResult> future = docRef.set(document);
            
            // Wait for the write to complete
            WriteResult result = future.get();
            log.info("GDPR: Service=storeInstagramUserData_complete, Operation=STORE_COMPLETE, FirebaseUID={}, InstagramID={}, Timestamp={}, Purpose=social_data_persistence", 
                    firebaseUserId, instagramUserId, result.getUpdateTime());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while storing Instagram data in Firestore: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (ExecutionException e) {
            log.error("Failed to store Instagram data in Firestore: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (Exception e) {
            log.error("Unexpected error storing Instagram data: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        }
    }

    /**
     * Builds a type-safe InstagramUserDocument from the raw Instagram API response
     * 
     * @param instagramData Raw data from Instagram API
     * @param firebaseUserId Firebase user ID for linking
     * @return Type-safe InstagramUserDocument
     */
    private InstagramUserDocument buildInstagramUserDocument(Map<String, Object> instagramData, String firebaseUserId) {
        InstagramUserDocument.InstagramUserDocumentBuilder builder = InstagramUserDocument.builder();
        
        // Required fields
        String instagramUserId = String.valueOf(instagramData.get("user_id"));
        builder.user_id(instagramUserId);  // Store Instagram ID as a field
        builder.firebaseUserId(firebaseUserId);  // Firebase UID (also the document ID)
        builder.lastUpdated(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        builder.provider("instagram");
        
        // Optional fields with null-safe handling
        if (instagramData.containsKey("access_token")) {
            String accessToken = (String) instagramData.get("access_token");
            // Encrypt token before storing
            String encryptedToken = tokenEncryptionService.encryptToken(accessToken);
            builder.access_token(encryptedToken);
            log.debug("GDPR: Service=encryptToken, Operation=ENCRYPT_SENSITIVE_DATA, FirebaseUID={}, DataType=access_token, Purpose=security", firebaseUserId);
        }
        
        if (instagramData.containsKey("username")) {
            builder.username((String) instagramData.get("username"));
        }
        
        if (instagramData.containsKey("profile_picture_url")) {
            builder.profile_picture_url((String) instagramData.get("profile_picture_url"));
        }
        
        if (instagramData.containsKey("followers_count")) {
            Object followersObj = instagramData.get("followers_count");
            if (followersObj instanceof Number) {
                builder.followers_count(((Number) followersObj).longValue());
            }
        }
        
        // Handle permissions array
        if (instagramData.containsKey("permissions")) {
            Object permsObj = instagramData.get("permissions");
            if (permsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> permissions = (List<String>) permsObj;
                builder.permissions(permissions);
            } else {
                builder.permissions(new ArrayList<>());
            }
        } else {
            builder.permissions(new ArrayList<>());
        }
        
        // Handle nested user object
        if (instagramData.containsKey("user") && instagramData.get("user") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userData = (Map<String, Object>) instagramData.get("user");
            
            InstagramUserDocument.InstagramUserData.InstagramUserDataBuilder userBuilder = 
                InstagramUserDocument.InstagramUserData.builder();
            
            if (userData.containsKey("account_type")) {
                userBuilder.account_type((String) userData.get("account_type"));
            }
            
            if (userData.containsKey("id")) {
                userBuilder.id(String.valueOf(userData.get("id")));
            }
            
            if (userData.containsKey("username")) {
                userBuilder.username((String) userData.get("username"));
            }
            
            if (userData.containsKey("followers_count")) {
                Object followersObj = userData.get("followers_count");
                if (followersObj instanceof Number) {
                    userBuilder.followers_count(((Number) followersObj).longValue());
                }
            }
            
            if (userData.containsKey("media_count")) {
                Object mediaObj = userData.get("media_count");
                if (mediaObj instanceof Number) {
                    userBuilder.media_count(((Number) mediaObj).longValue());
                }
            }
            
            builder.user(userBuilder.build());
        }
        
        return builder.build();
    }

    /**
     * @deprecated This method is no longer used. We store all Instagram data in the instagramUsers collection.
     * The socialConnections collection was redundant as instagramUsers already contains all necessary data.
     * 
     * Stores a social connection record in Firestore.
     * This creates a more structured record linking Firebase users to their social accounts.
     *
     * @param firebaseUserId The Firebase user ID
     * @param platform The social platform name
     * @param socialUserId The social platform user ID
     * @param socialData Additional social data to store
     */
    @Deprecated
    public void storeSocialConnection(String firebaseUserId, String platform, 
                                     String socialUserId, Map<String, Object> socialData) {
        // This method is deprecated and should not be used
        // All Instagram data is now stored only in the instagramUsers collection
        log.warn("storeSocialConnection is deprecated. Use storeInstagramUserData instead.");
        
        // Original implementation commented out to prevent accidental usage
        /*
        try {
            // Using injected Firestore bean
            
            Map<String, Object> connectionData = new HashMap<>();
            connectionData.put("firebaseUserId", firebaseUserId);
            connectionData.put("platform", platform);
            connectionData.put("socialUserId", socialUserId);
            connectionData.put("socialData", socialData);
            connectionData.put("createdAt", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            connectionData.put("lastSynced", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            
            // Create document ID as combination of firebase user and platform
            String documentId = firebaseUserId + "_" + platform;
            
            DocumentReference docRef = firestore.collection(SOCIAL_CONNECTIONS_COLLECTION).document(documentId);
            ApiFuture<WriteResult> future = docRef.set(connectionData);
            
            WriteResult result = future.get();
            log.info("Successfully stored social connection for user: {} platform: {} at time: {}", 
                    firebaseUserId, platform, result.getUpdateTime());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while storing social connection: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (ExecutionException e) {
            log.error("Failed to store social connection in Firestore: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (Exception e) {
            log.error("Unexpected error storing social connection: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        }
        */
    }

    /**
     * Updates the access token and timestamp for an existing Instagram user in Firestore.
     * Used when refreshing expired or near-expiry tokens.
     *
     * @param firebaseUid The Firebase user ID (document ID)
     * @param newAccessToken The new access token to store (will be encrypted)
     * @param tokenCreatedAt Timestamp when the new token was created (epoch seconds)
     */
    public void updateAccessToken(String firebaseUid, String newAccessToken, long tokenCreatedAt) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "Firebase UID");
        }

        if (newAccessToken == null || newAccessToken.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "Access Token");
        }

        log.info("GDPR: Service=updateAccessToken, Operation=UPDATE_TOKEN, FirebaseUID={}, Purpose=token_refresh", firebaseUid);

        try {
            // Using injected Firestore bean
            DocumentReference docRef = firestore.collection(INSTAGRAM_USERS_COLLECTION).document(firebaseUid);

            // Encrypt the new token before storing
            String encryptedToken = tokenEncryptionService.encryptToken(newAccessToken);

            // Update only the token and timestamp fields
            Map<String, Object> updates = new HashMap<>();
            updates.put("access_token", encryptedToken);
            updates.put("lastUpdated", tokenCreatedAt);
            updates.put("tokenRefreshedAt", tokenCreatedAt);

            ApiFuture<WriteResult> future = docRef.update(updates);
            WriteResult result = future.get();

            log.info("GDPR: Service=updateAccessToken_complete, Operation=TOKEN_UPDATED, FirebaseUID={}, Timestamp={}, Purpose=token_refresh",
                    firebaseUid, result.getUpdateTime());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while updating access token: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (ExecutionException e) {
            log.error("Failed to update access token in Firestore: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (Exception e) {
            log.error("Unexpected error updating access token: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        }
    }

    /**
     * Gets the decrypted access token for a user.
     *
     * @param firebaseUid The Firebase user ID
     * @return Decrypted access token, or null if not found
     */
    public String getDecryptedAccessToken(String firebaseUid) {
        Map<String, Object> data = getInstagramUserDataByFirebaseUid(firebaseUid);
        if (data != null && data.containsKey("access_token")) {
            return (String) data.get("access_token"); // Already decrypted by getInstagramUserDataByFirebaseUid
        }
        return null;
    }

    /**
     * Retrieves Instagram user data from Firestore by Firebase UID.
     * Direct document access using Firebase UID as document ID.
     *
     * @param firebaseUid The Firebase user ID (document ID)
     * @return Map containing the stored Instagram data, or null if not found
     */
    public Map<String, Object> getInstagramUserDataByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "Firebase UID");
        }

        log.info("GDPR: Service=getInstagramUserDataByFirebaseUid, Operation=READ_SOCIAL_DATA, FirebaseUID={}, Purpose=oauth_validation", firebaseUid);
        
        try {
            // Using injected Firestore bean
            
            // Direct document access using Firebase UID as document ID - NO QUERY NEEDED!
            DocumentReference docRef = firestore.collection(INSTAGRAM_USERS_COLLECTION).document(firebaseUid);
            ApiFuture<com.google.cloud.firestore.DocumentSnapshot> future = docRef.get();
            com.google.cloud.firestore.DocumentSnapshot document = future.get();
            
            if (document.exists()) {
                log.info("GDPR: Service=getInstagramUserDataByFirebaseUid_found, Operation=READ_SUCCESS, FirebaseUID={}, Purpose=oauth_validation", firebaseUid);
                
                Map<String, Object> data = document.getData();
                
                // The Instagram ID is already in the data as 'user_id' field
                // Just add it as 'instagramId' for consistency in TokenExchangeService
                if (data.containsKey("user_id")) {
                    data.put("instagramId", data.get("user_id"));
                }
                
                // Decrypt access token if present
                if (data.containsKey("access_token")) {
                    String encryptedToken = (String) data.get("access_token");
                    if (encryptedToken != null) {
                        try {
                            String decryptedToken = tokenEncryptionService.decryptToken(encryptedToken);
                            data.put("access_token", decryptedToken);
                            // GDPR: Don't log the token itself, just the operation
                            log.debug("GDPR: Service=decryptToken, Operation=DECRYPT_SENSITIVE_DATA, FirebaseUID={}, DataType=access_token, Purpose=token_validation", firebaseUid);
                        } catch (Exception e) {
                            log.error("GDPR: Service=decryptToken_failed, Operation=DECRYPT_ERROR, FirebaseUID={}, Error={}", firebaseUid, e.getMessage());
                            // Remove the token if decryption fails
                            data.remove("access_token");
                        }
                    }
                }
                
                return data;
            } else {
                log.info("GDPR: Service=getInstagramUserDataByFirebaseUid_notFound, Operation=READ_EMPTY, FirebaseUID={}, Purpose=oauth_validation", firebaseUid);
                return null;
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while retrieving Instagram data by Firebase UID: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (ExecutionException e) {
            log.error("Failed to retrieve Instagram data from Firestore by Firebase UID: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (Exception e) {
            log.error("Unexpected error retrieving Instagram data by Firebase UID: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        }
    }
    
    /**
     * Checks if Instagram user data exists in Firestore for a given Firebase UID.
     *
     * @param firebaseUid The Firebase user ID
     * @return true if data exists, false otherwise
     */
    public boolean existsInstagramUserData(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            return false;
        }

        try {
            DocumentReference docRef = firestore.collection(INSTAGRAM_USERS_COLLECTION).document(firebaseUid);
            ApiFuture<com.google.cloud.firestore.DocumentSnapshot> future = docRef.get();
            com.google.cloud.firestore.DocumentSnapshot document = future.get();
            return document.exists();
        } catch (Exception e) {
            log.error("Error checking Instagram data existence for user {}: {}", firebaseUid, e.getMessage());
            return false;
        }
    }

    /**
     * Deletes Instagram user data from Firestore for cascade delete operations.
     * Used during user account deletion to clean up Firestore data.
     *
     * @param firebaseUid The Firebase user ID (document ID in instagramUsers collection)
     * @return true if deleted successfully or didn't exist, false if error occurred
     */
    public boolean deleteInstagramUserData(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            log.warn("Cannot delete Instagram data: Firebase UID is null or empty");
            return false;
        }

        log.warn("GDPR: DELETION Service=deleteInstagramUserData, Operation=DELETE_SOCIAL_DATA, FirebaseUID={}, Purpose=account_deletion", firebaseUid);

        try {
            DocumentReference docRef = firestore.collection(INSTAGRAM_USERS_COLLECTION).document(firebaseUid);

            // Check if document exists first
            ApiFuture<com.google.cloud.firestore.DocumentSnapshot> getFuture = docRef.get();
            com.google.cloud.firestore.DocumentSnapshot document = getFuture.get();

            if (!document.exists()) {
                log.info("GDPR: Service=deleteInstagramUserData_notFound, Operation=DELETE_SKIP, FirebaseUID={}, Reason=document_not_found", firebaseUid);
                return true; // Nothing to delete, consider it success
            }

            // Delete the document
            ApiFuture<WriteResult> deleteFuture = docRef.delete();
            WriteResult result = deleteFuture.get();

            log.info("GDPR: DELETION_COMPLETE Service=deleteInstagramUserData, FirebaseUID={}, DeletedAt={}",
                    firebaseUid, result.getUpdateTime());
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while deleting Instagram data for user {}: {}", firebaseUid, e.getMessage());
            return false;
        } catch (ExecutionException e) {
            log.error("Failed to delete Instagram data from Firestore for user {}: {}", firebaseUid, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error deleting Instagram data for user {}: {}", firebaseUid, e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves Instagram user data from Firestore by Instagram user ID.
     *
     * @param instagramUserId The Instagram user ID
     * @return Map containing the stored Instagram data, or null if not found
     */
    public Map<String, Object> getInstagramUserData(String instagramUserId) {
        if (instagramUserId == null || instagramUserId.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "Instagram User ID");
        }
        
        log.info("GDPR: Service=getInstagramUserData, Operation=READ_SOCIAL_DATA, InstagramID={}, Purpose=social_data_retrieval", instagramUserId);
        
        try {
            // Using injected Firestore bean
            DocumentReference docRef = firestore.collection(INSTAGRAM_USERS_COLLECTION).document(instagramUserId);
            
            ApiFuture<com.google.cloud.firestore.DocumentSnapshot> future = docRef.get();
            com.google.cloud.firestore.DocumentSnapshot document = future.get();
            
            if (document.exists()) {
                log.info("GDPR: Service=getInstagramUserData_found, Operation=READ_SUCCESS, InstagramID={}, Purpose=social_data_retrieval", instagramUserId);
                Map<String, Object> data = document.getData();
                
                // Decrypt access token if present
                if (data != null && data.containsKey("access_token")) {
                    String encryptedToken = (String) data.get("access_token");
                    if (encryptedToken != null) {
                        try {
                            String decryptedToken = tokenEncryptionService.decryptToken(encryptedToken);
                            data.put("access_token", decryptedToken);
                            log.debug("GDPR: Service=decryptToken, Operation=DECRYPT_SENSITIVE_DATA, InstagramID={}, DataType=access_token, Purpose=data_access", instagramUserId);
                        } catch (Exception e) {
                            log.error("GDPR: Service=decryptToken_failed, Operation=DECRYPT_ERROR, InstagramID={}, Error={}", instagramUserId, e.getMessage());
                            // Remove the token if decryption fails
                            data.remove("access_token");
                        }
                    }
                }
                
                return data;
            } else {
                log.info("GDPR: Service=getInstagramUserData_notFound, Operation=READ_EMPTY, InstagramID={}, Purpose=social_data_retrieval", instagramUserId);
                return null;
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while retrieving Instagram data: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (ExecutionException e) {
            log.error("Failed to retrieve Instagram data from Firestore: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        } catch (Exception e) {
            log.error("Unexpected error retrieving Instagram data: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Firestore");
        }
    }
}