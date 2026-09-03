package com.sm.instagram.platform.storage.service;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link StorageUrlValidator} (pentest 3.1 — attachment source
 * substitution). Confirms only URLs inside this app's own storage bucket are
 * accepted and every off-bucket / off-host URL is rejected.
 */
class StorageUrlValidatorUnitTest {

    private static final String BUCKET = "check-it-out-47c50.firebasestorage.app";
    private StorageUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StorageUrlValidator(BUCKET);
    }

    @Test
    void acceptsFirebaseDownloadUrlForOwnBucket() {
        String url = "https://firebasestorage.googleapis.com/v0/b/" + BUCKET
                + "/o/content%2Fuser1%2F123_photo.png?alt=media";
        assertTrue(validator.isOwnBucketUrl(url));
        assertDoesNotThrow(() -> validator.requireOwnBucketUrl(url));
    }

    @Test
    void acceptsCanonicalGcsUrlForOwnBucket() {
        String url = "https://storage.googleapis.com/" + BUCKET + "/content/user1/photo.png";
        assertTrue(validator.isOwnBucketUrl(url));
    }

    @Test
    void rejectsAttackerControlledHost() {
        // The exact URL the pentest substituted (report Listing 3.1).
        String url = "https://attacker-controlled-server/evil-controlled";
        assertFalse(validator.isOwnBucketUrl(url));
        assertThrows(ValidationTranslatableException.class,
                () -> validator.requireOwnBucketUrl(url));
    }

    @Test
    void rejectsFirebaseHostButDifferentBucket() {
        // Correct Google host, someone else's bucket — must not pass.
        String url = "https://firebasestorage.googleapis.com/v0/b/attacker-bucket.firebasestorage.app/o/x";
        assertFalse(validator.isOwnBucketUrl(url));
    }

    @Test
    void rejectsHttpScheme() {
        String url = "http://firebasestorage.googleapis.com/v0/b/" + BUCKET + "/o/x";
        assertFalse(validator.isOwnBucketUrl(url));
    }

    @Test
    void rejectsGoogleAvatarHost() {
        // A social-login avatar host — legitimate elsewhere, but NOT a bucket
        // file URL. Confirms the guard is strict (why it is not applied to
        // profilePicture, which may legitimately hold such URLs).
        assertFalse(validator.isOwnBucketUrl("https://lh3.googleusercontent.com/a/default-user"));
    }

    @Test
    void rejectsNullBlankAndMalformed() {
        assertFalse(validator.isOwnBucketUrl(null));
        assertFalse(validator.isOwnBucketUrl("   "));
        assertFalse(validator.isOwnBucketUrl("not a url"));
        assertFalse(validator.isOwnBucketUrl("javascript:alert(1)"));
        assertThrows(ValidationTranslatableException.class,
                () -> validator.requireOwnBucketUrl(null));
    }

    @Test
    void rejectsBucketNameAsSubdomainTrick() {
        // Host that merely contains the bucket name must not pass.
        String url = "https://firebasestorage.googleapis.com.attacker.com/v0/b/" + BUCKET + "/o/x";
        assertFalse(validator.isOwnBucketUrl(url));
    }
}
