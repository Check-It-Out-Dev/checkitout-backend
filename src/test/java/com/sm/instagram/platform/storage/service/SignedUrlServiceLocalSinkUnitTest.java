package com.sm.instagram.platform.storage.service;

import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.model.FileUploadRequest;
import com.sm.instagram.platform.storage.model.FileUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SignedUrlService} in dev-lite mode: with a
 * {@link LocalUploadSink} present and NO GCS {@code Storage} bean, the whole
 * signed-URL pipeline — mint, validate, confirm — must run against the local
 * sink while validation, rate limits and tracking stay on the shared path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignedUrlServiceLocalSinkUnitTest {

    private static final String USER = "firebase-uid-dev-lite";

    @TempDir
    Path tempDir;

    @Mock private StorageRateLimitService rateLimiter;
    @Mock private FileTrackingService trackingService;

    private LocalUploadSink sink;
    private SignedUrlService service;

    @BeforeEach
    void setUp() {
        sink = new LocalUploadSink(tempDir.toString());
        service = new SignedUrlService(null, "unused-bucket", rateLimiter, trackingService, null, null, sink);
        ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
        ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");

        when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(StorageRateLimitService.RateLimitResult.allowed(10, 50));
        when(rateLimiter.hasStorageSpace(anyString(), anyLong())).thenReturn(true);
        when(rateLimiter.getUserStatus(anyString())).thenReturn(
                new StorageRateLimitService.RateLimitStatus(1, 10, 1, 50, 1024L, 100 * 1024 * 1024L));

        FileUpload tracked = new FileUpload(USER, "path", "pic.jpg", "image/jpeg", 1024L);
        tracked.setId("upload-dev-lite-1");
        when(trackingService.recordUploadRequest(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(tracked);
    }

    private FileUploadRequest request() {
        FileUploadRequest r = new FileUploadRequest();
        r.setFilename("pic.jpg");
        r.setContentType("image/jpeg");
        r.setFileSize(1024L);
        return r;
    }

    @Test
    void mintsLocalUploadAndPublicUrlsWithoutGcs() {
        FileUploadResponse response = service.generateSignedUrl(USER, request());

        assertThat(response.getUploadUrl()).startsWith("/api/dev-lite/upload/");
        assertThat(response.getPublicUrl()).startsWith("/api/dev-lite/files/content/" + USER + "/");
        assertThat(response.getUploadId()).isEqualTo("upload-dev-lite-1");
    }

    @Test
    void fullLocalRoundTripValidatesAndConfirms() {
        FileUploadResponse response = service.generateSignedUrl(USER, request());
        String filePath = response.getFilePath();
        assertThat(service.validateUploadSuccess(filePath)).isFalse();

        // The FE-side PUT, played locally: consume the minted token + store bytes.
        String token = response.getUploadUrl().substring(response.getUploadUrl().lastIndexOf('/') + 1);
        LocalUploadSink.Pending pending = sink.consume(token);
        assertThat(pending).isNotNull();
        sink.store(pending, "0123456789".getBytes(StandardCharsets.UTF_8));

        assertThat(service.validateUploadSuccess(filePath)).isTrue();

        service.confirmUpload(USER, filePath);
        verify(rateLimiter).recordUpload(USER, 10L);
        verify(trackingService).confirmUploadViaApi(filePath, 10L);
    }
}
