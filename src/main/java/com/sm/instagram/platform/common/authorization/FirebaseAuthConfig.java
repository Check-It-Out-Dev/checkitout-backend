package com.sm.instagram.platform.common.authorization;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;

/**
 * Firebase Authentication and Firestore configuration.
 * Uses centralized GoogleCredentialsProvider to eliminate duplicate credential loading.
 * 
 * @author CheckItOut Team
 * @since 2.0.0 - Refactored to use GoogleCredentialsProvider
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseAuthConfig {

    private final GoogleCredentialsProvider credentialsProvider;
    
    @Value("${app.environment:UNKNOWN}")
    private String appEnvironment;
    
    /**
     * Initialize Firebase Authentication service.
     * 
     * @return FirebaseAuth instance
     * @throws IOException if credentials cannot be loaded
     */
    @Bean(initMethod = "")
    @org.springframework.context.annotation.Lazy(false)  // Force eager initialization
    public FirebaseAuth firebaseAuth() throws IOException {
        log.info("=========================================");
        log.info("🔥 FIREBASE AUTHENTICATION INITIALIZATION");
        log.info("=========================================");
        log.info("Environment: {}", appEnvironment);
        
        // Get credentials and project ID from the centralized provider
        String projectId = credentialsProvider.getProjectId();
        log.info("Using Project ID: {}", projectId);
        log.info("Instance Type: {}", credentialsProvider.getInstanceType());
        
        // Build Firebase options with credentials from provider
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentialsProvider.getCachedCredentials())
                .setProjectId(projectId)
                .build();

        // Initialize Firebase App if not already initialized
        FirebaseApp firebaseApp;
        if (FirebaseApp.getApps().isEmpty()) {
            firebaseApp = FirebaseApp.initializeApp(options);
            
            // Verify connection and log details
            String connectedProjectId = firebaseApp.getOptions().getProjectId();
            
            if (connectedProjectId == null) {
                log.error("❌ FIREBASE CONNECTION FAILED!");
                log.error("   Could not determine project ID from Firebase connection");
                log.error("   Possible causes:");
                log.error("   1. Invalid service account JSON");
                log.error("   2. Missing 'project_id' field in credentials");
                log.error("   3. Network connectivity issues");
                log.error("   4. Firebase service temporarily unavailable");
                throw new IllegalStateException("Firebase initialization failed - no project ID available");
            }
            
            log.info("✅ Firebase connection SUCCESSFUL");
            log.info("📍 Connected to project: {}", connectedProjectId);
            log.info("   Instance type: {}", credentialsProvider.getInstanceType());
            
            // Verify environment matches credentials
            verifyEnvironmentCredentialMatch(connectedProjectId);
            
        } else {
            firebaseApp = FirebaseApp.getInstance();
            String connectedProjectId = firebaseApp.getOptions().getProjectId();
            log.info("♻️ Using existing Firebase App instance");
            log.info("📍 Already connected to: {}", connectedProjectId);
            log.info("   Instance type: {}", credentialsProvider.getInstanceType());
        }
        
        log.info("=========================================");

        return FirebaseAuth.getInstance(firebaseApp);
    }
    
    /**
     * Initialize Firestore client.
     * Depends on FirebaseAuth bean to ensure Firebase is initialized first.
     * 
     * @return Firestore client instance
     */
    @Bean(destroyMethod = "")  // Prevent Spring from closing Firestore prematurely
    @DependsOn("firebaseAuth")
    public Firestore firestore() {
        log.info("Initializing Firestore client");
        
        // Firestore requires FirebaseApp to be initialized first
        if (FirebaseApp.getApps().isEmpty()) {
            log.error("FirebaseApp not initialized! This should not happen if @DependsOn is working.");
            throw new IllegalStateException("FirebaseApp must be initialized before Firestore. " +
                "Ensure FirebaseAuth bean is created first.");
        }
        
        Firestore firestore = FirestoreClient.getFirestore();
        log.info("✅ Firestore client initialized successfully");
        
        return firestore;
    }

    /**
     * Configure JWT decoder for Firebase ID token validation.
     * 
     * @param projectId Firebase project ID for JWT validation
     * @return JwtDecoder configured for Firebase tokens
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String projectId = credentialsProvider.getProjectId();
        log.info("Configuring JWT decoder for Firebase project: {}", projectId);
        
        // Firebase JWT issuer URL
        String issuerUri = "https://securetoken.google.com/" + projectId;
        
        // Use NimbusJwtDecoder with Firebase's public keys
        return NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
                .build();
    }
    
    /**
     * Verify that environment and credentials match to prevent accidental
     * use of production credentials in test environments or vice versa.
     * 
     * @param connectedProjectId The actual Firebase project ID we're connected to
     */
    private void verifyEnvironmentCredentialMatch(String connectedProjectId) {
        boolean isProductionEnv = "PRODUCTION".equals(appEnvironment) || 
                                  "PRODUCTION-STANDALONE".equals(appEnvironment);
        boolean isConnectedToProd = "check-it-out-prod".equals(connectedProjectId);
        
        if (isProductionEnv && !isConnectedToProd) {
            log.error("❌ CRITICAL MISMATCH: Production environment but NOT connected to production Firebase!");
            log.error("   Environment: {}", appEnvironment);
            log.error("   Connected to: {}", connectedProjectId);
            log.error("   Expected: check-it-out-prod");
            // In production, this should potentially throw an exception
        } else if (!isProductionEnv && isConnectedToProd) {
            log.warn("⚠️ WARNING: Non-production environment connected to PRODUCTION Firebase!");
            log.warn("   Environment: {}", appEnvironment);
            log.warn("   Connected to: {} (PRODUCTION!)", connectedProjectId);
            log.warn("   This may be intentional for pre-release debugging");
        } else {
            log.info("✓ Environment and credentials match appropriately");
        }
    }
}
