package com.sm.instagram.platform.auth.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling Firebase authentication operations.
 * Centralizes all Firebase related functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseService {

    private static final String IDENTITY_TOOLKIT_SCOPE = "https://www.googleapis.com/auth/identitytoolkit";

    private final FirebaseAuth firebaseAuth;
    private final RestTemplate restTemplate;
    private final GoogleCredentialsProvider credentialsProvider;
    // Resolves the Identity Toolkit base and the bearer token: production, or the emulator in a test run.
    private final com.sm.instagram.platform.common.firebase.FirebaseEmulator firebaseEmulator;

    /**
     * Checks if a Firebase user exists by ID
     *
     * @param uid Firebase user ID
     * @return UserRecord if the user exists
     * @throws ExternalServiceException if the user does not exist or Firebase fails
     */
    public UserRecord getUserById(String uid) {
        log.info("GDPR: Service=getUserById, Operation=READ_USER, FirebaseUID={}, Purpose=authentication", uid);
        
        try {
            return firebaseAuth.getUser(uid);
        } catch (FirebaseAuthException e) {
            // Debug logging for external service troubleshooting
            log.debug("Firebase getUserById failed [uid={}, errorCode={}]: {}",
                    uid, e.getErrorCode(), e.getMessage());

            throw new ExternalServiceException(
                    "Failed to retrieve user from Firebase",
                    "Firebase",
                    "getUserById",
                    e
            );
        }
    }

    /**
     * Retrieves a user from Firebase by their email.
     *
     * @param email The user's email
     * @return UserRecord if the user exists
     * @throws ExternalServiceException if the user does not exist or Firebase fails
     */
    public UserRecord getUserByEmail(String email) {
        try {
            return firebaseAuth.getUserByEmail(email);
        } catch (FirebaseAuthException e) {
            // Debug logging for external service troubleshooting
            log.debug("Firebase getUserByEmail failed [email={}, errorCode={}]: {}",
                    email, e.getErrorCode(), e.getMessage());

            throw new ExternalServiceException(
                    "Failed to retrieve user by email from Firebase",
                    "Firebase",
                    "getUserByEmail",
                    e
            );
        }
    }

    /**
     * Creates a new Firebase user or validates an existing one.
     *
     * @param email       User's email
     * @param password    User's password (only used for creation, not validation)
     * @param displayName User's display name
     * @return Map containing user details including the UID
     */
    public Map<String, Object> createOrValidateFirebaseUser(String email, String password, String displayName, Permission userType) {
        log.info("GDPR: Service=createOrValidateUser, Operation=USER_AUTH, Email={}, Purpose=account_management", 
            email != null && email.length() > 3 ? email.substring(0, 3) + "***" : "***");
        
        try {
            // First, try to get the user by email
            UserRecord userRecord = firebaseAuth.getUserByEmail(email);
            log.debug("Found existing Firebase user with email: {}", email);

            // Update display name if needed
            if (displayName != null && !displayName.equals(userRecord.getDisplayName())) {
                UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(userRecord.getUid())
                        .setDisplayName(displayName);
                userRecord = firebaseAuth.updateUser(updateRequest);
                log.debug("Updated display name for Firebase user: {}", email);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("uid", userRecord.getUid());
            result.put("email", userRecord.getEmail());
            result.put("displayName", userRecord.getDisplayName());
            result.put("existed", true);

            return result;
        } catch (FirebaseAuthException e) {
            log.debug("Firebase error [email={}, errorCode={}]: {}", email, e.getErrorCode(), e.getMessage());

            if (e.getErrorCode().toString().equals("NOT_FOUND")) {
                // Create new user
                try {
                    UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                            .setEmail(email)
                            .setPassword(password)
                            .setDisplayName(displayName)
                            .setEmailVerified(false);

                    UserRecord userRecord = firebaseAuth.createUser(request);
                    
                    // Don't set role claim yet - wait for admin activation
            Map<String, Object> claims = new HashMap<>();
            // NO ROLE CLAIM until admin activation
            claims.put("pendingActivation", true);
            claims.put("registeredAt", System.currentTimeMillis());
            firebaseAuth.setCustomUserClaims(userRecord.getUid(), claims);
            
            log.debug("Created new Firebase user with ID: {} (pending activation)", userRecord.getUid());

                    Map<String, Object> result = new HashMap<>();
                    result.put("uid", userRecord.getUid());
                    result.put("email", userRecord.getEmail());
                    result.put("displayName", userRecord.getDisplayName());
                    result.put("existed", false);

                    return result;
                } catch (FirebaseAuthException createError) {
                    log.debug("Failed to create Firebase user [email={}, errorCode={}]: {}",
                            email, createError.getErrorCode(), createError.getMessage());

                    // Provide more user-friendly error messages
                    String errorCode = createError.getErrorCode().toString();
                    String errorMessage;

                    if (errorCode.equals("EMAIL_EXISTS")) {
                        errorMessage = "An account with this email already exists";
                    } else if (errorCode.equals("INVALID_EMAIL")) {
                        errorMessage = "The email address is invalid";
                    } else if (errorCode.equals("WEAK_PASSWORD")) {
                        errorMessage = "The password is too weak";
                    } else {
                        errorMessage = "Failed to create user account: " + createError.getMessage();
                    }

                    throw new ExternalServiceException(errorMessage, "Firebase", "createUser", createError);
                }
            } else if (e.getErrorCode().toString().equals("EMAIL_EXISTS")) {
                throw new ExternalServiceException("An account with this email already exists", "Firebase", "getUserByEmail", e);
            } else {
                throw new ExternalServiceException("Authentication error: " + e.getMessage(), "Firebase", "createOrValidateUser", e);
            }
        }
    }

    /**
     * Creates a Firebase user with social credentials only (no password).
     *
     * @param email       User's email
     * @param displayName User's display name
     * @param provider    Social provider name (e.g., "instagram")
     * @param socialId    Social user ID
     * @return Map containing user details
     */
    public Map<String, Object> createSocialOnlyFirebaseUser(String email, String displayName,
                                                            String provider, String socialId) {
        try {
            // Create user with email but no password
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setDisplayName(displayName)
                    .setEmailVerified(true); // Auto-verify email for social users

            UserRecord userRecord = firebaseAuth.createUser(request);
            log.debug("Created new Firebase social user with ID: {}", userRecord.getUid());

            // Set claims WITHOUT role - wait for admin activation
            Map<String, Object> claims = new HashMap<>();
            // NO ROLE CLAIM until admin activation
            claims.put("provider", provider);
            claims.put("socialId", socialId);
            claims.put("pendingActivation", true);
            firebaseAuth.setCustomUserClaims(userRecord.getUid(), claims);

            Map<String, Object> result = new HashMap<>();
            result.put("uid", userRecord.getUid());
            result.put("email", userRecord.getEmail());
            result.put("displayName", userRecord.getDisplayName());
            result.put("provider", provider);
            result.put("existed", false);

            return result;
        } catch (FirebaseAuthException e) {
            log.debug("Failed to create Firebase social user [email={}, provider={}, errorCode={}]: {}",
                    email, provider, e.getErrorCode(), e.getMessage());

            // Check error code as string
            String errorCode = e.getErrorCode().toString();
            String errorMessage;

            if (errorCode.equals("EMAIL_EXISTS")) {
                errorMessage = "An account with this email already exists. Please sign in instead.";
            } else if (errorCode.equals("UID_ALREADY_EXISTS") ||
                    e.getMessage().contains("DUPLICATE_LOCAL_ID")) {
                errorMessage = "This social account is already registered. Please sign in instead.";
            } else if (errorCode.equals("INVALID_EMAIL")) {
                errorMessage = "The email address is invalid";
            } else {
                errorMessage = "Failed to create user account: " + e.getMessage();
            }

            throw new ExternalServiceException(errorMessage, "Firebase", "createSocialUser", e);
        }
    }

    /**
     * Generates a custom token for Firebase authentication.
     *
     * @param uid Firebase user ID
     * @return Custom authentication token
     */
    public String generateCustomToken(String uid) {
        try {
            String token = firebaseAuth.createCustomToken(uid);
            log.debug("Successfully created custom token for user: {}", uid);
            return token;
        } catch (FirebaseAuthException e) {
            log.debug("Failed to create custom token [uid={}, errorCode={}]: {}",
                    uid, e.getErrorCode(), e.getMessage());

            throw new ExternalServiceException("Failed to generate authentication token", "Firebase", "createCustomToken", e);
        }
    }

    /**
     * Generates a custom token with claims for Firebase authentication.
     *
     * @param uid    Firebase user ID
     * @param claims Map of custom claims
     * @return Custom authentication token with claims
     */
    public String generateCustomTokenWithClaims(String uid, Map<String, Object> claims) {
        try {
            return firebaseAuth.createCustomToken(uid, claims);
        } catch (FirebaseAuthException e) {
            log.debug("Failed to create custom token with claims [uid={}, errorCode={}]: {}",
                    uid, e.getErrorCode(), e.getMessage());

            throw new ExternalServiceException("Failed to generate custom token with claims", "Firebase", "createCustomTokenWithClaims", e);
        }
    }

    /**
     * Sets custom user claims in Firebase.
     *
     * @param uid         Firebase user ID
     * @param permissions List of permissions to set
     */
    public void setUserClaims(String uid, List<Permission> permissions) {
        log.info("GDPR: Service=setUserClaims, Operation=UPDATE_PERMISSIONS, FirebaseUID={}, PermissionCount={}, Purpose=access_control", 
            uid, permissions.size());
        
        try {
            String role = permissions.isEmpty() ? "USER" : permissions.get(0).toString();
            
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", role);
            
            // Store additional permissions if needed
            if (permissions.size() > 1) {
                List<String> permissionStrings = permissions.stream()
                        .map(Enum::toString)
                        .toList();
                claims.put("permissions", permissionStrings);
            }
            
            firebaseAuth.setCustomUserClaims(uid, claims);
            log.debug("Set custom claims for user [uid={}, role={}, permissions={}]", uid, role, permissions);
        } catch (FirebaseAuthException e) {
            log.debug("Failed to set user claims [uid={}, errorCode={}]: {}",
                    uid, e.getErrorCode(), e.getMessage());

            throw new ExternalServiceException("Failed to set user permissions", "Firebase", "setCustomUserClaims", e);
        }
    }

    /**
     * Deletes a Firebase user.
     *
     * @param uid Firebase user ID
     */
    public void deleteUser(String uid) {
        log.warn("GDPR: DELETION Service=deleteUser, Operation=DELETE_USER, FirebaseUID={}, Purpose=account_termination", uid);

        try {
            firebaseAuth.deleteUser(uid);
            log.info("GDPR: DELETION_COMPLETE FirebaseUID={}, Status=deleted", uid);
        } catch (FirebaseAuthException e) {
            log.debug("Failed to delete Firebase user [uid={}, errorCode={}]: {}",
                    uid, e.getErrorCode(), e.getMessage());

            throw new ExternalServiceException("Failed to delete user account", "Firebase", "deleteUser", e);
        }
    }

    /**
     * Deletes a Firebase user, handling "user not found" gracefully.
     * Used by cascade delete operations where the user may have been manually deleted from Firebase.
     *
     * @param uid Firebase user ID
     * @return DeleteResult indicating SUCCESS (deleted), SKIPPED (not found), or FAILED (error)
     */
    public DeleteResult deleteUserGraceful(String uid) {
        log.warn("GDPR: DELETION Service=deleteUserGraceful, Operation=DELETE_USER, FirebaseUID={}, Purpose=cascade_delete", uid);

        if (uid == null || uid.trim().isEmpty()) {
            log.warn("Cannot delete Firebase user: UID is null or empty");
            return DeleteResult.SKIPPED;
        }

        try {
            firebaseAuth.deleteUser(uid);
            log.info("GDPR: DELETION_COMPLETE FirebaseUID={}, Status=deleted", uid);
            return DeleteResult.SUCCESS;
        } catch (FirebaseAuthException e) {
            String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString() : "UNKNOWN";

            // Handle "user not found" gracefully - this is expected in cascade delete scenarios
            if ("NOT_FOUND".equals(errorCode) || "USER_NOT_FOUND".equals(errorCode)
                    || (e.getMessage() != null && e.getMessage().contains("No user record found"))) {
                log.info("GDPR: Service=deleteUserGraceful_notFound, Operation=DELETE_SKIP, FirebaseUID={}, Reason=user_not_found", uid);
                return DeleteResult.SKIPPED;
            }

            log.error("Failed to delete Firebase user [uid={}, errorCode={}]: {}",
                    uid, errorCode, e.getMessage());
            return DeleteResult.FAILED;
        }
    }

    /**
     * Checks if a Firebase user exists by UID.
     * Used for cascade delete preview to determine if Firebase cleanup is needed.
     *
     * @param uid Firebase user ID
     * @return true if user exists in Firebase Auth, false otherwise
     */
    public boolean userExists(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return false;
        }

        try {
            firebaseAuth.getUser(uid);
            return true;
        } catch (FirebaseAuthException e) {
            String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString() : "UNKNOWN";
            if ("NOT_FOUND".equals(errorCode) || "USER_NOT_FOUND".equals(errorCode)) {
                return false;
            }
            // Log unexpected errors but return false to be safe
            log.error("Error checking Firebase user existence [uid={}, errorCode={}]: {}",
                    uid, errorCode, e.getMessage());
            return false;
        }
    }

    /**
     * Result of a graceful delete operation.
     */
    public enum DeleteResult {
        /** User was successfully deleted. */
        SUCCESS,
        /** User was not found (already deleted or never existed). */
        SKIPPED,
        /** An error occurred during deletion. */
        FAILED
    }

    /**
     * Creates a Firebase user for Instagram OAuth without email.
     * Uses auto-generated Firebase UID and no identifier (email will be added later).
     * 
     * NOTE: Does NOT set role claim - this is set only after admin activation
     * 
     * @param username Instagram username for display name
     * @param socialId Instagram numeric ID for custom claims
     * @return Map containing Firebase UID and user details
     */
    public Map<String, Object> createInstagramFirebaseUser(String username, String socialId) {
        try {
            log.debug("Creating Firebase user for Instagram: {}", username);
            
            // Create user without email - Firebase will auto-generate UID
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setDisplayName(username);
            // NO .setEmail() - intentionally blank
            // NO .setUid() - let Firebase auto-generate
            
            UserRecord userRecord = firebaseAuth.createUser(request);
            String firebaseUid = userRecord.getUid();
            
            log.info("Created Firebase user with auto-generated UID: {}", firebaseUid);
            
            // Set MINIMAL custom claims - NO ROLE until admin activation
            Map<String, Object> claims = new HashMap<>();
            // NO ROLE CLAIM - will be set by admin during activation
            claims.put("provider", "instagram");
            claims.put("instagramId", socialId);
            claims.put("instagramUsername", username);
            claims.put("emailVerified", false); // Will be true after validation
            claims.put("pendingActivation", true); // Flag for pending admin activation
            
            firebaseAuth.setCustomUserClaims(firebaseUid, claims);
            
            Map<String, Object> result = new HashMap<>();
            result.put("uid", firebaseUid);
            result.put("displayName", username);
            result.put("socialId", socialId);
            result.put("provider", "instagram");
            
            return result;
        } catch (FirebaseAuthException e) {
            log.error("Failed to create Firebase user for Instagram", e);
            throw new ExternalServiceException(
                "Failed to create Firebase user", 
                "Firebase", 
                "createInstagramUser", 
                e
            );
        }
    }
    
    /**
     * Updates Firebase user email via REST API (accounts:update with localId).
     * Uses the Identity Toolkit REST API instead of Admin SDK to ensure
     * the password provider's Identifier (federatedId) is updated in sync.
     *
     * @param firebaseUid Firebase user ID
     * @param newEmail    New email address
     * @param oldEmail    Old email address (for logging)
     */
    public void updateFirebaseUserEmail(String firebaseUid, String newEmail, String oldEmail) {
        String url = firebaseEmulator.identityToolkitBase() + "/accounts:update";

        Map<String, Object> body = new HashMap<>();
        body.put("localId", firebaseUid);
        body.put("email", newEmail);
        body.put("emailVerified", false);
        body.put("returnSecureToken", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getIdentityToolkitAccessToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // Block 1: REST API call (critical - throw on failure, triggers PG rollback)
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            // Verify Identifier updated in response
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("providerUserInfo")) {
                @SuppressWarnings("unchecked")
                var providerList = (List<Map<String, Object>>) responseBody.get("providerUserInfo");
                for (var provider : providerList) {
                    if ("password".equals(provider.get("providerId"))) {
                        String federatedId = (String) provider.get("federatedId");
                        if (!newEmail.equals(federatedId)) {
                            log.warn("IDENTIFIER_STALE: REST API providerUserInfo not updated! expected={}, federatedId={}, uid={}",
                                maskEmail(newEmail), maskEmail(federatedId), firebaseUid);
                        } else {
                            log.info("Firebase Identifier updated via REST API: {}", maskEmail(newEmail));
                        }
                        break;
                    }
                }
            }

            log.info("Firebase email updated via REST API for uid {}: {} -> {}",
                    firebaseUid, maskEmail(oldEmail), maskEmail(newEmail));
        } catch (Exception e) {
            log.error("Failed to update Firebase user email via REST API: {}", e.getMessage());
            throw new ExternalServiceException(
                "Failed to update user email in Firebase",
                "Firebase",
                "accounts:update",
                e
            );
        }

        // Block 2: Claims update (non-critical - log on failure, don't throw)
        try {
            UserRecord user = firebaseAuth.getUser(firebaseUid);
            Map<String, Object> claims = new HashMap<>(user.getCustomClaims());
            claims.put("emailProvided", true);
            firebaseAuth.setCustomUserClaims(firebaseUid, claims);
        } catch (Exception e) {
            log.warn("Non-critical: Failed to set emailProvided claim for uid {}: {}",
                    firebaseUid, e.getMessage());
        }
    }

    /**
     * Backward-compatible overload (no oldEmail for logging).
     */
    public void updateFirebaseUserEmail(String firebaseUid, String email) {
        updateFirebaseUserEmail(firebaseUid, email, null);
    }

    private String getIdentityToolkitAccessToken() {
        // See FirebaseAuthProxyService.getAccessToken: the emulator takes one fixed owner token.
        String emulatorToken = firebaseEmulator.bearerTokenOrEmpty();
        if (!emulatorToken.isEmpty()) {
            return emulatorToken;
        }
        try {
            GoogleCredentials credentials = credentialsProvider.getCredentialsWithScopes(IDENTITY_TOOLKIT_SCOPE);
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            log.error("Failed to obtain OAuth access token for Identity Toolkit", e);
            throw new ExternalServiceException(
                "Failed to obtain Firebase access token",
                "Firebase",
                "getAccessToken",
                e
            );
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        if (local.length() <= 2) return local + "@***";
        return local.substring(0, 2) + "***@" + parts[1];
    }

    /**
     * Verifies an ID token from Firebase.
     *
     * @param idToken Firebase ID token
     * @return Decoded token
     */
    public FirebaseToken verifyIdToken(String idToken) {
        try {
            return firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            log.debug("Failed to verify ID token [errorCode={}]: {}", e.getErrorCode(), e.getMessage());

            throw new ExternalServiceException("Invalid authentication token", "Firebase", "verifyIdToken", e);
        }
    }
}
