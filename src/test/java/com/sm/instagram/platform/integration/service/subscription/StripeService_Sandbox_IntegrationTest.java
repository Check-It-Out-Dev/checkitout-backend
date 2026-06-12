package com.sm.instagram.platform.integration.service.subscription;

import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.SubscriptionCreateParams;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for StripeService using REAL Stripe sandbox.
 *
 * <p>These tests hit the actual Stripe test-mode API (sk_test_...) loaded from .env.
 * They create real objects in the Stripe sandbox and clean up after themselves.
 *
 * <p>No browser needed — all operations are API-direct using test card tokens.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StripeService_Sandbox_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    private static String testCustomerId;
    private static String testSubscriptionId;

    @Nested
    @DisplayName("createCustomer — real Stripe sandbox")
    class CreateCustomer {

        @Test
        @Order(1)
        @DisplayName("should create customer in Stripe sandbox with email and metadata")
        void shouldCreateCustomer() throws StripeException {
            var customer = stripeService.createCustomer(
                    "integration-test@checkitout.pl",
                    "Integration Test Company",
                    Map.of("userId", "12345", "environment", "integration-test")
            );

            assertThat(customer).isNotNull();
            assertThat(customer.getId()).startsWith("cus_");
            assertThat(customer.getEmail()).isEqualTo("integration-test@checkitout.pl");
            assertThat(customer.getName()).isEqualTo("Integration Test Company");
            assertThat(customer.getMetadata()).containsEntry("userId", "12345");

            testCustomerId = customer.getId();
        }
    }

    @Nested
    @DisplayName("createCheckoutSession — real Stripe sandbox")
    class CreateCheckoutSession {

        @Test
        @DisplayName("should create checkout session without existing customer (subscription mode auto-creates)")
        void shouldCreateSessionWithoutCustomer() throws StripeException {
            // In subscription mode, Stripe auto-creates customer — no customer_creation param needed
            var session = stripeService.createCheckoutSession(
                    null,
                    enterprisePlan.getStripePriceId(),
                    "https://checkitout.pl/success",
                    "https://checkitout.pl/cancel",
                    Map.of("planName", "ENTERPRISE")
            );

            assertThat(session).isNotNull();
            assertThat(session.getId()).startsWith("cs_test_");
            assertThat(session.getUrl()).isNotBlank();
            assertThat(session.getUrl()).contains("checkout.stripe.com");
            assertThat(session.getMode()).isEqualTo("subscription");
        }

        @Test
        @DisplayName("should create checkout session with existing customer")
        void shouldCreateSessionWithCustomer() throws StripeException {
            var customer = stripeService.createCustomer(
                    "session-test@checkitout.pl",
                    "Session Test Co",
                    Map.of()
            );

            var session = stripeService.createCheckoutSession(
                    customer.getId(),
                    businessPlan.getStripePriceId(),
                    "https://checkitout.pl/success",
                    "https://checkitout.pl/cancel",
                    Map.of("planName", "BUSINESS")
            );

            assertThat(session).isNotNull();
            assertThat(session.getId()).startsWith("cs_test_");
            assertThat(session.getCustomer()).isEqualTo(customer.getId());
        }

        @Test
        @DisplayName("should throw for invalid price ID")
        void shouldThrowForInvalidPriceId() {
            assertThatThrownBy(() -> stripeService.createCheckoutSession(
                    null,
                    "price_invalid_garbage",
                    "https://checkitout.pl/success",
                    "https://checkitout.pl/cancel",
                    Map.of()
            )).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("Full subscription lifecycle — real Stripe sandbox")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SubscriptionLifecycle {

        @Test
        @Order(1)
        @DisplayName("should create customer → attach payment method → create subscription")
        void shouldCreateFullSubscription() throws StripeException {
            // 1. Create customer
            var customer = stripeService.createCustomer(
                    "lifecycle-test@checkitout.pl",
                    "Lifecycle Test Co",
                    Map.of("testRun", "subscription-lifecycle")
            );
            testCustomerId = customer.getId();

            // 2. Create payment method with test card tok_visa
            var pmParams = PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.CARD)
                    .putExtraParam("card[token]", "tok_visa")
                    .build();
            var pm = PaymentMethod.create(pmParams);
            assertThat(pm.getId()).startsWith("pm_");

            // 3. Attach payment method to customer
            pm.attach(PaymentMethodAttachParams.builder()
                    .setCustomer(customer.getId())
                    .build());

            // 4. Set as default payment method
            customer.update(CustomerUpdateParams.builder()
                    .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                            .setDefaultPaymentMethod(pm.getId())
                            .build())
                    .build());

            // 5. Create subscription directly (no checkout session needed!)
            var subscription = Subscription.create(
                    SubscriptionCreateParams.builder()
                            .setCustomer(customer.getId())
                            .addItem(SubscriptionCreateParams.Item.builder()
                                    .setPrice(businessPlan.getStripePriceId())
                                    .build())
                            .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                            .build()
            );

            assertThat(subscription.getId()).startsWith("sub_");
            assertThat(subscription.getStatus()).isEqualTo("active");
            testSubscriptionId = subscription.getId();
        }

        @Test
        @Order(2)
        @DisplayName("should cancel subscription at period end")
        void shouldCancelAtPeriodEnd() throws StripeException {
            Assumptions.assumeTrue(testSubscriptionId != null, "Requires subscription from previous test");

            stripeService.cancelSubscriptionAtPeriodEnd(testSubscriptionId);

            var sub = Subscription.retrieve(testSubscriptionId);
            assertThat(sub.getCancelAtPeriodEnd()).isTrue();
            assertThat(sub.getStatus()).isEqualTo("active"); // still active until period end
        }

        @Test
        @Order(3)
        @DisplayName("should cancel subscription immediately")
        void shouldCancelImmediately() throws StripeException {
            Assumptions.assumeTrue(testSubscriptionId != null, "Requires subscription from previous test");

            stripeService.cancelSubscriptionImmediately(testSubscriptionId);

            var sub = Subscription.retrieve(testSubscriptionId);
            assertThat(sub.getStatus()).isEqualTo("canceled");
        }
    }

    @Nested
    @DisplayName("createPortalSession — real Stripe sandbox")
    class PortalSession {

        @Test
        @DisplayName("should create billing portal session")
        void shouldCreatePortalSession() throws StripeException {
            var customer = stripeService.createCustomer(
                    "portal-test@checkitout.pl",
                    "Portal Test Co",
                    Map.of()
            );

            var session = stripeService.createPortalSession(
                    customer.getId(),
                    "https://checkitout.pl/settings"
            );

            assertThat(session).isNotNull();
            assertThat(session.getUrl()).isNotBlank();
            assertThat(session.getUrl()).contains("billing.stripe.com");
        }

        @Test
        @DisplayName("should throw for invalid customer ID")
        void shouldThrowForInvalidCustomer() {
            assertThatThrownBy(() -> stripeService.createPortalSession(
                    "cus_invalid_garbage",
                    "https://checkitout.pl/settings"
            )).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("getPriceIdForPlan — with real config")
    class GetPriceId {

        @Test
        @DisplayName("should return real Stripe price IDs from config")
        void shouldReturnRealPriceIds() {
            assertThat(stripeService.getPriceIdForPlan("BUSINESS")).startsWith("price_");
            assertThat(stripeService.getPriceIdForPlan("ENTERPRISE")).startsWith("price_");
        }

        @Test
        @DisplayName("should throw for FREE plan (no Stripe price)")
        void shouldThrowForFree() {
            assertThatThrownBy(() -> stripeService.getPriceIdForPlan("FREE"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Error handling — invalid IDs")
    class ErrorHandling {

        @Test
        @DisplayName("cancelSubscriptionImmediately with invalid ID should throw")
        void cancelInvalidId() {
            assertThatThrownBy(() -> stripeService.cancelSubscriptionImmediately("sub_invalid"))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("cancelSubscriptionAtPeriodEnd with invalid ID should throw")
        void cancelAtPeriodEndInvalidId() {
            assertThatThrownBy(() -> stripeService.cancelSubscriptionAtPeriodEnd("sub_invalid"))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("cancelSchedule with invalid ID should throw")
        void cancelScheduleInvalidId() {
            assertThatThrownBy(() -> stripeService.cancelSchedule("sub_sched_invalid"))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }
}
