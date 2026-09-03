package com.sm.instagram.platform.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * dev-lite replacement for the GCS signed-URL transport: uploads land in a
 * local directory instead of the bucket, and files are served straight back
 * by {@code DevLiteUploadController}. Everything AROUND the transport —
 * validation, rate limits, storage quota, the {@code file_uploads} tracking
 * row, the uploadId-only persistence contract — is the production code path
 * unchanged; only the byte transport is simulated.
 *
 * <p>Mirrors signed-URL semantics: {@link #prepareUpload} mints a random,
 * single-use, expiring token (the local analogue of the V4 signature), and
 * the PUT endpoint accepts exactly one body for it.
 */
@Slf4j
@Component
@Profile("dev-lite & !prod & !test")
public class LocalUploadSink {

    /**
     * Same ceiling {@code SignedUrlService} applies to the DECLARED size at
     * mint time, re-applied here to the actual bytes. GCS would not reject an
     * oversize PUT (the V4 signature binds no length) — the simulator is
     * deliberately stricter, because a lying client should not be able to fill
     * a stranger's disk. Quota accounting stays honest either way: confirmUpload
     * reads the real stored size.
     */
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

    /** A prepared upload waiting for its PUT — local analogue of a signed URL. */
    public record Pending(String filePath, String contentType, Instant expiresAt) {}

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Path baseDir;

    public LocalUploadSink(
            @Value("${file-upload.local-sink.dir:./dev-lite-storage}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("dev-lite: cannot create local upload dir " + this.baseDir, e);
        }
        log.info("dev-lite: local upload sink at {}", this.baseDir);
    }

    /** Mints the single-use upload token for a BE-generated file path. */
    public String prepareUpload(String filePath, String contentType, int expirationMinutes) {
        safeResolve(filePath); // fail fast on a hostile path before minting
        String token = UUID.randomUUID().toString();
        pending.put(token, new Pending(filePath, contentType,
                Instant.now().plusSeconds(expirationMinutes * 60L)));
        return token;
    }

    /** Relative URL the FE PUTs the bytes to (context-path /api included). */
    public String uploadUrlFor(String token) {
        return "/api/dev-lite/upload/" + token;
    }

    /** Relative URL the file is later served from — the stored "public" URL. */
    public String publicUrl(String filePath) {
        return "/api/dev-lite/files/" + filePath;
    }

    /**
     * Consumes the token (single-use) and returns its pending record.
     *
     * @return the pending upload, or {@code null} when unknown or expired.
     */
    public Pending consume(String token) {
        Pending p = pending.remove(token);
        if (p == null || Instant.now().isAfter(p.expiresAt())) {
            return null;
        }
        return p;
    }

    /**
     * A real signed PUT carries the Content-Type inside the V4 signature, so
     * sending different bytes than you asked for fails at the storage edge with
     * 403. The token alone cannot express that, so check it here — otherwise
     * dev-lite would silently accept an upload production rejects.
     *
     * @param sent the request's Content-Type header, may be null or carry a
     *             charset suffix.
     */
    public boolean contentTypeMatches(Pending upload, String sent) {
        if (sent == null || sent.isBlank()) {
            return false;
        }
        return mediaType(sent).equals(mediaType(upload.contentType()));
    }

    private static String mediaType(String header) {
        int semicolon = header.indexOf(';');
        return (semicolon < 0 ? header : header.substring(0, semicolon)).trim().toLowerCase();
    }

    /** Stores the uploaded bytes under the pending record's file path. */
    public void store(Pending upload, byte[] body) {
        Path target = safeResolve(upload.filePath());
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, body);
        } catch (IOException e) {
            throw new UncheckedIOException("dev-lite: cannot store upload " + upload.filePath(), e);
        }
    }

    public boolean exists(String filePath) {
        return Files.isRegularFile(safeResolve(filePath));
    }

    /** @return file size in bytes, or {@code null} when the file is absent. */
    public Long size(String filePath) {
        Path p = safeResolve(filePath);
        try {
            return Files.isRegularFile(p) ? Files.size(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** @return the stored bytes, or {@code null} when the file is absent. */
    public byte[] read(String filePath) {
        Path p = safeResolve(filePath);
        try {
            return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("dev-lite: cannot read " + filePath, e);
        }
    }

    /** Extension-based content type for serving; images are what we store. */
    public String contentTypeOf(String filePath) {
        String guessed = URLConnection.guessContentTypeFromName(filePath);
        return guessed != null ? guessed : "application/octet-stream";
    }

    /** Normalizes and confines every path to the sink directory. */
    private Path safeResolve(String filePath) {
        Path resolved = baseDir.resolve(filePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("dev-lite: path escapes the sink dir");
        }
        return resolved;
    }
}
