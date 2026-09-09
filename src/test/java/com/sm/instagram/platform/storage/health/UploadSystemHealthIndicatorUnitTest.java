package com.sm.instagram.platform.storage.health;

import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.storage.service.LocalUploadSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The upload-system indicator reports the sink that actually serves uploads. Under dev-lite that is the
 * {@link LocalUploadSink}; the GCS bean, when present, carries synthetic offline credentials and must not
 * decide the application's health. The first sandbox deploy went DOWN on exactly that (2026-09-09).
 */
class UploadSystemHealthIndicatorUnitTest {

    @TempDir
    Path tempDir;

    /** An unstubbed mock returns null from get(bucket): "configured but not accessible". */
    private Storage unreachableStorage() {
        return mock(Storage.class);
    }

    @Test
    void localSinkModeIsUpEvenWhenTheCloudStorageBeanCannotReachItsBucket() {
        UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(
                unreachableStorage(), null, null, new LocalUploadSink(tempDir.toString()));
        ReflectionTestUtils.setField(indicator, "bucketName", "some-bucket");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("local_sink", "UP")
                .containsKey("local_sink_dir")
                .doesNotContainKey("firebase_storage");
    }

    @Test
    void cloudModeStillGoesDownWhenTheBucketIsNotAccessible() {
        UploadSystemHealthIndicator indicator =
                new UploadSystemHealthIndicator(unreachableStorage(), null, null, null);
        ReflectionTestUtils.setField(indicator, "bucketName", "some-bucket");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("firebase_storage", "DOWN");
    }

    @Test
    void localSinkModeGoesDownWhenTheSinkIsNotAWritableDirectory() throws Exception {
        Path file = tempDir.resolve("not-a-directory");
        Files.writeString(file, "x");
        LocalUploadSink sink = new LocalUploadSink(tempDir.toString());
        ReflectionTestUtils.setField(sink, "baseDir", file);

        Health health = new UploadSystemHealthIndicator(null, null, null, sink).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("local_sink", "DOWN");
    }
}
