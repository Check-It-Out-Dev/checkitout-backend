package com.sm.instagram.platform.common.authorization;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final FirebaseAuth firebaseAuth;
    
    /**
     * Safely extract a list of strings from an object that might be a List.
     * This method handles the type checking properly to avoid unchecked warnings.
     * 
     * @param obj The object to extract strings from
     * @return A list of valid string values, empty list if obj is not a List
     */
    private List<String> extractStringList(Object obj) {
        List<String> result = new ArrayList<>();
        
        if (obj instanceof List<?>) {
            List<?> list = (List<?>) obj;
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                } else if (item != null) {
                    log.warn("Non-string value in list: {} (type: {})", item, item.getClass().getSimpleName());
                }
            }
        }
        
        return result;
    }

    public void setUserClaims(String uid, List<Permission> requestedPermissions) throws FirebaseAuthException {
        // GDPR: Log permission modification operation
        log.info("GDPR: Operation=setUserClaims, FirebaseUID={}, RequestedPermissions={}, Purpose=permission_management, LegalBasis=legitimate_interest",
                uid, requestedPermissions);
        
        // Get the primary role (first permission in list)
        String role = requestedPermissions.isEmpty() ? "USER" : requestedPermissions.get(0).toString();
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        
        // If multiple permissions, store them as well (for future use)
        if (requestedPermissions.size() > 1) {
            List<String> permissions = requestedPermissions
                    .stream()
                    .map(Enum::toString)
                    .toList();
            claims.put("permissions", permissions);
        }

        try {
            firebaseAuth.setCustomUserClaims(uid, claims);
            
            // GDPR: Log successful permission update
            log.info("GDPR: Operation=setUserClaims_SUCCESS, FirebaseUID={}, UpdatedRole={}, UpdatedPermissions={}, DataModified=user.claims,user.role,user.permissions, Purpose=access_control",
                    uid, role, requestedPermissions);
        } catch (FirebaseAuthException e) {
            // GDPR: Log failed permission update
            log.error("GDPR: Operation=setUserClaims_FAILED, FirebaseUID={}, AttemptedRole={}, Error={}, Purpose=permission_management",
                    uid, role, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get current permissions for a user (for audit logging).
     * 
     * @param uid Firebase user ID
     * @return List of current permissions
     * @throws FirebaseAuthException if user not found
     */
    public List<Permission> getUserPermissions(String uid) throws FirebaseAuthException {
        // GDPR: Log permission read operation
        log.info("GDPR: Operation=getUserPermissions, FirebaseUID={}, Purpose=permission_verification, DataAccessed=user.claims,user.role,user.permissions",
                uid);
        
        UserRecord userRecord = firebaseAuth.getUser(uid);
        Map<String, Object> claims = userRecord.getCustomClaims();
        
        List<Permission> permissions = new ArrayList<>();
        
        // Check for role claim
        String role = (String) claims.get("role");
        if (role != null) {
            try {
                permissions.add(Permission.valueOf(role));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown role in claims: {}", role);
            }
        }
        
        // Check for additional permissions
        Object permissionsClaim = claims.get("permissions");
        List<String> permissionStrings = extractStringList(permissionsClaim);
        
        for (String perm : permissionStrings) {
            try {
                Permission permission = Permission.valueOf(perm);
                if (!permissions.contains(permission)) {
                    permissions.add(permission);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Unknown permission in claims: {}", perm);
            }
        }
        
        // GDPR: Log retrieved permissions
        log.debug("GDPR: Operation=getUserPermissions_COMPLETE, FirebaseUID={}, PermissionsRetrieved={}, DataAccessed=user.claims, Purpose=authorization_check",
                uid, permissions);
        
        return permissions;
    }
}