package com.sm.instagram.platform.integration.service.subscription;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.param.*;
import com.sm.instagram.platform.subscription.entity.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for downgrade, cancel-downgrade, and payment recovery.
 * Real PostgreSQL (TestContainers) + Real Stripe sandbox.
 */
class SubscriptionService_Downgrade_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    // ========================================================================
    // DB-ONLY: Payment Recovery
    // ========================================================================

    @Nested
    @DisplayName("handleInvoicePaid — payment recovery (DB only)")
    class PaymentRecovery {

        @Test
        @DisplayName("should recover BUSINESS from PAYMENT_FAILED when Stripe retry succeeds")
        void shouldRecoverBusiness() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.PAYMENT_FAILED);
            sub.setPreviousPlan(businessPlan);
            sub.setStripeSubscriptionId("sub_recovery_biz");
            companySubscriptionRepo.save(sub);

            createBillingPeriodForUser(testCompany, businessPlan,
                    LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(15),
                    BillingPeriodStatus.ACTIVE);

            subscriptionService.handleInvoicePaid("evt_rec_1", "sub_recovery_biz", null, 2900L, "pln");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("BUSINESS");

            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.PAYMENT_RECOVERED);

            var invoices = invoiceRecordRepo.findByUserIdOrderByCreatedTimeDesc(testCompany.getId());
            assertThat(invoices).hasSize(1);
            assertThat(invoices.get(0).getStatus()).isEqualTo(InvoiceStatus.PENDING);
        }

        @Test
        @DisplayName("should recover ENTERPRISE from PAYMENT_FAILED")
        void shouldRecoverEnterprise() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.PAYMENT_FAILED);
            sub.setPreviousPlan(enterprisePlan);
            sub.setStripeSubscriptionId("sub_recovery_ent");
            companySubscriptionRepo.save(sub);

            subscriptionService.handleInvoicePaid("evt_rec_2", "sub_recovery_ent", null, 9900L, "pln");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ENTERPRISE_ACTIVE);
        }

        @Test
        @DisplayName("should NOT recover when previousPlan is null")
        void shouldNotRecoverWithoutPreviousPlan() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.PAYMENT_FAILED);
            sub.setPreviousPlan(null);
            sub.setStripeSubscriptionId("sub_recovery_null");
            companySubscriptionRepo.save(sub);

            subscriptionService.handleInvoicePaid("evt_rec_3", "sub_recovery_null", null, 2900L, "pln");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        }
    }

    // ========================================================================
    // DB-ONLY: Downgrade guards
    // ========================================================================

    @Nested
    @DisplayName("requestDowngrade — guards (DB only)")
    class DowngradeGuards {

        @Test
        @DisplayName("should reject downgrade from FREE_ACTIVE")
        void shouldRejectFromFree() {
            authenticateAs(testCompany);
            createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);

            assertThatThrownBy(() -> subscriptionService.requestDowngrade(testCompany.getId(), "FREE"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should allow trial cancellation (immediate downgrade to FREE)")
        void shouldAllowTrialCancellation() throws Exception {
            authenticateAs(testCompany);
            createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);

            subscriptionService.requestDowngrade(testCompany.getId(), "FREE");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(updated.getTrialUsed()).isTrue();
        }

        @Test
        @DisplayName("should reject cancelDowngrade when not DOWNGRADE_PENDING")
        void shouldRejectCancelWhenNotPending() {
            authenticateAs(testCompany);
            createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);

            assertThatThrownBy(() -> subscriptionService.cancelDowngrade(testCompany.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should reject cancelDowngrade when subscription not found")
        void shouldRejectCancelWhenNotFound() {
            assertThatThrownBy(() -> subscriptionService.cancelDowngrade(999999L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ========================================================================
    // REAL STRIPE: Downgrade to FREE (cancel_at_period_end)
    // ========================================================================

    @Nested
    @DisplayName("requestDowngrade to FREE — real Stripe sandbox")
    class DowngradeToFree {

        private String sandboxCustomerId;
        private String sandboxSubscriptionId;

        @Test
        @DisplayName("should set cancel_at_period_end on Stripe and DOWNGRADE_PENDING in DB")
        void shouldDowngradeToFree() throws StripeException {
            authenticateAs(testCompany);

            // Create real Stripe subscription
            sandboxCustomerId = createStripeCustomer();
            sandboxSubscriptionId = createStripeSubscription(sandboxCustomerId, businessPlan.getStripePriceId());

            // Wire up DB
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeCustomerId(sandboxCustomerId);
            sub.setStripeSubscriptionId(sandboxSubscriptionId);
            companySubscriptionRepo.save(sub);

            // Downgrade
            subscriptionService.requestDowngrade(testCompany.getId(), "FREE");

            // Verify DB
            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
            assertThat(updated.getTargetPlan().getName()).isEqualTo("FREE");
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("BUSINESS");

            // Verify Stripe
            var stripeSub = Subscription.retrieve(sandboxSubscriptionId);
            assertThat(stripeSub.getCancelAtPeriodEnd()).isTrue();
        }

        @Test
        @DisplayName("should cancel downgrade (reactivate) and restore BUSINESS")
        void shouldCancelDowngradeToFree() throws StripeException {
            authenticateAs(testCompany);

            sandboxCustomerId = createStripeCustomer();
            sandboxSubscriptionId = createStripeSubscription(sandboxCustomerId, businessPlan.getStripePriceId());

            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeCustomerId(sandboxCustomerId);
            sub.setStripeSubscriptionId(sandboxSubscriptionId);
            companySubscriptionRepo.save(sub);

            // Downgrade then cancel
            subscriptionService.requestDowngrade(testCompany.getId(), "FREE");
            subscriptionService.cancelDowngrade(testCompany.getId());

            // Verify DB restored
            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(updated.getTargetPlan()).isNull();

            // Verify Stripe reactivated
            var stripeSub = Subscription.retrieve(sandboxSubscriptionId);
            assertThat(stripeSub.getCancelAtPeriodEnd()).isFalse();
        }

        @AfterEach
        void cleanup() throws StripeException {
            if (sandboxSubscriptionId != null) {
                try { Subscription.retrieve(sandboxSubscriptionId).cancel(); } catch (Exception ignored) {}
            }
        }
    }

    // ========================================================================
    // REAL STRIPE: Downgrade Enterprise to Business (Stripe Schedule)
    // ========================================================================

    @Nested
    @DisplayName("requestDowngrade Enterprise→Business — real Stripe Schedule")
    class DowngradeEnterpriseToBusiness {

        private String sandboxCustomerId;
        private String sandboxSubscriptionId;
        private String sandboxScheduleId;

        @Test
        @DisplayName("should create Stripe Schedule and set DOWNGRADE_PENDING")
        void shouldCreateSchedule() throws StripeException {
            authenticateAs(testCompany);

            sandboxCustomerId = createStripeCustomer();
            sandboxSubscriptionId = createStripeSubscription(sandboxCustomerId, enterprisePlan.getStripePriceId());

            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.ENTERPRISE_ACTIVE);
            sub.setStripeCustomerId(sandboxCustomerId);
            sub.setStripeSubscriptionId(sandboxSubscriptionId);
            companySubscriptionRepo.save(sub);

            subscriptionService.requestDowngrade(testCompany.getId(), "BUSINESS");

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
            assertThat(updated.getStripeScheduleId()).isNotNull();
            sandboxScheduleId = updated.getStripeScheduleId();

            // Verify Stripe Schedule exists
            var schedule = SubscriptionSchedule.retrieve(sandboxScheduleId);
            assertThat(schedule).isNotNull();
        }

        @AfterEach
        void cleanup() throws StripeException {
            if (sandboxScheduleId != null) {
                try { SubscriptionSchedule.retrieve(sandboxScheduleId).cancel(); } catch (Exception ignored) {}
            }
            if (sandboxSubscriptionId != null) {
                try { Subscription.retrieve(sandboxSubscriptionId).cancel(); } catch (Exception ignored) {}
            }
        }
    }

    // ========================================================================
    // Stripe helper methods
    // ========================================================================

    private String createStripeCustomer() throws StripeException {
        var customer = stripeService.createCustomer(
                "downgrade-int-test-" + System.currentTimeMillis() + "@checkitout.pl",
                "Downgrade Integration Test", Map.of());
        var pm = PaymentMethod.create(PaymentMethodCreateParams.builder()
                .setType(PaymentMethodCreateParams.Type.CARD)
                .putExtraParam("card[token]", "tok_visa")
                .build());
        pm.attach(PaymentMethodAttachParams.builder().setCustomer(customer.getId()).build());
        Customer.retrieve(customer.getId()).update(CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(pm.getId()).build())
                .build());
        return customer.getId();
    }

    private String createStripeSubscription(String customerId, String priceId) throws StripeException {
        var sub = Subscription.create(SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                .build());
        return sub.getId();
    }
}
