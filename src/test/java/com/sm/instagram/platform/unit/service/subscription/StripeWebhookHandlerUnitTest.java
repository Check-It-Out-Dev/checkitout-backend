package com.sm.instagram.platform.unit.service.subscription;

import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.repository.SubscriptionEventRepository;
import com.sm.instagram.platform.subscription.stripe.StripeWebhookHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookHandlerUnitTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private SubscriptionEventRepository subscriptionEventRepo;

    @InjectMocks private StripeWebhookHandler webhookHandler;

    // ========================================================================
    // Idempotency
    // ========================================================================

    @Nested
    @DisplayName("Idempotency check")
    class Idempotency {

        @Test
        @DisplayName("should skip already processed event")
        void shouldSkipDuplicate() {
            var event = mockEvent("evt_123", "invoice.paid");
            when(subscriptionEventRepo.existsByStripeEventId("evt_123")).thenReturn(true);

            webhookHandler.handle(event);

            verifyNoInteractions(subscriptionService);
        }

        @Test
        @DisplayName("should process event with null ID (bypass idempotency check)")
        void shouldProcessNullId() {
            // Use unrecognized event type so we only test the idempotency bypass, not handler logic
            var event = mockEvent(null, "unknown.event.type");

            webhookHandler.handle(event);

            // null ID: `event.getId() != null` is false, so idempotency check is skipped entirely
            verify(subscriptionEventRepo, never()).existsByStripeEventId(any());
            verifyNoInteractions(subscriptionService);
        }
    }

    // ========================================================================
    // checkout.session.completed
    // ========================================================================

    @Nested
    @DisplayName("checkout.session.completed")
    class CheckoutCompleted {

        @Test
        @DisplayName("should delegate to subscriptionService with correct params")
        void happyPath() {
            var event = mockEventWithSession("evt_cs_1", "cs_123", "cus_456", "sub_789");
            when(subscriptionEventRepo.existsByStripeEventId("evt_cs_1")).thenReturn(false);

            webhookHandler.handle(event);

            verify(subscriptionService).handleCheckoutCompleted("evt_cs_1", "cus_456", "sub_789");
        }

        @Test
        @DisplayName("should skip when subscriptionId is null (not a subscription checkout)")
        void shouldSkipNonSubscription() {
            var event = mockEventWithSession("evt_cs_2", "cs_123", "cus_456", null);
            when(subscriptionEventRepo.existsByStripeEventId("evt_cs_2")).thenReturn(false);

            webhookHandler.handle(event);

            verifyNoInteractions(subscriptionService);
        }
    }

    // ========================================================================
    // invoice.paid
    // ========================================================================

    @Nested
    @DisplayName("invoice.paid")
    class InvoicePaid {

        @Test
        @DisplayName("should delegate with subscription ID, amount, currency")
        void happyPath() {
            var event = mockEventWithInvoice("evt_ip_1", "invoice.paid", "sub_123", 9900L, "pln");
            when(subscriptionEventRepo.existsByStripeEventId("evt_ip_1")).thenReturn(false);

            webhookHandler.handle(event);

            verify(subscriptionService).handleInvoicePaid("evt_ip_1", "sub_123", "cus_test", 9900L, "pln");
        }

        @Test
        @DisplayName("should skip non-subscription invoice (subscriptionId null)")
        void shouldSkipNonSubscription() {
            var event = mockEventWithInvoice("evt_ip_2", "invoice.paid", null, 5000L, "pln");
            when(subscriptionEventRepo.existsByStripeEventId("evt_ip_2")).thenReturn(false);

            webhookHandler.handle(event);

            verifyNoInteractions(subscriptionService);
        }
    }

    // ========================================================================
    // invoice.payment_failed
    // ========================================================================

    @Nested
    @DisplayName("invoice.payment_failed")
    class PaymentFailed {

        @Test
        @DisplayName("should delegate to handlePaymentFailed")
        void happyPath() {
            var event = mockEventWithInvoice("evt_pf_1", "invoice.payment_failed", "sub_123", 0L, "pln");
            when(subscriptionEventRepo.existsByStripeEventId("evt_pf_1")).thenReturn(false);

            webhookHandler.handle(event);

            verify(subscriptionService).handlePaymentFailed("evt_pf_1", "sub_123");
        }

        @Test
        @DisplayName("should skip non-subscription payment failure")
        void shouldSkipNonSubscription() {
            var event = mockEventWithInvoice("evt_pf_2", "invoice.payment_failed", null, 0L, "pln");
            when(subscriptionEventRepo.existsByStripeEventId("evt_pf_2")).thenReturn(false);

            webhookHandler.handle(event);

            verifyNoInteractions(subscriptionService);
        }
    }

    // ========================================================================
    // customer.subscription.deleted
    // ========================================================================

    @Nested
    @DisplayName("customer.subscription.deleted")
    class SubscriptionDeleted {

        @Test
        @DisplayName("should delegate to handleSubscriptionDeleted")
        void happyPath() {
            var event = mockEventWithSubscription("evt_sd_1", "customer.subscription.deleted", "sub_123", "cus_456", null);
            when(subscriptionEventRepo.existsByStripeEventId("evt_sd_1")).thenReturn(false);

            webhookHandler.handle(event);

            verify(subscriptionService).handleSubscriptionDeleted("evt_sd_1", "sub_123");
        }
    }

    // ========================================================================
    // customer.subscription.updated
    // ========================================================================

    @Nested
    @DisplayName("customer.subscription.updated")
    class SubscriptionUpdated {

        @Test
        @DisplayName("should delegate when schedule released")
        void shouldDelegateOnScheduleRelease() {
            var event = mockEventWithSubscription("evt_su_1", "customer.subscription.updated",
                    "sub_123", "cus_456", "price_biz_123");
            mockPreviousAttributes(event, Map.of("schedule", "sub_sched_old"));
            when(subscriptionEventRepo.existsByStripeEventId("evt_su_1")).thenReturn(false);

            webhookHandler.handle(event);

            verify(subscriptionService).handleSubscriptionUpdated("evt_su_1", "sub_123", "price_biz_123");
        }

        @Test
        @DisplayName("should delegate when items changed")
        void shouldDelegateOnItemsChanged() {
            var event = mockEventWithSubscription("evt_su_2", "customer.subscription.updated",
                    "sub_123", "cus_456", "price_ent_456");
            mockPreviousAttributes(event, Map.of("items", Map.of()));
            when(subscriptionEventRepo.existsByStripeEventId("evt_su_2")).thenReturn(false);

            webhookHandler.handle(event);

            verify(subscriptionService).handleSubscriptionUpdated("evt_su_2", "sub_123", "price_ent_456");
        }

        @Test
        @DisplayName("should NOT delegate when no plan change (e.g., status-only update)")
        void shouldNotDelegateOnNonPlanChange() {
            var event = mockEventWithSubscription("evt_su_3", "customer.subscription.updated",
                    "sub_123", "cus_456", "price_biz_123");
            mockPreviousAttributes(event, Map.of("status", "active"));
            when(subscriptionEventRepo.existsByStripeEventId("evt_su_3")).thenReturn(false);

            webhookHandler.handle(event);

            verifyNoInteractions(subscriptionService);
        }

        @Test
        @DisplayName("should NOT delegate when previousAttributes is null")
        void shouldNotDelegateOnNullPreviousAttributes() {
            var event = mockEventWithSubscription("evt_su_4", "customer.subscription.updated",
                    "sub_123", "cus_456", "price_biz_123");
            mockPreviousAttributes(event, null);
            when(subscriptionEventRepo.existsByStripeEventId("evt_su_4")).thenReturn(false);

            webhookHandler.handle(event);

            verifyNoInteractions(subscriptionService);
        }
    }

    // ========================================================================
    // Unrecognized event type
    // ========================================================================

    @Test
    @DisplayName("should ignore unrecognized event type")
    void shouldIgnoreUnrecognizedEventType() {
        var event = mockEvent("evt_unk", "charge.succeeded");
        when(subscriptionEventRepo.existsByStripeEventId("evt_unk")).thenReturn(false);

        webhookHandler.handle(event);

        verifyNoInteractions(subscriptionService);
    }

    // ========================================================================
    // Mock helpers
    // ========================================================================

    private Event mockEvent(String eventId, String eventType) {
        var event = mock(Event.class, withSettings().lenient());
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn(eventType);
        return event;
    }

    private Event mockEventWithSession(String eventId, String sessionId, String customerId, String subscriptionId) {
        var event = mockEvent(eventId, "checkout.session.completed");
        var session = mock(Session.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getCustomer()).thenReturn(customerId);
        when(session.getSubscription()).thenReturn(subscriptionId);
        mockDeserializer(event, session);
        return event;
    }

    private Event mockEventWithInvoice(String eventId, String eventType, String subscriptionId, Long amountPaid, String currency) {
        var event = mockEvent(eventId, eventType);
        var invoice = mock(Invoice.class, withSettings().lenient());
        // Stripe SDK 31.x: subscription moved to invoice.getParent().getSubscriptionDetails().getSubscription()
        var parent = mock(Invoice.Parent.class, withSettings().lenient());
        var subDetails = mock(Invoice.Parent.SubscriptionDetails.class, withSettings().lenient());
        when(subDetails.getSubscription()).thenReturn(subscriptionId);
        when(parent.getSubscriptionDetails()).thenReturn(subscriptionId != null ? subDetails : null);
        when(invoice.getParent()).thenReturn(subscriptionId != null ? parent : null);
        when(invoice.getId()).thenReturn("inv_test_" + eventId);
        when(invoice.getCustomer()).thenReturn("cus_test");
        if (subscriptionId != null) {
            when(invoice.getAmountPaid()).thenReturn(amountPaid);
            when(invoice.getCurrency()).thenReturn(currency);
        }
        if (eventType.equals("invoice.payment_failed") && subscriptionId != null) {
            when(invoice.getAttemptCount()).thenReturn(1L);
            when(invoice.getNextPaymentAttempt()).thenReturn(null);
        }
        mockDeserializer(event, invoice);
        return event;
    }

    private Event mockEventWithSubscription(String eventId, String eventType,
                                             String subscriptionId, String customerId, String priceId) {
        var event = mockEvent(eventId, eventType);
        var subscription = mock(Subscription.class, withSettings().lenient());
        when(subscription.getId()).thenReturn(subscriptionId);
        when(subscription.getCustomer()).thenReturn(customerId);

        if (priceId != null) {
            var price = mock(Price.class, withSettings().lenient());
            when(price.getId()).thenReturn(priceId);
            var item = mock(SubscriptionItem.class, withSettings().lenient());
            when(item.getPrice()).thenReturn(price);
            var itemCollection = mock(SubscriptionItemCollection.class, withSettings().lenient());
            when(itemCollection.getData()).thenReturn(java.util.List.of(item));
            when(subscription.getItems()).thenReturn(itemCollection);
        }

        mockDeserializer(event, subscription);

        // Default: empty event data for previousAttributes
        var eventData = mock(Event.Data.class, withSettings().lenient());
        when(event.getData()).thenReturn(eventData);
        when(eventData.getPreviousAttributes()).thenReturn(null);

        return event;
    }

    private void mockDeserializer(Event event, StripeObject object) {
        var deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(object));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    }

    private void mockPreviousAttributes(Event event, Map<String, Object> attributes) {
        var eventData = event.getData();
        when(eventData.getPreviousAttributes()).thenReturn(attributes);
    }
}
