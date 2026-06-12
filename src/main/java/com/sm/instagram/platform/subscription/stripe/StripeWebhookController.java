package com.sm.instagram.platform.subscription.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
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
        } catch (Exception e) {
            log.error("Stripe webhook handling failed: type={}, id={}", event.getType(), event.getId(), e);
            // Return 200 anyway — Stripe retries on non-2xx, and we don't want infinite retries
            // on business logic errors. The error is logged for investigation.
        }

        return ResponseEntity.ok("Received");
    }
}
