package com.sm.instagram.platform.storage.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.storage.entity.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SignedUrlService#resolveOwnedUpload} — the BE-minted
 * URL round trip (pentest 3.1 + owner directive 2026-06-13: a client never
 * supplies a persistable file URL; the server derives it from its own
 * PostgreSQL tracking after verifying ownership + that the blob landed in our
 * bucket).
 */
@ExtendWith(MockitoExtension.class)
class SignedUrlServiceResolveUploadUnitTest {

    private static final String BUCKET = "check-it-out-47c50.firebasestorage.app";
    private static final String OWNER = "firebase-owner-uid";

    @Mock private Storage storage;
    @Mock private StorageRateLimitService rateLimiter;
    @Mock private FileTrackingService trackingService;
    @Mock private Blob blob;

    private SignedUrlService service;

    @BeforeEach
    void setUp() {
        // metricsService + credentialsProvider are optional (null in prod when
        // absent); resolveOwnedUpload doesn't touch them.
        service = new SignedUrlService(storage, BUCKET, rateLimiter, trackingService, null, null, null);
    }

    private FileUpload trackedUpload(String owner) {
        FileUpload u = new FileUpload();
        u.setId("upload-1");
        u.setUserId(owner);
        u.setFilePath("content/" + owner + "/1699999999_pic.jpg");
        u.setFilename("pic.jpg");
        u.setContentType("image/jpeg");
        u.setFileSize(2048L);
        return u;
    }

    @Test
    void resolvesOwnedUploadToBucketDerivedValues() {
        when(trackingService.getUpload("upload-1")).thenReturn(Optional.of(trackedUpload(OWNER)));
        when(storage.get(BUCKET, "content/" + OWNER + "/1699999999_pic.jpg")).thenReturn(blob);
        when(blob.exists()).thenReturn(true);

        SignedUrlService.ResolvedUpload resolved = service.resolveOwnedUpload(OWNER, "upload-1");

        assertThat(resolved.filename()).isEqualTo("pic.jpg");
        assertThat(resolved.contentType()).isEqualTo("image/jpeg");
        assertThat(resolved.fileSize()).isEqualTo(2048L);
        // URL is derived from OUR bucket + the tracked path — never client input.
        assertThat(resolved.publicUrl())
                .startsWith("https://firebasestorage.googleapis.com/v0/b/" + BUCKET + "/o/")
                .contains("content%2F");
    }

    @Test
    void rejectsUnknownUploadId() {
        when(trackingService.getUpload("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveOwnedUpload(OWNER, "ghost"))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasMessage("error.attachment.unknown_upload");
    }

    @Test
    void rejectsUploadOwnedByAnotherUser() {
        // Ownership check — an attacker cannot attach someone else's file.
        when(trackingService.getUpload("upload-1")).thenReturn(Optional.of(trackedUpload("someone-else")));

        assertThatThrownBy(() -> service.resolveOwnedUpload(OWNER, "upload-1"))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasMessage("error.attachment.unknown_upload");
    }

    @Test
    void unknownAndForeignOwnerAreIndistinguishable() {
        // ANTI-ENUMERATION: a foreign-owned id and a nonexistent id must fail
        // with the SAME message key, so an attacker cannot probe which
        // uploadIds exist by diffing the error.
        when(trackingService.getUpload("ghost")).thenReturn(Optional.empty());
        when(trackingService.getUpload("foreign")).thenReturn(Optional.of(trackedUpload("someone-else")));

        String unknownKey = catchThrowableOfType(
                () -> service.resolveOwnedUpload(OWNER, "ghost"),
                ValidationTranslatableException.class).getMessage();
        String foreignKey = catchThrowableOfType(
                () -> service.resolveOwnedUpload(OWNER, "foreign"),
                ValidationTranslatableException.class).getMessage();

        assertThat(unknownKey).isEqualTo(foreignKey);
    }

    @Test
    void rejectsWhenBlobIsNotInBucket() {
        // Tracked but the client never actually PUT the blob.
        when(trackingService.getUpload("upload-1")).thenReturn(Optional.of(trackedUpload(OWNER)));
        when(storage.get(BUCKET, "content/" + OWNER + "/1699999999_pic.jpg")).thenReturn(null);

        assertThatThrownBy(() -> service.resolveOwnedUpload(OWNER, "upload-1"))
                .isInstanceOf(ValidationTranslatableException.class);
    }

    @Test
    void rejectsBlankCaller() {
        assertThatThrownBy(() -> service.resolveOwnedUpload("  ", "upload-1"))
                .isInstanceOf(ValidationTranslatableException.class);
    }
}
