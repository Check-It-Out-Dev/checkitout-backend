package com.sm.instagram.platform.common.authorization;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(100) // Run after critical components are initialized
@ConditionalOnProperty(
    name = "admin.check.enabled",
    havingValue = "true",
    matchIfMissing = true // Enable by default in production
)
public class AdminCheckRunner implements CommandLineRunner {

    @Value("${ADMIN_FIREBASE_UID:}")
    private String adminFirebaseUid;
    
    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    private final FirebaseAuth firebaseAuth;
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;
    private final TotpFirestoreService totpFirestoreService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=========================================");
        log.info("👤 ADMIN USER VERIFICATION STARTING");
        log.info("=========================================");
        
        if (adminFirebaseUid == null || adminFirebaseUid.trim().isEmpty()) {
            log.error("❌ CRITICAL: ADMIN_FIREBASE_UID not configured in .env file");
            throw new ExternalServiceException(
                "ADMIN_FIREBASE_UID must be set in .env file",
                "Configuration",
                "adminSetup",
                null
            );
        }
        
        if (adminEmail == null || adminEmail.trim().isEmpty()) {
            log.error("❌ CRITICAL: ADMIN_EMAIL not configured in .env file");
            throw new ExternalServiceException(
                "ADMIN_EMAIL must be set in .env file",
                "Configuration",
                "adminSetup",
                null
            );
        }
        
        log.info("Admin UID: {}", adminFirebaseUid);
        log.info("Admin Email: {}", adminEmail);

