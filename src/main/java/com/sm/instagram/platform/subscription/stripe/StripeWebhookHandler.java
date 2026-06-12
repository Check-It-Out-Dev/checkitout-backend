package com.sm.instagram.platform.subscription.stripe;

import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.repository.SubscriptionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Routes Stripe webhook events to SubscriptionService methods.
 *
 * <p>Resilience layers (defense in depth):
 * <ol>
 *   <li><b>Fast-path check</b>: {@code existsByStripeEventId} — avoids unnecessary work</li>
 *   <li><b>DB unique constraint</b>: {@code subscription_event_stripe_unique} — the real idempotency guard.
 *       If concurrent threads pass layer 1 simultaneously, only one INSERT wins; others get
 *       {@link DataIntegrityViolationException} which we catch and treat as "already processed".</li>
 *   <li><b>Optimistic locking</b>: {@code @Version} on CompanySubscription — prevents lost updates
 *       if two threads try to mutate the same subscription row. Loser gets
 *       {@link ObjectOptimisticLockingFailureException} which Stripe will retry via webhook retry.</li>
 * </ol>
 *
 * <p>Only handles {@code invoice.paid} (not {@code invoice.payment_succeeded}) because Stripe sends
 * BOTH for the same payment. Handling only one prevents duplicate invoice records.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeWebhookHandler {

    private final SubscriptionService subscriptionService;
    private final SubscriptionEventRepository subscriptionEventRepo;

    public void handle(Event event) {
        // Layer 1: Fast-path idempotency check (non-blocking, may have TOCTOU race)
        if (event.getId() != null && subscriptionEventRepo.existsByStripeEventId(event.getId())) {
            log.info("Stripe event already processed, skipping: id={}, type={}", event.getId(), event.getType());
            return;
        }

        try {
            route(event);
        } catch (DataIntegrityViolationException e) {
            // Layer 2: DB unique constraint caught — concurrent duplicate delivery, already processed
            log.info("Stripe event already processed (concurrent duplicate): id={}, type={}", event.getId(), event.getType());
        } catch (ObjectOptimisticLockingFailureException e) {
            // Layer 3: Optimistic lock conflict — another thread mutated the subscription.
            // Re-throw so controller returns non-2xx and Stripe retries later.
            log.warn("Optimistic lock conflict processing Stripe event: id={}, type={}. Stripe will retry.",
                    event.getId(), event.getType());
            throw e;
        }
    }

    private void route(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "invoice.paid" -> handleInvoicePaid(event);
            // invoice.payment_succeeded is intentionally NOT handled — Stripe sends both
            // invoice.paid AND invoice.payment_succeeded for the same payment. We only react
            // to invoice.paid to prevent duplicate invoice records.
            case "invoice.payment_succeeded" -> log.debug("Ignoring invoice.payment_succeeded (handled via invoice.paid): {}",
                    event.getId());
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            default -> log.info("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        var session = deserialize(event, Session.class);
        String customerId = session.getCustomer();
        String subscriptionId = session.getSubscription();

        log.info("Checkout session completed: sessionId={}, customerId={}, subscriptionId={}",
                session.getId(), customerId, subscriptionId);

        if (subscriptionId == null) {
            log.warn("Checkout session has no subscription — skipping (not a subscription checkout)");
            return;
        }

        subscriptionService.handleCheckoutCompleted(
                event.getId(),
                customerId,
                subscriptionId
        );
    }

    private void handleInvoicePaid(Event event) {
        var invoice = deserialize(event, Invoice.class);
        String subscriptionId = extractSubscriptionId(invoice);

        if (subscriptionId == null) {
            log.info("Non-subscription invoice paid: invoiceId={}", invoice.getId());
            return;
        }

        log.info("Invoice paid: invoiceId={}, subscriptionId={}, customerId={}, amount={} {}",
                invoice.getId(), subscriptionId, invoice.getCustomer(), invoice.getAmountPaid(), invoice.getCurrency());

        subscriptionService.handleInvoicePaid(
                event.getId(),
                subscriptionId,
                invoice.getCustomer(),
                invoice.getAmountPaid(),
                invoice.getCurrency()
        );
    }

    private void handleInvoicePaymentFailed(Event event) {
        var invoice = deserialize(event, Invoice.class);
        String subscriptionId = extractSubscriptionId(invoice);

        if (subscriptionId == null) {
            log.info("Non-subscription invoice payment failed: invoiceId={}", invoice.getId());
            return;
        }

        boolean willRetry = invoice.getNextPaymentAttempt() != null;
        log.warn("Invoice payment failed: invoiceId={}, subscriptionId={}, willRetry={}",
                invoice.getId(), subscriptionId, willRetry);

        subscriptionService.handlePaymentFailed(
                event.getId(),
                subscriptionId
        );
    }

    private void handleSubscriptionDeleted(Event event) {
        var subscription = deserialize(event, Subscription.class);

        log.info("Subscription deleted: subscriptionId={}, customerId={}",
                subscription.getId(), subscription.getCustomer());

        subscriptionService.handleSubscriptionDeleted(
                event.getId(),
                subscription.getId()
        );
    }

    private void handleSubscriptionUpdated(Event event) {
        var subscription = deserialize(event, Subscription.class);
        var previousAttributes = event.getData().getPreviousAttributes();

        boolean scheduleReleased = previousAttributes != null && previousAttributes.containsKey("schedule");
        boolean itemsChanged = previousAttributes != null && previousAttributes.containsKey("items");

        if (scheduleReleased || itemsChanged) {
            String currentPriceId = subscription.getItems().getData().get(0).getPrice().getId();

            log.info("Subscription plan changed: subscriptionId={}, newPriceId={}, scheduleReleased={}",
                    subscription.getId(), currentPriceId, scheduleReleased);

            subscriptionService.handleSubscriptionUpdated(
                    event.getId(),
                    subscription.getId(),
                    currentPriceId
            );
        } else {
            log.info("Subscription updated (no plan change): subscriptionId={}, status={}",
                    subscription.getId(), subscription.getStatus());
        }
    }

    /**
     * Extract subscription ID from Invoice.
     * Stripe SDK 31.x: Invoice.getSubscription() → Invoice.getParent().getSubscriptionDetails().getSubscription()
     */
    private String extractSubscriptionId(Invoice invoice) {
        var parent = invoice.getParent();
        if (parent != null && parent.getSubscriptionDetails() != null) {
            return parent.getSubscriptionDetails().getSubscription();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T deserialize(Event event, Class<T> clazz) {
        return (T) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to deserialize Stripe event " + event.getId() + " as " + clazz.getSimpleName()));
    }
}
