package com.sm.instagram.platform.userpreferences;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("user-preferences")
// java:S6856 -- the overrides below re-declare @PathVariable but inherit their mapping from
// BaseController, where the "{id}"/"{ids}" templates are declared. Spring resolves them through
// the type hierarchy; the rule looks only at the method in front of it. Restating the paths here
// would give every route two declarations and one of them would eventually be wrong.
@SuppressWarnings("java:S6856")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class UserPreferencesController extends BaseController<UserPreferences, Long, UserPreferencesDtoIn, UserPreferencesDtoOut> {
    private final UserPreferencesService userPreferencesService;

    protected UserPreferencesController(UserPreferencesService userPreferencesService) {
        super(UserPreferences.class);
        this.userPreferencesService = userPreferencesService;
    }

    @Override
    protected BaseService<UserPreferences, Long, UserPreferencesDtoIn> getService() {
        return userPreferencesService;
    }

    // ----------------- Overrides with PreAuthorize -----------------

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserPreferencesDtoOut> getById(@PathVariable Long id) {
        return super.getById(id);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Page<UserPreferencesDtoOut>> findPaginated(Pageable pageable, @RequestParam Map<String, String> filters) {
        return super.findPaginated(pageable, filters);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserPreferencesDtoOut> create(@Valid @RequestBody UserPreferencesDtoIn dto) {
        return super.create(dto);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserPreferencesDtoOut> update(@PathVariable Long id, @Valid @RequestBody UserPreferencesDtoIn dto) {
        return super.update(id, dto);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserPreferencesDtoOut> patch(@PathVariable Long id, @Valid @RequestBody Map<String, Object> updates) {
        return super.patch(id, updates);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable List<Long> ids) {
        return super.delete(ids);
    }

    /**
     * Get the current user's preferences
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<UserPreferencesDtoOut> getCurrentUserPreferences() {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log preference read operation
        log.info("GDPR: Operation=getCurrentUserPreferences, FirebaseUID={}, Purpose=user_preference_display, DataAccessed=user.preferences.*",
                firebaseUid);
        long startTime = System.currentTimeMillis();

        UserPreferencesDtoOut result = userPreferencesService.getCurrentUserPreferencesAsDto();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved preferences for current user in {}ms", duration);

        // GDPR: Log successful data retrieval
        log.info("GDPR: Operation=getCurrentUserPreferences_SUCCESS, FirebaseUID={}, DataRetrieved=user_preferences, Purpose=display_to_owner",
                firebaseUid);

        return ResponseEntity.ok(result);
    }

    /**
     * Update current user's preferences
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<UserPreferencesDtoOut> updateCurrentUserPreferences(
            @RequestBody @Valid UserPreferencesDtoIn userPreferencesDtoIn
    ) {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log preference update operation
        log.info("GDPR: Operation=updateCurrentUserPreferences, FirebaseUID={}, DataModified=user.preferences.*, Purpose=user_requested_update, LegalBasis=consent",
                firebaseUid);
        log.debug("Updating current user preferences with data: {}", userPreferencesDtoIn);
        long startTime = System.currentTimeMillis();

        UserPreferencesDtoOut result = userPreferencesService.updateCurrentUserPreferencesAsDto(userPreferencesDtoIn);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully updated preferences for current user in {}ms", duration);

        // GDPR: Log successful preference update
        log.info("GDPR: Operation=updateCurrentUserPreferences_SUCCESS, FirebaseUID={}, DataModified=user_preferences, Purpose=settings_update",
                firebaseUid);

        return ResponseEntity.ok(result);
    }

    /**
     * Patch current user's preferences
     */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me")
    public ResponseEntity<UserPreferencesDtoOut> patchCurrentUserPreferences(
            @RequestBody Map<String, Object> updates
    ) {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log partial preference update
        log.info("GDPR: Operation=patchCurrentUserPreferences, FirebaseUID={}, FieldsModified={}, Purpose=partial_settings_update, LegalBasis=consent",
                firebaseUid, updates.keySet());
        log.debug("Patching current user preferences with updates: {}", updates);
        long startTime = System.currentTimeMillis();

        UserPreferencesDtoOut result = userPreferencesService.patchCurrentUserPreferencesAsDto(updates);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully patched preferences for current user in {}ms", duration);

        return ResponseEntity.ok(result);
    }

    /**
     * Get user preferences by User ID (admin only)
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserPreferencesDtoOut> getUserPreferences(
            @PathVariable Long userId
    ) {
        String adminFirebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log admin access to user preferences
        log.warn("GDPR: ADMIN_ACCESS Operation=getUserPreferences, AdminFirebaseUID={}, TargetUserID={}, Purpose=admin_support, LegalBasis=legitimate_interest, DataAccessed=user.preferences.*",
                adminFirebaseUid, userId);
        long startTime = System.currentTimeMillis();

        UserPreferencesDtoOut result = userPreferencesService.getUserPreferencesAsDto(userId);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully retrieved preferences for user ID: {} in {}ms", userId, duration);

        return ResponseEntity.ok(result);
    }

    /**
     * Update any user's preferences (admin only)
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @PatchMapping("/user/{userId}")
    public ResponseEntity<UserPreferencesDtoOut> patchUserPreferences(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> updates
    ) {
        String adminFirebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log admin modification of user preferences
        log.warn("GDPR: ADMIN_MODIFICATION Operation=patchUserPreferences, AdminFirebaseUID={}, TargetUserID={}, FieldsModified={}, Purpose=admin_support, LegalBasis=legitimate_interest",
                adminFirebaseUid, userId, updates.keySet());
        log.debug("Admin patching user {} preferences with updates: {}", userId, updates);
        long startTime = System.currentTimeMillis();

        UserPreferencesDtoOut result = userPreferencesService.patchUserPreferencesAsDto(userId, updates);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully patched preferences for user ID: {} in {}ms", userId, duration);

        return ResponseEntity.ok(result);
    }
}
