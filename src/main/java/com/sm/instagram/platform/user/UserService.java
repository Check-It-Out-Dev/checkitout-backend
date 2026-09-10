package com.sm.instagram.platform.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.address.AddressDtoOut;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.service.EmailChangeService;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.notification.event.AccountActivatedEvent;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionDtoOut;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service class for user management operations.
 */
@Service
@Transactional
@Slf4j
public class UserService extends BaseService<User, Long, UserDtoIn> {

    private final AddressRepository addressRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PermissionUtils permissionUtils;
    private final UserSocialConnectionService userSocialConnectionService;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseService firebaseService;
    private final DictionaryService dictionaryService;
    private final HttpServletRequest request;
    private final UserAccountOrchestrator userAccountOrchestrator;
    private final UserCacheService userCacheService;
    private final EmailChangeService emailChangeService;
    private final EmailVerificationService emailVerificationService;
    private final com.sm.instagram.platform.legal.LegalConsentService legalConsentService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.sm.instagram.platform.storage.service.SignedUrlService signedUrlService;

    public UserService(ApplicationContext applicationContext,
                       SpecificationBuilder<User> specificationBuilder,
                       UserRepository repository,
                       ModelMapper modelMapper,
                       RepositoryResolver repositoryResolver,
                       AddressRepository addressRepository,
                       ObjectMapper objectMapper,
                       PermissionUtils permissionUtils,
                       UserSocialConnectionService userSocialConnectionService,
                       FirebaseAuth firebaseAuth,
                       FirebaseService firebaseService,
                       DictionaryService dictionaryService,
                       HttpServletRequest request,
                       UserAccountOrchestrator userAccountOrchestrator,
                       UserCacheService userCacheService,
                       EmailChangeService emailChangeService,
                       EmailVerificationService emailVerificationService,
                       com.sm.instagram.platform.legal.LegalConsentService legalConsentService,
                       ApplicationEventPublisher eventPublisher,
                       com.sm.instagram.platform.storage.service.SignedUrlService signedUrlService) {
        super(applicationContext, specificationBuilder, repository, modelMapper, repositoryResolver);
        this.addressRepository = addressRepository;
        this.objectMapper = objectMapper;
        this.userRepository = repository;
        this.permissionUtils = permissionUtils;
        this.userSocialConnectionService = userSocialConnectionService;
        this.firebaseAuth = firebaseAuth;
        this.firebaseService = firebaseService;
        this.dictionaryService = dictionaryService;
        this.request = request;
        this.userAccountOrchestrator = userAccountOrchestrator;
        this.userCacheService = userCacheService;
        this.emailChangeService = emailChangeService;
        this.emailVerificationService = emailVerificationService;
        this.legalConsentService = legalConsentService;
        this.eventPublisher = eventPublisher;
        this.signedUrlService = signedUrlService;
    }

    @Override
    protected UserService getSelf() {
        return (UserService) super.getSelf();
    }

    /**
     * Get locale from Accept-Language header
     */
    private Locale getLocaleFromRequest() {
        String acceptLanguageHeader = request.getHeader("Accept-Language");
        if (acceptLanguageHeader != null && !acceptLanguageHeader.isEmpty()) {
            String language = acceptLanguageHeader.split(",")[0].split(";")[0].trim();
            return Locale.forLanguageTag(language);
        }
        return Locale.forLanguageTag("pl");
    }

    public String getCurrentFirebaseUserId() {
        return permissionUtils.getUserId();
    }

    // ===== CREATE =====

    @Transactional(rollbackFor = Exception.class)
    public User save(UserDtoIn userDtoIn) {
        String firebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=createUser, FirebaseUID={}, Email={}, UserType={}, Purpose=account_creation, LegalBasis=contract, DataCreated=user.profile,user.addresses",
                firebaseUid, userDtoIn.getEmail(), userDtoIn.getUserType());

        User user = modelMapper.map(userDtoIn, User.class);
        user.setFirebaseUserId(firebaseUid);
        user.setAddresses(processAddresses(user, userDtoIn));

        if (!permissionUtils.isAdmin()) {
            user.setAccountStatus(AccountStatus.IN_VALIDATION);
        }
        handleStatusAndRoleChange(user);

        User savedUser = userRepository.save(getSelf().updateEntityUpdater(user));

