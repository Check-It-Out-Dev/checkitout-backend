package com.sm.instagram.platform.consent;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service class for consent management operations.
 * Handles GDPR-compliant consent tracking for users.
 */
@Service
@Transactional
@Slf4j
public class ConsentService {

    public static final String USER_NOT_FOUND = "User not found";
    public static final String DESC = "_DESC";
    private final ConsentDefinitionRepository consentDefinitionRepository;
    private final ConsentVersionRepository consentVersionRepository;
    private final UserConsentRepository userConsentRepository;
    private final UserCurrentConsentRepository userCurrentConsentRepository;
    private final UserRepository userRepository;
    private final PermissionUtils permissionUtils;
    private final HttpServletRequest request;
    private final ApplicationContext applicationContext;
    private final DictionaryService dictionaryService;
    private final TranslationService translationService;

    @Autowired
    public ConsentService(ConsentDefinitionRepository consentDefinitionRepository,
                          ConsentVersionRepository consentVersionRepository,
                          UserConsentRepository userConsentRepository,
                          UserCurrentConsentRepository userCurrentConsentRepository,
                          UserRepository userRepository,
                          PermissionUtils permissionUtils,
                          HttpServletRequest request,
                          ApplicationContext applicationContext,
                          DictionaryService dictionaryService,
                          TranslationService translationService) {
        this.consentDefinitionRepository = consentDefinitionRepository;
        this.consentVersionRepository = consentVersionRepository;
        this.userConsentRepository = userConsentRepository;
        this.userCurrentConsentRepository = userCurrentConsentRepository;
        this.userRepository = userRepository;
        this.permissionUtils = permissionUtils;
        this.request = request;
        this.applicationContext = applicationContext;
        this.dictionaryService = dictionaryService;
        this.translationService = translationService;
    }

    /**
     * Gets the proxied instance of this service to ensure @Transactional methods work correctly.
     * This avoids circular dependency issues while ensuring proper transaction management.
     */
    protected ConsentService getSelf() {
        return applicationContext.getBean(ConsentService.class);
    }

