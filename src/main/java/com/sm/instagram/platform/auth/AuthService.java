package com.sm.instagram.platform.auth;

import static com.sm.instagram.platform.common.util.PiiMaskingUtils.maskEmail;

import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.auth.dto.*;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.social.SocialPlatformFactory;
import com.sm.instagram.platform.auth.social.SocialPlatformService;
import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central service for handling authentication, user registration and social media connections.
 * This service orchestrates the integration between Firebase and our backend database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final FirebaseService firebaseService;
    private final FirestoreService firestoreService;
    private final UserRepository userRepository;
    private final UserSocialConnectionRepository socialConnectionRepository;
    private final SocialPlatformFactory socialPlatformFactory;
    private final AddressRepository addressRepository;
    private final PlatformRepository platformRepository;
    private final PermissionUtils permissionUtils;

    /**
     * Handles social media sign-in by processing the authentication code and returning a Firebase token.
     * If the user doesn't exist in the system yet, the response indicates this so the frontend can
     * proceed with registration.
     *
     * @param request The social sign-in request with platform and auth code
     * @return SocialSignInResponse with authentication details
     */
    @Transactional
    public SocialSignInResponse socialSignIn(SocialSignInRequest request) {
        // GDPR: Log social sign-in attempt
        log.info("GDPR: Operation=socialSignIn, Platform={}, DataAccessed=social_profile, Purpose=authentication, LegalBasis=consent, ThirdParty={}",
            request != null ? request.getPlatformName() : "unknown",
            request != null ? request.getPlatformName() : "unknown");
        
        // Validate request
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getPlatformName() == null || request.getPlatformName().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.auth.platform_name_required");
        }
        if (request.getAuthCode() == null || request.getAuthCode().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.auth.auth_code_required");
        }

        log.info("Processing social sign-in for platform: {}", request.getPlatformName());

        SocialSignInResponse response = new SocialSignInResponse();

        // Get the appropriate social platform service
        SocialPlatformService socialService;
        try {
            socialService = socialPlatformFactory.getSocialService(request.getPlatformName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting social service: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.invalid_argument");
        }

        // Exchange auth code for user profile
        Map<String, Object> socialUserData;
        try {
            socialUserData = socialService.exchangeAuthCodeForProfile(request.getAuthCode());
        } catch (IllegalArgumentException e) {
            // Pass through IllegalArgumentException which will be handled by the exception handler
            throw e;
        } catch (Exception e) {
            log.error("Error exchanging auth code: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
        }

        // Validate social user data
        if (socialUserData == null || socialUserData.isEmpty()) {
            throw new AuthenticationTranslatableException("error.auth.service_unavailable");
        }
        if (!socialUserData.containsKey("user_id") || socialUserData.get("user_id") == null) {
            throw new AuthenticationTranslatableException("error.auth.service_unavailable");
        }

        String socialUserId = socialUserData.get("user_id").toString();
        if (socialUserId.trim().isEmpty()) {
            throw new AuthenticationTranslatableException("error.auth.service_unavailable");
        }
        log.info("Received social user ID: {}", socialUserId);

        // Normalize platform name for consistent lookup
        String normalizedPlatformName = request.getPlatformName().toLowerCase();
        String displayPlatformName = normalizedPlatformName.substring(0, 1).toUpperCase() +
                normalizedPlatformName.substring(1);

        log.info("Looking for social connection with platform: {} and socialUserId: {}",
                displayPlatformName, socialUserId);

        // Try to find connection with multiple approaches
        Optional<UserSocialConnection> connectionOpt = findSocialConnection(displayPlatformName, normalizedPlatformName, socialUserId);

        if (connectionOpt.isPresent()) {
            UserSocialConnection connection = connectionOpt.get();
            User user = connection.getUser();

            log.info("Found existing user: {} with Firebase ID: {}", user.getId(), user.getFirebaseUserId());

            // Verify Firebase user exists and generate token
            try {
                firebaseService.getUserById(user.getFirebaseUserId());
                
                // Create custom claims for the token
                Map<String, Object> claims = new HashMap<>();
                claims.put("role", user.getUserType().toString());
                claims.put("userId", user.getId().toString());
                claims.put("email", user.getEmail());
                
                String customToken = firebaseService.generateCustomTokenWithClaims(user.getFirebaseUserId(), claims);

                response.setSuccess(true);
                response.setUserExists(true);
                response.setFirebaseUserId(user.getFirebaseUserId());
                response.setCustomToken(customToken);
                response.setUserId(user.getId());
                
                // GDPR: Log successful social sign-in
                log.info("GDPR: SocialSignInSuccess, FirebaseUID={}, UserID={}, Platform={}, DataAccessed=user_profile,social_tokens, Purpose=authentication",
                    user.getFirebaseUserId(), user.getId(), request.getPlatformName());

                // Update connection data
                connection.setLastSyncTime(LocalDateTime.now());
                socialConnectionRepository.save(connection);

                // Update Instagram data in Firestore
                try {
                    firestoreService.storeInstagramUserData(socialUserData, user.getFirebaseUserId());
                    log.debug("Updated Instagram data in Firestore for existing user");
                } catch (Exception e) {
                    log.error("Failed to update Instagram data in Firestore: {}", e.getMessage());
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error generating token for user: {}", e.getMessage());
                throw new AuthenticationTranslatableException("error.auth.service_unavailable");
            }

            log.info("Social sign-in successful for existing user with ID: {}", user.getId());
        } else {
            // User doesn't exist yet - prepare response indicating registration needed
            log.info("No user found for social ID: {}. New registration required.", socialUserId);
            response.setSuccess(true);
            response.setUserExists(false);
            response.setSocialUserData(socialUserData);
        }

        return response;
    }

    /**
     * Helper method to find a social connection by platform name and user ID
     */
    private Optional<UserSocialConnection> findSocialConnection(String displayPlatformName, String normalizedPlatformName, String socialUserId) {
        // Try with display name format first
        Optional<UserSocialConnection> connectionOpt = socialConnectionRepository
                .findByPlatform_NameAndSocialUserId(displayPlatformName, socialUserId);

        if (!connectionOpt.isPresent()) {
            // Try with normalized name
            connectionOpt = socialConnectionRepository
                    .findByPlatform_NameAndSocialUserId(normalizedPlatformName, socialUserId);
        }

        if (!connectionOpt.isPresent()) {
            // Log platform info for debugging
            log.info("Connection not found by direct lookup. Checking platforms in DB...");
            List<Platform> platforms = platformRepository.findAll();
            for (Platform platform : platforms) {
                log.info("Found platform in DB: {} with ID: {}", platform.getName(), platform.getId());
            }

            // Try generic lookup by social user ID
            List<UserSocialConnection> connections = socialConnectionRepository.findBySocialUserId(socialUserId);
            if (!connections.isEmpty()) {
                log.info("Found {} connections with socialUserId: {}", connections.size(), socialUserId);
                connectionOpt = Optional.of(connections.get(0));
            }
        }

        return connectionOpt;
    }

    /**
     * Registers a new user and creates associated resources based on user type.
     * This method handles:
     * - Firebase user creation/validation
     * - Backend user creation
     * - Social connection setup (for INFLUENCER type)
     * - Address creation (for COMPANY type)
     *
     * @param request All required information for registration
     * @return RegisterUserResponse with user data and success status
     */
    @Transactional
    public RegisterUserResponse registerUser(RegisterUserRequest request) {
        // GDPR: Log registration attempt
        log.info("GDPR: Operation=registerUser, Email={}, UserType={}, DataAccessed=registration_data, Purpose=account_creation, LegalBasis=contract",
            request != null ? maskEmail(request.getEmail()) : "unknown",
            request != null ? request.getUserType() : "unknown");
        
        // Validate request
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }

        log.info("Processing registration for user with email: {}, type: {}",
                maskEmail(request.getEmail()), request.getUserType());

        // 1. Create or validate Firebase user
        Map<String, Object> firebaseUser;
        try {
            // Build display name, use email prefix if names are not provided
            String displayName;
            if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
                displayName = request.getFirstName();
                if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
                    displayName += " " + request.getLastName();
                }
            } else if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
                displayName = request.getLastName();
            } else {
                displayName = request.getEmail().split("@")[0]; // Use email prefix as display name
            }

            firebaseUser = firebaseService.createOrValidateFirebaseUser(
                    request.getEmail(),
                    request.getPassword(),
                    displayName,
                    Permission.valueOf(request.getUserType()));

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Firebase error during registration: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.auth.service_unavailable");
        }

        // Validate Firebase response
        if (firebaseUser == null || !firebaseUser.containsKey("uid")) {
            throw new AuthenticationTranslatableException("error.auth.service_unavailable");
        }

        String firebaseUserId = (String) firebaseUser.get("uid");
        if (firebaseUserId == null || firebaseUserId.trim().isEmpty()) {
            throw new AuthenticationTranslatableException("error.auth.service_unavailable");
        }

        // Check if the user already exists in our database
        checkUserDoesNotExist(firebaseUserId);

        try {
            // 2. Create backend user
            User user = new User();
            user.setFirebaseUserId(firebaseUserId);
            user.setEmail(request.getEmail());
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setUserType(UserType.valueOf(request.getUserType()));
            user.setAccountStatus(AccountStatus.IN_VALIDATION);
            user.setCreatedTime(LocalDateTime.now());
            user.setLastUpdateTime(LocalDateTime.now());

            // Set company-specific fields if applicable
            if (UserType.COMPANY.name().equals(request.getUserType())) {
                user.setName(request.getCompanyName());
                user.setPhoneNumber(request.getPhoneNumber());
                user.setProfilePicture(request.getProfilePictureUrl());
            }

            // Let DataIntegrityViolationException propagate to global handler (BusinessExceptionHandler)
            // which will map database errors (duplicate key, foreign key, etc.) to user-friendly messages
            User savedUser = userRepository.save(user);

            // 3. Create Address entity for COMPANY users
            if (UserType.COMPANY.name().equals(request.getUserType()) &&
                    StringUtils.hasText(request.getAddressStreet()) &&
                    StringUtils.hasText(request.getAddressCity()) &&
                    StringUtils.hasText(request.getAddressPostalCode())) {

                try {
                    createCompanyAddress(savedUser, request);
                    log.info("Created address for company user: {}", savedUser.getId());
                } catch (Exception e) {
                    log.error("Error creating company address: {}", e.getMessage());
                    throw new BusinessRuleTranslatableException("error.business.data_integrity");
                }
            }

            // 4. If this is an influencer and has social data, create the connection
            RegisterUserResponse response = new RegisterUserResponse();

            if (UserType.INFLUENCER.name().equals(request.getUserType()) && request.getSocialPlatform() != null) {
                try {
                    SocialConnectionRequest socialRequest = new SocialConnectionRequest();
                    socialRequest.setPlatformName(request.getSocialPlatform());
                    socialRequest.setAuthCode(request.getSocialAuthCode());

                    SocialConnectionResponse socialConnectionResponse = connectSocialPlatformForRegistration(
                            firebaseUserId, socialRequest);

                    response.setSocialConnection(socialConnectionResponse);
                } catch (Exception e) {
                    // Registration continues but we record the failure
                    response.setSocialConnectionError(e.getMessage());
                    log.warn("Social connection failed during registration: {}", e.getMessage());
                }
            }

            // Generate custom token for frontend authentication
            String customToken;
            try {
                Map<String, Object> claims = new HashMap<>();
                claims.put("role", savedUser.getUserType().toString());
                claims.put("userId", savedUser.getId().toString());
                claims.put("email", savedUser.getEmail());

                customToken = firebaseService.generateCustomTokenWithClaims(firebaseUserId, claims);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Token generation error: {}", e.getMessage());
                throw new AuthenticationTranslatableException("error.auth.service_unavailable");
            }

            response.setSuccess(true);
            response.setFirebaseUserId(firebaseUserId);
            response.setCustomToken(customToken);
            response.setUserId(savedUser.getId());

            // GDPR: Log successful registration
            log.info("GDPR: RegistrationComplete, FirebaseUID={}, UserID={}, Email={}, UserType={}, DataStored=user_profile,credentials, Purpose=account_creation",
                firebaseUserId, savedUser.getId(), maskEmail(savedUser.getEmail()), savedUser.getUserType());

            log.info("Successfully registered user with ID: {}, Firebase ID: {}",
                    savedUser.getId(), firebaseUserId);

            return response;

        } catch (Exception e) {
            // Clean up Firebase user if created but PG save failed
            try {
                firebaseService.deleteUser(firebaseUserId);
                log.info("Cleaned up Firebase user {} after registration failure", firebaseUserId);
            } catch (Exception ex) {
                log.error("Failed to clean up Firebase user {} after registration failure: {}", firebaseUserId, ex.getMessage());
            }
            throw e;
        }
    }

    /**
     * Helper method to create an address for a company
     */
    private void createCompanyAddress(User company, RegisterUserRequest request) {
        Address address = new Address();
        address.setUser(company);
        address.setStreet(request.getAddressStreet());
        address.setCity(request.getAddressCity());
        address.setPostalCode(request.getAddressPostalCode());
        address.setCountry(request.getAddressCountry());
        address.setState(request.getAddressState());
        address.setAdditionalInfo(request.getAddressInfo());
        address.setAddressType("MAIN");
        address.setPrimary(true);

        addressRepository.save(address);
    }

    /**
     * Register a new influencer user using social data from a previous auth exchange
     */
    @Transactional
    public RegisterUserResponse registerInfluencer(RegisterInfluencerRequest request) {
        // GDPR: Log influencer registration
        log.info("GDPR: Operation=registerInfluencer, Email={}, Platform={}, DataAccessed=social_profile,email, Purpose=influencer_account_creation, LegalBasis=contract, ThirdParty={}",
            request != null ? maskEmail(request.getEmail()) : "unknown",
            request != null ? request.getPlatformName() : "unknown",
            request != null ? request.getPlatformName() : "unknown");
        
        // Validate request
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.invalid_email");
        }
        if (request.getPlatformName() == null || request.getPlatformName().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.auth.platform_name_required");
        }

        log.info("Processing influencer registration with email: {}", maskEmail(request.getEmail()));

        String firebaseUserId = null;

        try {
            Map<String, Object> socialUserData = request.getSocialUserData();
            if (socialUserData == null || socialUserData.isEmpty()) {
                throw new ValidationTranslatableException("error.validation.required_field", "social data");
            }

            // Extract user information from the social data
            String platformName = request.getPlatformName();

            // Validate social user ID
            if (!socialUserData.containsKey("user_id") || socialUserData.get("user_id") == null) {
                throw new ValidationTranslatableException("error.validation.required_field", "user_id");
            }
            String socialUserId = String.valueOf(socialUserData.get("user_id"));
            if (socialUserId.equals("null") || socialUserId.trim().isEmpty()) {
                throw new ValidationTranslatableException("error.validation.required_field", "user_id");
            }

            String accessToken = (String) socialUserData.get("access_token");

            // Check if user already exists with this social account
            Optional<UserSocialConnection> existingConnection = socialConnectionRepository
                    .findByPlatform_NameAndSocialUserId(request.getPlatformName(), socialUserId);

            if (existingConnection.isPresent()) {
                throw new BusinessRuleTranslatableException("error.auth.social_account_registered");
            }

            // Extract profile info specifically from this structure
            String username = (String) socialUserData.getOrDefault("username", "");
            if (username == null || username.trim().isEmpty()) {
                throw new ValidationTranslatableException("error.validation.required_field", "username");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> userDetails = socialUserData.containsKey("user") && socialUserData.get("user") instanceof Map ?
                    (Map<String, Object>) socialUserData.get("user") : new HashMap<>();

            // Get followers count, prioritizing the main object then falling back to user object
            Integer followersCount = extractFollowersCount(socialUserData, userDetails);

            // Create Firebase user (without password)
            Map<String, Object> firebaseUser;
            try {
                firebaseUser = firebaseService.createSocialOnlyFirebaseUser(
                        request.getEmail(),
                        username,
                        platformName,
                        socialUserId);
                firebaseUserId = (String) firebaseUser.get("uid");
            } catch (ExternalServiceException e) {
                // Check if user already exists in Firebase
                if (e.getMessage().contains("already exists")) {
                    log.warn("User with email {} already exists in Firebase but not in database", maskEmail(request.getEmail()));
                    // Try to get the existing Firebase user
                    try {
                        UserRecord existingUser = firebaseService.getUserByEmail(request.getEmail());
                        firebaseUserId = existingUser.getUid();
                        log.info("Found existing Firebase user with ID: {}", firebaseUserId);
                        // Continue with registration using existing Firebase user
                    } catch (Exception fetchError) {
                        log.error("Failed to fetch existing Firebase user: {}", fetchError.getMessage());
                        throw new AuthenticationTranslatableException("error.auth.service_unavailable");
                    }
                } else {
                    throw new AuthenticationTranslatableException("error.auth.service_unavailable");
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error creating Firebase user: {}", e.getMessage());
                throw new AuthenticationTranslatableException("error.auth.service_unavailable");
            }

            // Check if user already exists with this Firebase ID
            checkUserDoesNotExist(firebaseUserId);

            // Create backend user
            User user = createInfluencerUser(
                    firebaseUserId,
                    request.getEmail(),
                    username,
                    socialUserData
            );

            // Let DataIntegrityViolationException propagate to global handler
            User savedUser = userRepository.save(user);

            // Create social connection
            UserSocialConnection connection = createSocialConnection(
                    savedUser,
                    platformName,
                    socialUserId,
                    username,
                    (String) socialUserData.getOrDefault("profile_picture_url", ""),
                    followersCount,
                    accessToken
            );

            UserSocialConnection savedConnection;
            try {
                savedConnection = socialConnectionRepository.save(connection);
            } catch (Exception e) {
                log.error("Error saving social connection: {}", e.getMessage());
                throw new BusinessRuleTranslatableException("error.business.data_integrity");
            }

            // Store Instagram data in Firestore (including access token)
            try {
                firestoreService.storeInstagramUserData(socialUserData, firebaseUserId);
                log.info("Successfully stored Instagram data in Firestore for user: {}", socialUserId);
            } catch (Exception e) {
                // Log the error but don't fail the registration
                log.error("Failed to store Instagram data in Firestore: {}", e.getMessage());
                // Could optionally store this error in the response for monitoring
            }

            // Generate custom token for Firebase authentication
            String customToken;
            try {
                // Create custom claims for the token
                Map<String, Object> claims = new HashMap<>();
                claims.put("role", savedUser.getUserType().toString());
                claims.put("userId", savedUser.getId().toString());
                claims.put("email", savedUser.getEmail());
                
                customToken = firebaseService.generateCustomTokenWithClaims(firebaseUserId, claims);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error generating custom token: {}", e.getMessage());
                throw new AuthenticationTranslatableException("error.auth.service_unavailable");
            }

            // Create response
            RegisterUserResponse response = buildInfluencerResponse(
                    firebaseUserId,
                    customToken,
                    savedUser,
                    platformName,
                    socialUserId,
                    username,
                    followersCount,
                    savedConnection
            );

            // GDPR: Log successful influencer registration
            log.info("GDPR: InfluencerRegistrationComplete, FirebaseUID={}, UserID={}, Email={}, Platform={}, DataStored=user_profile,social_connection, Purpose=influencer_onboarding",
                firebaseUserId, savedUser.getId(), maskEmail(savedUser.getEmail()), platformName);
            
            log.info("Successfully registered influencer with ID: {}, Firebase ID: {}",
                    savedUser.getId(), firebaseUserId);

            return response;

        } catch (IllegalArgumentException e) {
            // Pass through IllegalArgumentException for consistent error handling
            throw e;
        } catch (Exception e) {
            // Clean up Firebase user if we created one but failed to complete registration
            if (firebaseUserId != null) {
                try {
                    firebaseService.deleteUser(firebaseUserId);
                    log.info("Deleted Firebase user {} after registration failure", firebaseUserId);
                } catch (Exception ex) {
                    log.error("Failed to delete Firebase user after registration failure", ex.getMessage());
                }
            }

            log.error("Influencer registration failed: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.invalid_state");
        }
    }

    /**
     * Extract followers count from social data
     */
    private Integer extractFollowersCount(Map<String, Object> socialUserData, Map<String, Object> userDetails) {
        return socialUserData.containsKey("followers_count") ?
                (Integer) socialUserData.get("followers_count") :
                (userDetails.containsKey("followers_count") ? (Integer) userDetails.get("followers_count") : 0);
    }

    /**
     * Create a User entity for an influencer
     */
    private User createInfluencerUser(String firebaseUserId, String email, String username, Map<String, Object> socialUserData) {
        User user = new User();
        user.setFirebaseUserId(firebaseUserId);
        user.setEmail(email);

        // Set firstName and lastName as empty strings (not null or placeholders)
        // Users can fill these in during profile completion
        user.setFirstName("");
        user.setLastName("");
        user.setName(username); // Instagram username for display
        user.setUserType(UserType.INFLUENCER);
        user.setAccountStatus(AccountStatus.IN_VALIDATION);
        user.setCreatedTime(LocalDateTime.now());
        user.setLastUpdateTime(LocalDateTime.now());

        // Only set profile picture if it's not empty
        String profilePicUrl = (String) socialUserData.getOrDefault("profile_picture_url", "");
        if (profilePicUrl != null && !profilePicUrl.isEmpty()) {
            // Ensure URL starts with https://
            if (!profilePicUrl.startsWith("https://")) {
                profilePicUrl = "https://" + profilePicUrl.replaceFirst("^http://", "");
            }
            user.setProfilePicture(profilePicUrl);
        }

        return user;
    }

    /**
     * Create a social connection for a user
     */
    private UserSocialConnection createSocialConnection(
            User user,
            String platformName,
            String socialUserId,
            String username,
            String profilePictureUrl,
            Integer followersCount,
            String accessToken) {

        SocialPlatformService socialService;
        try {
            socialService = socialPlatformFactory.getSocialService(platformName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting social platform service: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.invalid_argument");
        }

        UserSocialConnection connection = new UserSocialConnection();
        connection.setUser(user);
        connection.setPlatform(socialService.getPlatformEntity());
        connection.setSocialUserId(socialUserId);
        connection.setDisplayName(username);

        // Only set profile picture URL in connection if not empty
        if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
            connection.setProfilePictureUrl(profilePictureUrl);
        }

        connection.setFollowersCount(followersCount);
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setLastSyncTime(LocalDateTime.now());
        connection.setIsPrimary(true);

        // Store the access token if available
        //if (accessToken != null) {
        //    connection.setNote("access_token:" + accessToken);
        //}

        return connection;
    }

    /**
     * Build response for influencer registration
     */
    private RegisterUserResponse buildInfluencerResponse(
            String firebaseUserId,
            String customToken,
            User user,
            String platformName,
            String socialUserId,
            String username,
            Integer followersCount,
            UserSocialConnection connection) {

        RegisterUserResponse response = new RegisterUserResponse();
        response.setSuccess(true);
        response.setFirebaseUserId(firebaseUserId);
        response.setCustomToken(customToken);
        response.setUserId(user.getId());

        // Add social connection info to response
        SocialConnectionResponse socialConnectionResponse = new SocialConnectionResponse();
        socialConnectionResponse.setSuccess(true);
        socialConnectionResponse.setConnectionId(connection.getId());
        socialConnectionResponse.setPlatformName(platformName);
        socialConnectionResponse.setSocialUserId(socialUserId);
        socialConnectionResponse.setDisplayName(username);
        socialConnectionResponse.setFollowersCount(followersCount);
        response.setSocialConnection(socialConnectionResponse);

        return response;
    }

    /**
     * Connects the current authenticated user to a social media platform with enhanced validation.
     * This can be used both during registration and later to add additional platforms.
     *
     * @param request The social connection request with platform details
     * @return SocialConnectionResponse with connection details
     */
    @Transactional
    public SocialConnectionResponse connectSocialPlatform(SocialConnectionRequest request) {
        // Validate request
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }
        if (request.getPlatformName() == null || request.getPlatformName().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.auth.platform_name_required");
        }
        if (request.getAuthCode() == null || request.getAuthCode().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.auth.auth_code_required");
        }

        // Get the Firebase user ID from the security context
        String firebaseUserId = permissionUtils.getUserId();
        if (firebaseUserId == null || firebaseUserId.isEmpty()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    "anonymous",
                    "connectSocialPlatform",
                    "SocialConnection");
        }

        log.info("Connecting user {} to social platform: {}", firebaseUserId, request.getPlatformName());

        // 1. Verify user exists
        User user = userRepository.findByFirebaseUserId(firebaseUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // 2. Get appropriate social platform service
        SocialPlatformService socialService;
        try {
            socialService = socialPlatformFactory.getSocialService(request.getPlatformName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting social platform service: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.invalid_argument");
        }

        // 3. Process authentication with the social platform
        Map<String, Object> socialUserData;
        try {
            socialUserData = socialService.exchangeAuthCodeForProfile(request.getAuthCode());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Social platform auth error: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
        }

        // 4. Extract social account details
        String socialUserId = socialUserData.get("user_id").toString();
        String username = (String) socialUserData.getOrDefault("username", "");
        String profilePicture = (String) socialUserData.getOrDefault("profile_picture_url", "");
        Integer followersCount = socialUserData.containsKey("followers_count") ?
                (Integer) socialUserData.get("followers_count") : 0;
        String accountType = (String) socialUserData.getOrDefault("account_type", "");

        // 5.1 Check if this user already has this social connection
        Optional<UserSocialConnection> existingUserConnection = socialConnectionRepository
                .findByUserIdAndPlatformNameAndSocialUserId(
                        user.getId(),
                        request.getPlatformName(),
                        socialUserId);

        if (existingUserConnection.isPresent()) {
            log.info("User already has this social connection: {} - {}", request.getPlatformName(), socialUserId);
            // Instead of throwing an error, we'll update the existing connection
            log.info("Updating existing social connection");
        }

        // 5.2 Check if this social account is connected to ANOTHER user account
        Optional<UserSocialConnection> otherUserConnection = socialConnectionRepository
                .findByPlatformNameAndSocialUserIdAndUserIdNot(
                        request.getPlatformName(),
                        socialUserId,
                        user.getId());

        if (otherUserConnection.isPresent()) {
            User otherUser = otherUserConnection.get().getUser();
            log.warn("Social account {} is already connected to another user (ID: {})",
                    socialUserId, otherUser.getId());
            throw new BusinessRuleTranslatableException("error.business.duplicate_entry", "Social account");
        }

        // 6. Create or update social connection
        UserSocialConnection connection = existingUserConnection.orElse(new UserSocialConnection());
        connection.setUser(user);
        connection.setPlatform(socialService.getPlatformEntity());
        connection.setSocialUserId(socialUserId);
        connection.setDisplayName(username);
        connection.setProfilePictureUrl(profilePicture);
        connection.setFollowersCount(followersCount);
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setLastSyncTime(LocalDateTime.now());

        // Handle primary flag
        if (request.getSetPrimary() != null && request.getSetPrimary()) {
            connection.setIsPrimary(true);

            // If setting this connection as primary, ensure no other connections are primary
            if (request.getSetPrimary()) {
                List<UserSocialConnection> userConnections = socialConnectionRepository.findByUserId(user.getId());
                for (UserSocialConnection existingConn : userConnections) {
                    if (!existingConn.getId().equals(connection.getId()) &&
                            Boolean.TRUE.equals(existingConn.getIsPrimary())) {
                        existingConn.setIsPrimary(false);
                        socialConnectionRepository.save(existingConn);
                    }
                }
            }
        }

        // Save connection
        UserSocialConnection savedConnection;
        try {
            savedConnection = socialConnectionRepository.save(connection);
        } catch (Exception e) {
            log.error("Error saving social connection: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }

        // Store social data in Firestore (for Instagram, includes access token)
        if ("Instagram".equalsIgnoreCase(request.getPlatformName())) {
            try {
                firestoreService.storeInstagramUserData(socialUserData, firebaseUserId);
                log.info("Stored Instagram data in Firestore for user: {}", socialUserId);
            } catch (Exception e) {
                log.error("Failed to store Instagram data in Firestore: {}", e.getMessage());
            }
        }

        // 7. Return response
        SocialConnectionResponse response = new SocialConnectionResponse();
        response.setSuccess(true);
        response.setConnectionId(savedConnection.getId());
        response.setPlatformName(request.getPlatformName());
        response.setSocialUserId(socialUserId);
        response.setDisplayName(username);
        response.setFollowersCount(followersCount);

        log.info("Successfully connected user {} to {} with social ID: {}",
                user.getFirebaseUserId(), request.getPlatformName(), socialUserId);

        return response;
    }

    /**
     * Connects a specified user to a social media platform during registration.
     * This is a variant of connectSocialPlatform that takes an explicit Firebase user ID
     * instead of getting it from the security context.
     *
     * @param firebaseUserId The Firebase user ID to connect
     * @param request        The social connection request details
     * @return SocialConnectionResponse with connection details
     */
    @Transactional
    public SocialConnectionResponse connectSocialPlatformForRegistration(
            String firebaseUserId, SocialConnectionRequest request) {

        log.info("Connecting user {} to social platform during registration: {}",
                firebaseUserId, request.getPlatformName());

        // 1. Verify user exists
        User user = userRepository.findByFirebaseUserId(firebaseUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // 2. Get appropriate social platform service
        SocialPlatformService socialService;
        try {
            socialService = socialPlatformFactory.getSocialService(request.getPlatformName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting social platform service: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.invalid_argument");
        }

        // 3. Process authentication with the social platform
        Map<String, Object> socialUserData;
        try {
            socialUserData = socialService.exchangeAuthCodeForProfile(request.getAuthCode());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Social platform auth error: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.auth.invalid_credentials");
        }

        // 4. Create or update social connection
        String socialUserId = socialUserData.get("user_id").toString();
        String username = (String) socialUserData.getOrDefault("username", "");
        String profilePicture = (String) socialUserData.getOrDefault("profile_picture_url", "");
        Integer followersCount = socialUserData.containsKey("followers_count") ?
                (Integer) socialUserData.get("followers_count") : 0;

        UserSocialConnection connection = new UserSocialConnection();
        connection.setUser(user);
        connection.setPlatform(socialService.getPlatformEntity());
        connection.setSocialUserId(socialUserId);
        connection.setDisplayName(username);
        connection.setProfilePictureUrl(profilePicture);
        connection.setFollowersCount(followersCount);
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setLastSyncTime(LocalDateTime.now());
        connection.setIsPrimary(true); // Always set primary during registration

        // Save connection
        UserSocialConnection savedConnection;
        try {
            savedConnection = socialConnectionRepository.save(connection);
        } catch (Exception e) {
            log.error("Error saving social connection: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }

        // Store social data in Firestore (for Instagram, includes access token)
        if ("Instagram".equalsIgnoreCase(request.getPlatformName())) {
            try {
                firestoreService.storeInstagramUserData(socialUserData, firebaseUserId);
                log.info("Stored Instagram data in Firestore during registration for user: {}", socialUserId);
            } catch (Exception e) {
                log.error("Failed to store Instagram data in Firestore during registration: {}", e.getMessage());
            }
        }

        // 5. Return response
        SocialConnectionResponse response = new SocialConnectionResponse();
        response.setSuccess(true);
        response.setConnectionId(savedConnection.getId());
        response.setPlatformName(request.getPlatformName());
        response.setSocialUserId(socialUserId);
        response.setDisplayName(username);
        response.setFollowersCount(followersCount);

        log.info("Successfully connected user {} to {} with social ID: {}",
                user.getFirebaseUserId(), request.getPlatformName(), socialUserId);

        return response;
    }

    /**
     * Refreshes a social media connection's data (followers, profile pic, etc.)
     * This method ensures the current user has permission to refresh the connection.
     *
     * @param connectionId The ID of the existing connection to refresh
     * @return Updated SocialConnectionResponse
     */
    @Transactional
    public SocialConnectionResponse refreshSocialConnection(Long connectionId) {
        // Get current authenticated user
        String currentUserId = permissionUtils.getUserId();
        if (currentUserId == null || currentUserId.isEmpty()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    "anonymous",
                    "refreshSocialConnection",
                    "SocialConnection#" + connectionId);
        }

        // Find the connection
        UserSocialConnection connection = socialConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Social connection"));

        // Verify the user has permission to refresh this connection
        if (!permissionUtils.isAdmin() &&
                !connection.getUser().getFirebaseUserId().equals(currentUserId)) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserId,
                    "refreshSocialConnection",
                    "SocialConnection#" + connectionId);
        }

        SocialPlatformService socialService;
        try {
            socialService = socialPlatformFactory.getSocialService(
                    connection.getPlatform().getName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting social platform service: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.invalid_argument");
        }

        // Refresh connection data
        Map<String, Object> refreshedData;
        try {
            refreshedData = socialService.refreshSocialData(connection.getSocialUserId());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error refreshing social data: {}", e.getMessage());
            throw new AuthenticationTranslatableException("error.auth.service_unavailable");
        }

        // Update connection with new data
        connection.setDisplayName((String) refreshedData.getOrDefault("username", connection.getDisplayName()));
        connection.setProfilePictureUrl((String) refreshedData.getOrDefault("profile_picture_url", connection.getProfilePictureUrl()));
        connection.setFollowersCount((Integer) refreshedData.getOrDefault("followers_count", connection.getFollowersCount()));
        connection.setLastSyncTime(LocalDateTime.now());

        UserSocialConnection updatedConnection;
        try {
            updatedConnection = socialConnectionRepository.save(connection);
        } catch (Exception e) {
            log.error("Error saving updated connection: {}", e.getMessage());
            throw new BusinessRuleTranslatableException("error.business.data_integrity");
        }

        // Return response
        SocialConnectionResponse response = new SocialConnectionResponse();
        response.setSuccess(true);
        response.setConnectionId(updatedConnection.getId());
        response.setPlatformName(connection.getPlatform().getName());
        response.setSocialUserId(connection.getSocialUserId());
        response.setDisplayName(connection.getDisplayName());
        response.setFollowersCount(connection.getFollowersCount());

        return response;
    }

    /**
     * Helper method to check if a user exists by Firebase ID
     */
    private void checkUserDoesNotExist(String firebaseUserId) {
        Optional<User> existingUser = userRepository.findByFirebaseUserId(firebaseUserId);
        if (existingUser.isPresent()) {
            log.warn("Attempted to register existing user with Firebase ID: {}", firebaseUserId);
            throw new BusinessRuleTranslatableException("error.business.duplicate_entry", "User");
        }
    }

    // handleDataIntegrityViolation() method removed - now handled by BusinessExceptionHandler.mapDatabaseError()
}
