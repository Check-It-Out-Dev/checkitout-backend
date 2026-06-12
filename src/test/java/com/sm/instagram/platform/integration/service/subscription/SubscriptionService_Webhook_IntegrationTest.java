package com.sm.instagram.platform.integration.service.subscription;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.param.*;
import com.sm.instagram.platform.subscription.entity.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SubscriptionService webhook handlers.
 * Uses real PostgreSQL (TestContainers) and real Stripe sandbox.
 *
 * <p>4 of 5 handlers are pure DB operations — no Stripe calls.
 * handleCheckoutCompleted calls stripeService.retrieveSubscription() so it needs
 * a real Stripe subscription created in the sandbox.
 */
class SubscriptionService_Webhook_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    // ========================================================================
    // handleCheckoutCompleted — REAL STRIPE SANDBOX
    // ========================================================================

    @Nested
    @DisplayName("handleCheckoutCompleted — real Stripe sandbox + real DB")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class HandleCheckoutCompleted {

        private static String sandboxCustomerId;
        private static String sandboxSubscriptionId;

        @Test
        @Order(1)
        @DisplayName("should activate BUSINESS plan from real Stripe subscription")
        void shouldActivateBusinessFromRealStripe() throws StripeException {
            authenticateAs(testCompany);

            // 1. Create real Stripe Customer + Subscription
            var customer = stripeService.createCustomer(
                    "checkout-int-test@checkitout.pl", "Checkout Integration Test", Map.of());
            sandboxCustomerId = customer.getId();

            var pm = PaymentMethod.create(PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.CARD)
                    .putExtraParam("card[token]", "tok_visa")
                    .build());
            pm.attach(PaymentMethodAttachParams.builder().setCustomer(customer.getId()).build());
            Customer.retrieve(customer.getId()).update(CustomerUpdateParams.builder()
                    .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                            .setDefaultPaymentMethod(pm.getId()).build())
                    .build());

            var stripeSub = Subscription.create(SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(businessPlan.getStripePriceId()).build())
                    .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                    .build());
            sandboxSubscriptionId = stripeSub.getId();

            // 2. Create DB subscription with stripeCustomerId
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);
            sub.setStripeCustomerId(customer.getId());
            companySubscriptionRepo.save(sub);

            createBillingPeriodForUser(testCompany, freePlan,
                    LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(15),
                    BillingPeriodStatus.ACTIVE);

            // 3. Call handler with real Stripe subscription ID
            subscriptionService.handleCheckoutCompleted("evt_int_checkout_1", customer.getId(), stripeSub.getId());

            // 4. Verify DB state
            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("BUSINESS");
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("FREE");
            assertThat(updated.getStripeSubscriptionId()).isEqualTo(stripeSub.getId());

            // Old period expired
            var periods = billingPeriodRepo.findAll();
            assertThat(periods).anyMatch(p -> p.getStatus() == BillingPeriodStatus.EXPIRED);
            assertThat(periods).anyMatch(p ->
                    p.getStatus() == BillingPeriodStatus.ACTIVE && p.getPlan().getName().equals("BUSINESS"));

            // Event logged
            var events = subscriptionEventRepo.findAll();
            assertThat(events).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.SUBSCRIPTION_CREATED
                            && "evt_int_checkout_1".equals(e.getStripeEventId())
                            && "FREE".equals(e.getPlanFrom())
                            && "BUSINESS".equals(e.getPlanTo()));
        }

        @Test
        @Order(2)
        @DisplayName("should throw EntityNotFoundException for unknown customer")
        void shouldThrowForUnknownCustomer() {
            assertThatThrownBy(() ->
                    subscriptionService.handleCheckoutCompleted("evt_int_checkout_2", "cus_nonexistent", "sub_test"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @AfterAll
        static void cleanupStripe() throws StripeException {
            if (sandboxSubscriptionId != null) {
                try { Subscription.retrieve(sandboxSubscriptionId).cancel(); } catch (Exception ignored) {}
            }
        }
    }

    // ========================================================================
    // handleInvoicePaid — DB only, no Stripe
    // ========================================================================

    @Nested
    @DisplayName("handleInvoicePaid — real DB")
    class HandleInvoicePaid {

        @Test
        @DisplayName("should extend billing period and create invoice record")
        void shouldExtendPeriodAndCreateInvoice() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeSubscriptionId("sub_inv_test_1");
            companySubscriptionRepo.save(sub);

            var endDate = LocalDateTime.now().plusDays(15);
            createBillingPeriodForUser(testCompany, businessPlan,
                    LocalDateTime.now().minusDays(15), endDate, BillingPeriodStatus.ACTIVE);

            subscriptionService.handleInvoicePaid("evt_inv_1", "sub_inv_test_1", null, 2900L, "pln");

            // Billing period extended by 1 month
            var period = billingPeriodRepo.findActiveByUserId(testCompany.getId()).orElseThrow();
            assertThat(period.getEndDate()).isAfter(endDate);

            // Invoice record created
            var invoices = invoiceRecordRepo.findByUserIdOrderByCreatedTimeDesc(testCompany.getId());
            assertThat(invoices).hasSize(1);
            assertThat(invoices.get(0).getAmountPln()).isEqualByComparingTo(new BigDecimal("29"));
            assertThat(invoices.get(0).getStatus()).isEqualTo(InvoiceStatus.PENDING);
            assertThat(invoices.get(0).getBillingPeriod()).isNotNull();

            // Event logged
            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.SUBSCRIPTION_RENEWED
                            && "evt_inv_1".equals(e.getStripeEventId()));
        }

        @Test
        @DisplayName("should create invoice even without active billing period")
        void shouldCreateInvoiceWithoutPeriod() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeSubscriptionId("sub_inv_test_2");
            companySubscriptionRepo.save(sub);
            // No billing period created

            subscriptionService.handleInvoicePaid("evt_inv_2", "sub_inv_test_2", null, 9900L, "pln");

            var invoices = invoiceRecordRepo.findByUserIdOrderByCreatedTimeDesc(testCompany.getId());
            assertThat(invoices).hasSize(1);
            assertThat(invoices.get(0).getAmountPln()).isEqualByComparingTo(new BigDecimal("99"));
            assertThat(invoices.get(0).getBillingPeriod()).isNull();
        }

        @Test
        @DisplayName("should silently ignore unknown subscription ID")
        void shouldIgnoreUnknownSubscription() {
            long invoicesBefore = invoiceRecordRepo.count();
            long eventsBefore = subscriptionEventRepo.count();

            subscriptionService.handleInvoicePaid("evt_inv_3", "sub_unknown_xyz", null, 2900L, "pln");

            assertThat(invoiceRecordRepo.count()).isEqualTo(invoicesBefore);
            assertThat(subscriptionEventRepo.count()).isEqualTo(eventsBefore);
        }
    }

    // ========================================================================
    // handlePaymentFailed — DB only
    // ========================================================================

    @Nested
    @DisplayName("handlePaymentFailed — real DB")
    class HandlePaymentFailed {

        @Test
        @DisplayName("should transition BUSINESS_ACTIVE to PAYMENT_FAILED")
        void shouldTransitionToPaymentFailed() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeSubscriptionId("sub_pf_test_1");
            companySubscriptionRepo.save(sub);

            subscriptionService.handlePaymentFailed("evt_pf_1", "sub_pf_test_1");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("BUSINESS");
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("BUSINESS");

            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.PAYMENT_FAILED
                            && "evt_pf_1".equals(e.getStripeEventId()));
        }

        @Test
        @DisplayName("should transition ENTERPRISE_ACTIVE to PAYMENT_FAILED")
        void shouldTransitionEnterpriseToPaymentFailed() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.ENTERPRISE_ACTIVE);
            sub.setStripeSubscriptionId("sub_pf_test_2");
            companySubscriptionRepo.save(sub);

            subscriptionService.handlePaymentFailed("evt_pf_2", "sub_pf_test_2");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("ENTERPRISE");
        }

        @Test
        @DisplayName("should silently ignore unknown subscription ID")
        void shouldIgnoreUnknown() {
            long eventsBefore = subscriptionEventRepo.count();

            subscriptionService.handlePaymentFailed("evt_pf_3", "sub_unknown");

            assertThat(subscriptionEventRepo.count()).isEqualTo(eventsBefore);
        }
    }

    // ========================================================================
    // handleSubscriptionDeleted — DB only
    // ========================================================================

    @Nested
    @DisplayName("handleSubscriptionDeleted — real DB")
    class HandleSubscriptionDeleted {

        @Test
        @DisplayName("should downgrade BUSINESS to FREE and clear Stripe IDs")
        void shouldDowngradeBusinessToFree() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeSubscriptionId("sub_del_test_1");
            sub.setStripeScheduleId("sched_123");
            sub.setTargetPlan(freePlan);
            companySubscriptionRepo.save(sub);

            createBillingPeriodForUser(testCompany, businessPlan,
                    LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(15),
                    BillingPeriodStatus.ACTIVE);

            subscriptionService.handleSubscriptionDeleted("evt_del_1", "sub_del_test_1");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("FREE");
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("BUSINESS");
            assertThat(updated.getStripeSubscriptionId()).isNull();
            assertThat(updated.getStripeScheduleId()).isNull();
            assertThat(updated.getTargetPlan()).isNull();

            // Old period expired, new FREE period created
            var periods = billingPeriodRepo.findAll();
            assertThat(periods).anyMatch(p -> p.getStatus() == BillingPeriodStatus.EXPIRED);
            assertThat(periods).anyMatch(p ->
                    p.getStatus() == BillingPeriodStatus.ACTIVE && p.getPlan().getName().equals("FREE"));

            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.PAYMENT_EXHAUSTED
                            && "BUSINESS".equals(e.getPlanFrom()) && "FREE".equals(e.getPlanTo()));
        }

        @Test
        @DisplayName("should downgrade ENTERPRISE to FREE")
        void shouldDowngradeEnterpriseToFree() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.ENTERPRISE_ACTIVE);
            sub.setStripeSubscriptionId("sub_del_test_2");
            companySubscriptionRepo.save(sub);

            subscriptionService.handleSubscriptionDeleted("evt_del_2", "sub_del_test_2");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("FREE");
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("ENTERPRISE");
        }

        @Test
        @DisplayName("should downgrade from PAYMENT_FAILED to FREE")
        void shouldDowngradeFromPaymentFailed() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.PAYMENT_FAILED);
            sub.setStripeSubscriptionId("sub_del_test_3");
            sub.setPreviousPlan(businessPlan);
            companySubscriptionRepo.save(sub);

            subscriptionService.handleSubscriptionDeleted("evt_del_3", "sub_del_test_3");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
        }

        @Test
        @DisplayName("should silently ignore unknown subscription ID")
        void shouldIgnoreUnknown() {
            long eventsBefore = subscriptionEventRepo.count();

            subscriptionService.handleSubscriptionDeleted("evt_del_4", "sub_unknown");

            assertThat(subscriptionEventRepo.count()).isEqualTo(eventsBefore);
        }
    }

    // ========================================================================
    // handleSubscriptionUpdated — DB only (reads config, no Stripe API)
    // ========================================================================

    @Nested
    @DisplayName("handleSubscriptionUpdated — real DB")
    class HandleSubscriptionUpdated {

        @Test
        @DisplayName("should downgrade ENTERPRISE to BUSINESS when schedule applied")
        void shouldDowngradeEnterpriseToBusiness() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.DOWNGRADE_PENDING);
            sub.setStripeSubscriptionId("sub_upd_test_1");
            sub.setStripeScheduleId("sched_456");
            sub.setTargetPlan(businessPlan);
            companySubscriptionRepo.save(sub);

            createBillingPeriodForUser(testCompany, enterprisePlan,
                    LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(15),
                    BillingPeriodStatus.ACTIVE);

            subscriptionService.handleSubscriptionUpdated("evt_upd_1", "sub_upd_test_1",
                    businessPlan.getStripePriceId());

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("BUSINESS");
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("ENTERPRISE");
            assertThat(updated.getStripeScheduleId()).isNull();
            assertThat(updated.getTargetPlan()).isNull();

            // Old period expired, new BUSINESS period created
            var periods = billingPeriodRepo.findAll();
            assertThat(periods).anyMatch(p -> p.getStatus() == BillingPeriodStatus.EXPIRED);
            assertThat(periods).anyMatch(p ->
                    p.getStatus() == BillingPeriodStatus.ACTIVE && p.getPlan().getName().equals("BUSINESS"));

            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.SUBSCRIPTION_DOWNGRADED
                            && "ENTERPRISE".equals(e.getPlanFrom()) && "BUSINESS".equals(e.getPlanTo()));
        }

        @Test
        @DisplayName("should upgrade BUSINESS to ENTERPRISE")
        void shouldUpgradeBusinessToEnterprise() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeSubscriptionId("sub_upd_test_2");
            companySubscriptionRepo.save(sub);

            subscriptionService.handleSubscriptionUpdated("evt_upd_2", "sub_upd_test_2",
                    enterprisePlan.getStripePriceId());

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ENTERPRISE_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("ENTERPRISE");
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("BUSINESS");
        }

        @Test
        @DisplayName("should silently skip unknown price ID (graceful degradation)")
        void shouldSkipUnknownPriceId() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.ENTERPRISE_ACTIVE);
            sub.setStripeSubscriptionId("sub_upd_test_3");
            companySubscriptionRepo.save(sub);

            subscriptionService.handleSubscriptionUpdated("evt_upd_3", "sub_upd_test_3", "price_unknown_garbage");

            // No change to subscription
            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ENTERPRISE_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("ENTERPRISE");
        }

        @Test
        @DisplayName("should silently ignore unknown subscription ID")
        void shouldIgnoreUnknown() {
            long eventsBefore = subscriptionEventRepo.count();

            subscriptionService.handleSubscriptionUpdated("evt_upd_4", "sub_unknown", "price_biz");

            assertThat(subscriptionEventRepo.count()).isEqualTo(eventsBefore);
        }
    }

    // ========================================================================
    // Webhook endpoint — signed payload POST
    // ========================================================================

    @Nested
    @DisplayName("Webhook endpoint — signed payload POST")
    class WebhookEndpoint {

        @org.springframework.beans.factory.annotation.Autowired
        private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

        @org.springframework.beans.factory.annotation.Value("${stripe.webhook-secret}")
        private String webhookSecret;

        @Test
        @DisplayName("should return 200 for valid signed webhook payload")
        void shouldAcceptValidSignature() throws Exception {
            String payload = "{\"id\":\"evt_endpoint_1\",\"object\":\"event\",\"type\":\"charge.succeeded\",\"api_version\":\"2024-12-18.acacia\",\"created\":" + java.time.Instant.now().getEpochSecond() + ",\"data\":{\"object\":{\"id\":\"ch_test\",\"object\":\"charge\"}}}";

            var sigHeader = computeStripeSignature(payload, webhookSecret);

            var headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Stripe-Signature", sigHeader);

            var response = restTemplate.postForEntity(
                    "/webhooks/stripe",
                    new org.springframework.http.HttpEntity<>(payload, headers),
                    String.class);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("Received");
        }

        @Test
        @DisplayName("should return 400 for invalid signature")
        void shouldRejectInvalidSignature() {
            String payload = """
                    {"id":"evt_bad","object":"event","type":"invoice.paid","data":{"object":{"id":"inv_1","object":"invoice"}}}
                    """;

            var headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Stripe-Signature", "t=1234567890,v1=invalid_garbage");

            var response = restTemplate.postForEntity(
                    "/webhooks/stripe",
                    new org.springframework.http.HttpEntity<>(payload, headers),
                    String.class);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        private String computeStripeSignature(String payload, String secret) throws Exception {
            long timestamp = java.time.Instant.now().getEpochSecond();
            String payloadToSign = timestamp + "." + payload;
            String signature = com.stripe.net.Webhook.Util.computeHmacSha256(secret, payloadToSign);
            return "t=" + timestamp + ",v1=" + signature;
        }
    }
}