    /**
     * Get locale from Accept-Language header with fallback for null request
     */
    private Locale getLocaleFromRequest() {
        try {
            if (request != null) {
                String acceptLanguageHeader = request.getHeader("Accept-Language");
                if (acceptLanguageHeader != null && !acceptLanguageHeader.isEmpty()) {
                    // Parse the first language from Accept-Language header
                    String language = acceptLanguageHeader.split(",")[0].split(";")[0].trim();
                    return Locale.forLanguageTag(language);
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine locale from request header, using default", e);
        }
        // Default to Polish if request is null or any error occurs
        return Locale.forLanguageTag("pl");
    }

    /**
     * Maps ConsentAction to ConsentActionDtoOut with translations
     */
    private ConsentActionDtoOut mapConsentAction(ConsentAction action) {
        if (action == null) return null;

        Locale locale = getLocaleFromRequest();
        String label = action.name();
        String description = action.getDescription();

        // Try to get translated values, with fallbacks if services are null
        try {
            if (dictionaryService != null) {
                label = action.getLabel(dictionaryService, locale);
                description = action.getDescription(dictionaryService, locale);
            }
        } catch (Exception e) {
            log.warn("Could not get translated labels for ConsentAction, using defaults", e);
        }

        return ConsentActionDtoOut.builder()
                .value(action.name())
                .label(label)
                .description(description)
                .originalLabel(action.name())
                .colorTheme(action.getColorTheme())
                .icon(action.getIcon())
                .isPositiveAction(action.isPositiveAction())
                .isNegativeAction(action.isNegativeAction())
                .isModificationAction(action.isModificationAction())
                .build();
    }

    /**
     * Maps collection method string to CollectionMethodDtoOut with translations
     */
    private CollectionMethodDtoOut mapCollectionMethod(String collectionMethod) {
        if (collectionMethod == null) return null;

        Locale locale = getLocaleFromRequest();
        String translatedLabel = collectionMethod;
        String translatedDescription = null;

        // Try to get translated values, with fallbacks if services are null
        try {
            if (translationService != null) {
                translatedLabel = translationService.translateCollectionMethod(collectionMethod, locale);
            }
            if (dictionaryService != null) {
                translatedDescription = dictionaryService.getTranslation(
                        "COLLECTION_METHOD_" + collectionMethod.toUpperCase() + DESC,
                        locale.getLanguage()
                ).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Could not get translated labels for CollectionMethod, using defaults", e);
        }

        return CollectionMethodDtoOut.builder()
                .value(collectionMethod)
                .label(translatedLabel)
                .description(translatedDescription)
                .originalLabel(collectionMethod)
                .build();
    }

    /**
     * Maps legal basis string to LegalBasisDtoOut with translations
     */
    private LegalBasisDtoOut mapLegalBasis(String legalBasis) {
        if (legalBasis == null) return null;

        Locale locale = getLocaleFromRequest();
        String translatedLabel = legalBasis;
        String translatedDescription = null;

        // Try to get translated values, with fallbacks if services are null
        try {
            if (translationService != null) {
                translatedLabel = translationService.translateLegalBasis(legalBasis, locale);
            }
            if (dictionaryService != null) {
                translatedDescription = dictionaryService.getTranslation(
                        "LEGAL_BASIS_" + legalBasis.toUpperCase() + DESC,
                        locale.getLanguage()
                ).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Could not get translated labels for LegalBasis, using defaults", e);
        }

        return LegalBasisDtoOut.builder()
                .value(legalBasis)
                .label(translatedLabel)
                .description(translatedDescription)
                .originalLabel(legalBasis)
                .build();
    }

    /**
     * Maps consent type string to ConsentTypeDtoOut with translations
     */
    private ConsentTypeDtoOut mapConsentType(String consentType) {
        if (consentType == null) return null;

        Locale locale = getLocaleFromRequest();
        String translatedLabel = consentType;
        String translatedDescription = null;

        // Try to get translated values, with fallbacks if services are null
        try {
            if (translationService != null) {
                translatedLabel = translationService.translateConsentType(consentType, locale);
            }
            if (dictionaryService != null) {
                translatedDescription = dictionaryService.getTranslation(
                        "CONSENT_TYPE_" + consentType.toUpperCase() + DESC,
                        locale.getLanguage()
                ).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Could not get translated labels for ConsentType, using defaults", e);
        }

        return ConsentTypeDtoOut.builder()
                .value(consentType)
                .label(translatedLabel)
                .description(translatedDescription)
                .originalLabel(consentType)
                .build();
    }

    /**
     * Maps UserConsent to UserConsentDtoOut with translated action, collectionMethod, and legalBasis
     */
    private UserConsentDtoOut mapUserConsentToDto(UserConsent consent) {
        UserConsentDtoOut dto = new UserConsentDtoOut();

        // Map basic fields manually
        dto.setId(consent.getId());
        dto.setUserId(consent.getUser().getId());
        dto.setConsentGiven(consent.getConsentGiven());
        dto.setCreatedAt(consent.getCreatedAt());

        // Map ConsentVersion manually to avoid ModelMapper dependency
        if (consent.getConsentVersion() != null) {
            dto.setConsentVersion(mapConsentVersionToDto(consent.getConsentVersion()));
        }

        // Translate complex fields using helper methods
        dto.setAction(mapConsentAction(consent.getAction()));
        dto.setCollectionMethod(mapCollectionMethod(consent.getCollectionMethod()));
        dto.setLegalBasis(mapLegalBasis(consent.getLegalBasis()));

        return dto;
    }

    /**
     * Maps ConsentVersion to ConsentVersionDtoOut manually
     */
    private ConsentVersionDtoOut mapConsentVersionToDto(ConsentVersion consentVersion) {
        if (consentVersion == null) return null;

        ConsentVersionDtoOut dto = new ConsentVersionDtoOut();
        dto.setId(consentVersion.getId());
        dto.setConsentDefinitionId(consentVersion.getConsentDefinition().getId());
        dto.setVersion(consentVersion.getVersion());
        dto.setConsentText(consentVersion.getConsentText());
        dto.setPolicyUrl(consentVersion.getPolicyUrl());
        dto.setEffectiveFrom(consentVersion.getEffectiveFrom());
        dto.setEffectiveUntil(consentVersion.getEffectiveUntil());
        dto.setCreatedAt(consentVersion.getCreatedAt());

        return dto;
    }

    /**
     * Maps ConsentDefinition to ConsentDefinitionDtoOut manually
     */
    private ConsentDefinitionDtoOut mapConsentDefinitionToDto(ConsentDefinition consentDefinition) {
        if (consentDefinition == null) return null;

        ConsentDefinitionDtoOut dto = new ConsentDefinitionDtoOut();
        dto.setId(consentDefinition.getId());
        dto.setConsentType(consentDefinition.getConsentType());
        dto.setName(consentDefinition.getName());
        dto.setDescription(consentDefinition.getDescription());
        dto.setRegulationReference(consentDefinition.getRegulationReference());
        dto.setIsActive(consentDefinition.getIsActive());
        dto.setCreatedAt(consentDefinition.getCreatedAt());
        dto.setUpdatedAt(consentDefinition.getUpdatedAt());

        // Note: We don't populate the versions list here to avoid potential circular references
        // If needed, this could be added as a separate method or parameter

        return dto;
    }

    // ===== ADMIN OPERATIONS =====

    /**
     * Creates a new consent definition (admin only).
     */
    @Transactional
    public ConsentDefinitionDtoOut createConsentDefinition(ConsentDefinitionDtoIn dtoIn) {
        if (!permissionUtils.isAdmin()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "createConsentDefinition",
                    "ConsentDefinition");
        }

        if (consentDefinitionRepository.existsByConsentType(dtoIn.getConsentType())) {
            throw new BusinessRuleTranslatableException("error.business.duplicate_entry", "Consent type");
        }

        // Manual mapping to avoid ModelMapper dependency
        ConsentDefinition definition = new ConsentDefinition();
        definition.setConsentType(dtoIn.getConsentType());
        definition.setName(dtoIn.getName());
        definition.setDescription(dtoIn.getDescription());
        definition.setRegulationReference(dtoIn.getRegulationReference());
        definition.setIsActive(dtoIn.getIsActive() != null ? dtoIn.getIsActive() : true);
        definition.setUpdaterId(permissionUtils.getUserId());

        ConsentDefinition saved = consentDefinitionRepository.save(definition);
        log.info("GDPR: Created consent definition: type={}, id={}", saved.getConsentType(), saved.getId());

        return mapConsentDefinitionToDto(saved);
    }

    /**
     * Creates a new consent version (admin only).
     */
    @Transactional
    public ConsentVersionDtoOut createConsentVersion(ConsentVersionDtoIn dtoIn) {
        if (!permissionUtils.isAdmin()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "createConsentVersion",
                    "ConsentVersion");
        }

        ConsentDefinition definition = consentDefinitionRepository.findById(dtoIn.getConsentDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Consent definition"));

        // Set current version to expire if this is effective now
        if (dtoIn.getEffectiveFrom().isBefore(LocalDateTime.now()) || dtoIn.getEffectiveFrom().isEqual(LocalDateTime.now())) {
            Optional<ConsentVersion> currentVersion = consentVersionRepository
                    .findCurrentVersionByDefinitionId(definition.getId(), LocalDateTime.now());

            if (currentVersion.isPresent() && currentVersion.get().getEffectiveUntil() == null) {
                currentVersion.get().setEffectiveUntil(dtoIn.getEffectiveFrom());
                consentVersionRepository.save(currentVersion.get());
            }
        }

        // Manual mapping to avoid ModelMapper dependency
        ConsentVersion version = new ConsentVersion();
        version.setConsentDefinition(definition);
        version.setVersion(dtoIn.getVersion());
        version.setConsentText(dtoIn.getConsentText());
        version.setPolicyUrl(dtoIn.getPolicyUrl());
        version.setEffectiveFrom(dtoIn.getEffectiveFrom());
        version.setEffectiveUntil(dtoIn.getEffectiveUntil());
        version.setUpdaterId(permissionUtils.getUserId());

        ConsentVersion saved = consentVersionRepository.save(version);
        log.info("GDPR: Created consent version: type={}, version={}, id={}",
                definition.getConsentType(), saved.getVersion(), saved.getId());

        return mapConsentVersionToDto(saved);
    }

    /**
     * Gets all consent definitions (admin only).
     */
    @Transactional(readOnly = true)
    public List<ConsentDefinitionDtoOut> getAllConsentDefinitions() {
        if (!permissionUtils.isAdmin()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "getAllConsentDefinitions",
                    "ConsentDefinitions");
        }

        return consentDefinitionRepository.findByIsActiveTrueOrderByConsentType().stream()
                .map(this::mapConsentDefinitionToDto)
                .toList();
    }

    // ===== USER OPERATIONS =====

    /**
     * Gets available consent types for the current authenticated user.
     */
    @Transactional(readOnly = true)
    public List<UserCurrentConsentDtoOut> getMyAvailableConsents() {
        String firebaseUid = permissionUtils.getUserId();
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
        return getSelf().getAvailableConsentsForUser(user.getId());
    }

    /**
     * Gets available consent types for a user to consent to.
     */
    @Transactional(readOnly = true)
    public List<UserCurrentConsentDtoOut> getAvailableConsentsForUser(Long userId) {
        validateUserAccess(userId);

        List<ConsentDefinition> activeDefinitions = consentDefinitionRepository.findByIsActiveTrueOrderByConsentType();
        LocalDateTime now = LocalDateTime.now();

        return activeDefinitions.stream().map(definition -> {
            UserCurrentConsentDtoOut dto = new UserCurrentConsentDtoOut();
            dto.setUserId(userId);

            // Use the translated ConsentType DTO
            dto.setConsentType(mapConsentType(definition.getConsentType()));

            dto.setName(definition.getName());
            dto.setDescription(definition.getDescription());

            // Get current version
            Optional<ConsentVersion> currentVersion = consentVersionRepository
                    .findCurrentVersionByDefinitionId(definition.getId(), now);

            if (currentVersion.isPresent()) {
                dto.setCurrentVersion(currentVersion.get().getVersion());
                dto.setConsentText(currentVersion.get().getConsentText());
                dto.setPolicyUrl(currentVersion.get().getPolicyUrl());
            }

            // Get current consent status
            Optional<UserCurrentConsent> currentConsent = userCurrentConsentRepository
                    .findByUserIdAndConsentDefinitionId(userId, definition.getId());

            if (currentConsent.isPresent()) {
                dto.setConsentGiven(currentConsent.get().getConsentGiven());
                dto.setGrantedAt(currentConsent.get().getGrantedAt());
                dto.setWithdrawnAt(currentConsent.get().getWithdrawnAt());
                dto.setLastUpdated(currentConsent.get().getLastUpdated());
            } else {
                dto.setConsentGiven(false);
                dto.setLastUpdated(now);
            }

            return dto;
        }).toList();
    }

    /**
     * Records consent for the current authenticated user.
     */
    @Transactional
    public UserConsentDtoOut recordMyConsent(UserConsentDtoIn dtoIn) {
        String firebaseUid = permissionUtils.getUserId();
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
        return getSelf().recordUserConsentForUser(user.getId(), dtoIn);
    }

    /**
     * Records user consent for a specific consent type.
     */
    @Transactional
    public UserConsentDtoOut recordUserConsentForUser(Long userId, UserConsentDtoIn dtoIn) {
        validateUserAccess(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // Get current version of the consent
        Optional<ConsentVersion> currentVersion = consentVersionRepository
                .findCurrentVersionByConsentType(dtoIn.getConsentType(), LocalDateTime.now());

        if (currentVersion.isEmpty()) {
            throw new ResourceNotFoundException("error.business.item_not_found", "Consent version");
        }

        ConsentVersion version = currentVersion.get();

        // Create new consent record
        UserConsent consent = new UserConsent();
        consent.setUser(user);
        consent.setConsentVersion(version);
        consent.setAction(Boolean.TRUE.equals(dtoIn.getConsentGiven()) ? ConsentAction.GRANTED : ConsentAction.WITHDRAWN);
        consent.setConsentGiven(dtoIn.getConsentGiven());
        consent.setIpAddress(getClientIpAddress());
        consent.setUserAgent(dtoIn.getUserAgent());
        consent.setCollectionMethod(dtoIn.getCollectionMethod());
        consent.setLegalBasis("consent");
        consent.setUpdaterId(permissionUtils.getUserId());

        UserConsent savedConsent = userConsentRepository.save(consent);

        // Update current consent status
        updateCurrentConsentStatus(userId, version.getConsentDefinition().getId(), version, dtoIn.getConsentGiven());

        log.info("GDPR: User consent recorded: userId={}, consentType={}, consentGiven={}, action={}",
                userId, dtoIn.getConsentType(), dtoIn.getConsentGiven(), consent.getAction());

        return getSelf().mapUserConsentToDto(savedConsent);
    }

    /**
     * Gets current user's consent history for a specific consent type.
     */
    @Transactional(readOnly = true)
    public List<UserConsentDtoOut> getMyConsentHistory(String consentType) {
        String firebaseUid = permissionUtils.getUserId();
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
        return getSelf().getUserConsentHistoryForUser(user.getId(), consentType);
    }

    /**
     * Gets user's consent history for a specific consent type.
     */
    @Transactional(readOnly = true)
    public List<UserConsentDtoOut> getUserConsentHistoryForUser(Long userId, String consentType) {
        validateUserAccess(userId);

        List<UserConsent> history = userConsentRepository.findByUserIdAndConsentType(userId, consentType);

        return history.stream()
                .map(consent -> getSelf().mapUserConsentToDto(consent))
                .toList();
    }

    /**
     * Gets all of current user's consent statuses.
     */
    @Transactional(readOnly = true)
    public List<UserCurrentConsentDtoOut> getMyCurrentConsents() {
        return getSelf().getMyAvailableConsents();
    }

    /**
     * Gets all of user's current consent statuses.
     */
    @Transactional(readOnly = true)
    public List<UserCurrentConsentDtoOut> getUserCurrentConsentsForUser(Long userId) {
        validateUserAccess(userId);
        return getSelf().getAvailableConsentsForUser(userId);
    }

    // ===== HELPER METHODS =====

    /**
     * Validates that the current user can access the specified user's consent data.
     */
    private void validateUserAccess(Long userId) {
        if (!permissionUtils.isAdmin()) {
            // Check if user is accessing their own data
            String currentFirebaseId = permissionUtils.getUserId();
            User requestedUser = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

            if (!currentFirebaseId.equals(requestedUser.getFirebaseUserId())) {
                throw new InsufficientPermissionsException(
                        "error.auth.insufficient_permissions",
                        currentFirebaseId,
                        "validateUserAccess",
                        "User#" + userId);
            }
        }
    }

    /**
     * Updates or creates the current consent status for a user.
     */
    private void updateCurrentConsentStatus(Long userId, Long consentDefinitionId, ConsentVersion version, Boolean consentGiven) {
        Optional<UserCurrentConsent> existing = userCurrentConsentRepository
                .findByUserIdAndConsentDefinitionId(userId, consentDefinitionId);

        UserCurrentConsent currentConsent;
        if (existing.isPresent()) {
            currentConsent = existing.get();
        } else {
            currentConsent = new UserCurrentConsent();
            currentConsent.setUserId(userId);
            currentConsent.setConsentDefinitionId(consentDefinitionId);
        }

        currentConsent.setConsentVersion(version);
        currentConsent.setConsentGiven(consentGiven);
        currentConsent.setLastUpdated(LocalDateTime.now());

        if (Boolean.TRUE.equals(consentGiven)) {
            currentConsent.setGrantedAt(LocalDateTime.now());
            currentConsent.setWithdrawnAt(null);
        } else {
            currentConsent.setWithdrawnAt(LocalDateTime.now());
        }

        userCurrentConsentRepository.save(currentConsent);
    }

    /**
     * Gets the client's IP address from the request.
     */
    private InetAddress getClientIpAddress() {
        try {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // Take the first IP in case of multiple
                String firstIp = xForwardedFor.split(",")[0].trim();
                return InetAddress.getByName(firstIp);
            }

            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return InetAddress.getByName(xRealIp);
            }

            return InetAddress.getByName(request.getRemoteAddr());
        } catch (UnknownHostException e) {
            log.warn("Could not determine client IP address", e);
            try {
                return InetAddress.getByName("0.0.0.0");
            } catch (UnknownHostException ex) {
                return null;
            }
        }
    }
}
