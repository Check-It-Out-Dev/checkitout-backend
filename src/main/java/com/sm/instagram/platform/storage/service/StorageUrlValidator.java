package com.sm.instagram.platform.storage.service;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Validates that a file URL points at THIS application's own Google Cloud /
 * Firebase Storage bucket — the only place the signed-upload pipeline mints
 * URLs (see {@link SignedUrlService}).
 *
 * <p>Security (pentest 3.1): the support-attachment endpoints persist a
 * client-supplied {@code fileUrl} that is later served to other users as a
 * download link. Without this guard an attacker can substitute a URL on a
 * host they control, turning a trusted ticket thread into a malware /
 * phishing delivery channel. The server never fetches the URL, so the risk
 * is stored-URL substitution rather than classic SSRF; pinning the host to
 * the app's own bucket closes it. Legitimate uploads already carry the
 * bucket URL returned by {@code /upload/signed-url}, so this is non-breaking.
 *
 * <p><b>Status: no production callers.</b> The follow-up owner directive of
 * 2026-06-13 replaced the client-supplied {@code fileUrl} with an uploadId-only
 * contract ({@code TicketAttachmentDtoIn}), which is strictly stronger: the
 * client can no longer name a URL at all, so there is nothing left to pin. This
 * class is kept — with its tests — for the day an endpoint has to accept a URL
 * again. Do not read its presence as the live control; the live control is the
 * ownership-checked tracking row in {@code SignedUrlService#resolveOwnedUpload}.
 */
@Slf4j
@Component
public class StorageUrlValidator {

    private static final String FIREBASE_HOST = "firebasestorage.googleapis.com";
    private static final String GCS_HOST = "storage.googleapis.com";

    private final String bucketName;

    public StorageUrlValidator(
            @Value("${gcp.bucket-name:check-it-out-47c50.firebasestorage.app}") String bucketName) {
        this.bucketName = bucketName;
    }

    /**
     * @return true when {@code url} is an https URL pointing at a file inside
     *         this app's own storage bucket, on a Google-owned storage host.
     */
    public boolean isOwnBucketUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        final URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        final String host = uri.getHost().toLowerCase();
        final String path = uri.getRawPath();
        if (path == null) {
            return false;
        }
        // Firebase download URL: https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{path}
        if (FIREBASE_HOST.equals(host)) {
            return path.startsWith("/v0/b/" + bucketName + "/o/");
        }
        // Canonical GCS URL: https://storage.googleapis.com/{bucket}/{path}
        if (GCS_HOST.equals(host)) {
            return path.startsWith("/" + bucketName + "/");
        }
        return false;
    }

    /**
     * @throws ValidationTranslatableException when {@code url} is not an
     *         own-bucket URL (pentest 3.1 — reject attacker-host substitution).
     */
    public void requireOwnBucketUrl(String url) {
        if (!isOwnBucketUrl(url)) {
            // Do NOT log the URL itself — it is attacker-controlled input.
            log.warn("SECURITY: Rejected untrusted file URL (host is not the application storage bucket)");
            throw new ValidationTranslatableException("error.attachment.untrusted_url");
        }
    }
}
