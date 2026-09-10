package com.sm.instagram.platform.storage.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Service for proxying Instagram profile pictures to Firebase Storage.
 * Instagram CDN URLs expire after ~24-48 hours, so we download and store permanently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfilePictureProxyService {

    private final Storage storage;

    @Value("${gcp.bucket-name:check-it-out-47c50.firebasestorage.app}")
    private String bucketName;

    private static final Set<String> ALLOWED_CDN_HOSTS = Set.of(
            "cdninstagram.com", "scontent.cdninstagram.com",
            "instagram.com", "fbcdn.net", "facebook.com"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Download profile picture from Instagram CDN and upload to Firebase Storage.
     * Returns permanent Firebase Storage URL.
     *
     * @param instagramCdnUrl Original Instagram CDN URL
     * @param firebaseUid User's Firebase UID (used for path)
     * @return Firebase Storage URL, or null if failed
     */
    public String proxyToFirebaseStorage(String instagramCdnUrl, String firebaseUid) {
        if (instagramCdnUrl == null || instagramCdnUrl.isBlank()) {
            log.debug("No Instagram CDN URL provided, skipping proxy");
            return null;
        }

        if (!isAllowedUrl(instagramCdnUrl)) {
            log.warn("Rejected profile picture URL with disallowed scheme or host: {}",
                    instagramCdnUrl.substring(0, Math.min(instagramCdnUrl.length(), 60)));
            return null;
        }

        try {
            // 1. Download from Instagram CDN
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(instagramCdnUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.warn("Failed to download profile picture from Instagram CDN. Status: {}",
                        response.statusCode());
                return null;
            }

            byte[] imageBytes = response.body().readAllBytes();

            if (imageBytes.length == 0) {
                log.warn("Downloaded empty image from Instagram CDN");
                return null;
            }

            // 2. Determine content type
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("image/jpeg");

            // 3. Create Firebase Storage path
            String extension = contentType.contains("png") ? "png" : "jpg";
            String storagePath = String.format("profile-pictures/%s/%d.%s",
                    firebaseUid, System.currentTimeMillis(), extension);

            // 4. Upload to Firebase Storage
            BlobId blobId = BlobId.of(bucketName, storagePath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .setCacheControl("public, max-age=31536000") // 1 year cache
                    .build();

            storage.create(blobInfo, imageBytes);

            // 5. Return public URL
            String publicUrl = String.format(
                    "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media",
                    bucketName,
                    storagePath.replace("/", "%2F")
            );

            log.info("GDPR: Profile picture proxied to Firebase Storage: {} -> {}",
                    firebaseUid, storagePath);

            return publicUrl;

        } catch (Exception e) {
            // A broad catch takes InterruptedException with it, and the interrupt flag goes
            // too: a pool thread told to stop would carry on as if nothing had happened.
            // Restore it, then handle the failure exactly as before.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to proxy profile picture to Firebase Storage: {}", e.getMessage());
            return null; // Graceful fallback - continue with original URL or null
        }
    }

    /**
     * Check if a URL is an Instagram CDN URL that may expire.
     *
     * @param url The URL to check
     * @return true if it's an Instagram CDN URL
     */
    public boolean isInstagramCdnUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.contains("cdninstagram.com") ||
               url.contains("instagram.") ||
               url.contains("fbcdn.net");
    }

    /**
     * Check if a URL is a permanent Firebase Storage URL (not expiring Instagram CDN).
     * Used to determine if user has manually uploaded a photo that should NOT be overwritten
     * by Instagram sync.
     *
     * @param url The URL to check
     * @return true if it's a permanent Firebase Storage URL
     */
    public boolean isPermanentFirebaseStorageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.contains("firebasestorage.googleapis.com") ||
               url.contains(".appspot.com") ||
               url.contains("storage.googleapis.com");
    }

    /**
     * Check if user already has a saved profile picture that should not be overwritten.
     * A photo should NOT be overwritten if it's a permanent Firebase Storage URL
     * (either proxied from Instagram initially or manually uploaded by user).
     *
     * @param currentProfilePictureUrl The user's current profile picture URL
     * @return true if user has a permanent photo that should be preserved
     */
    public boolean hasPreservableProfilePicture(String currentProfilePictureUrl) {
        // User has a preservable photo if:
        // 1. They have a profile picture URL set
        // 2. It's stored in Firebase Storage (permanent, not expiring Instagram CDN)
        return currentProfilePictureUrl != null &&
               !currentProfilePictureUrl.isBlank() &&
               isPermanentFirebaseStorageUrl(currentProfilePictureUrl);
    }

    /**
     * SSRF protection: validate that the URL uses HTTPS and points to an allowed CDN host.
     */
    private boolean isAllowedUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            return ALLOWED_CDN_HOSTS.stream().anyMatch(allowed ->
                    host.equals(allowed) || host.endsWith("." + allowed));
        } catch (Exception e) {
            return false;
        }
    }
}
