package com.sm.instagram.platform.subscription.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventCollection;
import com.stripe.param.EventListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * DEV-ONLY: Polls Stripe Event API and feeds events directly to StripeWebhookHandler.
 * Eliminates the need for `stripe listen` CLI during local development.
 *
 * <p>How it works:
 * <ol>
 *   <li>Every 10 seconds, fetches recent Stripe events (last 5 minutes)</li>
 *   <li>Filters to only the 5 event types we handle</li>
 *   <li>Skips events already processed (in-memory dedup + DB idempotency in handler)</li>
 *   <li>Calls StripeWebhookHandler.handle() directly — no HTTP, no signature verification</li>
 * </ol>
 *
 * <p>Active ONLY with profile "dev". Never runs in production, test, e2e, or integration.
 */
@Slf4j
@Component
@Profile("dev-poller")
@RequiredArgsConstructor
public class StripeDevEventPoller {

    private final StripeWebhookHandler webhookHandler;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "checkout.session.completed",
            "invoice.paid",
            "invoice.payment_failed",
            "customer.subscription.deleted",
            "customer.subscription.updated"
    );

    /** In-memory set of already-processed event IDs (resets on restart — DB idempotency is the real guard) */
    private final Set<String> processedEventIds = new HashSet<>();

    /** Timestamp of the first poll — only fetch events created after app startup */
    private final long startedAt = Instant.now().getEpochSecond();

    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void pollStripeEvents() {
        try {
            EventListParams params = EventListParams.builder()
                    .setCreated(EventListParams.Created.builder()
                            .setGte(startedAt)
                            .build())
                    .setLimit(25L)
                    .build();

            EventCollection events = Event.list(params);

            for (Event event : events.getData()) {
                if (!HANDLED_TYPES.contains(event.getType())) {
                    continue;
                }
                if (processedEventIds.contains(event.getId())) {
                    continue;
                }

                log.info("🔔 [DEV POLLER] Processing Stripe event: type={}, id={}", event.getType(), event.getId());
                try {
                    webhookHandler.handle(event);
                    processedEventIds.add(event.getId());
                    log.info("✅ [DEV POLLER] Event processed successfully: {}", event.getId());
                } catch (Exception e) {
                    log.error("❌ [DEV POLLER] Failed to process event {}: {}", event.getId(), e.getMessage(), e);
                    processedEventIds.add(event.getId()); // Don't retry — same as webhook behavior
                }
            }
        } catch (StripeException e) {
            log.debug("[DEV POLLER] Failed to poll Stripe events: {}", e.getMessage());
        }
    }
}
