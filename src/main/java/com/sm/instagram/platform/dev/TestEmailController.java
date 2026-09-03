package com.sm.instagram.platform.dev;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.sm.instagram.platform.notification.email.EmailCronJob;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Test-only inbox controller for the dev / e2e profiles.
 *
 * <p>Exposes the messages GreenMail has captured to FE Playwright
 * integration tests so they can drive email-gated flows end-to-end
 * (step-up email-code, password-reset, verification-link). Tests poll
 * {@link #latest} after triggering an action that emits an email, then
 * regex out the verification code from the body.
 *
 * <p><b>SECURITY:</b> Bean is guarded by
 * {@code @Profile("(e2e | dev) & !prod & !test")}. In production the
 * controller is not registered; its endpoints return 404. The endpoint
 * surface is intentionally simple — it leaks captured email bodies and
 * recipients, which is fine for ephemeral test inboxes but never for
 * real user data.
 *
 * <p>Coordinated with the GreenMail bean wiring in
 * {@link GreenMailConfig}. If GreenMail isn't running (e.g., profile
 * mismatch), the bean isn't created and this controller is also absent.
 */
@Slf4j
@RestController
@Profile("(e2e | dev) & !prod & !test")
@RequestMapping("/test/email")
@RequiredArgsConstructor
public class TestEmailController {

    private final GreenMail greenMail;
    private final EmailCronJob emailCronJob;

    /**
     * Manually flush the BE's pending-notification-emails queue.
     *
     * <p>Production code dispatches notification emails via a 15-minute cron
     * (see {@link EmailCronJob#processEmailQueue}). Tests can't wait that
     * long, so this endpoint synchronously invokes the same code path.
     * After it returns, any pending notifications have been pushed to
     * GreenMail and {@link #latest} reflects them.
     *
     * <p>Idempotent: per-notification dedup (email_sent=true) is enforced
     * inside the cron logic — calling this twice doesn't send the same
     * email twice.
     */
    @PostMapping("/flush")
    public ResponseEntity<Map<String, Object>> flushPending() {
        try {
            emailCronJob.processEmailQueue();
            return ResponseEntity.ok(Map.of("flushed", true));
        } catch (Exception e) {
            log.warn("[dev/e2e] flushPending failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List every email currently in the GreenMail inbox, newest first.
     * Optionally filter by recipient via {@code ?to=foo@bar.test}.
     */
    @GetMapping
    public ResponseEntity<List<CapturedEmail>> list(
            @RequestParam(value = "to", required = false) String recipientFilter) {

        MimeMessage[] received = greenMail.getReceivedMessages();
        Stream<CapturedEmail> stream = Arrays.stream(received).map(TestEmailController::toCaptured);
        if (recipientFilter != null && !recipientFilter.isBlank()) {
            String filter = recipientFilter.toLowerCase();
            stream = stream.filter(e -> e.to().stream().anyMatch(addr -> addr.toLowerCase().contains(filter)));
        }
        List<CapturedEmail> body = stream
                .sorted(Comparator.comparing(CapturedEmail::receivedAtMillis).reversed())
                .toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Latest captured email (optionally for a specific recipient).
     * Returns 404 if the inbox is empty / no match.
     */
    @GetMapping("/latest")
    public ResponseEntity<CapturedEmail> latest(
            @RequestParam(value = "to", required = false) String recipientFilter) {

        ResponseEntity<List<CapturedEmail>> all = list(recipientFilter);
        return Optional.ofNullable(all.getBody())
                .filter(list -> !list.isEmpty())
                .map(list -> ResponseEntity.ok(list.get(0)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Drop the inbox. Tests should call this in {@code beforeEach} so a
     * stray email from a prior scenario can't surface as a false-positive
     * code match in the current scenario.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clear() {
        int before = greenMail.getReceivedMessages().length;
        try {
            greenMail.purgeEmailFromAllMailboxes();
        } catch (Exception e) {
            log.warn("[dev/e2e] GreenMail purge failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "purgedCount", 0));
        }
        return ResponseEntity.ok(Map.of("purgedCount", before));
    }

    private static CapturedEmail toCaptured(MimeMessage msg) {
        try {
            List<String> from = addressesAsStrings(msg.getFrom());
            List<String> to = addressesAsStrings(msg.getRecipients(Message.RecipientType.TO));
            String subject = Optional.ofNullable(msg.getSubject()).orElse("");
            String body = GreenMailUtil.getBody(msg);
            long receivedMillis = Optional.ofNullable(msg.getReceivedDate())
                    .map(d -> d.getTime())
                    .orElseGet(() -> Optional.ofNullable(unchecked(msg::getSentDate))
                            .map(d -> d.getTime())
                            .orElseGet(System::currentTimeMillis));
            return new CapturedEmail(
                    subject,
                    from,
                    to,
                    body,
                    Instant.ofEpochMilli(receivedMillis).toString(),
                    receivedMillis
            );
        } catch (MessagingException e) {
            log.warn("[dev/e2e] Failed to read MimeMessage: {}", e.getMessage());
            return new CapturedEmail("<unreadable>", List.of(), List.of(), "", Instant.now().toString(), System.currentTimeMillis());
        }
    }

    private static List<String> addressesAsStrings(Address[] addresses) {
        if (addresses == null) return List.of();
        return Arrays.stream(addresses).map(Address::toString).toList();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws MessagingException;
    }

    private static <T> T unchecked(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (MessagingException e) {
            return null;
        }
    }

    /**
     * Captured email envelope shape. {@code body} is the raw text body
     * (or the first text part of a multipart message). Tests typically
     * regex it for verification codes — keep simple, keep predictable.
     */
    public record CapturedEmail(
            String subject,
            List<String> from,
            List<String> to,
            String body,
            String receivedAt,
            long receivedAtMillis
    ) {}
}
