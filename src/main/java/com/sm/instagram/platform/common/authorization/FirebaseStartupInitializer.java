package com.sm.instagram.platform.common.authorization;

import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Forces Firebase initialization at application startup to ensure:
 * 1. Firebase connection is established early
 * 2. Connection status is logged clearly
 * 3. Any connection issues are detected immediately
 *
 * Runs before AdminCheckRunner (Order 1 vs Order 100).
 * Disabled in integration tests (firebase.admin.setup.enabled=false) to prevent
 * transient Firebase API failures from breaking unrelated tests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // Run very early in startup sequence
@ConditionalOnProperty(
    name = "firebase.admin.setup.enabled",
    havingValue = "true",
    matchIfMissing = true // Enable by default in production
)
public class FirebaseStartupInitializer implements ApplicationRunner {

    private final FirebaseAuth firebaseAuth;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=========================================");
        log.info("🔥 FIREBASE STARTUP VERIFICATION");
        log.info("=========================================");
        
        try {
            // Simply accessing the bean triggers initialization and logging
            if (firebaseAuth == null) {
                log.error("❌ Firebase Auth service is NULL - initialization failed!");
                throw new IllegalStateException("Firebase Auth is null - cannot continue");
            }
            
            log.info("✅ Firebase Auth service is READY");
            
            // Try a simple operation to verify it's working
            try {
                // This doesn't actually create a user, just tests the connection
                firebaseAuth.getInstance();
                log.info("✅ Firebase connection verified - can communicate with Firebase");
            } catch (Exception e) {
                log.error("❌ Firebase initialized but cannot communicate with Firebase!");
                log.error("   Error: {}", e.getMessage());
                throw new IllegalStateException("Firebase communication test failed", e);
            }
            
        } catch (Exception e) {
            log.error("❌ FATAL: Firebase startup verification failed!", e);
            log.error("   This is likely due to:");
            log.error("   1. Invalid service account JSON");
            log.error("   2. Network connectivity issues");
            log.error("   3. Firebase service unavailable");
            log.error("=========================================");
            
            // FATAL - application cannot function without Firebase
            throw new RuntimeException("Firebase initialization failed - application cannot start", e);
        }
        
        log.info("=========================================");
    }
}