        log.info("GDPR: Operation=createUser_SUCCESS, FirebaseUID={}, UserID={}, AccountStatus={}",
                firebaseUid, savedUser.getId(), savedUser.getAccountStatus());
        return savedUser;
    }

    // ===== READ =====

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        String accessorFirebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=findUserById, AccessorFirebaseUID={}, TargetUserID={}, Purpose=user_data_retrieval, DataAccessed=user.profile,user.addresses,user.socialConnections",
                accessorFirebaseUid, id);

        User user = userRepository.findByIdWithAssociationsFetched(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        return checkPermissionsAndReturnUser(user);
    }

    public User viewById(Long id) {
        return userRepository.findByIdWithAssociationsFetched(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
    }

    public User findByFirebaseUserId(String firebaseId) {
        return userRepository.findByFirebaseUserId(firebaseId)
                .map(this::checkPermissionsAndReturnUser)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
    }

    public User findByFirebaseUserIdNoPermissionCheck(String firebaseId) {
        return userRepository.findByFirebaseUserId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
    }

    // ===== UPDATE =====

    @Override
    @Transactional
    public User update(Long id, UserDtoIn dtoIn) {
        String updaterFirebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=updateUser, UpdaterFirebaseUID={}, TargetUserID={}, Purpose=profile_update, DataModified=user.profile,user.addresses, LegalBasis=consent",
                updaterFirebaseUid, id);

        User user = getSelf().findById(id);
        validateUserUpdatePermission(user);

        // Check for critical field changes BEFORE applying updates
        boolean hasCriticalChanges = hasCriticalFieldChangesFromDto(user, dtoIn);

        // Track email change for Firebase sync and verification
        String oldEmail = user.getEmail();
        String newEmail = dtoIn.getEmail();
        boolean emailChanging = !Objects.equals(newEmail, oldEmail) && newEmail != null && !newEmail.trim().isEmpty();

        // Handle email change via EmailChangeService BEFORE modelMapper
        // (validates, sets PG fields, flushes, updates Firebase, evicts cache, sends verification)
        if (emailChanging) {
            emailChangeService.changeEmail(user, newEmail);
        }

        String firebaseUserId = user.getFirebaseUserId();

        ModelMapper updateMapper = new ModelMapper();
        updateMapper.getConfiguration().setSkipNullEnabled(true);
        updateMapper.typeMap(UserDtoIn.class, User.class)
                .addMappings(mapper -> {
                    mapper.skip(User::setAddresses);
                    // SECURITY (pentest 3.1): avatar is never set via a full
                    // update — only the gated PATCH field-router. Preserves the
                    // existing (possibly OAuth) avatar; blocks attacker-host
                    // substitution through PUT /users/{id}. See UserMapping.
                    mapper.skip(User::setProfilePicture);
                });
        updateMapper.map(dtoIn, user);

        user.setFirebaseUserId(firebaseUserId);

        if (dtoIn.getAddresses() != null) {
            if (!dtoIn.getAddresses().isEmpty()) {
                updateUserAddresses(user, dtoIn.getAddresses());
            } else {
                user.getAddresses().clear();
            }
        } else if (dtoIn.getAddressesIds() != null) {
            if (!dtoIn.getAddressesIds().isEmpty()) {
                handleAddressIdsUpdate(user, dtoIn.getAddressesIds());
            } else {
                user.getAddresses().clear();
            }
        }

        // Only trigger re-verification for critical field changes (deferred Firebase)
        List<Runnable> deferredFirebaseActions = new ArrayList<>();
        handleStatusAndRoleChange(user, hasCriticalChanges, deferredFirebaseActions);

        User updatedUser = userRepository.saveAndFlush(getSelf().updateEntityUpdater(user));
        log.info("GDPR: Operation=updateUser_SUCCESS, UpdaterFirebaseUID={}, UserID={}, AccountStatus={}, CriticalChanges={}",
                updaterFirebaseUid, updatedUser.getId(), updatedUser.getAccountStatus(), hasCriticalChanges);

        // Execute deferred Firebase actions AFTER PG save succeeded.
        // Best-effort: a target-side Firebase claim failure must NOT fail this request (it maps to a
        // 401 and logs the *caller* out) nor roll back the PG change. PG is the source of truth; the
        // target's bumped tokenVersion forces a claim re-sync on their next token refresh.
        runDeferredFirebaseActions(deferredFirebaseActions);

        return updatedUser;
    }

    @Transactional
    public User updatePremiumStatus(Long id, Boolean premium) {
        if (!permissionUtils.isAdmin()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "updatePremiumStatus",
                    "User#" + id);
        }
        if (premium == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "premium");
        }

        String adminFirebaseUid = permissionUtils.getUserId();
        log.warn("GDPR: Operation=setPremiumStatus, AdminFirebaseUID={}, TargetUserID={}, DataModified=premium_status, NewValue={}, Purpose=admin_account_management",
                adminFirebaseUid, id, premium);

        User user = getSelf().findById(id);
        Boolean oldPremium = user.getPremium();
        user.setPremium(premium);

        User updated = userRepository.save(getSelf().updateEntityUpdater(user));
        log.info("Admin updated premium status for user {}: {} -> {}", id, oldPremium, premium);
        return updated;
    }

    @Override
    @Transactional
    public User patch(Long id, Map<String, Object> updates) {
        String updaterFirebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=patchUser, UpdaterFirebaseUID={}, TargetUserID={}, FieldsModified={}, Purpose=partial_profile_update, LegalBasis=consent",
                updaterFirebaseUid, id, updates.keySet());

        User existingUser = getUserToUpdate(id);
        validateUserUpdatePermission(existingUser);

        // Check for critical field changes BEFORE applying updates
        boolean hasCriticalChanges = hasCriticalFieldChanges(existingUser, updates);

        // Collect deferred Firebase actions — executed AFTER PG save to maintain consistency
        List<Runnable> deferredFirebaseActions = new ArrayList<>();

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            processUserUpdateField(existingUser, entry.getKey(), entry.getValue(), updates, deferredFirebaseActions);
        }

        // Only trigger re-verification for critical field changes (deferred Firebase)
        handleStatusAndRoleChange(existingUser, hasCriticalChanges, deferredFirebaseActions);

        // Activation trigger: if setup incomplete, check if now email verified + profile complete
        if (!Boolean.TRUE.equals(existingUser.getInitialAccountSetupCompleted())
                && Boolean.TRUE.equals(existingUser.getEmailVerified())
                && checkProfileCompleteness(existingUser).isComplete()) {
            existingUser.setInitialAccountSetupCompleted(true);
            log.info("Initial account setup completed for user: {} (profile update triggered)", existingUser.getFirebaseUserId());
        }

        // Auto-activate IN_VALIDATION users who just became eligible by completing their profile
        // (verify-first onboarding). Idempotent; never reactivates non-IN_VALIDATION accounts.
        if (emailVerificationService.evaluateAutoActivation(existingUser, deferredFirebaseActions)) {
            userCacheService.evict(existingUser.getFirebaseUserId());
            log.info("User {} auto-activated via profile completion", existingUser.getFirebaseUserId());
        }

        User updatedUser = userRepository.save(getSelf().updateEntityUpdater(existingUser));
        log.info("Patched user with ID: {} (critical changes: {})", updatedUser.getId(), hasCriticalChanges);

        // Execute deferred Firebase actions AFTER PG save succeeded.
        // Best-effort: a target-side Firebase claim failure must NOT fail this request (it maps to a
        // 401 and logs the *caller* out) nor roll back the PG change. PG is the source of truth; the
        // target's bumped tokenVersion forces a claim re-sync on their next token refresh.
        runDeferredFirebaseActions(deferredFirebaseActions);

        return updatedUser;
    }

    // ===== DELETE =====

    @Override
    @Transactional
    public void delete(Long id) {
        String deleterFirebaseUid = permissionUtils.getUserId();
        log.warn("GDPR: DELETION_REQUEST Operation=deleteUser, DeleterFirebaseUID={}, TargetUserID={}, Purpose=account_deletion, LegalBasis=user_request",
                deleterFirebaseUid, id);

        if (validateUserUpdatePermission(getSelf().findById(id))) {
            User user = getSelf().findById(id);
            userAccountOrchestrator.archiveUser(user);
            log.info("User with ID {} marked for deletion", id);
            log.warn("GDPR: DELETION_ARCHIVED Operation=deleteUser_ARCHIVED, DeleterFirebaseUID={}, UserID={}, Status=TO_BE_DELETED",
                    deleterFirebaseUid, id);
        }
    }

    @Transactional
    public void deletePermanently(Long id) {
        String adminFirebaseUid = permissionUtils.getUserId();
        log.warn("GDPR: PERMANENT_DELETION Operation=deletePermanently, AdminFirebaseUID={}, TargetUserID={}, Purpose=permanent_removal, LegalBasis=legal_requirement",
                adminFirebaseUid, id);

        if (!permissionUtils.isAdmin()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    adminFirebaseUid,
                    "deletePermanently",
                    "User#" + id);
        }

        User userToDelete = getSelf().findById(id);
        String targetFirebaseUid = userToDelete.getFirebaseUserId();

        // Evict from cache BEFORE deletion to immediately block authentication
        if (targetFirebaseUid != null) {
            userCacheService.evict(targetFirebaseUid);
            log.info("SECURITY: Cache evicted for user {} before permanent deletion", id);
        }

        repository.delete(userToDelete);
        repository.flush();

        // Delete from Firebase AFTER PG commit succeeds (irreversible)
        if (targetFirebaseUid != null) {
            firebaseService.deleteUserGraceful(targetFirebaseUid);
            log.info("GDPR: Firebase user deleted for permanent deletion - uid: {}", targetFirebaseUid);
        }

        log.warn("GDPR: PERMANENT_DELETION_COMPLETE Operation=deletePermanently_SUCCESS, AdminFirebaseUID={}, DeletedUserID={}, DeletedFirebaseUID={}, DataRemoved=ALL_USER_DATA",
                adminFirebaseUid, id, targetFirebaseUid);
    }

    // ===== Permissions =====

    private boolean validateUserUpdatePermission(User user) {
        // Every caller reaches this with a user that exists -- findById throws rather than returning
        // null -- but the analyser cannot see through orElseThrow and flagged the dereference below
        // (javabugs:S2259). Stating the precondition is cheaper than arguing with it, and a future
        // caller that does pass null gets told where it went wrong rather than a bare NPE.
        Objects.requireNonNull(user, "validateUserUpdatePermission requires a user");
        if (!permissionUtils.isAdmin() && !permissionUtils.isUserOwner(user)) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "updateUser",
                    "User#" + user.getId());
        }
        return true;
    }

    public User checkPermissionsAndReturnUser(User user) {
        validateUserUpdatePermission(user);
        return user;
    }

    public User checkPermissionsAndReturnUser(String firebaseId) {
        User user = userRepository.findByFirebaseUserId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
        validateUserUpdatePermission(user);
        return user;
    }

    public User getUserWithInitializedCollections(String firebaseId) {
        User user = userRepository.findByFirebaseUserId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        return userRepository.findByIdWithAssociationsFetched(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
    }

    // ===== Helpers =====

    private User getUserToUpdate(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
    }

    private List<Address> processAddresses(User user, UserDtoIn userDtoIn) {
        List<Address> allAddresses = new ArrayList<>();

        if (userDtoIn.getAddressesIds() != null && !userDtoIn.getAddressesIds().isEmpty()) {
            List<Address> addresses = fetchAndValidateAddresses(userDtoIn.getAddressesIds());
            addresses.forEach(address -> address.setUser(user));
            allAddresses.addAll(addresses);
        }

        if (userDtoIn.getAddresses() != null && !userDtoIn.getAddresses().isEmpty()) {
            for (AddressDtoIn addressDto : userDtoIn.getAddresses()) {
                Address address = modelMapper.map(addressDto, Address.class);
                address.setUser(user);
                allAddresses.add(address);
            }
        }

        return allAddresses;
    }

    private List<Address> fetchAndValidateAddresses(List<Long> addressIds) {
        if (addressIds == null || addressIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Address> addresses = addressRepository.findAllById(addressIds);
        if (addresses.size() != addressIds.size()) {
            throw new ResourceNotFoundException("error.business.item_not_found", "Address");
        }
        return addresses;
    }

    private void updateUserAddresses(User user, List<AddressDtoIn> addressDtos) {
        if (addressDtos == null || addressDtos.isEmpty()) return;

        List<Address> currentAddresses = initializeAddressList(user);
        currentAddresses.clear();

        for (AddressDtoIn dto : addressDtos) {
            Address address = null;
            if (dto.getId() != null) {
                Optional<Address> existingAddress = addressRepository.findById(dto.getId());
                if (existingAddress.isPresent()) {
                    address = existingAddress.get();
                    modelMapper.map(dto, address);
                }
            }
            if (address == null) {
                address = new Address();
                modelMapper.map(dto, address);
            }
            address.setUser(user);
            currentAddresses.add(address);
        }
    }

    private List<Address> initializeAddressList(User user) {
        List<Address> addresses = user.getAddresses();
        if (addresses == null) {
            addresses = new ArrayList<>();
            user.setAddresses(addresses);
        }
        return addresses;
    }

    /**
     * True when {@code incoming} would ACTUALLY change the stored email of
     * {@code userId}, under the same normalization {@link EmailChangeService}
     * applies (trim + lowercase). The controller's step-up gate calls this so
     * that full-DTO updates carrying the caller's own unchanged email — the
     * FE ships the schema-required {@code email} field on every profile
     * PATCH, including the avatar/uploadId flow — are not challenged for a
     * step-up token. Step-up protects the email CHANGE, not the request
     * shape.
     *
     * <p>Null/blank input and unknown users return {@code false}: those
     * requests are not change attempts, and the downstream validation /
     * not-found paths own their error semantics (400/404, not 401).
     */
    @Transactional(readOnly = true)
    public boolean wouldChangeEmail(Long userId, Object incoming) {
        if (userId == null || incoming == null) {
            return false;
        }
        String candidate = incoming.toString().trim().toLowerCase(Locale.ROOT);
        if (candidate.isEmpty()) {
            return false;
        }
        return userRepository.findById(userId)
                .map(existing -> {
                    String current = existing.getEmail();
                    return current == null
                            || !candidate.equals(current.trim().toLowerCase(Locale.ROOT));
                })
                .orElse(false);
    }

    private void processUserUpdateField(User user, String key, Object value, Map<String, Object> allUpdates, List<Runnable> deferredFirebaseActions) {
        switch (key) {
            case "accountStatus" -> handleAccountStatusUpdate(user, value, deferredFirebaseActions);
            case "email" -> emailChangeService.changeEmail(user, (String) value);
            case "profilePicture" -> handleProfilePictureUpdate(user, value);
            case "addresses" -> handleAddressesUpdate(user, allUpdates.get("addresses"));
            case "addressesIds", "addressIds" -> handleAddressIdsUpdate(user, value);
            case "phoneNumber" -> user.setPhoneNumber((String) value);
            case "noteFromAdmin" -> handleAdminNoteUpdate(user, value);
            case "firstName" -> user.setFirstName((String) value);
            case "lastName" -> user.setLastName((String) value);
            case "name" -> user.setName((String) value);
            case "companyDescription" -> user.setCompanyDescription((String) value);
            case "nip" -> user.setNip((String) value);
            case "userType" -> handleUserTypeUpdate(user, value, deferredFirebaseActions);
            default -> throw new ValidationTranslatableException("error.validation.invalid_argument", key);
        }
    }

    /**
     * SECURITY (pentest 3.1, same class as ticket attachments): the avatar
     * URL is stored verbatim and rendered as {@code <img src>} to other
     * users, so the client never chooses it freely. Two accepted shapes:
     * <ul>
     *   <li><b>uploadId</b> (preferred) — the tracked id from
     *       {@code POST /upload/signed-url}. The BE resolves the stored URL
     *       from its own {@code file_uploads} row after verifying the upload
     *       belongs to the caller and the blob exists in our bucket.</li>
     * </ul>
     *
     * <p><b>Why uploadId-only (owner directive 2026-06-13):</b> an own-bucket
     * URL is <em>not</em> sufficient — host-pinning stops attacker-host
     * substitution but not in-bucket cross-reference: a user could set their
     * avatar to any path in our bucket, including another user's photo. The
     * {@code file_uploads} table is the ownership state: only a tracked upload
     * the caller made is accepted, and the client never supplies a URL, so it
     * cannot point the avatar anywhere else. A raw URL (own-bucket or not)
     * won't resolve to a tracked upload and is rejected.
     *
     * <p>Null / blank clears the avatar (legitimate "remove photo" path).
     * OAuth/registration avatar writes call {@code setProfilePicture}
     * directly with a trusted provider URL and never pass through here.
     */
    private void handleProfilePictureUpdate(User user, Object value) {
        String uploadId = (String) value;
        if (uploadId == null || uploadId.isBlank()) {
            user.setProfilePicture(null); // remove-photo
            return;
        }
        user.setProfilePicture(
                signedUrlService.resolveOwnedUpload(permissionUtils.getUserId(), uploadId.trim()).publicUrl());
    }

    private void handleAccountStatusUpdate(User user, Object value, List<Runnable> deferredFirebaseActions) {
        // SECURITY: Only admins can change account status
        if (!permissionUtils.isAdmin()) {
            log.warn("SECURITY: Non-admin attempted to change accountStatus for user {}", user.getId());
            throw new InsufficientPermissionsException(
                "error.auth.insufficient_permissions",
                permissionUtils.getUserId(),
                "updateAccountStatus",
                "User#" + user.getId()
            );
        }

        String adminFirebaseUid = permissionUtils.getUserId();
        AccountStatus oldStatus = user.getAccountStatus();
        AccountStatus newStatus = AccountStatus.valueOf(value.toString());

        // Skip if status is not actually changing - prevents unnecessary token invalidation
        if (oldStatus == newStatus) {
            log.info("Account status unchanged for user {} (status: {}), skipping update", user.getId(), oldStatus);
            return;
        }

        {  // Admin check passed - proceed with status change

            log.warn("GDPR: ADMIN_STATUS_CHANGE Operation=updateAccountStatus, AdminFirebaseUID={}, TargetUserID={}, OldStatus={}, NewStatus={}, Purpose=account_management",
                    adminFirebaseUid, user.getId(), oldStatus, newStatus);

            user.setAccountStatus(newStatus);

            // --- Firebase claims sync by TARGET state ---
            // setCustomUserClaims replaces ALL claims; mergeExistingClaims preserves
            // social/auth claims only. Role is intentionally omitted for non-ACTIVE states.

            if (oldStatus == AccountStatus.IN_VALIDATION && newStatus == AccountStatus.ACTIVE) {
                // First activation (distinct timestamp from reactivation)
                final String firebaseUid = user.getFirebaseUserId();
                final String roleStr = user.getUserType().toString();
                final Long userId = user.getId();
                deferredFirebaseActions.add(() -> {
                    try {
                        Map<String, Object> claims = new HashMap<>();
                        claims.put("role", roleStr);
                        claims.put("activated", true);
                        claims.put("activatedAt", Instant.now().toString());
                        claims.put("pendingActivation", false);
                        mergeExistingClaims(firebaseUid, claims);
                        firebaseAuth.setCustomUserClaims(firebaseUid, claims);
                        log.info("Admin activated user {} - set Firebase role claim: {}", userId, roleStr);
                    } catch (Exception e) {
                        log.error("Failed to set Firebase claims during activation for user {}", userId, e);
                        throw new AuthenticationTranslatableException("error.user.activation_failed");
                    }
                });
            } else if (newStatus == AccountStatus.ACTIVE) {
                // Reactivation from any non-ACTIVE state (INACTIVE, BANNED, TO_BE_DELETED)
                final String firebaseUid = user.getFirebaseUserId();
                final String roleStr = user.getUserType().toString();
                final Long userId = user.getId();
                deferredFirebaseActions.add(() -> {
                    try {
                        Map<String, Object> claims = new HashMap<>();
                        claims.put("role", roleStr);
                        claims.put("activated", true);
                        claims.put("reactivatedAt", Instant.now().toString());
                        claims.put("pendingActivation", false);
                        claims.put("inactive", false);
                        mergeExistingClaims(firebaseUid, claims);
                        firebaseAuth.setCustomUserClaims(firebaseUid, claims);
                        log.info("Admin reactivated user {} - restored Firebase role claim: {}", userId, roleStr);
                    } catch (Exception e) {
                        log.error("Failed to update Firebase claims during reactivation for user {}", userId, e);
                        throw new AuthenticationTranslatableException("error.user.reactivation_failed");
                    }
                });
            } else if (newStatus == AccountStatus.IN_VALIDATION) {
                // Any state -> IN_VALIDATION (ACTIVE, INACTIVE, TO_BE_DELETED)
                final String firebaseUid = user.getFirebaseUserId();
                final Long userId = user.getId();
                deferredFirebaseActions.add(() -> {
                    try {
                        Map<String, Object> claims = new HashMap<>();
                        claims.put("pendingActivation", true);
                        claims.put("deactivatedAt", Instant.now().toString());
                        claims.put("activated", false);
                        claims.put("inactive", false);
                        mergeExistingClaims(firebaseUid, claims);
                        firebaseAuth.setCustomUserClaims(firebaseUid, claims);
                        log.info("Admin moved user {} to IN_VALIDATION from {}", userId, oldStatus);
                    } catch (Exception e) {
                        log.error("Failed to update Firebase claims during deactivation for user {}", userId, e);
                        throw new AuthenticationTranslatableException("error.user.deactivation_failed");
                    }
                });
            } else if (newStatus == AccountStatus.INACTIVE) {
                // Any state -> INACTIVE (ACTIVE, IN_VALIDATION)
                final String firebaseUid = user.getFirebaseUserId();
                final Long userId = user.getId();
                deferredFirebaseActions.add(() -> {
                    try {
                        Map<String, Object> claims = new HashMap<>();
                        claims.put("inactive", true);
                        claims.put("inactivatedAt", Instant.now().toString());
                        claims.put("activated", false);
                        claims.put("pendingActivation", false);
                        mergeExistingClaims(firebaseUid, claims);
                        firebaseAuth.setCustomUserClaims(firebaseUid, claims);
                        log.info("Admin made user {} inactive from {}", userId, oldStatus);
                    } catch (Exception e) {
                        log.error("Failed to update Firebase claims during inactivation for user {}", userId, e);
                        throw new AuthenticationTranslatableException("error.user.deactivation_failed");
                    }
                });
            } else if (newStatus == AccountStatus.BANNED) {
                // Any state -> BANNED (strips role, blocks access)
                final String firebaseUid = user.getFirebaseUserId();
                final Long userId = user.getId();
                deferredFirebaseActions.add(() -> {
                    try {
                        Map<String, Object> claims = new HashMap<>();
                        claims.put("activated", false);
                        claims.put("pendingActivation", false);
                        claims.put("inactive", false);
                        claims.put("bannedAt", Instant.now().toString());
                        mergeExistingClaims(firebaseUid, claims);
                        firebaseAuth.setCustomUserClaims(firebaseUid, claims);
                        log.info("Admin banned user {} from {} - stripped Firebase role claim", userId, oldStatus);
                    } catch (Exception e) {
                        log.error("Failed to update Firebase claims during ban for user {}", userId, e);
                        throw new AuthenticationTranslatableException("error.user.status_update_failed");
                    }
                });
            } else if (newStatus == AccountStatus.TO_BE_DELETED) {
                // Any state -> TO_BE_DELETED (strips role, blocks access)
                final String firebaseUid = user.getFirebaseUserId();
                final Long userId = user.getId();
                deferredFirebaseActions.add(() -> {
                    try {
                        Map<String, Object> claims = new HashMap<>();
                        claims.put("activated", false);
                        claims.put("pendingActivation", false);
                        claims.put("inactive", false);
                        claims.put("markedForDeletionAt", Instant.now().toString());
                        mergeExistingClaims(firebaseUid, claims);
                        firebaseAuth.setCustomUserClaims(firebaseUid, claims);
                        log.info("Admin marked user {} for deletion from {} - stripped Firebase role claim", userId, oldStatus);
                    } catch (Exception e) {
                        log.error("Failed to update Firebase claims during deletion marking for user {}", userId, e);
                        throw new AuthenticationTranslatableException("error.user.status_update_failed");
                    }
                });
            } else {
                // DELETED or unexpected — Firebase user is removed by deletePermanently()
                log.info("Admin changed user {} status from {} to {} (no Firebase claims update needed)",
                        user.getId(), oldStatus, newStatus);
            }
        }  // End of status change block

        // Publish account activation event (handled by NotificationEventListener after commit)
        if (newStatus == AccountStatus.ACTIVE) {
            eventPublisher.publishEvent(new AccountActivatedEvent(this, user, oldStatus, "ADMIN"));
        }

        // Increment token version to invalidate all existing sessions
        // This forces users to get a new token (via silent refresh) with updated permissions
        // NOTE: This only runs when status ACTUALLY changed (we early-return above if unchanged)
        user.incrementTokenVersion();
        // NOTE: Removed redundant save here - patch() method saves the user at the end
        log.info("GDPR: Operation=tokenVersion_increment, UserId={}, NewTokenVersion={}, Purpose=session_invalidation",
                user.getId(), user.getTokenVersion());

        // Evict user from cache to force fresh lookup on next request
        // This ensures status changes take effect immediately without waiting for cache TTL
        // NOTE: This runs BEFORE the transaction commits, but that's OK because:
        // 1. On cache miss, JwtAuthenticationFilter calls getTokenVersion() which reloads from DB
        // 2. The reload happens within the same transaction, so it sees the new tokenVersion
        userCacheService.evict(user.getFirebaseUserId());
        log.info("GDPR: Operation=cacheEvict_onStatusChange, UserId={}, OldStatus={}, NewStatus={}, Purpose=immediate_permission_update",
                user.getId(), oldStatus, newStatus);
    }

    /**
     * Merge preserved claims (provider, social IDs, 2FA) from existing Firebase user into new claims map.
     */
    private void mergeExistingClaims(String firebaseUid, Map<String, Object> claims) {
        try {
            var firebaseUser = firebaseService.getUserById(firebaseUid);
            Map<String, Object> existingClaims = firebaseUser.getCustomClaims();
            if (existingClaims != null) {
                for (String key : List.of("provider", "instagramId", "instagramUsername", "socialId",
                        "registeredAt", "twoFactorVerified", "requires2FA", "pendingAdmin", "adminChallengeCompletedAt")) {
                    if (existingClaims.containsKey(key)) {
                        claims.putIfAbsent(key, existingClaims.get(key));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read existing Firebase claims for {}: {}", firebaseUid, e.getMessage());
        }
    }

    /**
     * Executes deferred Firebase claim-sync actions on a best-effort basis.
     *
     * <p>These run AFTER the Postgres save has succeeded, and PG is the source of truth for the BE's
     * own authorization (account status, role, tokenVersion). A Firebase claim-write failure for the
     * TARGET user must therefore NOT fail the caller's request: in particular it must not surface as an
     * {@link com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException} (HTTP 401),
     * which the FE interprets as the <i>caller's</i> auth failing and logs the (e.g. admin) user out.
     * The target's bumped tokenVersion forces a claim re-sync on their next token refresh, so a
     * transient/absent-user failure self-heals.
     */
    private void runDeferredFirebaseActions(List<Runnable> deferredFirebaseActions) {
        for (Runnable firebaseAction : deferredFirebaseActions) {
            try {
                firebaseAction.run();
            } catch (Exception e) {
                log.error("Deferred Firebase claim sync failed (non-fatal; PG is the source of truth, "
                        + "claims re-sync on the user's next token refresh): {}", e.getMessage(), e);
            }
        }
    }

    private void handleAddressesUpdate(User user, Object addressesValue) {
        List<AddressDtoIn> addressDtos = objectMapper.convertValue(
                addressesValue,
                new TypeReference<>() {
                }
        );
        updateUserAddresses(user, addressDtos);
    }

    private void handleAddressIdsUpdate(User user, Object value) {
        @SuppressWarnings("unchecked")
        List<Long> addressIds = ((List<?>) value).stream()
                .map(Object::toString)
                .map(Long::valueOf)
                .toList();

        List<Address> addresses = fetchAndValidateAddresses(addressIds);
        List<Address> currentAddresses = initializeAddressList(user);

        Set<Long> newIds = new HashSet<>(addressIds);
        currentAddresses.removeIf(addr -> addr.getId() == null || !newIds.contains(addr.getId()));

        Set<Long> currentIds = currentAddresses.stream()
                .map(Address::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Address address : addresses) {
            if (!currentIds.contains(address.getId())) {
                address.setUser(user);
                currentAddresses.add(address);
            }
        }
    }

    private void handleAdminNoteUpdate(User user, Object value) {
        if (permissionUtils.isAdmin()) {
            user.setNoteFromAdmin((String) value);
        }
    }

    private void handleUserTypeUpdate(User user, Object value, List<Runnable> deferredFirebaseActions) {
        // SECURITY: Only admins can change user type/role
        if (!permissionUtils.isAdmin()) {
            log.warn("SECURITY: Non-admin attempted to change userType for user {}", user.getId());
            throw new InsufficientPermissionsException(
                "error.auth.insufficient_permissions",
                permissionUtils.getUserId(),
                "updateUserType",
                "User#" + user.getId()
            );
        }

        String adminFirebaseUid = permissionUtils.getUserId();
        UserType oldType = user.getUserType();
        UserType requestedType = UserType.valueOf(value.toString());
        UserType newType = requestedType;

        log.warn("GDPR: ADMIN_ROLE_CHANGE Operation=updateUserType, AdminFirebaseUID={}, TargetUserID={}, OldRole={}, RequestedRole={}, Purpose=role_management",
                adminFirebaseUid, user.getId(), oldType, requestedType);

        if (requestedType == UserType.ADMIN && oldType != UserType.ADMIN && oldType != UserType.PENDING_ADMIN) {
            newType = UserType.PENDING_ADMIN;
            log.warn("Admin promotion requested for user {} - setting to PENDING_ADMIN (2FA setup required)", user.getId());
        }

        user.setUserType(newType);

        if (user.getFirebaseUserId() != null && oldType != newType) {
            // Invalidate existing sessions when role changes (PG-side, immediate)
            user.incrementTokenVersion();
            userCacheService.evict(user.getFirebaseUserId());
            log.info("SECURITY: Session invalidated for user {} after role change from {} to {}",
                    user.getId(), oldType, newType);

            // Defer Firebase claims update until after PG save
            final String firebaseUid = user.getFirebaseUserId();
            final Long userId = user.getId();
            final AccountStatus accountStatus = user.getAccountStatus();
            final UserType finalNewType = newType;
            final UserType finalOldType = oldType;
            deferredFirebaseActions.add(() -> {
                try {
                    Map<String, Object> newClaims = new HashMap<>();
                    mergeExistingClaims(firebaseUid, newClaims);

                    if (finalNewType == UserType.PENDING_ADMIN) {
                        newClaims.put("role", "PENDING_ADMIN");
                        newClaims.put("pendingAdmin", true);
                        newClaims.put("requires2FA", true);
                        newClaims.put("promotedAt", Instant.now().toString());
                        if (accountStatus == AccountStatus.ACTIVE) newClaims.put("activated", true);
                    } else if (finalNewType == UserType.ADMIN) {
                        newClaims.put("role", "ADMIN");
                        newClaims.put("pendingAdmin", false);
                        newClaims.put("requires2FA", false);
                        newClaims.put("twoFactorVerified", true);
                        if (accountStatus == AccountStatus.ACTIVE) newClaims.put("activated", true);
                    } else if (finalNewType == UserType.INFLUENCER) {
                        if (accountStatus == AccountStatus.ACTIVE) {
                            newClaims.put("role", "INFLUENCER");
                            newClaims.put("activated", true);
                        }
                        newClaims.put("pendingAdmin", false);
                        newClaims.put("requires2FA", false);
                    } else if (finalNewType == UserType.COMPANY) {
                        if (accountStatus == AccountStatus.ACTIVE) {
                            newClaims.put("role", "COMPANY");
                            newClaims.put("activated", true);
                        }
                        newClaims.put("pendingAdmin", false);
                        newClaims.put("requires2FA", false);
                    }

                    newClaims.put("roleChangedAt", Instant.now().toString());
                    newClaims.put("previousRole", finalOldType.toString());

                    firebaseAuth.setCustomUserClaims(firebaseUid, newClaims);
                    log.info("Firebase claims updated for user {} - UserType changed from {} to {}", userId, finalOldType, finalNewType);
                } catch (Exception e) {
                    log.error("Failed to update Firebase claims for user {} type change from {} to {}: {}",
                            userId, finalOldType, finalNewType, e.getMessage(), e);
                }
            });
        }

        if (newType == UserType.PENDING_ADMIN && requestedType == UserType.ADMIN) {
            log.info("User {} needs to login and complete 2FA setup to become full ADMIN", user.getId());
        }
    }

    /**
     * Handle status change when user modifies their own data.
     * Only triggers re-verification for critical field changes.
     * Firebase claims update is deferred to execute after PG save.
     *
     * @param user The user being modified
     * @param hasCriticalChanges Whether critical fields were changed
     * @param deferredFirebaseActions List to collect deferred Firebase operations
     */
    private void handleStatusAndRoleChange(User user, boolean hasCriticalChanges, List<Runnable> deferredFirebaseActions) {
        // Profile field edits no longer trigger status transitions.
        // Account status is only changed by: email verification, company data confirmation,
        // admin actions, consent enforcement, and deletion flows.
        // Field validation (format, length, uniqueness) is enforced by DTO validators and service-level checks.
        if (!permissionUtils.isAdmin() && permissionUtils.isUserOwner(user)) {
            log.info("User {} editing own profile - no status transition (fields validated by DTO validators)",
                    user.getId());
        }
    }

    /**
     * Legacy overload: creates inline list and executes immediately.
     * Used by save() and handleUserDataModification() which don't have deferred action lists.
     */
    private void handleStatusAndRoleChange(User user, boolean hasCriticalChanges) {
        List<Runnable> deferredActions = new ArrayList<>();
        handleStatusAndRoleChange(user, hasCriticalChanges, deferredActions);
        deferredActions.forEach(Runnable::run);
    }

    /**
     * Legacy method for backward compatibility (save operations always trigger re-verification).
     * New user registrations always require admin verification.
     */
    private void handleStatusAndRoleChange(User user) {
        handleStatusAndRoleChange(user, true);
    }

    /**
     * Check if any critical fields are being changed in a patch operation.
     *
     * @param existingUser The current user state
     * @param updates The map of field updates
     * @return true if any critical fields are being changed
     */
    private boolean hasCriticalFieldChanges(User existingUser, Map<String, Object> updates) {
        for (String fieldName : updates.keySet()) {
            if (ProfileFieldCriticality.isCriticalField(fieldName)) {
                Object newValue = updates.get(fieldName);
                Object existingValue = getFieldValue(existingUser, fieldName);

                // Check if the value is actually changing
                if (!Objects.equals(existingValue, newValue)) {
                    log.debug("Critical field '{}' changing from '{}' to '{}'", fieldName, existingValue, newValue);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if any critical fields are being changed in an update operation.
     *
     * @param existingUser The current user state
     * @param dtoIn The DTO with updates
     * @return true if any critical fields are being changed
     */
    private boolean hasCriticalFieldChangesFromDto(User existingUser, UserDtoIn dtoIn) {
        if (dtoIn.getFirstName() != null &&
            !Objects.equals(dtoIn.getFirstName(), existingUser.getFirstName())) {
            return true;
        }
        if (dtoIn.getLastName() != null &&
            !Objects.equals(dtoIn.getLastName(), existingUser.getLastName())) {
            return true;
        }
        if (dtoIn.getEmail() != null &&
            !Objects.equals(dtoIn.getEmail(), existingUser.getEmail())) {
            return true;
        }
        if (dtoIn.getPhoneNumber() != null &&
            !Objects.equals(dtoIn.getPhoneNumber(), existingUser.getPhoneNumber())) {
            return true;
        }
        if (dtoIn.getName() != null &&
            !Objects.equals(dtoIn.getName(), existingUser.getName())) {
            return true;
        }
        return false;
    }

    /**
     * Get field value from user entity by field name.
     */
    private Object getFieldValue(User user, String fieldName) {
        return switch (fieldName) {
            case "firstName" -> user.getFirstName();
            case "lastName" -> user.getLastName();
            case "email" -> user.getEmail();
            case "phoneNumber" -> user.getPhoneNumber();
            case "name" -> user.getName();
            case "nip" -> user.getNip();
            default -> null;
        };
    }

    /**
     * Mask email for logging purposes (GDPR compliance).
     * Example: "john.doe@example.com" -> "joh*****@example.com"
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) return "N/A";
        return email.replaceAll("(?<=.{3}).(?=.*@)", "*");
    }

    /**
     * Public method to handle status change when user modifies their own data.
     * This method can be called from other services (e.g., UserSocialConnectionService)
     * to trigger validation when user's related data is modified.
     *
     * @param userId The ID of the user whose data was modified
     */
    @Transactional
    public void handleUserDataModification(Long userId) {
        User user = getUserToUpdate(userId);
        handleStatusAndRoleChange(user);
        userRepository.save(getSelf().updateEntityUpdater(user));
        log.info("User {} data modified - status validation triggered", userId);
    }

    // ===== DTO/paged mapping =====

    @Override
    @Transactional(readOnly = true)
    public <D> Page<D> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
        Page<User> page = getSelf().getDataPagedAndFiltered(pageable, filters);

        @SuppressWarnings("unchecked")
        Page<D> dtoPage = (Page<D>) page.map(user -> {
            UserDtoOut dto = new UserDtoOut();

            dto.setId(user.getId());
            dto.setFirebaseUserId(user.getFirebaseUserId());
            dto.setEmail(user.getEmail());
            if (user.getUserType() != null) {
                UserTypeDtoOut userTypeDtoOut = UserTypeDtoOut.builder()
                        .value(user.getUserType().name())
                        .label(user.getUserType().getLabel(dictionaryService, getLocaleFromRequest()))
                        .originalLabel(user.getUserType().name())
                        .build();
                dto.setUserType(userTypeDtoOut);
            }
            if (user.getAccountStatus() != null) {
                AccountStatusDtoOut accountStatusDtoOut = AccountStatusDtoOut.builder()
                        .value(user.getAccountStatus().name())
                        .label(user.getAccountStatus().getLabel(dictionaryService, getLocaleFromRequest()))
                        .description(user.getAccountStatus().getDescription(dictionaryService, getLocaleFromRequest()))
                        .originalLabel(user.getAccountStatus().name())
                        .colorTheme(user.getAccountStatus().getColorTheme())
                        .icon(user.getAccountStatus().getIcon())
                        .isActive(user.getAccountStatus().isActive())
                        .canLogin(user.getAccountStatus().canLogin())
                        .isTerminal(user.getAccountStatus().isTerminal())
                        .build();
                dto.setAccountStatus(accountStatusDtoOut);
            }
            dto.setFirstName(user.getFirstName());
            dto.setLastName(user.getLastName());
            dto.setName(user.getName());
            dto.setPhoneNumber(user.getPhoneNumber());
            dto.setCompanyDescription(user.getCompanyDescription());
            dto.setNip(user.getNip());
            dto.setPremium(user.getPremium());
            dto.setProfilePicture(user.getProfilePicture());
            dto.setNoteFromAdmin(user.getNoteFromAdmin());
            dto.setCreatedTime(user.getCreatedTime());
            dto.setLastUpdateTime(user.getLastUpdateTime());
            dto.setUpdater(user.getUpdaterId());

            if (user.getAddresses() != null) {
                try {
                    dto.setAddresses(modelMapper.map(user.getAddresses(),
                            new TypeReference<List<AddressDtoOut>>() {
                            }.getType()));
                } catch (Exception e) {
                    log.debug("Could not load addresses for user {}: {}", user.getId(), e.getMessage());
                    dto.setAddresses(new ArrayList<>());
                }
            } else {
                dto.setAddresses(new ArrayList<>());
            }

            if (user.getSocialConnections() != null) {
                try {
                    dto.setSocialConnections(modelMapper.map(user.getSocialConnections(),
                            new TypeReference<List<UserSocialConnectionDtoOut>>() {
                            }.getType()));
                } catch (Exception e) {
                    log.debug("Could not load social connections for user {}: {}", user.getId(), e.getMessage());
                    dto.setSocialConnections(new ArrayList<>());
                }
            } else {
                dto.setSocialConnections(new ArrayList<>());
            }

            return dto;
        });

        return dtoPage;
    }

    @Override
    public <U> U toDto(User entity) {
        if (entity == null) return null;

        UserDtoOut dto = modelMapper.map(entity, UserDtoOut.class);

        if (entity.getAccountStatus() != null) {
            AccountStatusDtoOut accountStatusDtoOut = AccountStatusDtoOut.builder()
                    .value(entity.getAccountStatus().name())
                    .label(entity.getAccountStatus().getLabel(dictionaryService, getLocaleFromRequest()))
                    .description(entity.getAccountStatus().getDescription(dictionaryService, getLocaleFromRequest()))
                    .originalLabel(entity.getAccountStatus().name())
                    .colorTheme(entity.getAccountStatus().getColorTheme())
                    .icon(entity.getAccountStatus().getIcon())
                    .isActive(entity.getAccountStatus().isActive())
                    .canLogin(entity.getAccountStatus().canLogin())
                    .isTerminal(entity.getAccountStatus().isTerminal())
                    .build();
            dto.setAccountStatus(accountStatusDtoOut);
        }

        if (entity.getUserType() != null) {
            UserTypeDtoOut userTypeDtoOut = UserTypeDtoOut.builder()
                    .value(entity.getUserType().name())
                    .label(entity.getUserType().getLabel(dictionaryService, getLocaleFromRequest()))
                    .originalLabel(entity.getUserType().name())
                    .build();
            dto.setUserType(userTypeDtoOut);
        }

        // Calculate profile completeness
        ProfileCompletenessResult completenessResult = checkProfileCompleteness(entity);
        dto.setProfileComplete(completenessResult.isComplete());
        dto.setProfileMissingFields(completenessResult.getMissingFields());

        // Consent status
        dto.setNewestConsentsAccepted(entity.getNewestConsentsAccepted());
        if (!Boolean.TRUE.equals(entity.getNewestConsentsAccepted())) {
            dto.setDaysToAcceptNewTerms(legalConsentService.computeDaysToAcceptNewTerms());
        }

        @SuppressWarnings("unchecked")
        U result = (U) dto;
        return result;
    }

    @Override
    @Transactional
    public <U> U createFromDtoAsDto(UserDtoIn dto) {
        User entity = getSelf().save(dto);
        return getSelf().toDto(entity);
    }

    public List<UserDtoOut> toDtoList(List<User> entities) {
        if (entities == null || entities.isEmpty()) return new ArrayList<>();
        List<UserDtoOut> result = new ArrayList<>();
        for (User entity : entities) {
            UserDtoOut dto = getSelf().toDto(entity);
            result.add(dto);
        }
        return result;
    }

    public InfluencerPublicProfileDto toInfluencerPublicProfileDto(User entity) {
        if (entity == null) return null;
        return modelMapper.map(entity, InfluencerPublicProfileDto.class);
    }

    public InfluencerPublicProfileDto toInfluencerPublicProfileDtoWithSocialData(User entity, UserSocialConnection primaryConnection) {
        if (entity == null) return null;

        InfluencerPublicProfileDto dto = modelMapper.map(entity, InfluencerPublicProfileDto.class);

        if (primaryConnection != null) {
            dto.setPlatformName(primaryConnection.getPlatform() != null ?
                    primaryConnection.getPlatform().getName() : null);
            dto.setDisplayName(primaryConnection.getDisplayName());
            dto.setProfileUrl(primaryConnection.getProfileUrl());
            dto.setFollowersCount(primaryConnection.getFollowersCount());
        } else {
            dto.setPlatformName(null);
            dto.setDisplayName(null);
            dto.setProfileUrl(null);
            dto.setFollowersCount(null);
        }

        return dto;
    }

    public CompanyPublicProfileDto toCompanyPublicProfileDto(User entity) {
        if (entity == null) return null;
        return modelMapper.map(entity, CompanyPublicProfileDto.class);
    }

    // ===== Find as DTO =====

    @Override
    @Transactional(readOnly = true)
    public <U> U findByIdAsDto(Long id) {
        User user = getSelf().findById(id);
        return getSelf().toDto(user);
    }

    @Transactional(readOnly = true)
    public UserDtoOut viewByIdAsDto(Long id) {
        User user = getSelf().viewById(id);
        return getSelf().toDto(user);
    }

    @Transactional
    public UserDtoOut findByFirebaseUserIdAsDto(String firebaseId) {
        User user = getSelf().getUserWithInitializedCollections(firebaseId);

        // Sync email verification with PG-wins guard (delegates to EmailVerificationService)
        // Only check Firebase if not yet verified in PostgreSQL (performance optimization)
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            try {
                UserRecord firebaseUser = FirebaseAuth.getInstance().getUser(firebaseId);
                emailVerificationService.syncEmailVerificationStatus(user, firebaseUser.isEmailVerified());
            } catch (FirebaseAuthException e) {
                log.warn("Could not check Firebase email status for user {}: {}", firebaseId, e.getMessage());
            }
        }

        return getSelf().toDto(user);
    }

    @Transactional(readOnly = true)
    public InfluencerPublicProfileDto getInfluencerPublicProfile(Long id) {
        User influencer = getSelf().viewById(id);
        UserSocialConnection primaryConnection = userSocialConnectionService.getPrimaryConnectionSafe(influencer);
        if (primaryConnection == null) {
            log.warn("No primary social connection found for influencer {}", id);
            throw new ResourceNotFoundException("error.business.item_not_found", "Social Connection");
        }
        return getSelf().toInfluencerPublicProfileDtoWithSocialData(influencer, primaryConnection);
    }

    @Transactional(readOnly = true)
    public CompanyPublicProfileDto getCompanyPublicProfile(Long id) {
        User company = getSelf().viewById(id);
        return getSelf().toCompanyPublicProfileDto(company);
    }

    @Transactional
    public UserDtoOut saveAsDto(UserDtoIn userDtoIn) {
        User user = getSelf().save(userDtoIn);
        return getSelf().toDto(user);
    }

    @Override
    @Transactional
    public <U> U updateAsDto(Long id, UserDtoIn dtoIn) {
        User user = getSelf().update(id, dtoIn);
        return getSelf().toDto(user);
    }

    @Override
    @Transactional
    public <U> U patchAsDto(Long id, Map<String, Object> updates) {
        User user = getSelf().patch(id, updates);
        return getSelf().toDto(user);
    }

    // ===== Deletion eligibility (delegation) =====

    @Transactional(readOnly = true)
    public DeletionEligibilityDto checkMyDeletionEligibility() {
        String firebaseUid = permissionUtils.getUserId();
        User currentUser = getSelf().findByFirebaseUserIdNoPermissionCheck(firebaseUid);
        log.info("GDPR: Operation=checkMyDeletionEligibility, FirebaseUID={}, UserID={}, Purpose=deletion_assessment",
                firebaseUid, currentUser.getId());
        return userAccountOrchestrator.checkDeletionEligibilityForUser(currentUser, getLocaleFromRequest());
    }

    @Transactional(readOnly = true)
    public DeletionEligibilityDto checkDeletionEligibilityById(Long id) {
        if (!permissionUtils.isAdmin()) {
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "checkDeletionEligibility",
                    "User#" + id);
        }
        String checkerFirebaseUid = permissionUtils.getUserId();
        log.info("GDPR: Operation=checkDeletionEligibility, CheckerFirebaseUID={}, TargetUserID={}, Purpose=deletion_assessment",
                checkerFirebaseUid, id);

        User user = getSelf().findById(id);
        return userAccountOrchestrator.checkDeletionEligibilityForUser(user, getLocaleFromRequest());
    }

    @Transactional(readOnly = true)
    public DeletionEligibilityDto checkDeletionEligibilityForUser(User user) {
        return userAccountOrchestrator.checkDeletionEligibilityForUser(user, getLocaleFromRequest());
    }

    // ===== Profile Completeness =====

    /**
     * Check if user profile is complete based on user type
     *
     * @param user The user to check
     * @return ProfileCompletenessResult containing completion status and missing fields
     */
    public ProfileCompletenessResult checkProfileCompleteness(User user) {
        List<String> missingFields = new ArrayList<>();

        if (user == null) {
            return new ProfileCompletenessResult(true, missingFields);
        }

        // Check common required fields
        if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()) {
            missingFields.add("First Name");
        }
        if (user.getLastName() == null || user.getLastName().trim().isEmpty()) {
            missingFields.add("Last Name");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            missingFields.add("Email");
        }
        if (user.getPhoneNumber() == null || user.getPhoneNumber().trim().isEmpty()) {
            missingFields.add("Phone Number");
        }

        // Check for primary address
        List<Address> addresses = user.getAddresses();
        Address primaryAddress = null;
        if (addresses != null && !addresses.isEmpty()) {
            primaryAddress = addresses.stream()
                    .filter(Address::isPrimary)
                    .findFirst()
                    .orElse(null);
        }

        if (primaryAddress == null) {
            missingFields.add("Primary Address");
        } else {
            // Check required address fields
            if (primaryAddress.getStreet() == null || primaryAddress.getStreet().trim().isEmpty()) {
                missingFields.add("Address Street");
            }
            if (primaryAddress.getCity() == null || primaryAddress.getCity().trim().isEmpty()) {
                missingFields.add("Address City");
            }
            if (primaryAddress.getPostalCode() == null || primaryAddress.getPostalCode().trim().isEmpty()) {
                missingFields.add("Address Postal Code");
            }
            if (primaryAddress.getCountry() == null || primaryAddress.getCountry().trim().isEmpty()) {
                missingFields.add("Address Country");
            }
            if (primaryAddress.getState() == null || primaryAddress.getState().trim().isEmpty()) {
                missingFields.add("Address State");
            }
        }

        boolean isComplete = missingFields.isEmpty();
        return new ProfileCompletenessResult(isComplete, missingFields);
    }

    /**
     * Inner class to hold profile completeness result
     */
    public static class ProfileCompletenessResult {
        private final boolean isComplete;
        private final List<String> missingFields;

        public ProfileCompletenessResult(boolean isComplete, List<String> missingFields) {
            this.isComplete = isComplete;
            this.missingFields = missingFields;
        }

        public boolean isComplete() {
            return isComplete;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }
    }
}