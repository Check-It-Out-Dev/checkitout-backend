package com.sm.instagram.platform.auth.service;

import static com.sm.instagram.platform.common.util.PiiMaskingUtils.maskEmail;

import com.sm.instagram.platform.auth.dto.RegisterUserRequest;
import com.sm.instagram.platform.auth.dto.RegistrationResponse;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.session.SessionData;
import com.sm.instagram.platform.auth.session.SocialAuthSessionService;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
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
import com.sm.instagram.platform.legal.ConsentSource;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.userpreferences.UserPreferencesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Service handling user registration flows.
 * Manages both email/password and social OAuth registrations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final FirebaseService firebaseService;
    private final FirestoreService firestoreService;
    private final PlatformRepository platformRepository;
    private final UserSocialConnectionRepository socialConnectionRepository;
    private final SocialAuthSessionService sessionService;
    private final EmailVerificationService emailVerificationService;
    private final UserPreferencesService userPreferencesService;
    private final LegalConsentService legalConsentService;
    private final EmailService emailService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Register a new user with email/password.
     *
     * @param request Registration request
     * @return Registration response with custom token
     */
    @Transactional
    public RegistrationResponse registerUser(RegisterUserRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        // GDPR: Log registration attempt
        log.info("GDPR: Operation=registerUser_started, Email={}, UserType={}, Purpose=account_creation, LegalBasis=consent",
            maskEmail(request.getEmail()), request.getUserType());

        validateRegistrationRequest(request);

        // Validate consent cookies are present BEFORE Firebase/DB operations
        // This ensures "missing consents" error (400) is thrown before "email already used" (409)
        legalConsentService.validateConsentCookiesPresent(httpRequest);

        // Normalize email to lowercase
        request.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));

        String firebaseUid = null;
        try {
            // 1. Create Firebase user
            String displayName = buildDisplayName(request);
            Map<String, Object> firebaseUser = firebaseService.createOrValidateFirebaseUser(
                    request.getEmail(),
                    request.getPassword(),
                    displayName,
                    com.sm.instagram.platform.common.authorization.Permission.valueOf(request.getUserType())
            );

            firebaseUid = (String) firebaseUser.get("uid");
            
            // GDPR: Log Firebase user creation
            log.info("GDPR: Operation=firebase_user_created, FirebaseUID={}, Email={}, Purpose=authentication_setup, DataCreated=firebase_account",
                firebaseUid, maskEmail(request.getEmail()));

            // 2. Check if user already exists
            if (userRepository.findByFirebaseUserId(firebaseUid).isPresent()) {
                log.warn("GDPR: Operation=registration_duplicate_attempt, FirebaseUID={}, Email={}, Purpose=duplicate_prevention",
                    firebaseUid, maskEmail(request.getEmail()));
                throw new BusinessRuleTranslatableException("error.business.duplicate_entry", "User");
            }

            // 3. Create database user
            User user = createUser(request, firebaseUid);
            
            // GDPR: Log personal data storage
            log.info("GDPR: Operation=user_data_stored, FirebaseUID={}, DataAccessed=email,firstName,lastName,userType,profilePicture, Purpose=account_creation, RetentionPeriod=until_deletion_request", 
                firebaseUid);
            
            // GDPR: Log address data if company user
            if (UserType.COMPANY.name().equals(request.getUserType()) && request.getAddressStreet() != null) {
                log.info("GDPR: Operation=address_data_stored, FirebaseUID={}, DataAccessed=street,city,postalCode,country,state, Purpose=company_registration, LegalBasis=legitimate_interest", 
                    firebaseUid);
            }
            
            User savedUser = userRepository.save(user);

            // Create default user preferences (opt-in: all notifications disabled by default)
            userPreferencesService.createDefaultPreferences(savedUser);

            // Process legal consent cookies (ToS, Privacy Policy, Cookie Policy)
            try {
                legalConsentService.processRegistrationConsents(
                        savedUser.getId(), httpRequest, httpResponse, ConsentSource.REGISTRATION);
            } catch (Exception e) {
                log.warn("GDPR: Consent cookie processing failed during registration for user {}. "
                        + "User created but consent records may be incomplete: {}",
                        savedUser.getId(), e.getMessage());
                // Don't fail registration — consent can be re-confirmed via re-consent flow
            }

            // Send verification email for COMPANY users (deferred to afterCommit to release DB connection)
            if (UserType.COMPANY.equals(savedUser.getUserType())) {
                final String uid = firebaseUid;
                final String language = LocaleContextHolder.getLocale().getLanguage();
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                emailVerificationService.sendVerificationEmail(uid, language);
                            } catch (Exception e) {
                                log.error("Failed to send verification email: {}", e.getMessage(), e);
                            }
                        }
                    });
                } else {
                    try {
                        emailVerificationService.sendVerificationEmail(uid, language);
                    } catch (Exception e) {
                        log.error("Failed to send verification email: {}", e.getMessage(), e);
                    }
                }
            }

            // Notify admin about new registration (deferred to afterCommit)
            {
                final String regUserName = savedUser.getName() != null ? savedUser.getName()
                        : (savedUser.getFirstName() != null ? savedUser.getFirstName() : "N/A");
                final String regUserEmail = savedUser.getEmail();
                final String regUserType = savedUser.getUserType().name();
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            emailService.sendNewUserAdminNotification(regUserName, regUserEmail, regUserType);
                        }
                    });
                } else {
                    emailService.sendNewUserAdminNotification(regUserName, regUserEmail, regUserType);
                }
            }

            // Publish event for admin in-app notification (handled by NotificationEventListener)
            eventPublisher.publishEvent(
                    new com.sm.instagram.platform.notification.event.NewUserRegisteredEvent(
                            this, savedUser, "EMAIL_PASSWORD"));

            // Generate custom token for immediate authentication
            // Frontend will use this to sign in with Firebase and get an ID token
            String customToken = firebaseService.generateCustomToken(firebaseUid);

            // GDPR: Log successful registration
            log.info("GDPR: Operation=registration_completed, FirebaseUID={}, UserId={}, Email={}, Purpose=account_activated",
                firebaseUid, savedUser.getId(), maskEmail(savedUser.getEmail()));

            return RegistrationResponse.builder()
                    .success(true)
                    .customToken(customToken)
                    .userId(savedUser.getId())
                    .firebaseUid(firebaseUid)
                    .userType(savedUser.getUserType().toString())
                    .build();

        } catch (Exception e) {
            // Clean up Firebase user if created but PG save failed
            if (firebaseUid != null) {
                try {
                    firebaseService.deleteUser(firebaseUid);
                    log.info("Cleaned up Firebase user {} after registration failure", firebaseUid);
                } catch (Exception ex) {
                    log.error("Failed to clean up Firebase user {} after registration failure: {}", firebaseUid, ex.getMessage());
                }
            }

            if (e instanceof DataIntegrityViolationException) {
                // GDPR: Log registration failure due to duplicate
                log.error("GDPR: Operation=registration_failed, Email={}, Error=duplicate_email_or_phone, Purpose=error_logging",
                    maskEmail(request.getEmail()), e);
                throw e;
            } else if (e instanceof IllegalArgumentException) {
                throw e;
            }
            // GDPR: Log registration failure
            log.error("GDPR: Operation=registration_failed, Email={}, Error={}, Purpose=error_logging",
                maskEmail(request.getEmail()), e.getMessage(), e);
            throw new BusinessRuleTranslatableException("error.business.invalid_state");
        }
    }

    /**
     * Complete social registration for OAuth users.
     *
     * @param sessionId   Session ID with stored social data
     * @param email       User email
     * @param sessionData Session data with social info
     * @return Registration response with custom token
     */
    @Transactional
    public RegistrationResponse completeSocialRegistration(
            String sessionId,
            String email,
            SessionData sessionData,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // GDPR: Log social registration attempt
        log.info("GDPR: Operation=social_registration_started, SessionId={}, Email={}, Purpose=social_account_creation, LegalBasis=consent",
            sessionId, maskEmail(email));

        if (email == null || email.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.invalid_email");
        }

        // Validate consent cookies are present BEFORE Firebase/DB operations
        legalConsentService.validateConsentCookiesPresent(httpRequest);

        // Normalize email to lowercase
        email = email.trim().toLowerCase(Locale.ROOT);

        Map<String, Object> socialData = sessionData.getSocialData();
        String platform = sessionData.getPlatform();
        String socialUserId = String.valueOf(socialData.get("user_id"));
        String username = (String) socialData.get("username");

        String firebaseUid = null;
        try {
            // 1. Check if social account already registered
            var existingConnection = socialConnectionRepository
                    .findByPlatform_NameAndSocialUserId(platform, socialUserId);

            if (existingConnection.isPresent()) {
                log.warn("GDPR: Operation=social_registration_duplicate, Platform={}, SocialUserId={}, Purpose=duplicate_prevention",
                    platform, socialUserId);
                throw new BusinessRuleTranslatableException("error.auth.social_account_registered");
            }

            // 2. Create Firebase user (social-only, no password)
            Map<String, Object> firebaseUser = firebaseService.createSocialOnlyFirebaseUser(
                    email,
                    username,
                    platform,
                    socialUserId
            );

            firebaseUid = (String) firebaseUser.get("uid");
            
            // GDPR: Log social Firebase user creation
            log.info("GDPR: Operation=social_firebase_user_created, FirebaseUID={}, Email={}, Platform={}, Purpose=social_authentication_setup",
                firebaseUid, maskEmail(email), platform);

            // 3. Create database user
            User user = createInfluencerUser(email, username, firebaseUid, socialData);
            
            // GDPR: Log influencer data storage
            log.info("GDPR: Operation=influencer_data_stored, FirebaseUID={}, DataAccessed=email,username,profilePicture,userType, Platform={}, Purpose=social_account_creation", 
                firebaseUid, platform);
            
            User savedUser = userRepository.save(user);

            // Create default user preferences (opt-in: all notifications disabled by default)
            userPreferencesService.createDefaultPreferences(savedUser);

            // Process legal consent cookies (HMAC-signed cookies set during registration UI)
            try {
                legalConsentService.processRegistrationConsents(
                        savedUser.getId(), httpRequest, httpResponse, ConsentSource.SOCIAL_REGISTRATION);
                savedUser.setNewestConsentsAccepted(true);
                userRepository.save(savedUser);
            } catch (Exception e) {
                log.warn("Failed to process consent cookies during social registration for user {}: {}",
                        savedUser.getId(), e.getMessage());
                // Don't fail social registration — re-consent flow will catch missing consents
            }

            // 4. Create social connection
            UserSocialConnection connection = createSocialConnection(
                    savedUser,
                    platform,
                    socialUserId,
                    username,
                    socialData
            );
            
            // GDPR: Log social connection data
            log.info("GDPR: Operation=social_connection_created, FirebaseUID={}, Platform={}, DataAccessed=socialUserId,username,followersCount,profilePictureUrl, Purpose=platform_integration", 
                firebaseUid, platform);
            
            socialConnectionRepository.save(connection);

            // 5. Store data in Firestore (for Instagram)
            if ("Instagram".equalsIgnoreCase(platform)) {
                try {
                    // GDPR: Log third-party data storage
                    log.warn("GDPR: Operation=firestore_data_storage, FirebaseUID={}, ThirdParty=Firebase_Firestore, DataShared=instagram_profile_data, Purpose=data_synchronization, LegalBasis=legitimate_interest", 
                        firebaseUid);
                    firestoreService.storeInstagramUserData(socialData, firebaseUid);
                } catch (Exception e) {
                    log.error("GDPR: Operation=firestore_storage_failed, FirebaseUID={}, Error={}, Purpose=error_logging", 
                        firebaseUid, e.getMessage(), e);
                    // Don't fail registration for this
                }
            }

            // Notify admin about new social registration (deferred to afterCommit)
            {
                final String regUserName = savedUser.getName() != null ? savedUser.getName()
                        : (savedUser.getFirstName() != null ? savedUser.getFirstName() : "N/A");
                final String regUserEmail = email;
                final String regUserType = UserType.INFLUENCER.name();
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            emailService.sendNewUserAdminNotification(regUserName, regUserEmail, regUserType);
                        }
                    });
                } else {
                    emailService.sendNewUserAdminNotification(regUserName, regUserEmail, regUserType);
                }
            }

            // Publish event for admin in-app notification (handled by NotificationEventListener)
            eventPublisher.publishEvent(
                    new com.sm.instagram.platform.notification.event.NewUserRegisteredEvent(
                            this, savedUser, "SOCIAL_OAUTH"));

            // Generate custom token for immediate authentication after social registration
            String customToken = firebaseService.generateCustomToken(firebaseUid);

            // GDPR: Log successful social registration
            log.info("GDPR: Operation=social_registration_completed, FirebaseUID={}, UserId={}, Email={}, Platform={}, Purpose=account_activated",
                firebaseUid, savedUser.getId(), maskEmail(email), platform);

            return RegistrationResponse.builder()
                    .success(true)
                    .customToken(customToken)
                    .userId(savedUser.getId())
                    .firebaseUid(firebaseUid)
                    .userType(UserType.INFLUENCER.toString())
                    .build();

        } catch (Exception e) {
            // Clean up Firebase user if created but PG save failed
            if (firebaseUid != null) {
                try {
                    firebaseService.deleteUser(firebaseUid);
                    log.info("Cleaned up Firebase user {} after social registration failure", firebaseUid);
                } catch (Exception ex) {
                    log.error("Failed to clean up Firebase user {} after social registration failure: {}", firebaseUid, ex.getMessage());
                }
            }

            if (e instanceof DataIntegrityViolationException) {
                log.error("GDPR: Operation=social_registration_failed, Email={}, Platform={}, Error=duplicate_email, Purpose=error_logging",
                    maskEmail(email), platform, e);
                throw e;
            } else if (e instanceof IllegalArgumentException) {
                throw e;
            }
            // GDPR: Log social registration failure
            log.error("GDPR: Operation=social_registration_failed, Email={}, Platform={}, Error={}, Purpose=error_logging",
                maskEmail(email), platform, e.getMessage(), e);
            throw new BusinessRuleTranslatableException("error.business.invalid_state");
        }
    }

    /**
     * Validate registration request.
     *
     * @param request Registration request
     */
    private void validateRegistrationRequest(RegisterUserRequest request) {
        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.invalid_email");
        }

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new ValidationTranslatableException("error.auth.weak_password");
        }

        if (request.getUserType() == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "userType");
        }

        try {
            UserType.valueOf(request.getUserType());
        } catch (IllegalArgumentException e) {
            throw new ValidationTranslatableException("error.validation.type_mismatch", "userType");
        }
    }

    /**
     * Build display name from registration request.
     *
     * @param request Registration request
     * @return Display name
     */
    private String buildDisplayName(RegisterUserRequest request) {
        if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
            String name = request.getFirstName();
            if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
                name += " " + request.getLastName();
            }
            return name;
        } else if (request.getCompanyName() != null && !request.getCompanyName().trim().isEmpty()) {
            return request.getCompanyName();
        } else {
            return request.getEmail().split("@")[0];
        }
    }

    /**
     * Create user entity from registration request.
     *
     * @param request     Registration request
     * @param firebaseUid Firebase user ID
     * @return User entity
     */
    private User createUser(RegisterUserRequest request, String firebaseUid) {
        User user = new User();
        user.setFirebaseUserId(firebaseUid);
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUserType(UserType.valueOf(request.getUserType()));
        user.setAccountStatus(AccountStatus.IN_VALIDATION);
        user.setCreatedTime(LocalDateTime.now());
        user.setLastUpdateTime(LocalDateTime.now());
        if (request.getProfilePictureUrl() != null && !request.getProfilePictureUrl().trim().isEmpty()) {
            user.setProfilePicture(request.getProfilePictureUrl());
        }
        if (UserType.COMPANY.name().equals(request.getUserType())) {
            user.setName(request.getCompanyName());
            user.setPhoneNumber(request.getPhoneNumber());
            createAddressForUser(user, request);
        }

        return user;
    }

    /**
     * Create address entity for company user from registration request.
     *
     * @param user    User entity
     * @param request Registration request with address data
     */
    private void createAddressForUser(User user, RegisterUserRequest request) {
        if (request.getAddressStreet() != null && !request.getAddressStreet().trim().isEmpty() &&
                request.getAddressCity() != null && !request.getAddressCity().trim().isEmpty() &&
                request.getAddressPostalCode() != null && !request.getAddressPostalCode().trim().isEmpty() &&
                request.getAddressCountry() != null && !request.getAddressCountry().trim().isEmpty()) {

            com.sm.instagram.platform.address.Address address = new com.sm.instagram.platform.address.Address();
            address.setUser(user);
            address.setStreet(request.getAddressStreet());
            address.setCity(request.getAddressCity());
            address.setPostalCode(request.getAddressPostalCode());
            address.setCountry(request.getAddressCountry());
            address.setState(request.getAddressState());
            address.setAdditionalInfo(request.getAddressInfo());
            address.setAddressType("MAIN");
            address.setPrimary(true);
            address.setSourceType(com.sm.instagram.platform.address.AddressSourceType.CUSTOM);
            address.setCreatedTime(LocalDateTime.now());
            address.setLastUpdateTime(LocalDateTime.now());

            user.getAddresses().add(address);
        }
    }

    /**
     * Create influencer user from social data.
     *
     * @param email       User email
     * @param username    Social username
     * @param firebaseUid Firebase user ID
     * @param socialData  Social platform data
     * @return User entity
     */
    private User createInfluencerUser(String email, String username, String firebaseUid, Map<String, Object> socialData) {
        User user = new User();
        user.setFirebaseUserId(firebaseUid);
        user.setEmail(email);
        user.setName(username);
        user.setUserType(UserType.INFLUENCER);
        user.setAccountStatus(AccountStatus.IN_VALIDATION);
        user.setCreatedTime(LocalDateTime.now());
        user.setLastUpdateTime(LocalDateTime.now());

        String profilePicUrl = (String) socialData.get("profile_picture_url");
        if (profilePicUrl != null && !profilePicUrl.isEmpty()) {
            user.setProfilePicture(profilePicUrl);
        }

        return user;
    }

    /**
     * Create social connection entity.
     *
     * @param user         User entity
     * @param platformName Platform name
     * @param socialUserId Social user ID
     * @param username     Social username
     * @param socialData   Social platform data
     * @return Social connection entity
     */
    private UserSocialConnection createSocialConnection(
            User user,
            String platformName,
            String socialUserId,
            String username,
            Map<String, Object> socialData) {

        Platform platform = platformRepository.findByName(platformName)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Platform"));

        UserSocialConnection connection = new UserSocialConnection();
        connection.setUser(user);
        connection.setPlatform(platform);
        connection.setSocialUserId(socialUserId);
        connection.setDisplayName(username);
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setLastSyncTime(LocalDateTime.now());
        connection.setIsPrimary(true);

        String profilePicUrl = (String) socialData.get("profile_picture_url");
        if (profilePicUrl != null && !profilePicUrl.isEmpty()) {
            connection.setProfilePictureUrl(profilePicUrl);
        }

        Integer followersCount = extractFollowersCount(socialData);
        connection.setFollowersCount(followersCount);

        return connection;
    }

    /**
     * Extract followers count from social data.
     *
     * @param socialData Social platform data
     * @return Followers count
     */
    private Integer extractFollowersCount(Map<String, Object> socialData) {
        if (socialData.containsKey("followers_count")) {
            Object count = socialData.get("followers_count");
            if (count instanceof Integer integer) {
                return integer;
            } else if (count instanceof String string) {
                try {
                    return Integer.parseInt(string);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Build user claims for JWT.
     *
     * @param user User entity
     * @return Claims map
     */
    private Map<String, Object> buildUserClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getUserType().toString());
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        return claims;
    }
}
