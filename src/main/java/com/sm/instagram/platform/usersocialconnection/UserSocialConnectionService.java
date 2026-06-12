package com.sm.instagram.platform.usersocialconnection;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.user.User;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
public class UserSocialConnectionService extends BaseService<UserSocialConnection, Long, UserSocialConnectionDtoIn> {

    public static final String USER_NOT_FOUND_WITH_ID = "User not found with ID: ";
    private final UserSocialConnectionRepository userSocialConnectionRepository;
    private final PermissionUtils permissionUtils;

    protected UserSocialConnectionService(SpecificationBuilder<UserSocialConnection> specificationBuilder,
                                          UserSocialConnectionRepository repository,
                                          ModelMapper modelMapper,
                                          RepositoryResolver repositoryResolver,
                                          PermissionUtils permissionUtils,
                                          ApplicationContext applicationContext) {
        super(applicationContext, specificationBuilder, repository, modelMapper, repositoryResolver);
        this.userSocialConnectionRepository = repository;
        this.permissionUtils = permissionUtils;
    }

    @Override
    public UserSocialConnection findById(Long id) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log social connection access
        log.info("GDPR: Operation=findSocialConnectionById, FirebaseUID={}, ConnectionID={}, Purpose=social_profile_retrieval, DataAccessed=social.profile,social.username,social.followers",
                firebaseUid, id);

