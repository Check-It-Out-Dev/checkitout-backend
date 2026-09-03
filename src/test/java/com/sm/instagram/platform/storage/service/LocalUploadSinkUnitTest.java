package com.sm.instagram.platform.storage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LocalUploadSink} — the dev-lite byte transport.
 * Tokens must behave like signed URLs (single-use, expiring) and every path
 * must stay confined to the sink directory.
 */
class LocalUploadSinkUnitTest {

    @TempDir
    Path tempDir;

    private LocalUploadSink sink() {
        return new LocalUploadSink(tempDir.toString());
    }

    @Test
    void prepareStoreServeRoundTrip() {
        LocalUploadSink sink = sink();
        String filePath = "content/user-1/1700000000_pic.jpg";

        String token = sink.prepareUpload(filePath, "image/jpeg", 5);
        assertThat(sink.uploadUrlFor(token)).isEqualTo("/api/dev-lite/upload/" + token);
        assertThat(sink.publicUrl(filePath)).isEqualTo("/api/dev-lite/files/" + filePath);
        assertThat(sink.exists(filePath)).isFalse();

        LocalUploadSink.Pending pending = sink.consume(token);
        assertThat(pending).isNotNull();
        sink.store(pending, "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));

        assertThat(sink.exists(filePath)).isTrue();
        assertThat(sink.size(filePath)).isEqualTo(15L);
        assertThat(sink.read(filePath)).asString(StandardCharsets.UTF_8).isEqualTo("fake-jpeg-bytes");
        assertThat(sink.contentTypeOf(filePath)).isEqualTo("image/jpeg");
    }

    @Test
    void tokensAreSingleUse() {
        LocalUploadSink sink = sink();
        String token = sink.prepareUpload("content/u/1_a.png", "image/png", 5);

        assertThat(sink.consume(token)).isNotNull();
        assertThat(sink.consume(token)).isNull();
    }

    @Test
    void unknownAndExpiredTokensAreRejected() {
        LocalUploadSink sink = sink();
        assertThat(sink.consume("no-such-token")).isNull();

        String expired = sink.prepareUpload("content/u/2_b.png", "image/png", -1);
        assertThat(sink.consume(expired)).isNull();
    }

    @Test
    void pathsEscapingTheSinkDirAreRejected() {
        LocalUploadSink sink = sink();
        assertThatThrownBy(() -> sink.prepareUpload("../../outside.txt", "image/png", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sink.exists("content/../../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentTypeMustMatchThePreparedUpload() {
        LocalUploadSink sink = sink();
        LocalUploadSink.Pending pending =
                sink.consume(sink.prepareUpload("content/u/3_c.jpg", "image/jpeg", 5));

        // A real signed PUT binds the content type into the V4 signature.
        assertThat(sink.contentTypeMatches(pending, "image/jpeg")).isTrue();
        assertThat(sink.contentTypeMatches(pending, "IMAGE/JPEG")).isTrue();
        assertThat(sink.contentTypeMatches(pending, "image/jpeg; charset=binary")).isTrue();

        assertThat(sink.contentTypeMatches(pending, "text/html")).isFalse();
        assertThat(sink.contentTypeMatches(pending, "application/octet-stream")).isFalse();
        assertThat(sink.contentTypeMatches(pending, "")).isFalse();
        assertThat(sink.contentTypeMatches(pending, null)).isFalse();
    }

    @Test
    void absentFilesReadAsNull() {
        LocalUploadSink sink = sink();
        assertThat(sink.size("content/u/none.png")).isNull();
        assertThat(sink.read("content/u/none.png")).isNull();
        assertThat(sink.exists("content/u/none.png")).isFalse();
    }
}
