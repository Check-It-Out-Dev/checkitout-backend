package com.sm.instagram.platform.subscription.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@Slf4j
// Excluded from the published OpenAPI contract: Stripe billing webhook.
@Hidden
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payments.enabled", havingValue = "true", matchIfMissing = false)
public class StripeWebhookController {

    private final StripeProperties stripeProperties;
    private final StripeWebhookHandler webhookHandler;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    stripeProperties.getWebhookSecret()
            );
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Stripe webhook payload parsing failed", e);
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        log.info("Stripe webhook received: type={}, id={}", event.getType(), event.getId());

        try {
            webhookHandler.handle(event);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Transient concurrency conflict. The handler (resilience Layer 3) re-throws this BY
            // DESIGN so Stripe retries the delivery; idempotency Layers 1 & 2 make the retry safe.
            // Returning 200 here would silently drop the losing event with no retry and no cron net
            // for non-invoice events (subscription.updated/deleted, payment_failed) — so return 503.
            log.warn("Optimistic lock conflict on Stripe event: type={}, id={} — returning 503 so Stripe retries",
                    event.getType(), event.getId(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Retry");
        } catch (Exception e) {
            log.error("Stripe webhook handling failed: type={}, id={}", event.getType(), event.getId(), e);
            // Non-transient business error — return 200 to avoid infinite Stripe retries. Logged for investigation.
        }

        return ResponseEntity.ok("Received");
    }
}
