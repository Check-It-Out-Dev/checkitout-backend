package com.sm.instagram.platform.admin;

import com.google.firebase.auth.FirebaseAuthException;
import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.common.authorization.UserManagementService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
@RateLimit(profile = RateLimitProfile.HIGH, keyType = RateLimitKeyType.USER_ENDPOINT)  // 300 req/min for admin operations
public class AdminController {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String REQUEST_ID = "REQUEST_ID";
    private final UserManagementService userManagementService;

    /**
     * Sets user claims/permissions for a specific user.
     *
     * @param uid             The Firebase UID of the target user
     * @param requestedClaims List of permissions to set for the user
     * @throws FirebaseAuthException             if Firebase authentication fails
     * @throws ValidationTranslatableException   if input parameters are invalid
     * @throws ResourceNotFoundException         if authentication context is missing
     * @throws BusinessRuleTranslatableException if permission management fails
     */
    @PostMapping(path = "/user-claims/{uid}")
    public void setUserClaims(
            @PathVariable String uid,
            @RequestBody List<Permission> requestedClaims
    ) throws FirebaseAuthException {
        // Validate input parameters
        if (uid == null || uid.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "uid");
        }

        if (requestedClaims == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "requestedClaims");
        }

        long startTime = System.currentTimeMillis();

        // Get admin identity from security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }

        String adminUid = auth.getName();
        String adminEmail = auth.getPrincipal().toString();

        // GDPR: Log admin permission modification
        log.info("GDPR: Operation=setUserClaims, FirebaseUID={}, TargetUID={}, DataAccessed=user.permissions, Purpose=permission_management, LegalBasis=legitimate_interest",
                adminUid, uid);

        // Get current permissions before change (for audit trail)
        List<Permission> oldPermissions = null;
        try {
            oldPermissions = userManagementService.getUserPermissions(uid);
        } catch (Exception e) {
            // Intentionally continue - audit trail is best-effort, not critical for operation
            log.warn("Could not retrieve old permissions for audit (non-critical): {}", e.getMessage(), e);
        }

        // AUDIT LOG - Admin action attempt
        log.info("ADMIN_ACTION event=\"modify_user_permissions\" request_id=\"{}\" " +
                        "admin_uid=\"{}\" admin_email=\"{}\" action=\"SET_USER_CLAIMS\" " +
                        "target_uid=\"{}\" old_permissions=\"{}\" new_permissions=\"{}\"",
                MDC.get(REQUEST_ID), adminUid, adminEmail, uid,
                oldPermissions != null ? oldPermissions : UNKNOWN, requestedClaims);

        try {
            userManagementService.setUserClaims(uid, requestedClaims);

            long duration = System.currentTimeMillis() - startTime;

            // GDPR: Log successful permission change
            log.info("GDPR: Operation=setUserClaims_SUCCESS, FirebaseUID={}, TargetUID={}, DataModified=user.permissions, OldPermissions={}, NewPermissions={}",
                    adminUid, uid, oldPermissions != null ? oldPermissions : UNKNOWN, requestedClaims);

            // AUDIT LOG - Admin action success
            log.info("ADMIN_ACTION_SUCCESS event=\"permissions_modified\" request_id=\"{}\" " +
                            "admin_uid=\"{}\" admin_email=\"{}\" target_uid=\"{}\" " +
                            "permissions_changed_from=\"{}\" permissions_changed_to=\"{}\" " +
                            "execution_time_ms={}",
                    MDC.get(REQUEST_ID), adminUid, adminEmail, uid,
                    oldPermissions != null ? oldPermissions : UNKNOWN,
                    requestedClaims, duration);

        } catch (FirebaseAuthException e) {
            long duration = System.currentTimeMillis() - startTime;

            // GDPR: Log failed permission change
            log.error("GDPR: Operation=setUserClaims_FAILED, FirebaseUID={}, TargetUID={}, Error={}",
                    adminUid, uid, e.getMessage(), e);

            // AUDIT LOG - Admin action failed
            log.error("ADMIN_ACTION_FAILED event=\"permissions_modification_failed\" request_id=\"{}\" " +
                            "admin_uid=\"{}\" admin_email=\"{}\" target_uid=\"{}\" " +
                            "error_code=\"{}\" error_message=\"{}\" execution_time_ms={}",
                    MDC.get(REQUEST_ID), adminUid, adminEmail, uid,
                    e.getErrorCode(), e.getMessage(), duration);
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // AUDIT LOG - Admin action failed
            log.error("ADMIN_ACTION_FAILED event=\"permissions_modification_failed\" request_id=\"{}\" " +
                            "admin_uid=\"{}\" admin_email=\"{}\" target_uid=\"{}\" " +
                            "error_message=\"{}\" execution_time_ms={}",
                    MDC.get(REQUEST_ID), adminUid, adminEmail, uid,
                    e.getMessage(), duration, e);

            throw new AuthenticationTranslatableException("error.auth.permission_update_failed");
        }
    }
}
