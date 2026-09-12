package com.sm.instagram.platform.userpreferences;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.notification.EmailFrequency;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class UserPreferencesService extends BaseService<UserPreferences, Long, UserPreferencesDtoIn> {
    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PermissionUtils permissionUtils;

    @Autowired
    public UserPreferencesService(
            SpecificationBuilder<UserPreferences> specificationBuilder,
            UserPreferencesRepository repository,
            ModelMapper modelMapper,
            RepositoryResolver repositoryResolver,
            UserRepository userRepository,
            PermissionUtils permissionUtils,
            ApplicationContext applicationContext
    ) {
        super(applicationContext, specificationBuilder, repository, modelMapper, repositoryResolver);
        this.userRepository = userRepository;
        this.userPreferencesRepository = repository;
        this.permissionUtils = permissionUtils;
    }

    /**
     * Validates if the current user can access or modify the preferences of another user
     *
     * @param userId The ID of the user whose preferences are being accessed
     * @return The user entity for the given ID
     * @throws AccessDeniedException     if the current user doesn't have permission
     * @throws ResourceNotFoundException if the user is not found
     */
    private User validateUserAccess(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // If not admin and not the owner of these preferences
        if (!permissionUtils.isAdmin() && !permissionUtils.isUserOwner(user)) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "validateUserAccess",
                    "UserPreferences");
        }

        return user;
    }

    /**
     * Get the current authenticated user
     *
     * @return User entity for the current authenticated user
     * @throws ResourceNotFoundException if the user is not found
     */
    private User getCurrentUser() {
        String firebaseUserId = permissionUtils.getUserId();
        return userRepository.findByFirebaseUserId(firebaseUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
    }

    /**
     * Creates default user preferences for a user
     *
     * @param user The user to create preferences for
     * @return New UserPreferences with default values
     */
    public UserPreferences createDefaultPreferences(User user) {
        String firebaseUid = user.getFirebaseUserId();

        // GDPR: Log creation of new user preferences
        log.info("GDPR: Operation=createDefaultPreferences, FirebaseUID={}, DataCreated=user.preferences.*, Purpose=initial_setup, LegalBasis=contract",
                firebaseUid);

        UserPreferences preferences = new UserPreferences();
        preferences.setUser(user);
        preferences.setNotificationEmailEnabled(true);
        preferences.setNotificationPushEnabled(false);
        preferences.setNotificationSmsEnabled(false);
        preferences.setNotificationPartnershipEnabled(true);
        preferences.setNotificationSupportEnabled(true);
        preferences.setNotificationSystemEnabled(true);
        preferences.setNotificationEmailPartnershipEnabled(true);
        preferences.setNotificationEmailSupportEnabled(true);
        preferences.setDarkModeEnabled(false);
        // CIO-398: Use language from request header (set by AppLanguageFilter in LocaleContextHolder)
        // instead of hardcoding "en", respecting user's pre-registration language choice
        String requestLanguage = LocaleContextHolder.getLocale().getLanguage();
        // Only accept supported languages (en, pl), default to en for unknown
        String language = "pl".equals(requestLanguage) ? "pl" : "en";
        preferences.setLanguage(language);
        preferences.setTimezone("UTC");
        preferences.setCommunicationFrequency(EmailFrequency.WEEKLY_DIGEST);
        preferences.setGdprMarketingConsent(false);
        preferences.setSharePhoneForPayments(false);
        preferences.setTwoFactorAuthenticationEnabled(false);

        UserPreferences saved = userPreferencesRepository.save(updateEntityUpdater(preferences));

        // GDPR: Log successful creation
        log.info("GDPR: Operation=createDefaultPreferences_SUCCESS, FirebaseUID={}, PreferencesID={}, DefaultsApplied=true",
                firebaseUid, saved.getId());

        return saved;
    }

    /**
     * Get existing user preferences for the current user or create default ones if they don't exist
     *
     * @return User preferences (either existing or newly created)
     */
    @Transactional
    public UserPreferences getCurrentUserPreferences() {
        User user = getCurrentUser();
        String firebaseUid = user.getFirebaseUserId();

        // GDPR: Log preference access
        log.info("GDPR: Operation=getCurrentUserPreferences, FirebaseUID={}, Purpose=user_preference_retrieval, DataAccessed=user.preferences.*",
                firebaseUid);

        // Try to find existing preferences
        UserPreferences preferences = userPreferencesRepository.findByUser(user);

        // If preferences don't exist, create default ones
        if (preferences == null) {
            log.info("GDPR: Operation=getCurrentUserPreferences_CREATE_NEW, FirebaseUID={}, Reason=no_existing_preferences",
                    firebaseUid);
            preferences = createDefaultPreferences(user);
        }

        return preferences;
    }

    /**
     * Get user preferences for any user (admin only or own preferences)
     *
     * @param userId User ID to get preferences for
     * @return User preferences for the specified user
     */
    @Transactional
    public UserPreferences getUserPreferences(Long userId) {
        String adminFirebaseUid = permissionUtils.getUserId();

        // GDPR: Log admin or owner access to user preferences
        if (permissionUtils.isAdmin()) {
            log.warn("GDPR: ADMIN_ACCESS Operation=getUserPreferences, AdminFirebaseUID={}, TargetUserID={}, Purpose=admin_support, LegalBasis=legitimate_interest",
                    adminFirebaseUid, userId);
        } else {
            log.info("GDPR: Operation=getUserPreferences, FirebaseUID={}, TargetUserID={}, Purpose=own_data_access",
                    adminFirebaseUid, userId);
        }

        User user = validateUserAccess(userId);

        UserPreferences preferences = userPreferencesRepository.findByUser(user);

        if (preferences == null) {
            throw new ResourceNotFoundException("error.business.item_not_found", "Preferences");
        }

        return preferences;
    }

    /**
     * Update preferences for the current user
     *
     * @param dtoIn Input data for updates
     * @return Updated user preferences
     */
    @Transactional
    public UserPreferences updateCurrentUserPreferences(UserPreferencesDtoIn dtoIn) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log preference update
        log.info("GDPR: Operation=updateCurrentUserPreferences, FirebaseUID={}, DataModified=user.preferences.*, Purpose=user_settings_update, LegalBasis=consent",
                firebaseUid);

        // Get existing preferences or create default
        UserPreferences preferences = getCurrentUserPreferences();

        // Validate input length
        validatePreferencesInput(dtoIn);

        // Check for GDPR consent changes
        boolean gdprConsentChanged = dtoIn.getGdprMarketingConsent() != null &&
                !dtoIn.getGdprMarketingConsent().equals(preferences.getGdprMarketingConsent());

        // Map DTO values to entity, preserving the existing ID and user
        Long id = preferences.getId();
        User user = preferences.getUser();
        modelMapper.map(dtoIn, preferences);
        preferences.setId(id);  // Preserve ID
        preferences.setUser(user);  // Preserve user reference

        // Explicitly set communication frequency
        if (dtoIn.getCommunicationFrequency() != null) {
            preferences.setCommunicationFrequency(
                    EmailFrequency.valueOf(dtoIn.getCommunicationFrequency())
            );
        }

        // GDPR: Log consent changes specifically
        if (gdprConsentChanged) {
            log.warn("GDPR: CONSENT_CHANGE Operation=updateGdprConsent, FirebaseUID={}, NewConsent={}, PreviousConsent={}, Purpose=marketing_consent_update",
                    firebaseUid, dtoIn.getGdprMarketingConsent(), !dtoIn.getGdprMarketingConsent());
        }

        UserPreferences saved = userPreferencesRepository.save(updateEntityUpdater(preferences));

        log.info("GDPR: Operation=updateCurrentUserPreferences_SUCCESS, FirebaseUID={}, PreferencesID={}",
                firebaseUid, saved.getId());

        return saved;
    }

    /**
     * Validate preferences input data
     *
     * @param dtoIn The input data to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validatePreferencesInput(UserPreferencesDtoIn dtoIn) {
        if (dtoIn.getLanguage() != null && dtoIn.getLanguage().length() > 10) {
            throw new ValidationTranslatableException("error.validation.invalid_argument", "language length");
        }
        if (dtoIn.getTimezone() != null && dtoIn.getTimezone().length() > 50) {
            throw new ValidationTranslatableException("error.validation.invalid_argument", "timezone length");
        }
    }

    /**
     * Process updates for UserPreferences from a map of field updates
     *
     * @param preferences The preferences object to update
     * @param updates     Map of field names to new values
     * @throws IllegalArgumentException if an invalid field is specified or type is incorrect
     */
    private void processPreferencesUpdates(UserPreferences preferences, Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            switch (key) {
                case "notificationEmailEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationEmailEnabled((Boolean) value);
                }
                case "notificationPushEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationPushEnabled((Boolean) value);
                }
                case "notificationSmsEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationSmsEnabled((Boolean) value);
                }
                case "notificationPartnershipEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationPartnershipEnabled((Boolean) value);
                }
                case "notificationSupportEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationSupportEnabled((Boolean) value);
                }
                case "notificationSystemEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationSystemEnabled((Boolean) value);
                }
                case "notificationEmailPartnershipEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationEmailPartnershipEnabled((Boolean) value);
                }
                case "notificationEmailSupportEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setNotificationEmailSupportEnabled((Boolean) value);
                }
                case "darkModeEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setDarkModeEnabled((Boolean) value);
                }
                case "language" -> {
                    validateType(value, String.class, key);
                    String language = (String) value;
                    if (language != null && language.length() > 10) {
                        throw new ValidationTranslatableException("error.validation.invalid_argument", "language length");
                    }
                    preferences.setLanguage(language);
                }
                case "timezone" -> {
                    validateType(value, String.class, key);
                    String timezone = (String) value;
                    if (timezone != null && timezone.length() > 50) {
                        throw new ValidationTranslatableException("error.validation.invalid_argument", "timezone length");
                    }
                    preferences.setTimezone(timezone);
                }
                case "communicationFrequency" -> {
                    validateType(value, String.class, key);
                    String frequency = (String) value;
                    try {
                        preferences.setCommunicationFrequency(
                                EmailFrequency.valueOf(frequency)
                        );
                    } catch (IllegalArgumentException e) {
                        throw new ValidationTranslatableException("error.validation.invalid_argument", "communication frequency");
                    }
                }
                case "gdprMarketingConsent" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setGdprMarketingConsent((Boolean) value);
                }
                case "sharePhoneForPayments" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setSharePhoneForPayments((Boolean) value);
                }
                case "twoFactorAuthenticationEnabled" -> {
                    validateType(value, Boolean.class, key);
                    preferences.setTwoFactorAuthenticationEnabled((Boolean) value);
                }
                default -> throw new ValidationTranslatableException("error.validation.invalid_argument", key);
            }
        });
    }

    /**
     * Validates that a value is of the expected type
     *
     * @param value        The value to validate
     * @param expectedType The expected type class
     * @param fieldName    The field name for error reporting
     * @throws IllegalArgumentException if the type doesn't match
     */
    private void validateType(Object value, Class<?> expectedType, String fieldName) {
        if (value != null && !expectedType.isInstance(value)) {
            throw new ValidationTranslatableException("error.validation.type_mismatch", fieldName);
        }
    }

    /**
     * Patch preferences for the current user
     *
     * @param updates Map of fields to update
     * @return Updated user preferences
     */
    @Transactional
    public UserPreferences patchCurrentUserPreferences(Map<String, Object> updates) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log partial preference update
        log.info("GDPR: Operation=patchCurrentUserPreferences, FirebaseUID={}, FieldsModified={}, Purpose=partial_settings_update, LegalBasis=consent",
                firebaseUid, updates.keySet());

        // Get existing preferences or create default
        UserPreferences preferences = getCurrentUserPreferences();

        // Check for GDPR consent changes
        if (updates.containsKey("gdprMarketingConsent")) {
            log.warn("GDPR: CONSENT_CHANGE Operation=patchGdprConsent, FirebaseUID={}, NewConsent={}, Purpose=marketing_consent_update",
                    firebaseUid, updates.get("gdprMarketingConsent"));
        }

        // Process updates
        processPreferencesUpdates(preferences, updates);

        UserPreferences saved = userPreferencesRepository.save(updateEntityUpdater(preferences));

        log.info("GDPR: Operation=patchCurrentUserPreferences_SUCCESS, FirebaseUID={}, ModifiedFields={}",
                firebaseUid, updates.keySet());

        return saved;
    }

    /**
     * Admin method to update any user's preferences
     *
     * @param userId  User ID to update preferences for
     * @param updates Map of fields to update
     * @return Updated user preferences
     */
    @Transactional
    public UserPreferences patchUserPreferences(Long userId, Map<String, Object> updates) {
        String adminFirebaseUid = permissionUtils.getUserId();

        // Only admins can use this method
        if (!permissionUtils.isAdmin()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    adminFirebaseUid,
                    "patchUserPreferences",
                    "UserPreferences#" + userId);
        }

        // GDPR: Log admin modification of user preferences
        log.warn("GDPR: ADMIN_MODIFICATION Operation=patchUserPreferences, AdminFirebaseUID={}, TargetUserID={}, FieldsModified={}, Purpose=admin_support, LegalBasis=legitimate_interest",
                adminFirebaseUid, userId, updates.keySet());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // Check for GDPR consent changes by admin
        if (updates.containsKey("gdprMarketingConsent")) {
            log.warn("GDPR: ADMIN_CONSENT_CHANGE Operation=adminPatchGdprConsent, AdminFirebaseUID={}, TargetUserID={}, NewConsent={}, Purpose=admin_override",
                    adminFirebaseUid, userId, updates.get("gdprMarketingConsent"));
        }

        // Try to find existing preferences
        UserPreferences preferences = userPreferencesRepository.findByUser(user);

        // If preferences don't exist, create default ones
        if (preferences == null) {
            log.info("GDPR: Operation=adminCreateDefaultPreferences, AdminFirebaseUID={}, TargetUserID={}, Reason=no_existing_preferences",
                    adminFirebaseUid, userId);
            preferences = createDefaultPreferences(user);
        }

        // Process updates
        processPreferencesUpdates(preferences, updates);

        UserPreferences saved = userPreferencesRepository.save(updateEntityUpdater(preferences));

        log.warn("GDPR: ADMIN_MODIFICATION_SUCCESS Operation=patchUserPreferences_SUCCESS, AdminFirebaseUID={}, TargetUserID={}, PreferencesID={}",
                adminFirebaseUid, userId, saved.getId());

        return saved;
    }

    /**
     * Get or create user preferences by ID with permission check
     */
    @Override
    public UserPreferences findById(Long id) {
        UserPreferences preferences = super.findById(id);
        User user = preferences.getUser();
        validateUserAccess(user.getId());
        return preferences;
    }

    /**
     * Update user preferences by ID with partial updates
     */
    @Override
    @Transactional
    public UserPreferences patch(Long id, Map<String, Object> updates) {
        UserPreferences existingPreferences = findById(id);
        // Validate permission is implicitly handled by findById

        // Process updates
        processPreferencesUpdates(existingPreferences, updates);

        return userPreferencesRepository.save(updateEntityUpdater(existingPreferences));
    }

    /**
     * Converts a UserPreferences entity to DTO.
     * This method ensures all conversions happen within a transaction boundary.
     *
     * @param entity The entity to convert
     * @param <O>    The output DTO type
     * @return The converted DTO
     */
    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <O> O toDto(UserPreferences entity) {
        if (entity == null) return null;
        // Type-safe: We know we're converting to UserPreferencesDtoOut
        return (O) modelMapper.map(entity, UserPreferencesDtoOut.class);
    }

    /**
     * Type-safe internal method for converting to specific DTO.
     *
     * @param entity The entity to convert
     * @return The converted UserPreferencesDtoOut
     */
    // No @Transactional: a private method is not proxied, so readOnly never took effect here.
    // Ten call sites reach this from methods that carry their own; those are the ones that count.
    private UserPreferencesDtoOut toDtoInternal(UserPreferences entity) {
        if (entity == null) return null;
        return modelMapper.map(entity, UserPreferencesDtoOut.class);
    }

    /**
     * Creates a new entity from DTO and returns it as DTO.
     * This method ensures all operations happen within a transaction boundary.
     *
     * @param dto The input DTO
     * @param <O> The output DTO type
     * @return The created entity as DTO
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public <O> O createFromDtoAsDto(UserPreferencesDtoIn dto) {
        UserPreferences entity = modelMapper.map(dto, UserPreferences.class);
        UserPreferences saved = save(entity);
        return (O) toDtoInternal(saved);
    }

    /**
     * Converts a list of UserPreferences entities to DTOs.
     * This method ensures all conversions happen within a transaction boundary.
     *
     * @param entities The entities to convert
     * @return The list of converted DTOs
     */
    @Transactional(readOnly = true)
    public List<UserPreferencesDtoOut> toDtoList(List<UserPreferences> entities) {
        if (entities == null || entities.isEmpty()) return new ArrayList<>();
        return entities.stream()
                .map(this::toDtoInternal)
                .collect(Collectors.toList());
    }

    /**
     * Finds UserPreferences by ID and returns it as a DTO.
     *
     * @param id The ID of the entity
     * @return The entity as a DTO
     */
    @Transactional(readOnly = true)
    public UserPreferencesDtoOut findByIdAsDto(Long id) {
        UserPreferences entity = findById(id);
        return toDtoInternal(entity);
    }

    /**
     * Gets the current user's preferences and returns as DTO.
     *
     * @return The current user's preferences as a DTO
     */
    @Transactional
    public UserPreferencesDtoOut getCurrentUserPreferencesAsDto() {
        UserPreferences entity = getCurrentUserPreferences();
        return toDtoInternal(entity);
    }

    /**
     * Updates the current user's preferences and returns as DTO.
     *
     * @param dtoIn The input DTO with updated values
     * @return The updated preferences as a DTO
     */
    @Transactional
    public UserPreferencesDtoOut updateCurrentUserPreferencesAsDto(UserPreferencesDtoIn dtoIn) {
        UserPreferences entity = updateCurrentUserPreferences(dtoIn);
        return toDtoInternal(entity);
    }

    /**
     * Patches the current user's preferences and returns as DTO.
     *
     * @param updates The map of fields to update
     * @return The patched preferences as a DTO
     */
    @Transactional
    public UserPreferencesDtoOut patchCurrentUserPreferencesAsDto(Map<String, Object> updates) {
        UserPreferences entity = patchCurrentUserPreferences(updates);
        return toDtoInternal(entity);
    }

    /**
     * Gets user preferences by user ID and returns as DTO.
     *
     * @param userId The user ID
     * @return The user's preferences as a DTO
     */
    @Transactional(readOnly = true)
    public UserPreferencesDtoOut getUserPreferencesAsDto(Long userId) {
        UserPreferences entity = getUserPreferences(userId);
        return toDtoInternal(entity);
    }

    /**
     * Patches user preferences by user ID and returns as DTO.
     *
     * @param userId  The user ID
     * @param updates The map of fields to update
     * @return The patched preferences as a DTO
     */
    @Transactional
    public UserPreferencesDtoOut patchUserPreferencesAsDto(Long userId, Map<String, Object> updates) {
        UserPreferences entity = patchUserPreferences(userId, updates);
        return toDtoInternal(entity);
    }

    /**
     * Retrieves a paginated list of entities as DTOs with all conversions done within transaction.
     * This prevents LazyInitializationException by ensuring all DTO mappings happen inside @Transactional.
     *
     * @param pageable Pagination parameters
     * @param filters  Filter parameters
     * @param <O>      The output DTO type
     * @return Page of DTOs with all lazy relationships properly loaded
     */
    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <O> Page<O> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
        // Get entities with proper pagination and filtering
        Page<UserPreferences> page = getDataPagedAndFiltered(pageable, filters);

        // Convert to DTOs within transaction boundary
        // Type-safe: We know we're converting to UserPreferencesDtoOut
        Page<UserPreferencesDtoOut> dtoPage = page.map(this::toDtoInternal);
        return (Page<O>) dtoPage;
    }
}