        UserSocialConnection uSC = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Social Connection"));
        canUpdateUSC(uSC);
        return uSC;
    }

    @Override
    public Page<UserSocialConnection> getDataPagedAndFiltered(Pageable pageable, Map<String, String> filters) {
        Specification<UserSocialConnection> spec = createSpecification(filters);
        if (!permissionUtils.isAdmin()) {
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("user").get("firebaseUserId"), permissionUtils.getUserId()));
        }
        return repository.findAll(spec, pageable);
    }

    @Override
    public UserSocialConnection save(UserSocialConnection uSC) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log social connection save
        log.info("GDPR: Operation=saveSocialConnection, FirebaseUID={}, Purpose=social_profile_storage, DataCreated=social.profile,social.username,social.followers, LegalBasis=consent",
                firebaseUid);

        canUpdateUSC(uSC);
        UserSocialConnection saved = repository.save(getSelf().updateEntityUpdater(uSC));

        log.info("GDPR: Operation=saveSocialConnection_SUCCESS, FirebaseUID={}, ConnectionID={}, Platform={}",
                firebaseUid, saved.getId(), saved.getPlatform() != null ? saved.getPlatform().getName() : "unknown");

        return saved;
    }

    public UserSocialConnection create(UserSocialConnectionDtoIn dtoIn) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log social connection creation
        log.info("GDPR: Operation=createSocialConnection, FirebaseUID={}, Platform={}, Username={}, Purpose=social_profile_linking, DataCreated=social.profile,social.username,social.followers, LegalBasis=consent",
                firebaseUid, dtoIn.getPlatform(), dtoIn.getDisplayName());

        UserSocialConnection uSC = modelMapper.map(dtoIn, UserSocialConnection.class);

        if (dtoIn.getUserId() != null) {
            User user = repositoryResolver.getRepository(User.class).findById(dtoIn.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
            uSC.setUser(user);
        }

        canUpdateUSC(uSC);
        UserSocialConnection saved = repository.save(getSelf().updateEntityUpdater(uSC));

        log.info("GDPR: Operation=createSocialConnection_SUCCESS, FirebaseUID={}, ConnectionID={}, Username={}, FollowersCount={}",
                firebaseUid, saved.getId(), saved.getDisplayName(), saved.getFollowersCount());

        return saved;
    }

    @Override
    public UserSocialConnection update(Long id, UserSocialConnectionDtoIn dtoIn) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log social connection update
        log.info("GDPR: Operation=updateSocialConnection, FirebaseUID={}, ConnectionID={}, Purpose=social_profile_update, DataModified=social.profile,social.username,social.followers, LegalBasis=consent",
                firebaseUid, id);

        UserSocialConnection uSC = findById(id);
        canUpdateUSC(uSC);
        User currentUser = uSC.getUser();
        modelMapper.map(dtoIn, uSC);
        if (dtoIn.getUserId() != null && !dtoIn.getUserId().equals(currentUser.getId())) {
            User newUser = repositoryResolver.getRepository(User.class).findById(dtoIn.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
            uSC.setUser(newUser);
        } else {
            uSC.setUser(currentUser);
        }

        UserSocialConnection saved = repository.save(getSelf().updateEntityUpdater(uSC));

        log.info("GDPR: Operation=updateSocialConnection_SUCCESS, FirebaseUID={}, ConnectionID={}, Username={}",
                firebaseUid, saved.getId(), saved.getDisplayName());

        return saved;
    }

    @Override
    public UserSocialConnection patch(Long id, Map<String, Object> updates) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log partial social connection update
        log.info("GDPR: Operation=patchSocialConnection, FirebaseUID={}, ConnectionID={}, FieldsModified={}, Purpose=partial_profile_update, LegalBasis=consent",
                firebaseUid, id, updates.keySet());

        UserSocialConnection uSC = findById(id);
        canUpdateUSC(uSC);
        updates.forEach((fieldName, value) -> {
            try {
                if (fieldName.equals("userId")) {
                    if (value != null) {
                        Long userId = Long.parseLong(value.toString());
                        User user = repositoryResolver.getRepository(User.class).findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));
                        uSC.setUser(user);
                    }
                    return;
                }

                Field field = uSC.getClass().getDeclaredField(fieldName);
                if (!fieldName.equals("id") && !fieldName.equals("user") && !fieldName.equals("createdTime") && !fieldName.equals("lastUpdateTime")) {
                    Object castValue = convertValueToFieldType(field, value);
                    field.set(uSC, castValue);
                }
            } catch (NoSuchFieldException e) {
                throw new ValidationTranslatableException("error.validation.invalid_argument", fieldName);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to set value for field " + fieldName, e);
            }
        });

        UserSocialConnection saved = repository.save(getSelf().updateEntityUpdater(uSC));

        log.info("GDPR: Operation=patchSocialConnection_SUCCESS, FirebaseUID={}, ConnectionID={}, ModifiedFields={}",
                firebaseUid, saved.getId(), updates.keySet());

        return saved;
    }

    @Override
    public void delete(Long id) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log social connection deletion
        log.warn("GDPR: DELETION Operation=deleteSocialConnection, FirebaseUID={}, ConnectionID={}, Purpose=social_profile_removal, LegalBasis=user_request",
                firebaseUid, id);

        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("error.business.item_not_found", "Entity");
        }
        UserSocialConnection uSC = findById(id);
        canUpdateUSC(uSC);

        String username = uSC.getDisplayName();
        String platform = uSC.getPlatform() != null ? uSC.getPlatform().getName() : "unknown";
        Long userId = uSC.getUser() != null ? uSC.getUser().getId() : null;

        repository.deleteById(id);

        log.warn("GDPR: DELETION_COMPLETE Operation=deleteSocialConnection_SUCCESS, FirebaseUID={}, ConnectionID={}, Username={}, Platform={}, DataRemoved=social_connection_data",
                firebaseUid, id, username, platform);
    }

    public UserSocialConnection getPrimaryConnection(User user) {
        String firebaseUid = permissionUtils.getUserId();

        // GDPR: Log primary connection retrieval
        log.info("GDPR: Operation=getPrimaryConnection, FirebaseUID={}, UserID={}, Purpose=primary_social_profile_retrieval, DataAccessed=primary_social_connection",
                firebaseUid, user.getId());

        return userSocialConnectionRepository
                .findByUserIdAndIsPrimaryTrue(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Primary Social Connection"));
    }

    public UserSocialConnection getPrimaryConnectionSafe(User user) {
        return userSocialConnectionRepository
                .findByUserIdAndIsPrimaryTrue(user.getId())
                .orElse(null);
    }

    private void canUpdateUSC(UserSocialConnection uSC) {
        if (!permissionUtils.isAdmin() && !permissionUtils.isUserOwner(uSC)) {
            String firebaseUid = permissionUtils.getUserId();
            Long connectionId = uSC.getId();
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    firebaseUid,
                    "canUpdateUSC",
                    "UserSocialConnection#" + connectionId);
        }
    }

    /**
     * Lazy-loads UserService to avoid circular dependency.
     * Called after social connection modifications to trigger user validation.
     */
    private void triggerUserValidation(Long userId) {
        try {
            // Lazy load UserService to avoid circular dependency
            com.sm.instagram.platform.user.UserService userService = 
                applicationContext.getBean(com.sm.instagram.platform.user.UserService.class);
            userService.handleUserDataModification(userId);
        } catch (Exception e) {
            log.error("Failed to trigger user validation for user {}: {}", userId, e.getMessage(), e);
            // Don't throw - the social connection operation itself succeeded
        }
    }

    /**
     * Converts a UserSocialConnection entity to DTO.
     * This method ensures all conversions happen within a transaction boundary.
     *
     * @param entity The entity to convert
     * @return The converted DTO
     */
    @Override
    @Transactional(readOnly = true)
    public <O> O toDto(UserSocialConnection entity) {
        if (entity == null) return null;
        @SuppressWarnings("unchecked")
        O result = (O) modelMapper.map(entity, UserSocialConnectionDtoOut.class);
        return result;
    }

    /**
     * Converts a list of UserSocialConnection entities to DTOs.
     * This method ensures all conversions happen within a transaction boundary.
     *
     * @param entities The entities to convert
     * @return The list of converted DTOs
     */
    @Transactional(readOnly = true)
    public List<UserSocialConnectionDtoOut> toDtoList(List<UserSocialConnection> entities) {
        if (entities == null || entities.isEmpty()) return new ArrayList<>();
        return entities.stream()
                .map(entity -> (UserSocialConnectionDtoOut) getSelf().toDto(entity))
                .toList();
    }

    /**
     * Finds a UserSocialConnection by ID and returns it as a DTO.
     *
     * @param id The ID of the entity
     * @return The entity as a DTO
     */
    @Override
    @Transactional(readOnly = true)
    public <O> O findByIdAsDto(Long id) {
        UserSocialConnection entity = findById(id);
        return getSelf().toDto(entity);
    }

    /**
     * Creates a new UserSocialConnection and returns it as a DTO.
     *
     * @param dtoIn The input DTO
     * @return The created entity as a DTO
     */
    @Override
    @Transactional
    public <O> O createFromDtoAsDto(UserSocialConnectionDtoIn dtoIn) {
        UserSocialConnection savedEntity = create(dtoIn);
        return getSelf().toDto(savedEntity);
    }

    /**
     * Creates a new UserSocialConnection and returns it as a DTO.
     * Kept for backward compatibility.
     *
     * @param dtoIn The input DTO
     * @return The created entity as a DTO
     */
    @Transactional
    public UserSocialConnectionDtoOut createAsDto(UserSocialConnectionDtoIn dtoIn) {
        return getSelf().createFromDtoAsDto(dtoIn);
    }

    /**
     * Updates a UserSocialConnection and returns it as a DTO.
     *
     * @param id    The ID of the entity to update
     * @param dtoIn The input DTO with updated values
     * @return The updated entity as a DTO
     */
    @Override
    @Transactional
    public <O> O updateAsDto(Long id, UserSocialConnectionDtoIn dtoIn) {
        UserSocialConnection updatedEntity = update(id, dtoIn);
        return getSelf().toDto(updatedEntity);
    }

    /**
     * Patches a UserSocialConnection and returns it as a DTO.
     *
     * @param id      The ID of the entity to patch
     * @param updates The map of fields to update
     * @return The patched entity as a DTO
     */
    @Override
    @Transactional
    public <O> O patchAsDto(Long id, Map<String, Object> updates) {
        UserSocialConnection patchedEntity = patch(id, updates);
        return getSelf().toDto(patchedEntity);
    }

    /**
     * Gets the primary connection for a user and returns it as a DTO.
     *
     * @param user The user
     * @return The primary connection as a DTO
     */
    @Transactional(readOnly = true)
    public UserSocialConnectionDtoOut getPrimaryConnectionAsDto(User user) {
        UserSocialConnection entity = getPrimaryConnection(user);
        return getSelf().toDto(entity);
    }

    /**
     * Gets the primary connection for a user safely and returns it as a DTO.
     *
     * @param user The user
     * @return The primary connection as a DTO, or null if not found
     */
    @Transactional(readOnly = true)
    public UserSocialConnectionDtoOut getPrimaryConnectionSafeAsDto(User user) {
        UserSocialConnection entity = getPrimaryConnectionSafe(user);
        return getSelf().toDto(entity);
    }

    /**
     * Retrieves a paginated list of entities as DTOs with all conversions done within transaction.
     * This prevents LazyInitializationException by ensuring all DTO mappings happen inside @Transactional.
     *
     * @param pageable Pagination parameters
     * @param filters  Filter parameters
     * @param <O> The output DTO type
     * @return Page of DTOs with all lazy relationships properly loaded
     */
    @Override
    @Transactional(readOnly = true)
    public <O> Page<O> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
        // Get entities with proper pagination and filtering
        Page<UserSocialConnection> page = getDataPagedAndFiltered(pageable, filters);

        // Convert to DTOs within transaction boundary using modelMapper
        Page<UserSocialConnectionDtoOut> dtoPage = page.map(getSelf()::toDto);

        // Cast to generic type
        @SuppressWarnings("unchecked")
        Page<O> result = (Page<O>) dtoPage;
        return result;
    }
}
