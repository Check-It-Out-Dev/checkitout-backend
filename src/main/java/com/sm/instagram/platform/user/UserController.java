package com.sm.instagram.platform.user;

import static com.sm.instagram.platform.common.util.PiiMaskingUtils.maskEmail;

import com.sm.instagram.platform.auth.stepup.StepUpActionType;
import com.sm.instagram.platform.auth.stepup.StepUpAuthService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
@PreAuthorize("hasAuthority('ADMIN')")
@RequestMapping("users")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class UserController extends BaseController<User, Long, UserDtoIn, UserDtoOut> {
    private final UserService userService;
    private final UserSocialConnectionService userSocialConnectionService;
    private final TranslationService translationService;
    private final DefaultNoteService defaultNoteService;  // CIO-341: Language-aware default notes
    private final HttpServletRequest request;
    private final StepUpAuthService stepUpAuthService;
    private final PermissionUtils permissionUtils;

    protected UserController(UserService userService,
                             UserSocialConnectionService userSocialConnectionService,
                             TranslationService translationService,
                             DefaultNoteService defaultNoteService,
                             HttpServletRequest request,
                             StepUpAuthService stepUpAuthService,
                             PermissionUtils permissionUtils) {
        super(User.class);
        this.userService = userService;
        this.userSocialConnectionService = userSocialConnectionService;
        this.translationService = translationService;
        this.defaultNoteService = defaultNoteService;
        this.request = request;
        this.stepUpAuthService = stepUpAuthService;
        this.permissionUtils = permissionUtils;
    }

    /**
     * Get locale from Accept-Language header
     */
    private Locale getLocaleFromRequest() {
        String acceptLanguageHeader = request.getHeader("Accept-Language");
        if (acceptLanguageHeader != null && !acceptLanguageHeader.isEmpty()) {
            // Parse the first language from Accept-Language header
            String language = acceptLanguageHeader.split(",")[0].split(";")[0].trim();
            return Locale.forLanguageTag(language);
        }
        return Locale.forLanguageTag("pl"); // Default to Polish
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<UserDtoOut> getCurrentUser() {
        log.info("Retrieving current authenticated user");
        long startTime = System.currentTimeMillis();

        try {
            // Get authentication from security context
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || auth.getPrincipal() == null) {
                log.error("Authentication not available in security context");
                throw new ResourceNotFoundException("error.auth.not_authenticated");
            }

            // Principal is now Firebase ID (secure UUID) from JWT subject
            String firebaseUid = auth.getPrincipal().toString();

            // GDPR: Log personal data access
            log.info("GDPR: Operation=getCurrentUser, FirebaseUID={}, DataAccessed=user.profile,user.email,user.type, Purpose=profile_display", firebaseUid);

            // Retrieve user by Firebase ID (the secure, consistent identifier)
            UserDtoOut userDtoOut = userService.findByFirebaseUserIdAsDto(firebaseUid);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully retrieved current user (DB ID: {}, Firebase ID: {}) in {}ms",
                    userDtoOut.getId(), firebaseUid, duration);

            // GDPR: Log successful data retrieval
            log.info("GDPR: Operation=getCurrentUser_success, FirebaseUID={}, DataRetrieved=user.profile, Purpose=profile_display_complete", firebaseUid);

            return ResponseEntity.ok(userDtoOut);
        } catch (ResourceNotFoundException e) {
            // Re-throw ResourceNotFoundException as-is
            throw e;
        }
        // Let all other exceptions propagate to @ControllerAdvice handlers
    }

    @GetMapping("/influencers/{id}/public-profile")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('COMPANY')")
    public ResponseEntity<InfluencerPublicProfileDto> getInfluencerPublicProfile(@PathVariable Long id) {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log public profile access
        log.info("GDPR: Operation=getInfluencerPublicProfile, FirebaseUID={}, TargetUserID={}, DataAccessed=public_profile, Purpose=business_interaction", firebaseUid, id);

        log.info("Retrieving influencer public profile for user ID: {}", id);
        long startTime = System.currentTimeMillis();

        InfluencerPublicProfileDto dto = userService.getInfluencerPublicProfile(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved influencer public profile for user ID: {} in {}ms", id, duration);

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/companies/{id}/public-profile")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('INFLUENCER')")
    public ResponseEntity<CompanyPublicProfileDto> getCompanyPublicProfile(@PathVariable Long id) {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log public profile access
        log.info("GDPR: Operation=getCompanyPublicProfile, FirebaseUID={}, TargetUserID={}, DataAccessed=company_public_profile, Purpose=business_interaction", firebaseUid, id);

        log.info("Retrieving company public profile for user ID: {}", id);
        long startTime = System.currentTimeMillis();

        CompanyPublicProfileDto dto = userService.getCompanyPublicProfile(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved company public profile for user ID: {} in {}ms", id, duration);

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/paged/public-profile")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPANY', 'INFLUENCER')")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<PublicProfileDto>> findPublicPaginated(Pageable pageable, @RequestParam Map<String, String> filters) {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log paginated data access
        log.info("GDPR: Operation=findPublicPaginated, FirebaseUID={}, DataAccessed=public_profiles_list, Purpose=business_discovery", firebaseUid);
        // Handle unpaged requests safely
        String pageInfo = pageable.isPaged() ?
                String.format("page: %d, size: %d", pageable.getPageNumber(), pageable.getPageSize()) :
                "unpaged";
        log.info("Retrieving paginated public profiles - {}, filters: {}", pageInfo, filters.keySet());
        long startTime = System.currentTimeMillis();

        // Remove pagination parameters from filters
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");
        filters.remove("direction");

        Page<User> users = userService.getDataPagedAndFiltered(pageable, filters);
        log.debug("Retrieved {} users for public profile mapping", users.getNumberOfElements());

        List<PublicProfileDto> mapped = users
                .getContent()
                .stream()
                .filter(user -> user.getUserType() != UserType.ADMIN)
                .map(user -> switch (user.getUserType()) {
                    case INFLUENCER -> {
                        // Get primary social connection safely
                        UserSocialConnection primaryConnection = userSocialConnectionService.getPrimaryConnectionSafe(user);

                        if (primaryConnection != null) {
                            log.debug("Mapped influencer {} with social connection", user.getName());

                            // Use service method to map with social connection data
                            yield userService.toInfluencerPublicProfileDtoWithSocialData(user, primaryConnection);
                        } else {
                            // In case of missing social connection, return a default public profile using user data
                            log.warn("No primary social connection found for influencer {}, using default profile", user.getId());
                            yield userService.toInfluencerPublicProfileDtoWithSocialData(user, null);
                        }
                    }
                    case COMPANY -> {
                        log.debug("Mapped company {}", user.getName());
                        yield userService.toCompanyPublicProfileDto(user);
                    }
                    default -> null; // Or skip with filter above
                })
                .toList();

        // Wrap back into Page
        Page<PublicProfileDto> dtoPage = new PageImpl<>(mapped, pageable, mapped.size());

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved {} public profiles (total: {}) in {}ms",
                mapped.size(), users.getTotalElements(), duration);

        return ResponseEntity.ok(dtoPage);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<UserDtoOut> create(@RequestBody @Valid UserDtoIn userDtoIn) {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log user creation
        log.warn("GDPR: Operation=createUser, FirebaseUID={}, DataCreated=new_user_profile, Email={}, Purpose=account_creation", firebaseUid, maskEmail(userDtoIn.getEmail()));

        log.info("Creating new user with email: {}", maskEmail(userDtoIn.getEmail()));
        long startTime = System.currentTimeMillis();

        // CIO-341: Set language-aware default admin note if not explicitly provided
        // Uses user's X-App-Language header (set by AppLanguageFilter in LocaleContextHolder)
        if (userDtoIn.getNoteFromAdmin() == null || userDtoIn.getNoteFromAdmin().isBlank()) {
            String defaultNote = defaultNoteService.getDefaultNote(userDtoIn.getUserType());
            userDtoIn.setNoteFromAdmin(defaultNote);
            log.debug("Set default admin note for new {} user (locale={})",
                    userDtoIn.getUserType(),
                    org.springframework.context.i18n.LocaleContextHolder.getLocale());
        }

        UserDtoOut result = userService.saveAsDto(userDtoIn);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully created user with ID: {} in {}ms", result.getId(), duration);

        return ResponseEntity.ok(result);
    }

    @Override
    // Authorization is owner-or-admin. Any authenticated caller may INVOKE this,
    // but the target row is gated inside UserService.patch ->
    // validateUserUpdatePermission (isAdmin || isUserOwner), which loads the
    // entity and throws InsufficientPermissionsException otherwise. Ownership is
    // enforced at the service layer — where the entity is already loaded — rather
    // than via a SpEL @PreAuthorize, to avoid a duplicate lookup and a second
    // source of truth. Locked by UserService_Patch_IntegrationTest
    // #nonOwnerCannotPatchOther and UserServiceUnitTest#shouldThrowWhenNonOwnerNonAdminChecks.
    @PreAuthorize("isAuthenticated()")
    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<UserDtoOut> patch(@PathVariable Long id, @RequestBody @Valid Map<String, Object> updates) {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log profile modification
        log.warn("GDPR: Operation=patchUser, FirebaseUID={}, TargetUserID={}, DataModified={}, Purpose=profile_update", firebaseUid, id, updates.keySet());

        // Step-up authentication: only an ACTUAL email change is the protected
        // action. The FE PATCHes the full DTO (email is schema-required), so a
        // presence-based gate would 401 every avatar/profile update carrying
        // the caller's own unchanged email. Unchanged email is a no-op in
        // EmailChangeService anyway — no privileged action, no challenge.
        if (updates.containsKey("email")
                && userService.wouldChangeEmail(id, updates.get("email"))) {
            String stepUpToken = request.getHeader("X-Step-Up-Token");
            stepUpAuthService.validateTokenIfRequired(firebaseUid, StepUpActionType.EMAIL_CHANGE, stepUpToken);
        }

        log.info("Patching user with ID: {} - fields: {}", id, updates.keySet());
        long startTime = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        UserDtoOut result = (UserDtoOut) getService().patchAsDto(id, updates);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully patched user with ID: {} in {}ms", id, duration);

        return ResponseEntity.ok(result);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<UserDtoOut> update(@PathVariable Long id, @RequestBody @Valid UserDtoIn dtoIn) {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log full profile update
        log.warn("GDPR: Operation=updateUser, FirebaseUID={}, TargetUserID={}, DataModified=full_profile, Purpose=profile_replacement", firebaseUid, id);

        // Step-up authentication: same actual-change gate as PATCH — a full
        // PUT always carries email, and an unchanged value is not the
        // protected action.
        if (dtoIn.getEmail() != null && userService.wouldChangeEmail(id, dtoIn.getEmail())) {
            String stepUpToken = request.getHeader("X-Step-Up-Token");
            stepUpAuthService.validateTokenIfRequired(firebaseUid, StepUpActionType.EMAIL_CHANGE, stepUpToken);
        }

        log.info("Updating user with ID: {}", id);
        long startTime = System.currentTimeMillis();

        UserDtoOut result = userService.updateAsDto(id, dtoIn);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully updated user with ID: {} in {}ms", id, duration);

        return ResponseEntity.ok(result);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{ids}")
    public ResponseEntity<Void> delete(@PathVariable List<Long> ids) {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log soft deletion
        log.warn("GDPR: Operation=deleteUsers, FirebaseUID={}, TargetUserIDs={}, DataDeleted=soft_delete, Purpose=user_removal", firebaseUid, ids);

        return super.delete(ids);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/delete-permanently/{ids}")
    @Transactional
    public ResponseEntity<Void> deletePermanently(@PathVariable List<Long> ids) {
        // Extract Firebase UID (admin)
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log permanent deletion - CRITICAL
        log.error("GDPR: PERMANENT_DELETION Operation=deletePermanently, AdminFirebaseUID={}, TargetUserIDs={}, DataDeleted=all_user_data, Purpose=permanent_removal, LegalBasis=admin_action", firebaseUid, ids);

        log.info("Permanently deleting {} users with IDs: {}", ids.size(), ids);
        long startTime = System.currentTimeMillis();

        if (ids == null || ids.isEmpty()) {
            log.warn("Permanent delete request rejected - ID list is null or empty");
            throw new ValidationTranslatableException("error.validation.empty_list", "IDs");
        }
        if (ids.size() > 100) {
            log.warn("Permanent delete request rejected - too many IDs: {}", ids.size());
            throw new ValidationTranslatableException("error.validation.list_too_large", "100");
        }

        if (ids.size() == 1) {
            userService.deletePermanently(ids.getFirst());
            log.info("Successfully permanently deleted user with ID: {}", ids.getFirst());
        } else {
            for (Long entityToDelete : ids) {
                userService.deletePermanently(entityToDelete);
            }
            log.info("Successfully permanently deleted {} users", ids.size());
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Permanent delete operation completed in {}ms", duration);

        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping(value = "/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<UserDtoOut> getById(@PathVariable Long id) {
        // Extract Firebase UID (admin)
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log admin user access
        log.warn("GDPR: Operation=getById_admin, AdminFirebaseUID={}, TargetUserID={}, DataAccessed=full_user_profile, Purpose=admin_review", firebaseUid, id);

        log.info("Admin retrieving user with ID: {}", id);
        long startTime = System.currentTimeMillis();

        UserDtoOut userDtoOut = userService.findByIdAsDto(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved user with ID: {} in {}ms", id, duration);

        return ResponseEntity.ok(userDtoOut);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/user/{userId}", produces = "application/json")
    public ResponseEntity<UserDtoOut> getUserByUserId(@PathVariable String userId) {
        // Extract requesting user's Firebase UID
        String requestingFirebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // SECURITY (pentest 3.7 IDOR): this endpoint returns the full account
        // record (email, phone, addresses, admin notes). Knowing another user's
        // Firebase UID must NOT grant access to their data — only the owner or
        // an admin may read it. Non-owner non-admins get 403.
        if (!permissionUtils.isAdmin() && !requestingFirebaseUid.equals(userId)) {
            log.warn("SECURITY: Blocked cross-account user lookup RequestingFirebaseUID={}, TargetFirebaseUID={}",
                    requestingFirebaseUid, userId);
            throw new InsufficientPermissionsException("error.auth.insufficient_permissions");
        }

        // GDPR: Log user lookup by Firebase ID
        log.info("GDPR: Operation=getUserByUserId, RequestingFirebaseUID={}, TargetFirebaseUID={}, DataAccessed=user_profile, Purpose=user_lookup", requestingFirebaseUid, userId);

        log.info("Retrieving user by Firebase user ID: {}", userId);
        long startTime = System.currentTimeMillis();

        // Use the AsDto method that handles collections within transaction
        UserDtoOut userDtoOut = userService.findByFirebaseUserIdAsDto(userId);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved user by Firebase ID: {} (DB ID: {}) in {}ms",
                userId, userDtoOut.getId(), duration);

        return ResponseEntity.ok(userDtoOut);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/accounts/status", produces = "application/json")
    public ResponseEntity<List<String>> getAccountStatusList() {
        log.info("Retrieving account status enum values");

        List<String> statusList = Arrays.stream(AccountStatus.values())
                .map(Enum::name)
                .toList();

        log.debug("Retrieved {} account status values: {}", statusList.size(), statusList);
        return ResponseEntity.ok(statusList);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/type", produces = "application/json")
    public ResponseEntity<List<String>> getUserTypeList() {
        log.info("Retrieving user type enum values");

        List<String> typeList = Arrays.stream(UserType.values())
                .map(Enum::name)
                .toList();

        log.debug("Retrieved {} user type values: {}", typeList.size(), typeList);
        return ResponseEntity.ok(typeList);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PatchMapping("/{id}/premium")
    public ResponseEntity<UserDtoOut> setPremiumStatus(@PathVariable Long id, @RequestParam Boolean premium) {
        // Extract Firebase UID (admin)
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log premium status modification
        log.warn("GDPR: Operation=setPremiumStatus, AdminFirebaseUID={}, TargetUserID={}, DataModified=premium_status, NewValue={}, Purpose=admin_account_management", firebaseUid, id, premium);

        log.info("Admin setting premium status for user {} to {}", id, premium);
        long startTime = System.currentTimeMillis();

        // Use dedicated service method for admin-only premium updates
        User updatedUser = userService.updatePremiumStatus(id, premium);
        UserDtoOut result = userService.toDto(updatedUser);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully updated premium status for user {} to {} in {}ms", id, premium, duration);

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/deletion-eligibility")
    public ResponseEntity<DeletionEligibilityDto> checkMyDeletionEligibility() {
        // Extract Firebase UID
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        log.info("User checking their own deletion eligibility: {}", firebaseUid);
        long startTime = System.currentTimeMillis();

        DeletionEligibilityDto result = userService.checkMyDeletionEligibility();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully checked deletion eligibility for user {} in {}ms", firebaseUid, duration);

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/{id}/deletion-eligibility")
    public ResponseEntity<DeletionEligibilityDto> checkDeletionEligibility(@PathVariable Long id) {
        // Extract Firebase UID (admin)
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log admin deletion eligibility check
        log.warn("GDPR: Operation=checkDeletionEligibility, AdminFirebaseUID={}, TargetUserID={}, Purpose=deletion_assessment", firebaseUid, id);

        log.info("Admin checking deletion eligibility for user ID: {}", id);
        long startTime = System.currentTimeMillis();

        DeletionEligibilityDto result = userService.checkDeletionEligibilityById(id);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully checked deletion eligibility for user ID {} in {}ms", id, duration);

        return ResponseEntity.ok(result);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping(value = "/paged")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<UserDtoOut>> findPaginated(Pageable pageable, @RequestParam Map<String, String> filters) {
        // Extract Firebase UID (admin)
        String firebaseUid = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log bulk user data access
        log.warn("GDPR: Operation=findPaginated_admin, AdminFirebaseUID={}, DataAccessed=user_list, PageSize={}, Purpose=admin_user_management",
                firebaseUid, pageable.isPaged() ? pageable.getPageSize() : "unpaged");

        log.info("Admin accessing paginated users endpoint");
        long startTime = System.currentTimeMillis();

        // Remove pagination parameters from filters
        filters.remove("page");
        filters.remove("size");
        filters.remove("sort");
        filters.remove("direction");

        Page<UserDtoOut> dtoPage = userService.getDataPagedAndFilteredAsDtos(pageable, filters);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved {} users (total: {}) in {}ms",
                dtoPage.getNumberOfElements(), dtoPage.getTotalElements(), duration);

        return ResponseEntity.ok(dtoPage);
    }

    @Override
    protected BaseService<User, Long, UserDtoIn> getService() {
        return userService;
    }
}
