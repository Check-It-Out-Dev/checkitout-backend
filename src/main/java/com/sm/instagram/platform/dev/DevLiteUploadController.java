package com.sm.instagram.platform.dev;

import com.sm.instagram.platform.storage.service.LocalUploadSink;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * dev-lite byte transport for {@link LocalUploadSink}: the PUT endpoint plays
 * the role of the GCS signed URL (the single-use token IS the authorization,
 * mirroring signed-URL semantics — hence permitAll in WebSecurityConfiguration),
 * and the GET endpoint serves stored files back as the "public" URL.
 *
 * <p><b>SECURITY:</b> Registered only under the {@code dev-lite} simulator
 * profile — in every other profile these endpoints return 404. Paths are
 * confined to the sink directory by {@link LocalUploadSink}.
 */
@Slf4j
@RestController
// e2e as well as dev-lite: it is the byte transport for LocalUploadSink, and the sink is pointless
// without it. The pair is gated together so neither can be active on its own.
@Profile("(dev-lite | e2e) & !prod & !test")
@RequestMapping("/dev-lite")
@RequiredArgsConstructor
public class DevLiteUploadController {

    private final LocalUploadSink sink;

    /** The local stand-in for the signed PUT to storage.googleapis.com. */
    @PutMapping(value = "/upload/{token}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> upload(
            @PathVariable String token,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody byte[] body) {
        LocalUploadSink.Pending pending = sink.consume(token);
        if (pending == null) {
            // Unknown, expired or reused token — same outcome as an invalid
            // signature on a real signed URL.
            return ResponseEntity.status(403).build();
        }
        if (!sink.contentTypeMatches(pending, contentType)) {
            // GCS rejects this too: the content type is part of the signature.
            log.warn("dev-lite: upload rejected — content type does not match the prepared upload");
            return ResponseEntity.status(403).build();
        }
        if (body.length > LocalUploadSink.MAX_UPLOAD_BYTES) {
            log.warn("dev-lite: upload rejected — {} bytes exceeds the {} byte ceiling",
                    body.length, LocalUploadSink.MAX_UPLOAD_BYTES);
            return ResponseEntity.status(413).build();
        }
        sink.store(pending, body);
        log.info("dev-lite: stored upload {} ({} bytes)", pending.filePath(), body.length);
        return ResponseEntity.ok().build();
    }

    /**
     * Deterministic placeholder image for the seeded demo world (changeset
     * 007 repoints picsum.photos URLs here). Same seed → same picture, so
     * screenshots and visual checks stay stable, and nothing leaves the box.
     */
    @GetMapping(value = "/placeholder/{seed}", produces = "image/svg+xml")
    public ResponseEntity<String> placeholder(@PathVariable String seed) {
        // Two-tone gradient with a soft highlight — reads as "image not
        // supplied", never as broken. No lettering: a seed like "user-401"
        // has no meaningful initials to show.
        int hash = seed.hashCode();
        int hue = Math.floorMod(hash, 360);
        int hue2 = (hue + 35) % 360;
        int cx = 220 + Math.floorMod(hash / 360, 360);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600" width="800" height="600">
                  <defs>
                    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="hsl(%d 45%% 78%%)"/>
                      <stop offset="100%%" stop-color="hsl(%d 38%% 62%%)"/>
                    </linearGradient>
                    <radialGradient id="h" cx="50%%" cy="45%%" r="55%%">
                      <stop offset="0%%" stop-color="rgba(255,255,255,0.45)"/>
                      <stop offset="100%%" stop-color="rgba(255,255,255,0)"/>
                    </radialGradient>
                  </defs>
                  <rect width="800" height="600" fill="url(#g)"/>
                  <circle cx="%d" cy="250" r="300" fill="url(#h)"/>
                </svg>
                """.formatted(hue, hue2, cx);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=86400")
                .body(svg);
    }

    /** Serves a stored file — the target of the minted public URL. */
    @GetMapping("/files/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request) {
        // Everything after "/files/" is the storage path (contains slashes,
        // so a path-variable cannot capture it).
        String uri = request.getRequestURI();
        String marker = "/files/";
        String filePath = UriUtils.decode(
                uri.substring(uri.indexOf(marker) + marker.length()), StandardCharsets.UTF_8);
        byte[] bytes = sink.read(filePath);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Content-Type", sink.contentTypeOf(filePath))
                .header("Cache-Control", "public, max-age=3600")
                .body(bytes);
    }
}
