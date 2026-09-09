package com.sm.instagram.platform.common.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Where the Firebase Auth emulator is, when one is running.
 *
 * <p>The Admin SDK finds the emulator by itself: {@code FirebaseAuth} reads
 * {@code FIREBASE_AUTH_EMULATOR_HOST} and {@code Firestore} reads {@code FIRESTORE_EMULATOR_HOST},
 * switching their own endpoints and skipping signature checks on ID tokens. The calls this
 * application makes to the Identity Toolkit REST API by hand do not: they carry a hard-coded
 * production host and an OAuth bearer token minted from the service account. This component is
 * what those call sites ask instead.
 *
 * <p>The emulator serves the production API under a path prefix, so
 * {@code https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword} becomes
 * {@code http://<host>/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword}, and it
 * accepts the literal bearer token {@code owner} in place of a real one. Nothing here is reachable
 * unless the environment variable is set, which it never is outside a test run.
 */
@Slf4j
@Component
public class FirebaseEmulator {

    private static final String PRODUCTION_IDENTITY_TOOLKIT = "https://identitytoolkit.googleapis.com/v1";
    private static final String PRODUCTION_SECURE_TOKEN = "https://securetoken.googleapis.com/v1";

    /** The emulator's owner token: any request carrying it is treated as fully privileged. */
    private static final String EMULATOR_OWNER_TOKEN = "owner";

    private final String authHost;

    public FirebaseEmulator(@Value("${FIREBASE_AUTH_EMULATOR_HOST:}") String authHost) {
        this.authHost = authHost == null ? "" : authHost.trim();
    }

    @PostConstruct
    void announce() {
        if (isEnabled()) {
            log.warn("Firebase Auth EMULATOR in use at {} - no request will reach Google, and ID tokens are unsigned",
                    authHost);
        }
    }

    public boolean isEnabled() {
        return !authHost.isEmpty();
    }

    /** Base for {@code accounts:signInWithPassword}, {@code accounts:signUp}, {@code accounts:update}, ... */
    public String identityToolkitBase() {
        return isEnabled() ? "http://" + authHost + "/identitytoolkit.googleapis.com/v1" : PRODUCTION_IDENTITY_TOOLKIT;
    }

    /** Base for the refresh-token exchange. */
    public String secureTokenBase() {
        return isEnabled() ? "http://" + authHost + "/securetoken.googleapis.com/v1" : PRODUCTION_SECURE_TOKEN;
    }

    /**
     * The bearer token for a REST call, or empty when the caller should mint a real one.
     * Returning an {@code Optional}-shaped empty string keeps the call sites free of a branch on
     * {@link #isEnabled()} that could drift out of step with this class.
     */
    public String bearerTokenOrEmpty() {
        return isEnabled() ? EMULATOR_OWNER_TOKEN : "";
    }
}
