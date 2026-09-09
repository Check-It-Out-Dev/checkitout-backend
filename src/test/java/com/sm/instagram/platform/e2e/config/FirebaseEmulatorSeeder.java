package com.sm.instagram.platform.e2e.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.cloud.firestore.Firestore;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Puts the three fixed e2e actors into the Firebase Auth emulator, once, at context start.
 *
 * <p>The suite signs in as an admin, a company and an influencer with fixed Firebase UIDs. Against
 * the real project those accounts already exist; against a freshly started emulator nothing exists,
 * so this creates them. It runs only when {@code FIREBASE_AUTH_EMULATOR_HOST} is set, which is
 * never true outside a test run.
 *
 * <p>Seeding goes through the application's own beans on purpose. The admin's TOTP secret is
 * written with {@link TotpFirestoreService}, so it is encrypted by whatever cipher the application
 * is configured with - Cloud KMS in production, the configured local key when KMS is off. A seeder
 * that wrote the document itself would encode the format twice and drift.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseEmulatorSeeder {

    private final FirebaseAuth firebaseAuth;
    private final TotpFirestoreService totpFirestoreService;
    private final Firestore firestore;

    @Value("${FIREBASE_AUTH_EMULATOR_HOST:}")
    private String emulatorHost;

    @Value("${e2e.seed.password:e2e-emulator-password}")
    private String password;

    @Value("${e2e.admin.firebase-uid:}")
    private String adminUid;
    @Value("${e2e.admin.email:}")
    private String adminEmail;
    @Value("${e2e.admin.totp-secret:}")
    private String adminTotpSecret;

    @Value("${e2e.company.firebase-uid:}")
    private String companyUid;
    @Value("${e2e.company.email:}")
    private String companyEmail;

    @Value("${e2e.influencer.firebase-uid:}")
    private String influencerUid;
    @Value("${e2e.influencer.email:}")
    private String influencerEmail;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (emulatorHost == null || emulatorHost.isBlank()) {
            return;
        }
        log.info("Seeding the Firebase Auth emulator at {}", emulatorHost);

        upsert(adminUid, adminEmail, "Admin");
        upsert(companyUid, companyEmail, "Company");
        upsert(influencerUid, influencerEmail, "Influencer");

        if (!isBlank(adminUid) && !isBlank(adminTotpSecret)) {
            try {
                // Only when it is missing: re-running the seeder must not rotate a secret the suite
                // has already read, and a suite that shares one emulator across runs would then fail.
                if (!totpFirestoreService.totpSecretExists(adminUid)) {
                    totpFirestoreService.storeTotpSecret(adminUid, adminTotpSecret,
                            List.of("11111111", "22222222", "33333333"));
                    totpFirestoreService.enable2FA(adminUid);
                    log.info("Seeded the admin's TOTP secret and enabled 2FA");
                } else {
                    log.info("The admin's TOTP secret is already there, leaving it alone");
                }
            } catch (Exception e) {
                log.error("Could not seed the admin's TOTP secret: {}", e.getMessage(), e);
            }
        } else {
            log.warn("No e2e.admin.totp-secret configured: the 2FA scenarios will not be able to sign in");
        }

        seedInstagramProfile();
    }

    /**
     * The influencer signs in through a simulated Instagram OAuth, and the endpoint behind it reads the
     * profile out of Firestore rather than calling Meta. Against the real project that document is already
     * there; against a fresh emulator it is not.
     *
     * <p>No access_token: it is stored encrypted, the reader drops it when decryption fails, and the
     * scenarios that exercise the token do not run on this path.
     */
    private void seedInstagramProfile() {
        if (isBlank(influencerUid)) {
            return;
        }
        try {
            var doc = firestore.collection("instagramUsers").document(influencerUid);
            if (doc.get().get().exists()) {
                log.info("Instagram profile for {} is already there, leaving it alone", influencerUid);
                return;
            }
            doc.set(java.util.Map.of(
                    "user_id", "17841400000000001",
                    "username", "styleguru",
                    "followers_count", 12500,
                    "profile_picture_url", "https://example.test/styleguru.jpg",
                    "firebaseUid", influencerUid
            )).get();
            log.info("Seeded the Instagram profile for {}", influencerUid);
        } catch (Exception e) {
            log.error("Could not seed the Instagram profile for {}: {}", influencerUid, e.getMessage(), e);
        }
    }

    private void upsert(String uid, String email, String label) {
        if (isBlank(uid) || isBlank(email)) {
            log.warn("{} actor has no uid or email configured, skipped", label);
            return;
        }
        try {
            UserRecord existing = firebaseAuth.getUser(uid);
            // The password is the one thing the suite depends on and the one thing getUser cannot report,
            // so it is set every time rather than trusted.
            firebaseAuth.updateUser(new UserRecord.UpdateRequest(uid)
                    .setEmail(email)
                    .setEmailVerified(true)
                    .setPassword(password));
            log.info("{} actor {} already existed ({}), password reset", label, uid, existing.getEmail());
        } catch (FirebaseAuthException notFound) {
            try {
                firebaseAuth.createUser(new UserRecord.CreateRequest()
                        .setUid(uid)
                        .setEmail(email)
                        .setEmailVerified(true)
                        .setPassword(password));
                log.info("{} actor {} created in the emulator", label, uid);
            } catch (FirebaseAuthException e) {
                log.error("Could not create the {} actor {}: {}", label, uid, e.getMessage(), e);
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