        try {
            // 1. Verify user exists in Firebase, or create if missing
            final UserRecord userRecord = getOrCreateFirebaseAdmin();

            // 2. Check 2FA status in Firestore (NOT database)
            boolean has2FA = totpFirestoreService.is2FAEnabled(adminFirebaseUid);

            // 3. Set appropriate role based on 2FA status
            Map<String, Object> claims = userRecord.getCustomClaims();
            String currentRole = claims != null ? (String) claims.get("role") : null;
            String expectedRole = has2FA ? "ADMIN" : "PENDING_ADMIN";

            if (!expectedRole.equals(currentRole)) {
                // Update Firebase custom claims with new role
                Map<String, Object> newClaims = claims != null ? new java.util.HashMap<>(claims) : new java.util.HashMap<>();
                newClaims.put("role", expectedRole);
                firebaseAuth.setCustomUserClaims(adminFirebaseUid, newClaims);
                log.info("✅ Firebase role updated to: {}", expectedRole);
            } else {
                log.info("✔️ Firebase role already correct: {}", currentRole);
            }

            // 4. Ensure user exists in PostgreSQL database (for business data only)
            final boolean finalHas2FA = has2FA;
            User adminUser = userRepository.findByFirebaseUserId(adminFirebaseUid)
                .orElseGet(() -> createAdminInDatabase(userRecord, finalHas2FA));
            
            // 5. Verify database user has correct type and status
            // UserType should match Firebase role (ADMIN or PENDING_ADMIN)
            boolean needsUpdate = false;
            UserType expectedUserType = has2FA ? UserType.ADMIN : UserType.PENDING_ADMIN;
            
            if (adminUser.getUserType() != expectedUserType) {
                adminUser.setUserType(expectedUserType);
                needsUpdate = true;
                log.info("🔧 Updated user type to {} in database to match Firebase role", expectedUserType);
            }
            
            if (adminUser.getAccountStatus() != AccountStatus.ACTIVE) {
                adminUser.setAccountStatus(AccountStatus.ACTIVE);
                needsUpdate = true;
                log.info("🔧 Updated account status to ACTIVE in database");
            }
            
            // 6. Update email if it doesn't match configured value
            if (!adminEmail.equals(adminUser.getEmail())) {
                adminUser.setEmail(adminEmail);
                needsUpdate = true;
                log.info("🔧 Updated admin email to configured value: {}", adminEmail);
            }
            
            if (needsUpdate) {
                userRepository.save(adminUser);
                log.info("💾 Admin user updated in database");
            } else {
                log.info("✔️ Admin user already correctly configured in database");
            }
            
            log.info("=========================================");
            log.info("✅ ADMIN SETUP COMPLETE");
            log.info("   Firebase UID: {}", adminFirebaseUid);
            log.info("   Email: {}", adminEmail);
            log.info("   Firebase Role: {}", expectedRole);
            log.info("   Database Type: {}", adminUser.getUserType());
            log.info("   2FA Status: {}", has2FA ? "Configured" : "Not configured");
            log.info("   Database ID: {}", adminUser.getId());
            log.info("   Account Status: {}", adminUser.getAccountStatus());
            if (!has2FA) {
                log.info("   ⚠️ ACTION REQUIRED: Admin needs to setup 2FA for full access");
                log.info("   📝 Current access level: PENDING_ADMIN (limited)");
            } else {
                log.info("   ✅ Full admin access granted");
            }
            log.info("=========================================");

        } catch (Exception e) {
            log.error("=========================================");
            log.error("❌ CRITICAL: Failed to setup admin user");
            log.error("   UID: {}", adminFirebaseUid);
            log.error("   Email: {}", adminEmail);
            log.error("   Error: {}", e.getMessage(), e);
            log.error("=========================================");

            throw new ExternalServiceException(
                "Failed to initialize admin user - CHECK FIREBASE AND DATABASE!",
                "AdminSetup",
                "initialization",
                e
            );
        }
    }

    private User createAdminInDatabase(UserRecord firebaseRecord, boolean has2FA) {
        log.info("📝 Creating admin user in database...");
        
        // Set UserType based on 2FA status to match Firebase role
        UserType userType = has2FA ? UserType.ADMIN : UserType.PENDING_ADMIN;
        
        User adminUser = new User();
        adminUser.setFirebaseUserId(adminFirebaseUid);
        adminUser.setUserType(userType);
        adminUser.setAccountStatus(AccountStatus.ACTIVE);
        
        log.info("🔑 Setting user type: {} (2FA {})", userType, has2FA ? "enabled" : "not enabled");
        
        // Use configured email
        adminUser.setEmail(adminEmail);
        
        // Set display name if available from Firebase
        if (firebaseRecord.getDisplayName() != null) {
            adminUser.setName(firebaseRecord.getDisplayName());
            
            // Try to split display name into first/last
            String[] nameParts = firebaseRecord.getDisplayName().split(" ", 2);
            if (nameParts.length > 0) {
                adminUser.setFirstName(nameParts[0]);
            }
            if (nameParts.length > 1) {
                adminUser.setLastName(nameParts[1]);
            }
        } else {
            // Use email prefix as name if no display name
            String emailPrefix = adminEmail.substring(0, adminEmail.indexOf('@'));
            adminUser.setName(emailPrefix);
            adminUser.setFirstName(emailPrefix);
        }
        
        // Set admin note
        adminUser.setNoteFromAdmin("System Administrator Account");
        
        // Save to database - Add null check
        User savedUser = userRepository.save(adminUser);
        if (savedUser != null && savedUser.getId() != null) {
            log.info("✅ Admin user created in database with ID: {}", savedUser.getId());
        } else {
            log.error("❌ Failed to save admin user to database - repository returned null");
            throw new ExternalServiceException(
                "Database save operation failed - check database configuration",
                "Database",
                "adminSetup",
                null
            );
        }
        
        return savedUser;
    }

    /**
     * Gets admin user from Firebase, or creates if not found.
     */
    private UserRecord getOrCreateFirebaseAdmin() throws FirebaseAuthException {
        try {
            UserRecord userRecord = firebaseAuth.getUser(adminFirebaseUid);
            log.info("✅ Admin found in Firebase: {}", userRecord.getEmail());
            return userRecord;
        } catch (FirebaseAuthException e) {
            boolean isUserNotFound = (e.getAuthErrorCode() != null &&
                "USER_NOT_FOUND".equals(e.getAuthErrorCode().name())) ||
                (e.getMessage() != null && e.getMessage().contains("No user record found"));

            if (isUserNotFound) {
                log.warn("⚠️ Admin user not found in Firebase, creating with UID: {}", adminFirebaseUid);
                UserRecord created = createAdminInFirebase();
                log.info("✅ Admin created in Firebase: {}", created.getEmail());
                return created;
            } else {
                throw e;
            }
        }
    }

    /**
     * Creates admin user in Firebase Auth with the configured UID.
     * This is used to restore accidentally deleted admin users.
     */
    private UserRecord createAdminInFirebase() throws FirebaseAuthException {
        if (adminPassword == null || adminPassword.trim().isEmpty()) {
            throw new ExternalServiceException(
                "ADMIN_PASSWORD must be set in .env file to create admin user in Firebase",
                "Configuration",
                "adminSetup",
                null
            );
        }

        log.info("📝 Creating admin user in Firebase with UID: {}", adminFirebaseUid);

        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
            .setUid(adminFirebaseUid)
            .setEmail(adminEmail)
            .setPassword(adminPassword)
            .setEmailVerified(true)
            .setDisplayName("Admin User");

        UserRecord userRecord = firebaseAuth.createUser(request);

        // Set initial claims (PENDING_ADMIN until 2FA is set up)
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("role", "PENDING_ADMIN");
        firebaseAuth.setCustomUserClaims(adminFirebaseUid, claims);

        log.info("✅ Admin user created in Firebase with UID: {}", userRecord.getUid());
        return userRecord;
    }

    private boolean isUserAdmin(UserRecord userRecord) {
        Map<String, Object> customClaims = userRecord.getCustomClaims();
        if (customClaims != null && customClaims.containsKey("role")) {
            String role = (String) customClaims.get("role");
            return Permission.ADMIN.name().equals(role);
        }
        return false;
    }
}
